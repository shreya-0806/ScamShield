package com.shreyanshi.scamshield.ui.history;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

        holder.tvName.setText(model.getName() != null ? model.getName() : model.getNumber());
        holder.tvNumber.setText(model.getNumber());
        
        String dateTime = model.getDateTime();
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            Date date = inputFormat.parse(dateTime);
            if (date != null) {
                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                holder.tvDate.setText(timeFormat.format(date));
            } else {
                holder.tvDate.setText(dateTime);
            }
        } catch (Exception e) {
            holder.tvDate.setText(dateTime);
        }

        int iconRes;
        int iconTint;
        switch (model.getCallType()) {
            case CallLogModel.TYPE_OUTGOING:
                iconRes = android.R.drawable.sym_call_outgoing;
                iconTint = 0xFF4CAF50;
                break;
            case CallLogModel.TYPE_INCOMING:
                iconRes = android.R.drawable.sym_call_incoming;
                iconTint = 0xFF4CAF50;
                break;
            default:
                iconRes = android.R.drawable.sym_call_missed;
                iconTint = 0xFFF44336;
                break;
        }
        
        holder.ivCallType.setImageResource(iconRes);
        holder.ivCallType.setColorFilter(iconTint);

        if (model.isScam()) {
            holder.tvScamIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.tvScamIndicator.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + model.getNumber()));
            v.getContext().startActivity(intent);
        });
        
        holder.btnCall.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + model.getNumber()));
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return callLogs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNumber, tvDate, tvScamIndicator;
        ImageView ivCallType;
        ImageButton btnCall;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCallName);
            tvNumber = itemView.findViewById(R.id.tvCallNumber);
            tvDate = itemView.findViewById(R.id.tvCallDate);
            tvScamIndicator = itemView.findViewById(R.id.tvScamIndicator);
            ivCallType = itemView.findViewById(R.id.ivCallType);
            btnCall = itemView.findViewById(R.id.btnCallHistory);
        }
    }
}
