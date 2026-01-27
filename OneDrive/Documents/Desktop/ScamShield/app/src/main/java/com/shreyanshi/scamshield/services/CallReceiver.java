package com.shreyanshi.scamshield.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.Manifest;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;

import androidx.core.content.ContextCompat;

public class CallReceiver extends BroadcastReceiver {

    private static boolean isCallActive = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (state == null) {
            // Outgoing call detection
            String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            startMonitoring(context, number);
            return;
        }

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
    }

    private void startMonitoring(Context context, String number) {
        // Start the overlay service (already existing)
        Intent serviceIntent = new Intent(context, ScamOverlayService.class);
        serviceIntent.putExtra("action", "START_MONITORING");
        serviceIntent.putExtra("number", number);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        // Check if user enabled scam alerts in prefs
        SharedPreferences prefs = context.getSharedPreferences("ScamShieldPrefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("scam_alerts_enabled", true);
        boolean consent = prefs.getBoolean("user_consent_given", false);
        if (!enabled) return;
        if (!consent) return;

        // Only start live detection if RECORD_AUDIO permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Intent live = new Intent(context, LiveDetectionService.class);
            live.setAction(LiveDetectionService.ACTION_START);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(live);
            } else {
                context.startService(live);
            }
        }
    }

    private void stopMonitoring(Context context) {
        Intent serviceIntent = new Intent(context, ScamOverlayService.class);
        serviceIntent.putExtra("action", "STOP_MONITORING");
        context.startService(serviceIntent);

        Intent live = new Intent(context, LiveDetectionService.class);
        live.setAction(LiveDetectionService.ACTION_STOP);
        context.startService(live);
    }
}