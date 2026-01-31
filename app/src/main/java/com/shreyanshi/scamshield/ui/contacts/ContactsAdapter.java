package com.shreyanshi.scamshield.ui.contacts;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;

import java.util.ArrayList;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder> {

    private List<ContactModel> contacts;
    private List<ContactModel> contactsFull;

    public ContactsAdapter(List<ContactModel> contacts) {
        this.contacts = contacts;
        this.contactsFull = new ArrayList<>(contacts);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContactModel model = contacts.get(position);

        holder.tvName.setText(model.getName());
        holder.tvNumber.setText(model.getNumber());

        if (model.getName() != null && !model.getName().isEmpty()) {
            holder.tvInitial.setText(model.getName().substring(0, 1).toUpperCase());
        } else {
            holder.tvInitial.setText("?");
        }

        // Toggle Favorite Icon
        if (model.isFavorite()) {
            holder.btnStar.setImageResource(android.R.drawable.btn_star_big_on);
        } else {
            holder.btnStar.setImageResource(android.R.drawable.btn_star_big_off);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + model.getNumber()));
            v.getContext().startActivity(intent);
        });

        holder.btnEdit.setOnClickListener(v -> {
            long contactId = getContactId(v.getContext(), model.getNumber());
            if (contactId != -1) {
                Intent intent = new Intent(Intent.ACTION_EDIT);
                Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
                intent.setData(contactUri);
                v.getContext().startActivity(intent);
            } else {
                Toast.makeText(v.getContext(), "Contact not found in phonebook", Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnBlock.setOnClickListener(v -> {
            Toast.makeText(v.getContext(), model.getName() + " added to block list", Toast.LENGTH_SHORT).show();
        });

        holder.btnStar.setOnClickListener(v -> {
            boolean newState = !model.isFavorite();
            model.setFavorite(newState);
            
            // Also update in contactsFull if they are different references
            for (ContactModel m : contactsFull) {
                if (m.getNumber().equals(model.getNumber())) {
                    m.setFavorite(newState);
                    break;
                }
            }
            
            notifyItemChanged(position);
            Toast.makeText(v.getContext(), newState ? "Marked as Favorite" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
        });
    }

    private long getContactId(Context context, String number) {
        Uri contactUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        Cursor cursor = context.getContentResolver().query(contactUri, new String[]{ContactsContract.PhoneLookup._ID}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                cursor.close();
                return id;
            }
            cursor.close();
        }
        return -1;
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    public void filter(String text) {
        contacts.clear();
        if (text == null || text.isEmpty()) {
            contacts.addAll(contactsFull);
        } else {
            String query = text.toLowerCase().trim();
            for (ContactModel item : contactsFull) {
                String name = item.getName() != null ? item.getName().toLowerCase() : "";
                String number = item.getNumber() != null ? item.getNumber() : "";
                if (name.contains(query) || number.contains(query)) {
                    contacts.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNumber, tvInitial;
        ImageView btnEdit, btnBlock, btnStar;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvNumber = itemView.findViewById(R.id.tvNumber);
            tvInitial = itemView.findViewById(R.id.tvInitial);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnBlock = itemView.findViewById(R.id.btnBlock);
            btnStar = itemView.findViewById(R.id.btnStar);
        }
    }
}
