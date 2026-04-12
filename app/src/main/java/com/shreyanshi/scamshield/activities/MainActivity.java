package com.shreyanshi.scamshield.activities;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.stt.SpeechListener;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SpeechListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 200;
    private static final int REQUEST_ROLE_DIALER = 101;
    private static final String PREF_NAME = "ScamShieldPrefs";
    private static final String PREF_DIALER_ROLE_REQUESTED = "dialer_role_requested";
    
    private static MainActivity instance;
    
    // Internal Debug Terminal UI components
    private TextView internalDebugLog;
    private ScrollView debugScrollView;
    private LocalBroadcastManager localBroadcastManager;
    private static final int MAX_DEBUG_LINES = 50;
    
    // LocalBroadcast receiver for debug events from ScamMonitorService
    private final android.content.BroadcastReceiver debugReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (intent != null && "com.shreyanshi.scamshield.DEBUG_LOG".equals(intent.getAction())) {
                String message = intent.getStringExtra("log_message");
                if (message != null) {
                    appendLog(message);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkCrashLogs();
        checkAndRequestPermissions();
        requestDefaultDialerRole();

        setContentView(R.layout.activity_main);
        
        // Initialize internal debug terminal FIRST (before other UI setup)
        initializeInternalDebugTerminal();

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        if (savedInstanceState == null) {
            loadFragmentByName("com.shreyanshi.scamshield.ui.home.HomeFragment");
        }
        bottomNavigation.setOnItemSelectedListener(item -> loadFragmentById(item.getItemId()));
    }
    
    /**
     * Initialize Internal Debug Terminal UI
     * 
     * Sets up TextView and ScrollView for displaying real-time logs.
     * Logs appear in green (#00FF00) text on black (#000000) background.
     * Format: [HH:mm:ss] message
     * 
     * Registers LocalBroadcast receiver to listen for debug events from
     * ScamMonitorService and GoogleSpeechRecognizer.
     * 
     * AGENTS.md Reference:
     * - Line 37: Use dark theme for consistent UI
     * - Line 1614: Use SimpleDateFormat for debug log timestamps
     * - Line 1631: Always use handler.post() for UI updates from background threads
     */
    private void initializeInternalDebugTerminal() {
        try {
            internalDebugLog = findViewById(R.id.internal_debug_log);
            debugScrollView = findViewById(R.id.debug_scroll_view);
            
            if (internalDebugLog == null || debugScrollView == null) {
                Log.w(TAG, "⚠️ Debug terminal UI elements not found in layout");
                return;
            }
            
            // Initialize LocalBroadcastManager
            localBroadcastManager = LocalBroadcastManager.getInstance(this);
            
            // Register receiver for debug log broadcasts
            android.content.IntentFilter filter = new android.content.IntentFilter("com.shreyanshi.scamshield.DEBUG_LOG");
            localBroadcastManager.registerReceiver(debugReceiver, filter);
            
            appendLog("✅ Internal Debug Terminal initialized");
            appendLog("🔍 Waiting for speech recognition events...");
            
            Log.i(TAG, "✅ Debug terminal ready (waiting for SpeechRecognizer events)");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing debug terminal: " + e.getMessage());
        }
    }
    
    /**
     * Append timestamped message to internal debug terminal
     * 
     * CRITICAL: Uses handler.post() to ensure main thread execution
     * (AGENTS.md line 1631: DON'T append to TextViews without handler.post())
     * 
     * Features:
     * - Adds [HH:mm:ss] timestamp to each message
     * - Auto-scrolls to show latest entries
     * - Limits to 50 lines to prevent memory bloat (AGENTS.md line 1615)
     * - Uses monospace font for alignment
     * 
     * @param message The log message to display (can include emoji prefixes)
     */
    public void appendLog(String message) {
        if (internalDebugLog == null) {
            return;
        }
        
        // Use Handler to ensure UI updates on main thread (critical for safety)
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Format timestamp: [HH:mm:ss]
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
                String timestamp = "[" + sdf.format(new Date()) + "] ";
                String logEntry = timestamp + message + "\n";
                
                // Get current text and append new entry
                String currentText = internalDebugLog.getText().toString();
                String newText = currentText + logEntry;
                
                // Limit to MAX_DEBUG_LINES to prevent memory bloat
                String[] lines = newText.split("\n");
                if (lines.length > MAX_DEBUG_LINES) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = lines.length - MAX_DEBUG_LINES; i < lines.length; i++) {
                        if (i > lines.length - MAX_DEBUG_LINES && i > 0) {
                            sb.append("\n");
                        }
                        if (i < lines.length) {
                            sb.append(lines[i]);
                        }
                    }
                    newText = sb.toString();
                }
                
                // Update TextView with new text
                internalDebugLog.setText(newText);
                
                // Auto-scroll to bottom to show latest entry
                if (debugScrollView != null) {
                    debugScrollView.post(() -> debugScrollView.fullScroll(ScrollView.FOCUS_DOWN));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error appending log: " + e.getMessage());
            }
        });
    }
    
    /**
     * Static method to send debug log message from ScamMonitorService
     * 
     * Call from ScamMonitorService via: MainActivity.logToTerminal("message")
     * This method safely checks if MainActivity instance exists before appending.
     * 
     * @param message The debug message to display in terminal
     */
    public static void logToTerminal(String message) {
        if (instance != null) {
            instance.appendLog(message);
        }
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

    /**
     * Request user to set ScamShield as the default dialer app.
     * Uses RoleManager on Android 10+ with TelecomManager fallback for Android 7-9.
     * Shows request dialog only once per app lifetime (tracked via SharedPreferences).
     */
    private void requestDefaultDialerRole() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            boolean alreadyRequested = prefs.getBoolean(PREF_DIALER_ROLE_REQUESTED, false);
            
            if (alreadyRequested) {
                Log.d(TAG, "ℹ️ Dialer role already requested in previous session");
                return;
            }

            // Mark as requested for this session
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(PREF_DIALER_ROLE_REQUESTED, true);
            editor.apply();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ uses RoleManager
                try {
                    RoleManager roleManager = getSystemService(RoleManager.class);
                    if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                        Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                        startActivityForResult(intent, REQUEST_ROLE_DIALER);
                        Log.i(TAG, "✅ Requested default dialer role via RoleManager");
                    } else {
                        Log.w(TAG, "⚠️ Dialer role not available on this device");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ RoleManager error: " + e.getMessage());
                }
            } else {
                // Android 7-9 uses TelecomManager
                try {
                    Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                    intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                    startActivity(intent);
                    Log.i(TAG, "✅ Requested default dialer role via TelecomManager");
                } catch (Exception e) {
                    Log.w(TAG, "⚠️ TelecomManager error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error requesting default dialer role: " + e.getMessage());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        instance = this;
        appendLog("✅ MainActivity resumed");
    }

    @Override
    public void onStop() {
        super.onStop();
        instance = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister LocalBroadcast receiver
        if (localBroadcastManager != null && debugReceiver != null) {
            try {
                localBroadcastManager.unregisterReceiver(debugReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering debug receiver: " + e.getMessage());
            }
        }
    }
    
    /**
     * Implement SpeechListener interface for debug events
     * (from SpeechListener interface)
     */
    @Override
    public void onSpeechRecognized(String text) {
        // Not used in MainActivity - debug logs come through appendLog()
    }
    
    @Override
    public void onDebugLog(String debugMessage) {
        appendLog(debugMessage);
    }

    /**
     * Static method to trigger fallback alert from service
     * Used when ScamAlertActivity fails to launch
     */
    public static void triggerFallbackAlertStatic(String keyword) {
        if (instance != null) {
            instance.triggerFallbackAlert(keyword);
        }
    }

    /**
     * Trigger RED background alert + vibration as fallback
     */
    private void triggerFallbackAlert(String keyword) {
        FrameLayout mainContainer = findViewById(R.id.main_container);
        if (mainContainer == null) return;

        try {
            // Change background to RED (#FFD32F2F)
            mainContainer.setBackgroundColor(android.graphics.Color.parseColor("#FFD32F2F"));

            // Vibrate device in pattern
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(
                        new long[]{0, 200, 100, 200, 100, 200}, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 200, 100, 200, 100, 200}, -1);
                }
            }

            Log.w(TAG, "🚨 FALLBACK ALERT TRIGGERED: " + keyword);

            // Reset background after 2 seconds
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                mainContainer.setBackgroundColor(android.graphics.Color.parseColor("#121212"));
                Log.i(TAG, "Alert background reset to normal");
            }, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Error triggering fallback alert: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            boolean allGranted = true;
            boolean hasRecordAudio = false;
            
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                } else if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                    hasRecordAudio = true;
                }
            }
            
            if (!allGranted) {
                prefs.edit().putBoolean("scam_alerts_enabled", false).apply();
                appendLog("⚠️ Some permissions denied - scam detection disabled");
            } else if (hasRecordAudio) {
                // CRITICAL: Android 14 requirement - Start service from MainActivity foreground state
                // Service must be started from Activity that's currently in foreground (visible to user)
                startScamMonitorService();
                appendLog("✅ All permissions granted - starting scam detection");
            }
        }
    }
    
    /**
     * Start ScamMonitorService from MainActivity.
     * 
     * CRITICAL for Android 14 compliance:
     * - Service MUST be started from Activity in foreground state (visible on screen)
     * - Service is NOT started from Fragment or background context
     * - Foreground notification shown within 5 seconds of service startup
     * - Microphone access allowed immediately after notification
     * 
     * AGENTS.md Reference:
     * - Line 35: Use startForegroundService() on Android O+
     * - Line 223: Service must be started while MainActivity is visible (in foreground)
     */
    private void startScamMonitorService() {
        try {
            long startTime = System.currentTimeMillis();
            appendLog("[" + formatTime(startTime) + "] 🚀 Starting ScamMonitorService from MainActivity foreground");
            
            Intent serviceIntent = new Intent(this, com.shreyanshi.scamshield.services.ScamMonitorService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
                Log.i(TAG, "✅ startForegroundService() called");
            } else {
                startService(serviceIntent);
                Log.i(TAG, "✅ startService() called (Android 7)");
            }
            
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean("scam_alerts_enabled", true).apply();
            
            appendLog("✅ ScamMonitorService started from MainActivity foreground");
            Log.i(TAG, "✅ ScamMonitorService started - Android 14 eligible foreground state");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting ScamMonitorService: " + e.getMessage());
            appendLog("❌ Failed to start service: " + e.getMessage());
        }
    }
    
    /**
     * Helper to format timestamps for debug log
     */
    private String formatTime(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        return sdf.format(new java.util.Date(timestamp));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_ROLE_DIALER) {
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "✅ Successfully set as default dialer");
                appendLog("✅ ScamShield set as default dialer");
            } else {
                Log.i(TAG, "ℹ️ User declined default dialer role");
            }
        }
    }
}
