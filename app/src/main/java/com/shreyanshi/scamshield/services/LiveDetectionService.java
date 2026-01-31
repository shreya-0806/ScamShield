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

    private final List<String> KEYWORDS = Arrays.asList(
            "otp", "one time password", "pin", "password", "otp code", "account number",
            "bank", "transfer", "money", "verify", "card number", "upi", "paytm", "netbanking",
            "reset password", "remote access", "confirm code"
    );

    @Override
    public void onCreate() {
        super.onCreate();
        // Move heavy initialization to a background thread to prevent ANR
        executorService.execute(this::initializeProcessors);
    }

    private void initializeProcessors() {
        try {
            VoskProcessor vp = new VoskProcessor(this);
            if (vp.isAvailable()) {
                voskProcessor = vp;
                usingVosk = true;
                voskProcessor.start();

                voskReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent intent) {
                        if (intent == null) return;
                        String action = intent.getAction();
                        if ("com.shreyanshi.scamshield.VOSK_DETECTED".equals(action)) {
                            String keywords = intent.getStringExtra("keywords");
                            String text = intent.getStringExtra("text");
                            Intent i = new Intent(ctx, ScamOverlayService.class);
                            i.putExtra("action", "SHOW_ALERT");
                            i.putExtra("keywords", keywords != null ? keywords : text);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ctx.startForegroundService(i);
                            } else {
                                ctx.startService(i);
                            }
                        }
                    }
                };
                
                IntentFilter filter = new IntentFilter("com.shreyanshi.scamshield.VOSK_DETECTED");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(voskReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(voskReceiver, filter);
                }

                Log.i(TAG, "Using VoskProcessor for live detection");
                isInitialized = true;
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "VoskProcessor init failed: " + t.getMessage());
            usingVosk = false;
            voskProcessor = null;
        }

        // Fallback to Google SpeechRecognizer (must be initialized on Main Thread)
        handler.post(() -> {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(this)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                    speechRecognizer.setRecognitionListener(new LiveRecognitionListener());

                    recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                    
                    isInitialized = true;
                    // Start listening if the service was started with ACTION_START
                    if (isInitialized) {
                       restartListening();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create SpeechRecognizer", e);
            }
        });
    }

    private class LiveRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(android.os.Bundle params) { isListening = true; }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { isListening = false; }

        @Override
        public void onError(int error) {
            Log.w(TAG, "Recognizer error: " + error);
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
                Log.w(TAG, "startListening failed", e);
                isListening = false;
            }
        }
    }

    private void restartListeningWithDelay() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::restartListening, 500);
    }

    private void processResults(@Nullable android.os.Bundle results) {
        if (usingVosk || results == null) return;
        ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (texts == null || texts.isEmpty()) return;
        
        String joined = String.join(" ", texts).toLowerCase(Locale.getDefault());
        for (String k : KEYWORDS) {
            if (joined.contains(k)) {
                Intent i = new Intent(this, ScamOverlayService.class);
                i.putExtra("action", "SHOW_ALERT");
                i.putExtra("keywords", k);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i);
                } else {
                    startService(i);
                }
                break;
            }
        }
    }

    private void stopListening() {
        handler.removeCallbacksAndMessages(null);
        if (usingVosk) {
            if (voskProcessor != null && voskProcessor.isRunning()) voskProcessor.stop();
            return;
        }
        try {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            }
        } catch (Exception ignored) {}
        isListening = false;
    }

    private void startForegroundNotification() {
        String CHANNEL_ID = "live_detection_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Live Detection", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ScamShield - Live detection")
                .setContentText("Listening for suspicious keywords...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(201, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(201, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            startForegroundNotification();
            if (isInitialized) {
                if (usingVosk && voskProcessor != null) {
                    if (!voskProcessor.isRunning()) voskProcessor.start();
                } else {
                    restartListening();
                }
            }
        } else if (ACTION_STOP.equals(action)) {
            stopListening();
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopListening();
        if (voskReceiver != null) {
            try { unregisterReceiver(voskReceiver); } catch (Exception ignored) {}
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        executorService.shutdownNow();
    }

    @Nullable
    @Override public IBinder onBind(Intent intent) { return null; }
}