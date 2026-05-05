package com.shreyanshi.scamshield.activities;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.services.ScamShieldInCallService;

public class InCallActivity extends AppCompatActivity {

    public static final String EXTRA_CALL_NUMBER = "call_number";
    public static final String EXTRA_CALL_STATE = "call_state";

    public static final int STATE_RINGING = 1;
    public static final int STATE_ACTIVE = 2;
    public static final int STATE_DISCONNECTED = 3;

    private TextView tvStatus, tvNumber, tvTimer;
    private ImageButton btnAccept, btnDecline, btnEnd;

    private Handler handler = new Handler();
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_in_call);

        tvStatus = findViewById(R.id.tvCallStatus);
        tvNumber = findViewById(R.id.tvCallerNumber);
        tvTimer = findViewById(R.id.tvCallDuration);

        btnAccept = findViewById(R.id.btnAccept);
        btnDecline = findViewById(R.id.btnDecline);
        btnEnd = findViewById(R.id.btnEndCallLarge);

        String number = getIntent().getStringExtra(EXTRA_CALL_NUMBER);
        int state = getIntent().getIntExtra(EXTRA_CALL_STATE, STATE_RINGING);

        tvNumber.setText(number);

        updateUI(state);

        btnAccept.setOnClickListener(v -> {
            Call call = ScamShieldInCallService.currentCall;
            if (call != null) call.answer(VideoProfile.STATE_AUDIO_ONLY);
        });

        btnDecline.setOnClickListener(v -> {
            Call call = ScamShieldInCallService.currentCall;
            if (call != null) call.disconnect();
        });

        btnEnd.setOnClickListener(v -> {
            Call call = ScamShieldInCallService.currentCall;
            if (call != null) call.disconnect();
        });
    }

    private void updateUI(int state) {

        if (state == STATE_RINGING) {
            tvStatus.setText("Incoming Call");
            btnAccept.setVisibility(View.VISIBLE);
            btnDecline.setVisibility(View.VISIBLE);
            btnEnd.setVisibility(View.GONE);
        }

        else if (state == STATE_ACTIVE) {
            tvStatus.setText("Call Active");
            btnAccept.setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
            btnEnd.setVisibility(View.VISIBLE);

            startTimer();
        }

        else if (state == STATE_DISCONNECTED) {
            finish();
        }
    }

    private void startTimer() {
        startTime = System.currentTimeMillis();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                int sec = (int) (elapsed / 1000);
                tvTimer.setText("00:" + String.format("%02d", sec));
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }
}