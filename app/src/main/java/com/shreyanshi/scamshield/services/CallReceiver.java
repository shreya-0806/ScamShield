package com.shreyanshi.scamshield.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class CallReceiver extends BroadcastReceiver {
    private static final String TAG = "ScamShield-Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.w(TAG, "Received null intent");
            return;
        }
        
        String action = intent.getAction();
        Log.d(TAG, "Broadcast received: " + action);

        try {
            // Check if scam detection is enabled
            SharedPreferences prefs = context.getSharedPreferences("ScamShieldPrefs", Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("scam_alerts_enabled", true);
            
            if (!enabled) {
                Log.d(TAG, "Scam detection disabled, ignoring call");
                return;
            }

            // Verify context is valid
            if (context == null) {
                Log.e(TAG, "Context is null - cannot process broadcast");
                return;
            }

            if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
                String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                Log.d(TAG, "Outgoing call to: " + (number != null ? number : "Unknown"));
                startScamMonitor(context, number);
            } 
            else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                Log.d(TAG, "Phone state changed: " + state);
                
                if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                    // Incoming call ringing
                    String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                    Log.d(TAG, "Incoming call: " + (incomingNumber != null ? incomingNumber : "Unknown"));
                    startScamMonitor(context, incomingNumber);
                }
                else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                    // Call in progress
                    Log.d(TAG, "Call in progress");
                    startScamMonitor(context, "");
                } 
                else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                    // Call ended
                    Log.d(TAG, "Call ended");
                    stopScamMonitor(context);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing broadcast: " + e.getClass().getName() + ": " + e.getMessage(), e);
            // Don't rethrow - BroadcastReceiver must not crash
        }
    }

    private void startScamMonitor(Context context, String number) {
        try {
            // Verify RECORD_AUDIO permission before starting service
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "RECORD_AUDIO permission not granted - cannot monitor calls");
                return;
            }

            // Validate context
            if (context == null) {
                Log.e(TAG, "Context is null - cannot start service");
                return;
            }

            Log.i(TAG, "Starting ScamMonitorService with number: " + (number != null ? number : "N/A"));
            
            Intent serviceIntent = new Intent(context, ScamMonitorService.class);
            serviceIntent.setAction(ScamMonitorService.ACTION_START);
            serviceIntent.putExtra("number", number != null ? number : "");
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                    Log.d(TAG, "✅ startForegroundService called successfully");
                } else {
                    context.startService(serviceIntent);
                    Log.d(TAG, "✅ startService called successfully (pre-Android 8)");
                }
            } catch (IllegalStateException e) {
                // Can happen if app is in background and service limit exceeded
                Log.e(TAG, "Background service limit exceeded: " + e.getMessage());
                // Attempt regular startService as fallback
                try {
                    context.startService(serviceIntent);
                    Log.d(TAG, "Fallback to startService succeeded");
                } catch (Exception fallbackE) {
                    Log.e(TAG, "Fallback startService also failed: " + fallbackE.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ScamMonitorService: " + e.getClass().getName() + ": " + e.getMessage(), e);
            // Don't rethrow - don't crash the BroadcastReceiver
        }
    }

    private void stopScamMonitor(Context context) {
        try {
            if (context == null) {
                Log.w(TAG, "Context is null - cannot stop service");
                return;
            }

            Log.i(TAG, "Stopping ScamMonitorService...");
            
            Intent serviceIntent = new Intent(context, ScamMonitorService.class);
            serviceIntent.setAction(ScamMonitorService.ACTION_STOP);
            
            try {
                context.startService(serviceIntent);
                Log.d(TAG, "✅ Stop signal sent to ScamMonitorService");
            } catch (Exception e) {
                Log.e(TAG, "Error sending stop signal: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in stopScamMonitor: " + e.getClass().getName() + ": " + e.getMessage(), e);
        }
    }
}