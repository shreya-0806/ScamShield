package com.shreyanshi.scamshield.stt;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoskProcessor implements SpeechProcessor, RecognitionListener {

    private final Context context;
    private Model model;
    private SpeechService speechService;
    private final SpeechListener listener;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private static final String MODEL_PATH_KEY = "vosk-model";
    private static final String TAG = "ScamShield-Vosk";

    public VoskProcessor(Context context, SpeechListener listener) {
        this.context = context;
        this.listener = listener;
        initModelAsync();
    }

    public void initModel(File sourceDir) throws IOException {
        model = new Model(sourceDir.getAbsolutePath());
    }

    private void initModelAsync() {
        executorService.execute(() -> {
            File sourceDir = new File(context.getFilesDir(), MODEL_PATH_KEY);
            if (!sourceDir.exists()) {
                Log.d(TAG, "Model folder not found. Unpacking from assets in background...");
                StorageService.unpack(context, MODEL_PATH_KEY, MODEL_PATH_KEY,
                    (m) -> {
                        this.model = m;
                        Log.d(TAG, "Model unpacked and loaded successfully.");
                    },
                    (exception) -> {
                        Log.e(TAG, "Failed to unpack model from assets.", exception);
                    });
            } else {
                Log.d(TAG, "Model found in internal storage. Loading in background...");
                try {
                    initModel(sourceDir);
                    Log.d(TAG, "Model loaded successfully.");
                } catch (IOException e) {
                    Log.e(TAG, "Failed to load existing model", e);
                }
            }
        });
    }

    public boolean isAvailable() {
        return model != null;
    }

    @Override
    public void start() {
        if (model == null) {
            Log.e(TAG, "Cannot start: Model is still loading or missing.");
            return;
        }

        try {
            // Corrected syntax for keyword list
            Recognizer rec = new Recognizer(model, 16000.0f, "[\"otp\", \"bank\", \"account\", \"blocked\", \"verify\", \"card\", \"password\"]");
            speechService = new SpeechService(rec, 16000.0f);
            speechService.startListening(this);
            Log.d(TAG, "Vosk is now listening.");
        } catch (IOException e) {
            Log.e(TAG, "Error starting to listen.", e);
        }
    }

    @Override
    public void stop() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
            Log.d(TAG, "Vosk has stopped listening.");
        }
    }

    @Override
    public boolean isRunning() {
        return speechService != null;
    }

    @Override
    public void onResult(String hypothesis) {
        processHypothesis(hypothesis, "text");
    }

    @Override
    public void onFinalResult(String hypothesis) {
        processHypothesis(hypothesis, "text");
    }

    @Override
    public void onPartialResult(String hypothesis) {
        processHypothesis(hypothesis, "partial");
    }

    private void processHypothesis(String hypothesis, String key) {
        try {
            JSONObject json = new JSONObject(hypothesis);
            String text = json.optString(key);
            if (!text.isEmpty()) {
                Log.i(TAG, "Hearing (" + key + "): " + text);
                if (listener != null) {
                    listener.onSpeechRecognized(text);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not parse result: " + hypothesis, e);
        }
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "Recognition error", e);
    }

    @Override
    public void onTimeout() {
        Log.d(TAG, "Recognition timeout.");
    }
}
