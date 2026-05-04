package com.shreyanshi.scamshield.activities;

import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.Manifest;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.activities.MainActivity;
import com.shreyanshi.scamshield.services.ScamMonitorService;
import com.shreyanshi.scamshield.services.ScamShieldInCallService;

/**
 * InCallActivity - Professional In-Call UI for ScamShield
 * 
 * Features:
 * - Center-aligned contact name with circular avatar/initial
 * - Mute and Speaker toggle buttons
 * - Contact name resolution via ContactsContract
 * - Automatic finish() on call disconnect
 * - Synchronized ScamMonitorService start on STATE_ACTIVE
 * - Proper audio routing for speech recognition
 * 
 * Shows over lock screen using setShowWhenLocked() and setTurnScreenOn().
 */
public class InCallActivity extends AppCompatActivity {
    private static final String TAG = "ScamShield-InCall";
    
    // Intent extras
    public static final String EXTRA_CALL_NUMBER = "call_number";
    public static final String EXTRA_CALL_STATE = "call_state";
    public static final String EXTRA_IS_INCOMING = "is_incoming";
    public static final String EXTRA_CONTACT_NAME = "contact_name";
    
    // Call states
    public static final int STATE_RINGING = 1;
    public static final int STATE_ACTIVE = 2;
    public static final int STATE_DISCONNECTED = 3;
    public static final int STATE_DIALING = 4;
    
    // UI Components
    private TextView tvCallStatus;
    private TextView tvContactName;
    private TextView tvCallerNumber;
    private TextView tvCallDuration;
    private TextView tvScamStatus;
    private TextView tvAvatarInitial;
    private View avatarBackground;
    private ImageButton btnAccept;
    private ImageButton btnDecline;
    private View btnEndCallLarge;  // Large red button for active calls (safe fallback for mismatched inflation)
    private ImageButton btnMute;
    private ImageButton btnSpeaker;
    private ImageButton btnHold;
    private ImageButton btnRecord;
    private TextView tvMuteLabel;
    private TextView tvSpeakerLabel;
    private TextView tvHoldLabel;
    private TextView tvRecordLabel;
    
    // Call state
    private int callState = STATE_RINGING;
    private boolean isMuted = false;
    private boolean isSpeakerOn = false;
    private boolean isOnHold = false;
    private long callStartTime = 0;
    private Ringtone ringtone;
    private Vibrator vibrator;
    private Handler durationHandler;
    private Runnable durationRunnable;
    private String callerNumber = "";
    private Call currentCall = null;
    private View ongoingCallLayout;
    private View callButtonRow;
    private View incomingCallLayout;
    
    // Service binding
    private ServiceConnection serviceConnection;
    private boolean serviceBound = false;
    private ScamMonitorService scamMonitorService;
    
    // MainActivity reference for protected calls
    private MainActivity mainActivity;
    
    // Broadcast receiver for call events
    // CRITICAL: Handles DISCONNECTED from ScamShieldInCallService
    private android.content.BroadcastReceiver callEventReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                String action = intent.getAction();
                Log.i(TAG, "Received broadcast: " + action);
                
                if ("com.shreyanshi.scamshield.ACTION_DISCONNECT_CALL".equals(action) ||
                    "com.shreyanshi.scamshield.ACTION_FINISH_INCALL".equals(action) ||
                    ScamShieldInCallService.ACTION_CALL_DISCONNECTED.equals(action)) {
                    // Call disconnected - finish activity immediately (FIRST CLICK)
                    Log.i(TAG, "📞 CALL DISCONNECTED - closing activity NOW");
                    callState = STATE_DISCONNECTED;
                    finishAndRemoveTask();
                }
                else if ("com.shreyanshi.scamshield.ACTION_START_RECORDING".equals(action)) {
                    // Auto-start recording
                    Log.i(TAG, "🎙️ Auto-start recording received");
                    toggleRecord(); // Start recording
                }
                else if ("com.shreyanshi.scamshield.ACTION_STOP_RECORDING".equals(action)) {
                    // Auto-stop recording
                    if (isRecording) {
                        Log.i(TAG, "🎙️ Auto-stop recording received");
                        stopRecording(); // Stop recording
                    }
                }
                else if (ScamShieldInCallService.ACTION_FINISH_UI.equals(action)) {
                    // STEP 1: Handle ACTION_FINISH_UI to prevent crashes
                    Log.i(TAG, "📞 ACTION_FINISH_UI - closing activity");
                    finishAndRemoveTask();
                }
                else if (ScamShieldInCallService.ACTION_CALL_ACTIVE.equals(action)) {
                    // Call became ACTIVE - update UI immediately
                    Log.i(TAG, "📞 CALL ACTIVE - updating UI");
                    updateUI(STATE_ACTIVE);
                }
                else if (ScamShieldInCallService.ACTION_CALL_STATE_UPDATE.equals(action)) {
                    // Real-time state update from InCallService - sync UI with actual call state
                    int newState = intent.getIntExtra(ScamShieldInCallService.EXTRA_CALL_STATE, callState);
                    Log.i(TAG, "📞 STATE UPDATE - new state: " + newState + " (current: " + callState + ")");
                    updateUI(newState);
                }
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_in_call);
        
        // CRITICAL: Initialize ServiceConnection BEFORE calling bindService
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "Service connected");
                ScamMonitorService.LocalBinder binder = (ScamMonitorService.LocalBinder) service;
                scamMonitorService = binder.getService();
                serviceBound = true;
            }
            
            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "Service disconnected");
                scamMonitorService = null;
                serviceBound = false;
            }
        };
        
        // Get intent extras
        callerNumber = getIntent().getStringExtra(EXTRA_CALL_NUMBER);
        callState = getIntent().getIntExtra(EXTRA_CALL_STATE, STATE_RINGING);
        
        // Initialize handler
        durationHandler = new Handler(Looper.getMainLooper());
        
        // Initialize UI
        initializeUI();
        
        // Lookup contact name from phone number
        String contactName = lookupContactName(callerNumber);
        if (contactName != null) {
            tvContactName.setText(contactName);
            // Update avatar initial
            if (tvAvatarInitial != null && contactName.length() > 0) {
                tvAvatarInitial.setText(String.valueOf(contactName.charAt(0)).toUpperCase());
            }
        }
        
        // Bind to ScamMonitorService
        bindToScamMonitorService();
        
        // Update UI
        updateUI(callState);
    }
    
    /**
     * Query ContactsContract to find contact name from phone number
     */
    private String lookupContactName(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return null;
        }
        
        // Normalize phone number - remove special characters
        String normalizedNumber = phoneNumber.replaceAll("[^0-9+]", "");
        
        try {
            ContentResolver resolver = getContentResolver();
            Uri uri = android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(normalizedNumber)
                    .build();
            
            Cursor cursor = resolver.query(uri, new String[]{
                    android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME,
                    android.provider.ContactsContract.PhoneLookup.PHOTO_URI
            }, null, null, null);
            
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
    
    @Override
    public void onBackPressed() {
        // Instead of finishing, move task to background
        // This keeps the call alive and allows re-opening from notification
        moveTaskToBack(true);
        Log.i(TAG, "Call UI moved to background");
    }
    
    private void initializeUI() {
        tvCallStatus = findViewById(R.id.tvCallStatus);
        tvContactName = findViewById(R.id.tvContactName);
        tvCallerNumber = findViewById(R.id.tvCallerNumber);
        tvCallDuration = findViewById(R.id.tvCallDuration);
        tvScamStatus = findViewById(R.id.tvScamStatus);
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial);
        avatarBackground = findViewById(R.id.avatarBackground);
        btnAccept = findViewById(R.id.btnAccept);
        if (btnAccept == null) {
            Log.e("UI_ERROR", "btnAccept is null");
        }
        btnDecline = findViewById(R.id.btnDecline);
        if (btnDecline == null) {
            Log.e("UI_ERROR", "btnDecline is null");
        }
        View endCallLargeView = findViewById(R.id.btnEndCallLarge);  // Large red button for active calls
        btnEndCallLarge = endCallLargeView;
        if (btnEndCallLarge == null) {
            Log.e("UI_ERROR", "btnEndCallLarge is null");
        } else {
            Log.d("UI_DEBUG", "btnEndCallLarge view class=" + btnEndCallLarge.getClass().getName());
        }
        btnMute = findViewById(R.id.btnMute);
        if (btnMute == null) {
            Log.e("UI_ERROR", "btnMute is null");
        }
        btnSpeaker = findViewById(R.id.btnSpeaker);
        if (btnSpeaker == null) {
            Log.e("UI_ERROR", "btnSpeaker is null");
        }
        btnHold = findViewById(R.id.btnHold);
        if (btnHold == null) {
            Log.e("UI_ERROR", "btnHold is null");
        }
        btnRecord = findViewById(R.id.btnRecord);
        if (btnRecord == null) {
            Log.e("UI_ERROR", "btnRecord is null");
        }
        Log.d("UI_DEBUG", "All buttons initialized successfully");
        tvMuteLabel = findViewById(R.id.tvMuteLabel);
        tvSpeakerLabel = findViewById(R.id.tvSpeakerLabel);
        tvHoldLabel = findViewById(R.id.tvHoldLabel);
        tvRecordLabel = findViewById(R.id.tvRecordLabel);
        ongoingCallLayout = findViewById(R.id.ongoing_call_layout);
        callButtonRow = findViewById(R.id.callButtonRow);
        incomingCallLayout = findViewById(R.id.incoming_call_layout);  // Add for RINGING state control
        
        // Accept button click
        btnAccept.setOnClickListener(v -> {
            Log.d("CALL_DEBUG", "Button clicked: ACCEPT");
            android.telecom.Call call = ScamShieldInCallService.currentCall;
            if (call == null) {
                Log.e("CALL_DEBUG", "Accept clicked but currentCall is null");
                return;
            }
            // Check if call is actually ringing before answering
            if (call.getState() != android.telecom.Call.STATE_RINGING) {
                Log.w("CALL_DEBUG", "Accept clicked but call state is not RINGING (state=" + call.getState() + ")");
                return;
            }
            try {
                call.answer(VideoProfile.STATE_AUDIO_ONLY);
                Log.i("CALL_DEBUG", "✅ Call answered successfully");
            } catch (Exception e) {
                Log.e(TAG, "Accept error: " + e.getMessage());
            }
        });
        
        // Decline button click
        btnDecline.setOnClickListener(v -> {
            Log.d("CALL_DEBUG", "Button clicked: DECLINE");
            android.telecom.Call call = ScamShieldInCallService.currentCall;
            if (call == null) {
                Log.e("CALL_DEBUG", "Decline clicked but currentCall is null");
                return;
            }
            // Check if call is ringing or active before disconnecting
            int state = call.getState();
            if (state != android.telecom.Call.STATE_RINGING && state != android.telecom.Call.STATE_ACTIVE) {
                Log.w("CALL_DEBUG", "Decline clicked but call state is not RINGING/ACTIVE (state=" + state + ")");
                return;
            }
            try {
                call.disconnect();
                Log.i("CALL_DEBUG", "✅ Call declined/disconnected successfully");
            } catch (Exception e) {
                Log.e(TAG, "Decline error: " + e.getMessage());
            }
        });
        
        // Mute button click
        btnMute.setOnClickListener(v -> {
            Log.d("CALL_DEBUG", "Button clicked: MUTE");
            toggleMute();
        });
        
        // Speaker button click
        btnSpeaker.setOnClickListener(v -> {
            Log.d("CALL_DEBUG", "Button clicked: SPEAKER");
            toggleSpeaker();
        });
        
        // Hold button click
        btnHold.setOnClickListener(v -> {
            Log.d("CALL_DEBUG", "Button clicked: HOLD");
            toggleHold();
        });
        
        // Record button click
        btnRecord.setOnClickListener(v -> {
            toggleRecord();
        });
        
        // Large end call button click (for active calls)
        btnEndCallLarge.setOnClickListener(v -> {
            Log.d("CALL_DEBUG", "Button clicked: END");
            android.telecom.Call call = ScamShieldInCallService.currentCall;
            if (call == null) {
                Log.e("CALL_DEBUG", "End call clicked but currentCall is null");
                return;
            }
            // Check if call is active before disconnecting
            if (call.getState() != android.telecom.Call.STATE_ACTIVE) {
                Log.w("CALL_DEBUG", "End call clicked but call state is not ACTIVE (state=" + call.getState() + ")");
                return;
            }
            try {
                call.disconnect();
                Log.i("CALL_DEBUG", "✅ Call ended successfully");
            } catch (Exception e) {
                Log.e(TAG, "End call error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Bind to ScamMonitorService for communication
     */
    private void bindToScamMonitorService() {
        Intent intent = new Intent(this, ScamMonitorService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }
    
    /**
     * Update UI based on call state.
     */
    private void updateUI(int state) {
        int previousState = callState;
        callState = state;
        Log.d("CALL_UI", "State: " + state);

        // Hide everything by default
        if (incomingCallLayout != null) incomingCallLayout.setVisibility(View.GONE);
        if (ongoingCallLayout != null) ongoingCallLayout.setVisibility(View.GONE);
        if (btnAccept != null) btnAccept.setVisibility(View.GONE);
        if (btnDecline != null) btnDecline.setVisibility(View.GONE);
        if (btnEndCallLarge != null) btnEndCallLarge.setVisibility(View.GONE);
        if (btnMute != null) btnMute.setVisibility(View.GONE);
        if (btnSpeaker != null) btnSpeaker.setVisibility(View.GONE);
        if (btnHold != null) btnHold.setVisibility(View.GONE);
        if (btnRecord != null) btnRecord.setVisibility(View.GONE);
        if (callButtonRow != null) callButtonRow.setVisibility(View.GONE);
        if (tvCallDuration != null) tvCallDuration.setVisibility(View.GONE);

        switch (state) {
            case STATE_RINGING:
                tvCallStatus.setText("Incoming Call");
                tvCallStatus.setTextColor(0xFF4CAF50);

                if (incomingCallLayout != null) incomingCallLayout.setVisibility(View.VISIBLE);
                if (btnAccept != null) btnAccept.setVisibility(View.VISIBLE);
                if (btnDecline != null) btnDecline.setVisibility(View.VISIBLE);

                startIncomingAlert();
                Log.d("CALL_UI", "State: RINGING");
                break;

            case STATE_DIALING:
                tvCallStatus.setText("Dialing");
                tvCallStatus.setTextColor(0xFF2196F3);

                if (ongoingCallLayout != null) ongoingCallLayout.setVisibility(View.VISIBLE);
                if (callButtonRow != null) callButtonRow.setVisibility(View.VISIBLE);
                if (btnMute != null) btnMute.setVisibility(View.VISIBLE);
                if (btnSpeaker != null) btnSpeaker.setVisibility(View.VISIBLE);
                if (btnHold != null) btnHold.setVisibility(View.VISIBLE);
                if (btnRecord != null) btnRecord.setVisibility(View.VISIBLE);
                if (btnEndCallLarge != null) btnEndCallLarge.setVisibility(View.VISIBLE);

                Log.d("CALL_UI", "State: DIALING");
                break;

            case STATE_ACTIVE:
                tvCallStatus.setText("Call in Progress");
                tvCallStatus.setTextColor(0xFF2196F3);

                if (ongoingCallLayout != null) ongoingCallLayout.setVisibility(View.VISIBLE);
                if (callButtonRow != null) callButtonRow.setVisibility(View.VISIBLE);
                if (btnMute != null) btnMute.setVisibility(View.VISIBLE);
                if (btnSpeaker != null) btnSpeaker.setVisibility(View.VISIBLE);
                if (btnHold != null) btnHold.setVisibility(View.VISIBLE);
                if (btnRecord != null) btnRecord.setVisibility(View.VISIBLE);
                if (btnEndCallLarge != null) btnEndCallLarge.setVisibility(View.VISIBLE);
                if (tvCallDuration != null) tvCallDuration.setVisibility(View.VISIBLE);
                if (callButtonRow != null) callButtonRow.setVisibility(View.VISIBLE);

                stopIncomingAlert();
                startCallDurationTimer();
                if (previousState != STATE_ACTIVE) {
                    startScamDetection();
                }
                Log.d("CALL_UI", "State: ACTIVE");
                break;

            case STATE_DISCONNECTED:
                if (tvCallStatus != null) {
                    tvCallStatus.setText("Call Ended");
                    tvCallStatus.setTextColor(0xFFFF9800);
                }
                stopCallDurationTimer();
                stopScamDetection();
                Log.d("CALL_UI", "State: DISCONNECTED");
                finish();
                break;

            default:
                Log.d("CALL_UI", "State: UNKNOWN - hiding UI");
                stopIncomingAlert();
                break;
        }
    }
    
    private void startIncomingAlert() {
        if (ringtone == null) {
            Uri alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(this, alertUri);
        }
        if (ringtone != null && !ringtone.isPlaying()) {
            ringtone.play();
        }

        if (vibrator == null) {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(800);
            }
        }
    }

    private void stopIncomingAlert() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    /**
     * Toggle mute state
     */
    private void toggleMute() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager == null) {
            Log.e(TAG, "AudioManager is null - cannot toggle mute");
            return;
        }

        boolean currentlyMuted = audioManager.isMicrophoneMute();
        audioManager.setMicrophoneMute(!currentlyMuted);
        isMuted = audioManager.isMicrophoneMute();

        if (isMuted) {
            btnMute.setImageResource(R.drawable.ic_mic_off);
            tvMuteLabel.setText("Unmute");
            tvMuteLabel.setTextColor(0xFFF44336);
            Log.i(TAG, "🔇 Microphone muted");
        } else {
            btnMute.setImageResource(R.drawable.ic_mic_on);
            tvMuteLabel.setText("Mute");
            tvMuteLabel.setTextColor(0xFFFFFFFF);
            Log.i(TAG, "🔊 Microphone unmuted");
        }
    }
    
    /**
     * Toggle speaker state - Direct hardware read
     */
    private void toggleSpeaker() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager == null) {
            Log.e(TAG, "AudioManager is null - cannot toggle speaker");
            return;
        }

        boolean currentlyOn = audioManager.isSpeakerphoneOn();
        audioManager.setSpeakerphoneOn(!currentlyOn);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        boolean newState = audioManager.isSpeakerphoneOn();
        if (newState) {
            btnSpeaker.setImageResource(R.drawable.ic_speaker_on);
            tvSpeakerLabel.setText("Speaker");
            tvSpeakerLabel.setTextColor(0xFF4CAF50);
            Log.i(TAG, "🔊 Now on Speaker");
        } else {
            btnSpeaker.setImageResource(R.drawable.ic_speaker_off);
            tvSpeakerLabel.setText("Earpiece");
            tvSpeakerLabel.setTextColor(0xFFFFFFFF);
            Log.i(TAG, "🔇 Now on Earpiece");
        }
    }
    
    /**
     * Toggle hold state - Direct Telecom state check
     */
    private void toggleHold() {
        android.telecom.Call activeCall = ScamShieldInCallService.currentCall;
        if (activeCall == null) {
            Log.e(TAG, "No active call to toggle hold");
            return;
        }

        try {
            int state = activeCall.getState();
            if (state == android.telecom.Call.STATE_HOLDING) {
                activeCall.unhold();
                btnHold.setImageResource(R.drawable.ic_hold);
                tvHoldLabel.setText("Hold");
                tvHoldLabel.setTextColor(0xFFFFFFFF);
                Log.i(TAG, "▶️ Resumed from hold");
            } else if (state == android.telecom.Call.STATE_ACTIVE) {
                activeCall.hold();
                btnHold.setImageResource(R.drawable.ic_hold_active);
                tvHoldLabel.setText("Unhold");
                tvHoldLabel.setTextColor(0xFFFF9800);
                Log.i(TAG, "⏸️ Now on hold");
            } else {
                Log.w(TAG, "Hold toggle ignored for call state: " + state);
            }
        } catch (Exception e) {
            Log.e(TAG, "Hold error: " + e.getMessage());
        }
    }
    
    /**
     * Toggle record state - uses MediaRecorder with VOICE_COMMUNICATION
     */
    private boolean isRecording = false;
    private android.media.MediaRecorder mediaRecorder;
    private String recordingFilePath;
    
    /**
     * Toggle record state - Manual start/stop with MediaRecorder lifecycle
     */
    private void toggleRecord() {
        // CRITICAL: Use static reference from InCallService
        android.telecom.Call activeCall = ScamShieldInCallService.currentCall;
        
        if (activeCall == null) {
            Log.w(TAG, "No active call to record");
            return;
        }
        
        // Check RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, 
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }
        
        try {
            if (isRecording) {
                // Stop recording
                if (mediaRecorder != null) {
                    mediaRecorder.stop();
                    mediaRecorder.release();
                    mediaRecorder = null;
                }
                isRecording = false;
                btnRecord.setImageResource(R.drawable.ic_record);
                btnRecord.clearColorFilter();
                tvRecordLabel.setText("Record");
                tvRecordLabel.setTextColor(0xFFFFFFFF);
                android.widget.Toast.makeText(this, "Recording saved: " + recordingFilePath, android.widget.Toast.LENGTH_LONG).show();
                Log.i(TAG, "✅ Recording stopped: " + recordingFilePath);
            } else {
                // Use external files dir + VOICE_COMMUNICATION for two-way
                java.io.File recordingsDir = getExternalFilesDir(null);
                if (recordingsDir == null) {
                    recordingsDir = getFilesDir();
                }
                
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US);
                String fileName = "Call_" + sdf.format(new java.util.Date()) + ".m4a";
                recordingFilePath = new java.io.File(recordingsDir, fileName).getAbsolutePath();
                
                mediaRecorder = new android.media.MediaRecorder();
                mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION);
                mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioSamplingRate(44100);
                mediaRecorder.setAudioEncodingBitRate(128000);
                mediaRecorder.setOutputFile(recordingFilePath);
                mediaRecorder.prepare();
                mediaRecorder.start();
                
                isRecording = true;
                btnRecord.setImageResource(R.drawable.ic_record);
                btnRecord.setColorFilter(0xFFFF0000);
                tvRecordLabel.setText("Stop");
                tvRecordLabel.setTextColor(0xFFFF0000);
                android.widget.Toast.makeText(this, "Recording Started", android.widget.Toast.LENGTH_SHORT).show();
                Log.i(TAG, "🔴 Recording started: " + recordingFilePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Recording error: " + e.getMessage());
            android.widget.Toast.makeText(this, "Recording failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            isRecording = false;
        }
    }
    
    /**
     * Stop recording - called on call end or auto-stop
     */
    private void stopRecording() {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                Log.i(TAG, "✅ Recording auto-stopped: " + recordingFilePath);
            } catch (Exception e) {
                Log.e(TAG, "Error auto-stopping recorder: " + e.getMessage());
            }
            mediaRecorder = null;
            isRecording = false;
            btnRecord.setImageResource(R.drawable.ic_record);
            btnRecord.clearColorFilter();
            tvRecordLabel.setText("Record");
            tvRecordLabel.setTextColor(0xFFFFFFFF);
        }
    }
    
    /**
     * Answer the incoming call
     */
    private void answerCall() {
        // Set audio mode for voice communication only; state updates are handled by call lifecycle
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(false);
        }
        Log.i(TAG, "✅ Answer call requested - audio mode set");
    }
    
    /**
     * End the current call - Direct hardware hook
     */
    private void endCall() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(false);
        }
        Log.i(TAG, "✅ End call requested");
    }
    
    /**
     * Notify InCallService to answer the call
     */
    private void notifyInCallServiceAnswer() {
        try {
            Intent intent = new Intent("com.shreyanshi.scamshield.ACTION_ANSWER_CALL");
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error notifying InCallService: " + e.getMessage());
        }
    }
    
    /**
     * Notify InCallService to disconnect the call
     */
    private void notifyInCallServiceDisconnect() {
        try {
            Intent intent = new Intent("com.shreyanshi.scamshield.ACTION_DISCONNECT_CALL");
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error notifying InCallService: " + e.getMessage());
        }
    }
    
    /**
     * Start call duration timer
     */
    private void startCallDurationTimer() {
        callStartTime = System.currentTimeMillis();
        
        durationRunnable = new Runnable() {
            @Override
            public void run() {
                if (callState != STATE_ACTIVE) return;
                
                long elapsed = System.currentTimeMillis() - callStartTime;
                long minutes = (elapsed / 1000) / 60;
                long seconds = (elapsed / 1000) % 60;
                String time = String.format("%02d:%02d", minutes, seconds);
                tvCallDuration.setText(time);
                durationHandler.postDelayed(this, 1000);
            }
        };
        
        durationHandler.post(durationRunnable);
    }
    
    /**
     * Stop call duration timer
     */
    private void stopCallDurationTimer() {
        if (durationRunnable != null) {
            durationHandler.removeCallbacks(durationRunnable);
            durationRunnable = null;
        }
    }
    
    /**
     * Start scam detection - notify ScamMonitorService
     * Only called when call state becomes STATE_ACTIVE
     */
    private void startScamDetection() {
        try {
            // Send broadcast to start monitoring
            Intent intent = new Intent(ScamMonitorService.ACTION_START);
            intent.putExtra(ScamMonitorService.EXTRA_CALL_NUMBER, callerNumber);
            intent.putExtra(ScamMonitorService.EXTRA_FROM_INCALL, true);
            intent.putExtra(ScamMonitorService.EXTRA_CALL_ACTIVE, true); // Sync: call is active
            sendBroadcast(intent);
            
            tvScamStatus.setText("🛡️ ScamShield: Detecting...");
            tvScamStatus.setTextColor(0xFF4CAF50); // Green
            Log.i(TAG, "✅ Scam detection started (STATE_ACTIVE sync)");
        } catch (Exception e) {
            Log.e(TAG, "Error starting scam detection: " + e.getMessage());
        }
    }
    
    /**
     * Stop scam detection - notify ScamMonitorService
     */
    private void stopScamDetection() {
        try {
            // Send broadcast to stop monitoring
            Intent intent = new Intent(ScamMonitorService.ACTION_STOP);
            sendBroadcast(intent);
            
            tvScamStatus.setText("🛡️ ScamShield: Inactive");
            tvScamStatus.setTextColor(0xFFAAAAAA); // Gray
            Log.i(TAG, "✅ Scam detection stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping scam detection: " + e.getMessage());
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Register broadcast receiver for call events
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.shreyanshi.scamshield.ACTION_DISCONNECT_CALL");
        filter.addAction("com.shreyanshi.scamshield.ACTION_FINISH_INCALL");
        // CRITICAL: Listen for CALL_DISCONNECTED from InCallService
        filter.addAction(ScamShieldInCallService.ACTION_CALL_DISCONNECTED);
        // STEP 1: Listen for ACTION_FINISH_UI to prevent crashes
        filter.addAction(ScamShieldInCallService.ACTION_FINISH_UI);
        // STEP 2: Listen for CALL_ACTIVE - triggers UI state transition
        filter.addAction(ScamShieldInCallService.ACTION_CALL_ACTIVE);
        // STEP 3: Listen for real-time state updates from InCallService
        filter.addAction(ScamShieldInCallService.ACTION_CALL_STATE_UPDATE);
        // Recording controls from InCallService
        filter.addAction("com.shreyanshi.scamshield.ACTION_START_RECORDING");
        filter.addAction("com.shreyanshi.scamshield.ACTION_STOP_RECORDING");
        if (callEventReceiver != null) {
            try {
                registerReceiver(callEventReceiver, filter);
                Log.i(TAG, "Call event receiver registered");
            } catch (Exception e) {
                Log.e(TAG, "Error registering receiver: " + e.getMessage());
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Unregister broadcast receiver
        if (callEventReceiver != null) {
            try {
                unregisterReceiver(callEventReceiver);
                Log.i(TAG, "Call event receiver unregistered");
            } catch (Exception e) {
                // Ignore if not registered
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Stop timer
        stopCallDurationTimer();
        
        // Unbind from service
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
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
    
    /**
     * Static method to create intent with contact name
     */
    public static Intent createIntent(Context context, String number, String name, int state, boolean isIncoming) {
        Intent intent = createIntent(context, number, state, isIncoming);
        intent.putExtra(EXTRA_CONTACT_NAME, name);
        return intent;
    }
}