package com.shreyanshi.scamshield.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class StorageManager {
    private static final String PREF_NAME = "ScamShieldPrefs";
    private static final String KEY_SCAM_ALERTS = "scam_alerts_enabled";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";
    private static final String KEY_SOUNDS = "sounds_enabled";
    private static final String KEY_VIBRATION = "vibration_enabled";

    private final SharedPreferences sharedPreferences;

    public StorageManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setScamAlertsEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_SCAM_ALERTS, enabled).apply();
    }

    public boolean isScamAlertsEnabled() {
        return sharedPreferences.getBoolean(KEY_SCAM_ALERTS, true);
    }

    public void setDarkModeEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }

    public boolean isDarkModeEnabled() {
        return sharedPreferences.getBoolean(KEY_DARK_MODE, false);
    }

    // Sounds setting
    public void setSoundsEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_SOUNDS, enabled).apply();
    }

    public boolean isSoundsEnabled() {
        return sharedPreferences.getBoolean(KEY_SOUNDS, true);
    }

    // Vibration setting
    public void setVibrationEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_VIBRATION, enabled).apply();
    }

    public boolean isVibrationEnabled() {
        return sharedPreferences.getBoolean(KEY_VIBRATION, true);
    }
}
