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
    private static boolean isCallActive = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

            if (state == null) {
                // Outgoing call detection (NEW_OUTGOING_CALL)
                String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                startMonitoring(context, number);
                return;
            }

            Log.d(TAG, "Phone State Change: " + state);

            if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                // Call answered (Incoming or Outgoing)
                isCallActive = true;
                startMonitoring(context, "");
            } else if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                // Call ended
                if (isCallActive) {
                    isCallActive = false;
                    stopMonitoring(context);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in CallReceiver", e);
        }
    }

    private void startMonitoring(Context context, String number) {
        SharedPreferences prefs = context.getSharedPreferences("ScamShieldPrefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("scam_alerts_enabled", true);
        if (!enabled) return;

        // 1. Start Overlay Service
        try {
            Intent serviceIntent = new Intent(context, ScamOverlayService.class);
            serviceIntent.putExtra("action", "START_MONITORING");
            serviceIntent.putExtra("number", number);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ScamOverlayService", e);
        }

        // 2. Start Live Detection Service (Microphone)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent live = new Intent(context, LiveDetectionService.class);
                live.setAction(LiveDetectionService.ACTION_START);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(live);
                } else {
                    context.startService(live);
                }
            } catch (Exception e) {
                // On Android 14+, this might fail if the app is backgrounded.
                Log.e(TAG, "Failed to start LiveDetectionService (Background restriction)", e);
            }
        }
    }

    private void stopMonitoring(Context context) {
        try {
            Intent serviceIntent = new Intent(context, ScamOverlayService.class);
            serviceIntent.putExtra("action", "STOP_MONITORING");
            context.startService(serviceIntent);

            Intent live = new Intent(context, LiveDetectionService.class);
            live.setAction(LiveDetectionService.ACTION_STOP);
            context.startService(live);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping services", e);
        }
    }
}