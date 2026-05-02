package com.shreyanshi.scamshield.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.settings.RecordingSettings;

public class RecordingSettingsActivity extends AppCompatActivity {
    private static final String TAG = "ScamShield-RecordingSettings";
    
    private static final int PERMISSION_REQUEST = 100;
    
    private RecordingSettings recordingSettings;
    private SwitchMaterial switchRecordAll;
    private SwitchMaterial switchRecordUnknown;
    private SwitchMaterial switchSaveTranscripts;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording_settings);
        
        recordingSettings = new RecordingSettings(this);
        
        initializeUI();
        loadSettings();
        requestPermissions();
    }
    
    private void initializeUI() {
        switchRecordAll = findViewById(R.id.switchRecordAll);
        switchRecordUnknown = findViewById(R.id.switchRecordUnknown);
        switchSaveTranscripts = findViewById(R.id.switchSaveTranscripts);
        Button btnSave = findViewById(R.id.btnSave);
        
        switchRecordAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                switchRecordUnknown.setChecked(false);
            }
        });
        
        switchRecordUnknown.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                switchRecordAll.setChecked(false);
            }
        });
        
        btnSave.setOnClickListener(v -> saveSettings());
    }
    
    private void loadSettings() {
        switchRecordAll.setChecked(recordingSettings.isRecordAllCallsEnabled());
        switchRecordUnknown.setChecked(recordingSettings.isRecordUnknownOnlyEnabled());
        switchSaveTranscripts.setChecked(recordingSettings.isSaveTranscriptsEnabled());
    }
    
    private void saveSettings() {
        recordingSettings.setRecordAllCalls(switchRecordAll.isChecked());
        recordingSettings.setRecordUnknownOnly(switchRecordUnknown.isChecked());
        recordingSettings.setSaveTranscripts(switchSaveTranscripts.isChecked());
        
        Log.i(TAG, "Settings saved - RecordAll: " + switchRecordAll.isChecked() + 
            ", RecordUnknown: " + switchRecordUnknown.isChecked() + 
            ", SaveTranscripts: " + switchSaveTranscripts.isChecked());
        
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private void requestPermissions() {
        String[] permissions;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 14+ permissions
            permissions = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-13 permissions
            permissions = new String[]{
                Manifest.permission.READ_CONTACTS
            };
        } else {
            // Android 9 and below
            permissions = new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        
        boolean needsPermission = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                needsPermission = true;
                break;
            }
        }
        
        if (needsPermission) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "✅ Permission granted: " + permissions[i]);
                } else {
                    Log.w(TAG, "❌ Permission denied: " + permissions[i]);
                }
            }
        }
    }
}