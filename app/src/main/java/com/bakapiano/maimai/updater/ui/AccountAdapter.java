package com.bakapiano.maimai.updater.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.bakapiano.maimai.updater.R;

import java.io.InputStream;
import java.util.List;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.AccountViewHolder> {
    
    private List<MainActivity.Account> accounts;
    private OnAccountClickListener listener;
    private MainActivity.Account currentAccount;
    
    public interface OnAccountClickListener {
        void onAccountClick(MainActivity.Account account);
        default void onAccountDelete(MainActivity.Account account) {}
        default void onAccountEdit(MainActivity.Account account) {}
    }
    
    public AccountAdapter(List<MainActivity.Account> accounts, OnAccountClickListener listener) {
        this.accounts = accounts;
        this.listener = listener;
    }
    
    public void setCurrentAccount(MainActivity.Account currentAccount) {
        this.currentAccount = currentAccount;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account, parent, false);
        return new AccountViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        MainActivity.Account account = accounts.get(position);
        holder.bind(account);
    }
    
    @Override
    public int getItemCount() {
        return accounts.size();
    }
    
    class AccountViewHolder extends RecyclerView.ViewHolder {
        
        private ShapeableImageView accountAvatar;
        private TextView accountNameItem;
        private TextView accountStatusItem;
        private ImageView editAccountButton;
        private ImageView deleteAccountButton;
        
        public AccountViewHolder(@NonNull View itemView) {
            super(itemView);
            
            accountAvatar = itemView.findViewById(R.id.account_avatar);
            accountNameItem = itemView.findViewById(R.id.account_name_item);
            accountStatusItem = itemView.findViewById(R.id.account_status_item);
            editAccountButton = itemView.findViewById(R.id.edit_account_button);
            deleteAccountButton = itemView.findViewById(R.id.delete_account_button);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onAccountClick(accounts.get(position));
                }
            });
            
            editAccountButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onAccountEdit(accounts.get(position));
                }
            });
            
            deleteAccountButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && position < accounts.size()) {
                    MainActivity.Account accountToDelete = accounts.get(position);
                    accounts.remove(position);
                    notifyItemRemoved(position);
                    if (listener != null) {
                        listener.onAccountDelete(accountToDelete);
                    }
                }
            });
        }
        
        public void bind(MainActivity.Account account) {
            accountNameItem.setText(account.username);
            
            // Check if this is the current account
            boolean isCurrentAccount = currentAccount != null && 
                                     currentAccount.username.equals(account.username);
            
            // Set selected state to trigger background selector
            itemView.setSelected(isCurrentAccount);
            
            if (isCurrentAccount) {
                accountStatusItem.setText("当前账号");
            } else {
                accountStatusItem.setText("点击选择此账号");
            }
            
            // Load avatar
            if (account.avatarPath != null) {
                try {
                    Uri uri = Uri.parse(account.avatarPath);
                    InputStream imageStream = itemView.getContext().getContentResolver().openInputStream(uri);
                    Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                    accountAvatar.setImageBitmap(selectedImage);
                } catch (Exception e) {
                    accountAvatar.setImageResource(R.drawable.ic_account_circle_24);
                }
            } else {
                accountAvatar.setImageResource(R.drawable.ic_account_circle_24);
            }
        }
    }
}
