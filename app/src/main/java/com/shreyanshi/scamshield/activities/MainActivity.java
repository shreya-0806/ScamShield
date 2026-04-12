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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.stt.SpeechListener;
import com.shreyanshi.scamshield.utils.DebugLogWindow;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity implements SpeechListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 200;
    private static final int REQUEST_ROLE_DIALER = 101;
    private static final String PREF_NAME = "ScamShieldPrefs";
    private static final String PREF_DIALER_ROLE_REQUESTED = "dialer_role_requested";
    
    private DebugLogWindow debugLogWindow;
    private static MainActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkCrashLogs();
        checkAndRequestPermissions();
        requestDefaultDialerRole();

        setContentView(R.layout.activity_main);
        
        // Initialize debug log window after setContentView()
        try {
            FrameLayout mainContainer = findViewById(R.id.main_container);
            if (mainContainer != null) {
                debugLogWindow = new DebugLogWindow(this);
                debugLogWindow.initialize(mainContainer);
                debugLogWindow.logToScreen("✅ MainActivity initialized");
                
                // Add debug window refresh button
                addDebugWindowRefreshButton(mainContainer);
            } else {
                Log.w(TAG, "⚠️ main_container not found, debug log window skipped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing debug log: " + e.getMessage());
        }

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        if (savedInstanceState == null) {
            loadFragmentByName("com.shreyanshi.scamshield.ui.home.HomeFragment");
        }
        bottomNavigation.setOnItemSelectedListener(item -> loadFragmentById(item.getItemId()));
    }

    /**
     * Add a refresh button to manually trigger debug window re-initialization
     * Useful if debug window fails to load on first try or for testing purposes
     * 
     * Button appears as a floating FAB-style button (small, transparent, top-right)
     */
    private void addDebugWindowRefreshButton(FrameLayout mainContainer) {
        try {
            // Create button with refresh icon/text
            android.widget.Button refreshBtn = new android.widget.Button(this);
            refreshBtn.setText("🔄");  // Refresh emoji
            refreshBtn.setTextSize(24);
            refreshBtn.setAlpha(0.7f);  // Semi-transparent
            refreshBtn.setPadding(12, 12, 12, 12);
            
            // Create layout params for FAB-style positioning (top-right corner)
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP | android.view.Gravity.END
            );
            params.setMargins(0, 16, 16, 0);  // 16dp margin from top and right
            
            // Set button listener
            refreshBtn.setOnClickListener(v -> {
                Log.i(TAG, "🔄 Manual debug window refresh triggered");
                
                // Re-initialize debug window
                try {
                    if (debugLogWindow != null) {
                        debugLogWindow.initialize(mainContainer);
                        Log.i(TAG, "✅ Debug window re-initialized via refresh button");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error re-initializing debug window: " + e.getMessage());
                }
                
                // Brief visual feedback
                refreshBtn.animate().alpha(1.0f).setDuration(200).start();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    refreshBtn.animate().alpha(0.7f).setDuration(200).start();
                }, 300);
            });
            
            // Add button to container
            mainContainer.addView(refreshBtn, params);
            Log.d(TAG, "✅ Debug window refresh button added (top-right corner)");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error adding refresh button: " + e.getMessage());
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
        
        // Register as debug listener for ScamMonitorService
        try {
            com.shreyanshi.scamshield.services.ScamMonitorService.setDebugListener(this);
            if (debugLogWindow != null) {
                debugLogWindow.logToScreen("✅ Debug listener registered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering debug listener: " + e.getMessage());
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        
        // Unregister debug listener
        try {
            com.shreyanshi.scamshield.services.ScamMonitorService.clearDebugListener();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing debug listener: " + e.getMessage());
        }
        
        instance = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (debugLogWindow != null) {
            debugLogWindow.destroy();
        }
    }
    
    /**
     * Implement SpeechListener interface for debug events
     */
    @Override
    public void onSpeechRecognized(String text) {
        // Not used in MainActivity - debug log handled through onDebugLog()
    }
    
    @Override
    public void onDebugLog(String debugMessage) {
        if (debugLogWindow != null) {
            debugLogWindow.logToScreen(debugMessage);
        }
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_ROLE_DIALER) {
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "✅ Successfully set as default dialer");
            } else {
                Log.i(TAG, "ℹ️ User declined default dialer role");
            }
        }
    }
}
