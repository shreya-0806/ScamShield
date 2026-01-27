package com.shreyanshi.scamshield.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
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

public class LiveDetectionService extends Service {
    private static final String TAG = "LiveDetectionService";
    public static final String ACTION_START = "com.shreyanshi.scamshield.ACTION_START_LIVE_DETECTION";
    public static final String ACTION_STOP = "com.shreyanshi.scamshield.ACTION_STOP_LIVE_DETECTION";

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;

    private SpeechProcessor voskProcessor = null;
    private boolean usingVosk = false;

    private BroadcastReceiver voskReceiver = null;

    // Small curated keyword list for PoC
    private final List<String> KEYWORDS = Arrays.asList(
            "otp", "one time password", "pin", "password", "otp code", "account number",
            "bank", "transfer", "money", "verify", "card number", "upi", "paytm", "netbanking",
            "reset password", "remote access", "confirm code"
    );

    @Override
    public void onCreate() {
        super.onCreate();
        // Try to initialize VoskProcessor first
        try {
            VoskProcessor vp = new VoskProcessor(this);
            if (vp.isAvailable()) {
                voskProcessor = vp;
                usingVosk = true;
                voskProcessor.start();

                // register receiver for Vosk detection broadcasts
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
                registerReceiver(voskReceiver, new IntentFilter("com.shreyanshi.scamshield.VOSK_DETECTED"));

                Log.i(TAG, "Using VoskProcessor for live detection");
                return; // Vosk will handle detection; SpeechRecognizer not needed
            }
        } catch (Throwable t) {
            Log.w(TAG, "VoskProcessor init failed: " + t.getMessage());
            usingVosk = false;
            voskProcessor = null;
        }

        // Fallback to SpeechRecognizer PoC
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(new LiveRecognitionListener());

                recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            } else {
                Log.w(TAG, "Speech recognition not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create SpeechRecognizer", e);
        }
    }

    private class LiveRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(android.os.Bundle params) { }

        @Override
        public void onBeginningOfSpeech() { }

        @Override
        public void onRmsChanged(float rmsdB) { }

        @Override
        public void onBufferReceived(byte[] buffer) { }

        @Override
        public void onEndOfSpeech() { }

        @Override
        public void onError(int error) {
            Log.w(TAG, "Recognizer error: " + error);
            restartListeningWithDelay();
        }

        @Override
        public void onResults(android.os.Bundle results) {
            processResults(results);
            restartListening();
        }

        @Override
        public void onPartialResults(android.os.Bundle partialResults) {
            processResults(partialResults);
        }

        @Override
        public void onEvent(int eventType, android.os.Bundle params) { }
    }

    private void restartListening() {
        if (usingVosk) return; // Vosk handles its own lifecycle
        if (speechRecognizer != null && !isListening) {
            try {
                isListening = true;
                speechRecognizer.startListening(recognizerIntent);
            } catch (Exception e) {
                Log.w(TAG, "startListening failed", e);
                isListening = false;
            }
        }
    }

    private void restartListeningWithDelay() {
        // simple restart without scheduling complexity for PoC
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        isListening = false;
        restartListening();
    }

    private void processResults(@Nullable android.os.Bundle results) {
        if (usingVosk) return; // Vosk triggers its own detection and will call overlay directly
        if (results == null) return;
        ArrayList<String> texts = new ArrayList<>();
        try {
            Object obj = results.get(SpeechRecognizer.RESULTS_RECOGNITION);
            if (obj instanceof ArrayList) {
                //noinspection unchecked
                texts = (ArrayList<String>) obj;
            }
        } catch (Exception ignored) { }
        if (texts == null || texts.isEmpty()) return;
        String joined = String.join(" ", texts).toLowerCase(Locale.getDefault());
        for (String k : KEYWORDS) {
            if (joined.contains(k)) {
                // send overlay alert
                Intent i = new Intent(this, ScamOverlayService.class);
                i.putExtra("action", "SHOW_ALERT");
                i.putExtra("keywords", k);
                startService(i);
                break;
            }
        }
    }

    private void stopListening() {
        if (usingVosk) {
            if (voskProcessor != null && voskProcessor.isRunning()) voskProcessor.stop();
            usingVosk = false;
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
                .build();
        try {
            startForeground(201, notification);
        } catch (SecurityException se) {
            // Some OEMs/Android versions restrict microphone-type FGS. Fall back to posting a regular notification to avoid crash.
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(201, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            startForegroundNotification();
            // Permission check done by caller; try to start
            if (usingVosk && voskProcessor != null && !voskProcessor.isRunning()) {
                voskProcessor.start();
            } else {
                isListening = false;
                restartListening();
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
        try {
            if (voskReceiver != null) {
                try { unregisterReceiver(voskReceiver); } catch (Exception ignored) {}
                voskReceiver = null;
            }
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
                speechRecognizer = null;
            }
        } catch (Exception ignored) {}
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
