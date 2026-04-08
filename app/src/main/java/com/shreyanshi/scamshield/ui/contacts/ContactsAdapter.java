package com.shreyanshi.scamshield.ui.contacts;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.database.BlockedNumberDatabase;

import java.util.ArrayList;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder> {

    private List<ContactModel> contacts;
    private List<ContactModel> contactsFull;
    private BlockedNumberDatabase blockedDb;

    public ContactsAdapter(List<ContactModel> contacts, BlockedNumberDatabase blockedDb) {
        this.contacts = contacts;
        this.contactsFull = new ArrayList<>(contacts);
        this.blockedDb = blockedDb;
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

        if (model.isFavorite()) {
            holder.btnStar.setImageResource(android.R.drawable.btn_star_big_on);
            holder.btnStar.setColorFilter(0xFFFFD700);
        } else {
            holder.btnStar.setImageResource(android.R.drawable.btn_star_big_off);
            holder.btnStar.setColorFilter(0xFF757575);
        }

        if (model.isBlocked()) {
            holder.btnBlock.setImageResource(R.drawable.ic_blocked);
            holder.btnBlock.setColorFilter(0xFFF44336);
        } else {
            holder.btnBlock.setImageResource(R.drawable.ic_block);
            holder.btnBlock.setColorFilter(0xFF757575);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + model.getNumber()));
            v.getContext().startActivity(intent);
        });

        holder.btnEdit.setVisibility(View.GONE);

        holder.btnBlock.setOnClickListener(v -> {
            boolean currentlyBlocked = model.isBlocked();
            if (currentlyBlocked) {
                blockedDb.unblockNumber(model.getNumber());
                model.setBlocked(false);
                holder.btnBlock.setImageResource(R.drawable.ic_block);
                holder.btnBlock.setColorFilter(0xFF757575);
                Toast.makeText(v.getContext(), model.getName() + " unblocked", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialog.Builder(v.getContext())
                    .setTitle("Block Contact")
                    .setMessage("Block " + model.getName() + "? You will not receive calls from this number.")
                    .setPositiveButton("Block", (d, w) -> {
                        blockedDb.blockNumber(model.getNumber());
                        model.setBlocked(true);
                        holder.btnBlock.setImageResource(R.drawable.ic_blocked);
                        holder.btnBlock.setColorFilter(0xFFF44336);
                        Toast.makeText(v.getContext(), model.getName() + " blocked", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        });

        holder.btnStar.setOnClickListener(v -> {
            boolean newState = !model.isFavorite();
            model.setFavorite(newState);
            
            for (ContactModel m : contactsFull) {
                if (m.getNumber().equals(model.getNumber())) {
                    m.setFavorite(newState);
                    break;
                }
            }
            
            if (newState) {
                holder.btnStar.setImageResource(android.R.drawable.btn_star_big_on);
                holder.btnStar.setColorFilter(0xFFFFD700);
            } else {
                holder.btnStar.setImageResource(android.R.drawable.btn_star_big_off);
                holder.btnStar.setColorFilter(0xFF757575);
            }
            Toast.makeText(v.getContext(), newState ? "Added to favorites" : "Removed from favorites", Toast.LENGTH_SHORT).show();
        });

        holder.btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(v.getContext())
                .setTitle("Delete Contact")
                .setMessage("Delete " + model.getName() + " from call history?")
                .setPositiveButton("Delete", (d, w) -> {
                    contacts.remove(position);
                    contactsFull.remove(model);
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, contacts.size());
                    Toast.makeText(v.getContext(), "Contact deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        ImageButton btnDelete;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvNumber = itemView.findViewById(R.id.tvNumber);
            tvInitial = itemView.findViewById(R.id.tvInitial);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnBlock = itemView.findViewById(R.id.btnBlock);
            btnStar = itemView.findViewById(R.id.btnStar);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
