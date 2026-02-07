package com.shreyanshi.scamshield.stt;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.IOException;

public class VoskProcessor implements RecognitionListener {

    private final Context context;
    private Model model;
    private SpeechStreamService speechStreamService;
    private final SpeechProcessor.Listener listener;

    private static final String MODEL_PATH_KEY = "vosk-model";
    private static final String TAG = "ScamShield-Vosk";

    public VoskProcessor(Context context, SpeechProcessor.Listener listener) {
        this.context = context;
        this.listener = listener;
        initModel();
    }

    private void initModel() {
        File sourceDir = new File(context.getFilesDir(), MODEL_PATH_KEY);
        if (!sourceDir.exists()) {
            Log.d(TAG, "Model folder not found in internal storage. Unpacking from assets...");
            Toast.makeText(context, "Preparing offline model for first use...", Toast.LENGTH_LONG).show();
            // Use a background thread to avoid blocking the main thread
            new Thread(() -> {
                try {
                    StorageService.unpack(context, MODEL_PATH_KEY, MODEL_PATH_KEY,
                        (model) -> {
                            this.model = model;
                            Log.d(TAG, "Model unpacked successfully.");
                        },
                        (exception) -> {
                            Log.e(TAG, "Failed to unpack model from assets.", exception);
                            // Optionally, inform the user on the main thread
                            // new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, "Error: Could not load detection model.", Toast.LENGTH_SHORT).show());
                        });
                } catch (Exception e) {
                     Log.e(TAG, "Unpacking failed catastrophically.", e);
                }
            }).start();
        } else {
            Log.d(TAG, "Model found in internal storage. Loading...");
            this.model = new Model(sourceDir.getAbsolutePath());
        }
    }

    public void startListening() {
        if (model == null) {
            Log.e(TAG, "Cannot start listening, model is not loaded!");
            Toast.makeText(context, "Error: Detection model not ready.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Correctly escaped JSON array string for grammar/keywords
            Recognizer rec = new Recognizer(model, 16000.0f, "[\"otp\", \"bank\", \"account\", \"blocked\", \"verify\", \"card\", \"password\"]");
            speechStreamService = new SpeechStreamService(rec, 16000);
            speechStreamService.start(this);
            Log.d(TAG, "Vosk is now listening.");
        } catch (IOException e) {
            Log.e(TAG, "Error starting to listen.", e);
        }
    }

    public void stopListening() {
        if (speechStreamService != null) {
            speechStreamService.stop();
            speechStreamService = null;
            Log.d(TAG, "Vosk has stopped listening.");
        }
    }

    @Override
    public void onResult(String hypothesis) {
        try {
            JSONObject json = new JSONObject(hypothesis);
            String text = json.getString("text");
            if (text != null && !text.isEmpty()) {
                Log.i(TAG, "Hearing (Final): " + text);
                listener.onSpeechRecognized(text);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not parse final result: " + hypothesis, e);
        }
    }

    @Override
    public void onFinalResult(String hypothesis) {
        // Handled by onResult
    }

    @Override
    public void onPartialResult(String hypothesis) {
        try {
            JSONObject json = new JSONObject(hypothesis);
            String text = json.getString("partial");
            if (text != null && !text.isEmpty()) {
                Log.i(TAG, "Hearing (Partial): " + text);
                listener.onSpeechRecognized(text);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not parse partial result: " + hypothesis, e);
        }
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "Recognition error", e);
    }

    @Override
    public void onTimeout() {
        // This is normal, just means a pause in speech.
        Log.d(TAG, "Recognition timeout.");
    }
}
