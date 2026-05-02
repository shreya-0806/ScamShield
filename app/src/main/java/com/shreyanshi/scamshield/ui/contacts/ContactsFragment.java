package com.shreyanshi.scamshield.ui.contacts;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.activities.MainActivity;
import com.shreyanshi.scamshield.database.BlockedNumberDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContactsFragment extends Fragment {

    private static final String TAG = "ContactsFragment";
    private ContactsAdapter adapter;
    private List<ContactModel> contactList = new ArrayList<>();
    private BlockedNumberDatabase blockedDb;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_contacts, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerContacts);
        SearchView searchView = view.findViewById(R.id.searchViewContacts);
        tvEmpty = view.findViewById(R.id.tvContactsEmpty);
        
        try {
            blockedDb = new BlockedNumberDatabase(requireContext());
        } catch (Exception e) {
            Log.e(TAG, "Error creating blockedDb", e);
        }
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        loadContacts();

        try {
            adapter = new ContactsAdapter(contactList, blockedDb);
            // FIX: Pass MainActivity reference for protected calls
            if (getActivity() instanceof MainActivity) {
                adapter.setMainActivity((MainActivity) getActivity());
            }
            recyclerView.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "Error creating adapter", e);
        }

        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    if (adapter != null) adapter.filter(query);
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    if (adapter != null) adapter.filter(newText);
                    return false;
                }
            });
        }

        return view;
    }

    private void loadContacts() {
        contactList.clear();
        if (getContext() == null) return;
        
        if (ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.READ_CONTACTS
        ) != PackageManager.PERMISSION_GRANTED) {
            if (tvEmpty != null) {
                tvEmpty.setText("Permission required to view contacts");
                tvEmpty.setVisibility(View.VISIBLE);
            }
            return;
        }

        try {
            Cursor cursor = getContext().getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null, null, null, 
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

            if (cursor != null) {
                Set<String> seenNumbers = new HashSet<>();
                while (cursor.moveToNext()) {
                    try {
                        String name = cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                        String number = cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        ContactsContract.CommonDataKinds.Phone.NUMBER));

                        if (name == null || name.isEmpty()) {
                            name = number;
                        }

                        String normalized = number.replaceAll("\\D+", "");
                        if (normalized.isEmpty() || seenNumbers.contains(normalized)) continue;
                        seenNumbers.add(normalized);

                        ContactModel contact = new ContactModel(name, number);
                        if (blockedDb != null) {
                            contact.setBlocked(blockedDb.isBlocked(number));
                        }
                        contactList.add(contact);
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading contact", e);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading contacts", e);
        }

        if (contactList.isEmpty()) {
            if (tvEmpty != null) {
                tvEmpty.setText("No contacts found");
                tvEmpty.setVisibility(View.VISIBLE);
            }
        } else {
            if (tvEmpty != null) {
                tvEmpty.setVisibility(View.GONE);
            }
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (blockedDb != null) {
            blockedDb.close();
            blockedDb = null;
        }
    }
}
