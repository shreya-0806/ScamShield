package com.shreyanshi.scamshield.services;

import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.activities.InCallActivity;

public class ScamShieldInCallService extends InCallService {
    private static final String TAG = "ScamShieldInCallService";
    public static Call currentCall = null;
    private LocalBroadcastManager localBroadcastManager;
    private Call.Callback callCallback;

    public static final String ACTION_CALL_STATE_CHANGED = "com.shreyanshi.scamshield.ACTION_CALL_STATE_CHANGED";
    public static final String ACTION_CALL_ACTIVE = "com.shreyanshi.scamshield.ACTION_CALL_ACTIVE";
    public static final String ACTION_CALL_DISCONNECTED = "com.shreyanshi.scamshield.ACTION_CALL_DISCONNECTED";
    public static final String EXTRA_CALL_STATE = "call_state";
    public static final String EXTRA_PHONE_NUMBER = "phone_number";

    @Override
    public void onCreate() {
        super.onCreate();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        callCallback = new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                Log.d(TAG, "Call state changed: " + state);
                Intent intent = new Intent(ACTION_CALL_STATE_CHANGED);
                intent.putExtra(EXTRA_CALL_STATE, state);
                intent.putExtra(EXTRA_PHONE_NUMBER, getPhoneNumber(call));
                localBroadcastManager.sendBroadcast(intent);

                if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                    if (currentCall == call) {
                        currentCall = null;
                    }
                    Log.i(TAG, "Call disconnected");
                }
            }
        };
        Log.i(TAG, "InCallService created");
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        String phoneNumber = getPhoneNumber(call);
        Log.i(TAG, "Call added: " + phoneNumber);
        currentCall = call;
        call.registerCallback(callCallback);

        Intent activityIntent = new Intent(this, InCallActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activityIntent.putExtra(EXTRA_CALL_STATE, call.getState());
        activityIntent.putExtra(EXTRA_PHONE_NUMBER, phoneNumber);
        startActivity(activityIntent);
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        Log.i(TAG, "Call removed: " + getPhoneNumber(call));
        if (currentCall == call) {
            currentCall = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        currentCall = null;
        Log.i(TAG, "InCallService destroyed");
    }

    private String getPhoneNumber(Call call) {
        if (call.getDetails() != null && call.getDetails().getHandle() != null) {
            return call.getDetails().getHandle().getSchemeSpecificPart();
        }
        return "Unknown";
    }
}
