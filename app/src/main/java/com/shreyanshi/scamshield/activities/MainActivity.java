package com.shreyanshi.scamshield.activities;

import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.shreyanshi.scamshield.R;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_START_PERMISSIONS = 200;
    private static final int REQUEST_ID_DEFAULT_DIALER = 201;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check crash logs
        checkCrashLogs();

        // Check permissions
        SharedPreferences prefs = getSharedPreferences("ScamShieldPrefs", Context.MODE_PRIVATE);
        boolean scamAlerts = prefs.getBoolean("scam_alerts_enabled", true);
        if (scamAlerts) {
            ensureStartPermissions();
            requestDefaultDialerRole();
        }

        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        if (savedInstanceState == null) {
            loadFragmentByName("com.shreyanshi.scamshield.ui.home.HomeFragment");
        }
        bottomNavigation.setOnItemSelectedListener(item -> loadFragmentById(item.getItemId()));
    }

    private void requestDefaultDialerRole() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                startActivityForResult(intent, REQUEST_ID_DEFAULT_DIALER);
            }
        } else {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager != null && !getPackageName().equals(telecomManager.getDefaultDialerPackage())) {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                startActivity(intent);
            }
        }
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
                                .setMessage("The app restarted after a background issue. To prevent this, please ensure 'Battery Optimization' is off for ScamShield.\n\nDetails:\n" + s)
                                .setPositiveButton("OK", (d, w) -> f.delete())
                                .show();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void ensureStartPermissions() {
        String[] perms = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG
        };
        
        boolean needsRequest = false;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needsRequest = true;
                break;
            }
        }

        if (needsRequest) {
            ActivityCompat.requestPermissions(this, perms, REQUEST_START_PERMISSIONS);
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
        } catch (Exception ignored) {}
    }
}