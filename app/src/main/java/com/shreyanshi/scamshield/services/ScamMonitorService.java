package com.shreyanshi.scamshield.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.stt.SpeechListener;
import com.shreyanshi.scamshield.stt.VoskProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import android.Manifest;
import android.content.pm.PackageManager;

public class ScamMonitorService extends Service implements SpeechListener {
    private static final String TAG = "ScamShield-Monitor";
    private static final int NOTIFICATION_ID = 1001;
    
    public static final String ACTION_START = "com.shreyanshi.scamshield.ACTION_START_MONITORING";
    public static final String ACTION_STOP = "com.shreyanshi.scamshield.ACTION_STOP_MONITORING";
    private static final long ALERT_DEBOUNCE_MS = 30000;

    private static final String CHANNEL_ID = "scam_monitor_channel";

    private SpeechRecognizer speechRecognizer;
    private android.content.Intent recognizerIntent;
    private boolean isListening = false;
    private boolean isServiceRunning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastAlertTime = 0;

    private VoskProcessor voskProcessor = null;
    private boolean usingVosk = false;

    private WindowManager windowManager;
    private View overlayView;
    private boolean isOverlayShowing = false;

    private final List<String> SCAM_KEYWORDS = Arrays.asList(
            "otp", "one time password", "pin", "password", "account blocked", "verify your account",
            "bank", "transfer", "money", "verify", "card number", "upi", "paytm", "netbanking",
            "reset password", "remote access", "confirm code", "lottery", "gift card", "customer care",
            "blocked", "locked", "account"
    );

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isServiceRunning) {
            isServiceRunning = true;
            startForegroundWithNotification();
            initializeSpeechRecognition();
        }
        
        return START_STICKY;
    }

    private void startForegroundWithNotification() {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ScamShield Active")
                .setContentText("Monitoring calls for scam attempts")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            Log.d(TAG, "startForeground successful");
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Scam Monitoring",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Shows when ScamShield is monitoring calls");
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void initializeSpeechRecognition() {
        boolean hasRecordAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (!hasRecordAudio) {
            Log.w(TAG, "Record audio permission missing");
            return;
        }

        try {
            voskProcessor = new VoskProcessor(this, this);
            handler.postDelayed(() -> {
                if (voskProcessor != null && voskProcessor.isAvailable()) {
                    usingVosk = true;
                    voskProcessor.start();
                    Log.i(TAG, "Vosk detection started");
                } else {
                    Log.w(TAG, "Vosk model not available, using Google Speech");
                    setupGoogleSpeech();
                }
            }, 1500);
        } catch (Exception e) {
            Log.e(TAG, "Vosk initialization failed", e);
        }
    }

    private void setupGoogleSpeech() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(new GoogleRecognitionListener());

                recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                
                startListeningGoogle();
            } catch (Exception e) {
                Log.e(TAG, "Google Speech setup failed", e);
            }
        }
    }

    private void startListeningGoogle() {
        if (speechRecognizer != null && !isListening) {
            try {
                isListening = true;
                speechRecognizer.startListening(recognizerIntent);
            } catch (Exception e) {
                isListening = false;
                Log.e(TAG, "Google start failed: " + e.getMessage());
                handler.postDelayed(this::startListeningGoogle, 2000);
            }
        }
    }

    @Override
    public void onSpeechRecognized(String text) {
        if (text == null || text.isEmpty()) return;
        
        String lowerText = text.toLowerCase();
        for (String k : SCAM_KEYWORDS) {
            if (lowerText.contains(k)) {
                triggerAlert(k);
                break;
            }
        }
    }

    private void triggerAlert(String detectedKeyword) {
        long now = System.currentTimeMillis();
        if (now - lastAlertTime < ALERT_DEBOUNCE_MS) {
            Log.d(TAG, "Alert debounced (cooldown active)");
            return;
        }
        lastAlertTime = now;
        
        Log.w(TAG, "!!! SCAM KEYWORD DETECTED: " + detectedKeyword);
        showScamOverlay(detectedKeyword);
    }

    @SuppressLint("InflateParams")
    private void showScamOverlay(String keywords) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted");
            return;
        }

        try {
            if (isOverlayShowing && overlayView != null) {
                TextView tv = overlayView.findViewById(R.id.tvOverlayKeywords);
                if (tv != null) tv.setText("Scam Detected: " + keywords);
                return;
            }

            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            overlayView = LayoutInflater.from(this).inflate(R.layout.layout_scam_alert_overlay, null);

            TextView tvKeywords = overlayView.findViewById(R.id.tvOverlayKeywords);
            if (tvKeywords != null) tvKeywords.setText("Scam Detected: " + keywords);

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
                
                View dismiss = overlayView.findViewById(R.id.btnDismissOverlay);
                if (dismiss != null) {
                    dismiss.setOnClickListener(v -> hideScamOverlay());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing overlay: " + e.getMessage());
        }
    }

    private void hideScamOverlay() {
        if (isOverlayShowing && windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay: " + e.getMessage());
            }
            isOverlayShowing = false;
        }
    }

    private class GoogleRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(android.os.Bundle params) { 
            isListening = true;
            Log.d(TAG, "Google Speech ready");
        }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() { 
            isListening = false; 
        }
        @Override public void onError(int error) {
            isListening = false;
            Log.w(TAG, "Google Speech error: " + error);
            handler.postDelayed(() -> {
                if (isServiceRunning) {
                    startListeningGoogle();
                }
            }, 1500);
        }
        @Override
        public void onResults(android.os.Bundle results) {
            processResults(results);
            isListening = false;
            if (isServiceRunning) {
                startListeningGoogle();
            }
        }
        @Override
        public void onPartialResults(android.os.Bundle partialResults) {
            processResults(partialResults);
        }
        @Override public void onEvent(int eventType, android.os.Bundle params) {}
    }

    private void processResults(android.os.Bundle results) {
        ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (texts != null) {
            for (String text : texts) {
                onSpeechRecognized(text);
            }
        }
    }

    private void stopMonitoring() {
        isServiceRunning = false;
        hideScamOverlay();
        
        if (voskProcessor != null) {
            voskProcessor.stop();
            voskProcessor = null;
        }
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {}
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        Log.d(TAG, "Monitoring stopped");
    }

    @Override
    public void onDestroy() {
        stopMonitoring();
        super.onDestroy();
    }

    @Nullable
    @Override public IBinder onBind(Intent intent) { return null; }
}
