package com.shreyanshi.scamshield.activities;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;

public class ScamAlertActivity extends AppCompatActivity {

    private static final String EXTRA_AUDIO = "extra_audio";
    private static final String EXTRA_TRANSCRIPT = "extra_transcript";
    private static final String EXTRA_MATCHES = "extra_matches";

    public static Intent createIntent(Context ctx, String audioPath, String transcript, ArrayList<String> matches) {
        Intent i = new Intent(ctx, ScamAlertActivity.class);
        i.putExtra(EXTRA_AUDIO, audioPath);
        i.putExtra(EXTRA_TRANSCRIPT, transcript);
        i.putStringArrayListExtra(EXTRA_MATCHES, matches);
        return i;
    }

    private MediaPlayer player;
    private String audioPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int layoutId = getResources().getIdentifier("activity_scam_alert", "layout", getPackageName());
        if (layoutId != 0) setContentView(layoutId);

        int titleId = getResources().getIdentifier("alertTitle", "id", getPackageName());
        int transcriptId = getResources().getIdentifier("alertTranscript", "id", getPackageName());
        int matchesId = getResources().getIdentifier("alertMatches", "id", getPackageName());
        int playBtnId = getResources().getIdentifier("alertPlayBtn", "id", getPackageName());
        int dismissBtnId = getResources().getIdentifier("alertDismissBtn", "id", getPackageName());

        TextView title = titleId != 0 ? findViewById(titleId) : null;
        TextView transcriptView = transcriptId != 0 ? findViewById(transcriptId) : null;
        TextView matchesView = matchesId != 0 ? findViewById(matchesId) : null;
        Button playBtn = playBtnId != 0 ? findViewById(playBtnId) : null;
        Button dismissBtn = dismissBtnId != 0 ? findViewById(dismissBtnId) : null;

        audioPath = getIntent().getStringExtra(EXTRA_AUDIO);
        String transcript = getIntent().getStringExtra(EXTRA_TRANSCRIPT);
        ArrayList<String> matches = getIntent().getStringArrayListExtra(EXTRA_MATCHES);

        if (title != null) title.setText("Potential scam detected");
        if (transcriptView != null) transcriptView.setText(transcript != null ? transcript : "(no transcript)");
        if (matchesView != null) matchesView.setText(matches != null ? matches.toString() : "[]");

        if (playBtn != null) playBtn.setOnClickListener(v -> playRecording());
        if (dismissBtn != null) dismissBtn.setOnClickListener(v -> finish());
    }

    private void playRecording() {
        if (audioPath == null) return;
        try {
            if (player == null) player = new MediaPlayer();
            else player.reset();
            player.setDataSource(audioPath);
            player.prepare();
            player.start();
        } catch (IOException e) {
            Log.e("ScamAlert", "Play error", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }
}
