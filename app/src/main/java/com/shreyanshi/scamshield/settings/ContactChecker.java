package com.shreyanshi.scamshield.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.core.content.ContextCompat;
import android.Manifest.permission;

public class ContactChecker {
    private static final String TAG = "ScamShield-ContactCheck";
    
    private final Context context;
    
    public ContactChecker(Context context) {
        this.context = context;
    }
    
    public boolean isKnownContact(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        
        if (ContextCompat.checkSelfPermission(context, permission.READ_CONTACTS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted");
            return false;
        }
        
        try {
            String normalizedNumber = normalizePhoneNumber(phoneNumber);
            
            ContentResolver resolver = context.getContentResolver();
            Uri contactUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, 
                Uri.encode(normalizedNumber)
            );
            
            String[] projection = { ContactsContract.PhoneLookup._ID };
            
            try (Cursor cursor = resolver.query(contactUri, projection, null, null, null)) {
                if (cursor != null && cursor.getCount() > 0) {
                    Log.i(TAG, "✅ Known contact found: " + phoneNumber);
                    return true;
                }
            }
            
            Log.d(TAG, "Unknown contact: " + phoneNumber);
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking contact: " + e.getMessage());
            return false;
        }
    }
    
    private String normalizePhoneNumber(String number) {
        if (number == null) return "";
        
        String digits = number.replaceAll("[^0-9+]", "");
        
        if (digits.startsWith("+91") && digits.length() > 12) {
            digits = digits.substring(3);
        } else if (digits.startsWith("91") && digits.length() > 11) {
            digits = digits.substring(2);
        } else if (digits.length() == 10 && !digits.startsWith("+")) {
            // Already normalized 10-digit
        }
        
        return digits;
    }
}