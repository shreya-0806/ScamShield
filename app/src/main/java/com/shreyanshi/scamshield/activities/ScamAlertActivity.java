package com.shreyanshi.scamshield.activities;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.shreyanshi.scamshield.R;

public class ScamAlertActivity extends AppCompatActivity {

    public static final String EXTRA_KEYWORDS = "keywords";
    public static final String EXTRA_NUMBER = "number";

    private Ringtone ringtone;

    public static Intent createIntent(Context ctx, String keywords, String number) {
        Intent i = new Intent(ctx, ScamAlertActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(EXTRA_KEYWORDS, keywords);
        i.putExtra(EXTRA_NUMBER, number);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setupWindowFlags();

        setContentView(R.layout.activity_scam_alert);

        String keywords = getIntent().getStringExtra(EXTRA_KEYWORDS);
        String number = getIntent().getStringExtra(EXTRA_NUMBER);

        TextView tvKeywords = findViewById(R.id.tvAlertKeywords);
        TextView tvNumber = findViewById(R.id.tvAlertNumber);
        TextView tvAdvice = findViewById(R.id.tvAlertAdvice);
        Button btnDismiss = findViewById(R.id.btnDismissAlert);
        Button btnEndCall = findViewById(R.id.btnEndCall);

        if (tvKeywords != null) {
            tvKeywords.setText("Scam Alert: " + (keywords != null ? keywords : "Suspicious Activity Detected"));
        }
        if (tvNumber != null && number != null && !number.isEmpty()) {
            tvNumber.setText("From: " + number);
            tvNumber.setVisibility(TextView.VISIBLE);
        } else if (tvNumber != null) {
            tvNumber.setVisibility(TextView.GONE);
        }

        playAlertSound();
        vibrateDevice();

        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(v -> dismissAlert());
        }

        if (btnEndCall != null) {
            btnEndCall.setOnClickListener(v -> {
                endCall();
                dismissAlert();
            });
        }
    }

    private void setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        
        final WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.8f;
        window.setAttributes(params);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private void playAlertSound() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            if (alarmUri != null) {
                ringtone = RingtoneManager.getRingtone(this, alarmUri);
                if (ringtone != null) {
                    ringtone.play();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void vibrateDevice() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500, 200, 500}, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 500, 200, 500, 200, 500}, -1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void endCall() {
        try {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager != null) {
                telecomManager.endCall();
                return;
            }
        } catch (Exception e) {
            try {
                Runtime.getRuntime().exec("input keyevent " + android.view.KeyEvent.KEYCODE_ENDCALL);
            } catch (Exception ignored) {}
        }
    }

    private void dismissAlert() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        dismissAlert();
    }
}
