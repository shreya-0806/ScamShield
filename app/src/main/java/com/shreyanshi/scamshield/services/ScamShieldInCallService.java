package com.shreyanshi.scamshield.services;

import android.os.Build;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

/**
 * ScamShieldInCallService - Telecom Framework Integration for Default Dialer Role
 * 
 * This service is required when ScamShield is set as the system's default phone dialer.
 * It implements the InCallService interface to handle incoming and outgoing calls through
 * Android's Telecom framework (Android 10+).
 * 
 * Responsibilities:
 * - Handle call lifecycle events (onCallAdded, onCallRemoved, etc.)
 * - Log call state changes for debugging
 * - Prevent app crashes when system calls InCallService methods
 * - Provide minimal implementation (no complex call management needed)
 * 
 * Requirements for Default Dialer:
 * - Android 10+: RoleManager.ROLE_DIALER
 * - AndroidManifest: android.permission.BIND_INCALL_SERVICE
 * - AndroidManifest: android.permission.MANAGE_ONGOING_CALLS (Android 11+)
 * 
 * @author ScamShield Development Team
 * @version 1.0
 */
public class ScamShieldInCallService extends InCallService {
    private static final String TAG = "ScamShield-InCall";

    /**
     * Called when a new call is added to the system.
     * This occurs both for incoming and outgoing calls.
     * 
     * We log the call but don't interfere with normal call handling.
     * The ScamMonitorService handles scam detection independently via CallReceiver.
     */
    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        try {
            if (call != null && call.getDetails() != null) {
                String handle = call.getDetails().getHandle() != null ? 
                    call.getDetails().getHandle().getSchemeSpecificPart() : "Unknown";
                Log.i(TAG, "☎️ Call added: " + handle);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error logging call add: " + e.getMessage());
        }
    }

    /**
     * Called when a call is removed from the system.
     * This occurs when a call ends or is dismissed.
     */
    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        try {
            if (call != null && call.getDetails() != null) {
                String handle = call.getDetails().getHandle() != null ? 
                    call.getDetails().getHandle().getSchemeSpecificPart() : "Unknown";
                Log.i(TAG, "☎️ Call removed: " + handle);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error logging call removal: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "✅ ScamShieldInCallService created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "🛑 ScamShieldInCallService destroyed");
    }
}
