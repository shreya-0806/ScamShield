package com.shreyanshi.scamshield.activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.SharedPreferences;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show crash file if present (helpful for diagnosing why the app was force-closing)
        try {
            File dir = getExternalFilesDir("logs");
            if (dir != null) {
                File f = new File(dir, "last_crash.txt");
                if (f.exists() && f.length() > 0) {
                    // try-with-resources ensures the stream is closed automatically
                    try (FileInputStream fis = new FileInputStream(f)) {
                        byte[] data = new byte[(int) f.length()];
                        int offset = 0;
                        while (offset < data.length) {
                            int r = fis.read(data, offset, data.length - offset);
                            if (r == -1) break;
                            offset += r;
                        }

                        String s = new String(data, 0, Math.max(0, offset), StandardCharsets.UTF_8);

                        // show in a dialog once
                        new AlertDialog.Builder(this)
                                .setTitle("Last crash report")
                                .setMessage(s)
                                .setPositiveButton("OK", (d, w) -> d.dismiss())
                                .show();
                    } catch (Exception ignored2) {
                        // ignore read errors
                    } finally {
                        // Attempt to delete the crash file; log if deletion failed
                        boolean deleted = f.delete();
                        if (!deleted) {
                            Log.w("MainActivity", "Failed to delete crash file: " + f.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Check permissions proactively if scam alerts are enabled (read prefs directly instead of StorageManager)
        SharedPreferences prefs = getSharedPreferences("ScamShieldPrefs", Context.MODE_PRIVATE);
        boolean scamAlerts = prefs.getBoolean("scam_alerts_enabled", true);
        if (scamAlerts) {
            ensureStartPermissions();
        }

        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);

        // Check if we are restoring from a state (e.g., theme change)
        if (savedInstanceState == null) {
            // Default screen → Home only on first launch
            loadFragmentByName("com.shreyanshi.scamshield.ui.home.HomeFragment");
        } else {
            int selectedId = bottomNavigation.getSelectedItemId();
            loadFragmentById(selectedId);
        }

        // expression lambda style
        bottomNavigation.setOnItemSelectedListener(item -> loadFragmentById(item.getItemId()));
    }

    private void ensureStartPermissions() {
        boolean needAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;
        boolean needNotifications = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;

        if (needAudio || needNotifications) {
            String[] perms = needNotifications ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS} : new String[]{Manifest.permission.RECORD_AUDIO};
            ActivityCompat.requestPermissions(this, perms, REQUEST_START_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // no-op for now; SettingsActivity handles permission rejection UX
    }

    private boolean loadFragmentById(int id) {
        String className = null;

        if (id == R.id.nav_home) {
            className = "com.shreyanshi.scamshield.ui.home.HomeFragment";
        } else if (id == R.id.nav_history) {
            className = "com.shreyanshi.scamshield.ui.history.HistoryFragment";
        } else if (id == R.id.nav_contacts) {
            className = "com.shreyanshi.scamshield.ui.contacts.ContactsFragment";
        } else if (id == R.id.nav_news) {
            className = "com.shreyanshi.scamshield.ui.news.NewsFragment";
        } else if (id == R.id.nav_settings) {
            className = "com.shreyanshi.scamshield.ui.settings.SettingsFragment";
        }

        if (className != null) {
            loadFragmentByName(className);
            return true;
        }
        return false;
    }

    private void loadFragmentByName(String fqcn) {
        Fragment fragment;
        try {
            fragment = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), fqcn);
        } catch (Exception e) {
            // Fallback: show an empty fragment to avoid crash
            fragment = new Fragment();
        }

        loadFragment(fragment);
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
