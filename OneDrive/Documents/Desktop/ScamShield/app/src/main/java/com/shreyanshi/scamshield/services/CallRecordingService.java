package com.shreyanshi.scamshield.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.utils.ScamDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CallRecordingService extends Service {

    public static String lastRecordingPath = null;
    private MediaRecorder recorder;

    // Speech recognizer for realtime listening
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;

    // Backoff handler for restart
    private Handler backoffHandler;
    private int backoffAttempts = 0;
    private static final int MAX_BACKOFF_ATTEMPTS = 6; // up to ~32s

    // throttle alerts (ms)
    private static final long ALERT_THROTTLE_MS = 30_000; // 30 seconds
    private long lastAlertTime = 0;

    private static final String CHANNEL_ID = "scamshield_alerts";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        try {
            File dir = new File(getExternalFilesDir(null), "recordings");
            if (!dir.exists()) dir.mkdirs();

            lastRecordingPath = dir.getAbsolutePath()
                    + "/call_" + System.currentTimeMillis() + ".3gp";

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(lastRecordingPath);

            recorder.prepare();
            recorder.start();

            Log.d("Recorder", "Recording started");

            // prepare backoff handler
            HandlerThread ht = new HandlerThread("SpeechBackoff");
            ht.start();
            backoffHandler = new Handler(ht.getLooper());

            // Start speech recognizer if permission granted
            startSpeechRecognizerIfPermitted();

            // Promote to foreground on newer OS versions to reduce being killed
            startForegroundIfNeeded();

        } catch (Exception e) {
            Log.e("Recorder", "Recording failed", e);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            stopSpeechRecognizer();

            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
                Log.d("Recorder", "Recording stopped");
            }

            if (backoffHandler != null) {
                backoffHandler.getLooper().quitSafely();
                backoffHandler = null;
            }

        } catch (Exception e) {
            Log.e("Recorder", "Stop error", e);
        }
        super.onDestroy();
    }

    private void startSpeechRecognizerIfPermitted() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w("CallRecordingService", "RECORD_AUDIO permission not granted; skipping live detection");
                return;
            }

            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.w("CallRecordingService", "Speech recognition not available on this device");
                return;
            }

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(new MyRecognitionListener());
            }

            if (recognizerIntent == null) {
                recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            }

            if (!isListening) {
                speechRecognizer.startListening(recognizerIntent);
                isListening = true;
                // reset backoff on success
                backoffAttempts = 0;
            }

        } catch (Exception e) {
            Log.e("CallRecordingService", "Failed to start SpeechRecognizer", e);
            scheduleRestartWithBackoff();
        }
    }

    private void scheduleRestartWithBackoff() {
        if (backoffHandler == null) return;
        backoffAttempts = Math.min(backoffAttempts + 1, MAX_BACKOFF_ATTEMPTS);
        long delay = (1L << backoffAttempts) * 500; // exponential: 1s,2s,4s... *500ms base
        if (delay > 60_000) delay = 60_000; // cap at 60s
        Log.w("SpeechRec", "Scheduling restart in " + delay + "ms (attempt " + backoffAttempts + ")");
        backoffHandler.postDelayed(this::tryRestartListening, delay);
    }

    private void tryRestartListening() {
        try {
            if (speechRecognizer == null) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED && SpeechRecognizer.isRecognitionAvailable(this)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                    speechRecognizer.setRecognitionListener(new MyRecognitionListener());
                } else {
                    return;
                }
            }

            if (recognizerIntent == null) {
                recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            }

            if (!isListening) {
                speechRecognizer.startListening(recognizerIntent);
                isListening = true;
                backoffAttempts = 0; // success
            }
        } catch (Exception e) {
            Log.e("SpeechRec", "tryRestartListening failed", e);
            scheduleRestartWithBackoff();
        }
    }

    private void restartListeningWithDelay() {
        // simpler restart: schedule a short restart via backoff handler
        try {
            if (backoffHandler != null) {
                backoffHandler.postDelayed(() -> {
                    try {
                        if (speechRecognizer != null) {
                            speechRecognizer.stopListening();
                            speechRecognizer.cancel();
                        }
                    } catch (Exception ignored) {}
                    isListening = false;
                    scheduleRestartWithBackoff();
                }, 300);
            }
        } catch (Exception e) {
            Log.e("SpeechRec", "restartListeningWithDelay error", e);
        }
    }

    private void stopSpeechRecognizer() {
        try {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.stopListening();
                } catch (Exception ignored) {
                }
                speechRecognizer.cancel();
                speechRecognizer.destroy();
                speechRecognizer = null;
                isListening = false;
            }
        } catch (Exception e) {
            Log.e("SpeechRec", "Error stopping recognizer", e);
        }
    }

    private void processTranscript(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) return;
        String t = transcript.toLowerCase();
        Log.d("SpeechRec", "Transcript: " + t);

        List<String> matches = ScamDetector.detectKeywords(t);
        if (matches != null && !matches.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastAlertTime < ALERT_THROTTLE_MS) {
                Log.d("ScamDetect", "Alert throttled");
                return;
            }
            lastAlertTime = now;

            String message = ScamDetector.buildAlertMessage(matches, transcript);
            showAlertNotification(message, transcript, matches);
            // attempt immediate popup activity (best-effort)
            try {
                Intent i = new Intent();
                i.putExtra("extra_audio", lastRecordingPath);
                i.putExtra("extra_transcript", transcript);
                i.putStringArrayListExtra("extra_matches", new ArrayList<>(matches));
                i.setClassName(getPackageName(), "com.shreyanshi.scamshield.activities.ScamAlertActivity");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
            } catch (Exception e) {
                Log.w("ScamDetect", "Could not start alert activity from background", e);
            }
        }
    }

    private void showAlertNotification(String message, String transcript, List<String> matches) {
        try {
            createNotificationChannelIfNeeded();

            Intent intent = new Intent();
            intent.putExtra("extra_audio", lastRecordingPath);
            intent.putExtra("extra_transcript", transcript);
            intent.putStringArrayListExtra("extra_matches", new ArrayList<>(matches));
            intent.setClassName(getPackageName(), "com.shreyanshi.scamshield.activities.ScamAlertActivity");

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Potential scam detected")
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message + "\n" + transcript))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setDefaults(Notification.DEFAULT_ALL);

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), builder.build());

        } catch (Exception e) {
            Log.e("ScamNotify", "Failed to post notification", e);
        }
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
                if (ch == null) {
                    ch = new NotificationChannel(CHANNEL_ID, "ScamShield Alerts", NotificationManager.IMPORTANCE_HIGH);
                    ch.setDescription("Alerts when potential scam words are detected during calls");
                    nm.createNotificationChannel(ch);
                }
            } catch (Exception e) {
                Log.e("ScamNotify", "channel create failed", e);
            }
        }
    }

    private void startForegroundIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                createNotificationChannelIfNeeded();
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Call recording")
                        .setContentText("Recording active")
                        .setPriority(NotificationCompat.PRIORITY_LOW);
                startForeground(1111, builder.build());
            } catch (Exception e) {
                Log.w("CallRecordingService", "startForeground failed", e);
            }
        }
    }

    // Named RecognitionListener to satisfy static analysis and keep code readable
    private class MyRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(android.os.Bundle params) {
            Log.d("SpeechRec", "Ready for speech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d("SpeechRec", "Beginning of speech");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            Log.d("SpeechRec", "End of speech");
            restartListeningWithDelay();
        }

        @Override
        public void onError(int error) {
            Log.w("SpeechRec", "Error: " + error);
            restartListeningWithDelay();
        }

        @Override
        public void onPartialResults(android.os.Bundle partialResults) {
            if (partialResults != null) {
                ArrayList<String> texts = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (texts != null && !texts.isEmpty()) {
                    processTranscript(texts.get(0));
                }
            }
        }

        @Override
        public void onResults(android.os.Bundle results) {
            if (results != null) {
                ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (texts != null && !texts.isEmpty()) {
                    processTranscript(texts.get(0));
                }
            }
            restartListeningWithDelay();
        }

        @Override
        public void onEvent(int eventType, android.os.Bundle params) {
        }
    }
}
