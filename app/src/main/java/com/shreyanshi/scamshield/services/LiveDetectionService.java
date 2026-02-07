package com.shreyanshi.scamshield.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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

import com.shreyanshi.scamshield.stt.SpeechProcessor;
import com.shreyanshi.scamshield.stt.VoskProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LiveDetectionService extends Service implements SpeechProcessor.Listener {
    private static final String TAG = "ScamShield-LiveDetect";
    public static final String ACTION_START = "com.shreyanshi.scamshield.ACTION_START_LIVE_DETECTION";
    public static final String ACTION_STOP = "com.shreyanshi.scamshield.ACTION_STOP_LIVE_DETECTION";

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private VoskProcessor voskProcessor = null;
    private boolean usingVosk = false;

    private final List<String> SCAM_KEYWORDS = Arrays.asList(
            "otp", "one time password", "pin", "password", "account blocked", "verify your account",
            "bank", "transfer", "money", "verify", "card number", "upi", "paytm", "netbanking",
            "reset password", "remote access", "confirm code", "lottery", "gift card", "customer care",
            "blocked", "locked", "account"
    );

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        
        // Initialize Vosk
        voskProcessor = new VoskProcessor(this, this);
        if (voskProcessor.isAvailable()) {
            usingVosk = true;
            voskProcessor.start();
            Log.i(TAG, "Vosk detection initialized");
        } else {
            Log.w(TAG, "Vosk model not found, falling back to Google Speech");
            setupGoogleSpeech();
        }
    }

    private void setupGoogleSpeech() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new GoogleRecognitionListener());

            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            
            startListeningGoogle();
        }
    }

    private void startListeningGoogle() {
        if (speechRecognizer != null && !isListening) {
            try {
                speechRecognizer.startListening(recognizerIntent);
            } catch (Exception e) {
                Log.e(TAG, "Google start failed: " + e.getMessage());
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
        Log.w(TAG, "!!! SCAM KEYWORD DETECTED: " + detectedKeyword);
        Intent i = new Intent(this, ScamOverlayService.class);
        i.putExtra("action", "SHOW_ALERT");
        i.putExtra("keywords", detectedKeyword);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private class GoogleRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(android.os.Bundle params) { isListening = true; }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() { isListening = false; }
        @Override public void onError(int error) {
            isListening = false;
            handler.postDelayed(() -> startListeningGoogle(), 1000);
        }
        @Override
        public void onResults(android.os.Bundle results) {
            processResults(results);
            isListening = false;
            startListeningGoogle();
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

    private void startForegroundNotification() {
        String CHANNEL_ID = "live_detection_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Scam Monitoring", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ScamShield Monitor Active")
                .setContentText("Listening for suspicious activity...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(201, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(201, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (voskProcessor != null) voskProcessor.stop();
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }

    @Nullable
    @Override public IBinder onBind(Intent intent) { return null; }
}
