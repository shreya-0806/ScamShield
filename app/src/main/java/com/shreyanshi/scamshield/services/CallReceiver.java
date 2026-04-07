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

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent monitorIntent = new Intent(context, ScamMonitorService.class);
                monitorIntent.setAction(ScamMonitorService.ACTION_START);
                monitorIntent.putExtra("number", number);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(monitorIntent);
                } else {
                    context.startService(monitorIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start ScamMonitorService", e);
            }
        }
    }

    private void stopMonitoring(Context context) {
        try {
            Intent stopIntent = new Intent(context, ScamMonitorService.class);
            stopIntent.setAction(ScamMonitorService.ACTION_STOP);
            context.startService(stopIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping monitoring service", e);
        }
    }
}