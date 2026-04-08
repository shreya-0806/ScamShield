package com.shreyanshi.scamshield.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.Manifest;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class CallReceiver extends BroadcastReceiver {
    private static final String TAG = "ScamShield-Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        
        String action = intent.getAction();
        Log.d(TAG, "Broadcast received: " + action);

        try {
            SharedPreferences prefs = context.getSharedPreferences("ScamShieldPrefs", Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("scam_alerts_enabled", true);
            
            if (!enabled) {
                Log.d(TAG, "Scam detection disabled, ignoring call");
                return;
            }

            if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
                String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                Log.d(TAG, "Outgoing call to: " + number);
                startScamMonitor(context, number);
            } 
            else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                Log.d(TAG, "Phone state: " + state);
                
                if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                    startScamMonitor(context, "");
                } 
                else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                    stopScamMonitor(context);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing broadcast: " + e.getMessage(), e);
        }
    }

    private void startScamMonitor(Context context, String number) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "RECORD_AUDIO permission not granted");
                return;
            }

            Log.i(TAG, "Starting ScamMonitorService...");
            
            Intent serviceIntent = new Intent(context, ScamMonitorService.class);
            serviceIntent.setAction(ScamMonitorService.ACTION_START);
            serviceIntent.putExtra("number", number != null ? number : "");
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            Log.i(TAG, "ScamMonitorService started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ScamMonitorService: " + e.getMessage(), e);
        }
    }

    private void stopScamMonitor(Context context) {
        try {
            Log.i(TAG, "Stopping ScamMonitorService...");
            
            Intent serviceIntent = new Intent(context, ScamMonitorService.class);
            serviceIntent.setAction(ScamMonitorService.ACTION_STOP);
            context.startService(serviceIntent);
            
            Log.i(TAG, "ScamMonitorService stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping service: " + e.getMessage(), e);
        }
    }
}