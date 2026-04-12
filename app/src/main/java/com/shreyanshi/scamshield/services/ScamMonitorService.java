package com.shreyanshi.scamshield.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.activities.MainActivity;
import com.shreyanshi.scamshield.activities.ScamAlertActivity;
import com.shreyanshi.scamshield.stt.SpeechListener;
import com.shreyanshi.scamshield.stt.GoogleSpeechRecognizer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.Manifest;
import android.content.pm.PackageManager;
import android.provider.Settings;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class ScamMonitorService extends Service implements SpeechListener {
    private static final String TAG = "ScamShield-Monitor";
    private static final int NOTIFICATION_ID = 1001;
    
    public static final String ACTION_START = "com.shreyanshi.scamshield.ACTION_START_MONITORING";
    public static final String ACTION_STOP = "com.shreyanshi.scamshield.ACTION_STOP_MONITORING";
    public static final String ACTION_DEBUG_LOG = "com.shreyanshi.scamshield.DEBUG_LOG";
    public static final String EXTRA_DEBUG_MESSAGE = "log_message";
    private static final long ALERT_DEBOUNCE_MS = 30000;

    private static final String CHANNEL_ID = "scam_monitor_channel";
    
    // Static listener for MainActivity to register debug callbacks
    private static SpeechListener debugListener = null;

    private GoogleSpeechRecognizer googleSpeechRecognizer;
    private boolean isServiceRunning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastAlertTime = 0;
    private String currentNumber = "";

    private final Set<String> SCAM_KEYWORDS = new HashSet<>(Arrays.asList(
            "otp", "one time password", "pin", "password", "account blocked", "verify your account",
            "bank", "transfer", "money", "verify", "card number", "upi", "paytm", "netbanking",
            "reset password", "remote access", "confirm code", "lottery", "gift card", "customer care",
            "blocked", "locked", "account", "aadhar", "PAN card", "KYC", "debit card", "credit card",
            "UPI id", "bank account", "suspended", "immediate action", "urgent"
    ));

    /**
     * Register a debug listener (typically MainActivity)
     * for real-time debug event callbacks
     */
    public static void setDebugListener(SpeechListener listener) {
        debugListener = listener;
        Log.d("ScamMonitorService", "Debug listener registered");
    }

    /**
     * Unregister debug listener
     */
    public static void clearDebugListener() {
        debugListener = null;
    }

    /**
     * Send debug message via LocalBroadcast to MainActivity.
     * This is safer than direct listener callbacks because it works even if
     * MainActivity is not running, and provides cleaner service-to-UI communication.
     * 
     * CRITICAL: All debug events must be sent through this method for visibility
     * in the Internal Debug Terminal (bottom of MainActivity UI).
     */
    private void sendDebugLogBroadcast(String message) {
        try {
            Intent intent = new Intent(ACTION_DEBUG_LOG);
            intent.putExtra(EXTRA_DEBUG_MESSAGE, message);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            
            // Also send to direct listener if available (MainActivity active)
            if (debugListener != null) {
                try {
                    debugListener.onDebugLog(message);
                } catch (Exception e) {
                    Log.d(TAG, "Direct debug listener callback failed: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending debug broadcast: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        long onStartCommandTime = System.currentTimeMillis();
        
        sendDebugLogBroadcast("✅ ScamMonitorService.onStartCommand() called");
        sendDebugLogBroadcast("[ANDROID 14] Starting foreground notification (must be within 5 seconds)");
        
        // CRITICAL: Start foreground notification FIRST (must be within 5 seconds)
        // Android 14 requirement: startForeground() must be called before microphone access
        if (!isServiceRunning) {
            isServiceRunning = true;
            long notificationStartTime = System.currentTimeMillis();
            startForegroundWithNotification();
            long notificationEndTime = System.currentTimeMillis();
            
            long elapsedTime = notificationEndTime - onStartCommandTime;
            Log.i(TAG, "⏱️ Foreground notification setup took " + elapsedTime + "ms (max: 5000ms)");
            if (elapsedTime > 5000) {
                Log.w(TAG, "⚠️ WARNING: Foreground notification took > 5 seconds!");
                sendDebugLogBroadcast("⚠️ WARNING: Notification setup took " + elapsedTime + "ms (max: 5000ms)");
            } else {
                sendDebugLogBroadcast("✅ Notification setup completed in " + elapsedTime + "ms");
            }
        }
        
        // CHECK: Is scam detection enabled in SharedPreferences?
        SharedPreferences prefs = getSharedPreferences("ScamShieldPrefs", Context.MODE_PRIVATE);
        boolean scamAlertsEnabled = prefs.getBoolean("scam_alerts_enabled", true);
        
        if (!scamAlertsEnabled) {
            Log.i(TAG, "⏹️ Scam detection disabled by user, stopping service");
            sendDebugLogBroadcast("⏹️ Scam detection disabled, stopping service");
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null) {
            currentNumber = intent.getStringExtra("number");
        }

        // CRITICAL: Initialize speech recognition on main thread using Handler
        // This avoids race conditions during service startup
        handler.post(() -> {
            Log.d(TAG, "Initializing speech recognition on main thread");
            long initStartTime = System.currentTimeMillis();
            initializeSpeechRecognition();
            long initEndTime = System.currentTimeMillis();
            
            Log.i(TAG, "⏱️ Speech recognition initialization took " + (initEndTime - initStartTime) + "ms");
        });
        
        return START_STICKY;
    }

    private void startForegroundWithNotification() {
        Log.d(TAG, "startForegroundWithNotification: Starting");
        sendDebugLogBroadcast("🔧 Creating foreground notification...");
        
        // CRITICAL: Create channel FIRST before building notification
        try {
            createNotificationChannel();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create notification channel: " + e.getMessage());
            sendDebugLogBroadcast("❌ Notification channel creation failed");
            // Don't stop - try to continue
        }

        // Validate notification manager exists
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            Log.e(TAG, "CRITICAL: NotificationManager is null - cannot show foreground service");
            sendDebugLogBroadcast("❌ NotificationManager is null");
            // FALLBACK: Try to stop gracefully
            try {
                stopSelf();
            } catch (Exception ignored) {}
            return;
        }

        // Verify icon resource exists and is accessible
        try {
            getResources().getDrawable(R.drawable.ic_notification, null);
            Log.d(TAG, "Icon verification: R.drawable.ic_notification is accessible");
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Icon resource missing or invalid: " + e.getMessage());
            sendDebugLogBroadcast("❌ Icon resource missing");
            // FALLBACK: Use system icon (better than crash)
            // Don't proceed with notification
            try {
                stopSelf();
            } catch (Exception ignored) {}
            return;
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Build notification with full error handling
        try {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("ScamShield Active")
                    .setContentText("Protecting you from fraud calls...")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setContentIntent(pendingIntent)
                    .setSubText("Real-time scam detection")
                    .build();

            Log.d(TAG, "Notification built successfully");

            // Call startForeground with proper error handling
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                    Log.d(TAG, "startForeground called with FOREGROUND_SERVICE_TYPE_MICROPHONE");
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                    Log.d(TAG, "startForeground called (Android O/P)");
                }
                Log.d(TAG, "✅ Foreground service started successfully");
                sendDebugLogBroadcast("✅ Foreground notification created");
            } catch (Exception e) {
                Log.e(TAG, "startForeground failed: " + e.getClass().getName() + ": " + e.getMessage(), e);
                Log.e(TAG, "Details: Notification ID=" + NOTIFICATION_ID + " Channel=" + CHANNEL_ID);
                sendDebugLogBroadcast("❌ startForeground failed: " + e.getMessage());
                throw e; // Critical failure
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage(), e);
            sendDebugLogBroadcast("❌ Failed to create notification: " + e.getMessage());
            // Last resort: cancel notification and stop
            try {
                nm.cancel(NOTIFICATION_ID);
                stopSelf();
            } catch (Exception ignored) {
                Log.e(TAG, "Error during cleanup: " + ignored.getMessage());
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) {
                Log.e(TAG, "Cannot create notification channel: NotificationManager is null");
                return;
            }

            try {
                // Delete old channel if exists (forces recreation)
                nm.deleteNotificationChannel(CHANNEL_ID);
                Log.d(TAG, "Deleted old notification channel");
            } catch (Exception e) {
                Log.d(TAG, "No existing channel to delete: " + e.getMessage());
            }

            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Scam Monitoring Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Real-time monitoring for scam calls");
                channel.setShowBadge(false);
                channel.enableVibration(false);
                channel.setSound(null, null);
                
                nm.createNotificationChannel(channel);
                Log.d(TAG, "✅ Notification channel created: " + CHANNEL_ID);
            } catch (Exception e) {
                Log.e(TAG, "Error creating notification channel: " + e.getMessage(), e);
                throw e; // Critical
            }
        }
    }

    private void initializeSpeechRecognition() {
        // Check RECORD_AUDIO permission before using audio
        boolean hasRecordAudio = ContextCompat.checkSelfPermission(this, 
            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        
        if (!hasRecordAudio) {
            Log.e(TAG, "❌ RECORD_AUDIO permission missing - cannot start speech recognition");
            sendDebugLogBroadcast("❌ RECORD_AUDIO permission missing");
            showToast("Microphone permission required for scam detection");
            return;
        }
        
        Log.i(TAG, "✅ RECORD_AUDIO permission verified");
        sendDebugLogBroadcast("✅ RECORD_AUDIO permission verified");
        
        // Check overlay permission (optional but good to know)
        boolean hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        if (hasOverlay) {
            Log.i(TAG, "✅ SYSTEM_ALERT_WINDOW permission available");
            sendDebugLogBroadcast("✅ Overlay permission available");
        } else {
            Log.w(TAG, "⚠️ SYSTEM_ALERT_WINDOW permission not granted (optional)");
            sendDebugLogBroadcast("⚠️ Overlay permission not granted (optional)");
        }
        
        try {
            // Initialize Google On-Device Speech Recognizer (instant, no model loading)
            sendDebugLogBroadcast("🎤 Initializing Google Speech Recognizer...");
            
            googleSpeechRecognizer = new GoogleSpeechRecognizer(this, this);
            googleSpeechRecognizer.start();
            
            Log.i(TAG, "✅ Google On-Device Speech Recognizer initialized and listening");
            sendDebugLogBroadcast("✅ Speech Recognizer initialized and listening");
            showToast("ScamShield is monitoring calls");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing Google Speech: " + e.getMessage(), e);
            sendDebugLogBroadcast("❌ Speech Recognizer failed: " + e.getMessage());
            showToast("Failed to start scam detection");
        }
    }
    
    private void showToast(String msg) {
        try {
            handler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
        } catch (Exception ignored) {}
    }
    
    @Override
    public void onSpeechRecognized(String text) {
        if (text == null || text.isEmpty()) {
            Log.d(TAG, "Empty text received from speech recognizer");
            return;
        }
        
        Log.d(TAG, "📢 Recognized text: '" + text + "'");
        
        // Normalize text: lowercase and trim whitespace
        String normalizedText = text.toLowerCase().trim();
        
        // Check for keyword matches
        for (String keyword : SCAM_KEYWORDS) {
            // Use word boundary matching: keyword must be a complete word
            // This prevents "pin" from matching "pincode" if we want strict matching
            // For now, using contains() for better recall (catches "verify your account")
            if (normalizedText.contains(keyword)) {
                Log.i(TAG, "🚨 SCAM KEYWORD DETECTED: '" + keyword + "' in '" + text + "'");
                triggerAlert(keyword);
                return; // Exit after first match to avoid multiple alerts
            }
        }
        
        Log.d(TAG, "No scam keywords detected in: '" + text + "'");
    }

    private void triggerAlert(String detectedKeyword) {
        long now = System.currentTimeMillis();
        if (now - lastAlertTime < ALERT_DEBOUNCE_MS) {
            Log.d(TAG, "Alert debounced (cooldown active)");
            return;
        }
        lastAlertTime = now;
        
        Log.w(TAG, "!!! SCAM KEYWORD DETECTED: " + detectedKeyword);
        showScamAlert(detectedKeyword);
    }

    private void showScamAlert(String keywords) {
        try {
            Intent alertIntent = ScamAlertActivity.createIntent(this, keywords, currentNumber);
            startActivity(alertIntent);
            
            // Notify via broadcast
            sendDebugLogBroadcast("✅ Alert shown for: " + keywords);
        } catch (Exception e) {
            Log.e(TAG, "Error showing alert: " + e.getMessage());
            handler.post(() -> Toast.makeText(this, "Scam Alert: " + keywords, Toast.LENGTH_LONG).show());
            
            // Fallback: Try to trigger MainActivity fallback alert
            try {
                MainActivity.triggerFallbackAlertStatic(keywords);
            } catch (Exception e2) {
                Log.d(TAG, "Fallback alert not available: " + e2.getMessage());
            }
            
            // Notify of failure
            sendDebugLogBroadcast("❌ Alert failed, fallback triggered");
        }
    }

     private void stopMonitoring() {
        isServiceRunning = false;
        Log.d(TAG, "🛑 Stopping monitoring...");
        
        // Stop Google On-Device Speech Recognizer
        if (googleSpeechRecognizer != null) {
            try {
                googleSpeechRecognizer.stop();
                googleSpeechRecognizer.destroy();
                Log.d(TAG, "✅ Google Speech Recognizer stopped and destroyed");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping Google Speech: " + e.getMessage());
            }
            googleSpeechRecognizer = null;
        }
        
        Log.i(TAG, "✅ Monitoring stopped completely");
    }

    @Override
    public void onDebugLog(String debugMessage) {
        // Service receives debug events but doesn't display them
        // MainActivity (if active) will display through listener callback
        Log.d(TAG, "🔍 Debug: " + debugMessage);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "🛑 ScamMonitorService.onDestroy() called");
        sendDebugLogBroadcast("🛑 Service destroying...");
        
        // Stop all monitoring immediately
        stopMonitoring();
        
        // Remove the foreground notification
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
            Log.d(TAG, "✅ Foreground notification removed");
            sendDebugLogBroadcast("✅ Foreground notification removed");
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Error removing foreground notification: " + e.getMessage());
        }
        
        super.onDestroy();
    }

    @Nullable
    @Override public IBinder onBind(Intent intent) { return null; }
}
