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
    private static final String TAG = "ScamShield-LiveDetect";
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
            "reset password", "remote access", "confirm code", "lottery", "gift card", "customer care",
            "blocked", "locked", "account"
    );

    @Override
    public void onCreate() {
        super.onCreate();
        // Start foreground IMMEDIATELY to prevent Android 14 background start crash
        startForegroundNotification();
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
                        if (intent != null && "com.shreyanshi.scamshield.VOSK_DETECTED".equals(intent.getAction())) {
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
                Log.i(TAG, "VoskProcessor started successfully");
                isInitialized = true;
                return;
            } else {
                Log.w(TAG, "Vosk model not available, falling back to Google Speech");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Vosk init error: " + t.getMessage());
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
                } else {
                    Log.e(TAG, "Google Speech Recognition not available");
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
        @Override public void onReadyForSpeech(android.os.Bundle params) { 
            isListening = true; 
            Log.d(TAG, "Recognition Ready");
        }
        @Override public void onBeginningOfSpeech() { Log.d(TAG, "Speech Beginning"); }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { 
            isListening = false; 
            Log.d(TAG, "Speech End");
        }

        @Override
        public void onError(int error) {
            String message;
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO: message = "Audio error"; break;
                case SpeechRecognizer.ERROR_CLIENT: message = "Client error"; break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Insufficient permissions"; break;
                case SpeechRecognizer.ERROR_NETWORK: message = "Network error"; break;
                case SpeechRecognizer.ERROR_NO_MATCH: message = "No match"; break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "Recognizer busy"; break;
                case SpeechRecognizer.ERROR_SERVER: message = "Server error"; break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "Speech timeout"; break;
                default: message = "Unknown error: " + error; break;
            }
            Log.e(TAG, "SpeechRecognizer Error: " + message);
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
                Log.e(TAG, "startListening failed: " + e.getMessage());
                isListening = false;
            }
        }
    }

    private void restartListeningWithDelay() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::restartListening, 2000);
    }

    private void processResults(@Nullable android.os.Bundle results) {
        if (results == null) return;
        ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (texts == null || texts.isEmpty()) return;
        
        String joined = String.join(" ", texts).toLowerCase(Locale.getDefault());
        Log.d(TAG, "Heard: " + joined);
        for (String k : SCAM_KEYWORDS) {
            if (joined.contains(k)) {
                triggerAlert(k);
                break;
            }
        }
    }

    private void startForegroundNotification() {
        try {
            String CHANNEL_ID = "live_detection_channel";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Live Detection Monitoring", NotificationManager.IMPORTANCE_LOW);
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.createNotificationChannel(channel);
            }
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("ScamShield - Call Monitor")
                    .setContentText("Listening for suspicious patterns...")
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(201, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(201, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not start foreground: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Ensure foreground notification is showing
        startForegroundNotification();
        
        if (isInitialized) {
            if (usingVosk && voskProcessor != null) {
                if (!voskProcessor.isRunning()) voskProcessor.start();
            } else {
                restartListening();
            }
        }
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service Destroyed");
        if (voskProcessor != null) voskProcessor.stop();
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }
        if (voskReceiver != null) {
            try {
                unregisterReceiver(voskReceiver);
            } catch (Exception ignored) {}
        }
        executorService.shutdownNow();
    }

    @Nullable
    @Override public IBinder onBind(Intent intent) { return null; }
}