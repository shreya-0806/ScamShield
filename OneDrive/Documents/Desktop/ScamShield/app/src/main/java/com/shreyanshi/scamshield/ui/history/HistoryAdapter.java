package com.shreyanshi.scamshield.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<CallLogModel> callLogs;

    public HistoryAdapter(List<CallLogModel> callLogs) {
        this.callLogs = callLogs;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_call_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CallLogModel model = callLogs.get(position);

        holder.tvPhone.setText(model.getPhoneNumber());
        holder.tvDate.setText(model.getDateTime());

        if (model.isScam()) {
            holder.tvStatus.setText("⚠ Scam Detected");
            holder.tvStatus.setTextColor(0xFFD32F2F);
        } else {
            holder.tvStatus.setText("Normal Call");
            holder.tvStatus.setTextColor(0xFF388E3C);
        }

        holder.btnBlock.setText(model.isBlocked() ? "Unblock" : "Block");

        holder.btnBlock.setOnClickListener(v -> {
            model.toggleBlocked();
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return callLogs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvPhone, tvDate, tvStatus;
        Button btnBlock;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnBlock = itemView.findViewById(R.id.btnBlock);
        }
    }
}
