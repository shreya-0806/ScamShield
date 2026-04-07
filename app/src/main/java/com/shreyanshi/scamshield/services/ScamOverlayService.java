package com.shreyanshi.scamshield.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.activities.MainActivity;

public class ScamOverlayService extends Service {
    private static final String TAG = "ScamShield-Overlay";
    private static final String ACTION_SHOW_ALERT = "SHOW_ALERT";
    private static final String ACTION_DISMISS = "DISMISS";
    private static final String EXTRA_KEYWORDS = "keywords";
    private static final int NOTIFICATION_ID = 1002;
    private static final String CHANNEL_ID = "scam_alert_channel";
    private static final long AUTO_DISMISS_MS = 10000;

    private WindowManager windowManager;
    private View overlayView;
    private boolean isOverlayShowing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        
        if (ACTION_DISMISS.equals(action)) {
            hideOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_SHOW_ALERT.equals(action)) {
            String keywords = intent.getStringExtra(EXTRA_KEYWORDS);
            startForegroundWithNotification();
            showAlertOverlay(keywords != null ? keywords : "Scam Detected");
        }

        return START_NOT_STICKY;
    }

    private void startForegroundWithNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ScamShield Alert")
                .setContentText("Scam alert is being displayed")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(false)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Scam Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Shows scam detection alerts");
                nm.createNotificationChannel(channel);
            }
        }
    }

    @SuppressLint("InflateParams")
    private void showAlertOverlay(String keywords) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M 
                && !android.provider.Settings.canDrawOverlays(this)) {
            return;
        }

        if (isOverlayShowing) {
            hideOverlay();
        }

        try {
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            overlayView = LayoutInflater.from(this).inflate(R.layout.layout_scam_alert_overlay, null);

            TextView tvKeywords = overlayView.findViewById(R.id.tvOverlayKeywords);
            if (tvKeywords != null) {
                tvKeywords.setText("Scam Alert: " + keywords);
            }

            Button btnDismiss = overlayView.findViewById(R.id.btnDismissOverlay);
            if (btnDismiss != null) {
                btnDismiss.setOnClickListener(v -> {
                    hideOverlay();
                    stopSelf();
                });
            }

            int layoutType;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP;

            if (windowManager != null) {
                windowManager.addView(overlayView, params);
                isOverlayShowing = true;

                handler.postDelayed(this::hideOverlay, AUTO_DISMISS_MS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideOverlay() {
        handler.removeCallbacksAndMessages(null);
        if (isOverlayShowing && windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            isOverlayShowing = false;
        }
    }

    @Override
    public void onDestroy() {
        hideOverlay();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
