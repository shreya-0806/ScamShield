package com.shreyanshi.scamshield.stt;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class VoskProcessor {

    private final Context context;
    private Object model; // org.vosk.Model when available
    private Object speechService; // org.vosk.android.SpeechService when available
    private final Object listener; // use Object to avoid static access issues

    private static final String MODEL_PATH_KEY = "vosk-model";
    private static final String TAG = "ScamShield-Vosk";

    public VoskProcessor(Context context, Object listener) {
        this.context = context;
        this.listener = listener;
        initModel();
    }

    private void initModel() {
        File sourceDir = new File(context.getFilesDir(), MODEL_PATH_KEY);
        if (!sourceDir.exists()) {
            Log.d(TAG, "Model folder not found in internal storage. Skipping unpack (reflection fallback). If you want Vosk support, add the model to files/" + MODEL_PATH_KEY + " or add the Vosk dependency.");
            // We intentionally don't call the Vosk StorageService here to avoid compile-time dependency.
            return;
        } else {
            Log.d(TAG, "Model found in internal storage. Loading (reflection)...");
            try {
                initModel(sourceDir);
            } catch (IOException e) {
                Log.e(TAG, "Failed to load model via reflection", e);
            }
        }
    }

    // Public initializer as requested by you; tries to load the Vosk Model via reflection
    public void initModel(File sourceDir) throws IOException {
        try {
            Class<?> modelCls = Class.forName("org.vosk.Model");
            Constructor<?> ctor = modelCls.getConstructor(String.class);
            this.model = ctor.newInstance(sourceDir.getAbsolutePath());
        } catch (ClassNotFoundException cnf) {
            throw new IOException("Vosk Model class not found on classpath", cnf);
        } catch (ReflectiveOperationException roe) {
            throw new IOException("Failed to instantiate Vosk Model via reflection", roe);
        }
    }

    public boolean isAvailable() {
        File f = new File(context.getFilesDir(), MODEL_PATH_KEY);
        return model != null || (f.exists() && f.isDirectory());
    }

    public void start() {
        if (model == null) {
            File sourceDir = new File(context.getFilesDir(), MODEL_PATH_KEY);
            if (sourceDir.exists()) {
                try {
                    initModel(sourceDir);
                } catch (IOException e) {
                    Log.e(TAG, "Cannot start listening, failed to load model!", e);
                    return;
                }
            } else {
                Log.e(TAG, "Cannot start listening, model is not loaded!");
                return;
            }
        }

        try {
            // Use reflection to create Recognizer and SpeechService and start listening via a dynamic proxy
            Class<?> recognizerCls = Class.forName("org.vosk.Recognizer");
            Class<?> speechServiceCls = Class.forName("org.vosk.android.SpeechService");
            Class<?> recListenerCls = Class.forName("org.vosk.android.RecognitionListener");

            // Recognizer constructor: Recognizer(Model, float, String)
            Constructor<?> recCtor = recognizerCls.getConstructor(Class.forName("org.vosk.Model"), float.class, String.class);
            Object recognizer = recCtor.newInstance(this.model, 16000.0f, "[\"otp\", \"bank\", \"account\", \"blocked\", \"verify\", \"card\", \"password\"]");

            // SpeechService constructor: SpeechService(Recognizer, float)
            Constructor<?> svcCtor = speechServiceCls.getConstructor(recognizerCls, float.class);
            this.speechService = svcCtor.newInstance(recognizer, 16000.0f);

            // Create a dynamic proxy for RecognitionListener that forwards calls to this class
            Object listenerProxy = Proxy.newProxyInstance(
                    recListenerCls.getClassLoader(),
                    new Class[]{recListenerCls},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        try {
                            if ("onResult".equals(name) || "onFinalResult".equals(name)) {
                                if (args != null && args.length > 0 && args[0] instanceof String) {
                                    processHypothesis((String) args[0], "text");
                                }
                            } else if ("onPartialResult".equals(name)) {
                                if (args != null && args.length > 0 && args[0] instanceof String) {
                                    processHypothesis((String) args[0], "partial");
                                }
                            } else if ("onError".equals(name)) {
                                if (args != null && args.length > 0 && args[0] instanceof Exception) {
                                    Log.e(TAG, "Recognition error (from proxy)", (Exception) args[0]);
                                }
                            } else if ("onTimeout".equals(name)) {
                                Log.d(TAG, "Recognition timeout (from proxy)");
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "Error in recognition proxy", t);
                        }
                        return null;
                    }
            );

            // Invoke startListening(listenerProxy)
            Method startListening = speechServiceCls.getMethod("startListening", recListenerCls);
            startListening.invoke(this.speechService, listenerProxy);

            Log.d(TAG, "(Reflection) Vosk is now listening.");
        } catch (ClassNotFoundException cnf) {
            Log.e(TAG, "Vosk classes not on classpath, cannot start offline detection.", cnf);
        } catch (ReflectiveOperationException roe) {
            Log.e(TAG, "Reflection failure while starting Vosk.", roe);
        }
    }

    public void stop() {
        if (this.speechService != null) {
            try {
                Method stop = this.speechService.getClass().getMethod("stop");
                stop.invoke(this.speechService);
            } catch (Exception e) {
                Log.w(TAG, "Failed to stop speech service via reflection", e);
            }
            this.speechService = null;
            Log.d(TAG, "(Reflection) Vosk has stopped listening.");
        }
    }

    public boolean isRunning() {
        return this.speechService != null;
    }

    private void processHypothesis(String hypothesis, String key) {
        try {
            JSONObject json = new JSONObject(hypothesis);
            String text = json.optString(key);
            if (!text.isEmpty()) {
                Log.i(TAG, "Hearing (" + key + "): " + text);
                if (listener != null) {
                    try {
                        Method m = listener.getClass().getMethod("onSpeechRecognized", String.class);
                        m.invoke(listener, text);
                    } catch (NoSuchMethodException nsme) {
                        Log.w(TAG, "Listener object does not implement onSpeechRecognized(String)", nsme);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to call listener.onSpeechRecognized via reflection", e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not parse result: " + hypothesis, e);
        }
    }
}
