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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.shreyanshi.scamshield.R;

import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends Fragment {

    private SwitchCompat switchScamAlerts;

    private ActivityResultLauncher<String[]> permissionLauncher;

    // SharedPreferences keys matching StorageManager
    private static final String PREF_NAME = "ScamShieldPrefs";
    private static final String KEY_SCAM_ALERTS = "scam_alerts_enabled";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";
    private static final String KEY_SOUNDS = "sounds_enabled";
    private static final String KEY_VIBRATION = "vibration_enabled";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register the permission launcher before the Fragment is created
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean audioGranted = Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO));
            boolean notificationsGranted = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationsGranted = Boolean.TRUE.equals(result.get(Manifest.permission.POST_NOTIFICATIONS));
            }

            SharedPreferences prefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

            if (!audioGranted) {
                prefs.edit().putBoolean(KEY_SCAM_ALERTS, false).apply();
                if (switchScamAlerts != null) switchScamAlerts.setChecked(false);

                new AlertDialog.Builder(requireContext())
                        .setTitle("Permission required")
                        .setMessage("Microphone permission is required for live scam detection. Please grant it in App Settings to enable this feature.")
                        .setPositiveButton("Open Settings", (d, w) -> openAppSettings())
                        .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                        .show();

                View v = getView();
                if (v != null) updatePermissionWarning(v);
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Notifications disabled")
                        .setMessage("Notification permission is required to show scam alerts. Please grant it in App Settings for timely alerts.")
                        .setPositiveButton("Open Settings", (d, w) -> openAppSettings())
                        .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                        .show();
            }

            // audioGranted is true here (we returned earlier if it wasn't), show confirmation
            Toast.makeText(requireContext(), "Scam detection enabled", Toast.LENGTH_SHORT).show();

            View v2 = getView();
            if (v2 != null) updatePermissionWarning(v2);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        SharedPreferences prefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Use direct resource IDs
        switchScamAlerts = view.findViewById(R.id.switchScamAlerts);
        SwitchCompat switchDarkMode = view.findViewById(R.id.switchDarkMode);
        SwitchCompat switchSounds = view.findViewById(R.id.switchSounds);
        SwitchCompat switchVibration = view.findViewById(R.id.switchVibration);
        Button btnAppPermissions = view.findViewById(R.id.btnAppPermissions);
        Button btnHelpFeedback = view.findViewById(R.id.btnHelpFeedback);
        Button btnPrivacyConsent = view.findViewById(R.id.btnPrivacyConsent);

        // Permission warning visibility
        updatePermissionWarning(view);

        // Load saved states from prefs
        if (switchScamAlerts != null) switchScamAlerts.setChecked(prefs.getBoolean(KEY_SCAM_ALERTS, true));
        if (switchDarkMode != null) switchDarkMode.setChecked(prefs.getBoolean(KEY_DARK_MODE, false));
        if (switchSounds != null) switchSounds.setChecked(prefs.getBoolean(KEY_SOUNDS, true));
        if (switchVibration != null) switchVibration.setChecked(prefs.getBoolean(KEY_VIBRATION, true));

        if (switchScamAlerts != null) {
            switchScamAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // require consent before enabling live detection
                    boolean consent = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .getBoolean("user_consent_given", false);
                    if (!consent) {
                        // show consent dialog
                        showConsentDialog();
                        // revert toggle
                        if (switchScamAlerts != null) switchScamAlerts.setChecked(false);
                        return;
                    }
                    // Show rationale if needed before requesting
                    if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.RECORD_AUDIO)) {
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Why we need microphone")
                                .setMessage("Scam detection listens for suspicious keywords during calls to warn you in real time. Please allow microphone access to enable this feature.")
                                .setPositiveButton("Continue", (d, w) -> requestPermissionsForScamDetection())
                                .setNegativeButton("Cancel", (d, w) -> {
                                    if (switchScamAlerts != null) switchScamAlerts.setChecked(false);
                                    d.dismiss();
                                })
                                .show();
                    } else {
                        requestPermissionsForScamDetection();
                    }

                    // tentatively enable
                    prefs.edit().putBoolean(KEY_SCAM_ALERTS, true).apply();
                } else {
                    prefs.edit().putBoolean(KEY_SCAM_ALERTS, false).apply();
                }
            });
        }

        if (switchDarkMode != null) {
            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
                AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            });
        }

        if (switchSounds != null) switchSounds.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(KEY_SOUNDS, checked).apply());
        if (switchVibration != null) switchVibration.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(KEY_VIBRATION, checked).apply());

        if (btnAppPermissions != null) btnAppPermissions.setOnClickListener(v -> openAppSettings());

        if (btnHelpFeedback != null) btnHelpFeedback.setOnClickListener(v -> {
            // Open email composer for feedback
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:")); // only email apps should handle this
            intent.putExtra(Intent.EXTRA_SUBJECT, "ScamShield Feedback");
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnPrivacyConsent != null) btnPrivacyConsent.setOnClickListener(v -> showConsentDialog());

        return view;
    }

    private void updatePermissionWarning(View root) {
        boolean micGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        View warn = root.findViewById(R.id.permissionWarning);
        if (warn != null) warn.setVisibility(micGranted ? View.GONE : View.VISIBLE);
    }

    private void requestPermissionsForScamDetection() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!needed.isEmpty()) {
            // use ActivityResultLauncher instead of deprecated requestPermissions
            permissionLauncher.launch(needed.toArray(new String[0]));
        } else {
            Toast.makeText(requireContext(), "Permissions already granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void showConsentDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.consent_title)
                .setMessage(R.string.consent_message)
                .setPositiveButton(R.string.consent_accept, (d, w) -> {
                    // store consent
                    requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit().putBoolean("user_consent_given", true).apply();
                    Toast.makeText(requireContext(), "Consent saved", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.view_privacy_policy, (d, w) -> {
                    // open privacy policy URL (if available)
                    Intent in = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/privacy"));
                    try { startActivity(in); } catch (Exception ignored) {}
                })
                .setNegativeButton(R.string.consent_decline, (d, w) -> {
                    requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit().putBoolean("user_consent_given", false).apply();
                    d.dismiss();
                })
                .show();
    }
}
