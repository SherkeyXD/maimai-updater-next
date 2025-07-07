package com.bakapiano.maimai.updater.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.bakapiano.maimai.updater.R;

import java.util.List;

public class DifficultyProgressAdapter extends RecyclerView.Adapter<DifficultyProgressAdapter.ProgressViewHolder> {
    
    private List<DifficultyProgress> progressList;
    
    public DifficultyProgressAdapter(List<DifficultyProgress> progressList) {
        this.progressList = progressList;
    }
    
    @NonNull
    @Override
    public ProgressViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_difficulty_progress, parent, false);
        return new ProgressViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ProgressViewHolder holder, int position) {
        DifficultyProgress progress = progressList.get(position);
        holder.bind(progress);
    }
    
    @Override
    public int getItemCount() {
        return progressList.size();
    }
    
    public void updateProgress(String difficulty, int progress, String status) {
        for (int i = 0; i < progressList.size(); i++) {
            DifficultyProgress item = progressList.get(i);
            if (item.difficultyName.equals(difficulty)) {
                item.progress = progress;
                item.status = status;
                notifyItemChanged(i);
                break;
            }
        }
    }
    
    static class ProgressViewHolder extends RecyclerView.ViewHolder {
        
        private TextView difficultyName;
        private TextView progressStatus;
        private LinearProgressIndicator difficultyProgress;
        
        public ProgressViewHolder(@NonNull View itemView) {
            super(itemView);
            
            difficultyName = itemView.findViewById(R.id.difficulty_name);
            progressStatus = itemView.findViewById(R.id.progress_status);
            difficultyProgress = itemView.findViewById(R.id.difficulty_progress);
        }
        
        public void bind(DifficultyProgress progress) {
            difficultyName.setText(progress.difficultyName);
            progressStatus.setText(progress.status);
            difficultyProgress.setProgress(progress.progress);
            
            // Set color based on status
            int color = getColorForStatus(progress.status);
            difficultyProgress.setIndicatorColor(color);
        }
        
        private int getColorForStatus(String status) {
            switch (status) {
                case "准备中":
                    return itemView.getContext().getColor(R.color.progress_preparing);
                case "获取中":
                    return itemView.getContext().getColor(R.color.progress_fetching);
                case "上传中":
                    return itemView.getContext().getColor(R.color.progress_uploading);
                case "完成":
                    return itemView.getContext().getColor(R.color.progress_complete);
                case "错误":
                    return itemView.getContext().getColor(R.color.progress_error);
                default:
                    return itemView.getContext().getColor(R.color.md_theme_light_primary);
            }
        }
    }
    
    public static class DifficultyProgress {
        public String difficultyName;
        public int progress;
        public String status;
        
        public DifficultyProgress(String difficultyName, int progress, String status) {
            this.difficultyName = difficultyName;
            this.progress = progress;
            this.status = status;
        }
    }
}
