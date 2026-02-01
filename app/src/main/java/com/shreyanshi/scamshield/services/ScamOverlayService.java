package com.shreyanshi.scamshield.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
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
        startForegroundService();
    }

    private void startForegroundService() {
        String CHANNEL_ID = "scam_shield_service";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Scam Monitoring", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ScamShield Active")
                .setContentText("Monitoring for suspicious keywords...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(101, notification);
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
        }

        return START_STICKY;
    }

    @SuppressLint("InflateParams")
    private void showScamOverlay(String keywords) {
        if (isOverlayShowing) return;

        // Check for overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Permission denied: SYSTEM_ALERT_WINDOW");
            Toast.makeText(this, "Please enable 'Display over other apps' permission for ScamShield", Toast.LENGTH_LONG).show();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            overlayView = LayoutInflater.from(this).inflate(R.layout.layout_scam_alert_overlay, null);

            TextView tvKeywords = overlayView.findViewById(R.id.tvOverlayKeywords);
            if (tvKeywords != null) {
                tvKeywords.setText("Suspicious Keyword: " + keywords);
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
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
