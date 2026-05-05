package com.shreyanshi.scamshield.activities;

import android.telecom.Call;
import android.telecom.VideoProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.services.ScamShieldInCallService;

public class InCallActivity extends AppCompatActivity {
    private static final String TAG = "InCallActivity";
    private Call currentCall;
    private int callState = Call.STATE_RINGING;
    private TextView txtCaller, txtCallerOngoing, txtTimer;
    private View incomingLayout, ongoingLayout;
    private Button btnAccept, btnDecline, btnEnd, btnSpeaker, btnMute, btnHold, btnRecord;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;
    private int seconds = 0;
    private BroadcastReceiver callStateReceiver;
    private AudioManager audioManager;
    private LocalBroadcastManager localBroadcastManager;
    private boolean isMuted = false;
    private boolean isOnHold = false;
    private boolean isRecording = false;

    public static final String ACTION_CALL_STATE_CHANGED = ScamShieldInCallService.ACTION_CALL_STATE_CHANGED;
    public static final String EXTRA_CALL_STATE = ScamShieldInCallService.EXTRA_CALL_STATE;
    public static final String EXTRA_PHONE_NUMBER = ScamShieldInCallService.EXTRA_PHONE_NUMBER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate START");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        setContentView(R.layout.activity_in_call);
        Log.d(TAG, "Layout set");

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        localBroadcastManager = LocalBroadcastManager.getInstance(this);

        initViews();
        setupTimer();

        // Set initial visibility
        if (incomingLayout != null) incomingLayout.setVisibility(View.VISIBLE);
        if (ongoingLayout != null) ongoingLayout.setVisibility(View.GONE);
        if (btnAccept != null) btnAccept.setVisibility(View.VISIBLE);
        if (btnDecline != null) btnDecline.setVisibility(View.VISIBLE);

        handleIntent(getIntent());

        callStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_CALL_STATE_CHANGED.equals(intent.getAction())) {
                    int newState = intent.getIntExtra(EXTRA_CALL_STATE, Call.STATE_NEW);
                    String phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER);
                    Log.d(TAG, "Broadcast: state=" + newState);
                    updateCallState(newState, phoneNumber);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_CALL_STATE_CHANGED);
        localBroadcastManager.registerReceiver(callStateReceiver, filter);
        Log.d(TAG, "onCreate END");
    }

    private void initViews() {
        incomingLayout = findViewById(R.id.incoming_call_layout);
        ongoingLayout = findViewById(R.id.ongoing_call_layout);
        txtCaller = findViewById(R.id.txtCaller);
        txtCallerOngoing = findViewById(R.id.txtCallerOngoing);
        txtTimer = findViewById(R.id.txtTimer);
        btnAccept = findViewById(R.id.btnAccept);
        btnDecline = findViewById(R.id.btnDecline);
        btnEnd = findViewById(R.id.btnEndCallLarge);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        btnMute = findViewById(R.id.btnMute);
        btnHold = findViewById(R.id.btnHold);
        btnRecord = findViewById(R.id.btnRecord);

        currentCall = ScamShieldInCallService.currentCall;

        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> {
                if (currentCall != null) {
                    currentCall.answer(VideoProfile.STATE_AUDIO_ONLY);
                    Log.i(TAG, "Answered call");
                }
            });
        }

        if (btnDecline != null) {
            btnDecline.setOnClickListener(v -> {
                if (currentCall != null) {
                    if (callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING) {
                        currentCall.disconnect();
                        Log.i(TAG, "Outgoing call disconnected");
                    } else {
                        currentCall.reject(0);
                        Log.i(TAG, "Incoming call rejected");
                    }
                }
                finish();
            });
        }

        if (btnEnd != null) {
            btnEnd.setOnClickListener(v -> {
                if (currentCall != null) {
                    currentCall.disconnect();
                    Log.i(TAG, "Ended call");
                }
                finish();
            });
        }

        if (btnSpeaker != null) {
            btnSpeaker.setOnClickListener(v -> {
                if (audioManager != null) {
                    boolean isOn = audioManager.isSpeakerphoneOn();
                    if (!isOn) {
                        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        audioManager.setSpeakerphoneOn(true);
                        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0);
                        btnSpeaker.setText("Speaker (On)");
                    } else {
                        audioManager.setSpeakerphoneOn(false);
                        btnSpeaker.setText("Speaker");
                    }
                }
            });
        }

        if (btnMute != null) {
            btnMute.setOnClickListener(v -> {
                if (audioManager != null) {
                    isMuted = !isMuted;
                    audioManager.setMicrophoneMute(isMuted);
                    btnMute.setText(isMuted ? "Mute (On)" : "Mute");
                }
            });
        }

        if (btnHold != null) {
            btnHold.setOnClickListener(v -> {
                if (currentCall != null) {
                    if (!isOnHold) {
                        currentCall.hold();
                        isOnHold = true;
                        btnHold.setText("Hold (On)");
                    } else {
                        currentCall.unhold();
                        isOnHold = false;
                        btnHold.setText("Hold");
                    }
                }
            });
        }

        if (btnRecord != null) {
            btnRecord.setOnClickListener(v -> {
                isRecording = !isRecording;
                btnRecord.setText(isRecording ? "Stop Rec" : "Record");
                Intent intent = new Intent(isRecording ?
                    "com.shreyanshi.scamshield.ACTION_START_RECORDING" :
                    "com.shreyanshi.scamshield.ACTION_STOP_RECORDING");
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            });
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            callState = intent.getIntExtra(EXTRA_CALL_STATE, Call.STATE_RINGING);
            String phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER);
            Log.d(TAG, "handleIntent: state=" + callState + " phone=" + phoneNumber);
            updateCallState(callState, phoneNumber);
        } else {
            Log.d(TAG, "handleIntent: null, defaulting to RINGING");
            updateCallState(Call.STATE_RINGING, "Unknown");
        }
    }

    private void updateCallState(int state, String phoneNumber) {
        callState = state;
        Log.d(TAG, "updateCallState: " + state);

        // Always update text views
        if (txtCaller != null) txtCaller.setText(phoneNumber != null ? phoneNumber : "Unknown Caller");
        if (txtCallerOngoing != null) txtCallerOngoing.setText(phoneNumber != null ? phoneNumber : "Unknown Caller");

        switch (state) {
            case Call.STATE_RINGING:
                Log.d(TAG, "RINGING - showing Accept + Decline");
                if (incomingLayout != null) incomingLayout.setVisibility(View.VISIBLE);
                if (ongoingLayout != null) ongoingLayout.setVisibility(View.GONE);
                if (btnAccept != null) btnAccept.setVisibility(View.VISIBLE);
                if (btnDecline != null) btnDecline.setVisibility(View.VISIBLE);
                if (audioManager != null) audioManager.setMode(AudioManager.MODE_RINGTONE);
                break;

            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING:
                Log.d(TAG, "DIALING/CONNECTING - showing ONLY Decline");
                if (incomingLayout != null) incomingLayout.setVisibility(View.VISIBLE);
                if (ongoingLayout != null) ongoingLayout.setVisibility(View.GONE);
                if (btnAccept != null) btnAccept.setVisibility(View.GONE);  // HIDE Accept
                if (btnDecline != null) btnDecline.setVisibility(View.VISIBLE);  // SHOW Only Decline
                break;

            case Call.STATE_ACTIVE:
                Log.d(TAG, "ACTIVE - showing full UI");
                if (incomingLayout != null) incomingLayout.setVisibility(View.GONE);
                if (ongoingLayout != null) ongoingLayout.setVisibility(View.VISIBLE);
                if (audioManager != null) audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                startTimer();
                break;

            case Call.STATE_HOLDING:
                Log.d(TAG, "HOLDING - showing ongoing layout");
                if (incomingLayout != null) incomingLayout.setVisibility(View.GONE);
                if (ongoingLayout != null) ongoingLayout.setVisibility(View.VISIBLE);
                break;

            case Call.STATE_DISCONNECTED:
            case Call.STATE_DISCONNECTING:
                Log.d(TAG, "DISCONNECTED - finishing");
                stopTimer();
                finish();
                break;

            default:
                Log.d(TAG, "Unknown state " + state + " - showing incoming");
                if (incomingLayout != null) incomingLayout.setVisibility(View.VISIBLE);
                if (ongoingLayout != null) ongoingLayout.setVisibility(View.GONE);
                if (btnAccept != null) btnAccept.setVisibility(View.VISIBLE);
                if (btnDecline != null) btnDecline.setVisibility(View.VISIBLE);
        }
    }

    private void setupTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                seconds++;
                int minutes = seconds / 60;
                int secs = seconds % 60;
                if (txtTimer != null) txtTimer.setText(String.format("%02d:%02d", minutes, secs));
                timerHandler.postDelayed(this, 1000);
            }
        };
    }

    private void startTimer() {
        seconds = 0;
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent called");
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        if (localBroadcastManager != null && callStateReceiver != null) {
            localBroadcastManager.unregisterReceiver(callStateReceiver);
        }
        currentCall = null;
    }
}
