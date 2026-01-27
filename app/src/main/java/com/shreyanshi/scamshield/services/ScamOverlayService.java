package com.shreyanshi.scamshield.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.shreyanshi.scamshield.R;

public class ScamOverlayService extends Service {

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
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        try {
            // Try to start as a foreground service (may throw if OS disallows microphone-type FGS)
            startForeground(101, notification);
        } catch (SecurityException se) {
            // Fall back: post a normal notification so the app doesn't crash. The service may be at higher risk of being killed, but this avoids a crash.
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(101, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getStringExtra("action") : "";
        
        if ("SHOW_ALERT".equals(action)) {
            String keywords = intent.getStringExtra("keywords");
            showScamOverlay(keywords);
        } else if ("HIDE_OVERLAY".equals(action)) {
            hideScamOverlay();
            showSavePrompt();
        }

        return START_STICKY;
    }

    @SuppressLint("InflateParams")
    private void showScamOverlay(String keywords) {
        if (isOverlayShowing) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            // Inflate without attaching to root; provide false to avoid layout params issues
            overlayView = LayoutInflater.from(this).inflate(R.layout.layout_scam_alert_overlay, null, false);

            TextView tvKeywords = overlayView.findViewById(R.id.tvOverlayKeywords);
            String disp = (keywords != null) ? keywords : "";
            tvKeywords.setText(getString(R.string.detected_format, disp));

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP;
            // addView can throw if overlay permission missing; guard it
            if (windowManager != null) {
                try {
                    windowManager.addView(overlayView, params);
                    isOverlayShowing = true;
                } catch (Exception e) {
                    // ignore overlay failures; do not crash the service
                    isOverlayShowing = false;
                }
            }

            View dismiss = overlayView.findViewById(R.id.btnDismissOverlay);
            if (dismiss != null) dismiss.setOnClickListener(v -> hideScamOverlay());
        } catch (Exception e) {
            // If inflation fails, ensure we don't crash
            isOverlayShowing = false;
        }
    }

    private void hideScamOverlay() {
        if (isOverlayShowing && windowManager != null && overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            isOverlayShowing = false;
            overlayView = null;
        }
    }

    private void showSavePrompt() {
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), "com.shreyanshi.scamshield.activities.PostCallActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
            // If PostCallActivity cannot be launched for any reason, ignore to avoid crashing the service
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
