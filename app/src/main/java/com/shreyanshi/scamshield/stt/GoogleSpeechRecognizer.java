package com.shreyanshi.scamshield.stt;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Google On-Device Speech Recognizer
 * 
 * Implements real-time speech-to-text using Android's native
 * SpeechRecognizer with on-device recognition via Google APIs.
 * 
 * Features:
 * - No external model dependencies (native Android)
 * - Automatic offline fallback (EXTRA_PREFER_OFFLINE)
 * - Partial and final result handling
 * - Auto-restart on transient errors
 * - Comprehensive error handling and logging
 */
public class GoogleSpeechRecognizer implements SpeechProcessor, RecognitionListener {
    
    private static final String TAG = "GoogleSpeech";
    
    private final Context context;
    private final SpeechListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;
    
    // Auto-restart configuration
    private static final long AUTO_RESTART_DELAY_MS = 1000;
    private boolean autoRestartEnabled = true;
    
    /**
     * Constructor: Initialize Google Speech Recognizer
     */
    public GoogleSpeechRecognizer(Context context, SpeechListener listener) {
        this.context = context;
        this.listener = listener;
        
        initializeSpeechRecognizer();
    }
    
    /**
     * Initialize the SpeechRecognizer and setup recognition intent
     */
    private void initializeSpeechRecognizer() {
        // Check if Google Speech is available on this device
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "❌ Google Speech Recognition not available on this device");
            if (listener != null) {
                try {
                    listener.onDebugLog("❌ Google Speech not available on this device");
                } catch (Exception e) {
                    Log.d(TAG, "Debug log callback failed: " + e.getMessage());
                }
            }
            return;
        }
        
        try {
            // Create on-device speech recognizer (no cloud dependency)
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(this);
            Log.i(TAG, "✅ Google On-Device Speech Recognizer created");
            if (listener != null) {
                try {
                    listener.onDebugLog("✅ Google On-Device Speech Recognizer created");
                } catch (Exception e) {
                    Log.d(TAG, "Debug log callback failed: " + e.getMessage());
                }
            }
            
            // Setup recognition intent with proper configuration
            setupRecognizerIntent();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to create speech recognizer: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
            speechRecognizer = null;
            if (listener != null) {
                try {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    listener.onDebugLog("❌ Speech Recognizer init failed: " + errorMsg);
                } catch (Exception e2) {
                    Log.d(TAG, "Debug log callback failed: " + e2.getMessage());
                }
            }
        }
    }
    
    /**
     * Configure the recognizer intent with proper extras for Indian English
     */
    private void setupRecognizerIntent() {
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        
        // Language and model configuration
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        
        // Enable partial results for real-time feedback
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        
        // Prefer offline (on-device) recognition for privacy and reliability
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        
        // Additional configuration
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);
        
        Log.d(TAG, "🔧 Recognizer intent configured (en-IN, offline mode)");
    }
    
    /**
     * Start listening for speech input
     * 
     * SAFETY FIX: Adds 1-second delay before first startListening() call
     * This prevents race condition with WindowManager initialization on Moto/Redmi
     * The OS needs time to set up audio resources after overlay window is created
     */
    @Override
    public void start() {
        if (speechRecognizer == null) {
            Log.e(TAG, "❌ Speech recognizer not initialized");
            return;
        }
        
        if (isListening) {
            Log.w(TAG, "⚠️ Already listening, ignoring duplicate start request");
            return;
        }
        
        // SAFETY: Add 1-second delay before first listening to prevent race condition
        // with WindowManager initialization (Redmi/Moto crash fix)
        Log.d(TAG, "⏳ Delaying speech start by 1 second (crash safety)...");
        handler.postDelayed(() -> {
            try {
                if (speechRecognizer == null) {
                    Log.e(TAG, "❌ Speech recognizer became null during startup delay");
                    return;
                }
                
                isListening = true;
                speechRecognizer.startListening(recognizerIntent);
                Log.i(TAG, "📢 Started listening for speech input");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error starting listening: " + e.getMessage(), e);
                isListening = false;
            }
        }, 1000);  // 1 second delay for safety
    }
    
    /**
     * Stop listening for speech input
     */
    @Override
    public void stop() {
        if (speechRecognizer == null) {
            return;
        }
        
        try {
            speechRecognizer.stopListening();
            isListening = false;
            Log.i(TAG, "⏹️ Stopped listening");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error stopping listening: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if recognizer is currently listening
     */
    @Override
    public boolean isRunning() {
        return isListening;
    }
    
    /**
     * Enable or disable auto-restart on errors
     */
    public void setAutoRestartEnabled(boolean enabled) {
        this.autoRestartEnabled = enabled;
        Log.d(TAG, "🔧 Auto-restart " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * RecognitionListener: Called when partial results are available
     * This provides real-time feedback during speech input
     */
    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> results = partialResults.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION);
        
        if (results != null && !results.isEmpty()) {
            String text = results.get(0).trim();
            
            if (!text.isEmpty() && listener != null) {
                Log.d(TAG, "🔄 Partial result: '" + text + "'");
                // Show Toast for real-time feedback
                showToast("📢 Heard: " + text);
                // Callback for speech recognition
                listener.onSpeechRecognized(text);
                // Debug logging for in-app display
                if (listener instanceof SpeechListener) {
                    try {
                        listener.onDebugLog("📢 Partial: " + text);
                    } catch (Exception e) {
                        Log.d(TAG, "Debug log callback failed: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * RecognitionListener: Called when final results are available
     * After this, recognition ends and we auto-restart for continuous monitoring
     */
    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION);
        
        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0).trim();
            
            if (!text.isEmpty()) {
                Log.i(TAG, "✅ Final result: '" + text + "'");
                if (listener != null) {
                    listener.onSpeechRecognized(text);
                    // Debug logging
                    try {
                        listener.onDebugLog("✅ Final: " + text);
                    } catch (Exception e) {
                        Log.d(TAG, "Debug log callback failed: " + e.getMessage());
                    }
                }
            } else {
                Log.d(TAG, "📝 Empty final result");
                if (listener != null) {
                    try {
                        listener.onDebugLog("📝 Empty result");
                    } catch (Exception e) {
                        Log.d(TAG, "Debug log callback failed: " + e.getMessage());
                    }
                }
            }
        } else {
            Log.d(TAG, "📝 No results received");
            if (listener != null) {
                try {
                    listener.onDebugLog("📝 No results");
                } catch (Exception e) {
                    Log.d(TAG, "Debug log callback failed: " + e.getMessage());
                }
            }
        }
        
        // Auto-restart for continuous monitoring
        isListening = false;
        autoRestartListening();
    }
    
    /**
     * RecognitionListener: Called when an error occurs
     * Implements graceful error handling with auto-restart for transient errors
     * 
     * Special handling for ERROR_NO_MATCH and ERROR_RECOGNIZER_BUSY:
     * These errors occur when the recognizer makes the "tu tu tu" beep sound,
     * indicating it's still monitoring for speech. We restart immediately (500ms)
     * instead of waiting 1-3 seconds, so the user's voice after the beep is captured.
     */
    @Override
    public void onError(int errorCode) {
        String errorMessage = getErrorString(errorCode);
        Log.e(TAG, "❌ Speech recognition error: [" + errorCode + "] " + errorMessage);
        
        // Debug logging
        if (listener != null) {
            try {
                listener.onDebugLog("❌ Error [" + errorCode + "]: " + errorMessage);
            } catch (Exception e) {
                Log.d(TAG, "Debug log callback failed: " + e.getMessage());
            }
        }
        
        isListening = false;
        
        // Special handling for NO_MATCH (7) and RECOGNIZER_BUSY (8)
        // These occur during normal operation (beep sound) and need immediate restart
        if (errorCode == SpeechRecognizer.ERROR_NO_MATCH 
                || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            if (autoRestartEnabled) {
                Log.i(TAG, "🔄 Beep heard (NO_MATCH/BUSY), restarting immediately (500ms)...");
                if (listener != null) {
                    try {
                        listener.onDebugLog("🔄 Beep heard, listening resumed in 500ms...");
                    } catch (Exception e) {
                        Log.d(TAG, "Debug log callback failed: " + e.getMessage());
                    }
                }
                // Immediate restart after short delay (don't wait 1 second)
                handler.postDelayed(this::autoRestartListening, 500);
            }
            return; // Don't process further, just restart
        }
        
        // Standard error handling for other transient errors
        if (autoRestartEnabled && isTransientError(errorCode)) {
            Log.i(TAG, "🔄 Transient error detected, auto-restarting...");
            if (listener != null) {
                try {
                    listener.onDebugLog("🔄 Auto-restarting after error...");
                } catch (Exception e) {
                    Log.d(TAG, "Debug log callback failed: " + e.getMessage());
                }
            }
            autoRestartListening();
        } else if (autoRestartEnabled) {
            // Even for non-transient errors, retry after longer delay
            Log.w(TAG, "⚠️ Non-transient error, retrying after delay...");
            if (listener != null) {
                try {
                    listener.onDebugLog("⚠️ Retrying in 3 seconds...");
                } catch (Exception e) {
                    Log.d(TAG, "Debug log callback failed: " + e.getMessage());
                }
            }
            handler.postDelayed(this::autoRestartListening, 3000);
        }
    }
    
    /**
     * Determine if an error is transient (recoverable) or permanent
     * 
     * Note: ERROR_NO_MATCH and ERROR_RECOGNIZER_BUSY are handled specially
     * in onError() with immediate 500ms restart (see onError() implementation)
     */
    private boolean isTransientError(int errorCode) {
        return errorCode == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
            || errorCode == SpeechRecognizer.ERROR_AUDIO
            || errorCode == SpeechRecognizer.ERROR_NO_MATCH
            || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY;
    }
    
    /**
     * Auto-restart listening after a delay
     * Enables continuous monitoring without user interaction
     */
    private void autoRestartListening() {
        handler.postDelayed(() -> {
            if (!isListening && speechRecognizer != null) {
                Log.d(TAG, "🔄 Auto-restarting listening after delay...");
                start();
            }
        }, AUTO_RESTART_DELAY_MS);
    }
    
    /**
     * Convert error code to human-readable message
     */
    private String getErrorString(int error) {
        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No speech input detected";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "Speech recognizer is busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "Server error";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "Speech input timeout";
                break;
            default:
                message = "Unknown error";
        }
        return message;
    }
    
    // ===== Other RecognitionListener methods (required by interface) =====
    
    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.d(TAG, "📢 Ready for speech input");
    }
    
    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "🎤 Speech input detected");
    }
    
    @Override
    public void onRmsChanged(float rmsdB) {
        // Not used in this implementation
    }
    
    @Override
    public void onBufferReceived(byte[] buffer) {
        // Not used in this implementation
    }
    
    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "🔇 End of speech detected");
    }
    
    @Override
    public void onEvent(int eventType, Bundle params) {
        // Not used in this implementation
    }
    
    /**
     * Display Toast message for user feedback
     */
    private void showToast(String message) {
        try {
            handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Log.d(TAG, "Could not show Toast: " + e.getMessage());
        }
    }
    
    /**
     * Clean up and release resources
     * Call this in Activity/Service onDestroy()
     * 
     * SAFETY FIX: Properly destroys recognizer to prevent "Double Initialization" crashes
     */
    public void destroy() {
        try {
            // Cancel any pending handler tasks FIRST
            handler.removeCallbacksAndMessages(null);
            Log.d(TAG, "✅ Cancelled all pending handler tasks");
            
            // Stop listening if active
            if (speechRecognizer != null) {
                try {
                    if (isListening) {
                        speechRecognizer.stopListening();
                        Log.d(TAG, "✅ Stopped listening");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "⚠️ Error stopping listening: " + e.getMessage());
                }
                
                // CRITICAL: Destroy recognizer completely
                try {
                    speechRecognizer.destroy();
                    Log.d(TAG, "✅ Destroyed speech recognizer");
                } catch (Exception e) {
                    Log.w(TAG, "⚠️ Error destroying recognizer: " + e.getMessage());
                }
                
                // Set to null IMMEDIATELY to prevent reuse (prevents double init crash)
                speechRecognizer = null;
            }
            
            isListening = false;
            Log.i(TAG, "✅ GoogleSpeechRecognizer destroyed and resources cleaned");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during cleanup: " + e.getMessage(), e);
        }
    }
}
