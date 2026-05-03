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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telecom.Call;
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
    
    // UI Components
    private TextView tvCallStatus;
    private TextView tvContactName;
    private TextView tvCallerNumber;
    private TextView tvCallDuration;
    private TextView tvScamStatus;
    private TextView tvAvatarInitial;
    private View avatarBackground;
    private Button btnAnswer;
    private Button btnEndCall;
    private Button btnEndCallLarge;  // Large red button for active calls
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
    private Handler durationHandler;
    private Runnable durationRunnable;
    private String callerNumber = "";
    private Call currentCall = null;
    private View bottomSection;
    private View callButtonRow;
    
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
                    callState = STATE_ACTIVE;
                    updateUIForCallState();
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
        updateUIForCallState();
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
        btnAnswer = findViewById(R.id.btnAnswer);
        btnEndCall = findViewById(R.id.btnEndCall);
        btnEndCallLarge = findViewById(R.id.btnEndCallLarge);  // Large red button for active calls
        btnMute = findViewById(R.id.btnMute);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        btnHold = findViewById(R.id.btnHold);
        btnRecord = findViewById(R.id.btnRecord);
        tvMuteLabel = findViewById(R.id.tvMuteLabel);
        tvSpeakerLabel = findViewById(R.id.tvSpeakerLabel);
        tvHoldLabel = findViewById(R.id.tvHoldLabel);
        tvRecordLabel = findViewById(R.id.tvRecordLabel);
        bottomSection = findViewById(R.id.bottomSection);
        callButtonRow = findViewById(R.id.callButtonRow);
        incomingButtonRow = findViewById(R.id.incomingButtonRow);  // Add for RINGING state control
        
        // Answer button click
        btnAnswer.setOnClickListener(v -> {
            Log.i(TAG, "📞 Answer button clicked");
            answerCall();
        });
        
        // End call button click
        btnEndCall.setOnClickListener(v -> {
            Log.i(TAG, "📞 End call button clicked");
            endCall();
        });
        
        // Mute button click
        btnMute.setOnClickListener(v -> {
            toggleMute();
        });
        
        // Speaker button click
        btnSpeaker.setOnClickListener(v -> {
            toggleSpeaker();
        });
        
        // Hold button click
        btnHold.setOnClickListener(v -> {
            toggleHold();
        });
        
        // Record button click
        btnRecord.setOnClickListener(v -> {
            toggleRecord();
        });
        
        // Large end call button click (for active calls)
        btnEndCallLarge.setOnClickListener(v -> {
            Log.i(TAG, "📞 Large end call button clicked");
            endCall();
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
     * Update UI based on call state
     * TELECOM LISTENER: This is called when call state changes from RINGING to ACTIVE
     */
    private void updateUIForCallState() {
        switch (callState) {
            case STATE_RINGING:
                // Only btnAnswer and btnEndCall visible, all others hidden
                tvCallStatus.setText("Incoming Call");
                tvCallStatus.setTextColor(0xFF00FF00); // Green
                
                // Show answer and end call buttons row
                if (callButtonRow != null) callButtonRow.setVisibility(View.VISIBLE);
                
                // Show answer and end call buttons
                btnAnswer.setVisibility(View.VISIBLE);
                btnEndCall.setVisibility(View.VISIBLE);
                
                // Hide all Mute/Speaker/Hold/Record buttons individually
                btnMute.setVisibility(View.GONE);
                btnSpeaker.setVisibility(View.GONE);
                btnHold.setVisibility(View.GONE);
                btnRecord.setVisibility(View.GONE);
                if (bottomSection != null) bottomSection.setVisibility(View.GONE);
                
                tvCallDuration.setVisibility(View.GONE);
                Log.i(TAG, "🔄 UI: RINGING - only answer/end buttons visible");
                break;
                
            case STATE_ACTIVE:
                // btnAnswer hidden, all other buttons visible
                tvCallStatus.setText("Call in Progress");
                tvCallStatus.setTextColor(0xFF2196F3); // Blue
                
                // Hide answer button row (call is now active)
                if (callButtonRow != null) callButtonRow.setVisibility(View.GONE);
                
                // Hide answer button
                btnAnswer.setVisibility(View.GONE);
                
                // Show all other buttons
                btnEndCall.setVisibility(View.VISIBLE);
                btnMute.setVisibility(View.VISIBLE);
                btnSpeaker.setVisibility(View.VISIBLE);
                btnHold.setVisibility(View.VISIBLE);
                btnRecord.setVisibility(View.VISIBLE);
                if (bottomSection != null) bottomSection.setVisibility(View.VISIBLE);
                
                tvCallDuration.setVisibility(View.VISIBLE);
                startCallDurationTimer();
                
                // Audio mode sync - ensure voice path is ready
                AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    Log.i(TAG, "🔊 Audio mode set to MODE_IN_COMMUNICATION");
                }
                
                Log.i(TAG, "🔄 UI: ACTIVE - all call controls visible");
                
                // Start scam detection ONLY when call becomes active
                startScamDetection();
                break;
                
            case STATE_DISCONNECTED:
                // Hide all buttons
                tvCallStatus.setText("Call Ended");
                tvCallStatus.setTextColor(0xFFFF9800); // Orange
                
                btnAnswer.setVisibility(View.GONE);
                btnEndCall.setVisibility(View.GONE);
                btnMute.setVisibility(View.GONE);
                btnSpeaker.setVisibility(View.GONE);
                btnHold.setVisibility(View.GONE);
                btnRecord.setVisibility(View.GONE);
                if (bottomSection != null) bottomSection.setVisibility(View.GONE);
                if (callButtonRow != null) callButtonRow.setVisibility(View.GONE);
                stopCallDurationTimer();
                
                // Stop scam detection when call ends
                stopScamDetection();
                
                // Instant close
                Log.i(TAG, "📞 Call disconnected - finishing activity NOW");
                finishAndRemoveTask();
                break;
        }
    }
    
    /**
     * Toggle mute state
     */
    private void toggleMute() {
        isMuted = !isMuted;
        
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMicrophoneMute(isMuted);
        }
        
        // Update UI
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
        if (audioManager == null) return;
        
        // Read current hardware state
        boolean currentlyOn = audioManager.isSpeakerphoneOn();
        
        // Toggle: if currently on, turn off (earpiece); if off, turn on (speaker)
        audioManager.setSpeakerphoneOn(!currentlyOn);
        
        // Set audio mode for call
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        
        // Read NEW state after toggle for UI update
        boolean newState = audioManager.isSpeakerphoneOn();
        
        // Update UI based on NEW hardware state
        if (newState) {
            btnSpeaker.setImageResource(R.drawable.ic_speaker_on);
            tvSpeakerLabel.setText("Speaker");
            tvSpeakerLabel.setTextColor(0xFF4CAF50); // Green
            Log.i(TAG, "🔊 Now on Speaker");
        } else {
            btnSpeaker.setImageResource(R.drawable.ic_speaker_off);
            tvSpeakerLabel.setText("Earpiece");
            tvSpeakerLabel.setTextColor(0xFFFFFFFF); // White
            Log.i(TAG, "🔇 Now on Earpiece");
        }
    }
    
    /**
     * Toggle hold state - Direct Telecom state check
     */
    private void toggleHold() {
        android.telecom.Call activeCall = ScamShieldInCallService.currentCall;
        if (activeCall == null) return;
        
        try {
            // Direct read from Telecom state
            int state = activeCall.getState();
            if (state == android.telecom.Call.STATE_HOLDING) {
                // Currently on hold - unhold
                activeCall.unhold();
                btnHold.setImageResource(R.drawable.ic_hold);
                tvHoldLabel.setText("Hold");
                tvHoldLabel.setTextColor(0xFFFFFFFF);
                Log.i(TAG, "▶️ Resumed from hold");
            } else {
                // Not on hold - hold
                activeCall.hold();
                btnHold.setImageResource(R.drawable.ic_hold_active);
                tvHoldLabel.setText("Unhold");
                tvHoldLabel.setTextColor(0xFFFF9800);
                Log.i(TAG, "⏸️ Now on hold");
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
        callState = STATE_ACTIVE;
        
        // Set audio mode for voice communication
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(false); // Default to earpiece
        }
        
        updateUIForCallState();
        
        // Notify InCallService to answer the call via Telecom
        notifyInCallServiceAnswer();
        
        Log.i(TAG, "✅ Call answered - audio mode: IN_COMMUNICATION");
    }
    
    /**
     * End the current call - Direct hardware hook
     */
    private void endCall() {
        // Reset audio first
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(false);
        }
        
        // Use static activeCallInstance - FIRST CLICK HANGUP
        if (ScamShieldInCallService.activeCallInstance != null) {
            try {
                ScamShieldInCallService.activeCallInstance.disconnect();
                Log.i(TAG, "📞 Disconnected via activeCallInstance");
            } catch (Exception e) {
                Log.e(TAG, "Disconnect error: " + e.getMessage());
            }
        }
        
        // CRITICAL: Immediately finish - no waiting
        finishAndRemoveTask();
        Log.i(TAG, "📞 End and remove task");
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