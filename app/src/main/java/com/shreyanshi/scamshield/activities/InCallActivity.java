package com.shreyanshi.scamshield.activities;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.services.ScamShieldInCallService;

/**
 * InCallActivity - Clean In-Call UI for ScamShield
 *
 * Features:
 * - Center-aligned contact name with circular avatar/initial
 * - Contact name lookup via ContactsContract.PhoneLookup
 * - State-based UI: RINGING (accept/decline), ACTIVE (controls), DISCONNECTED (finish)
 * - Direct hardware speaker control (no boolean flags)
 * - Timer only for ACTIVE state
 * - Back button guard for active calls
 * - Proper audio mode setting
 */
public class InCallActivity extends AppCompatActivity {
    private static final String TAG = "ScamShield-InCall";

    // Intent extras
    public static final String EXTRA_CALL_NUMBER = "call_number";
    public static final String EXTRA_CALL_STATE = "call_state";
    public static final String EXTRA_IS_INCOMING = "is_incoming";

    // Call states
    public static final int STATE_RINGING = 1;
    public static final int STATE_ACTIVE = 2;
    public static final int STATE_DISCONNECTED = 3;

    // UI Components
    private TextView tvCallStatus;
    private TextView tvContactName;
    private TextView tvCallerNumber;
    private TextView tvCallDuration;
    private TextView tvAvatarInitial;
    private View avatarBackground;
    private ImageButton btnAccept;
    private ImageButton btnDecline;
    private ImageButton btnEndCall;
    private ImageButton btnSpeaker;

    // Call state
    private int callState = STATE_RINGING;
    private String callerNumber = "";
    private long callStartTime = 0;
    private Handler durationHandler;
    private Runnable durationRunnable;
    private Ringtone ringtone;
    private Vibrator vibrator;

    // Broadcast receiver for call state updates
    private android.content.BroadcastReceiver callStateReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                String action = intent.getAction();
                Log.d(TAG, "Received broadcast: " + action);

                if (ScamShieldInCallService.ACTION_CALL_DISCONNECTED.equals(action)) {
                    Log.i(TAG, "📞 Call disconnected - finishing activity");
                    callState = STATE_DISCONNECTED;
                    updateUI(STATE_DISCONNECTED);
                } else if (ScamShieldInCallService.ACTION_CALL_ACTIVE.equals(action)) {
                    Log.i(TAG, "📞 Call became active - updating UI");
                    callState = STATE_ACTIVE;
                    updateUI(STATE_ACTIVE);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_in_call);

        // Get intent extras
        callerNumber = getIntent().getStringExtra(EXTRA_CALL_NUMBER);
        callState = getIntent().getIntExtra(EXTRA_CALL_STATE, STATE_RINGING);

        // Initialize UI and listeners
        initializeUI();
        setupClickListeners();

        // Lookup contact name
        String contactName = lookupContactName(callerNumber);
        if (contactName != null && tvContactName != null) {
            tvContactName.setText(contactName);
            if (tvAvatarInitial != null && contactName.length() > 0) {
                tvAvatarInitial.setText(String.valueOf(contactName.charAt(0)).toUpperCase());
            }
        }

        // Set volume control for voice calls
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        // Update UI for initial state
        updateUI(callState);
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        tvCallStatus = findViewById(R.id.tvCallStatus);
        tvContactName = findViewById(R.id.tvContactName);
        tvCallerNumber = findViewById(R.id.tvCallerNumber);
        tvCallDuration = findViewById(R.id.tvCallDuration);
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial);
        avatarBackground = findViewById(R.id.avatarBackground);
        btnAccept = findViewById(R.id.btnAccept);
        btnDecline = findViewById(R.id.btnDecline);
        btnEndCall = findViewById(R.id.btnEndCall);
        btnSpeaker = findViewById(R.id.btnSpeaker);

        durationHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Setup click listeners for buttons
     */
    private void setupClickListeners() {
        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> {
                Call call = ScamShieldInCallService.currentCall;
                if (call != null && call.getState() == Call.STATE_RINGING) {
                    try {
                        call.answer(VideoProfile.STATE_AUDIO_ONLY);
                        Log.i(TAG, "✅ Call answered");
                    } catch (Exception e) {
                        Log.e(TAG, "Error answering call: " + e.getMessage());
                    }
                }
            });
        }

        if (btnDecline != null) {
            btnDecline.setOnClickListener(v -> {
                Call call = ScamShieldInCallService.currentCall;
                if (call != null) {
                    try {
                        call.disconnect();
                        Log.i(TAG, "✅ Call declined");
                    } catch (Exception e) {
                        Log.e(TAG, "Error declining call: " + e.getMessage());
                    }
                }
            });
        }

        if (btnEndCall != null) {
            btnEndCall.setOnClickListener(v -> {
                Call call = ScamShieldInCallService.currentCall;
                if (call != null) {
                    try {
                        call.disconnect();
                        Log.i(TAG, "✅ Call ended");
                    } catch (Exception e) {
                        Log.e(TAG, "Error ending call: " + e.getMessage());
                    }
                }
            });
        }

        if (btnSpeaker != null) {
            btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        }
    }

    /**
     * Update UI based on call state
     */
    private void updateUI(int state) {
        callState = state;

        // Hide all by default
        setAllViewsVisibility(View.GONE);

        switch (state) {
            case STATE_RINGING:
                if (tvCallStatus != null) {
                    tvCallStatus.setText("Incoming Call");
                    tvCallStatus.setTextColor(0xFF4CAF50);
                    tvCallStatus.setVisibility(View.VISIBLE);
                }
                if (btnAccept != null) btnAccept.setVisibility(View.VISIBLE);
                if (btnDecline != null) btnDecline.setVisibility(View.VISIBLE);
                stopTimer();
                break;

            case STATE_ACTIVE:
                if (tvCallStatus != null) {
                    tvCallStatus.setText("Call in Progress");
                    tvCallStatus.setTextColor(0xFF2196F3);
                    tvCallStatus.setVisibility(View.VISIBLE);
                }
                if (btnEndCall != null) btnEndCall.setVisibility(View.VISIBLE);
                if (btnSpeaker != null) btnSpeaker.setVisibility(View.VISIBLE);
                if (tvCallDuration != null) tvCallDuration.setVisibility(View.VISIBLE);
                startTimer();
                break;

            case STATE_DISCONNECTED:
                stopTimer();
                finishAndRemoveTask();
                break;
        }
    }

    /**
     * Set all views to specified visibility
     */
    private void setAllViewsVisibility(int visibility) {
        if (tvCallStatus != null) tvCallStatus.setVisibility(visibility);
        if (tvContactName != null) tvContactName.setVisibility(visibility);
        if (tvCallerNumber != null) tvCallerNumber.setVisibility(visibility);
        if (tvCallDuration != null) tvCallDuration.setVisibility(visibility);
        if (tvAvatarInitial != null) tvAvatarInitial.setVisibility(visibility);
        if (avatarBackground != null) avatarBackground.setVisibility(visibility);
        if (btnAccept != null) btnAccept.setVisibility(visibility);
        if (btnDecline != null) btnDecline.setVisibility(visibility);
        if (btnEndCall != null) btnEndCall.setVisibility(visibility);
        if (btnSpeaker != null) btnSpeaker.setVisibility(visibility);
    }

    /**
     * Toggle speaker - Direct hardware read
     */
    private void toggleSpeaker() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager == null) return;

        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        boolean currentlyOn = audioManager.isSpeakerphoneOn();
        audioManager.setSpeakerphoneOn(!currentlyOn);

        Log.i(TAG, "🔊 Speaker toggled to: " + (!currentlyOn));
    }

    /**
     * Start call duration timer
     */
    private void startTimer() {
        stopTimer();
        callStartTime = System.currentTimeMillis();

        if (tvCallDuration != null) {
            tvCallDuration.setText("00:00");
        }

        durationRunnable = new Runnable() {
            @Override
            public void run() {
                if (callState != STATE_ACTIVE) return;

                long elapsed = System.currentTimeMillis() - callStartTime;
                long minutes = (elapsed / 1000) / 60;
                long seconds = (elapsed / 1000) % 60;
                String time = String.format("%02d:%02d", minutes, seconds);

                if (tvCallDuration != null) {
                    tvCallDuration.setText(time);
                }

                durationHandler.postDelayed(this, 1000);
            }
        };

        durationHandler.post(durationRunnable);
        Log.i(TAG, "⏱️ Timer started");
    }

    /**
     * Stop call duration timer
     */
    private void stopTimer() {
        if (durationRunnable != null) {
            durationHandler.removeCallbacks(durationRunnable);
            durationRunnable = null;
        }
        if (tvCallDuration != null) {
            tvCallDuration.setText("00:00");
        }
        Log.i(TAG, "⏱️ Timer stopped");
    }

    /**
     * Query ContactsContract to find contact name from phone number
     */
    private String lookupContactName(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) return null;

        String normalizedNumber = phoneNumber.replaceAll("[^0-9+]", "");

        try {
            ContentResolver resolver = getContentResolver();
            Uri uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(normalizedNumber)
                    .build();

            Cursor cursor = resolver.query(uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        String name = cursor.getString(0);
                        if (name != null && !name.isEmpty()) {
                            Log.i(TAG, "Contact found: " + name);
                            return name;
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error looking up contact: " + e.getMessage());
        }

        return null;
    }

    /**
     * Guard back button for active calls
     */
    @Override
    public void onBackPressed() {
        Call activeCall = ScamShieldInCallService.currentCall;
        if (activeCall != null) {
            int state = activeCall.getState();
            if (state == Call.STATE_ACTIVE || state == Call.STATE_RINGING) {
                Log.i(TAG, "Back press ignored - call is active");
                return;
            }
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ScamShieldInCallService.ACTION_CALL_DISCONNECTED);
        filter.addAction(ScamShieldInCallService.ACTION_CALL_ACTIVE);
        LocalBroadcastManager.getInstance(this).registerReceiver(callStateReceiver, filter);

        // Update caller info
        updateCallerInfo();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(callStateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
    }

    /**
     * Update caller information display
     */
    private void updateCallerInfo() {
        Call activeCall = ScamShieldInCallService.currentCall;
        if (activeCall != null && activeCall.getDetails() != null &&
            activeCall.getDetails().getHandle() != null) {

            String number = activeCall.getDetails().getHandle().getSchemeSpecificPart();
            if (number != null && !number.isEmpty()) {
                callerNumber = number;
                if (tvCallerNumber != null) {
                    tvCallerNumber.setText(number);
                }

                // Update contact name if not set
                if (tvContactName != null && tvContactName.getText().toString().equals("")) {
                    String contactName = lookupContactName(number);
                    if (contactName != null) {
                        tvContactName.setText(contactName);
                        if (tvAvatarInitial != null && contactName.length() > 0) {
                            tvAvatarInitial.setText(String.valueOf(contactName.charAt(0)).toUpperCase());
                        }
                    } else {
                        tvContactName.setText(number);
                        if (tvAvatarInitial != null && number.length() > 0) {
                            tvAvatarInitial.setText(String.valueOf(number.charAt(0)).toUpperCase());
                        }
                    }
                }
            }
        } else if (callerNumber != null && !callerNumber.isEmpty() && tvCallerNumber != null) {
            tvCallerNumber.setText(callerNumber);
        }
    }

    /**
     * Static method to create intent for this activity
     */
    public static Intent createIntent(Context context, String number, int state, boolean isIncoming) {
        Intent intent = new Intent(context, InCallActivity.class);
        intent.putExtra(EXTRA_CALL_NUMBER, number);
        intent.putExtra(EXTRA_CALL_STATE, state);
        intent.putExtra(EXTRA_IS_INCOMING, isIncoming);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }
}