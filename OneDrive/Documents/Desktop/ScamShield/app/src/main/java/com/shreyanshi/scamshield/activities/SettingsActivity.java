package com.shreyanshi.scamshield.activities;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.utils.StorageManager;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private StorageManager storageManager;
    private static final int REQUEST_PERMISSIONS = 101;
    private SwitchCompat switchScamAlerts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        storageManager = new StorageManager(this);

        switchScamAlerts = findViewById(R.id.switchScamAlerts);
        SwitchCompat switchDarkMode = findViewById(R.id.switchDarkMode);
        SwitchCompat switchSounds = findViewById(R.id.switchSounds);
        SwitchCompat switchVibration = findViewById(R.id.switchVibration);
        findViewById(R.id.btnAppPermissions).setOnClickListener(v -> openAppSettings());

        // Show the permission warning if mic permission missing
        updatePermissionWarning();

        // Load saved states
        switchScamAlerts.setChecked(storageManager.isScamAlertsEnabled());
        switchDarkMode.setChecked(storageManager.isDarkModeEnabled());
        switchSounds.setChecked(storageManager.isSoundsEnabled());
        switchVibration.setChecked(storageManager.isVibrationEnabled());

        // Listen for changes
        switchScamAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Show rationale if needed before requesting
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Why we need microphone")
                            .setMessage("Scam detection listens for suspicious keywords during calls to warn you in real time. Please allow microphone access to enable this feature.")
                            .setPositiveButton("Continue", (d, w) -> {
                                requestPermissionsForScamDetection();
                            })
                            .setNegativeButton("Cancel", (d, w) -> {
                                if (switchScamAlerts != null) switchScamAlerts.setChecked(false);
                                d.dismiss();
                            })
                            .show();
                } else {
                    requestPermissionsForScamDetection();
                }

                // We'll tentatively enable the flag; if permission is denied we'll revert below.
                storageManager.setScamAlertsEnabled(true);
            } else {
                storageManager.setScamAlertsEnabled(false);
            }
        });

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            storageManager.setDarkModeEnabled(isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        switchSounds.setOnCheckedChangeListener((b, checked) -> storageManager.setSoundsEnabled(checked));
        switchVibration.setOnCheckedChangeListener((b, checked) -> storageManager.setVibrationEnabled(checked));
    }

    private void updatePermissionWarning() {
        boolean micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        findViewById(R.id.permissionWarning).setVisibility(micGranted ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void requestPermissionsForScamDetection() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_PERMISSIONS);
        } else {
            Toast.makeText(this, "Permissions already granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean audioGranted = true;
            boolean notificationsGranted = true;

            for (int i = 0; i < permissions.length; i++) {
                String p = permissions[i];
                int r = grantResults[i];
                if (Manifest.permission.RECORD_AUDIO.equals(p)) {
                    audioGranted = (r == PackageManager.PERMISSION_GRANTED);
                } else if (Manifest.permission.POST_NOTIFICATIONS.equals(p)) {
                    notificationsGranted = (r == PackageManager.PERMISSION_GRANTED);
                }
            }

            if (!audioGranted) {
                // If user denied audio permission, disable scam alerts and inform
                storageManager.setScamAlertsEnabled(false);
                if (switchScamAlerts != null) switchScamAlerts.setChecked(false);

                new AlertDialog.Builder(this)
                        .setTitle("Permission required")
                        .setMessage("Microphone permission is required for live scam detection. Please grant it in App Settings to enable this feature.")
                        .setPositiveButton("Open Settings", (d, w) -> openAppSettings())
                        .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                        .show();

                updatePermissionWarning();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                // Notify user that notifications are disabled which may prevent alerts appearing
                new AlertDialog.Builder(this)
                        .setTitle("Notifications disabled")
                        .setMessage("Notification permission is required to show scam alerts. Please grant it in App Settings for timely alerts.")
                        .setPositiveButton("Open Settings", (d, w) -> openAppSettings())
                        .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                        .show();
            }

            if (audioGranted) {
                Toast.makeText(this, "Scam detection enabled", Toast.LENGTH_SHORT).show();
            }

            updatePermissionWarning();
        }
    }
}
