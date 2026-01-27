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
    private static final String TAG = "VoskProcessor";
    private boolean running = false;
    private final Context context;
    private final String modelPath;

    // Reflection handles
    private Object modelInstance = null;
    private Object recognizerInstance = null;
    private Object speechServiceInstance = null;

    private Thread pollThread = null;

    public VoskProcessor(Context context) {
        this.context = context.getApplicationContext();
        this.modelPath = new File(this.context.getFilesDir(), "vosk-model").getAbsolutePath();
    }

    public boolean isAvailable() {
        // Check model folder presence and Vosk classes availability
        File m = new File(modelPath);
        if (!m.exists() || !m.isDirectory()) return false;

        try {
            Class.forName("org.vosk.Model");
            Class.forName("org.vosk.Recognizer");
            Class.forName("org.vosk.android.SpeechService");
            return true;
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "Vosk classes not present on classpath: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void start() {
        if (running) return;
        if (!isAvailable()) {
            Log.w(TAG, "Vosk not available or model missing");
            running = false;
            return;
        }

        try {
            // Load org.vosk.Model model = new Model(modelPath);
            Class<?> modelClass = Class.forName("org.vosk.Model");
            Constructor<?> modelCtor = modelClass.getConstructor(String.class);
            modelInstance = modelCtor.newInstance(modelPath);

            // Recognizer recognizer = new Recognizer(model, 16000.0f);
            Class<?> recognizerClass = Class.forName("org.vosk.Recognizer");
            Constructor<?> recCtor = recognizerClass.getConstructor(modelClass, float.class);
            recognizerInstance = recCtor.newInstance(modelInstance, 16000.0f);

            // SpeechService speech = new org.vosk.android.SpeechService(recognizer, 16000.0f);
            Class<?> speechServiceClass = Class.forName("org.vosk.android.SpeechService");
            Constructor<?> speechCtor = speechServiceClass.getConstructor(recognizerClass, float.class);
            speechServiceInstance = speechCtor.newInstance(recognizerInstance, 16000.0f);

            // speech.start();
            Method startMethod = speechServiceClass.getMethod("start");
            startMethod.invoke(speechServiceInstance);

            running = true;

            // Start poll thread to read partial/result and broadcast detections
            pollThread = new Thread(() -> {
                try {
                    Method getPartial = null;
                    Method getResult = null;
                    try {
                        getPartial = recognizerInstance.getClass().getMethod("getPartialResult");
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        getResult = recognizerInstance.getClass().getMethod("getResult");
                    } catch (NoSuchMethodException ignored) {}

                    while (running) {
                        try {
                            String json = null;
                            if (getPartial != null) {
                                Object o = getPartial.invoke(recognizerInstance);
                                if (o != null) json = o.toString();
                            }
                            if ((json == null || json.isEmpty()) && getResult != null) {
                                Object o2 = getResult.invoke(recognizerInstance);
                                if (o2 != null) json = o2.toString();
                            }

                            if (json != null && !json.isEmpty()) {
                                String text = extractTextFromVoskJson(json);
                                if (text != null && !text.isEmpty()) {
                                    // simple keyword matching; broadcast match
                                    String lower = text.toLowerCase();
                                    for (String k : new String[]{"otp","one time password","pin","password","bank","transfer","money","verify","upi"}) {
                                        if (lower.contains(k)) {
                                            Intent i = new Intent("com.shreyanshi.scamshield.VOSK_DETECTED");
                                            i.putExtra("keywords", k);
                                            i.putExtra("text", text);
                                            context.sendBroadcast(i);
                                            break;
                                        }
                                    }
                                }
                            }

                            // sleep briefly to avoid busy spinning
                            try { Thread.sleep(300); } catch (InterruptedException ie) { break; }
                        } catch (Exception e) {
                            Log.w(TAG, "Error polling Vosk recognizer: " + e.getMessage());
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Vosk poll thread failure: " + t.getMessage());
                }
            }, "vosk-poll");
            pollThread.start();

            Log.i(TAG, "Vosk live detection started with model at " + modelPath);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Vosk processor: " + e.getMessage(), e);
            running = false;
            // try to clean up partial instances
            stop();
        }
    }

    private String extractTextFromVoskJson(String json) {
        if (json == null) return null;
        try {
            JSONObject obj = new JSONObject(json);
            // prefer partial, then text
            if (obj.has("partial")) {
                String p = obj.optString("partial", "").trim();
                if (!p.isEmpty()) return p;
            }
            if (obj.has("text")) {
                String t = obj.optString("text", "").trim();
                if (!t.isEmpty()) return t;
            }
        } catch (JSONException je) {
            // not strictly JSON, fall back to raw string
            Log.w(TAG, "Failed to parse Vosk JSON: " + je.getMessage());
        }
        // fallback: return the raw JSON
        return json;
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        try {
            if (pollThread != null) {
                pollThread.interrupt();
                pollThread = null;
            }
            if (speechServiceInstance != null) {
                Method stopMethod = speechServiceInstance.getClass().getMethod("stop");
                stopMethod.invoke(speechServiceInstance);
            }

            // Attempt to free recognizer/model
            if (recognizerInstance != null) {
                Method free = recognizerInstance.getClass().getMethod("close");
                free.invoke(recognizerInstance);
            }
            if (modelInstance != null) {
                Method freeModel = modelInstance.getClass().getMethod("close");
                freeModel.invoke(modelInstance);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error while stopping Vosk processor: " + e.getMessage());
        } finally {
            speechServiceInstance = null;
            recognizerInstance = null;
            modelInstance = null;
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
