package com.shreyanshi.scamshield.stt;

/**
 * Callback interface for speech recognition events
 * Allows real-time feedback from GoogleSpeechRecognizer to listeners
 */
public interface SpeechListener {
    /**
     * Called when speech is recognized (partial or final result)
     * @param text The recognized speech text
     */
    void onSpeechRecognized(String text);
    
    /**
     * Called for debug logging of speech events
     * Enables in-app debug log display showing real-time recognition progress
     * @param debugMessage Debug information (e.g., "Partial: hello", "Error: timeout")
     */
    void onDebugLog(String debugMessage);
}
