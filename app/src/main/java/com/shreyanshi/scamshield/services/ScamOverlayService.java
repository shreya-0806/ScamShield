package com.shreyanshi.scamshield.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.shreyanshi.scamshield.R;

public class ScamOverlayService extends Service {
    private static final String TAG = "ScamShield-Overlay";
    private WindowManager windowManager;
    private View overlayView;
    private boolean isOverlayShowing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        // Call this immediately to satisfy Android 14 requirements
        startForegroundInternal();
    }

    private void startForegroundInternal() {
        try {
            String CHANNEL_ID = "scam_shield_overlay_channel";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Scam Alert Overlay", NotificationManager.IMPORTANCE_LOW);
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.createNotificationChannel(channel);
            }

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("ScamShield is active")
                    .setContentText("Monitoring for scam attempts...")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(101, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage());
            // On some Android 14 devices, we can't stop the crash if it's strictly backgrounded, 
            // but the try-catch prevents the crash from taking down the whole process if handled by CallReceiver.
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        
        String action = intent.getStringExtra("action");
        if ("SHOW_ALERT".equals(action)) {
            String keywords = intent.getStringExtra("keywords");
            showScamOverlay(keywords);
        } else if ("HIDE_OVERLAY".equals(action)) {
            hideScamOverlay();
        } else if ("STOP_MONITORING".equals(action)) {
            stopSelf();
        }

        return START_STICKY;
    }

    @SuppressLint("InflateParams")
    private void showScamOverlay(String keywords) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return;
        }

        try {
            if (isOverlayShowing) {
                TextView tv = overlayView.findViewById(R.id.tvOverlayKeywords);
                if (tv != null) tv.setText("Suspicious phrase: " + keywords);
                return;
            }

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            overlayView = LayoutInflater.from(this).inflate(R.layout.layout_scam_alert_overlay, null);

            TextView tvKeywords = overlayView.findViewById(R.id.tvOverlayKeywords);
            if (tvKeywords != null) tvKeywords.setText("Scam Detected: " + keywords);

            int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                             WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                             WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP;
            
            if (windowManager != null) {
                windowManager.addView(overlayView, params);
                isOverlayShowing = true;
                
                View dismiss = overlayView.findViewById(R.id.btnDismissOverlay);
                if (dismiss != null) dismiss.setOnClickListener(v -> hideScamOverlay());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing overlay: " + e.getMessage());
        }
    }

    private void hideScamOverlay() {
        if (isOverlayShowing && windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            isOverlayShowing = false;
        }
    }

    @Override
    public void onDestroy() {
        hideScamOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}