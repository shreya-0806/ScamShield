package com.shreyanshi.scamshield.stt;

public interface SpeechProcessor {
    void start();
    void stop();
    boolean isRunning();

    interface Listener {
        void onSpeechRecognized(String text);
    }
}
