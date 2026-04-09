package com.shreyanshi.scamshield.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.database.ScamDatabaseHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final String PREF_NAME = "ScamShieldPrefs";
    private TextView tvNumber;
    private final StringBuilder numberBuilder = new StringBuilder();
    private ScamDatabaseHelper dbHelper;
    private RecyclerView rvSuggestions;
    private ContactSuggestionAdapter suggestionAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        dbHelper = new ScamDatabaseHelper(getContext());
        tvNumber = view.findViewById(R.id.tvNumber);
        rvSuggestions = view.findViewById(R.id.rvContactSuggestions);
        
        setupContactSuggestions(view);

        ImageButton btnBackspace = view.findViewById(R.id.btnBackspace);
        FloatingActionButton btnCall = view.findViewById(R.id.btnCall);

        setDigitClickListener(view, R.id.btn0, "0");
        setDigitClickListener(view, R.id.btn1, "1");
        setDigitClickListener(view, R.id.btn2, "2");
        setDigitClickListener(view, R.id.btn3, "3");
        setDigitClickListener(view, R.id.btn4, "4");
        setDigitClickListener(view, R.id.btn5, "5");
        setDigitClickListener(view, R.id.btn6, "6");
        setDigitClickListener(view, R.id.btn7, "7");
        setDigitClickListener(view, R.id.btn8, "8");
        setDigitClickListener(view, R.id.btn9, "9");
        setDigitClickListener(view, R.id.btnStar, "*");
        setDigitClickListener(view, R.id.btnHash, "#");

        btnBackspace.setOnClickListener(v -> {
            if (numberBuilder.length() > 0) {
                numberBuilder.deleteCharAt(numberBuilder.length() - 1);
                updateDisplay();
            }
        });

        btnCall.setOnClickListener(v -> {
            String number = numberBuilder.toString();
            if (!TextUtils.isEmpty(number)) {
                makeCall(number);
            } else {
                Toast.makeText(getContext(), "Please enter a number", Toast.LENGTH_SHORT).show();
            }
        });

        tvNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                if (query.length() >= 2) {
                    searchContacts(query);
                } else {
                    rvSuggestions.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        return view;
    }

    private void setupContactSuggestions(View view) {
        suggestionAdapter = new ContactSuggestionAdapter();
        rvSuggestions.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSuggestions.setAdapter(suggestionAdapter);
        
        suggestionAdapter.setListener(number -> {
            numberBuilder.setLength(0);
            numberBuilder.append(number);
            tvNumber.setText(number);
            rvSuggestions.setVisibility(View.GONE);
            makeCall(number);
        });
    }

    private void searchContacts(String query) {
        if (getContext() == null) return;
        
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS) 
                != PackageManager.PERMISSION_GRANTED) {
            rvSuggestions.setVisibility(View.GONE);
            return;
        }

        List<ContactSuggestionAdapter.ContactInfo> suggestions = new ArrayList<>();
        String normalizedQuery = query.replaceAll("\\D+", "");
        
        try {
            Cursor cursor = getContext().getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ? OR " 
                    + ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?",
                    new String[]{"%" + query + "%", "%" + normalizedQuery + "%"},
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC");

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < 5) {
                    try {
                        String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                        String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                        if (name == null || name.isEmpty()) {
                            name = number;
                        }
                        suggestions.add(new ContactSuggestionAdapter.ContactInfo(name, number));
                        count++;
                    } catch (Exception e) {
                        // Skip malformed contacts
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (suggestions.isEmpty()) {
            rvSuggestions.setVisibility(View.GONE);
        } else {
            suggestionAdapter.updateContacts(suggestions);
            rvSuggestions.setVisibility(View.VISIBLE);
        }
    }

    private void setDigitClickListener(View parent, int id, String digit) {
        View v = parent.findViewById(id);
        if (v != null) v.setOnClickListener(v1 -> {
            numberBuilder.append(digit);
            updateDisplay();
        });
    }

    private void updateDisplay() {
        tvNumber.setText(numberBuilder.toString());
    }

    private void makeCall(String number) {
        String currentDateTime = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
        dbHelper.insertCallLog(number, currentDateTime, false);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + Uri.encode(number)));
            startActivity(callIntent);
        } else {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(Uri.parse("tel:" + Uri.encode(number)));
            startActivity(dialIntent);
        }
    }
}
