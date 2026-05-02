package com.shreyanshi.scamshield.ui.history;

import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.activities.MainActivity;
import com.shreyanshi.scamshield.database.BlockedNumberDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<CallLogModel> callLogs;
    private MainActivity mainActivity;

    public HistoryAdapter(List<CallLogModel> callLogs) {
        this.callLogs = callLogs;
    }
    
    /**
     * Set the MainActivity reference for protected calls
     */
    public void setMainActivity(MainActivity activity) {
        this.mainActivity = activity;
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
        // Null check for safety
        if (callLogs == null || position >= callLogs.size()) {
            return;
        }
        
        CallLogModel model = callLogs.get(position);
        if (model == null) {
            return;
        }

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

        // FIX: Context menu with Edit/Block/Call options
        holder.itemView.setOnLongClickListener(v -> {
            showContextMenu(v, model);
            return true;
        });
        
        // Click on item also shows context menu
        holder.itemView.setOnClickListener(v -> {
            showContextMenu(v, model);
        });
        
        // FIX: Call button now uses protected call (starts service + makes call)
        holder.btnCall.setOnClickListener(v -> {
            if (mainActivity != null) {
                // Use protected call method - starts service first, then makes call
                mainActivity.startProtectedCall(model.getNumber());
            } else {
                // Fallback to regular dial
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + model.getNumber()));
                v.getContext().startActivity(intent);
            }
        });
    }
    
    /**
     * Show context menu with Edit/Block/Call options
     */
    private void showContextMenu(View anchor, CallLogModel model) {
        if (model == null) return;
        
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor, Gravity.END);
        popup.getMenuInflater().inflate(R.menu.menu_call_log_context, popup.getMenu());
        
        // Handle menu item clicks
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.action_edit) {
                // Edit Number - open dialer
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + model.getNumber()));
                anchor.getContext().startActivity(intent);
                return true;
                
            } else if (itemId == R.id.action_block) {
                // Block Number - add to blocked database
                try {
                    BlockedNumberDatabase db = new BlockedNumberDatabase(anchor.getContext());
                    db.blockNumber(model.getNumber());
                    Toast.makeText(anchor.getContext(), "Number blocked: " + model.getNumber(), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(anchor.getContext(), "Failed to block: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                return true;
                
            } else if (itemId == R.id.action_call) {
                // Call - use protected call
                if (mainActivity != null) {
                    mainActivity.startProtectedCall(model.getNumber());
                } else {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + model.getNumber()));
                    anchor.getContext().startActivity(intent);
                }
                return true;
            }
            
            return false;
        });
        
        popup.show();
    }

    @Override
    public int getItemCount() {
        return callLogs != null ? callLogs.size() : 0;
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
