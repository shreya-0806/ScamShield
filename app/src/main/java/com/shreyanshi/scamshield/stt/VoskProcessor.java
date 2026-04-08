package com.shreyanshi.scamshield.stt;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoskProcessor implements SpeechProcessor, RecognitionListener {

    private final Context context;
    private Model model;
    private SpeechService speechService;
    private final SpeechListener listener;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean modelLoading = new AtomicBoolean(false);

    private static final String MODEL_PATH_KEY = "vosk-model";
    private static final String MODEL_DIR_NAME = "vosk-model";
    private static final String TAG = "ScamShield-Vosk";

    public VoskProcessor(Context context, SpeechListener listener) {
        this.context = context;
        this.listener = listener;
        initModelAsync();
    }

    public void initModel(File sourceDir) throws IOException {
        if (sourceDir.exists() && sourceDir.isDirectory()) {
            File amDir = new File(sourceDir, "am");
            if (amDir.exists()) {
                model = new Model(sourceDir.getAbsolutePath());
                return;
            }
        }
        throw new IOException("Invalid model directory structure");
    }

    private void initModelAsync() {
        if (modelLoading.getAndSet(true)) {
            return;
        }
        
        executorService.execute(() -> {
            File modelDir = new File(context.getFilesDir(), MODEL_DIR_NAME);
            
            if (model != null) {
                return;
            }
            
            if (modelDir.exists() && isValidModel(modelDir)) {
                Log.d(TAG, "Loading existing model from: " + modelDir.getAbsolutePath());
                try {
                    initModel(modelDir);
                    Log.d(TAG, "Model loaded successfully from internal storage");
                    return;
                } catch (IOException e) {
                    Log.e(TAG, "Failed to load existing model, will try assets", e);
                    deleteCorruptedModel(modelDir);
                }
            }
            
            Log.d(TAG, "Model not found. Checking assets...");
            if (modelExistsInAssets()) {
                Log.d(TAG, "Model found in assets. Unpacking...");
                unpackModelFromAssets();
            } else {
                Log.e(TAG, "Model not found in assets either!");
            }
        });
    }

    private boolean modelExistsInAssets() {
        try {
            AssetManager assetManager = context.getAssets();
            String[] files = assetManager.list(MODEL_PATH_KEY);
            return files != null && files.length > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private void unpackModelFromAssets() {
        StorageService.unpack(context, MODEL_PATH_KEY, MODEL_DIR_NAME,
            (m) -> {
                this.model = m;
                Log.i(TAG, "Model unpacked and loaded successfully from assets");
            },
            (exception) -> {
                Log.e(TAG, "Failed to unpack model from assets", exception);
                modelLoading.set(false);
            });
    }

    private boolean isValidModel(File dir) {
        File amDir = new File(dir, "am");
        File graphDir = new File(dir, "graph");
        return amDir.exists() && graphDir.exists();
    }

    private void deleteCorruptedModel(File dir) {
        try {
            deleteRecursive(dir);
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete corrupted model", e);
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    public boolean isAvailable() {
        return model != null;
    }

    public boolean isModelReady() {
        return model != null;
    }

    @Override
    public void start() {
        if (model == null) {
            Log.e(TAG, "Cannot start: Model is still loading or missing.");
            return;
        }

        try {
            Recognizer rec = new Recognizer(model, 16000.0f);
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
