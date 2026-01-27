package com.shreyanshi.scamshield.ui.contacts;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContactsFragment extends Fragment {

    private ContactsAdapter adapter;
    private List<ContactModel> contactList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_contacts, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerContacts);
        SearchView searchView = view.findViewById(R.id.searchViewContacts);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        loadContacts();

        adapter = new ContactsAdapter(contactList);
        recyclerView.setAdapter(adapter);

        // Setup Search Functionality
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return false;
            }
        });

        return view;
    }

    private void loadContacts() {
        contactList.clear();
        if (getContext() != null && ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED) {

            Cursor cursor = getContext().getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null, null, null, 
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

            if (cursor != null) {
                Set<String> seenNumbers = new HashSet<>();
                while (cursor.moveToNext()) {
                    String name = cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String number = cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.Phone.NUMBER));

                    // Normalize number by keeping only digits to dedupe
                    String normalized = number.replaceAll("\\D+", "");
                    if (normalized.isEmpty() || seenNumbers.contains(normalized)) continue;
                    seenNumbers.add(normalized);

                    contactList.add(new ContactModel(name, number));
                }
                cursor.close();
            }
        }
    }
}
