package com.shreyanshi.scamshield.stt;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class VoskProcessor implements SpeechProcessor {
    private static final String TAG = "ScamShield-Vosk";
    private boolean running = false;
    private final Context context;
    private final String modelPath;

    private Object modelInstance = null;
    private Object recognizerInstance = null;
    private Object speechServiceInstance = null;

    private Thread pollThread = null;

    public VoskProcessor(Context context) {
        this.context = context.getApplicationContext();
        this.modelPath = new File(this.context.getFilesDir(), "vosk-model").getAbsolutePath();
    }

    public boolean isAvailable() {
        File m = new File(modelPath);
        if (!m.exists() || !m.isDirectory()) {
            Log.w(TAG, "Model folder missing at: " + modelPath + ". Offline detection will not work.");
            return false;
        }

        try {
            Class.forName("org.vosk.Model");
            Class.forName("org.vosk.Recognizer");
            Class.forName("org.vosk.android.SpeechService");
            return true;
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "Vosk library not found in project");
            return false;
        }
    }

    @Override
    public void start() {
        if (running) return;
        if (!isAvailable()) return;

        try {
            Class<?> modelClass = Class.forName("org.vosk.Model");
            Constructor<?> modelCtor = modelClass.getConstructor(String.class);
            modelInstance = modelCtor.newInstance(modelPath);

            Class<?> recognizerClass = Class.forName("org.vosk.Recognizer");
            Constructor<?> recCtor = recognizerClass.getConstructor(modelClass, float.class);
            recognizerInstance = recCtor.newInstance(modelInstance, 16000.0f);

            Class<?> speechServiceClass = Class.forName("org.vosk.android.SpeechService");
            Constructor<?> speechCtor = speechServiceClass.getConstructor(recognizerClass, float.class);
            speechServiceInstance = speechCtor.newInstance(recognizerInstance, 16000.0f);

            Method startMethod = speechServiceClass.getMethod("start");
            startMethod.invoke(speechServiceInstance);

            running = true;

            pollThread = new Thread(() -> {
                Log.d(TAG, "Vosk Poll Thread Started");
                try {
                    Method getPartial = recognizerInstance.getClass().getMethod("getPartialResult");
                    Method getResult = recognizerInstance.getClass().getMethod("getResult");

                    while (running) {
                        String json = (String) getPartial.invoke(recognizerInstance);
                        // Check partial results for instant detection
                        if (json != null && !json.isEmpty() && !json.contains("\"partial\" : \"\"")) {
                            String text = extractTextFromVoskJson(json);
                            if (text != null && !text.isEmpty()) {
                                Log.v(TAG, "Hearing (Partial): " + text);
                                checkKeywords(text);
                            }
                        }
                        
                        // Small sleep to prevent CPU hammering
                        Thread.sleep(300);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Poll Thread Error: " + e.getMessage());
                }
            });
            pollThread.start();

            Log.i(TAG, "Vosk Started Successfully");
        } catch (Exception e) {
            Log.e(TAG, "Start Failed: " + e.getMessage());
            running = false;
        }
    }

    private void checkKeywords(String text) {
        String lower = text.toLowerCase();
        // Expanded keyword list for better detection
        String[] keywords = {
            "otp", "password", "pin", "bank", "transfer", "money", 
            "verify", "card", "upi", "paytm", "blocked", "account", "locked"
        };
        
        for (String k : keywords) {
            if (lower.contains(k)) {
                Log.w(TAG, "!!! SCAM KEYWORD DETECTED: " + k + " in text: " + text);
                Intent i = new Intent("com.shreyanshi.scamshield.VOSK_DETECTED");
                i.putExtra("keywords", k);
                i.putExtra("text", text);
                context.sendBroadcast(i);
                // Don't break here if we want to log everything, but one broadcast is enough
                break; 
            }
        }
    }

    private String extractTextFromVoskJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("partial")) return obj.getString("partial").trim();
            if (obj.has("text")) return obj.getString("text").trim();
        } catch (JSONException ignored) {}
        return null;
    }

    @Override
    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
        try {
            if (speechServiceInstance != null) {
                speechServiceInstance.getClass().getMethod("stop").invoke(speechServiceInstance);
            }
        } catch (Exception ignored) {}
        Log.i(TAG, "Vosk Stopped");
    }

    @Override
    public boolean isRunning() { return running; }
}
