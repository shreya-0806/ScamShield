package com.shreyanshi.scamshield.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;

import java.util.ArrayList;
import java.util.List;

public class ContactSuggestionAdapter extends RecyclerView.Adapter<ContactSuggestionAdapter.ViewHolder> {

    private List<ContactInfo> contacts = new ArrayList<>();
    private OnContactClickListener listener;

    public interface OnContactClickListener {
        void onContactClick(String number);
    }

    public void setListener(OnContactClickListener listener) {
        this.listener = listener;
    }

    public void updateContacts(List<ContactInfo> newContacts) {
        contacts.clear();
        if (newContacts != null) {
            contacts.addAll(newContacts);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContactInfo contact = contacts.get(position);
        holder.tvName.setText(contact.name);
        holder.tvNumber.setText(contact.number);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onContactClick(contact.number);
            }
        });

        holder.btnQuickCall.setOnClickListener(v -> {
            if (listener != null) {
                listener.onContactClick(contact.number);
            }
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNumber;
        ImageButton btnQuickCall;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvSuggestionName);
            tvNumber = itemView.findViewById(R.id.tvSuggestionNumber);
            btnQuickCall = itemView.findViewById(R.id.btnQuickCall);
        }
    }

    public static class ContactInfo {
        public String name;
        public String number;

        public ContactInfo(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }
}
