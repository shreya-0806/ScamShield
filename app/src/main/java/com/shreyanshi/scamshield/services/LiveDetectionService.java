package com.shreyanshi.scamshield.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.stt.SpeechProcessor;
import com.shreyanshi.scamshield.stt.VoskProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiveDetectionService extends Service {
    private static final String TAG = "LiveDetectionService";
    public static final String ACTION_START = "com.shreyanshi.scamshield.ACTION_START_LIVE_DETECTION";
    public static final String ACTION_STOP = "com.shreyanshi.scamshield.ACTION_STOP_LIVE_DETECTION";

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private SpeechProcessor voskProcessor = null;
    private boolean usingVosk = false;
    private boolean isInitialized = false;

    private BroadcastReceiver voskReceiver = null;

    private final List<String> SCAM_KEYWORDS = Arrays.asList(
            "otp", "one time password", "pin", "password", "account blocked", "verify your account",
            "bank", "transfer", "money", "verify", "card number", "upi", "paytm", "netbanking",
            "reset password", "remote access", "confirm code", "lottery", "gift card", "customer care"
    );

    @Override
    public void onCreate() {
        super.onCreate();
        executorService.execute(this::initializeProcessors);
    }

    private void initializeProcessors() {
        try {
            VoskProcessor vp = new VoskProcessor(this);
            if (vp.isAvailable()) {
                voskProcessor = vp;
                usingVosk = true;
                
                voskReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent intent) {
                        if (intent == null) return;
                        if ("com.shreyanshi.scamshield.VOSK_DETECTED".equals(intent.getAction())) {
                            String keywords = intent.getStringExtra("keywords");
                            triggerAlert(keywords);
                        }
                    }
                };
                
                IntentFilter filter = new IntentFilter("com.shreyanshi.scamshield.VOSK_DETECTED");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(voskReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(voskReceiver, filter);
                }

                voskProcessor.start();
                Log.i(TAG, "Using VoskProcessor for live detection");
                isInitialized = true;
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "VoskProcessor init failed: " + t.getMessage());
        }

        // Fallback to Google SpeechRecognizer
        handler.post(() -> {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(this)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                    speechRecognizer.setRecognitionListener(new LiveRecognitionListener());

                    recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                    
                    isInitialized = true;
                    restartListening();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create SpeechRecognizer", e);
            }
        });
    }

    private void triggerAlert(String detectedKeyword) {
        Log.w(TAG, "SCAM ALERT TRIGGERED: " + detectedKeyword);
        Intent i = new Intent(this, ScamOverlayService.class);
        i.putExtra("action", "SHOW_ALERT");
        i.putExtra("keywords", detectedKeyword);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private class LiveRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(android.os.Bundle params) { isListening = true; }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { isListening = false; }

        @Override
        public void onError(int error) {
            isListening = false;
            restartListeningWithDelay();
        }

        @Override
        public void onResults(android.os.Bundle results) {
            processResults(results);
            isListening = false;
            restartListening();
        }

        @Override
        public void onPartialResults(android.os.Bundle partialResults) {
            processResults(partialResults);
        }

        @Override public void onEvent(int eventType, android.os.Bundle params) { }
    }

    private void restartListening() {
        if (!isInitialized || usingVosk) return;
        if (speechRecognizer != null && !isListening) {
            try {
                speechRecognizer.startListening(recognizerIntent);
            } catch (Exception e) {
                isListening = false;
            }
        }
    }

    private void restartListeningWithDelay() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::restartListening, 1000);
    }

    private void processResults(@Nullable android.os.Bundle results) {
        if (results == null) return;
        ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (texts == null || texts.isEmpty()) return;
        
        String joined = String.join(" ", texts).toLowerCase(Locale.getDefault());
        for (String k : SCAM_KEYWORDS) {
            if (joined.contains(k)) {
                triggerAlert(k);
                break;
            }
        }
    }

    private void startForegroundNotification() {
        String CHANNEL_ID = "live_detection_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Live Detection", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ScamShield - Monitoring Call")
                .setContentText("Listening for suspicious activity...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(201, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(201, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            startForegroundNotification();
            if (isInitialized) {
                if (usingVosk && voskProcessor != null) {
                    if (!voskProcessor.isRunning()) voskProcessor.start();
                } else {
                    restartListening();
                }
            }
        } else if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (voskProcessor != null) voskProcessor.stop();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (voskReceiver != null) unregisterReceiver(voskReceiver);
        executorService.shutdownNow();
    }

    @Nullable
    @Override public IBinder onBind(Intent intent) { return null; }
}
