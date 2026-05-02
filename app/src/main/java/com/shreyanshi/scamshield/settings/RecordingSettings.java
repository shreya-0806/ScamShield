package com.shreyanshi.scamshield.settings;

import android.content.Context;
import android.content.SharedPreferences;

public class RecordingSettings {
    private static final String PREFS_NAME = "recording_settings";
    
    public static final String KEY_RECORD_ALL_CALLS = "record_all_calls";
    public static final String KEY_RECORD_UNKNOWN_ONLY = "record_unknown_only";
    public static final String KEY_SAVE_TRANSCRIPTS = "save_transcripts";
    
    private final SharedPreferences prefs;
    
    public RecordingSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public boolean isRecordAllCallsEnabled() {
        return prefs.getBoolean(KEY_RECORD_ALL_CALLS, false);
    }
    
    public boolean isRecordUnknownOnlyEnabled() {
        return prefs.getBoolean(KEY_RECORD_UNKNOWN_ONLY, true);
    }
    
    public boolean isSaveTranscriptsEnabled() {
        return prefs.getBoolean(KEY_SAVE_TRANSCRIPTS, false);
    }
    
    public void setRecordAllCalls(boolean enabled) {
        prefs.edit().putBoolean(KEY_RECORD_ALL_CALLS, enabled).apply();
    }
    
    public void setRecordUnknownOnly(boolean enabled) {
        prefs.edit().putBoolean(KEY_RECORD_UNKNOWN_ONLY, enabled).apply();
    }
    
    public void setSaveTranscripts(boolean enabled) {
        prefs.edit().putBoolean(KEY_SAVE_TRANSCRIPTS, enabled).apply();
    }
    
    public boolean shouldRecordCall(String phoneNumber, boolean isKnownContact) {
        if (isRecordAllCallsEnabled()) {
            return true;
        }
        
        if (isRecordUnknownOnlyEnabled()) {
            return !isKnownContact;
        }
        
        return false;
    }
}