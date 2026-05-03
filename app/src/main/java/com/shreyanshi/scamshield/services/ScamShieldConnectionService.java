package com.shreyanshi.scamshield.services;

import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

import java.util.UUID;

/**
 * ScamShieldConnectionService - CRITICAL for Default Dialer Role
 * 
 * This service is REQUIRED for Android's Telecom framework to recognize ScamShield 
 * as a valid phone dialer app. Without this, incoming calls won't work on many devices.
 * 
 * On Android 10+, this service must be registered in AndroidManifest.xml with:
 * - android:name="android.telecom.ConnectionService" intent filter
 * - android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE"
 * 
 * Responsibilities:
 * - Create Connection objects for incoming/outgoing calls
 * - Handle call state transitions
 * - Bridge between Telecom framework and ScamShield UI
 * 
 * Flow:
 * 1. System calls onCreateOutgoingConnection() or onCreateIncomingConnection()
 * 2. We create a ScamShieldConnection object
 * 3. Connection handles call states (RINGING, ACTIVE, DISCONNECTED, etc.)
 * 4. InCallService is notified of call state changes
 * 5. InCallActivity shows call UI
 * 
 * @author ScamShield Development Team
 * @version 1.0
 */
public class ScamShieldConnectionService extends ConnectionService {
    private static final String TAG = "ScamShield-ConnService";
    
    /**
     * Called when system needs to create an incoming connection.
     * This happens when a call is received.
     */
    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.i(TAG, "✅ onCreateIncomingConnection() called - INCOMING CALL");
        
        try {
            // Create connection object for this call
            ScamShieldConnection connection = new ScamShieldConnection(this, request, true);
            
            // Get phone number from request
            String phoneNumber = "";
            if (request.getAddress() != null) {
                phoneNumber = request.getAddress().getSchemeSpecificPart();
                Log.i(TAG, "📞 Incoming from: " + phoneNumber);
            }
            
            // Initialize connection in RINGING state
            connection.setRinging();
            connection.setPhoneNumber(phoneNumber);
            
            // Enable handle for accept/decline
            connection.setConnectionCapabilities(
                Connection.CAPABILITY_SUPPORT_HOLD |
                Connection.CAPABILITY_HOLD |
                Connection.CAPABILITY_MUTE
            );
            
            Log.i(TAG, "✅ IncomingConnection created successfully");
            return connection;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating incoming connection: " + e.getMessage());
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.ERROR));
        }
    }
    
    /**
     * Called when system needs to create an outgoing connection.
     * This happens when user makes a call from ScamShield or other dialer.
     */
    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.i(TAG, "✅ onCreateOutgoingConnection() called - OUTGOING CALL");
        
        try {
            // Create connection object for this call
            ScamShieldConnection connection = new ScamShieldConnection(this, request, false);
            
            // Get phone number from request
            String phoneNumber = "";
            if (request.getAddress() != null) {
                phoneNumber = request.getAddress().getSchemeSpecificPart();
                Log.i(TAG, "📞 Dialing: " + phoneNumber);
            }
            
            // Initialize connection in DIALING state
            connection.setDialing();
            connection.setPhoneNumber(phoneNumber);
            
            // Enable call controls
            connection.setConnectionCapabilities(
                Connection.CAPABILITY_SUPPORT_HOLD |
                Connection.CAPABILITY_HOLD |
                Connection.CAPABILITY_MUTE
            );
            
            Log.i(TAG, "✅ OutgoingConnection created successfully");
            return connection;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating outgoing connection: " + e.getMessage());
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.ERROR));
        }
    }
    
    /**
     * Called when system needs to create an unspecified connection.
     * Rare, but handle for safety.
     */
    public Connection onCreateUnknownConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.w(TAG, "⚠️ onCreateUnknownConnection() called");
        
        try {
            ScamShieldConnection connection = new ScamShieldConnection(this, request, false);
            connection.setActive();
            Log.i(TAG, "✅ UnknownConnection created");
            return connection;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating unknown connection: " + e.getMessage());
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.ERROR));
        }
    }
    
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "✅ ScamShieldConnectionService created");
    }
    
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "🛑 ScamShieldConnectionService destroyed");
    }
}
