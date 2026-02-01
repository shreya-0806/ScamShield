package com.shreyanshi.scamshield.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.net.Uri;
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
    private static final String TAG = "ScamOverlayService";
    private WindowManager windowManager;
    private View overlayView;
    private boolean isOverlayShowing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundInternal();
    }

    private void startForegroundInternal() {
        String CHANNEL_ID = "scam_shield_overlay_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Scam Overlay Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ScamShield Overlay Active")
                .setContentText("Ready to show alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // specialUse was added in API 34, for older versions we can just start foreground
            startForeground(101, notification);
        } else {
            startForeground(101, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        
        String action = intent.getStringExtra("action");
        if ("SHOW_ALERT".equals(action)) {
            String keywords = intent.getStringExtra("keywords");
            Log.d(TAG, "Showing Alert for: " + keywords);
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
        if (isOverlayShowing) {
            // Update existing overlay if already showing
            try {
                TextView tvKeywords = overlayView.findViewById(R.id.tvOverlayKeywords);
                if (tvKeywords != null) {
                    tvKeywords.setText("Suspicious Keyword: " + keywords);
                }
                return;
            } catch (Exception e) {
                hideScamOverlay();
            }
        }

        // Check for overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Permission denied: SYSTEM_ALERT_WINDOW");
            Toast.makeText(this, "Please enable 'Display over other apps' permission", Toast.LENGTH_LONG).show();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            overlayView = LayoutInflater.from(this).inflate(R.layout.layout_scam_alert_overlay, null);

            TextView tvKeywords = overlayView.findViewById(R.id.tvOverlayKeywords);
            if (tvKeywords != null) {
                tvKeywords.setText("Suspicious Activity: " + keywords);
            }

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

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
                
                // Set up dismiss button
                View dismiss = overlayView.findViewById(R.id.btnDismissOverlay);
                if (dismiss != null) {
                    dismiss.setOnClickListener(v -> hideScamOverlay());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay view", e);
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
