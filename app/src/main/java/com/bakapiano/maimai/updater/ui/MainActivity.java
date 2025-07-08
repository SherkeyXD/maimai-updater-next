package com.bakapiano.maimai.updater.ui;

import static com.bakapiano.maimai.updater.Util.copyText;
import static com.bakapiano.maimai.updater.Util.getDifficulties;
import static com.bakapiano.maimai.updater.crawler.CrawlerCaller.writeLog;
import static java.lang.Integer.parseInt;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

import com.bakapiano.maimai.updater.R;
import com.bakapiano.maimai.updater.crawler.Callback;
import com.bakapiano.maimai.updater.crawler.CrawlerCaller;
import com.bakapiano.maimai.updater.notification.NotificationUtil;
import com.bakapiano.maimai.updater.server.HttpServer;
import com.bakapiano.maimai.updater.server.HttpServerService;
import com.bakapiano.maimai.updater.vpn.core.Constant;
import com.bakapiano.maimai.updater.vpn.core.LocalVpnService;
import com.bakapiano.maimai.updater.vpn.core.ProxyConfig;

public class MainActivity extends AppCompatActivity implements
        LocalVpnService.onStatusChangedListener,
        NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int START_VPN_SERVICE_REQUEST_CODE = 1985;
    private static String GL_HISTORY_LOGS;

    // UI Components
    private DrawerLayout drawerLayout;
    private MaterialToolbar toolbar;
    private NavigationView navigationView;
    private MaterialCardView accountCard;
    private ShapeableImageView avatarImage;
    private TextView accountName;
    private TextView accountStatus;
    private ImageView accountExpandIcon;
    
    // Upload Settings
    private MaterialCardView uploadSettingsCard;
    private LinearLayout uploadSettingsContent;
    private ImageView uploadSettingsExpandIcon;
    private SwitchMaterial copyUrlSwitch;
    private SwitchMaterial autoLaunchSwitch;
    private ChipGroup difficultyChipGroup;
    private MaterialButton startTaskButton;
    
    // Progress
    private LinearProgressIndicator overallProgress;
    private TextView overallProgressText;
    private LinearLayout individualProgressContainer;
    
    // Log
    private TextView logText;
    
    // Data
    private SharedPreferences mContextSp;
    private Calendar mCalendar;
    private List<Account> accountList = new ArrayList<>();
    private Account currentAccount;
    private boolean uploadSettingsExpanded = false;
    
    // Theme and Avatar
    private ActivityResultLauncher<Intent> avatarPickerLauncher;
    private ActivityResultLauncher<Intent> editAvatarPickerLauncher;
    private String tempAvatarPath;
    private ShapeableImageView tempAvatarImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 应用保存的主题设置
        applyThemeSettings();
        
        setContentView(R.layout.activity_main);
        
        initViews();
        initData();
        initListeners();
        loadContextData();
        updateUI();
        
        LocalVpnService.addOnStatusChangedListener(this);
        CrawlerCaller.listener = this;
    }
    
    private void initViews() {
        // Toolbar and Navigation
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.app_name, R.string.app_name);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        
        // Account Section
        accountCard = findViewById(R.id.account_card);
        avatarImage = findViewById(R.id.avatar_image);
        accountName = findViewById(R.id.account_name);
        accountStatus = findViewById(R.id.account_status);
        accountExpandIcon = findViewById(R.id.account_expand_icon);
        
        // Upload Settings
        uploadSettingsCard = findViewById(R.id.upload_settings_card);
        uploadSettingsContent = findViewById(R.id.upload_settings_content);
        uploadSettingsExpandIcon = findViewById(R.id.upload_settings_expand_icon);
        copyUrlSwitch = findViewById(R.id.copy_url_switch);
        autoLaunchSwitch = findViewById(R.id.auto_launch_switch);
        difficultyChipGroup = findViewById(R.id.difficulty_chip_group);
        startTaskButton = findViewById(R.id.start_task_button);
        
        // Progress
        overallProgress = findViewById(R.id.overall_progress);
        overallProgressText = findViewById(R.id.overall_progress_text);
        individualProgressContainer = findViewById(R.id.individual_progress_container);
        
        // Log
        logText = findViewById(R.id.log_text);
        
        // Initialize avatar picker
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            if (tempAvatarImageView != null) {
                                // 用于编辑对话框
                                loadAvatarFromUri(imageUri, tempAvatarImageView);
                                tempAvatarPath = imageUri.toString();
                                tempAvatarImageView = null; // 清除临时引用
                            } else {
                                // 用于主界面
                                loadAvatarFromUri(imageUri);
                            }
                        }
                    }
                });
    }
    
    private void initData() {
        mContextSp = getSharedPreferences("com.bakapiano.maimai.updater.data", Context.MODE_PRIVATE);
        mCalendar = Calendar.getInstance();
        
        // Set initial log
        if (GL_HISTORY_LOGS != null) {
            logText.setText(GL_HISTORY_LOGS);
        } else {
            logText.setText("准备就绪，等待开始...");
        }
    }
    
    private void initListeners() {
        navigationView.setNavigationItemSelectedListener(this);
        
        // Account card click
        accountCard.setOnClickListener(v -> showAccountManagementDialog());
        
        // Upload settings expand/collapse
        findViewById(R.id.upload_settings_header).setOnClickListener(v -> toggleUploadSettings());
        
        // Start/Stop task button
        startTaskButton.setOnClickListener(v -> toggleTask());
    }
    
    private void updateUI() {
        updateTitle();
        updateAccountDisplay();
        updateProgress();
    }
    
    private void updateTitle() {
        if (LocalVpnService.IsRunning) {
            toolbar.setTitle(getString(R.string.connected));
        } else {
            toolbar.setTitle(getString(R.string.disconnected));
        }
    }
    
    private void updateAccountDisplay() {
        if (currentAccount != null) {
            accountName.setText(currentAccount.username);
            accountStatus.setText("已登录");
            if (currentAccount.avatarPath != null) {
                loadAvatarFromPath(currentAccount.avatarPath);
            }
        } else {
            accountName.setText("未选择账号");
            accountStatus.setText("点击选择或添加账号");
            avatarImage.setImageResource(R.drawable.ic_account_circle_24);
        }
    }
    
    private void updateProgress() {
        // Update overall progress based on current state
        if (LocalVpnService.IsRunning) {
            overallProgress.setProgress(30);
            overallProgressText.setText("VPN 已连接，等待获取数据...");
        } else {
            overallProgress.setProgress(0);
            overallProgressText.setText("就绪");
        }
        // 更新按钮状态
        updateTaskButtonState();
    }
    
    private void toggleUploadSettings() {
        uploadSettingsExpanded = !uploadSettingsExpanded;
        
        if (uploadSettingsExpanded) {
            uploadSettingsContent.setVisibility(View.VISIBLE);
            uploadSettingsExpandIcon.setRotation(180f);
        } else {
            uploadSettingsContent.setVisibility(View.GONE);
            uploadSettingsExpandIcon.setRotation(0f);
        }
        
        // Animate the icon rotation
        ObjectAnimator rotation = ObjectAnimator.ofFloat(
                uploadSettingsExpandIcon, "rotation", 
                uploadSettingsExpanded ? 0f : 180f, 
                uploadSettingsExpanded ? 180f : 0f
        );
        rotation.setDuration(300);
        rotation.setInterpolator(new DecelerateInterpolator());
        rotation.start();
    }
    
    private void showAccountManagementDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_account_management, null);
        
        // Find all views
        LinearLayout accountListSection = dialogView.findViewById(R.id.account_list_section);
        LinearLayout newAccountSection = dialogView.findViewById(R.id.new_account_section);
        TextView newAccountTitle = dialogView.findViewById(R.id.new_account_title);
        ShapeableImageView dialogAvatarImage = dialogView.findViewById(R.id.dialog_avatar_image);
        TextInputEditText dialogUsername = dialogView.findViewById(R.id.dialog_username);
        TextInputEditText dialogPassword = dialogView.findViewById(R.id.dialog_password);
        MaterialButton selectAvatarButton = dialogView.findViewById(R.id.select_avatar_button);
        RecyclerView accountsRecyclerView = dialogView.findViewById(R.id.accounts_recycler_view);
        MaterialButton addAccountButton = dialogView.findViewById(R.id.add_account_button);
        MaterialButton dialogSaveButton = dialogView.findViewById(R.id.dialog_save_button);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // Decide what to show based on whether accounts exist
        boolean hasAccounts = accountList != null && !accountList.isEmpty();
        final boolean[] isShowingNewAccountForm = {!hasAccounts};
        
        if (hasAccounts) {
            // Show account list section, hide new account section
            accountListSection.setVisibility(View.VISIBLE);
            newAccountSection.setVisibility(View.GONE);
            addAccountButton.setText("添加新账号");
            
            // Set up RecyclerView for account list
            AccountAdapter adapter = new AccountAdapter(accountList, new AccountAdapter.OnAccountClickListener() {
                @Override
                public void onAccountClick(Account account) {
                    // Switch to this account
                    currentAccount = account;
                    DataContext.Username = account.username;
                    DataContext.Password = account.password;
                    saveAccountData();
                    updateAccountDisplay();
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "已切换到账号: " + account.username, Toast.LENGTH_SHORT).show();
                }
                
                @Override
                public void onAccountEdit(Account account) {
                    // Close current dialog and show edit dialog
                    dialog.dismiss();
                    showEditAccountDialog(account);
                }
                
                @Override
                public void onAccountDelete(Account account) {
                    // Remove account from list
                    accountList.remove(account);
                    
                    // Handle account deletion
                    if (currentAccount != null && currentAccount.username.equals(account.username)) {
                        // If deleting current account, switch to first remaining account or reset
                        if (!accountList.isEmpty()) {
                            currentAccount = accountList.get(0);
                            DataContext.Username = currentAccount.username;
                            DataContext.Password = currentAccount.password;
                        } else {
                            currentAccount = null;
                            DataContext.Username = "";
                            DataContext.Password = "";
                        }
                    }
                    
                    saveAccountData();
                    updateAccountDisplay();
                    
                    // Refresh the dialog
                    dialog.dismiss();
                    showAccountManagementDialog();
                    
                    Toast.makeText(MainActivity.this, "账号已删除", Toast.LENGTH_SHORT).show();
                }
            });
            accountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            accountsRecyclerView.setAdapter(adapter);
            
            // Set current account to highlight it
            adapter.setCurrentAccount(currentAccount);
        } else {
            // Show new account section, hide account list section
            accountListSection.setVisibility(View.GONE);
            newAccountSection.setVisibility(View.VISIBLE);
            newAccountTitle.setText("添加第一个账号");
            addAccountButton.setText("保存账号");
            isShowingNewAccountForm[0] = true;
        }
        
        // Avatar selection
        selectAvatarButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            avatarPickerLauncher.launch(intent);
        });
        
        // Add account button click
        addAccountButton.setOnClickListener(v -> {
            if (!isShowingNewAccountForm[0]) {
                // Check if we can add more accounts
                if (accountList != null && accountList.size() >= 10) {
                    Toast.makeText(MainActivity.this, "最多只能添加10个账号", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Show new account form
                accountListSection.setVisibility(View.GONE);
                newAccountSection.setVisibility(View.VISIBLE);
                newAccountTitle.setText("添加新账号");
                addAccountButton.setText("保存新账号");
                dialogUsername.setText("");
                dialogPassword.setText("");
                dialogAvatarImage.setImageResource(R.drawable.ic_account_circle_24);
                isShowingNewAccountForm[0] = true;
            } else {
                // Save the new account
                addNewAccount(dialogUsername, dialogPassword, dialogAvatarImage, dialog);
            }
        });
        
        // Save account button - only for editing existing accounts
        dialogSaveButton.setOnClickListener(v -> {
            if (isShowingNewAccountForm[0]) {
                // Save new account
                addNewAccount(dialogUsername, dialogPassword, dialogAvatarImage, dialog);
            } else {
                // Close dialog
                dialog.dismiss();
            }
        });
        
        dialog.show();
    }
    
    private void addNewAccount(TextInputEditText usernameEdit, TextInputEditText passwordEdit, 
                              ShapeableImageView avatarImage, AlertDialog dialog) {
        String username = usernameEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入完整的账号信息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if username already exists
        if (accountList != null) {
            for (Account account : accountList) {
                if (account.username.equals(username)) {
                    Toast.makeText(this, "账号名称已存在", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        
        // 验证账户有效性
        Toast.makeText(this, "正在验证账户...", Toast.LENGTH_SHORT).show();
        CrawlerCaller.verifyAccount(username, password, result -> {
            runOnUiThread(() -> {
                if (!(Boolean) result) {
                    Toast.makeText(this, "账户验证失败，请检查用户名和密码", Toast.LENGTH_LONG).show();
                    return;
                }
                
                // 账户验证成功，创建新账号
                Account newAccount = new Account();
                newAccount.username = username;
                newAccount.password = password;
                newAccount.avatarPath = null; // Will be set by avatar picker if needed
                
                // Initialize account list if needed
                if (accountList == null) {
                    accountList = new ArrayList<>();
                }
                
                accountList.add(newAccount);
                
                // Set as current account
                currentAccount = newAccount;
                DataContext.Username = username;
                DataContext.Password = password;
                
                saveAccountData();
                updateAccountDisplay();
                
                dialog.dismiss();
                Toast.makeText(this, "账号验证成功并已添加", Toast.LENGTH_SHORT).show();
            });
        });
    }
    
    private void loadAvatarFromUri(Uri imageUri) {
        try {
            InputStream imageStream = getContentResolver().openInputStream(imageUri);
            Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
            avatarImage.setImageBitmap(selectedImage);
            
            // Save avatar path for current account
            if (currentAccount != null) {
                currentAccount.avatarPath = imageUri.toString();
                saveAccountData();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "无法加载头像", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadAvatarFromPath(String avatarPath) {
        loadAvatarFromPath(avatarPath, avatarImage);
    }
    
    private void loadAvatarFromPath(String avatarPath, ShapeableImageView imageView) {
        try {
            Uri uri = Uri.parse(avatarPath);
            InputStream imageStream = getContentResolver().openInputStream(uri);
            Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
            imageView.setImageBitmap(selectedImage);
        } catch (Exception e) {
            e.printStackTrace();
            imageView.setImageResource(R.drawable.ic_account_circle_24);
        }
    }
    
    private void saveSettings() {
        checkProberAccount(result -> {
            if ((Boolean) result) {
                saveContextData();
                runOnUiThread(() -> {
                    Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void toggleTask() {
        if (LocalVpnService.IsRunning) {
            // 停止VPN服务
            stopVpnService();
        } else {
            // 启动VPN服务
            startVpnService();
        }
    }
    
    private void startVpnService() {
        // 首先检查当前账户有效性
        checkProberAccount(result -> {
            if (!(Boolean) result) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "当前账户无效，请重新验证账户信息", Toast.LENGTH_LONG).show();
                });
                return;
            }
            
            runOnUiThread(() -> {
                // 读取当前UI数据并保存到上下文
                readCurrentUIDataAndSave();
                
                // 检查VPN权限
                Intent intent = VpnService.prepare(this);
                if (intent != null) {
                    // 需要用户授权VPN权限
                    startActivityForResult(intent, START_VPN_SERVICE_REQUEST_CODE);
                } else {
                    // 已有VPN权限，直接启动服务
                    startVpnServiceDirectly();
                }
            });
        });
    }
    
    private void stopVpnService() {
        try {
            Intent intent = new Intent(this, LocalVpnService.class);
            stopService(intent);
            updateTaskButtonState();
            Toast.makeText(this, "正在停止任务...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "停止VPN服务失败", e);
            Toast.makeText(this, "停止失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void readCurrentUIDataAndSave() {
        // 读取当前UI的设置并保存到DataContext
        checkProberAccount(result -> {
            if ((Boolean) result) {
                saveContextData();
                runOnUiThread(() -> {
                    Log.d(TAG, "当前UI数据已保存到上下文");
                });
            }
        });
    }
    
    private void startVpnServiceDirectly() {
        try {
            Intent intent = new Intent(this, LocalVpnService.class);
            startService(intent);
            updateTaskButtonState();
            Toast.makeText(this, "正在启动任务...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "启动VPN服务失败", e);
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void updateTaskButtonState() {
        runOnUiThread(() -> {
            if (LocalVpnService.IsRunning) {
                startTaskButton.setText(getString(R.string.stop_task));
            } else {
                startTaskButton.setText(getString(R.string.start_task));
            }
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == START_VPN_SERVICE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // 用户授权了VPN权限，启动服务
                startVpnServiceDirectly();
            } else {
                // 用户拒绝了VPN权限
                Toast.makeText(this, "需要VPN权限才能启动任务", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.nav_home) {
            // Already on home
        } else if (id == R.id.nav_theme) {
            showThemeSettingsDialog();
        } else if (id == R.id.nav_proxy) {
            showProxySettingsDialog();
        } else if (id == R.id.nav_update) {
            checkForUpdates();
        } else if (id == R.id.nav_about) {
            showAboutDialog();
        }
        
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
    
    private void showThemeSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_theme_settings, null);
        
        RadioGroup themeModeGroup = dialogView.findViewById(R.id.theme_mode_group);
        MaterialRadioButton radioLight = dialogView.findViewById(R.id.radio_light);
        MaterialRadioButton radioDark = dialogView.findViewById(R.id.radio_dark);
        MaterialRadioButton radioAuto = dialogView.findViewById(R.id.radio_auto);
        
        ChipGroup colorChipGroup = dialogView.findViewById(R.id.color_chip_group);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // 读取当前主题设置
        SharedPreferences prefs = getSharedPreferences("theme_settings", Context.MODE_PRIVATE);
        int currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        String currentColor = prefs.getString("theme_color", "purple");
        
        // 设置当前选中状态
        switch (currentMode) {
            case AppCompatDelegate.MODE_NIGHT_NO:
                radioLight.setChecked(true);
                break;
            case AppCompatDelegate.MODE_NIGHT_YES:
                radioDark.setChecked(true);
                break;
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
            default:
                radioAuto.setChecked(true);
                break;
        }
        
        // 设置颜色选中状态
        switch (currentColor) {
            case "purple":
                dialogView.findViewById(R.id.chip_color_purple).setSelected(true);
                break;
            case "blue":
                dialogView.findViewById(R.id.chip_color_blue).setSelected(true);
                break;
            case "green":
                dialogView.findViewById(R.id.chip_color_green).setSelected(true);
                break;
            case "orange":
                dialogView.findViewById(R.id.chip_color_orange).setSelected(true);
                break;
            case "pink":
                dialogView.findViewById(R.id.chip_color_pink).setSelected(true);
                break;
            default:
                dialogView.findViewById(R.id.chip_color_purple).setSelected(true);
                break;
        }
        
        // 保存按钮
        dialogView.findViewById(R.id.theme_save_button).setOnClickListener(v -> {
            // 获取选中的主题模式
            int selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (radioLight.isChecked()) {
                selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (radioDark.isChecked()) {
                selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
            }
            
            // 获取选中的颜色
            String selectedColor = "purple";
            if (((Chip) dialogView.findViewById(R.id.chip_color_purple)).isChecked()) {
                selectedColor = "purple";
            } else if (((Chip) dialogView.findViewById(R.id.chip_color_blue)).isChecked()) {
                selectedColor = "blue";
            } else if (((Chip) dialogView.findViewById(R.id.chip_color_green)).isChecked()) {
                selectedColor = "green";
            } else if (((Chip) dialogView.findViewById(R.id.chip_color_orange)).isChecked()) {
                selectedColor = "orange";
            } else if (((Chip) dialogView.findViewById(R.id.chip_color_pink)).isChecked()) {
                selectedColor = "pink";
            }
            
            // 保存设置
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("theme_mode", selectedMode);
            editor.putString("theme_color", selectedColor);
            editor.apply();
            
            // 应用主题
            AppCompatDelegate.setDefaultNightMode(selectedMode);
            
            dialog.dismiss();
            Toast.makeText(this, "主题设置已保存", Toast.LENGTH_SHORT).show();
            
            // 重启Activity以应用颜色主题
            if (!currentColor.equals(selectedColor)) {
                recreate();
            }
        });
        
        dialogView.findViewById(R.id.theme_cancel_button).setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void showProxySettingsDialog() {
        // Implementation similar to original proxy settings
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("代理设置");
        
        // Create input fields
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        
        TextView textView1 = new TextView(this);
        textView1.setText("登录链接获取地址：");
        textView1.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        
        TextInputEditText editText1 = new TextInputEditText(this);
        editText1.setText(DataContext.WebHost);
        
        layout.addView(textView1);
        layout.addView(editText1);
        
        builder.setView(layout);
        
        builder.setPositiveButton("确定", (dialog, which) -> {
            String input1 = editText1.getText().toString();
            DataContext.WebHost = input1;
            mContextSp.edit().putString("webHost", input1).apply();
            Toast.makeText(this, "代理设置已保存", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("取消", null);
        
        builder.setNeutralButton("恢复默认", (dialog, which) -> {
            String defaultWebHost = "https://maimai.bakapiano.com/shortcut?username=bakapiano666&password=114514";
            DataContext.WebHost = defaultWebHost;
            mContextSp.edit().putString("webHost", defaultWebHost).apply();
            Toast.makeText(this, "已恢复默认设置", Toast.LENGTH_SHORT).show();
        });
        
        builder.show();
    }
    
    private void checkForUpdates() {
        getLatestVersion(result -> {
            String latest = (String) result;
            String current = getVersionName().trim();
            
            runOnUiThread(() -> {
                MaterialAlertDialogBuilder builder;
                if (latest != null) {
                    builder = new MaterialAlertDialogBuilder(this);
                    builder.setTitle(getString(R.string.app_name) + " " + current);
                    
                    if (latest.equals(current)) {
                        builder.setMessage("已经是最新版本~");
                        builder.setPositiveButton("确定", null);
                    } else {
                        builder.setMessage("当前版本：" + current + "\n最新版本：" + latest + "\n是否前往网站下载最新版？");
                        builder.setPositiveButton("更新", (dialog, which) -> 
                                openWebLink("https://maimaidx-prober-updater-android.bakapiano.com/"));
                        builder.setNegativeButton("取消", null);
                    }
                } else {
                    builder = new MaterialAlertDialogBuilder(this);
                    builder.setTitle(getString(R.string.app_name) + " " + current);
                    builder.setMessage("获取最新版本号时出现错误！");
                    builder.setPositiveButton("确定", null);
                }
                builder.show();
            });
        });
    }
    
    private void showAboutDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(getString(R.string.app_name) + " " + getVersionName());
        builder.setMessage(R.string.about_info);
        builder.setPositiveButton("确定", null);
        builder.show();
    }
    
    // Original methods adapted for new UI
    @Override
    public void onStatusChanged(String status, Boolean isRunning) {
        runOnUiThread(() -> {
            updateTitle();
            updateProgress();
            onLogReceived(status);
        });
    }
    
    public void onLogReceived(String logInfo) {
        GL_HISTORY_LOGS = GL_HISTORY_LOGS == null ? logInfo : GL_HISTORY_LOGS + "\n" + logInfo;
        runOnUiThread(() -> {
            logText.setText(GL_HISTORY_LOGS);
            // Auto-scroll to bottom
            if (logText.getParent() instanceof View) {
                ((View) logText.getParent()).post(() -> {
                    View parent = (View) logText.getParent();
                    if (parent.getParent() instanceof View) {
                        ((View) parent.getParent()).scrollTo(0, logText.getBottom());
                    }
                });
            }
        });
    }
    
    // Data management methods (adapted from original)
    private void loadContextData() {
        // Load account list first
        loadAccountListData();
        
        String username = mContextSp.getString("username", null);
        String password = mContextSp.getString("password", null);
        String avatarPath = mContextSp.getString("avatarPath", null);
        
        boolean copyUrl = mContextSp.getBoolean("copyUrl", true);
        boolean autoLaunch = mContextSp.getBoolean("autoLaunch", true);
        
        boolean basicEnabled = mContextSp.getBoolean("basicEnabled", false);
        boolean advancedEnabled = mContextSp.getBoolean("advancedEnabled", false);
        boolean expertEnabled = mContextSp.getBoolean("expertEnabled", true);
        boolean masterEnabled = mContextSp.getBoolean("masterEnabled", true);
        boolean remasterEnabled = mContextSp.getBoolean("remasterEnabled", true);
        
        String proxyHost = mContextSp.getString("porxyHost", "proxy.bakapiano.com");
        String webHost = mContextSp.getString("webHost", "");
        int proxyPort = mContextSp.getInt("porxyPort", 2569);
        
        // Set current account from account list if available
        if (accountList != null && !accountList.isEmpty()) {
            int currentAccountIndex = mContextSp.getInt("currentAccountIndex", 0);
            if (currentAccountIndex >= 0 && currentAccountIndex < accountList.size()) {
                currentAccount = accountList.get(currentAccountIndex);
            } else {
                currentAccount = accountList.get(0);
            }
        } else if (username != null && password != null) {
            // Fallback to old single account method
            currentAccount = new Account();
            currentAccount.username = username;
            currentAccount.password = password;
            currentAccount.avatarPath = avatarPath;
            
            // Migrate to new account list format
            accountList = new ArrayList<>();
            accountList.add(currentAccount);
            saveAccountData();
        }
        
        // Set switches
        copyUrlSwitch.setChecked(copyUrl);
        autoLaunchSwitch.setChecked(autoLaunch);
        
        // Set difficulty chips
        ((Chip) findViewById(R.id.chip_basic)).setChecked(basicEnabled);
        ((Chip) findViewById(R.id.chip_advanced)).setChecked(advancedEnabled);
        ((Chip) findViewById(R.id.chip_expert)).setChecked(expertEnabled);
        ((Chip) findViewById(R.id.chip_master)).setChecked(masterEnabled);
        ((Chip) findViewById(R.id.chip_remaster)).setChecked(remasterEnabled);
        
        // Set DataContext
        DataContext.Username = currentAccount != null ? currentAccount.username : "";
        DataContext.Password = currentAccount != null ? currentAccount.password : "";
        DataContext.CopyUrl = copyUrl;
        DataContext.AutoLaunch = autoLaunch;
        DataContext.BasicEnabled = basicEnabled;
        DataContext.AdvancedEnabled = advancedEnabled;
        DataContext.ExpertEnabled = expertEnabled;
        DataContext.MasterEnabled = masterEnabled;
        DataContext.RemasterEnabled = remasterEnabled;
        DataContext.ProxyPort = proxyPort;
        DataContext.ProxyHost = proxyHost;
        DataContext.WebHost = webHost;
    }
    
    private void loadAccountListData() {
        int accountCount = mContextSp.getInt("accountCount", 0);
        
        if (accountCount > 0) {
            accountList = new ArrayList<>();
            
            for (int i = 0; i < accountCount; i++) {
                Account account = new Account();
                account.username = mContextSp.getString("account_" + i + "_username", "");
                account.password = mContextSp.getString("account_" + i + "_password", "");
                account.avatarPath = mContextSp.getString("account_" + i + "_avatarPath", null);
                
                if (!account.username.isEmpty()) {
                    accountList.add(account);
                }
            }
        } else {
            accountList = new ArrayList<>();
        }
    }
    
    private void saveContextData() {
        SharedPreferences.Editor editor = mContextSp.edit();
        saveAccountContextData(editor);
        saveOptionsContextData(editor);
        saveDifficultiesContextData(editor);
        editor.apply();
    }
    
    private void saveAccountData() {
        SharedPreferences.Editor editor = mContextSp.edit();
        saveAccountContextData(editor);
        saveAccountListData(editor);
        editor.apply();
    }
    
    private void saveAccountListData(SharedPreferences.Editor editor) {
        if (accountList != null && !accountList.isEmpty()) {
            // Save number of accounts
            editor.putInt("accountCount", accountList.size());
            
            // Save each account
            for (int i = 0; i < accountList.size(); i++) {
                Account account = accountList.get(i);
                editor.putString("account_" + i + "_username", account.username);
                editor.putString("account_" + i + "_password", account.password);
                editor.putString("account_" + i + "_avatarPath", account.avatarPath);
            }
            
            // Save current account index
            if (currentAccount != null) {
                for (int i = 0; i < accountList.size(); i++) {
                    if (accountList.get(i).username.equals(currentAccount.username)) {
                        editor.putInt("currentAccountIndex", i);
                        break;
                    }
                }
            }
        } else {
            editor.putInt("accountCount", 0);
            editor.putInt("currentAccountIndex", -1);
        }
    }
    
    private void saveAccountContextData(SharedPreferences.Editor editor) {
        if (currentAccount != null) {
            editor.putString("username", currentAccount.username);
            editor.putString("password", currentAccount.password);
            editor.putString("avatarPath", currentAccount.avatarPath);
        }
    }
    
    private void saveOptionsContextData(SharedPreferences.Editor editor) {
        DataContext.CopyUrl = copyUrlSwitch.isChecked();
        DataContext.AutoLaunch = autoLaunchSwitch.isChecked();
        
        editor.putBoolean("copyUrl", DataContext.CopyUrl);
        editor.putBoolean("autoLaunch", DataContext.AutoLaunch);
    }
    
    private void saveDifficultiesContextData(SharedPreferences.Editor editor) {
        DataContext.BasicEnabled = ((Chip) findViewById(R.id.chip_basic)).isChecked();
        DataContext.AdvancedEnabled = ((Chip) findViewById(R.id.chip_advanced)).isChecked();
        DataContext.ExpertEnabled = ((Chip) findViewById(R.id.chip_expert)).isChecked();
        DataContext.MasterEnabled = ((Chip) findViewById(R.id.chip_master)).isChecked();
        DataContext.RemasterEnabled = ((Chip) findViewById(R.id.chip_remaster)).isChecked();
        
        editor.putBoolean("basicEnabled", DataContext.BasicEnabled);
        editor.putBoolean("advancedEnabled", DataContext.AdvancedEnabled);
        editor.putBoolean("expertEnabled", DataContext.ExpertEnabled);
        editor.putBoolean("masterEnabled", DataContext.MasterEnabled);
        editor.putBoolean("remasterEnabled", DataContext.RemasterEnabled);
    }
    
    // Utility methods (copied from original)
    private void checkProberAccount(Callback callback) {
        if (currentAccount == null) {
            showInvalidAccountDialog();
            callback.onResponse(false);
            return;
        }
        
        DataContext.Username = currentAccount.username;
        DataContext.Password = currentAccount.password;
        
        CrawlerCaller.verifyAccount(DataContext.Username, DataContext.Password, result -> {
            if (!(Boolean) result) showInvalidAccountDialog();
            callback.onResponse(result);
        });
    }
    
    private void showInvalidAccountDialog() {
        runOnUiThread(() -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(getString(R.string.app_name) + " " + getVersionName());
            builder.setMessage("查分账户信息无效");
            builder.setPositiveButton("确定", null);
            builder.show();
        });
    }
    
    private void getLatestVersion(Callback callback) {
        CrawlerCaller.getLatestVersion(callback);
    }
    
    private void openWebLink(String url) {
        Intent intent = new Intent();
        intent.setData(Uri.parse(url));
        intent.setAction(Intent.ACTION_VIEW);
        startActivity(intent);
    }
    
    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }
    
    @Override
    protected void onDestroy() {
        LocalVpnService.removeOnStatusChangedListener(this);
        super.onDestroy();
    }
    
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    
    // Inner classes
    public static class Account {
        public String username;
        public String password;
        public String avatarPath;
    }
    
    private void showEditAccountDialog(Account accountToEdit) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_account, null);
        
        TextInputEditText editUsername = dialogView.findViewById(R.id.edit_username);
        TextInputEditText editPassword = dialogView.findViewById(R.id.edit_password);
        ShapeableImageView editAvatarImage = dialogView.findViewById(R.id.edit_avatar_image);
        MaterialButton selectAvatarButton = dialogView.findViewById(R.id.edit_select_avatar_button);
        MaterialButton saveButton = dialogView.findViewById(R.id.edit_save_button);
        MaterialButton cancelButton = dialogView.findViewById(R.id.edit_cancel_button);
        
        // 填充当前账号信息
        editUsername.setText(accountToEdit.username);
        editPassword.setText(accountToEdit.password);
        
        // 加载头像
        if (accountToEdit.avatarPath != null) {
            loadAvatarFromPath(accountToEdit.avatarPath, editAvatarImage);
        } else {
            editAvatarImage.setImageResource(R.drawable.ic_account_circle_24);
        }
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(dialogView);
        builder.setTitle("编辑账号");
        
        AlertDialog dialog = builder.create();
        
        // 选择头像按钮
        selectAvatarButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            
            // 使用现有的avatarPickerLauncher，但临时修改其行为
            tempAvatarImageView = editAvatarImage; // 临时保存目标ImageView
            avatarPickerLauncher.launch(intent);
        });
        
        // 保存按钮
        saveButton.setOnClickListener(v -> {
            String newUsername = editUsername.getText().toString().trim();
            String newPassword = editPassword.getText().toString().trim();
            
            if (newUsername.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(this, "请输入完整的账号信息", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 检查用户名是否与其他账号重复（除了当前编辑的账号）
            if (accountList != null) {
                for (Account account : accountList) {
                    if (!account.username.equals(accountToEdit.username) && 
                        account.username.equals(newUsername)) {
                        Toast.makeText(this, "账号名称已存在", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
            
            // 验证账户有效性
            Toast.makeText(this, "正在验证账户...", Toast.LENGTH_SHORT).show();
            CrawlerCaller.verifyAccount(newUsername, newPassword, result -> {
                runOnUiThread(() -> {
                    if (!(Boolean) result) {
                        Toast.makeText(this, "账户验证失败，请检查用户名和密码", Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    // 验证成功，更新账号信息
                    accountToEdit.username = newUsername;
                    accountToEdit.password = newPassword;
                    
                    // 更新头像路径（如果有新的）
                    if (tempAvatarPath != null) {
                        accountToEdit.avatarPath = tempAvatarPath;
                        tempAvatarPath = null; // 清除临时路径
                    }
                    
                    // 如果编辑的是当前账号，更新DataContext
                    if (currentAccount != null && currentAccount.username.equals(accountToEdit.username)) {
                        currentAccount = accountToEdit;
                        DataContext.Username = newUsername;
                        DataContext.Password = newPassword;
                    }
                    
                    saveAccountData();
                    updateAccountDisplay();
                    
                    dialog.dismiss();
                    Toast.makeText(this, "账号信息已更新", Toast.LENGTH_SHORT).show();
                });
            });
        });
        
        // 取消按钮
        cancelButton.setOnClickListener(v -> {
            tempAvatarPath = null; // 清除临时头像路径
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void loadAvatarFromUri(Uri imageUri, ShapeableImageView imageView) {
        try {
            InputStream imageStream = getContentResolver().openInputStream(imageUri);
            Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
            imageView.setImageBitmap(selectedImage);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "无法加载头像", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void applyThemeSettings() {
        SharedPreferences prefs = getSharedPreferences("theme_settings", Context.MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        String themeColor = prefs.getString("theme_color", "default");
        
        // 应用夜间模式设置
        AppCompatDelegate.setDefaultNightMode(themeMode);
        
        // 应用颜色主题（如果需要的话）
        applyColorTheme(themeColor);
    }
    
    private void applyColorTheme(String colorTheme) {
        // 根据颜色主题设置不同的主题资源
        // 这里可以根据需要实现不同的颜色主题
        switch (colorTheme) {
            case "purple":
                // 紫色主题（默认）
                break;
            case "blue":
                // 可以在这里设置蓝色主题相关的配置
                break;
            case "green":
                // 绿色主题
                break;
            case "orange":
                // 橙色主题
                break;
            case "pink":
                // 粉色主题
                break;
            default:
                // 默认主题
                break;
        }
    }
}
