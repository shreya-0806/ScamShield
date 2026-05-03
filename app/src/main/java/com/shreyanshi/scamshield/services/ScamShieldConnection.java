package com.shreyanshi.scamshield.services;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * ScamShieldConnection - Individual call connection handler
 * 
 * Handles individual call lifecycle: RINGING -> ACTIVE -> DISCONNECTED
 * Notifies InCallService and InCallActivity of state changes.
 * 
 * Key States:
 * - RINGING: Call incoming, user hasn't answered
 * - ACTIVE: Call is connected and active
 * - HOLDING: User placed call on hold
 * - DISCONNECTED: Call ended
 * 
 * @author ScamShield Development Team
 */
public class ScamShieldConnection extends Connection {
    private static final String TAG = "ScamShield-Conn";
    
    private Context context;
    private String phoneNumber = "";
    private boolean isIncoming = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ConnectionRequest request;
    
    // Call state tracking
    private int currentState = STATE_NEW;
    private boolean isMuted = false;
    private boolean isOnHold = false;
    
    public ScamShieldConnection(Context context, ConnectionRequest request, boolean isIncoming) {
        this.context = context;
        this.request = request;
        this.isIncoming = isIncoming;
        
        // Set address (phone number)
        if (request.getAddress() != null) {
            setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
            this.phoneNumber = request.getAddress().getSchemeSpecificPart();
        }
        
        // Set call direction
        if (isIncoming) {
            setConnectionCapabilities(
                CAPABILITY_SUPPORT_HOLD |
                CAPABILITY_HOLD |
                CAPABILITY_MUTE
            );
        }
        
        Log.i(TAG, "✅ Connection created for: " + phoneNumber + 
            " (" + (isIncoming ? "INCOMING" : "OUTGOING") + ")");
    }
    
    /**
     * Set phone number for this connection
     */
    public void setPhoneNumber(String number) {
        this.phoneNumber = number;
    }
    
    /**
     * Accept incoming call (answer)
     */
    @Override
    public void onAnswer() {
        Log.i(TAG, "📞 Answer button pressed");
        // Transition from RINGING to ACTIVE
        setActive();
        handleCallActive();
    }
    
    /**
     * Reject incoming call
     */
    @Override
    public void onReject() {
        Log.i(TAG, "📞 Reject button pressed");
        disconnect();
    }
    
    /**
     * Disconnect/End call
     */
    @Override
    public void onDisconnect() {
        Log.i(TAG, "📞 Disconnect button pressed");
        disconnect();
    }
    
    /**
     * Hold call
     */
    @Override
    public void onHold() {
        Log.i(TAG, "📞 Hold button pressed");
        setOnHold();
    }
    
    /**
     * Resume call from hold
     */
    @Override
    public void onUnhold() {
        Log.i(TAG, "📞 Unhold button pressed");
        setActive();
    }
    
    /**
     * Mute microphone
     */
    @Override
    public void onMute(boolean shouldMute) {
        Log.i(TAG, "🔇 " + (shouldMute ? "Muting" : "Unmuting") + " microphone");
        this.isMuted = shouldMute;
        // Notify UI
        notifyAudioStateChanged();
    }
    
    /**
     * Called when call is DISCONNECTED
     */
    public void disconnect() {
        Log.i(TAG, "🛑 Disconnecting call...");
        
        // Set state to DISCONNECTED
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        
        // Notify broadcast
        try {
            Intent intent = new Intent(ScamShieldInCallService.ACTION_CALL_DISCONNECTED);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            Log.i(TAG, "📡 Broadcast sent: CALL_DISCONNECTED");
        } catch (Exception e) {
            Log.w(TAG, "Error sending disconnect broadcast: " + e.getMessage());
        }
        
        // Close the connection
        destroy();
    }
    
    /**
     * Handle call becoming active
     * Notify InCallService and start scam detection
     */
    private void handleCallActive() {
        Log.i(TAG, "🎯 Call is now ACTIVE");
        
        // Send broadcast to InCallService about active call
        try {
            Intent intent = new Intent(ScamShieldInCallService.ACTION_CALL_ACTIVE);
            intent.putExtra(ScamShieldInCallService.EXTRA_PHONE_NUMBER, phoneNumber);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            Log.i(TAG, "📡 Broadcast sent: CALL_ACTIVE");
        } catch (Exception e) {
            Log.w(TAG, "Error sending active broadcast: " + e.getMessage());
        }
    }
    
    /**
     * Notify UI about audio state changes (mute, hold, etc.)
     */
    private void notifyAudioStateChanged() {
        try {
            Intent intent = new Intent("com.shreyanshi.scamshield.ACTION_AUDIO_STATE_CHANGED");
            intent.putExtra("is_muted", isMuted);
            intent.putExtra("is_on_hold", isOnHold);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        } catch (Exception e) {
            Log.w(TAG, "Error notifying audio state: " + e.getMessage());
        }
    }
    
    /**
     * Get current phone number
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    /**
     * Check if this is incoming call
     */
    public boolean isIncomingCall() {
        return isIncoming;
    }
    
    /**
     * Check if call is muted
     */
    public boolean isMuted() {
        return isMuted;
    }
    
    /**
     * Check if call is on hold
     */
    public boolean isOnHold() {
        return isOnHold;
    }
    
    @Override
    public void onStateChanged(int state) {
        Log.d(TAG, "Connection state changed to: " + state);
        super.onStateChanged(state);
    }
}
