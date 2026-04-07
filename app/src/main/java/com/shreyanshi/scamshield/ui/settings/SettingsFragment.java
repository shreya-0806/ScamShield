package com.shreyanshi.scamshield.ui.settings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.shreyanshi.scamshield.R;

public class SettingsFragment extends Fragment {

    private static final String PREF_NAME = "ScamShieldPrefs";
    private static final String KEY_SCAM_ALERTS = "scam_alerts_enabled";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";
    private static final String KEY_SOUNDS = "sounds_enabled";
    private static final String KEY_VIBRATION = "vibration_enabled";

    private SwitchCompat switchScamAlerts;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean audioGranted = Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO));
                    
                    if (!audioGranted) {
                        if (switchScamAlerts != null) {
                            switchScamAlerts.setChecked(false);
                        }
                        showPermissionDeniedDialog();
                        return;
                    }
                    
                    Toast.makeText(requireContext(), "Scam detection enabled", Toast.LENGTH_SHORT).show();
                    updatePermissionWarnings();
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        SharedPreferences prefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        switchScamAlerts = view.findViewById(R.id.switchScamAlerts);
        SwitchCompat switchDarkMode = view.findViewById(R.id.switchDarkMode);
        SwitchCompat switchSounds = view.findViewById(R.id.switchSounds);
        SwitchCompat switchVibration = view.findViewById(R.id.switchVibration);
        View permissionWarning = view.findViewById(R.id.permissionWarning);
        Button btnAppPermissions = view.findViewById(R.id.btnAppPermissions);
        Button btnHelpFeedback = view.findViewById(R.id.btnHelpFeedback);
        Button btnPrivacyConsent = view.findViewById(R.id.btnPrivacyConsent);

        updatePermissionWarnings();

        if (switchScamAlerts != null) {
            switchScamAlerts.setChecked(prefs.getBoolean(KEY_SCAM_ALERTS, true));
            switchScamAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    requestScamDetectionPermissions();
                } else {
                    prefs.edit().putBoolean(KEY_SCAM_ALERTS, false).apply();
                    Toast.makeText(requireContext(), "Scam detection disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (switchDarkMode != null) {
            switchDarkMode.setChecked(prefs.getBoolean(KEY_DARK_MODE, false));
            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
                AppCompatDelegate.setDefaultNightMode(
                        isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
                );
            });
        }

        if (switchSounds != null) {
            switchSounds.setChecked(prefs.getBoolean(KEY_SOUNDS, true));
            switchSounds.setOnCheckedChangeListener((buttonView, isChecked) ->
                    prefs.edit().putBoolean(KEY_SOUNDS, isChecked).apply()
            );
        }

        if (switchVibration != null) {
            switchVibration.setChecked(prefs.getBoolean(KEY_VIBRATION, true));
            switchVibration.setOnCheckedChangeListener((buttonView, isChecked) ->
                    prefs.edit().putBoolean(KEY_VIBRATION, isChecked).apply()
            );
        }

        if (btnAppPermissions != null) {
            btnAppPermissions.setOnClickListener(v -> openAppSettings());
        }

        if (btnHelpFeedback != null) {
            btnHelpFeedback.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:"));
                intent.putExtra(Intent.EXTRA_SUBJECT, "ScamShield Feedback");
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnPrivacyConsent != null) {
            btnPrivacyConsent.setOnClickListener(v -> showPrivacyInfo());
        }

        return view;
    }

    private void requestScamDetectionPermissions() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SCAM_ALERTS, true).apply();

        boolean hasMic = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!hasMic) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Enable Scam Detection")
                    .setMessage("ScamShield needs microphone access to listen for suspicious keywords during calls. This helps protect you from fraud calls.\n\nAudio is processed locally and never saved or uploaded.")
                    .setPositiveButton("Grant Permission", (d, w) -> {
                        permissionLauncher.launch(new String[]{
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.READ_PHONE_STATE
                        });
                    })
                    .setNegativeButton("Cancel", (d, w) -> {
                        if (switchScamAlerts != null) switchScamAlerts.setChecked(false);
                        d.dismiss();
                    })
                    .show();
        }
    }

    private void updatePermissionWarnings() {
        View view = getView();
        if (view == null) return;

        View warn = view.findViewById(R.id.permissionWarning);
        if (warn != null) {
            boolean hasMic = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            warn.setVisibility(hasMic ? View.GONE : View.VISIBLE);
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Permission Required")
                .setMessage("Microphone permission is required for scam detection. Please grant it in app settings to enable this feature.")
                .setPositiveButton("Open Settings", (d, w) -> openAppSettings())
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    private void showPrivacyInfo() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Privacy Information")
                .setMessage("ScamShield processes call audio locally on your device to detect potential scam keywords.\n\n" +
                        "• Audio is NOT recorded or saved\n" +
                        "• Audio is NOT uploaded anywhere\n" +
                        "• Keywords are detected in real-time\n" +
                        "• You can disable this anytime\n\n" +
                        "This app is designed to protect you from phone scams while respecting your privacy.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
