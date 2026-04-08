package com.shreyanshi.scamshield.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.shreyanshi.scamshield.R;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 200;
    private static final String PREF_NAME = "ScamShieldPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyDarkModeSetting();
        
        super.onCreate(savedInstanceState);

        checkCrashLogs();
        checkAndRequestPermissions();

        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        if (savedInstanceState == null) {
            loadFragmentByName("com.shreyanshi.scamshield.ui.home.HomeFragment");
        }
        bottomNavigation.setOnItemSelectedListener(item -> loadFragmentById(item.getItemId()));
    }

    private void checkAndRequestPermissions() {
        String[] perms = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.POST_NOTIFICATIONS
        };

        boolean needsRequest = false;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needsRequest = true;
                break;
            }
        }

        if (needsRequest) {
            ActivityCompat.requestPermissions(this, perms, REQUEST_PERMISSIONS);
        }
    }

    private boolean loadFragmentById(int id) {
        String className = null;
        if (id == R.id.nav_home) className = "com.shreyanshi.scamshield.ui.home.HomeFragment";
        else if (id == R.id.nav_history) className = "com.shreyanshi.scamshield.ui.history.HistoryFragment";
        else if (id == R.id.nav_contacts) className = "com.shreyanshi.scamshield.ui.contacts.ContactsFragment";
        else if (id == R.id.nav_news) className = "com.shreyanshi.scamshield.ui.news.NewsFragment";
        else if (id == R.id.nav_settings) className = "com.shreyanshi.scamshield.ui.settings.SettingsFragment";

        if (className != null) {
            loadFragmentByName(className);
            return true;
        }
        return false;
    }

    private void loadFragmentByName(String fqcn) {
        try {
            Fragment f = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), fqcn);
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, f).commit();
        } catch (Exception e) {
            Log.e("MainActivity", "Error loading fragment: " + fqcn, e);
        }
    }

    private void applyDarkModeSetting() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean darkModeEnabled = prefs.getBoolean("dark_mode_enabled", false);
        AppCompatDelegate.setDefaultNightMode(
                darkModeEnabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    private void checkCrashLogs() {
        try {
            File dir = getExternalFilesDir("logs");
            if (dir != null) {
                File f = new File(dir, "last_crash.txt");
                if (f.exists() && f.length() > 0) {
                    try (FileInputStream fis = new FileInputStream(f)) {
                        byte[] data = new byte[(int) f.length()];
                        fis.read(data);
                        String s = new String(data, StandardCharsets.UTF_8);
                        new AlertDialog.Builder(this)
                                .setTitle("App Recovery Info")
                                .setMessage("The app restarted after a background issue. Details:\n" + s)
                                .setPositiveButton("OK", (d, w) -> f.delete())
                                .show();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                prefs.edit().putBoolean("scam_alerts_enabled", false).apply();
            }
        }
    }
}
