package com.shreyanshi.scamshield.services;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.shreyanshi.scamshield.activities.InCallActivity;

public class ScamShieldInCallService extends InCallService {

    private static final String TAG = "ScamShield-InCall";

    public static final String ACTION_CALL_STATE_UPDATE =
            "com.shreyanshi.scamshield.ACTION_CALL_STATE_UPDATE";

    public static final String EXTRA_CALL_STATE = "state";

    public static final int STATE_RINGING = 1;
    public static final int STATE_ACTIVE = 2;
    public static final int STATE_DISCONNECTED = 3;

    public static Call currentCall = null;

    private boolean isActivityLaunched = false;

    private AudioManager audioManager;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        Log.i(TAG, "Service created");
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);

        currentCall = call;

        String number = getNumber(call);

        Log.i(TAG, "📞 Call added: " + number);

        call.registerCallback(callCallback);

        launchInCallActivity(number, STATE_RINGING);
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);

        Log.i(TAG, "📞 Call removed");

        if (call != null) {
            call.unregisterCallback(callCallback);
        }

        currentCall = null;
        isActivityLaunched = false;

        sendState(STATE_DISCONNECTED);
    }

    private final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            handleStateChange(call, state);
        }
    };

    private void handleStateChange(Call call, int state) {

        int ourState;

        switch (state) {
            case Call.STATE_RINGING:
                ourState = STATE_RINGING;
                break;

            case Call.STATE_ACTIVE:
                ourState = STATE_ACTIVE;
                break;

            case Call.STATE_DISCONNECTED:
                ourState = STATE_DISCONNECTED;
                isActivityLaunched = false;
                break;

            default:
                return;
        }

        Log.i(TAG, "📡 State: " + ourState);

        sendState(ourState);
    }

    private void sendState(int state) {
        Intent intent = new Intent(ACTION_CALL_STATE_UPDATE);
        intent.putExtra(EXTRA_CALL_STATE, state);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void launchInCallActivity(String number, int state) {
        if (isActivityLaunched) return;

        try {
            Intent intent = InCallActivity.createIntent(this, number, state, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            startActivity(intent);

            isActivityLaunched = true;

            Log.i(TAG, "✅ Activity launched");

        } catch (Exception e) {
            Log.e(TAG, "Error launching activity: " + e.getMessage());
        }
    }

    private String getNumber(Call call) {
        if (call.getDetails() != null &&
                call.getDetails().getHandle() != null) {

            return call.getDetails()
                    .getHandle()
                    .getSchemeSpecificPart();
        }
        return "Unknown";
    }
}