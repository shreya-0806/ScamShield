package com.shreyanshi.scamshield.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.ServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.activities.InCallActivity;
import com.shreyanshi.scamshield.activities.MainActivity;

import java.util.List;

/**
 * ScamShieldInCallService - Telecom Framework Integration for Default Dialer Role
 * 
 * This service is required when ScamShield is set as the system's default phone dialer.
 * It implements the InCallService interface to handle incoming and outgoing calls through
 * Android's Telecom framework (Android 10+).
 * 
 * Responsibilities:
 * - Handle call lifecycle events (onCallAdded, onCallRemoved, onCallStateChanged)
 * - Start ScamMonitorService when call becomes STATE_ACTIVE (not on RINGING)
 * - Launch InCallActivity to show call UI
 * - Show persistent notification during active calls (visibility fix)
 * - Auto re-launch InCallActivity if UI is hidden (visibility fix)
 * - Manage audio routing so SpeechRecognizer can access microphone during calls
 * 
 * Requirements for Default Dialer:
 * - Android 10+: RoleManager.ROLE_DIALER
 * - AndroidManifest: android.permission.BIND_INCALL_SERVICE
 * - AndroidManifest: android.permission.MANAGE_ONGOING_CALLS (Android 11+)
 * 
 * @author ScamShield Development Team
 * @version 1.4
 */
public class ScamShieldInCallService extends InCallService {
    private static final String TAG = "ScamShield-InCall";
    
    // Notification constants
    private static final String CHANNEL_ID = "scam_call_channel";
    private static final int NOTIFICATION_ID = 2001;
    
    // Broadcast action for call events
    public static final String ACTION_CALL_ACTIVE = "com.shreyanshi.scamshield.ACTION_CALL_ACTIVE";
    public static final String ACTION_CALL_DISCONNECTED = "com.shreyanshi.scamshield.ACTION_CALL_DISCONNECTED";
    public static final String ACTION_FINISH_UI = "com.shreyanshi.scamshield.ACTION_FINISH_UI";
    public static final String ACTION_CALL_STATE_UPDATE = "com.shreyanshi.scamshield.ACTION_CALL_STATE_UPDATE";
    public static final String EXTRA_PHONE_NUMBER = "phone_number";
    public static final String EXTRA_CALL_STATE = "state";
    
    // Current active call - STATIC for InCallActivity access
    public static Call currentCall = null;
    public static Call activeCallInstance = null;  // Direct hardware hook
    private static Call currentCallInstance = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // Call state constants (matching InCallActivity)
    public static final int STATE_RINGING = 1;
    public static final int STATE_ACTIVE = 2;
    public static final int STATE_DISCONNECTED = 3;
    public static final int STATE_DIALING = 4;
    
    // Audio manager for audio routing
    private AudioManager audioManager;
    
    // Track current call direction
    private boolean isCurrentCallIncoming = true;
    
    // FIX 1: Service binding - bind to ScamMonitorService to create "security bridge"
    // This tells Android they are the same app, allowing audio interception
    private ScamMonitorService monitorService;
    private boolean isServiceBound = false;
    
    private final android.content.ServiceConnection serviceConnection = new android.content.ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "✅ Service bound to ScamMonitorService - security bridge established");
            ScamMonitorService.LocalBinder binder = (ScamMonitorService.LocalBinder) service;
            monitorService = binder.getService();
            isServiceBound = true;
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "⚠️ Service disconnected from ScamMonitorService");
            monitorService = null;
            isServiceBound = false;
        }
    };
    
    // Broadcast receiver for answer/disconnect from InCallActivity
    private final BroadcastReceiver callActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            
            String action = intent.getAction();
            if ("com.shreyanshi.scamshield.ACTION_ANSWER_CALL".equals(action)) {
                answerCall();
            } else if ("com.shreyanshi.scamshield.ACTION_DISCONNECT_CALL".equals(action)) {
                disconnectCall();
            }
        }
    };

    /**
     * Called when a new call is added to the system.
     * This occurs both for incoming and outgoing calls.
     */
    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        try {
            // CRITICAL: Set static reference for InCallActivity access
            currentCallInstance = call;
            currentCall = call;
            activeCallInstance = call;  // Direct hardware hook
            
            // CRITICAL: Audio path hook - set audio mode early for call routing
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                Log.i(TAG, "🔊 Audio mode set to MODE_IN_COMMUNICATION");
            }
            
// Answer the call
                // call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY); // REMOVED: Let user answer via InCallActivity button
                Log.i(TAG, "☎️ Call added - waiting for user to answer");
            
            String handle = call.getDetails().getHandle() != null ? 
                call.getDetails().getHandle().getSchemeSpecificPart() : "Unknown";
            
            Log.i(TAG, "☎️ Call added: " + handle);
            
            // CRITICAL: Request exclusive audio focus BEFORE call becomes active
            // This "claims" the audio stream early so ScamMonitorService can access it
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(false); // Default to earpiece
                Log.i(TAG, "🔊 Audio mode set to MODE_IN_COMMUNICATION in onCallAdded");
            }
            
            // Check auto-record preference
            android.content.SharedPreferences prefs = getSharedPreferences("ScamShieldPrefs", MODE_PRIVATE);
            boolean autoRecord = prefs.getBoolean("auto_record", false);
            if (autoRecord) {
                Log.i(TAG, "🎙️ Auto-record enabled - sending intent to InCallActivity");
                // Broadcast to start recording in InCallActivity
                Intent recordIntent = new Intent("com.shreyanshi.scamshield.ACTION_START_RECORDING");
                recordIntent.putExtra("phone_number", handle);
                LocalBroadcastManager.getInstance(this).sendBroadcast(recordIntent);
            }
            
            // Register for state changes
            call.registerCallback(callCallback);
            
            // Determine if incoming or outgoing and store for notification
            isCurrentCallIncoming = (call.getState() == Call.STATE_RINGING);
            
            // Already answered above - just launch UI
            
            // Launch InCallActivity
            launchInCallActivity(handle, call.getState(), isCurrentCallIncoming);
            
            // Start ongoing notification
            showCallNotification(handle, isCurrentCallIncoming);
            
        } catch (Exception e) {
            Log.w(TAG, "Error on call added: " + e.getMessage());
        }
    }
    
    /**
     * Set audio route to earpiece for bi-directional audio
     */
    private void setAudioRouteToEarpiece(Call call) {
        try {
            // Use AudioManager to set the audio routing
            if (audioManager != null) {
                // Disable speaker to route to earpiece
                audioManager.setSpeakerphoneOn(false);
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                Log.i(TAG, "🔊 Audio route set to EARPIECE via AudioManager");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting audio route: " + e.getMessage());
        }
    }
    
    /**
     * Call callback to track state changes
     */
    private final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            handleCallStateChange(call, state);
        }
    };
    
    /**
     * Handle call state changes
     * CRITICAL: Only start ScamMonitorService when call becomes ACTIVE, not on RINGING
     * This ensures SpeechRecognizer initializes after audio route is properly established
     */
    private void handleCallStateChange(Call call, int state) {
        String handle = call.getDetails().getHandle() != null ? 
            call.getDetails().getHandle().getSchemeSpecificPart() : "Unknown";
        
        Log.i(TAG, "📞 Call state changed to: " + state + " (" + getStateName(state) + ")");
        
        // Map Telecom state to our state
        int ourState;
        switch (state) {
            case Call.STATE_RINGING:
                ourState = STATE_RINGING;
                // Set audio mode for incoming call
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_RINGTONE);
                }
                break;
            case Call.STATE_NEW:
            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING:
                ourState = STATE_DIALING;
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                }
                break;
            case Call.STATE_ACTIVE:
            case Call.STATE_HOLDING:
                ourState = STATE_ACTIVE;
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                }
                break;
            case Call.STATE_DISCONNECTING:
                ourState = STATE_ACTIVE;
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                }
                break;
            default:
                ourState = STATE_DISCONNECTED;
                // CRITICAL: Clear static reference
                currentCallInstance = null;
                currentCall = null;
                // Reset audio mode
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_NORMAL);
                    // Turn OFF speakerphone when call ends
                    audioManager.setSpeakerphoneOn(false);
                    Log.i(TAG, "🔊 Speakerphone OFF (call ended)");
                }
                // CRITICAL: Broadcast DISCONNECTED to close InCallActivity
                broadcastCallDisconnected();
                // Stop notification when call ends
                stopCallNotification();
                break;
        }
        
        // CRITICAL: Broadcast state update to InCallActivity for real-time UI sync
        notifyActivity(ourState);
        
        // Update InCallActivity
        updateInCallActivity(handle, ourState);
        
        // Start monitor service only when call is active
        if (state == Call.STATE_ACTIVE) {
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(true);
                audioManager.setSpeakerphoneOn(false);
                Log.i(TAG, "🔊 Audio policy refreshed for active call");
            }
            if (audioManager != null) {
                int audioSessionId = audioManager.generateAudioSessionId();
                Log.i(TAG, "🎵 Generated audio session ID: " + audioSessionId);
                sendImmediateStartBroadcastWithSession(handle, audioSessionId);
            } else {
                sendImmediateStartBroadcast(handle);
            }
            showCallNotification(handle, isCurrentCallIncoming);
            startScamMonitorServiceForCall(handle);
        }
        
        // Stop scam detection when call ends
        if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
            stopScamMonitorService();
            stopCallNotification();
            // CRITICAL: Broadcast ACTION_FINISH_UI to close all UI
            Intent finishUi = new Intent(ACTION_FINISH_UI);
            LocalBroadcastManager.getInstance(this).sendBroadcast(finishUi);
            Log.i(TAG, "📞 DISCONNECTED - sent ACTION_FINISH_UI");
        }
    }
    
    /**
     * Launch InCallActivity with call details
     */
    private void launchInCallActivity(String number, int state, boolean isIncoming) {
        try {
            int ourState;
            if (state == Call.STATE_RINGING) {
                ourState = STATE_RINGING;
            } else if (state == Call.STATE_NEW || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                ourState = STATE_DIALING;
            } else {
                ourState = STATE_ACTIVE;
            }
            
            Intent intent = InCallActivity.createIntent(this, number, ourState, isIncoming);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            
            Log.i(TAG, "✅ InCallActivity launched for: " + number);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error launching InCallActivity: " + e.getMessage());
        }
    }
    
    /**
     * Update existing InCallActivity with new state
     */
    private void updateInCallActivity(String number, int state) {
        try {
            Intent intent = InCallActivity.createIntent(this, number, state, isCurrentCallIncoming);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error updating InCallActivity: " + e.getMessage());
        }
    }
    
    /**
     * Broadcast state update to InCallActivity for real-time UI synchronization
     */
    private void notifyActivity(int state) {
        try {
            Intent intent = new Intent(ACTION_CALL_STATE_UPDATE);
            intent.putExtra(EXTRA_CALL_STATE, state);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            Log.d(TAG, "📡 Broadcasted state update: " + state);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error broadcasting state update: " + e.getMessage());
        }
    }
    
    /**
     * Start ScamMonitorService for call-time detection
     * FIX 1: Also binds to service to create security bridge
     */
    private void startScamMonitorServiceForCall(String phoneNumber) {
        try {
            Intent serviceIntent = new Intent(this, ScamMonitorService.class);
            serviceIntent.putExtra("from_call", true);
            serviceIntent.putExtra("number", phoneNumber);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
                Log.i(TAG, "✅ Started ScamMonitorService from InCallService");
            } else {
                startService(serviceIntent);
                Log.i(TAG, "✅ Started ScamMonitorService (pre-O)");
            }
            
            // FIX 1: Bind to ScamMonitorService - creates security bridge between InCallService and MonitorService
            // This tells Android they are the same app, allowing audio interception during calls
            if (!isServiceBound) {
                boolean bindResult = bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
                Log.i(TAG, "🔗 Binding to ScamMonitorService: " + (bindResult ? "SUCCESS" : "FAILED"));
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting ScamMonitorService: " + e.getMessage());
        }
    }
    
    /**
     * CRITICAL: Send immediate broadcast to start speech recognition NOW
     * This is called the MILLISECOND the call state hits STATE_ACTIVE
     * 
     * Uses LocalBroadcastManager for reliable internal app communication
     */
    private void sendImmediateStartBroadcast(String phoneNumber) {
        sendImmediateStartBroadcastWithSession(phoneNumber, -1); // -1 means no session ID
    }
    
    /**
     * Send broadcast with audio session ID for sharing
     * This allows ScamMonitorService to use the same audio session as InCallService
     */
    private void sendImmediateStartBroadcastWithSession(String phoneNumber, int audioSessionId) {
        try {
            Intent intent = new Intent(ACTION_CALL_ACTIVE);
            intent.putExtra(EXTRA_PHONE_NUMBER, phoneNumber);
            intent.putExtra(ScamMonitorService.EXTRA_CALL_ACTIVE, true);
            
            // Include audio session ID for audio session sharing
            if (audioSessionId > 0) {
                intent.putExtra("audio_session_id", audioSessionId);
                Log.d(TAG, "📡 Including audio session ID: " + audioSessionId + " in broadcast");
            }
            
            // USE LOCALBROADCASTMANAGER - not system sendBroadcast()
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            
            Log.i(TAG, "📡 LOCAL BROADCAST sent via LocalBroadcastManager - phone=" + phoneNumber + ", sessionId=" + audioSessionId);
        } catch (Exception e) {
            Log.e(TAG, "Error sending LocalBroadcast: " + e.getMessage());
        }
    }
    
    /**
     * Broadcast CALL_DISCONNECTED to close InCallActivity
     */
    private void broadcastCallDisconnected() {
        try {
            Intent intent = new Intent(ACTION_CALL_DISCONNECTED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            Log.i(TAG, "📡 BROADCAST: CALL_DISCONNECTED sent to close InCallActivity");
        } catch (Exception e) {
            Log.e(TAG, "Error sending DISCONNECTED broadcast: " + e.getMessage());
        }
    }
    
    /**
     * Stop ScamMonitorService when call ends
     * Sends ACTION_STOP to trigger stopListeningForScams() in ScamMonitorService
     * This properly releases the microphone when call is disconnected
     * 
     * Uses LocalBroadcastManager for reliable internal app communication
     */
    private void stopScamMonitorService() {
        try {
            // USE LOCALBROADCASTMANAGER - not system sendBroadcast()
            Intent broadcastIntent = new Intent(ScamMonitorService.ACTION_STOP);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
            Log.i(TAG, "📡 Sent ACTION_STOP via LocalBroadcastManager - microphone will be released");
            
            // Optional: Also send service intent for cleanup
            Intent serviceIntent = new Intent(this, ScamMonitorService.class);
            serviceIntent.setAction(ScamMonitorService.ACTION_STOP);
            startService(serviceIntent);
            Log.i(TAG, "✅ Stop signal sent to ScamMonitorService");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping ScamMonitorService: " + e.getMessage());
        }
    }

    /**
     * Called when a call is removed from the system.
     */
    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        try {
            if (call != null) {
                String handle = call.getDetails().getHandle() != null ? 
                    call.getDetails().getHandle().getSchemeSpecificPart() : "Unknown";
                Log.i(TAG, "☎️ Call removed: " + handle);
                
                // Unregister callback
                call.unregisterCallback(callCallback);
            }
            
            // Stop notification and finish InCallActivity
            stopCallNotification();
            
            // Broadcast to stop recording if active
            Intent stopRecordIntent = new Intent("com.shreyanshi.scamshield.ACTION_STOP_RECORDING");
            LocalBroadcastManager.getInstance(this).sendBroadcast(stopRecordIntent);
            Log.i(TAG, "🎙️ Sent recording stop broadcast via onCallRemoved");
            
            // Finish InCallActivity
            Intent finishIntent = new Intent("com.shreyanshi.scamshield.ACTION_FINISH_INCALL");
            finishIntent.setClass(this, InCallActivity.class);
            startActivity(finishIntent);
            
            // Stop scam detection
            stopScamMonitorService();
            
        } catch (Exception e) {
            Log.w(TAG, "Error on call removed: " + e.getMessage());
        }
    }
    
    /**
     * Answer the current incoming call
     */
    public void answerCall() {
        if (currentCall != null) {
            try {
                currentCall.answer(0);
                Log.i(TAG, "✅ Call answered programmatically");
            } catch (Exception e) {
                Log.e(TAG, "Error answering call: " + e.getMessage());
            }
        }
    }
    
    /**
     * Reject the current incoming call
     */
    public void rejectCall() {
        if (currentCall != null) {
            try {
                currentCall.reject(false, null);
                Log.i(TAG, "📞 Call rejected programmatically");
            } catch (Exception e) {
                Log.e(TAG, "Error rejecting call: " + e.getMessage());
            }
        }
    }
    
    /**
     * Disconnect the current active call
     */
    public void disconnectCall() {
        if (currentCall != null) {
            try {
                currentCall.disconnect();
                Log.i(TAG, "📞 Call disconnected programmatically");
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting call: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get human-readable state name
     */
    private String getStateName(int state) {
        switch (state) {
            case Call.STATE_NEW: return "NEW";
            case Call.STATE_CONNECTING: return "CONNECTING";
            case Call.STATE_DIALING: return "DIALING";
            case Call.STATE_RINGING: return "RINGING";
            case Call.STATE_HOLDING: return "HOLDING";
            case Call.STATE_ACTIVE: return "ACTIVE";
            case Call.STATE_DISCONNECTED: return "DISCONNECTED";
            case Call.STATE_DISCONNECTING: return "DISCONNECTING";
            default: return "UNKNOWN";
        }
    }

    /**
     * Get human-readable state string for logging
     */
    private String getStateString(int state) {
        switch (state) {
            case Call.STATE_NEW: return "NEW";
            case Call.STATE_RINGING: return "RINGING";
            case Call.STATE_DIALING: return "DIALING";
            case Call.STATE_ACTIVE: return "ACTIVE";
            case Call.STATE_HOLDING: return "HOLDING";
            case Call.STATE_DISCONNECTED: return "DISCONNECTED";
            case Call.STATE_CONNECTING: return "CONNECTING";
            case Call.STATE_DISCONNECTING: return "DISCONNECTING";
            default: return "UNKNOWN";
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize audio manager for audio routing
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        // Create notification channel for call notifications
        createNotificationChannel();
        
        // Register broadcast receiver for call actions from InCallActivity
        // FIX: Android 14+ requires explicit export flag
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.shreyanshi.scamshield.ACTION_ANSWER_CALL");
        filter.addAction("com.shreyanshi.scamshield.ACTION_DISCONNECT_CALL");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 14+ (API 33+): Use RECEIVER_NOT_EXPORTED for internal receiver
            registerReceiver(callActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            // Android 12 and below: No flag needed
            registerReceiver(callActionReceiver, filter);
        }
        
        Log.i(TAG, "✅ ScamShieldInCallService created");
    }
    
    /**
     * Create notification channel for call notifications (Android 8.0+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Call Notifications",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows ongoing call status");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Show persistent notification during active call
     * This ensures the user can find the call again if they navigate away
     */
    private void showCallNotification(String phoneNumber, boolean isIncoming) {
        try {
            // Create intent to bring back to InCallActivity
            Intent notificationIntent = InCallActivity.createIntent(this, phoneNumber, STATE_ACTIVE, isIncoming);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Build notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ScamShield Call")
                .setContentText(isIncoming ? "Incoming: " + phoneNumber : "Outgoing: " + phoneNumber)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false);
            
            // Add action to open app
            Intent openAppIntent = InCallActivity.createIntent(this, phoneNumber, STATE_ACTIVE, isIncoming);
            openAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                this, 2, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(android.R.drawable.ic_menu_view, "Open App", openAppPendingIntent);
            
            // Add action to end call
            Intent endCallIntent = new Intent("com.shreyanshi.scamshield.ACTION_DISCONNECT_CALL");
            PendingIntent endCallPendingIntent = PendingIntent.getBroadcast(
                this, 1, endCallIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", endCallPendingIntent);
            
            // Start as foreground service with notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
            } else {
                startForeground(NOTIFICATION_ID, builder.build());
            }
            
            Log.i(TAG, "✅ Call notification shown - user can tap to return to call");
        } catch (Exception e) {
            Log.e(TAG, "Error showing call notification: " + e.getMessage());
        }
    }
    
    /**
     * Stop the call notification
     */
    private void stopCallNotification() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
            Log.i(TAG, "✅ Call notification removed");
        } catch (Exception e) {
            Log.w(TAG, "Error stopping notification: " + e.getMessage());
        }
    }
    
    /**
     * Auto re-launch InCallActivity if call is active but UI is hidden
     * Called when user presses home or navigates away
     */
    private void ensureCallUIVisibility(String phoneNumber) {
        try {
            // Check if InCallActivity is in foreground
            android.app.ActivityManager activityManager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                List<android.app.ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
                if (!tasks.isEmpty()) {
                    ComponentName topActivity = tasks.get(0).topActivity;
                    if (topActivity != null && !topActivity.getClassName().contains("InCallActivity")) {
                        // InCallActivity is not top - re-launch it
                        Log.i(TAG, "📱 Call UI hidden - re-launching InCallActivity");
                        launchInCallActivity(phoneNumber, STATE_ACTIVE, true);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking activity visibility: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Unregister broadcast receiver
        try {
            unregisterReceiver(callActionReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Error unregistering receiver: " + e.getMessage());
        }
        
        // Cleanup callbacks
        if (currentCall != null) {
            try {
                currentCall.unregisterCallback(callCallback);
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering callback: " + e.getMessage());
            }
        }
        
        // Reset audio mode
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(false);
        }
        
        Log.i(TAG, "🛑 ScamShieldInCallService destroyed");
    }
}