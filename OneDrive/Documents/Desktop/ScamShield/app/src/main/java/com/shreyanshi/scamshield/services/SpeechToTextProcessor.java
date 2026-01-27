package com.shreyanshi.scamshield.services;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class SpeechToTextProcessor {

    public static final int SPEECH_REQUEST_CODE = 501;

    // Start Google Speech Recognizer
    public static void startSpeechInput(Activity activity) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault()
        );
        intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak now..."
        );

        try {
            activity.startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(activity,
                    "Speech recognition not supported on this device",
                    Toast.LENGTH_LONG).show();
        }
    }

    // Extract recognized text from result Intent
    public static String getSpeechResult(Intent data) {
        if (data == null) return "";

        ArrayList<String> result =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

        if (result != null && !result.isEmpty()) {
            return result.get(0); // best match
        }
        return "";
    }
}
