# ScamShield - Development Guidelines

## Project Overview
ScamShield is an Android app that detects scam calls in real-time using Google On-Device Speech Recognition and displays alerts to protect users from fraud.

## Architecture
- **Language**: Java (Android)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34
- **Architecture**: Fragment-based with BottomNavigationView
- **Key Libraries**: Google On-Device Speech Recognition (native Android), Material Design

## Core Components
- `ScamMonitorService` - Foreground service for call monitoring
- `ScamAlertActivity` - Alert display (Activity-based, no overlay)
- `CallReceiver` - BroadcastReceiver for call state changes
- `GoogleSpeechRecognizer` - Google On-Device speech-to-text processing
- `ScamDatabaseHelper` - SQLite database for call history
- `NewsFragment` - Dynamic scam news with RSS feed
- `BlockedNumberDatabase` - Block/unblock contacts

## Permissions (All Optional Except Core)
- `RECORD_AUDIO` - Required for call monitoring (optional, show warning if denied)
- `READ_PHONE_STATE` - Detect call states
- `CALL_PHONE` - Make direct calls
- `READ_CALL_LOG` - Access call history
- `READ_CONTACTS` - Show contacts
- `FOREGROUND_SERVICE` - Background monitoring
- `POST_NOTIFICATIONS` - Show alerts
- `INTERNET` - Fetch news RSS feed
- `SYSTEM_ALERT_WINDOW` - NOT REQUIRED (use Activity instead)
- `ANSWER_PHONE_CALLS` - Answer phone calls

## DO
1. Use `startForegroundService()` for monitoring services on Android O+
2. Handle all permissions gracefully with fallback UI
3. Use dark theme (#121212 background) for consistent UI
4. Process audio locally - never upload without explicit user consent
5. Add meaningful logging with TAG constants
6. Register all services in AndroidManifest.xml
7. Test on Moto/Stock Android devices for compatibility
8. Use Activity-based alerts instead of SYSTEM_ALERT_WINDOW overlay
9. Fetch scam news from Google News RSS feed
10. Read call history from system CallLog provider
11. Use custom app icon from `icon/shieldicon.jpeg` (regenerate mipmap icons when changed)
12. Dark mode toggle in Settings applies theme immediately with recreate()
13. Contacts use BlockedNumberDatabase for block/unblock functionality
14. Use Google On-Device Speech Recognizer via `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)`
15. Set `RecognizerIntent.EXTRA_PREFER_OFFLINE = true` for offline-first operation
16. Implement auto-restart listening in `onResults()` and `onError()` methods for continuous monitoring
17. **CRITICAL - Android 14 (API 34) Foreground Service Requirements:**
    a. **Start service from MainActivity in foreground state (visible on screen)**
       - Service MUST be started while MainActivity.onStart() is active (visible to user)
       - Service MUST NOT be started from Fragment or background context
       - Service MUST NOT be started from broadcast receiver or other background components
       - Pattern: User grants permission → MainActivity.onRequestPermissionsResult() → startForegroundService()
    b. **Call startForeground() within 5 seconds of onStartCommand()**
       - Must be first operation in onStartCommand() (line 1)
       - Must complete notification setup within 5000ms
       - Add timing logs: `long startTime = System.currentTimeMillis(); startForegroundWithNotification(); long elapsed = System.currentTimeMillis() - startTime;`
       - Log warning if elapsed > 5000ms
    c. **Manifest declarations are correct:**
       - ✅ `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />`
       - ✅ `<service android:foregroundServiceType="microphone|specialUse">`
       - ✅ `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ... />`
    d. **Ensure startForeground() call includes type flag:**
       - ✅ API Q+ (29): `startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)`
       - ✅ API O-P (26-28): `startForeground(ID, notification)` (no type flag)
17. Display persistent notification with "ScamShield Active" title and "Protecting you from fraud calls..." text
18. Show Toast feedback "📢 Heard: [text]" for every recognized word (onPartialResults)
19. Implement 30-second debounce between alert notifications to prevent spam
20. Use white monochrome notification icon (ic_notification.xml) - never use colored icons

## Master Logic & Keyword Expansion (Android 15 Fix)
21. Bridge speech results to detector: Extract text + send to terminal + callback to listener
    - `onPartialResults()`: listener.onSpeechRecognized(text) + listener.onDebugLog("📢 LIVE TEXT: " + text)
    - `onResults()`: listener.onSpeechRecognized(text) + listener.onDebugLog("📢 LIVE TEXT: " + text)
22. Use universal scam keyword array with 40+ keywords:
    - `String[] SCAM_KEYWORDS = {"otp", "cvv", "password", "pin", "bank", "kyc", "lottery", "blocked", "suspended", "gift card", "customer care", ...}`
23. Case-insensitive keyword matching: `if (normalizedText.contains(keyword.toLowerCase()))`
24. Collect ALL matched keywords and log each: `sendDebugLogBroadcast("🚨 ALERT: Scam word [" + keyword + "] detected!")`

## Default Dialer Role (Android 10+)
25. Request default dialer role on EVERY app open using RoleManager
    - Call `requestDefaultDialerRole()` in `onCreate()` after permissions
    - Use `RoleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)` for Android 10+
    - Log `📢 Requesting Default Dialer Role...` before calling
    - App appears in "Default apps > Phone app" after user accepts
26. Manifest must have InCallService with BIND_INCALL_SERVICE permission
    - `<service android:permission="android.permission.BIND_INCALL_SERVICE">`
    - `<intent-filter><action android:name="android.telecom.InCallService" /></intent-filter>`

## DON'T
1. DON'T require SYSTEM_ALERT_WINDOW (causes install issues on Moto devices)
2. DON'T permanently save audio recordings - only process in memory
3. DON'T assume permissions are granted - always check and request
4. DON'T block the main thread - use ExecutorService for background tasks
5. DON'T hardcode URLs or API keys - use SharedPreferences or BuildConfig
6. DON'T use deprecated APIs without fallback for older Android versions
7. DON'T require overlay permission for any feature
8. DON'T use launcher icons (ic_launcher.webp) or adaptive icons for notification small icons
9. DON'T use colored icons for notifications - MUST be pure white (#FFFFFF) monochrome
10. DON'T assume Google Speech is available - always check with `SpeechRecognizer.isRecognitionAvailable()`
11. DON'T leave SpeechRecognizer listening indefinitely - implement auto-restart with bounded delays
12. DON'T ignore `onError()` callbacks - implement graceful error handling with logging
13. DON'T assume Toast will work in background - always use handler.post() for UI updates
14. DON'T assume TelecomManager.endCall() is available - wrap in try-catch
15. DON'T use deprecated WindowManager flags without API level fallback
16. **CRITICAL - Android 15 Foreground Service Violations:**
    - DON'T start service without FOREGROUND_SERVICE_TYPE_MICROPHONE on API 34+
    - DON'T skip call to startForeground() within 5 seconds of onStartCommand()
    - DON'T forget to log timing: `elapsed = System.currentTimeMillis() - startTime`
16. **CRITICAL - Android 14 (API 34) Foreground Service Violations:**
    - DON'T start service from Fragment (must start from Activity in foreground)
    - DON'T start service from BroadcastReceiver or background context
    - DON'T start service from onStop()/onPause() callbacks (Activity not visible)
    - DON'T call startForeground() after >5 seconds elapsed from onStartCommand()
    - DON'T forget to include ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE flag on API Q+
    - DON'T skip notification channel creation on Android 8.0+ (will crash)
    - DON'T assume startForeground() will work without foreground notification
    - DON'T ignore timing requirements - measure and log elapsed time

## Default Dialer Role & Restricted Settings (Android 13+)

### DO (Default Dialer)
1. **DO** request dialer role on every app open to handle Restricted Settings failures
2. **DO** use `RoleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)` for Android 10+
3. **DO** add fallback manual settings dialog when RoleManager fails (Android 13+ Restricted Settings)
4. **DO** include DIAL intent filters for both `tel:` and empty schemes in MainActivity
5. **DO** add `android.app.dialer.default=true` metadata in MainActivity
6. **DO** add `android.telecom.IN_CALL_SERVICE_UI` and `android.telecom.IN_CALL_SERVICE_RINGING` meta-data in InCallService
7. **DO** handle `onActivityResult()` to detect when user accepts/rejects dialer role

### DON'T (Default Dialer)
1. **DON'T** skip manual fallback when RoleManager fails on Moto/Redmi devices
2. **DON'T** assume programmatic dialer role request works on Android 13+ (Restricted Settings blocks it)
3. **DON'T** stop CallReceiver fallback - it works without dialer role
4. **DON'T** use `Settings.ACTION_DEFAULT_APPS_SETTINGS` - it's not a public API (use ACTION_APPLICATION_DETAILS_SETTINGS fallback)

## Voice Call Source & Error Handling Fixes

### Fix 0: High-Priority Speakerphone Bridge (Speaker Echo Loop)
**Problem**: MediaProjection is denied and AudioRecord returns -2 (ERROR_BAD_VALUE). Phone app blocks microphone during calls.
**Solution**: Route call audio to SPEAKER - the physical microphone can then "hear" the speaker output (acoustic echo loop)

### Fix 1: MediaProjection Foreground Service Lifecycle (Android 14+)
**Problem**: SecurityException for MEDIA_PROJECTION - foreground service not started in correct order
**Solution**: Start service FIRST, then pass MediaProjection data

```java
// MainActivity.onActivityResult - CRITICAL ORDER
if (resultCode == RESULT_OK && data != null) {
    // Step 1: Start foreground service FIRST (satisfies Android 14 eligible state)
    startForegroundService(serviceIntent);
    
    // Step 2: Then broadcast MediaProjection data
    broadcastMediaProjectionData(data);
}

// ScamMonitorService.onStartCommand - startForeground within 5 seconds
long startTime = System.currentTimeMillis();
startForegroundWithNotification();
long elapsed = System.currentTimeMillis() - startTime;
Log.i(TAG, "⏱️ startForeground in " + elapsed + "ms");

// AndroidManifest.xml - add mediaProjection to foregroundServiceType
android:foregroundServiceType="mediaProjection|microphone|phoneCall"
```

### Fix 2: Force Voice Call Source (VOICE_CALL = 4)
**Problem**: SpeechRecognizer defaults to Standard Mic (1) which is blocked during active calls
**Solution**: Use `intent.putExtra("android.speech.extra.AUDIO_SOURCE", 4)` (VOICE_CALL)

```java
// In GoogleSpeechRecognizer.setupRecognizerIntent()
try {
    recognizerIntent.putExtra("android.speech.extra.AUDIO_SOURCE", 4); // VOICE_CALL = 4
    Log.i(TAG, "🔊 Audio source set to VOICE_CALL (4) - accessing Telecom stream");
} catch (Exception e) {
    Log.w(TAG, "Could not set VOICE_CALL audio source: " + e.getMessage());
}
```

### Fix 2: Handle Security Bypass (Wait for VOICE_CALL stream)
**Problem**: App shows "Security Bypass Active: Listening via Standard Mic" when Standard Mic is silent
**Solution**: Remove fallback message - wait for VOICE_CALL stream to become available

```java
// In MainActivity START PROTECTION button click
// REMOVED: appendLog("🛡️ Security Bypass Active: Listening via Standard Mic");
// ADDED:
appendLog("🛡️ Protection Active: Waiting for call audio stream...");
```

### Fix 3: On-Device Recognition (EXTRA_PREFER_OFFLINE)
**Problem**: Google cloud recognizer fails during VoLTE/Wi-Fi calling (data connection used by call)
**Solution**: Use `EXTRA_PREFER_OFFLINE = true` for on-device model (already in code)

```java
// In GoogleSpeechRecognizer.setupRecognizerIntent()
recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
```

### Fix 4: Error 7/9 Handling (Speakerphone Toggle)
**Problem**: No Match (7) or Permissions (9) errors leave audio stream stuck
**Solution**: Toggle speakerphone to force audio refresh

```java
// In GoogleSpeechRecognizer.onError()
if (errorCode == SpeechRecognizer.ERROR_NO_MATCH 
        || errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
    Log.w(TAG, "🔄 Error [" + errorCode + "] - triggering speakerphone toggle");
    if (listener != null) {
        listener.onAudioRefreshNeeded();
    }
}

// In ScamMonitorService - implement onAudioRefreshNeeded()
@Override
public void onAudioRefreshNeeded() {
    AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    audioManager.setSpeakerphoneOn(true);
    handler.postDelayed(() -> audioManager.setSpeakerphoneOn(false), 500);
}
```

### DO (Voice Call Source)
1. **DO** use `android.speech.extra.AUDIO_SOURCE` with value 4 (VOICE_CALL) for call audio
2. **DO** set `EXTRA_PREFER_OFFLINE = true` for on-device recognition stability
3. **DO** implement `onAudioRefreshNeeded()` in ScamMonitorService to toggle speaker
4. **DO** wait for VOICE_CALL stream instead of falling back to Standard Mic silently
5. **DO** use 5-second retry delay for Error 9 (INSUFFICIENT_PERMISSIONS)
6. **DO** force speakerphone ON in InCallService when call is ACTIVE - enables acoustic echo loop
7. **DO** disable AcousticEchoCanceler, NoiseSuppressor, and AutomaticGainControl on AudioRecord
8. **DO** add MIC as final fallback - works with speakerphone echo loop
9. **DO** turn OFF speakerphone when call ends (cleanup)

### DON'T (Voice Call Source)
1. **DON'T** use Standard Mic (1) during active calls - it's blocked by Phone app
2. **DON'T** claim "Security Bypass" when using Standard Mic (it's not bypassing anything)
3. **DON'T** auto-restart immediately on Error 9 - it needs longer delay (5 seconds)
4. **DON'T** forget to add VOICE_CALL intent in AndroidManifest.xml queries
5. **DON'T** assume VOICE_CALL works without Default Dialer role
6. **DON'T** leave speakerphone ON after call ends - always cleanup
7. **DON'T** skip disabling AEC/NS/AGC - they filter out speaker as "noise"

## InCallService & InCallActivity Implementation

### DO (InCallService)
1. **DO** register Call.Callback to track state changes (onStateChanged)
2. **DO** launch InCallActivity when call is added (onCallAdded)
3. **DO** update InCallActivity on state changes (RINGING → ACTIVE → DISCONNECTED)
4. **DO** start ScamMonitorService only when call becomes ACTIVE (not on RINGING)
5. **DO** stop ScamMonitorService when call is DISCONNECTED
6. **DO** use Call.answer(), Call.reject(), and Call.disconnect() for call control
7. **DO** add LocalBinder to ScamMonitorService for InCallActivity binding
8. **DO** set AudioManager.MODE_IN_COMMUNICATION on STATE_ACTIVE for speech recognition
9. **DO** use BroadcastReceiver to handle answer/disconnect from InCallActivity
10. **DO** bind to ScamMonitorService in onCallAdded to create security bridge

### DON'T (InCallService)
1. **DON'T** forget to unregister Call.Callback in onDestroy()
2. **DON'T** use Call.STATE_REJECTED or STATE_PULLING (not available on all API levels)
3. **DON'T** launch multiple InCallActivity instances (use FLAG_ACTIVITY_CLEAR_TOP)
4. **DON'T** start speech recognition on RINGING - wait for ACTIVE state
5. **DON'T** forget to set audio mode before starting speech recognition
6. **DON'T** skip service binding - Android treats InCallService and MonitorService as separate apps

### Call Lifecycle Sync (Ghost Call Fix)

**Problem**: Ghost calls where hang-up button does nothing and UI doesn't close when other person hangs up.

**Solution**: Implement static Call reference + Call.Callback listener + broadcast on DISCONNECTED

```java
// In ScamShieldInCallService.java - STATIC reference for InCallActivity access
public static Call currentCall = null;
public static Call currentCallInstance = null;

// In onCallAdded - set static reference
currentCallInstance = call;
currentCall = call;

// In handleCallStateChange - on DISCONNECTED:
case Call.STATE_DISCONNECTED:
    // Clear static reference
    currentCallInstance = null;
    currentCall = null;
    // Reset audio mode
    audioManager.setMode(AudioManager.MODE_NORMAL);
    audioManager.setSpeakerphoneOn(false);
    // Broadcast DISCONNECTED to close InCallActivity
    broadcastCallDisconnected();
    break;

// Broadcast method
private void broadcastCallDisconnected() {
    Intent intent = new Intent(ACTION_CALL_DISCONNECTED);
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
}

// In InCallActivity.java - use static reference
private void endCall() {
    Call activeCall = ScamShieldInCallService.currentCall;
    if (activeCall != null) {
        activeCall.disconnect();
    }
    finish();
}

// Broadcast receiver handles CALL_DISCONNECTED
filter.addAction(ScamShieldInCallService.ACTION_CALL_DISCONNECTED);
if (ScamShieldInCallService.ACTION_CALL_DISCONNECTED.equals(action)) {
    finish();
}
```

### DO (Call Lifecycle Sync)
1. **DO** create static `public static Call currentCall` in ScamShieldInCallService
2. **DO** set static reference in onCallAdded
3. **DO** implement Call.Callback with onStateChanged
4. **DO** broadcast ACTION_CALL_DISCONNECTED on STATE_DISCONNECTED
5. **DO** use static reference in InCallActivity.endCall()
6. **DO** register broadcast receiver for ACTION_CALL_DISCONNECTED

### DON'T (Call Lifecycle Sync)
1. **DON'T** use local Call variable in InCallActivity (it's never set)
2. **DON'T** skip clearing static reference on DISCONNECTED (causes reuse bugs)
3. **DON'T** forget to reset audio mode on call end (next call has wrong mode)

### Call Control & Button Fixes

**Problem**: Double-click hang-up, hardware toggles not working, recording not stopping on call end.

**Solution**: Dynamic button listeners using static reference + MediaRecorder lifecycle

```java
// In InCallActivity - Dynamic Speaker Toggle
private void toggleSpeaker() {
    AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (audioManager == null) return;
    
    // CRITICAL: Read current state from system
    boolean currentlyOn = audioManager.isSpeakerphoneOn();
    
    if (currentlyOn) {
        audioManager.setSpeakerphoneOn(false);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        // Update UI icons
    } else {
        audioManager.setSpeakerphoneOn(true);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }
}

// Hold with static reference - no popup checks
private void toggleHold() {
    Call activeCall = ScamShieldInCallService.currentCall;
    if (activeCall == null) return;
    
    isOnHold = !isOnHold;
    if (isOnHold) activeCall.hold();
    else activeCall.unhold();
}

// Recording with MediaRecorder lifecycle
private boolean isRecording = false;
private MediaRecorder mediaRecorder;

private void toggleRecord() {
    if (isRecording) {
        // Stop and save
        mediaRecorder.stop();
        mediaRecorder.release();
    } else {
        // Start recording
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
        mediaRecorder.prepare();
        mediaRecorder.start();
    }
    isRecording = !isRecording;
}

// In ScamShieldInCallService - Auto-record check
public void onCallAdded(Call call) {
    SharedPreferences prefs = getSharedPreferences("ScamShieldPrefs", MODE_PRIVATE);
    if (prefs.getBoolean("auto_record", false)) {
        Intent recordIntent = new Intent("com.shreyanshi.scamshield.ACTION_START_RECORDING");
        LocalBroadcastManager.getInstance(this).sendBroadcast(recordIntent);
    }
}

// In ScamShieldInCallService - Auto-stop on call removed
public void onCallRemoved(Call call) {
    Intent stopRecordIntent = new Intent("com.shreyanshi.scamshield.ACTION_STOP_RECORDING");
    LocalBroadcastManager.getInstance(this).sendBroadcast(stopRecordIntent);
}
```

### DO (Call Control)
1. **DO** use AudioManager.isSpeakerphoneOn() for dynamic state detection
2. **DO** use static ScamShieldInCallService.currentCall in all button listeners
3. **DO** implement stopRecording() for auto-stop on call end
4. **DO** broadcast ACTION_START_RECORDING/ACTION_STOP_RECORDING from InCallService
5. **DO** add auto_record preference in StorageManager

### DON'T (Call Control)
1. **DON'T** check callState != STATE_ACTIVE in button listeners (use null check)
2. **DON'T** show Toast popup for invalid state (log only)
3. **DON'T** leave MediaRecorder running when call ends (always call stop())

### Direct Hardware Fixes

**Problem**: Wrong numbers, buttons stuck, hang-up doesn't work.

**Solution**: Direct hardware reads without boolean flags

```java
// Speaker - Direct hardware read
private void toggleSpeaker() {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    boolean on = am.isSpeakerphoneOn();
    am.setSpeakerphoneOn(!on);
    am.setMode(AudioManager.MODE_IN_COMMUNICATION);
    // Update UI based on actual state
}

// Hold - Direct Telecom state
private void toggleHold() {
    Call call = ScamShieldInCallService.currentCall;
    if (call == null) return;
    if (call.getState() == Call.STATE_HOLDING) {
        call.unhold();
    } else {
        call.hold();
    }
}

// Hang-up - Immediate finish
private void endCall() {
    Call call = ScamShieldInCallService.currentCall;
    if (call != null) call.disconnect();
    finish(); // IMMEDIATE - no waiting
}

// Recording - External files dir
private void toggleRecord() {
    File dir = getExternalFilesDir(null);
    // Use VOICE_COMMUNICATION
}
```

### DO (Direct Hardware)
1. **DO** use isSpeakerphoneOn() for UI state
2. **DO** use call.getState() == STATE_HOLDING for hold
3. **DO** call disconnect() then finish() immediately
4. **DO** use getExternalFilesDir() for recordings
5. **DO** use AudioSource.VOICE_COMMUNICATION

### STEP 1-4 Implementation Summary

**STEP 1: Call Lifecycle & Crash Fix**
- Added ACTION_FINISH_UI broadcast in Call.Callback on STATE_DISCONNECTED
- InCallActivity registers receiver for ACTION_FINISH_UI and calls finishAndRemoveTask()

**STEP 2: Hold & UI Persistency**
- Hold button uses call.getState() == STATE_HOLDING for direct Telecom state
- onBackPressed() uses moveTaskToBack(true) to keep call active
- Ongoing notification with PendingIntent to re-open UI

**STEP 3: Button Visibility & Audio**
- Answer button hidden when STATE_ACTIVE
- Uses audioManager.setMode(MODE_IN_COMMUNICATION) at call start

### UI State Transition Fix (Answer/Decline Buttons Stay Visible)

**Problem**: Answer and Decline buttons don't disappear after call is answered - UI stays in RINGING state

**Solution**: 
1. ScamShieldInCallService broadcasts ACTION_CALL_ACTIVE when state hits STATE_ACTIVE
2. InCallActivity listens for ACTION_CALL_ACTIVE and calls updateUIForCallState()
3. updateUIForCallState() hides btnAnswer, shows bottomSection (Mute/Speaker/End buttons), sets audio mode

```java
// In InCallActivity - BroadcastReceiver handles ACTION_CALL_ACTIVE
else if (ScamShieldInCallService.ACTION_CALL_ACTIVE.equals(action)) {
    callState = STATE_ACTIVE;
    updateUIForCallState();
}

// In InCallActivity - updateUIForCallState() handles visibility
case STATE_ACTIVE:
    btnAnswer.setVisibility(View.GONE);
    if (bottomSection != null) bottomSection.setVisibility(View.VISIBLE);
    // Audio sync
    AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    break;
```

**DO:**
1. **DO** broadcast ACTION_CALL_ACTIVE from InCallService when STATE_ACTIVE
2. **DO** register receiver for ACTION_CALL_ACTIVE in InCallActivity.onResume()
3. **DO** hide btnAnswer in STATE_ACTIVE case
4. **DO** show bottomSection (call controls) in STATE_ACTIVE case
5. **DO** call audioManager.setMode(MODE_IN_COMMUNICATION) when transitioning to ACTIVE

**DON'T:**
1. **DON'T** assume UI auto-updates when call state changes (must manually update)
2. **DON'T** skip audio mode sync (voice path won't open without it)

### Phase 1: Real-Time Button Swapping + Phase 2: Instant Close

**Problem**: 
- Green/Red buttons stay visible after call is answered
- Activity requires TWO CLICKS to close after call ends

**Solution**: PHASE 1 (Button Swapping) + PHASE 2 (Instant Close)

```java
// In InCallActivity - updateUIForCallState()

case STATE_RINGING:
    btnAnswer.setVisibility(View.VISIBLE);
    btnEndCall.setVisibility(View.VISIBLE);
    if (bottomSection != null) bottomSection.setVisibility(View.GONE); // Hide Mute/Speaker during ringing
    break;

case STATE_ACTIVE:
    btnAnswer.setVisibility(View.GONE);
    btnEndCall.setVisibility(View.VISIBLE);
    if (bottomSection != null) bottomSection.setVisibility(View.VISIBLE); // Show Mute/Speaker
    // Audio mode sync
    AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    break;

case STATE_DISCONNECTED:
    btnAnswer.setVisibility(View.GONE);
    btnEndCall.setVisibility(View.GONE);
    if (bottomSection != null) bottomSection.setVisibility(View.GONE);
    if (callButtonRow != null) callButtonRow.setVisibility(View.GONE);
    // PHASE 2: Instant Close - finish immediately
    finishAndRemoveTask();
    break;
```

**In endCall() method:**
```java
private void endCall() {
    AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (audioManager != null) {
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setSpeakerphoneOn(false);
    }
    
    // Use static activeCallInstance - FIRST CLICK HANGUP
    if (ScamShieldInCallService.activeCallInstance != null) {
        try {
            ScamShieldInCallService.activeCallInstance.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Disconnect error: " + e.getMessage());
        }
    }
    
    // CRITICAL: Immediately finish - NO CONDITIONS
    finishAndRemoveTask();
}
```

**STEP 4: Recording**
- Uses getExternalFilesDir("Recordings").getAbsolutePath()
- Uses AudioSource.VOICE_COMMUNICATION
- Try-catch around recorder.stop() to prevent crashes

### Service Binding & Foreground Type Fixes

### Fix 1: Service Binding (Security Bridge)
**Problem**: InCallService (with Phone Role) and ScamMonitorService (with Mic permission) are treated as separate apps by Android
**Solution**: Bind InCallService to ScamMonitorService in onCallAdded to create a "security bridge"

```java
// In ScamShieldInCallService.java
private final ServiceConnection serviceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ScamMonitorService.LocalBinder binder = (ScamMonitorService.LocalBinder) service;
        monitorService = binder.getService();
        isServiceBound = true;
    }
    
    @Override
    public void onServiceDisconnected(ComponentName name) {
        monitorService = null;
        isServiceBound = false;
    }
};

// In startScamMonitorServiceForCall()
if (!isServiceBound) {
    bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
}
```

### Fix 2: Foreground Service Type
**Problem**: Need all three types for audio interception during live calls
**Solution**: Use `android:foregroundServiceType="microphone|phoneCall|specialUse"`

```xml
<!-- AndroidManifest.xml -->
<service
    android:name="com.shreyanshi.scamshield.services.ScamMonitorService"
    android:foregroundServiceType="microphone|phoneCall|specialUse">
</service>
```

### Fix 3: Audio Manager Mode
**Problem**: Audio stream not ready when startListening() is called
**Solution**: Set MODE_IN_COMMUNICATION and unmute microphone RIGHT BEFORE startListening

```java
// In ScamMonitorService.startSpeechRecognitionNow()
AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
audioManager.setMicrophoneMute(false);
// THEN call googleSpeechRecognizer.start()
```

### DO (Service Binding)
1. **DO** bind InCallService to ScamMonitorService in onCallAdded
2. **DO** use all three foreground types: microphone|phoneCall|specialUse
3. **DO** set MODE_IN_COMMUNICATION and unmute before every startListening call
4. **DO** ensure LocalBinder exists in ScamMonitorService for binding

### DON'T (Service Binding)
1. **DON'T** start service without binding - treated as separate apps
2. **DON'T** skip phoneCall in foreground type - required for call audio
3. **DON'T** set audio mode after calling startListening - must be before

## Diagnostic Features for Call Audio Debugging

### Audio Level Logger (RMS dB)
**Problem**: SpeechRecognizer shows "Listening" but no text generated
**Solution**: Implement onRmsChanged() to log microphone levels

```java
// In GoogleSpeechRecognizer.onRmsChanged()
private long lastRmsLogTime = 0;

@Override
public void onRmsChanged(float rmsdB) {
    long currentTime = System.currentTimeMillis();
    
    // Throttle logging (500ms minimum)
    if (currentTime - lastRmsLogTime < 500) return;
    lastRmsLogTime = currentTime;
    
    // Log format: [DEBUG] Mic Volume: -XX.X dB
    Log.d(TAG, "[DEBUG] Mic Volume: " + rmsdB + " dB");
    
    // Send to debug terminal
    if (listener != null) {
        listener.onDebugLog("[DEBUG] Mic Volume: " + rmsdB + " dB");
    }
    
    // Detect muted/blocked mic
    if (rmsdB <= 0 || rmsdB < -100) {
        Log.w(TAG, "⚠️ MIC APPEARS MUTED/BLOCKED - RMS = " + rmsdB);
    }
}
```

### Raw Error Capture
**Problem**: Need to know exact error codes during debugging
**Solution**: Log human-readable error strings in onError()

```java
// In GoogleSpeechRecognizer.onError()
String errorMessage = getErrorString(errorCode);
Log.e(TAG, "❌ Speech error: [" + errorCode + "] " + errorMessage);

// Human-readable mapping
private String getErrorString(int error) {
    switch (error) {
        case 1: return "Audio recording error";
        case 2: return "Client error";
        case 3: return "Insufficient permissions";
        case 4: return "Network error";
        case 5: return "Network timeout";
        case 6: return "No speech input detected";
        case 7: return "Recognizer busy";
        case 8: return "Server error";
        default: return "Unknown error";
    }
}
```

### Experimental Audio Source Toggle (UNPROCESSED)
**Problem**: Android noise cancellation filters out caller's voice during calls
**Solution**: Try UNPROCESSED (9) as fallback to bypass audio processing

```java
// In setupRecognizerIntent()
try {
    // UNPROCESSED bypasses all audio processing for raw mic data
    recognizerIntent.putExtra("android.speech.extra.AUDIO_SOURCE", 9);
    Log.i(TAG, "🔊 Using UNPROCESSED audio source");
} catch (Exception e) {
    Log.w(TAG, "UNPROCESSED not available: " + e.getMessage());
}
```

### Service Type Verification
**Problem**: Verify manifest foregroundServiceType is correctly picked up
**Solution**: Check AndroidManifest.xml has all three types

```xml
<service
    android:name="com.shreyanshi.scamshield.services.ScamMonitorService"
    android:foregroundServiceType="microphone|phoneCall|specialUse">
</service>
```

### DO (Diagnostics)
1. **DO** implement onRmsChanged() to see if mic is receiving sound
2. **DO** log all error codes with human-readable strings
3. **DO** try UNPROCESSED as fallback if VOICE_CALL fails
4. **DO** verify foregroundServiceType has all three: microphone|phoneCall|specialUse

### DON'T (Diagnostics)
1. **DON'T** skip onRmsChanged() - critical for debugging mic issues
2. **DON'T** assume mic is working just because recognizer says "listening"
3. **DON'T** assume audio source is correct without checking RMS levels
4. **DON'T** ignore low/zero RMS values - indicates muted/blocked mic

## Manual AudioCapture - Raw Audio Buffer Approach

### Problem
SpeechRecognizer Intent-based capture is blocked during active calls. Android system blocks high-level APIs from accessing microphone while Phone app holds it.

### Solution
Use AudioRecord directly with VOICE_COMMUNICATION source to capture raw PCM audio during calls.

```java
// In ScamMonitorService.java - AudioRecord Thread
private static final int SAMPLE_RATE = 16000;
private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

private void startAudioCaptureWithVoiceCommunication() {
    int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // Use VOICE_COMMUNICATION (9) - requires Default Dialer role
    int audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
    
    audioRecord = new AudioRecord(
        audioSource,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        bufferSize * 2
    );
    
    // Start capture in background thread
    audioCaptureThread = new HandlerThread("AudioCaptureThread", Process.THREAD_PRIORITY_AUDIO);
    audioCaptureThread.start();
}

private void captureAudioLoop(int bufferSize) {
    try {
        // WARM-UP LOOP: Wait 500ms for hardware to initialize
        Thread.sleep(500);
        
        // Check if AudioRecord is still initialized
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            // Re-initialize if needed
            audioRecord = new AudioRecord(audioSource, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        }
        
        // BUFFER FLUSH: Wait 200ms for hardware to route audio
        audioRecord.startRecording();
        Thread.sleep(200);
        
        // Now start reading
        while (isAudioCaptureRunning) {
            int readResult = audioRecord.read(buffer, 0, bufferSize);
            // Process buffer...
        }
    } catch (InterruptedException e) {
        // Thread interrupted
    }
}

private void captureAudioLoop(int bufferSize) {
    byte[] buffer = new byte[bufferSize];
    audioRecord.startRecording();
    
    while (isAudioCaptureRunning) {
        int readResult = audioRecord.read(buffer, 0, bufferSize);
        
        if (readResult > 0) {
            // Calculate RMS for volume level
            float rms = calculateRms(buffer, readResult);
            Log.d(TAG, "🎤 RAW Audio: " + rms + " dB");
        }
    }
}

private float calculateRms(byte[] buffer, int readSize) {
    double sum = 0;
    for (int i = 0; i < readSize; i++) {
        short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
        sum += sample * sample;
    }
    double rms = Math.sqrt(sum / readSize);
    return (float) (20 * Math.log10(rms / 32768.0));
}
```

### Audio Mode Force
In InCallService, ensure audio mode is set BEFORE AudioRecord starts:

```java
// In ScamShieldInCallService - STATE_ACTIVE
audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
```

### DO (Manual AudioCapture)
1. **DO** use VOICE_COMMUNICATION source (9) - bypasses Phone app audio lock
2. **DO** use HandlerThread with THREAD_PRIORITY_AUDIO - prevents system from dropping buffers
3. **DO** use larger buffer size (bufferSize * 4) - prevents index errors on modern phones
4. **DO** wrap read() in SecurityException try-catch - catches Android 14 permission issues
5. **DO** log RMS levels to verify mic is receiving data
6. **DO** fallback to VOICE_RECOGNITION if VOICE_COMMUNICATION fails
7. **DO** use 16000 Hz sample rate (standard for speech recognition)
8. **DO** add PROPERTY_SPECIAL_USE_FGS_SUBTYPE with "scam_detection" value in manifest
9. **DO** set telecom parameters: "recording_mode=on" and "scam_detection_active=true"
10. **DO** use ByteBuffer with READ_BLOCKING for Android 14 stability
11. **DO** disable AcousticEchoCanceler, NoiseSuppressor, AutomaticGainControl to allow speaker capture
12. **DO** add MIC as final fallback - works with speakerphone echo loop trick
13. **DO** turn OFF speakerphone when call ends

### DON'T (Manual AudioCapture)
1. **DON'T** use MIC source during calls without speaker - mic will be blocked
2. **DON'T** use small buffer sizes - causes immediate index errors on modern high-end phones
3. **DON'T** skip thread priority - system drops buffers on regular threads
4. **DON'T** start AudioRecord without setting MODE_IN_COMMUNICATION first
5. **DON'T** forget to stop AudioRecord when call ends
6. **DON'T** assume AudioRecord will work without Default Dialer role
7. **DON'T** skip Android 14 property - required for foreground service type
8. **DON'T** skip telecom parameters - OEM skins like OneUI need explicit unlocking
9. **DON'T** skip disabling AEC/NS/AGC - they filter out speaker as "noise"

### InCallActivity UI Features
- Center-aligned contact name with circular avatar/initial
- Contact name lookup via ContactsContract.PhoneLookup
- Mute button (toggles microphone on/off)
- Speaker toggle (switches between earpiece and speaker)
- Automatic finish() on STATE_DISCONNECTED
- Timer showing call duration

### Audio Routing & Microphone Sync
- InCallService sets MODE_IN_COMMUNICATION when call becomes ACTIVE
- ScamMonitorService checks EXTRA_CALL_ACTIVE flag before starting speech recognition
- SpeechRecognizer only starts after audio route is properly established

### Immediate Start on Call Active
**Problem**: Audio focus gained after call ends, speech recognition starts too late

**Solution**:
1. InCallService sends ACTION_CALL_ACTIVE broadcast IMMEDIATELY when state hits STATE_ACTIVE
2. ScamMonitorService requests AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE on receive
3. SpeechRecognizer starts immediately after focus granted
4. AUDIOFOCUS_LOSS_TRANSIENT handled - doesn't auto-restart (prevents spam during connecting)

```java
// InCallService - send immediate broadcast
private void sendImmediateStartBroadcast(String phoneNumber) {
    Intent intent = new Intent(ACTION_CALL_ACTIVE);
    intent.putExtra(EXTRA_PHONE_NUMBER, phoneNumber);
    sendBroadcast(intent);
}

// In ScamMonitorService - request focus with AudioFocusRequest.Builder (Modern API)
AudioAttributes audioAttributes = new AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build();

AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
    .setAudioAttributes(audioAttributes)
    .setOnAudioFocusChangeListener(audioFocusListener)
    .setAcceptsDelayedFocusGain(true)  // Allow delayed focus for transitions
    .build();

int result = audioManager.requestAudioFocus(focusRequest);
```

### Audio Focus Request with Proper AudioAttributes
**Problem**: Audio focus denied repeatedly during call - focus only granted after call ends

**Root Cause**: Using deprecated `requestAudioFocus(listener, stream, focusChange)` without AudioAttributes

**Solution**:
1. Use `AudioAttributes.Builder()` with `USAGE_VOICE_COMMUNICATION` and `CONTENT_TYPE_SPEECH`
2. Use `AudioFocusRequest.Builder(AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)`
3. Set `setAcceptsDelayedFocusGain(true)` for transitions
4. Use exactly 500ms retry delay when focus is denied

```java
// Full implementation in ScamMonitorService
private void requestAudioFocusAndStartListening(String phoneNumber) {
    AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    
    AudioAttributes audioAttributes = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build();
    
    AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(audioFocusListener)
        .setAcceptsDelayedFocusGain(true)
        .build();
    
    int result = audioManager.requestAudioFocus(focusRequest);
    
    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        startSpeechRecognitionNow();
    } else {
        // Don't retry here - let audioFocusListener handle it
        // The listener will retry when system grants focus
    }
}
```

**Important**: The audio focus listener must handle AUDIOFOCUS_GAIN callback to start speech recognition when focus is later granted:
```java
case AudioManager.AUDIOFOCUS_GAIN:
    audioFocusHeld = true;
    if (isCallActive && googleSpeechRecognizer == null) {
        startSpeechRecognitionNow();  // Start when focus granted later
    }
    break;
```

### InCallService to ScamMonitorService Communication (LocalBroadcastManager)
**Problem**: Using system `sendBroadcast()` for internal app communication can be filtered/blocked by Android

**Solution**: Use `LocalBroadcastManager` for reliable internal communication between app components

**Sender (InCallService)**:
```java
// Import
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// Send broadcast
Intent intent = new Intent(ACTION_CALL_ACTIVE);
intent.putExtra(EXTRA_PHONE_NUMBER, phoneNumber);
intent.putExtra(ScamMonitorService.EXTRA_CALL_ACTIVE, true);
LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
```

**Receiver (ScamMonitorService)**:
```java
// Register in onCreate
IntentFilter filter = new IntentFilter();
filter.addAction(ACTION_CALL_ACTIVE);
filter.addAction(ACTION_STOP);
LocalBroadcastManager.getInstance(this).registerReceiver(callActiveReceiver, filter);

// Unregister in onDestroy
LocalBroadcastManager.getInstance(this).unregisterReceiver(callActiveReceiver);
```

**Manifest**: NO declaration needed - dynamic registration in `onCreate()` is sufficient for LocalBroadcastManager

### Android 14+ BroadcastReceiver Security Fix
**Problem**: java.lang.SecurityException: One of RECEIVER_EXPORTED or RECEIVER_EXPORTED should be specified

**Solution**:
1. For registerReceiver() calls in code, use:
```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
} else {
    registerReceiver(receiver, filter);
}
```
2. For receivers in AndroidManifest, use:
```xml
<receiver android:exported="true" ...>  <!-- for system broadcasts -->
<!-- or -->
<receiver android:exported="false" ...> <!-- for internal app use -->
```

### InCallService Call Visibility Fix
**Problem**: User navigates away from call screen and cannot find the call again

**Solution**:
1. Show persistent notification during STATE_ACTIVE that returns to InCallActivity when tapped
2. Add foregroundServiceType="phoneCall" to InCallService in AndroidManifest
3. Use AudioManager.STREAM_VOICE_CALL for audio focus (allowed for Default Dialer)

```xml
<!-- AndroidManifest.xml -->
<service
    android:name="...ScamShieldInCallService"
    android:foregroundServiceType="phoneCall">
```

```java
// ScamShieldInCallService - show notification
private void showCallNotification(String phoneNumber, boolean isIncoming) {
    Intent intent = InCallActivity.createIntent(this, phoneNumber, STATE_ACTIVE, isIncoming);
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, ...);
    
    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("ScamShield Call")
        .setContentText(isIncoming ? "Incoming: " + number : "Outgoing: " + number)
        .setSmallIcon(R.drawable.ic_notification)
        .setOngoing(true)
        .setContentIntent(pendingIntent)
        .build();
    
    startForeground(NOTIFICATION_ID, notification);
}
```

### Audio Source for Call Detection
**Problem**: Standard MIC is blocked during active phone calls

**Solution**: Use VOICE_CALL audio source (falls back to VOICE_RECOGNITION for better noise filtering)
```java
// In GoogleSpeechRecognizer.setupRecognizerIntent()
// On some devices, VOICE_COMMUNICATION is blocked, but as Default Dialer,
// VOICE_CALL allows hearing both sides of the conversation
// Falls back to VOICE_RECOGNITION (better at filtering background noise), then to VOICE_COMMUNICATION
try {
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, 
        android.media.MediaRecorder.AudioSource.VOICE_CALL);
} catch (Exception e) {
    // Fallback to VOICE_RECOGNITION - better at filtering background noise
    try {
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, 
            android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION);
    } catch (Exception e2) {
        // Fallback to VOICE_COMMUNICATION
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, 
            android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION);
    }
}

// Enable partial results for real-time feedback
recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

// Force LANGUAGE_MODEL_FREE_FORM for natural speech
recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

// Set language to en-IN for Indian English accents
recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
```

### Hardware Sync Delay
**Problem**: Audio session not ready immediately when call becomes ACTIVE

**Solution**: Add 1-second delay after receiving ACTIVE signal
```java
// In ScamMonitorService - BroadcastReceiver
if (callActive) {
    isCallActive = true;
    
    // 1-second delay to give hardware time to switch from 'Ringing' to 'In-Call' mode
    final String delayedPhoneNumber = phoneNumber;
    final int delayedSessionId = audioSessionId;
    
    handler.postDelayed(() -> {
        startListeningForScamsWithSession(delayedPhoneNumber, delayedSessionId);
    }, 1000);  // 1 second delay for hardware sync
}
```

### InCallService Early Audio Focus
**Problem**: Audio focus denied because not claimed early enough

**Solution**: Request audio focus in onCallAdded, before call becomes active
```java
// In ScamShieldInCallService - onCallAdded()
if (audioManager != null) {
    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    Log.i(TAG, "🔊 Audio mode set to MODE_IN_COMMUNICATION in onCallAdded");
}
```

### Mute/Unmute Hack (InCallService)
**Problem**: Audio pipeline locked by system, secondary listeners can't access stream

**Solution**: Programmatically Mute then Unmute to "wake up" audio pipeline
```java
// In ScamShieldInCallService - STATE_ACTIVE
handler.postDelayed(() -> {
    audioManager.setMicrophoneMute(true);
    Log.d(TAG, "🔇 Muted microphone");
    
    handler.postDelayed(() -> {
        audioManager.setMicrophoneMute(false);
        Log.d(TAG, "🔊 Unmuted - audio pipeline should be ready");
    }, 100);
}, 500); // Wait 500ms after call becomes active
```

### Service Binding with BIND_IMPORTANT (MainActivity)
**Problem**: ScamMonitorService doesn't get highest CPU/Audio priority

**Solution**: Bind to service with BIND_IMPORTANT flag
```java
// In MainActivity.startScamMonitorService()
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    bindService(serviceIntent, serviceConnection, Context.BIND_IMPORTANT);
    Log.d(TAG, "🔗 Binding with BIND_IMPORTANT for highest priority");
}
```

### Direct Audio Session Sharing (No-Focus Start)
**Problem**: Audio focus always denied during calls - even with retries

**Solution**: Skip audio focus request entirely when we have valid session ID from InCallService
```java
// In ScamMonitorService - startListeningForScamsWithSession()
public void startListeningForScamsWithSession(String phoneNumber, int audioSessionId) {
    // CRITICAL: If we have a valid session ID, SKIP audio focus request
    if (audioSessionId > 0) {
        Log.i(TAG, "🎵 Using DIRECT audio session ID - skipping audio focus request");
        sendDebugLogBroadcast("🎵 DIRECT START with sessionId=" + audioSessionId);
        
        // Go directly to speech recognition using shared session
        startSpeechRecognitionNow(audioSessionId);
        return;
    }
    
    // Fallback: normal audio focus request
    requestAudioFocusAndStartListeningWithSession(phoneNumber, audioSessionId);
}
```

### InCallService Audio Route Toggle
**Problem**: System holds microphone lock during active calls

**Solution**: Toggle audio route to force Android to refresh audio policy
```java
// In ScamShieldInCallService - STATE_ACTIVE
audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

// Toggle to SPEAKER then back to force audio policy refresh
audioManager.setSpeakerphoneOn(true);   // Force to speaker
audioManager.setSpeakerphoneOn(false);  // Back to earpiece
Log.d(TAG, "🔊 Audio route toggled to force policy refresh");
```

### Speaker Toggle Trick (InCallService)
**Problem**: Audio focus denied repeatedly - system doesn't recognize ScamShield as secondary listener

**Solution**: Toggle speakerphone on/off to force Audio Policy Manager to refresh
```java
// In ScamShieldInCallService - on STATE_ACTIVE
if (audioManager != null) {
    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    audioManager.setSpeakerphoneOn(false); // Default to earpiece
    
    // TRICK: Toggle speakerphone to force Audio Policy Manager to refresh
    audioManager.setSpeakerphoneOn(true);
    audioManager.setSpeakerphoneOn(false);
    Log.i(TAG, "🔊 Speaker toggle trick applied to refresh audio policy");
}
```

### Required Permissions for Audio
**Problem**: Audio focus requests fail without proper permissions

**Solution**: Add MODIFY_AUDIO_SETTINGS to AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

### Audio Session Sharing Between InCallService and ScamMonitorService
**Problem**: Audio focus denied repeatedly - ScamShield treated as separate app, not secondary listener

**Solution**: Share audio session ID between InCallService and ScamMonitorService

**InCallService - Generate and pass session ID**:
```java
// In onStateChanged - STATE_ACTIVE
int audioSessionId = audioManager.generateAudioSessionId();
sendImmediateStartBroadcastWithSession(handle, audioSessionId);

// New method
private void sendImmediateStartBroadcastWithSession(String phoneNumber, int audioSessionId) {
    Intent intent = new Intent(ACTION_CALL_ACTIVE);
    intent.putExtra(EXTRA_PHONE_NUMBER, phoneNumber);
    intent.putExtra(ScamMonitorService.EXTRA_CALL_ACTIVE, true);
    intent.putExtra("audio_session_id", audioSessionId);
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
}
```

**ScamMonitorService - Abandon focus before requesting**:
```java
public void startListeningForScamsWithSession(String phoneNumber, int audioSessionId) {
    // ABANDON FOCUS BEFORE REQUESTING - Reset to let system know priority changed
    abandonAudioFocus();
    
    requestAudioFocusAndStartListeningWithSession(currentNumber, audioSessionId);
    speechRecognitionStarted = true;
}

private void abandonAudioFocus() {
    if (audioFocusHeld) {
        audioManager.abandonAudioFocus(audioFocusListener);
        audioFocusHeld = false;
    }
}
```

**GoogleSpeechRecognizer - Accept audio session ID**:
```java
public GoogleSpeechRecognizer(Context context, SpeechListener listener, int audioSessionId) {
    this.context = context;
    this.listener = listener;
    this.audioSessionId = audioSessionId;
    initializeSpeechRecognizer();
}
```

## Common Issues & Solutions

### Foreground Service Notification Icon (CRITICAL)
**Problem**: BadForegroundServiceNotificationException - "Couldn't create icon StatusBarIcon"

**Root Cause**: Using wrong icon resource (launcher/adaptive icon instead of notification icon)

**Solution**:
1. Use `app/src/main/res/drawable/ic_notification.xml` (pure white monochrome vector)
2. Create using simple paths, white fill (#FFFFFF) only, 24dp size
3. Never use: launcher icons (ic_launcher), adaptive icons, colored icons
4. Verify resource exists before calling setSmallIcon()

**Code Pattern**:
```java
private void startForegroundWithNotification() {
    // 1. Create notification channel FIRST
    createNotificationChannel();
    
    // 2. Validate icon resource exists
    try {
        getResources().getDrawable(R.drawable.ic_notification, null);
    } catch (Exception e) {
        Log.e(TAG, "Icon missing: " + e.getMessage());
        stopSelf();
        return;
    }
    
    // 3. Build notification with ic_notification.xml
    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)  // Must be monochrome vector
        .setContentTitle("ScamShield Active")
        .setOngoing(true)
        .build();
    
    // 4. Call startForeground immediately (within 5 seconds)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    } else {
        startForeground(NOTIFICATION_ID, notification);
    }
}
```

**Icon Format** (ic_notification.xml):
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"  <!-- MUST be white, no colors -->
        android:pathData="M..."/>     <!-- Simple path, no gradients -->
</vector>
```

### Vosk Model Missing
- Ensure model is in `app/src/main/assets/vosk-model/`
- Check that all model files are present (am/, graph/, ivector/, conf/)
- Model should auto-unpack on first run to `context.getFilesDir()`
- Model path key: `vosk-model`

### Call Recording (India-Specific)
- On Android 10+, call audio is restricted
- App CANNOT record call audio directly without being default dialer
- Use device's built-in call recording if available
- For now, use microphone to detect scam keywords in real-time only

### Alert Display
- Use `ScamAlertActivity` instead of overlay
- Activity shows on lock screen using `showOnLockScreen` attribute
- Uses `FLAG_TURN_SCREEN_ON` and `FLAG_SHOW_WHEN_LOCKED`

### Making App a Dialer
- Add `android.intent.action.DIAL` intent filter to MainActivity
- Add `meta-data` with `android.app.dialer.default`
- This helps with call history and default dialer features

## Google On-Device Speech Recognition Best Practices

### Overview
Google On-Device Speech Recognition uses Android's native `SpeechRecognizer` with on-device models. Unlike Vosk, it requires **no separate model bundle** (reducing APK size by 67+ MB) and provides **instant initialization** with automatic error recovery.

### Architecture Pattern

**GoogleSpeechRecognizer** class implements `SpeechProcessor` interface:

```java
public class GoogleSpeechRecognizer implements SpeechProcessor, RecognitionListener {
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private SpeechListener listener;
    private Handler handler;
    private boolean isListening = false;
    
    // Constructor: Initialize immediately (no async loading)
    public GoogleSpeechRecognizer(Context context, SpeechListener listener) {
        this.listener = listener;
        initializeSpeechRecognizer();
    }
    
    // Check availability before creating
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        Log.e(TAG, "Google Speech not available");
        return;
    }
    
    // Create on-device recognizer (no cloud dependency)
    speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
    
    // Configure intent with offline preference
    recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
}
```

### Key Methods

1. **`start()`** - Begin listening immediately
   - No waiting for model loading (unlike Vosk)
   - Instant audio capture

2. **`stop()`** - Stop listening and cleanup

3. **`isRunning()`** - Check if currently listening

4. **`onPartialResults()`** - Handle intermediate speech results
   - Called continuously during speech
   - Pass to `listener.onSpeechRecognized(text)`

5. **`onResults()`** - Handle final speech results
   - Auto-restart listening for continuous monitoring
   - Call `autoRestartListening()` to restart after timeout

6. **`onError()`** - Handle recognition errors
   - Implement graceful error recovery
   - Auto-restart on transient errors (network timeout, no match, audio errors)
   - Log detailed error messages

### Integration in ScamMonitorService

```java
private GoogleSpeechRecognizer googleSpeechRecognizer;

private void initializeSpeechRecognition() {
    // Check permission first
    boolean hasRecordAudio = ContextCompat.checkSelfPermission(this, 
        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    if (!hasRecordAudio) {
        Log.e(TAG, "RECORD_AUDIO permission missing");
        return;
    }
    
    // Initialize Google Speech (instant - no async loading)
    googleSpeechRecognizer = new GoogleSpeechRecognizer(this, this);
    googleSpeechRecognizer.start();
    Log.i(TAG, "Google Speech initialized and listening");
}

@Override
public void onSpeechRecognized(String text) {
    // Normalize and check keywords
    String normalized = text.toLowerCase().trim();
    
    for (String keyword : SCAM_KEYWORDS) {
        if (normalized.contains(keyword)) {
            Log.i(TAG, "SCAM KEYWORD DETECTED: " + keyword);
            triggerAlert(keyword);
            return;
        }
    }
}

private void stopMonitoring() {
    if (googleSpeechRecognizer != null) {
        googleSpeechRecognizer.stop();
        googleSpeechRecognizer.destroy();
        googleSpeechRecognizer = null;
    }
}
```

### AndroidManifest.xml Configuration

```xml
<!-- For Android 11+ package visibility -->
<queries>
    <intent>
        <action android:name="android.intent.action.RECOGNIZE_SPEECH" />
    </intent>
</queries>

<!-- Service with microphone foreground service type -->
<service
    android:name="com.shreyanshi.scamshield.services.ScamMonitorService"
    android:foregroundServiceType="microphone|specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Real-time scam detection and alerting during calls" />
</service>
```

### Key Rules

1. **DO** check `SpeechRecognizer.isRecognitionAvailable()` before using
2. **DO** set `EXTRA_PREFER_OFFLINE = true` for on-device operation
3. **DO** implement auto-restart in `onResults()` and `onError()` for continuous monitoring
4. **DO** handle all error codes gracefully with logging
5. **DO** cleanup resources in `destroy()` method
6. **DON'T** assume Google Speech is always available
7. **DON'T** leave recognizer listening indefinitely without timeout
8. **DON'T** ignore `onError()` callbacks - they're critical for recovery
9. **DON'T** process audio without RECORD_AUDIO permission check first
10. **DON'T** start multiple SpeechRecognizer instances simultaneously

### Benefits Over Vosk

| Feature | Vosk | Google On-Device |
|---------|------|------------------|
| **Model Size** | 67.61 MB | 0 MB (built-in) |
| **APK Size** | +67 MB | No increase |
| **Initialization Time** | 2-10 seconds (async) | Instant |
| **Setup Complexity** | Complex polling, callbacks | Simple, direct |
| **Error Handling** | Manual fallback to Google | Auto-restart with logging |
| **Availability** | Requires model files | Built into Android |
| **Accuracy** | Good (Kaldi engine) | Excellent (Google Neural) |
| **Updates** | Manual app release | Via Google Play Services |

### Error Handling Pattern

```java
@Override
public void onError(int errorCode) {
    String errorMsg = getErrorString(errorCode);
    Log.e(TAG, "Speech error: [" + errorCode + "] " + errorMsg);
    
    isListening = false;
    
    // Transient errors: auto-restart
    if (isTransientError(errorCode)) {
        Log.i(TAG, "Transient error, auto-restarting...");
        autoRestartListening();  // Restart after 1 second
    } else {
        // Permanent errors: retry after longer delay
        Log.w(TAG, "Permanent error, retrying after 3 seconds...");
        handler.postDelayed(this::autoRestartListening, 3000);
    }
}

private boolean isTransientError(int errorCode) {
    return errorCode == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
        || errorCode == SpeechRecognizer.ERROR_AUDIO
        || errorCode == SpeechRecognizer.ERROR_NO_MATCH
        || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY;
}
```

## Runtime Recording & Keyword Detection Architecture

### Critical Issue: Silent Failure - Root Causes Identified

When ScamShield "silently fails" (app runs but doesn't detect scam words), the root causes are typically:

1. **Race Condition in Model Loading** - Vosk model unpacking is asynchronous, taking 2-5 seconds. Service timeout of 5 seconds was TOO AGGRESSIVE. Solution: Increased to 10 seconds, matching VoskProcessor's internal timeout.

2. **Dual-Start Vulnerability** - Both Vosk and Google Speech could start simultaneously if timing was wrong. Solution: Added `usingVosk` flag check in `setupGoogleSpeech()` to prevent dual-start.

3. **Handler Tasks Not Cleaned** - Polling task continues after service destroyed, causing NPE. Solution: Store `pollingTask` reference and cancel in `onDestroy()`.

4. **JSON Parsing Silent Failure** - Vosk returns `{"partial":""}` (empty), but code processed it silently. Solution: Added null checks and detailed logging in `processHypothesis()`.

5. **Permission Check Missing** - Service didn't verify RECORD_AUDIO at runtime. Solution: Added explicit permission check with error logging.

6. **Keyword Search Too Slow** - Linear search through 62 keywords (O(62n)). Solution: Changed from List to HashSet for O(1) lookup.

### Audio Pipeline Flow (Complete)

```
┌─────────────────────────────────────────────────────────────┐
│ INCOMING CALL DETECTED (CallReceiver.onReceive)            │
│ ├─ RINGING or OFFHOOK state detected                       │
│ └─ Calls: CallReceiver.startScamMonitor(context, number)   │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│ SERVICE STARTUP (ScamMonitorService.onStartCommand)        │
│ ├─ Foreground notification created & shown                 │
│ ├─ RECORD_AUDIO permission verified                        │
│ ├─ VoskProcessor created (async model loading starts)      │
│ └─ Polling loop initiated (10-second timeout)              │
└──────────────────┬──────────────────────────────────────────┘
                   │
          ┌────────┴─────────┐
          │                  │
          ▼                  ▼
    ┌─────────────┐   ┌──────────────┐
    │ VOSK PATH   │   │ FALLBACK PATH│
    │ (Primary)   │   │ (Google)     │
    └─────────────┘   └──────────────┘
          │                  │
          │ if model loads   │ if timeout or fail
          │ within 10 seconds│
          │                  │
          ▼                  ▼
    ┌─────────────┐   ┌──────────────┐
    │ VoskProcessor   │ SpeechRecognizer
    │ .start()        │ .startListening()
    │                 │
    │ SpeechService   │ Google Cloud/
    │ listens on mic  │ Device Speech API
    │ 16kHz, PCM      │
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐
    │ AUDIO CAPTURE   │
    │ (System Microphone)
    └────────┬────────┘
             │
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
 VOSK PATH       GOOGLE PATH
 (JSON Callback) (String Array)
    │                 │
    ▼                 ▼
 processHypothesis  onResults(Bundle)
 onFinalResult      getStringArrayList()
    │                 │
    └────────┬────────┘
             │
             ▼
   ScamMonitorService
   .onSpeechRecognized(String text)
             │
             ▼
   Normalize text:
   .toLowerCase().trim()
             │
             ▼
   Keyword Detection Loop:
   SCAM_KEYWORDS.contains(keyword)
   (O(1) HashSet lookup)
             │
    ┌────────┴─────────┐
    │                  │
    ▼                  ▼
 MATCH FOUND    NO MATCH
    │                  │
    ▼                  ▼
 triggerAlert()    Log & Continue
    │
    ├─ Debounce check (30s cooldown)
    ├─ Log "SCAM KEYWORD DETECTED"
    └─ Start ScamAlertActivity
       ├─ Show alert on screen
       ├─ FLAG_SHOW_WHEN_LOCKED
       └─ FLAG_TURN_SCREEN_ON
```

### Key Implementation Details

#### 1. Permission Verification (CRITICAL)
```java
// CHECK 1: Manifest declaration
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

// CHECK 2: Runtime permission verification (Android 6+)
boolean hasRecordAudio = ContextCompat.checkSelfPermission(this, 
    Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
if (!hasRecordAudio) {
    Log.e(TAG, "RECORD_AUDIO permission missing");
    return; // ABORT - cannot proceed without mic
}
```

#### 2. Foreground Service Type (Android 14+ Requirement)
```xml
<!-- AndroidManifest.xml -->
<service
    android:name="com.shreyanshi.scamshield.services.ScamMonitorService"
    android:foregroundServiceType="microphone|specialUse">
    <property 
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Real-time scam detection and alerting during calls" />
</service>
```

```java
// Java code - Android Q+ support
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(NOTIFICATION_ID, notification, 
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
} else {
    startForeground(NOTIFICATION_ID, notification);
}
```

#### 3. Vosk Model Loading Race Condition FIX
**Problem**: Original code had 5-second timeout while model needs up to 10 seconds.

**Solution**:
```java
// Sync timeouts - must match VoskProcessor.MODEL_LOAD_TIMEOUT_MS
final long maxWaitTime = 10000; // 10 seconds (not 5!)
final long pollInterval = 500;  // Check every 500ms

// Store Runnable for cleanup
pollingTask = new Runnable() {
    @Override
    public void run() {
        if (!isServiceRunning || voskProcessor == null) return;
        
        // CASE 1: Model ready
        if (voskProcessor.isAvailable()) {
            if (!usingVosk && speechRecognizer != null) {
                return; // Prevent dual-start
            }
            usingVosk = true;
            voskProcessor.start();
            return;
        }
        
        // CASE 2: Model failed
        if (voskProcessor.hasLoadingFailed()) {
            setupGoogleSpeech();
            return;
        }
        
        // CASE 3: Still loading - continue polling
        if (elapsedTime < maxWaitTime) {
            handler.postDelayed(this, pollInterval);
        } else {
            // CASE 4: Timeout - fallback
            setupGoogleSpeech();
        }
    }
};
handler.post(pollingTask);
```

#### 4. Prevent Dual-Start (Vosk + Google Speech Simultaneous)
```java
private void setupGoogleSpeech() {
    // CRITICAL: Only setup Google if Vosk is not running
    if (usingVosk) {
        Log.w(TAG, "Vosk already running, skipping Google");
        return;
    }
    
    // ... setup Google Speech ...
}
```

#### 5. Cleanup Handler Tasks in onDestroy()
```java
private void stopMonitoring() {
    // CRITICAL: Cancel pending tasks FIRST
    if (pollingTask != null) {
        handler.removeCallbacks(pollingTask);
        pollingTask = null;
    }
    
    // Then cleanup recognizers
    if (voskProcessor != null) {
        voskProcessor.stop();
        voskProcessor = null;
    }
    // ... cleanup Google Speech ...
}

@Override
public void onDestroy() {
    stopMonitoring();
    super.onDestroy();
}
```

#### 6. Optimized Keyword Detection
```java
// OLD (O(62n) - slow):
private final List<String> SCAM_KEYWORDS = Arrays.asList(...);

// NEW (O(1) - fast):
private final Set<String> SCAM_KEYWORDS = new HashSet<>(Arrays.asList(...));

// Usage:
@Override
public void onSpeechRecognized(String text) {
    if (text == null || text.isEmpty()) return;
    
    String normalized = text.toLowerCase().trim();
    
    // O(1) lookup instead of O(n)
    for (String keyword : SCAM_KEYWORDS) {
        if (normalized.contains(keyword)) {
            triggerAlert(keyword);
            return;
        }
    }
}
```

#### 7. Better JSON Error Handling
```java
private void processHypothesis(String hypothesis, String key) {
    try {
        // NULL/EMPTY check FIRST
        if (hypothesis == null || hypothesis.isEmpty()) {
            Log.d(TAG, "Empty hypothesis");
            return;
        }
        
        JSONObject json = new JSONObject(hypothesis);
        String text = json.optString(key, "").trim(); // Default to ""
        
        // Only process non-empty text
        if (!text.isEmpty()) {
            Log.i(TAG, "Recognized: " + text);
            listener.onSpeechRecognized(text);
        } else {
            Log.d(TAG, "No '" + key + "' field or empty value");
        }
    } catch (Exception e) {
        // Log JSON for debugging
        Log.e(TAG, "JSON parse error: " + e.getMessage() + 
            "\n  JSON: " + hypothesis, e);
    }
}
```

### Threading Model

| Component | Thread | Blocking? | Notes |
|-----------|--------|-----------|-------|
| **CallReceiver.onReceive()** | Binder | NO | Should return quickly |
| **ScamMonitorService.onStartCommand()** | Main | NO | Starts service async |
| **Vosk model loading** | ExecutorService | NO | Async unpacking, callback fires when done |
| **Polling loop** | Main Handler | NO | Posted with 500ms delays |
| **Google Speech** | System thread | NO | Offloaded to OS/Google |
| **Keyword detection** | STT callback | NO | Runs on callback thread (non-blocking) |
| **Alert display** | Main thread | NO | Via handler.post() |

### Do's & Don'ts for This Architecture

**DO:**
1. Always check RECORD_AUDIO permission at runtime before using audio
2. Sync service timeout (10s) with VoskProcessor timeout (10s)
3. Use polling with exponential backoff instead of blocking waits
4. Store Runnable references for cleanup in onDestroy()
5. Check `usingVosk` flag before starting second STT engine
6. Use HashSet for keyword lookup (O(1) vs O(n))
7. Log detailed errors with timestamps for debugging
8. Validate JSON responses from Vosk before processing

**DON'T:**
1. DON'T assume RECORD_AUDIO is granted just because it's declared
2. DON'T use busy-wait loops (Thread.sleep in while loop)
3. DON'T start multiple STT engines simultaneously
4. DON'T leave handler tasks pending after service destroy
5. DON'T use linear search for keyword matching
6. DON'T process empty/null JSON strings silently
7. DON'T hardcode timeouts - keep them synchronized
8. DON'T rely on file existence checks - use state query methods

## User Interface Feedback & Alert Overlays

### Overview
ScamShield provides real-time user feedback through persistent notifications and immediate alert overlays when scam keywords are detected. The implementation uses **Activity-based alerts (NO overlay permission required)** combined with audio/vibration feedback for maximum user awareness.

### Architecture Pattern

**Components:**
1. **Persistent Notification** - Continuous foreground service notification showing app is active
2. **ScamAlertActivity** - Activity-based alert (displays on top of current screen)
3. **Real-Time Speech Feedback** - Toast messages showing what the recognizer is hearing
4. **Audio/Vibration Alerts** - Audible alarm and device vibration when scam detected

**Flow:**
```
Incoming Call
     ↓
CallReceiver detects PHONE_STATE
     ↓
ScamMonitorService starts (foreground with notification)
     ↓
GoogleSpeechRecognizer listens
     ↓
onPartialResults (shows Toast: "📢 Heard: [text]")
     ↓
Scam Keyword Detected in onSpeechRecognized()
     ↓
triggerAlert() called with debounce check
     ↓
ScamAlertActivity started with Intent flags:
- FLAG_ACTIVITY_NEW_TASK
- FLAG_ACTIVITY_CLEAR_TOP
- FLAG_SHOW_WHEN_LOCKED (shows on lock screen)
- FLAG_TURN_SCREEN_ON (wakes device)
     ↓
Alert UI shows:
- Large red warning with "SCAM DETECTED!"
- Keyword and caller number
- Sound + vibration pattern (500ms bursts)
- "End Call & Report" and "Dismiss" buttons
```

### Implementation Details

#### 1. Persistent Notification (Foreground Service)
**Location:** `ScamMonitorService.startForegroundWithNotification()`

```java
Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
    .setContentTitle("ScamShield Active")  // ✅ Shows app is running
    .setContentText("Protecting you from fraud calls...")  // ✅ User context
    .setSmallIcon(R.drawable.ic_notification)  // ✅ White monochrome (24x24 dp)
    .setPriority(NotificationCompat.PRIORITY_LOW)  // Silent
    .setOngoing(true)  // Cannot be dismissed
    .setCategory(NotificationCompat.CATEGORY_SERVICE)  // Proper categorization
    .setSubText("Real-time scam detection")  // Additional info
    .setContentIntent(pendingIntent)  // Click to return to app
    .build();

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(NOTIFICATION_ID, notification, 
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
} else {
    startForeground(NOTIFICATION_ID, notification);
}
```

**Requirements:**
- NotificationChannel created FIRST (Android 8.0+)
- Icon must be white monochrome (#FFFFFF) - use `ic_notification.xml`
- `setOngoing(true)` prevents user dismissal
- Called within 5 seconds of `onStartCommand()`

#### 2. Real-Time Speech Feedback
**Location:** `GoogleSpeechRecognizer.onPartialResults()`

```java
@Override
public void onPartialResults(Bundle partialResults) {
    ArrayList<String> results = partialResults.getStringArrayList(
        SpeechRecognizer.RESULTS_RECOGNITION);
    
    if (results != null && !results.isEmpty()) {
        String text = results.get(0).trim();
        
        if (!text.isEmpty() && listener != null) {
            Log.d(TAG, "🔄 Partial result: '" + text + "'");
            
            // ✅ Show Toast for real-time feedback
            showToast("📢 Heard: " + text);
            
            // Callback to service for keyword detection
            listener.onSpeechRecognized(text);
        }
    }
}

private void showToast(String message) {
    try {
        handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    } catch (Exception e) {
        Log.d(TAG, "Could not show Toast: " + e.getMessage());
    }
}
```

**Purpose:**
- User sees what the app is hearing in real-time
- Shows as Toast at bottom of screen (non-intrusive)
- Provides confidence that speech recognition is working
- Logging with 📢 emoji for easy debugging

#### 3. Scam Alert Activity (Activity-Based, NO Overlay)
**Location:** `ScamAlertActivity` (53 lines, no overlay permission needed)

```java
public static Intent createIntent(Context ctx, String keywords, String number) {
    Intent i = new Intent(ctx, ScamAlertActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    i.putExtra(EXTRA_KEYWORDS, keywords);
    i.putExtra(EXTRA_NUMBER, number);
    return i;
}

private void setupWindowFlags() {
    // Show on lock screen (Android 8.1+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(true);
        setTurnScreenOn(true);
    } else {
        // Fallback for older Android
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }
    
    Window window = getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
}

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    setupWindowFlags();
    setContentView(R.layout.activity_scam_alert);
    
    // Get intent extras
    String keywords = getIntent().getStringExtra(EXTRA_KEYWORDS);
    String number = getIntent().getStringExtra(EXTRA_NUMBER);
    
    // Update UI with details
    TextView tvKeywords = findViewById(R.id.tvAlertKeywords);
    tvKeywords.setText("Scam Alert: " + keywords);
    
    // Play alert sound and vibration
    playAlertSound();
    vibrateDevice();
    
    // Setup button listeners
    Button btnDismiss = findViewById(R.id.btnDismissAlert);
    btnDismiss.setOnClickListener(v -> dismissAlert());
    
    Button btnEndCall = findViewById(R.id.btnEndCall);
    btnEndCall.setOnClickListener(v -> {
        endCall();
        dismissAlert();
    });
}

private void playAlertSound() {
    Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    if (alarmUri != null) {
        ringtone = RingtoneManager.getRingtone(this, alarmUri);
        ringtone.play();
    }
}

private void vibrateDevice() {
    Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    if (vibrator != null && vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(
                new long[]{0, 500, 200, 500, 200, 500}, -1));
        } else {
            vibrator.vibrate(new long[]{0, 500, 200, 500, 200, 500}, -1);
        }
    }
}

private void endCall() {
    try {
        TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager != null) {
            telecomManager.endCall();
        }
    } catch (Exception e) {
        Log.e(TAG, "Could not end call: " + e.getMessage());
    }
}

private void dismissAlert() {
    if (ringtone != null && ringtone.isPlaying()) {
        ringtone.stop();
    }
    Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    if (vibrator != null) {
        vibrator.cancel();
    }
    finish();
}
```

**Key Features:**
- Uses Activity (no SYSTEM_ALERT_WINDOW permission)
- Shows on lock screen with `setShowWhenLocked(true)`
- Wakes device with `setTurnScreenOn(true)`
- Plays notification ringtone
- Vibrates device in pattern (500ms + 200ms + 500ms + 200ms + 500ms)
- Two action buttons: "End Call & Report" and "Dismiss"
- Auto-cleanup of resources in `dismissAlert()`

#### 4. Alert Layout (UI Design)
**Location:** `activity_scam_alert.xml`

```xml
<LinearLayout android:background="#1A1A1A" android:padding="24dp">
    <MaterialCardView 
        app:cardBackgroundColor="#2D2D2D"
        app:strokeColor="#D32F2F"  <!-- Red border -->
        app:strokeWidth="2dp">
        
        <!-- Alert Icon (80x80 dp) -->
        <ImageView android:src="@android:drawable/ic_dialog_alert"
                   app:tint="#D32F2F" />
        
        <!-- Title -->
        <TextView android:text="SCAM DETECTED!"
                  android:textColor="#D32F2F"
                  android:textSize="24sp"
                  android:textStyle="bold" />
        
        <!-- Keyword Details -->
        <TextView android:text="Scam Alert: [keyword]"
                  android:textColor="#FF9800"
                  android:textSize="18sp" />
        
        <!-- Caller Number -->
        <TextView android:text="From: [number]"
                  android:textColor="#AAAAAA"
                  android:textSize="14sp" />
        
        <!-- Safety Advice -->
        <TextView android:text="Do NOT share any personal information, OTPs, or bank details. Hang up immediately if possible."
                  android:textColor="#FFEB3B"
                  android:textSize="14sp"
                  android:background="#333333" />
        
        <!-- Action Buttons -->
        <Button android:text="End Call & Report"
                android:backgroundTint="#D32F2F" />
        <Button android:text="Dismiss"
                android:backgroundTint="#757575" />
    </MaterialCardView>
</LinearLayout>
```

**Design:** Dark theme (#1A1A1A) with red accents (#D32F2F) for alarm urgency

#### 5. Alert Trigger Pipeline
**Location:** `ScamMonitorService.onSpeechRecognized()` → `triggerAlert()` → `showScamAlert()`

```java
@Override
public void onSpeechRecognized(String text) {
    if (text == null || text.isEmpty()) return;
    
    String normalizedText = text.toLowerCase().trim();
    
    // Check for keyword matches (O(1) lookup with HashSet)
    for (String keyword : SCAM_KEYWORDS) {
        if (normalizedText.contains(keyword)) {
            Log.i(TAG, "🚨 SCAM KEYWORD DETECTED: '" + keyword + "'");
            triggerAlert(keyword);
            return;
        }
    }
}

private void triggerAlert(String detectedKeyword) {
    long now = System.currentTimeMillis();
    
    // ✅ Debounce: Prevent multiple alerts within 30 seconds
    if (now - lastAlertTime < ALERT_DEBOUNCE_MS) {
        Log.d(TAG, "Alert debounced (cooldown active)");
        return;
    }
    lastAlertTime = now;
    
    Log.w(TAG, "!!! SCAM KEYWORD DETECTED: " + detectedKeyword);
    showScamAlert(detectedKeyword);
}

private void showScamAlert(String keywords) {
    try {
        // Create intent with context and extras
        Intent alertIntent = ScamAlertActivity.createIntent(this, keywords, currentNumber);
        
        // Start activity (will appear on top of dialer/lock screen)
        startActivity(alertIntent);
        
        Log.i(TAG, "✅ Scam alert displayed for: " + keywords);
    } catch (Exception e) {
        Log.e(TAG, "Error showing alert: " + e.getMessage());
        
        // Fallback to Toast if Activity fails
        handler.post(() -> Toast.makeText(this, "Scam Alert: " + keywords, Toast.LENGTH_LONG).show());
    }
}
```

**Key Logic:**
- 30-second debounce prevents alert spam (ALERT_DEBOUNCE_MS = 30000)
- HashSet lookup O(1) for fast keyword detection
- Try-catch with Toast fallback ensures user gets some feedback
- Logging with 🚨 emoji highlights critical events

### Permission Requirements

**NO SYSTEM_ALERT_WINDOW Permission Required**
- Activity-based approach uses existing Activity permissions
- No special overlay permission needed (unlike old system alerts)
- Safer on Moto and other OEM Android devices

**Required Permissions Already in Manifest:**
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />  <!-- For alerts -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />  <!-- For listening -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />  <!-- For service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />  <!-- Microphone -->
```

**Runtime Checks:**
- RECORD_AUDIO verified before speech recognition starts
- POST_NOTIFICATIONS checked on Android 13+ before showing notification

### DO's & DON'Ts for UI/Alerts

**DO:**
1. Check RECORD_AUDIO permission at runtime before initializing speech recognizer
2. Create NotificationChannel before building notification (Android 8.0+)
3. Use white monochrome icons for notifications (pure #FFFFFF fill)
4. Show Toast feedback for every recognized word (helps debug)
5. Use Activity-based alerts instead of overlay windows
6. Set `FLAG_SHOW_WHEN_LOCKED` and `FLAG_TURN_SCREEN_ON` for lock screen alerts
7. Implement debounce (30 seconds minimum) to prevent alert spam
8. Play sound and vibration for critical alerts (scam detected)
9. Provide clear action buttons: "End Call" and "Dismiss"
10. Log all alert events with timestamps and emoji markers (🚨, ✅, 📢)
11. Cleanup resources in AlertActivity.dismissAlert() (stop sound, cancel vibration)
12. Use handler.post() for Toast/UI updates from non-main threads

**DON'T:**
1. DON'T use SYSTEM_ALERT_WINDOW permission (causes install issues on Moto)
2. DON'T create notifications without NotificationChannel (Android 8.0+ crash)
3. DON'T use colored icons for notifications (must be monochrome white)
4. DON'T assume POST_NOTIFICATIONS is granted (check on Android 13+)
5. DON'T block main thread with alert display (use startActivity with flags)
6. DON'T show multiple alerts for same keyword in quick succession
7. DON'T leave ringtone playing after activity destroyed
8. DON'T ignore vibration permission errors
9. DON'T show Toast from background service without handler.post()
10. DON'T assume TelecomManager.endCall() is available (wrap in try-catch)
11. DON'T use deprecated WindowManager flags without fallback for older Android
12. DON'T process speech results on non-main thread (use handler for UI updates)

### Testing Checklist

- [ ] Install app on Android device (minSdk 24)
- [ ] Grant RECORD_AUDIO permission when prompted
- [ ] Verify foreground notification appears with "ScamShield Active"
- [ ] Trigger incoming call with test number
- [ ] Speak non-scam words: verify Toast shows "📢 Heard: [word]" for each partial result
- [ ] Speak scam keyword: verify alert activity appears immediately
- [ ] Verify alert shows keyword, caller number, and advice text
- [ ] Verify alert sound plays (notification ringtone)
- [ ] Verify device vibrates in pattern (3 bursts of 500ms)
- [ ] Test "End Call & Report" button
- [ ] Test "Dismiss" button (stops sound/vibration)
- [ ] Trigger same keyword twice within 30 seconds: verify 2nd alert is debounced
- [ ] Test on lock screen: alert should appear over lock screen
- [ ] Test with screen off: alert should turn screen on
- [ ] Check logs for emoji markers: 🚨 (critical), ✅ (success), 📢 (info)

### Troubleshooting

**Problem:** Notification doesn't appear
- **Solution:** Check NotificationChannel creation in createNotificationChannel()
- Verify icon resource R.drawable.ic_notification exists
- Check Android version (NotificationChannel required for O+)

**Problem:** Toast feedback not showing
- **Solution:** Ensure showToast() uses handler.post() for UI updates
- Check that GoogleSpeechRecognizer has Toast import and showToast() method

**Problem:** Alert activity not appearing
- **Solution:** Verify ScamAlertActivity is exported in AndroidManifest.xml
- Check intent flags: FLAG_ACTIVITY_NEW_TASK, FLAG_ACTIVITY_CLEAR_TOP
- Verify setupWindowFlags() called with setShowWhenLocked() and setTurnScreenOn()

**Problem:** Sound/vibration not working
- **Solution:** Check device has vibrator (vibrator.hasVibrator())
- Verify ringtone URI is not null (fallback to TYPE_RINGTONE if TYPE_NOTIFICATION fails)
- Check Build.VERSION.SDK_INT for VibrationEffect API level

**Problem:** Multiple alerts in quick succession
- **Solution:** Implement 30-second debounce in triggerAlert()
- Check lastAlertTime logic: now - lastAlertTime < ALERT_DEBOUNCE_MS

## Background Lifecycle & Debugging Architecture

### Overview
ScamShield implements continuous background speech recognition that persists across app lifecycle changes. The architecture ensures the listening loop never stops indefinitely, with automatic restart mechanisms in both error and success scenarios. In-app debug logging provides real-time visibility into speech recognition events and helps diagnose why scam detection might appear to "silently fail."

### Auto-Restart Listening Pattern

**Key Principle:** SpeechRecognizer must be restarted immediately after `onResults()` or `onError()` to maintain continuous monitoring. The original Google Speech API times out after ~60 seconds, so auto-restart is mandatory.

**Location:** `GoogleSpeechRecognizer.java`

```java
// Auto-restart timing constants
private static final long AUTO_RESTART_DELAY_MS = 1000;        // 1 second for transient errors
private static final long AUTO_RESTART_LONG_DELAY_MS = 3000;   // 3 seconds for permanent errors
private boolean isListening = false;
private Handler handler;

// Called when speech recognition completes (with or without results)
@Override
public void onResults(Bundle results) {
    ArrayList<String> matches = results.getStringArrayList(
        SpeechRecognizer.RESULTS_RECOGNITION);
    
    String text = "";
    if (matches != null && !matches.isEmpty()) {
        text = matches.get(0).trim();
    }
    
    if (!text.isEmpty() && listener != null) {
        Log.i(TAG, "✅ Final result: '" + text + "'");
        listener.onSpeechRecognized(text);
    } else {
        Log.d(TAG, "⚠️  Empty final result");
    }
    
    // CRITICAL: Auto-restart listening for continuous monitoring
    isListening = false;
    autoRestartListening();  // Restart immediately
    Log.d(TAG, "🔄 Auto-restarting listening after onResults()");
}

// Called when speech recognition encounters an error
@Override
public void onError(int errorCode) {
    String errorMsg = getErrorString(errorCode);
    Log.e(TAG, "❌ Speech error: [" + errorCode + "] " + errorMsg);
    
    isListening = false;
    
    // Determine delay based on error type
    long delay = AUTO_RESTART_DELAY_MS;
    
    if (isTransientError(errorCode)) {
        // Transient errors: retry quickly (1 second)
        Log.i(TAG, "🔄 Transient error detected, auto-restarting in 1 second...");
    } else {
        // Permanent errors: wait longer before retry (3 seconds)
        Log.w(TAG, "⚠️  Permanent error detected, retrying in 3 seconds...");
        delay = AUTO_RESTART_LONG_DELAY_MS;
    }
    
    handler.postDelayed(this::autoRestartListening, delay);
}

// Auto-restart method: safely restart listening
private void autoRestartListening() {
    if (speechRecognizer == null) {
        Log.e(TAG, "❌ SpeechRecognizer is null, cannot restart");
        return;
    }
    
    if (isListening) {
        Log.w(TAG, "⚠️  Already listening, skipping restart");
        return;
    }
    
    try {
        Log.i(TAG, "🎤 Starting speech recognition...");
        speechRecognizer.startListening(recognizerIntent);
        isListening = true;
    } catch (Exception e) {
        Log.e(TAG, "❌ Error starting listening: " + e.getMessage());
        
        // Schedule retry
        handler.postDelayed(this::autoRestartListening, AUTO_RESTART_DELAY_MS);
    }
}

// Determine if error is temporary (can retry quickly)
private boolean isTransientError(int errorCode) {
    return errorCode == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
        || errorCode == SpeechRecognizer.ERROR_AUDIO
        || errorCode == SpeechRecognizer.ERROR_NO_MATCH
        || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY;
}

// Get human-readable error message
private String getErrorString(int errorCode) {
    switch (errorCode) {
        case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
        case SpeechRecognizer.ERROR_CLIENT: return "Client error";
        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
        case SpeechRecognizer.ERROR_NETWORK: return "Network error";
        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
        case SpeechRecognizer.ERROR_NO_MATCH: return "No speech input";
        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognizer busy";
        case SpeechRecognizer.ERROR_SERVER: return "Server error";
        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech detected";
        default: return "Unknown error";
    }
}
```

### Enhanced SpeechListener Interface

**Purpose:** Enable bidirectional communication between GoogleSpeechRecognizer and ScamMonitorService. Allows real-time debug event broadcasting to MainActivity.

**Location:** `SpeechListener.java`

```java
public interface SpeechListener {
    /**
     * Called when speech is recognized (from onPartialResults or onResults)
     * @param text The recognized text (partial or final)
     */
    void onSpeechRecognized(String text);
    
    /**
     * Called for debug/diagnostic events
     * @param debugMessage Debug log message with emoji prefix
     */
    void onDebugLog(String debugMessage);
}
```

**Usage Example:**
```java
// In GoogleSpeechRecognizer
if (listener != null) {
    listener.onDebugLog("🔄 Partial result: '" + text + "'");
    listener.onSpeechRecognized(text);
}

// In ScamMonitorService
@Override
public void onDebugLog(String debugMessage) {
    Log.d(TAG, debugMessage);
    
    // Broadcast to MainActivity for in-app debug log
    if (debugListener != null) {
        debugListener.onDebugLog(debugMessage);
    }
}
```

### In-App Debug Log UI

**Purpose:** Show real-time speech recognition events in MainActivity for debugging why scam detection appears to fail silently.

**Location:** `MainActivity.java`

```java
private TextView tvDebugLog;
private ScrollView svDebugScroll;
private static final int MAX_DEBUG_LINES = 20;

// Initialize debug log UI
private void initializeDebugLog() {
    tvDebugLog = findViewById(R.id.tvDebugLog);
    svDebugScroll = findViewById(R.id.svDebugScroll);
    
    // Retrieve visibility preference from SharedPreferences
    SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
    boolean debugLogEnabled = prefs.getBoolean("debug_log_enabled", false);
    
    // Set initial visibility
    int visibility = debugLogEnabled ? View.VISIBLE : View.GONE;
    tvDebugLog.setVisibility(visibility);
    svDebugScroll.setVisibility(visibility);
    
    // Style the debug log
    tvDebugLog.setMaxLines(MAX_DEBUG_LINES);
    tvDebugLog.setTextColor(Color.GREEN);  // #00FF00
    tvDebugLog.setBackgroundColor(Color.BLACK);  // #000000
    tvDebugLog.setPadding(16, 16, 16, 16);
    tvDebugLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
    tvDebugLog.setTypeface(Typeface.MONOSPACE);
}

// Add timestamped debug log entry
private void addDebugLogEntry(String message) {
    Handler mainHandler = new Handler(Looper.getMainLooper());
    mainHandler.post(() -> {
        try {
            // Format: [HH:mm:ss] <message>
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
            String timestamp = "[" + sdf.format(new Date()) + "]";
            String logLine = timestamp + " " + message;
            
            // Get current text
            String currentText = tvDebugLog.getText().toString();
            
            // Append new line
            if (currentText.isEmpty()) {
                tvDebugLog.setText(logLine);
            } else {
                tvDebugLog.setText(currentText + "\n" + logLine);
            }
            
            // Keep only last 20 lines to prevent memory bloat
            String[] lines = tvDebugLog.getText().toString().split("\n");
            if (lines.length > MAX_DEBUG_LINES) {
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - MAX_DEBUG_LINES; i < lines.length; i++) {
                    if (i > lines.length - MAX_DEBUG_LINES) sb.append("\n");
                    sb.append(lines[i]);
                }
                tvDebugLog.setText(sb.toString());
            }
            
            // Auto-scroll to bottom
            svDebugScroll.post(() -> svDebugScroll.fullScroll(View.FOCUS_DOWN));
        } catch (Exception e) {
            Log.e(TAG, "Error adding debug log: " + e.getMessage());
        }
    });
}

// Toggle debug log visibility
private void toggleDebugLog() {
    SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
    boolean currentlyEnabled = prefs.getBoolean("debug_log_enabled", false);
    boolean newState = !currentlyEnabled;
    
    // Update UI
    int visibility = newState ? View.VISIBLE : View.GONE;
    tvDebugLog.setVisibility(visibility);
    svDebugScroll.setVisibility(visibility);
    
    // Save preference
    SharedPreferences.Editor editor = prefs.edit();
    editor.putBoolean("debug_log_enabled", newState);
    editor.apply();
    
    Log.i(TAG, "Debug log toggled: " + (newState ? "ON" : "OFF"));
}

// Inner listener class for receiving debug events
private class SpeechListenerImpl implements SpeechListener {
    @Override
    public void onSpeechRecognized(String text) {
        // Placeholder - handled elsewhere in MainActivity
    }
    
    @Override
    public void onDebugLog(String debugMessage) {
        addDebugLogEntry(debugMessage);
    }
}
```

**Layout Addition** (`activity_main.xml`):
```xml
<ScrollView
    android:id="@+id/svDebugScroll"
    android:layout_width="match_parent"
    android:layout_height="200dp"
    android:visibility="gone"
    android:layout_below="@id/bottomNavigation">
    
    <TextView
        android:id="@+id/tvDebugLog"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="#00FF00"
        android:textSize="10sp"
        android:textIsSelectable="true"
        android:padding="8dp"
        android:scrollbars="vertical" />
</ScrollView>
```

### Overlay Permission Safeguard

**Problem:** Display over other apps permission (SYSTEM_ALERT_WINDOW) causes install failures on some Android devices (e.g., Moto). ScamShield uses Activity-based alerts instead (no overlay needed), but we should prompt users to grant it for future features.

**Location:** `MainActivity.java`

```java
// Check overlay permission at app startup
private void checkOverlayPermission() {
    // Only relevant for Android 6.0+ (Build.VERSION_CODES.M)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return;
    }
    
    // Check if permission is already granted
    if (Settings.canDrawOverlays(this)) {
        Log.i(TAG, "✅ Overlay permission already granted");
        return;
    }
    
    // Check if we've already shown warning this session
    SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
    boolean warningShown = prefs.getBoolean("overlay_warning_shown", false);
    
    if (!warningShown) {
        showOverlayPermissionDialog();
        
        // Mark warning as shown
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("overlay_warning_shown", true);
        editor.apply();
    }
}

// Show dialog explaining overlay permission
private void showOverlayPermissionDialog() {
    new AlertDialog.Builder(this, R.style.AlertDialog)
        .setTitle("Enable Display Over Other Apps")
        .setMessage("ScamShield needs permission to show alerts over other apps. "
            + "Go to Settings > App Permissions > Display over other apps and enable it for ScamShield.")
        .setPositiveButton("Open Settings", (dialog, which) -> openOverlaySettings())
        .setNegativeButton("Not Now", (dialog, which) -> dialog.dismiss())
        .setCancelable(false)
        .show();
}

// Open Android Settings for overlay permission
private void openOverlaySettings() {
    try {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    } catch (ActivityNotFoundException e) {
        Log.w(TAG, "Overlay settings not found, trying fallback...");
        
        // Fallback to app details settings
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
```

**Key Points:**
- Only shown once per session (tracked in SharedPreferences with key: `overlay_warning_shown`)
- Graceful fallback to ACTION_APPLICATION_DETAILS_SETTINGS if primary intent fails
- Non-blocking: user can dismiss and continue using app

### Fallback Alert Mechanism

**Problem:** If ScamAlertActivity fails to launch (rare edge case), user gets no visual feedback that scam was detected.

**Solution:** Trigger fallback alert by changing MainActivity's background color to RED for 2 seconds + vibration pattern.

**Location:** `MainActivity.java`

```java
private static MainActivity instance;  // Static reference for callback

@Override
protected void onStart() {
    super.onStart();
    instance = this;  // Track instance for fallback alert
    
    // Register debug listener
    SpeechListenerImpl listenerImpl = new SpeechListenerImpl();
    ScamMonitorService.setDebugListener(listenerImpl);
}

@Override
protected void onStop() {
    super.onStop();
    instance = null;
    ScamMonitorService.clearDebugListener();
}

// Public static method called by ScamMonitorService when alert fails
public static void triggerFallbackAlertStatic(String keyword) {
    if (instance != null) {
        instance.triggerFallbackAlert(keyword);
    }
}

// Trigger RED background alert + vibration
private void triggerFallbackAlert(String keyword) {
    FrameLayout mainContainer = findViewById(R.id.main_container);
    if (mainContainer == null) return;
    
    try {
        // Change background to RED (#FFD32F2F)
        mainContainer.setBackgroundColor(Color.parseColor("#FFD32F2F"));
        
        // Vibrate device in pattern (3 bursts)
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(
                    new long[]{0, 200, 100, 200, 100, 200}, -1);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(new long[]{0, 200, 100, 200, 100, 200}, -1);
            }
        }
        
        Log.w(TAG, "🚨 FALLBACK ALERT TRIGGERED: " + keyword);
        
        // Reset background after 2 seconds
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            mainContainer.setBackgroundColor(Color.parseColor("#121212"));
            Log.i(TAG, "Alert background reset to normal");
        }, 2000);
        
    } catch (Exception e) {
        Log.e(TAG, "Error triggering fallback alert: " + e.getMessage());
    }
}
```

**Location:** `ScamMonitorService.java`

```java
// Enhanced showScamAlert with fallback trigger
private void showScamAlert(String keywords) {
    try {
        // Try to launch alert activity
        Intent alertIntent = ScamAlertActivity.createIntent(this, keywords, currentNumber);
        startActivity(alertIntent);
        
        Log.i(TAG, "✅ Scam alert activity displayed");
        
        // Notify debug listener
        if (debugListener != null) {
            debugListener.onDebugLog("✅ Alert activity launched for: " + keywords);
        }
        
    } catch (Exception e) {
        Log.e(TAG, "❌ Error showing alert: " + e.getMessage());
        
        // Fallback 1: Toast
        handler.post(() -> Toast.makeText(this, 
            "Scam Alert: " + keywords, Toast.LENGTH_LONG).show());
        
        // Fallback 2: RED background alert in MainActivity
        MainActivity.triggerFallbackAlertStatic(keywords);
        
        // Notify debug listener of failure
        if (debugListener != null) {
            debugListener.onDebugLog("❌ Alert failed, triggered fallback: " + e.getMessage());
        }
    }
}
```

**Layout Update** (`activity_main.xml`):
```xml
<FrameLayout
    android:id="@+id/main_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#121212">
    
    <FrameLayout
        android:id="@+id/fragment_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_above="@id/bottomNavigation" />
    
    <!-- Rest of layout... -->
</FrameLayout>
```

### Threading Model & Handler Safety

**Key Rule:** All UI updates must run on main thread, all background work in background threads.

| Operation | Thread | Mechanism | Example |
|-----------|--------|-----------|---------|
| **Speech recognition** | System (OS) | Android framework | `SpeechRecognizer.startListening()` |
| **onResults/onError callbacks** | Binder (non-main) | RecognitionListener | Override methods run on OS thread |
| **Toast/UI updates from callback** | Main | handler.post() | `handler.post(() -> Toast.show())` |
| **Auto-restart delay** | Main | handler.postDelayed() | `handler.postDelayed(this::start, 1000)` |
| **Debug log append** | Main | handler.post() | `mainHandler.post(() -> textView.append(...))` |
| **Keyword detection** | Binder (non-main) | STT callback thread | `onSpeechRecognized()` runs on callback thread |
| **Alert launch** | Main | startActivity() | Called from service via debugListener or static |

### Common Silent Failure Scenarios & Debug Approach

**Scenario 1: Listening started but no speech detected**
```
Debug Log Output:
[10:45:23] 🎤 Starting speech recognition...
[10:45:24] ❌ Speech error: [6] No speech input
[10:45:24] 🔄 Transient error detected, auto-restarting in 1 second...
[10:45:25] 🎤 Starting speech recognition...
(silence continues...)
```
**Root Causes:** Microphone muted, noise-cancellation enabled, RECORD_AUDIO denied
**Check:** Verify audio input with system recorder app, check Settings > App Permissions

**Scenario 2: Speech detected but no keyword match**
```
Debug Log Output:
[10:46:01] 📢 Partial result: 'hello how are you'
[10:46:03] ✅ Final result: 'hello how are you'
[10:46:03] 🔄 Auto-restarting listening after onResults()
```
**Root Cause:** Spoken text doesn't contain scam keywords
**Check:** Review SCAM_KEYWORDS list, test with actual scam phrases

**Scenario 3: Keyword detected but no alert appears**
```
Debug Log Output:
[10:47:15] 📢 Partial result: 'verify your OTP'
[10:47:17] ✅ Final result: 'verify your OTP'
[10:47:17] 🚨 SCAM KEYWORD DETECTED: 'OTP'
[10:47:17] ❌ Alert failed, triggered fallback: No activity found
```
**Root Cause:** ScamAlertActivity not registered in AndroidManifest.xml
**Check:** Verify `<activity>` declaration and `android:exported="true"`

**Scenario 4: Listening stuck (no auto-restart)**
```
Debug Log Output:
[10:48:30] ✅ Final result: 'test phrase'
(no further logs - listening NOT restarted)
```
**Root Cause:** Auto-restart not called in onResults() or handler removed
**Check:** Verify autoRestartListening() called in both onResults() and onError()

### DO's & DON'Ts for Background Lifecycle & Debugging

**DO:**
1. Always call `autoRestartListening()` in both `onResults()` and `onError()` callbacks
2. Use different delays for transient (1s) vs permanent (3s) errors
3. Check if already listening (`isListening` flag) before starting again
4. Use handler.postDelayed() for all delayed tasks (never Thread.sleep)
5. Log all speech recognition events with emoji prefixes (📢, ✅, ❌, 🔄, 🚨)
6. Store Handler/Runnable references for proper cleanup on destroy
7. Always call removeCallbacks() in onDestroy() to prevent memory leaks
8. Use SimpleDateFormat for debug log timestamps (for easy sorting)
9. Limit debug log to 20 lines to prevent memory bloat
10. Test auto-restart by disconnecting WiFi (triggers network timeout)
11. Make debug log visible by default on first install (helps identify issues)
12. Provide toggle for debug log in Settings (SharedPreferences key: `debug_log_enabled`)
13. Trigger fallback alert when ScamAlertActivity fails to launch
14. Use try-catch around overlay permission check (API 23+ requirement)
15. Show overlay permission dialog only once per session (SharedPreferences key: `overlay_warning_shown`)

**DON'T:**
1. DON'T assume onResults() or onError() are called on main thread - always use handler.post()
2. DON'T leave SpeechRecognizer in "listening" state without timeout
3. DON'T call startListening() multiple times without checking isListening flag
4. DON'T hardcode auto-restart delays - use constants (AUTO_RESTART_DELAY_MS)
5. DON'T ignore onError() callbacks - they indicate failures that need recovery
6. DON'T use Log.d() for debug events - use emoji-prefixed Log.i() for visibility
7. DON'T forget to call handler.removeCallbacks() in onDestroy()
8. DON'T append to TextViews from non-main threads without handler.post()
9. DON'T use Toast directly in callbacks - wrap in handler.post()
10. DON'T assume Settings.canDrawOverlays() is available (check Build.VERSION_CODES.M)
11. DON'T show overlay permission dialog on every app start
12. DON'T let fallback alert change background color permanently
13. DON'T call triggerFallbackAlert() from main thread if instance is null
14. DON'T forget to track MainActivity instance in static field for fallback callback
15. DON'T assume Exception.getMessage() is not null when logging errors

### Testing Checklist for Background Lifecycle

- [ ] Install app on Android device (min SDK 24)
- [ ] Open MainActivity and verify debug log appears (should show "Starting speech recognition...")
- [ ] Toggle debug log visibility: should appear/disappear
- [ ] Verify Setting is saved: close/reopen app, visibility state persists
- [ ] Trigger incoming call (use test app like Google Dialer)
- [ ] Speak non-scam phrase: verify "Heard: [text]" Toast appears
- [ ] Check debug log contains: "🎤 Starting", "📢 Partial result", "✅ Final result", "🔄 Auto-restarting"
- [ ] Speak scam keyword: verify "🚨 SCAM KEYWORD DETECTED" in debug log
- [ ] Verify ScamAlertActivity appears within 2 seconds of keyword detection
- [ ] Trigger keyword twice in 30 seconds: verify second alert is debounced in logs
- [ ] Disconnect WiFi during call: verify "Transient error" recovery in logs
- [ ] Test with device on lock screen: alert should appear over lock screen
- [ ] Test with screen off: screen should turn on when alert appears
- [ ] Verify fallback alert if you can reproduce ScamAlertActivity failure
  - Expected: RED background (#FFD32F2F) for 2 seconds
  - Expected: Vibration pattern plays
  - Expected: Debug log shows "❌ Alert failed, triggered fallback"
- [ ] Force-stop service and reopen call: listening should restart from onReceive()
- [ ] Check logs for memory leaks: verify handler tasks cleaned up after destroy
- [ ] Verify no Logcat errors related to Speech Recognition

### Troubleshooting Background Lifecycle Issues

**Problem:** Auto-restart not happening (listening stops after 60 seconds)
- **Check 1:** Verify `autoRestartListening()` called in `onResults()`: `grep -n "autoRestartListening" GoogleSpeechRecognizer.java`
- **Check 2:** Verify `autoRestartListening()` called in `onError()`: same grep
- **Check 3:** Check if handler is null: `if (handler != null) handler.postDelayed(...)`
- **Solution:** Add missing calls to `autoRestartListening()` in both callbacks

**Problem:** isListening flag never becomes false (stuck in listening state)
- **Check 1:** Verify `isListening = false` in `onError()` before restart
- **Check 2:** Verify `isListening = false` in `onResults()` before restart
- **Check 3:** Check for exception in autoRestartListening() that doesn't set `isListening = true`
- **Solution:** Ensure all error/result paths set `isListening` before restarting

**Problem:** Debug log not updating in real-time
- **Check 1:** Verify debug log visibility toggle is ON
- **Check 2:** Verify MainActivity instance is not null: `if (instance != null)`
- **Check 3:** Verify handler.post() wrapping: `mainHandler.post(() -> ...)`
- **Check 4:** Check Logcat for "Error adding debug log" exceptions
- **Solution:** Use handler.post() for all TextViev updates, add null checks

**Problem:** Fallback alert doesn't trigger when ScamAlertActivity fails
- **Check 1:** Verify `MainActivity.triggerFallbackAlertStatic()` called in catch block
- **Check 2:** Verify instance is not null: check onStart()/onStop() setup
- **Check 3:** Verify main_container exists: `findViewById(R.id.main_container)`
- **Check 4:** Check Logcat for exception in fallback: "Error triggering fallback alert"
- **Solution:** Add null check for instance, verify container ID in XML

**Problem:** Overlay permission dialog shows on every app start
- **Check 1:** Verify SharedPreferences key is `overlay_warning_shown`
- **Check 2:** Verify editor.apply() called (not just put)
- **Check 3:** Check Logcat for SharedPreferences errors
- **Solution:** Ensure SharedPreferences.Editor.apply() is called, use correct key

**Problem:** Thread safety issues (Logcat shows "Only the original thread can touch its views")
- **Check 1:** Search for all Toast.makeText() calls - must be in handler.post()
- **Check 2:** Search for all tvDebugLog.setText() calls - must be in handler.post()
- **Check 3:** Check autoRestartListening() - verify handler.postDelayed() used
- **Solution:** Always wrap UI updates in handler.post(), use mainHandler for main thread

## Default Dialer Role & Service Lifecycle Management

### Overview
ScamShield can be set as the system default phone dialer app using Android's RoleManager (Android 10+) and TelecomManager (Android 7-9). The app implements proper service lifecycle management to ensure the ScamMonitorService starts/stops correctly with the toggle in Settings and properly cleans up resources on destruction.

### Architecture Pattern

**Components:**
1. **RoleManager** - Android 10+ (Q) role-based dialer selection
2. **TelecomManager** - Android 7-9 legacy dialer selection via intent
3. **ScamShieldInCallService** - InCallService for telecom framework integration
4. **ScamMonitorService** - Foreground service that monitors for scam calls
5. **SettingsFragment** - Toggle to enable/disable scam detection (starts/stops service)

**Flow - Setting as Default Dialer:**
```
MainActivity.onCreate()
    ↓
requestDefaultDialerRole() called
    ↓
    ├─ Android 10+: RoleManager.createRequestRoleIntent(ROLE_DIALER)
    │  └─ startActivityForResult(intent, REQUEST_ROLE_DIALER)
    │     ↓
    │     onActivityResult(REQUEST_ROLE_DIALER)
    │     └─ RESULT_OK → System sets ScamShield as default dialer
    │
    └─ Android 7-9: TelecomManager.ACTION_CHANGE_DEFAULT_DIALER
       └─ startActivity(intent)
          └─ System prompts user to select default dialer
```

**Flow - Service Lifecycle:**
```
Toggle ON in Settings
    ↓
SettingsFragment.startScamProtection()
    ↓
startForegroundService(ScamMonitorService)
    ↓
ScamMonitorService.onStartCommand()
    ├─ Check SharedPreferences "scam_alerts_enabled"
    ├─ If enabled: initialize speech recognition
    └─ Start foreground with persistent notification
    
    ↓
    Service runs continuously monitoring for scam keywords
    
    ↓
Toggle OFF in Settings
    ↓
SettingsFragment.stopScamProtection()
    ↓
stopService(ScamMonitorService)
    ↓
ScamMonitorService.onDestroy()
    ├─ Call stopForeground(STOP_FOREGROUND_REMOVE)  → Removes notification
    ├─ googleSpeechRecognizer.destroy()             → Releases microphone
    └─ handler.removeCallbacks(pollingTask)         → Cleans up timers
```

### Implementation Details

#### 1. AndroidManifest.xml Configuration

**Required Permissions:**
```xml
<uses-permission android:name="android.permission.BIND_INCALL_SERVICE" />
<uses-permission android:name="android.permission.MANAGE_ONGOING_CALLS" />

<!-- For Android 11+ package visibility -->
<queries>
    <intent>
        <action android:name="android.telecom.InCallService" />
    </intent>
</queries>
```

**InCallService Declaration:**
```xml
<service
    android:name="com.shreyanshi.scamshield.services.ScamShieldInCallService"
    android:exported="true"
    android:permission="android.permission.BIND_INCALL_SERVICE">
    <intent-filter>
        <action android:name="android.telecom.InCallService" />
    </intent-filter>
</service>
```

**MainActivity Intent Filters (for default dialer):**
```xml
<activity android:name="com.shreyanshi.scamshield.activities.MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    
    <!-- Dialer intent filters -->
    <intent-filter>
        <action android:name="android.intent.action.DIAL" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="tel" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.CALL_BUTTON" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    
    <!-- Metadata declaring default dialer support -->
    <meta-data
        android:name="android.app.dialer.default"
        android:value="true" />
</activity>
```

#### 2. MainActivity - Default Dialer Role Request

**Location:** `MainActivity.java`

```java
private static final int REQUEST_ROLE_DIALER = 101;
private static final String PREF_DIALER_ROLE_REQUESTED = "dialer_role_requested";

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... other initialization ...
    requestDefaultDialerRole();  // Called once per app lifetime
    setContentView(R.layout.activity_main);
}

/**
 * Request user to set ScamShield as the default dialer app.
 * Only shown once per app lifetime (tracked by SharedPreferences).
 * Non-blocking: user can skip without affecting app functionality.
 */
private void requestDefaultDialerRole() {
    try {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean alreadyRequested = prefs.getBoolean(PREF_DIALER_ROLE_REQUESTED, false);
        
        if (alreadyRequested) {
            Log.d(TAG, "ℹ️ Dialer role already requested in previous session");
            return;
        }

        // Mark as requested for this session
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_DIALER_ROLE_REQUESTED, true);
        editor.apply();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses RoleManager
            try {
                RoleManager roleManager = getSystemService(RoleManager.class);
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                    startActivityForResult(intent, REQUEST_ROLE_DIALER);
                    Log.i(TAG, "✅ Requested default dialer role via RoleManager");
                } else {
                    Log.w(TAG, "⚠️ Dialer role not available on this device");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ RoleManager error: " + e.getMessage());
            }
        } else {
            // Android 7-9 uses TelecomManager
            try {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                startActivity(intent);
                Log.i(TAG, "✅ Requested default dialer role via TelecomManager");
            } catch (Exception e) {
                Log.w(TAG, "⚠️ TelecomManager error: " + e.getMessage());
            }
        }
    } catch (Exception e) {
        Log.e(TAG, "❌ Error requesting default dialer role: " + e.getMessage());
    }
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    
    if (requestCode == REQUEST_ROLE_DIALER) {
        if (resultCode == RESULT_OK) {
            Log.i(TAG, "✅ Successfully set as default dialer");
        } else {
            Log.i(TAG, "ℹ️ User declined default dialer role");
        }
    }
}
```

**Key Features:**
- Uses RoleManager on Android 10+ (modern API)
- Falls back to TelecomManager on Android 7-9
- Shows request dialog only once (tracked via SharedPreferences)
- Non-blocking: user can skip, app continues to work
- Detailed logging for debugging dialer role issues

#### 3. ScamShieldInCallService - Telecom Framework Integration

**Location:** `ScamShieldInCallService.java` (extends `InCallService`)

```java
public class ScamShieldInCallService extends InCallService {
    private static final String TAG = "ScamShield-InCall";

    /**
     * Called when a new call is added to the system.
     * Logs call information but doesn't interfere with normal call handling.
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
```

**Design Notes:**
- Minimal implementation (only logs call events)
- No complex call management logic (system handles that)
- Required for Android's Telecom framework to recognize ScamShield as a valid dialer
- Non-blocking implementation ensures system stability

#### 4. SettingsFragment - Service Toggle Implementation

**Location:** `SettingsFragment.java`

```java
// Initialize toggle listener
scamDetectionToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
    SharedPreferences prefs = getActivity().getSharedPreferences("ScamShieldPrefs", Context.MODE_PRIVATE);
    prefs.edit().putBoolean("scam_alerts_enabled", isChecked).apply();
    
    if (isChecked) {
        startScamProtection();
    } else {
        stopScamProtection();
    }
});

/**
 * Start scam protection service
 */
private void startScamProtection() {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().startForegroundService(
                new Intent(requireActivity(), ScamMonitorService.class)
            );
        } else {
            requireActivity().startService(
                new Intent(requireActivity(), ScamMonitorService.class)
            );
        }
        Log.i(TAG, "✅ Scam protection started");
    } catch (Exception e) {
        Log.e(TAG, "❌ Error starting scam protection: " + e.getMessage());
    }
}

/**
 * Stop scam protection service
 */
private void stopScamProtection() {
    try {
        requireActivity().stopService(
            new Intent(requireActivity(), ScamMonitorService.class)
        );
        Log.i(TAG, "✅ Scam protection stopped");
    } catch (Exception e) {
        Log.e(TAG, "❌ Error stopping scam protection: " + e.getMessage());
    }
}
```

**Key Features:**
- Uses `startForegroundService()` on Android O+ (required for foreground services)
- Reads/writes SharedPreferences to persist toggle state
- Non-blocking toggle UI updates
- Detailed logging for debugging service lifecycle

#### 5. ScamMonitorService - Lifecycle Management

**Location:** `ScamMonitorService.java`

```java
@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    // Check if scam alerts are enabled (read from SharedPreferences)
    SharedPreferences prefs = getSharedPreferences("ScamShieldPrefs", Context.MODE_PRIVATE);
    boolean scamAlertsEnabled = prefs.getBoolean("scam_alerts_enabled", false);
    
    if (!scamAlertsEnabled) {
        Log.w(TAG, "⚠️ Scam alerts disabled, stopping service");
        stopSelf();
        return START_NOT_STICKY;
    }
    
    // Check RECORD_AUDIO permission
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
        Log.e(TAG, "❌ RECORD_AUDIO permission missing, cannot start monitoring");
        stopSelf();
        return START_NOT_STICKY;
    }
    
    // Start foreground service with persistent notification
    startForegroundWithNotification();
    
    // Initialize speech recognition
    initializeSpeechRecognition();
    
    Log.i(TAG, "✅ ScamMonitorService started successfully");
    return START_STICKY;  // Restart if killed
}

/**
 * CRITICAL: Stop foreground and cleanup resources when service is destroyed.
 * This is called when the toggle is turned OFF in Settings.
 */
@Override
public void onDestroy() {
    super.onDestroy();
    
    Log.i(TAG, "🛑 ScamMonitorService.onDestroy() called");
    
    try {
        // CRITICAL: Remove notification immediately
        // Must be called before releasing microphone to avoid orphaned notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);  // Android N+: removes notification
        } else {
            stopForeground(true);  // Pre-N: removes notification
        }
        Log.i(TAG, "✅ Notification removed via stopForeground()");
    } catch (Exception e) {
        Log.e(TAG, "❌ Error removing notification: " + e.getMessage());
    }
    
    // Stop monitoring
    stopMonitoring();
    
    // Cleanup handler tasks
    if (handler != null) {
        handler.removeCallbacksAndMessages(null);
    }
    
    Log.i(TAG, "✅ ScamMonitorService cleanup complete");
}

/**
 * Stop speech recognition and release microphone
 */
private void stopMonitoring() {
    try {
        // Cancel any pending polling tasks
        if (pollingTask != null) {
            handler.removeCallbacks(pollingTask);
            pollingTask = null;
        }
        
        // Stop Vosk processor if running
        if (voskProcessor != null) {
            voskProcessor.stop();
            voskProcessor = null;
            Log.i(TAG, "✅ Vosk processor stopped");
        }
        
        // Stop Google Speech recognizer if running
        if (googleSpeechRecognizer != null) {
            try {
                googleSpeechRecognizer.stop();
                googleSpeechRecognizer.destroy();  // CRITICAL: Release microphone
                googleSpeechRecognizer = null;
                Log.i(TAG, "✅ Google Speech recognizer destroyed");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error destroying recognizer: " + e.getMessage());
            }
        }
        
        Log.i(TAG, "✅ All monitoring stopped");
    } catch (Exception e) {
        Log.e(TAG, "❌ Error stopping monitoring: " + e.getMessage());
    }
}

/**
 * Create and display foreground notification
 */
private void startForegroundWithNotification() {
    try {
        // Create notification channel first (Android 8.0+)
        createNotificationChannel();
        
        // Build notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScamShield Active")
            .setContentText("Protecting you from fraud calls...")
            .setSmallIcon(R.drawable.ic_notification)  // Must be white monochrome
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)  // Cannot be dismissed by user
            .build();
        
        // Start foreground (must happen within 5 seconds of onStartCommand)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        
        Log.i(TAG, "✅ Foreground service started with notification");
    } catch (Exception e) {
        Log.e(TAG, "❌ Error starting foreground: " + e.getMessage());
        stopSelf();
    }
}
```

**Key Design:**
- `onStartCommand()` checks SharedPreferences to honor toggle state
- `onDestroy()` calls `stopForeground(STOP_FOREGROUND_REMOVE)` to remove notification immediately
- `stopMonitoring()` calls `googleSpeechRecognizer.destroy()` to release microphone
- All handler tasks cleaned up in `onDestroy()`
- Uses `START_STICKY` so service restarts if killed (but respects toggle state)

### Comparison: RoleManager vs TelecomManager

| Feature | RoleManager (Android 10+) | TelecomManager (Android 7-9) |
|---------|---------------------------|------------------------------|
| **API Level** | Android 10 (Q, API 29+) | Android 7-9 (API 24-27) |
| **Method** | `createRequestRoleIntent()` | `ACTION_CHANGE_DEFAULT_DIALER` intent |
| **User Experience** | System dialog, modern UI | System chooser dialog |
| **Request Once** | Can request multiple times | Can request multiple times |
| **Non-blocking** | Yes, user can dismiss | Yes, user can skip |
| **Permission** | `android.permission.MANAGE_ONGOING_CALLS` (Android 11+) | No special permission |
| **Availability Check** | `isRoleAvailable()` required | Always available |
| **Success Indication** | `onActivityResult()` with `RESULT_OK` | Intent handled by system |

### DO's & DON'Ts for Default Dialer & Service Lifecycle

**DO:**
1. Register InCallService in AndroidManifest.xml with `android:permission="android.permission.BIND_INCALL_SERVICE"`
2. Add BIND_INCALL_SERVICE and MANAGE_ONGOING_CALLS permissions to manifest
3. Check Build.VERSION_CODES.Q before using RoleManager
4. Store PREF_DIALER_ROLE_REQUESTED to avoid showing request dialog repeatedly
5. Check SharedPreferences in onStartCommand() to respect toggle state
6. Call stopForeground(STOP_FOREGROUND_REMOVE) in onDestroy() to remove notification
7. Call speechRecognizer.destroy() to release microphone before service stops
8. Use startForegroundService() on Android O+, regular startService() on older versions
9. Log all service lifecycle events with emoji prefixes (✅, ❌, 🛑, ☎️)
10. Remove all handler callbacks in onDestroy() to prevent memory leaks
11. Check permission at runtime before initializing speech recognition
12. Use START_STICKY for service restart, but check SharedPreferences in onStartCommand()
13. Create NotificationChannel before building notification (Android 8.0+)
14. Use white monochrome icons (ic_notification.xml) for foreground service notifications
15. Test dialer role flow on both Android 10+ (RoleManager) and Android 7-9 (TelecomManager)

**DON'T:**
1. DON'T forget to export InCallService: `android:exported="true"`
2. DON'T assume RoleManager is available - always check API level first
3. DON'T call startForegroundService() on Android O without proper notification
4. DON'T block the main thread during service startup - use ExecutorService for heavy work
5. DON'T hardcode permission checks - always use ContextCompat.checkSelfPermission()
6. DON'T forget to call stopForeground() when service is destroyed
7. DON'T leave handler callbacks pending - always call removeCallbacks() in onDestroy()
8. DON'T assume speech recognizer.stop() releases microphone - must call destroy()
9. DON'T ignore onDestroy() - use it to cleanup all resources
10. DON'T use colored icons for foreground service notifications - must be pure white (#FFFFFF)
11. DON'T call stopSelf() in onStartCommand() if permissions missing without logging reason
12. DON'T forget to persist toggle state in SharedPreferences with key "scam_alerts_enabled"
13. DON'T assume notification icon resource exists - verify with try-catch
14. DON'T call requestDefaultDialerRole() every time app starts - track with SharedPreferences
15. DON'T ignore TelecomManager fallback for Android 7-9 devices - many devices still use older versions

### Testing Checklist for Default Dialer & Service Lifecycle

- [ ] Install app on Android 10+ device (API 29+)
- [ ] First app launch: verify "Request role" dialog appears (should appear only once)
- [ ] User selects "Yes" in dialog: verify app is set as default dialer
- [ ] Launch dialer app from home screen: verify ScamShield opens instead of stock dialer
- [ ] Incoming call notification: verify comes from ScamShield system integration
- [ ] Verify app appears in Settings > Apps > Default apps > Phone app
- [ ] Open Settings > Scam Detection toggle
- [ ] Toggle OFF: verify service stops immediately
- [ ] Toggle OFF: verify notification disappears within 2 seconds
- [ ] Toggle OFF: verify microphone is released (no audio glitches in other apps)
- [ ] Toggle ON: verify service starts immediately
- [ ] Toggle ON: verify persistent "ScamShield Active" notification appears
- [ ] Trigger incoming call: verify ScamMonitorService detects call and listens
- [ ] Close app while toggle is ON: verify service continues running (persistent)
- [ ] Reboot device while toggle is ON: verify service starts after reboot
- [ ] Test on Android 7-9 device (with TelecomManager fallback)
- [ ] Verify "Request role" uses TelecomManager on Android 7-9
- [ ] Check logcat for all lifecycle events: "✅ ScamMonitorService started successfully"
- [ ] Check logcat for cleanup: "✅ Notification removed via stopForeground()"
- [ ] Verify no ANR (Application Not Responding) during service startup/shutdown
- [ ] Test toggle ON/OFF 10 times rapidly: verify no crashes or orphaned services

### Troubleshooting Default Dialer & Service Lifecycle

**Problem:** "Request default dialer" dialog appears every app launch
- **Check 1:** Verify `PREF_DIALER_ROLE_REQUESTED` is saved: check SharedPreferences
- **Check 2:** Verify `editor.apply()` called (not just `put`)
- **Check 3:** Check Logcat for "Dialer role already requested"
- **Solution:** Ensure SharedPreferences.Editor.apply() is called immediately

**Problem:** Toggle OFF but notification still visible
- **Check 1:** Verify `stopForeground(STOP_FOREGROUND_REMOVE)` called in onDestroy()
- **Check 2:** Check if using old `stopForeground(true)` instead of REMOVE constant
- **Check 3:** Verify onDestroy() is actually being called: add log statement
- **Solution:** Use STOP_FOREGROUND_REMOVE constant (not just boolean true)

**Problem:** Microphone doesn't release after service stops
- **Check 1:** Verify `googleSpeechRecognizer.destroy()` called in stopMonitoring()
- **Check 2:** Verify `handler.removeCallbacks()` called to stop polling
- **Check 3:** Check for exception in stopMonitoring(): wrap in try-catch
- **Solution:** Ensure all recognizer references set to null after destroy()

**Problem:** Service doesn't start when toggle is turned ON
- **Check 1:** Verify `startForegroundService()` used on Android O+
- **Check 2:** Verify notification created before calling `startForeground()`
- **Check 3:** Check Logcat for permission errors: "RECORD_AUDIO permission missing"
- **Check 4:** Verify SharedPreferences value saved correctly
- **Solution:** Verify all prerequisites met before service startup

**Problem:** App crashes on default dialer role request
- **Check 1:** Verify RoleManager import: `import android.app.role.RoleManager`
- **Check 2:** Check Logcat for "Cannot find symbol" or "ClassNotFoundException"
- **Check 3:** Verify API level check: `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`
- **Solution:** Ensure proper imports and version checks in place

**Problem:** InCallService not recognized by Telecom framework
- **Check 1:** Verify AndroidManifest.xml declares service: `android:name="...ScamShieldInCallService"`
- **Check 2:** Verify intent-filter: `<action android:name="android.telecom.InCallService" />`
- **Check 3:** Verify `android:exported="true"` in service declaration
- **Check 4:** Verify `android:permission="android.permission.BIND_INCALL_SERVICE"`
- **Solution:** Check all manifest declaration requirements above

## In-App Real-Time Debug Logging System

### Overview
ScamShield now includes an in-app floating debug log window that displays real-time speech recognition events and service lifecycle logs without requiring Logcat access. This provides visibility into the app's internal processes for both end users and developers, helping diagnose why scam detection may appear to fail silently.

### Architecture Pattern

**Components:**
1. **DebugLogWindow class** - Manages floating TextView with timestamped log entries
2. **MainActivity integration** - Implements SpeechListener to receive debug events
3. **ScamMonitorService callbacks** - Sends debug events to active debug listeners
4. **SharedPreferences storage** - Persists debug log visibility state across app restarts

**Flow:**
```
ScamMonitorService lifecycle event
     ↓
ScamMonitorService.debugListener.onDebugLog("[timestamp] message")
     ↓
MainActivity.onDebugLog() received
     ↓
DebugLogWindow.logToScreen("message")
     ↓
Handler.post() to main thread
     ↓
Append to TextView with format: "[HH:mm:ss] [message]"
     ↓
Auto-scroll to latest entry
     ↓
Limit to 50 lines to prevent memory bloat
```

### Implementation Details

#### 1. DebugLogWindow Class
**Location:** `utils/DebugLogWindow.java` (200+ lines)

**Key Features:**
- Floating FrameLayout container (600dp height, bottom-positioned)
- Scrollable TextView with green (#00FF00) monospace text on dark background
- Timestamped entries: `[HH:mm:ss] message`
- Limits to 50 lines max (removes oldest entries when exceeded)
- Auto-scroll to bottom to show latest logs
- Visibility toggle (persisted in SharedPreferences with key: `debug_log_enabled`)

**Usage:**
```java
// In MainActivity.onCreate() after setContentView()
FrameLayout mainContainer = findViewById(R.id.main_container);
debugLogWindow = new DebugLogWindow(this);
debugLogWindow.initialize(mainContainer);
debugLogWindow.logToScreen("✅ Debug log initialized");

// From any thread (Handler.post() ensures main thread execution)
debugLogWindow.logToScreen("📢 Partial result: 'hello'");

// Toggle visibility (updates SharedPreferences)
debugLogWindow.toggleVisibility();

// Clear all entries
debugLogWindow.clear();

// Cleanup on destroy
debugLogWindow.destroy();
```

**Thread Safety:**
- All UI updates wrapped in `mainHandler.post()` for main thread safety
- Safe to call from background threads (speech recognition callbacks)

#### 2. MainActivity Integration
**Location:** `activities/MainActivity.java`

**Key Changes:**
- Implements `SpeechListener` interface
- Initializes `DebugLogWindow` in `onCreate()` after `setContentView()`
- Registers as debug listener in `onStart()`: `ScamMonitorService.setDebugListener(this)`
- Unregisters in `onStop()`: `ScamMonitorService.clearDebugListener()`
- Implements `onDebugLog()` callback to forward events to debug window
- Cleans up resources in `onDestroy()`

**Code Pattern:**
```java
@Override
public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    // Initialize debug log window
    FrameLayout mainContainer = findViewById(R.id.main_container);
    if (mainContainer != null) {
        debugLogWindow = new DebugLogWindow(this);
        debugLogWindow.initialize(mainContainer);
    }
}

@Override
public void onStart() {
    super.onStart();
    instance = this;
    // Register as debug listener
    ScamMonitorService.setDebugListener(this);
}

@Override
public void onStop() {
    super.onStop();
    // Unregister debug listener
    ScamMonitorService.clearDebugListener();
    instance = null;
}

@Override
public void onDebugLog(String debugMessage) {
    if (debugLogWindow != null) {
        debugLogWindow.logToScreen(debugMessage);
    }
}
```

#### 3. ScamMonitorService Debug Logging
**Location:** `services/ScamMonitorService.java`

**Enhanced Methods:**
- `onStartCommand()` - Logs service startup and SharedPreferences check
- `startForegroundWithNotification()` - Logs notification channel creation, icon verification, startForeground call
- `initializeSpeechRecognition()` - Logs permission check, recognizer initialization, errors
- `onSpeechRecognized()` - Logs keyword detection and alerts (inherited from GoogleSpeechRecognizer)
- `showScamAlert()` - Logs alert display and fallback triggers
- `onDestroy()` - Logs foreground removal and resource cleanup

**Pattern:**
```java
// In every critical method
if (debugListener != null) {
    try {
        debugListener.onDebugLog("✅ Event occurred");
    } catch (Exception e) {
        Log.d(TAG, "Debug listener callback failed");
    }
}
```

**Emoji Prefixes (Color-coded):**
- ✅ - Success events (initialization, listeners registered, resources created)
- ❌ - Errors (permissions missing, recognizer failed, alerts failed)
- 🎤 - Speech recognition events (ready, speech detected, ended)
- 📢 - Recognized text events (partial results, final results)
- 🔄 - State transitions (auto-restarting, retrying, reconnecting)
- 🚨 - Scam detections (keyword matched, alert triggered)
- 🛑 - Stopping/cleanup events (service stopped, resources freed)
- ⚠️ - Warnings (permission denied, retrying after error)

### Debug Log UI

**Location in App:**
- Appears as floating panel at bottom of MainActivity
- Semi-transparent dark background (#1A121212)
- Bright green text (#00FF00) for visibility
- Monospace font for aligned columns
- Scrollable with auto-scroll to latest entries
- Default: hidden (toggle via Settings or onDebugLog visibility setting)

**Layout Dimensions:**
- Width: Match parent (full screen width)
- Height: 600dp (half-screen)
- Position: Bottom of screen (Gravity.BOTTOM)
- Padding: 16dp all sides

**Styling:**
```xml
<!-- Floating debug container -->
<FrameLayout
    android:id="@+id/debug_container"
    android:layout_width="match_parent"
    android:layout_height="600dp"
    android:layout_alignParentBottom="true"
    android:background="#1A121212"
    android:visibility="gone" />

<!-- Scrollable text view inside -->
<ScrollView>
    <TextView
        android:textColor="#00FF00"
        android:textSize="10sp"
        android:typeface="monospace"
        android:padding="16dp"
        android:scrollbars="vertical" />
</ScrollView>
```

### Enabling/Disabling Debug Log

**Method 1: SharedPreferences (Programmatic)**
```java
SharedPreferences prefs = getSharedPreferences("ScamShieldPrefs", MODE_PRIVATE);
prefs.edit().putBoolean("debug_log_enabled", true).apply();
// Toggle: debugLogWindow.toggleVisibility()
```

**Method 2: Developer Settings (Future)**
- Add toggle in SettingsFragment (similar to dark mode toggle)
- Toggle calls `debugLogWindow.toggleVisibility()`
- Preference persisted with key: `debug_log_enabled`

**Visibility State:**
- Persisted in SharedPreferences across app restarts
- Default: OFF (hidden) for production builds
- Users can enable for troubleshooting

### Common Debug Log Scenarios

**Scenario 1: Speech Recognition Starting**
```
[14:23:45] ✅ MainActivity initialized
[14:23:46] ✅ Debug listener registered
[14:23:47] 🔧 Creating foreground notification...
[14:23:47] ✅ Foreground notification created
[14:23:48] ✅ RECORD_AUDIO permission verified
[14:23:48] 🎤 Initializing Google Speech Recognizer...
[14:23:49] ✅ Speech Recognizer initialized and listening
```

**Scenario 2: Partial Speech Results During Call**
```
[14:25:10] 🎤 Ready for speech input
[14:25:12] 🔄 Partial result: 'verify'
[14:25:13] 🔄 Partial result: 'verify your'
[14:25:14] 🔄 Partial result: 'verify your otp'
[14:25:15] ✅ Final result: 'verify your OTP'
[14:25:15] 🚨 SCAM KEYWORD DETECTED: 'otp' in 'verify your OTP'
[14:25:15] ✅ Alert shown for: otp
```

**Scenario 3: Error Recovery (Auto-restart)**
```
[14:26:30] ❌ Speech error: [6] No speech input
[14:26:30] 🔄 Transient error detected, auto-restarting...
[14:26:31] 🔄 Auto-restarting listening after delay...
[14:26:31] 🎤 Ready for speech input
```

**Scenario 4: Service Stopping (Toggle OFF)**
```
[14:27:45] ⏹️ Scam detection disabled by user, stopping service
[14:27:45] 🛑 Stopping monitoring...
[14:27:45] ✅ Google Speech Recognizer stopped and destroyed
[14:27:45] ✅ All monitoring stopped
[14:27:45] ✅ Foreground notification removed
```

### DO's & DON'Ts for Debug Logging

**DO:**
1. Call `debugListener.onDebugLog()` for all critical lifecycle events
2. Wrap debug listener calls in try-catch to prevent crashes
3. Use emoji prefixes consistently: ✅ ❌ 🎤 📢 🔄 🚨 🛑 ⚠️
4. Include context in messages: e.g., "❌ Permission missing: RECORD_AUDIO"
5. Use `handler.post()` for all UI updates in DebugLogWindow
6. Check if debugListener is null before calling (it's null when MainActivity is destroyed)
7. Initialize DebugLogWindow after `setContentView()` to ensure main_container exists
8. Persist visibility state in SharedPreferences with key: `debug_log_enabled`
9. Limit log entries to 50 lines to prevent memory bloat
10. Include timestamps in log format: `[HH:mm:ss] message`
11. Clean up resources in `onDestroy()`: `debugLogWindow.destroy()`
12. Test debug log with verbose logging: set `debug_log_enabled=true` in SharedPreferences
13. Verify logs appear in real-time during incoming calls
14. Use monospace font for visual alignment of log columns
15. Use green text (#00FF00) on dark background for visibility

**DON'T:**
1. DON'T call `debugLogWindow.logToScreen()` directly from service threads (use handler)
2. DON'T assume debugListener is always registered (check null)
3. DON'T log sensitive information (audio content, private numbers)
4. DON'T use heavy logging in tight loops (causes performance issues)
5. DON'T forget to unregister debug listener in `onStop()` or `onDestroy()`
6. DON'T initialize DebugLogWindow before `setContentView()` (causes NPE)
7. DON'T use colored text (green is default, don't add colors for individual messages)
8. DON'T leave DebugLogWindow visible in production (toggle disabled by default)
9. DON'T hardcode visibility state (use SharedPreferences key: `debug_log_enabled`)
10. DON'T assume main_container exists (check for null before initializing)
11. DON'T log same event multiple times (once per lifecycle event is sufficient)
12. DON'T forget to persist visibility toggle in SharedPreferences
13. DON'T use Log.d() for debug events visible to users (use onDebugLog() instead)
14. DON'T append to TextViews from background threads (always use handler.post())
15. DON'T exceed 50 log lines (DebugLogWindow auto-removes oldest entries)

### Testing Checklist for Debug Logging

- [ ] Install app on Android device
- [ ] Open MainActivity and verify debug log window is hidden by default
- [ ] Enable debug log via SharedPreferences: `debug_log_enabled = true`
- [ ] Reopen app and verify debug log window appears at bottom
- [ ] Verify "✅ MainActivity initialized" message appears in log
- [ ] Verify "✅ Debug listener registered" message in onStart()
- [ ] Trigger incoming call with scam keyword
- [ ] Verify speech recognition events show in real-time:
  - "🎤 Ready for speech input"
  - "📢 Partial result: [text]"
  - "✅ Final result: [text]"
- [ ] Verify scam detection appears: "🚨 SCAM KEYWORD DETECTED: [keyword]"
- [ ] Verify alert shown message: "✅ Alert shown for: [keyword]"
- [ ] Toggle scam detection OFF in Settings
- [ ] Verify "⏹️ Scam detection disabled" message
- [ ] Verify "🛑 Stopping monitoring..." and cleanup messages appear
- [ ] Disable debug log toggle
- [ ] Reopen app and verify debug log window is hidden
- [ ] Verify visibility state persists across app restarts
- [ ] Test debug log scroll behavior with 50+ entries
- [ ] Verify oldest entries removed when limit exceeded
- [ ] Test error recovery: disconnect WiFi during call
- [ ] Verify "❌ Error" and "🔄 Auto-restarting" messages
- [ ] Test with slow network (simulate lag in speech recognition)
- [ ] Verify thread safety: no crashes from background thread updates
- [ ] Check for memory leaks: monitor RAM while running for extended time
- [ ] Verify fallback alert triggered in debug log if ScamAlertActivity fails

### Troubleshooting Debug Logging Issues

**Problem:** Debug log window doesn't appear
- **Check 1:** Verify `debug_log_enabled = true` in SharedPreferences
- **Check 2:** Verify `main_container` exists in activity_main.xml
- **Check 3:** Verify `DebugLogWindow.initialize()` called after `setContentView()`
- **Solution:** Check SharedPreferences and ensure main_container layout element exists

**Problem:** Debug messages don't appear in real-time
- **Check 1:** Verify `debugListener` is not null in ScamMonitorService
- **Check 2:** Verify MainActivity is in foreground (onStart() called)
- **Check 3:** Check if exception thrown in onDebugLog() callback
- **Solution:** Verify MainActivity is active and debug listener is registered

**Problem:** App crashes when debug log appears
- **Check 1:** Verify all UI updates wrapped in `handler.post()`
- **Check 2:** Check Logcat for "Cannot find symbol" or "Null pointer" in debug code
- **Check 3:** Verify main_container is FrameLayout (not other ViewGroup)
- **Solution:** Ensure handler.post() used for all UI updates, verify layout type

**Problem:** Debug log text overflows or doesn't auto-scroll
- **Check 1:** Verify ScrollView wrapping TextView in DebugLogWindow
- **Check 2:** Verify `scrollbars="vertical"` set on TextView
- **Check 3:** Check if `fullScroll(View.FOCUS_DOWN)` called after append
- **Solution:** Verify ScrollView structure and auto-scroll logic

**Problem:** Visibility state not persisting across restarts
- **Check 1:** Verify SharedPreferences key is `debug_log_enabled`
- **Check 2:** Verify `editor.apply()` called (not just `put`)
- **Check 3:** Check for SharedPreferences mode (MODE_PRIVATE correct)
- **Solution:** Ensure SharedPreferences.Editor.apply() is called immediately

## WindowManager Implementation for Debug Window (Moto/Redmi Fix)

### Overview
ScamShield's debug log window now uses Android's `WindowManager` API instead of FrameLayout approach, providing compatibility with strict overlay restrictions on Moto, Redmi, and other OEM devices. This fix resolves the "invisible debug window" issue on devices with SYSTEM_ALERT_WINDOW restrictions.

### Problem Analysis

**Root Cause:** Original DebugLogWindow implementation used FrameLayout added to MainActivity's view hierarchy. On Moto/Redmi devices with strict overlay policies, this view could be:
1. Hidden behind other UI layers (status bar, navigation bar)
2. Clipped by parent container bounds
3. Prevented from rendering by system window manager restrictions
4. Not properly positioned due to activity layout constraints

**Solution:** Use Android's WindowManager directly to bypass activity layout constraints and render window on top of all app UI.

### Architecture Pattern

**DebugLogWindow.java (WindowManager approach):**
```java
private WindowManager windowManager;
private WindowManager.LayoutParams windowParams;
private boolean isWindowAdded = false;

public DebugLogWindow(AppCompatActivity activity) {
    this.activity = activity;
    this.windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
}

public void initialize(ViewGroup parentContainer) {
    // Create UI components
    debugContainer = new FrameLayout(activity);
    debugScroll = new ScrollView(activity);
    debugLogTextView = new TextView(activity);
    
    // Setup WindowManager parameters
    setupWindowManagerParams();
    
    // Add to WindowManager (bypasses activity layout)
    windowManager.addView(debugContainer, windowParams);
    isWindowAdded = true;
}

private void setupWindowManagerParams() {
    windowParams = new WindowManager.LayoutParams();
    
    // Choose type based on Android version
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    } else {
        windowParams.type = WindowManager.LayoutParams.TYPE_PHONE;
    }
    
    // Critical flags
    windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE   // No touch input
                       | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN; // Full coords
    
    windowParams.format = android.graphics.PixelFormat.TRANSLUCENT;
    windowParams.width = 800;
    windowParams.height = 600;
    windowParams.x = 0;
    windowParams.y = 0;
    windowParams.gravity = Gravity.BOTTOM | Gravity.START;
}

public void destroy() {
    if (isWindowAdded && debugContainer != null) {
        windowManager.removeView(debugContainer);
        isWindowAdded = false;
    }
}
```

### Window Type Selection (Critical)

| Android Version | Type | Method | Use Case |
|-----------------|------|--------|----------|
| **8.0+** (O) | `TYPE_APPLICATION_OVERLAY` | Preferred | Modern Android, apps exempt from restrictions |
| **7.0-7.1** (N) | `TYPE_APPLICATION_OVERLAY` | Best effort | Try overlay, fallback to TYPE_PHONE |
| **6.0 and older** | `TYPE_PHONE` | Legacy | Older devices, simpler permissions |

**DO NOT use:**
- `TYPE_SYSTEM_OVERLAY` - Requires SYSTEM_ALERT_WINDOW (causes install failures on Moto)
- `TYPE_SYSTEM_ALERT` - Deprecated, causes crashes on modern Android

### Flag Breakdown

**FLAG_NOT_FOCUSABLE** (CRITICAL)
```java
windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
```
- Window does NOT receive focus (app can still handle touches)
- Window does NOT consume touch events (touches pass through to underlying window)
- Required for overlay to not interfere with app navigation
- **Why it works on Moto:** Declares window as pure overlay, not interactive layer

**FLAG_LAYOUT_IN_SCREEN** (CRITICAL)
```java
windowParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
```
- Window measured and laid out in full screen coordinates
- Position not relative to status bar or other system UI
- Ensures consistent positioning across all devices
- **Why it works on Moto:** Bypasses device-specific layout calculations

**Optional: FLAG_NOT_TOUCHABLE**
```java
windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
```
- Window never receives touch events at all
- Useful if you want pure overlay behavior (can't scroll)
- Currently NOT used in ScamShield (we want scrollable text)

### Window Format

```java
windowParams.format = android.graphics.PixelFormat.TRANSLUCENT;
```
- Allows semi-transparent background (#1A121212 with alpha)
- Supports 32-bit color with transparency
- No additional overhead compared to opaque

### Manual Refresh Button

**New Feature:** Button in MainActivity allows users to manually trigger debug window re-initialization if it fails to load.

**Implementation** (MainActivity.java):
```java
private void addDebugWindowRefreshButton(FrameLayout mainContainer) {
    try {
        android.widget.Button refreshBtn = new android.widget.Button(this);
        refreshBtn.setText("🔄");  // Refresh emoji
        refreshBtn.setAlpha(0.7f);  // Semi-transparent
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP | android.view.Gravity.END
        );
        params.setMargins(0, 16, 16, 0);  // Top-right corner
        
        refreshBtn.setOnClickListener(v -> {
            if (debugLogWindow != null) {
                debugLogWindow.initialize(mainContainer);  // Re-initialize
                Log.i(TAG, "✅ Debug window re-initialized via refresh button");
            }
        });
        
        mainContainer.addView(refreshBtn, params);
    } catch (Exception e) {
        Log.e(TAG, "Error adding refresh button: " + e.getMessage());
    }
}
```

**Usage:**
- Visible in top-right corner (small, semi-transparent emoji)
- Click to force re-initialization of debug window
- Useful for development and troubleshooting
- Prevents "permanently invisible window" scenario

### DO's & DON'Ts for WindowManager Implementation

**DO:**
1. Check Build.VERSION_CODES to select correct window type (O for overlay, fallback to PHONE)
2. Use FLAG_NOT_FOCUSABLE + FLAG_LAYOUT_IN_SCREEN (required pair)
3. Set format to TRANSLUCENT for transparency support
4. Store isWindowAdded flag to prevent duplicate adds
5. Call removeView() in destroy() before cleanup
6. Wrap initialize() to remove old window before adding new one (re-initialization safe)
7. Use WindowManager from Context.getSystemService()
8. Position window with gravity constants (BOTTOM | START)
9. Set explicit dimensions (width, height) instead of MATCH_PARENT
10. Log window type selection for debugging device compatibility
11. Verify windowManager is not null before calling methods
12. Test on both Moto and stock Android devices
13. Use handler.post() for all UI updates (thread safety)
14. Provide manual refresh button as fallback
15. Document FLAG usage in code for future maintainers

**DON'T:**
1. DON'T use TYPE_SYSTEM_OVERLAY or TYPE_SYSTEM_ALERT (deprecated, causes crashes)
2. DON'T use SYSTEM_ALERT_WINDOW permission without careful consideration
3. DON'T forget FLAG_NOT_FOCUSABLE (window will steal focus from app)
4. DON'T forget FLAG_LAYOUT_IN_SCREEN (positioning will be wrong on some devices)
5. DON'T call addView() multiple times without removeView() first (causes crash)
6. DON'T use MATCH_PARENT for window width/height (causes full-screen window)
7. DON'T assume windowManager is available (always check for null)
8. DON'T forget to store isWindowAdded flag (prevents tracking of window state)
9. DON'T ignore removeView() exceptions in destroy() (can cause resource leaks)
10. DON'T use position relative to status bar (always use full screen coords)
11. DON'T forget to handle removal in onDestroy() (memory leaks without cleanup)
12. DON'T hardcode window dimensions (consider screen density)
13. DON'T assume TYPE_APPLICATION_OVERLAY works on all devices (need fallback)
14. DON'T update window after it's been destroyed (check isWindowAdded first)
15. DON'T use float coordinates for position (use int pixel values only)

### Troubleshooting WindowManager Issues

**Problem:** "BadTokenException: Unable to add window"
- **Check 1:** Verify windowManager not null: `if (windowManager != null)`
- **Check 2:** Verify window hasn't been added twice: check `isWindowAdded` flag
- **Check 3:** Verify activity is still valid (not destroyed)
- **Check 4:** Check logcat for "parameter is not an Activity"
- **Solution:** Ensure removeView() called before addView(), use isWindowAdded flag

**Problem:** Window appears below status bar or nav bar
- **Check 1:** Verify FLAG_LAYOUT_IN_SCREEN is set
- **Check 2:** Verify gravity is BOTTOM | START (not CENTER)
- **Check 3:** Check if device has notch (may affect coordinates)
- **Solution:** Use FLAG_LAYOUT_IN_SCREEN and test on target device

**Problem:** Window invisible on Moto/Redmi device
- **Check 1:** Verify using TYPE_APPLICATION_OVERLAY (not TYPE_SYSTEM_OVERLAY)
- **Check 2:** Verify SYSTEM_ALERT_WINDOW permission not required
- **Check 3:** Check device AOSP version (some Moto ROM versions have restrictions)
- **Check 4:** Verify FLAG_NOT_FOCUSABLE is set (overlay mode)
- **Solution:** Force TYPE_PHONE if overlay not available, provide manual refresh button

**Problem:** Window consumes all touch input (blocks app interaction)
- **Check 1:** Verify FLAG_NOT_FOCUSABLE is set
- **Check 2:** Verify FLAG_NOT_TOUCHABLE NOT set (unless desired)
- **Check 3:** Check if window covers entire screen (set proper dimensions)
- **Solution:** Ensure FLAG_NOT_FOCUSABLE set, verify width/height not MATCH_PARENT

**Problem:** Text doesn't scroll or window clips content
- **Check 1:** Verify ScrollView added to FrameLayout
- **Check 2:** Verify TextView has WRAP_CONTENT height inside ScrollView
- **Check 3:** Check if window height too small (600dp minimum recommended)
- **Solution:** Verify layout hierarchy: Container -> ScrollView -> TextView

**Problem:** Manual refresh button doesn't work
- **Check 1:** Verify debugLogWindow not null before calling initialize()
- **Check 2:** Verify initialize() removes old window before adding new one
- **Check 3:** Check if exception thrown in initialize() callback
- **Check 4:** Verify button listener properly captures mainContainer
- **Solution:** Add null check and exception handling in button click listener

### Testing Checklist for WindowManager Implementation

- [ ] Install app on stock Android device (Pixel, Nexus)
- [ ] Verify debug log window appears at bottom-left
- [ ] Verify window doesn't block app touch input
- [ ] Toggle debug log visibility: appears/disappears correctly
- [ ] Scroll debug log: text scrolls smoothly
- [ ] Enable debug log and restart app: window appears automatically
- [ ] Verify window positioning is correct (no overlap with nav bar)
- [ ] Test on Moto G device (AOSP-based ROM)
- [ ] Test on Redmi device (MIUI-based ROM)
- [ ] Verify debug window appears on Moto/Redmi (key test for this fix)
- [ ] Test manual refresh button: click 🔄 button
- [ ] Verify window re-initializes after refresh button click
- [ ] Test with screen rotation: window should persist
- [ ] Test app backgrounding: window removed on app destroy
- [ ] Check Logcat for "BadTokenException" errors (none should appear)
- [ ] Verify "TYPE_APPLICATION_OVERLAY" logged on Android 8.0+
- [ ] Verify "TYPE_PHONE" logged on Android 6.0-7.x fallback
- [ ] Test with debug log disabled: no window should appear
- [ ] Enable debug log in Settings: window should appear immediately
- [ ] Verify window survives activity recreation (rotate screen)
- [ ] Check memory usage with debug log open (should be <5MB)

### Benefits Over Previous FrameLayout Approach

| Feature | FrameLayout (Old) | WindowManager (New) |
|---------|------------------|-------------------|
| **Moto/Redmi** | ❌ Invisible | ✅ Works |
| **Redmi** | ❌ Invisible | ✅ Works |
| **OEM Restrictions** | ❌ Blocked by layout | ✅ Bypasses hierarchy |
| **Layout Dependency** | ❌ Requires parent | ✅ Independent window |
| **Status Bar Coverage** | ❌ Clipped | ✅ Full coords |
| **Performance** | ✅ Lightweight | ✅ Lightweight |
| **Setup Complexity** | ✅ Simple | ⚠️ Moderate |
| **Error Handling** | ❌ Limited | ✅ Comprehensive |
| **Device Compatibility** | 🟡 ~80% | ✅ ~99% |

## Error Code Handling for Speech Recognition (Beep Sound Fix)

### Problem Analysis

**Root Cause:** When Google Speech Recognizer encounters `ERROR_NO_MATCH` (code 7) or `ERROR_RECOGNIZER_BUSY` (code 8), it plays the "tu tu tu" beep sound. The original implementation waited 1-3 seconds before restarting listening. During this 1-3 second gap, the user's voice after the beep was lost.

**User Experience Impact:**
1. Device beeps ("tu tu tu")
2. User thinks app is now listening and speaks
3. But app is actually waiting 1-3 seconds to restart
4. User's voice is missed
5. User thinks app is broken

**Solution:** Detect NO_MATCH and BUSY errors specifically and restart immediately (500ms delay) instead of waiting 1-3 seconds.

### Implementation Details

**GoogleSpeechRecognizer.java (onError method):**
```java
@Override
public void onError(int errorCode) {
    String errorMessage = getErrorString(errorCode);
    Log.e(TAG, "❌ Speech recognition error: [" + errorCode + "] " + errorMessage);
    
    isListening = false;
    
    // SPECIAL HANDLING: NO_MATCH (7) and RECOGNIZER_BUSY (8)
    // These occur with beep sound, need immediate restart
    if (errorCode == SpeechRecognizer.ERROR_NO_MATCH 
            || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
        if (autoRestartEnabled) {
            Log.i(TAG, "🔄 Beep heard (NO_MATCH/BUSY), restarting immediately (500ms)...");
            if (listener != null) {
                listener.onDebugLog("🔄 Beep heard, listening resumed in 500ms...");
            }
            // Immediate restart - 500ms instead of 1000ms default
            handler.postDelayed(this::autoRestartListening, 500);
        }
        return; // Don't process further
    }
    
    // Standard handling for other errors...
    if (autoRestartEnabled && isTransientError(errorCode)) {
        Log.i(TAG, "🔄 Transient error detected, auto-restarting...");
        autoRestartListening();
    } else if (autoRestartEnabled) {
        Log.w(TAG, "⚠️ Non-transient error, retrying after delay...");
        handler.postDelayed(this::autoRestartListening, 3000);
    }
}
```

### Error Code Reference

| Code | Name | Cause | Beep Sound | Action |
|------|------|-------|-----------|--------|
| 7 | ERROR_NO_MATCH | No speech detected in audio buffer | ✅ Yes | **500ms restart** |
| 8 | ERROR_RECOGNIZER_BUSY | Recognizer already processing | ✅ Yes | **500ms restart** |
| 4 | ERROR_AUDIO | Microphone error | ❌ No | 1000ms restart |
| 6 | ERROR_SPEECH_TIMEOUT | User silent > timeout | ❌ No | 1000ms restart |
| 2 | ERROR_NETWORK_TIMEOUT | Network delay | ❌ No | 1000ms restart |
| Others | Permanent errors | Critical failure | ❌ No | 3000ms retry |

### Restart Delay Strategy

**Why Different Delays?**
- **500ms (NO_MATCH/BUSY):** Beep is acoustic signal that listening is ready. User expects immediate response.
- **1000ms (transient errors):** Standard timeout before retry. Gives OS time to recover.
- **3000ms (permanent errors):** Longer timeout for critical errors (permissions, network).

**Timing Calculation:**
```
Beep sound plays: 0ms
ErrorCode 7/8 received: ~100ms
Delay starts: 100ms
Restart listening: 100ms + 500ms = 600ms total
User speaks: ~700ms+
App captures voice: ✅ Success
```

Old approach with 1000ms delay:
```
Beep sound plays: 0ms
ErrorCode 7/8 received: ~100ms
Delay starts: 100ms
Restart listening: 100ms + 1000ms = 1100ms total
User speaks at 800ms: ❌ Missed (app not listening yet)
App restarts: 1100ms (too late)
```

### DO's & DON'Ts for Error Code Handling

**DO:**
1. Check for ERROR_NO_MATCH (7) and ERROR_RECOGNIZER_BUSY (8) specifically
2. Use 500ms restart delay for these "beep" errors (not 1000ms)
3. Log "🔄 Beep heard" message with emoji for quick scanning
4. Return early after handling NO_MATCH/BUSY (don't process as normal errors)
5. Use handler.postDelayed() for all restart delays (never Thread.sleep)
6. Log error code number for debugging: [7] or [8]
7. Provide debug log message for in-app visibility
8. Test with actual voice input after beep sound
9. Verify restart timing with debug log timestamps
10. Document error code meanings in comments
11. Keep beep-specific delay (500ms) separate from standard delay (1000ms)
12. Handle both error codes 7 and 8 identically
13. Check isListening flag before restarting
14. Verify autoRestartEnabled flag is true
15. Test on devices with slow microphone drivers

**DON'T:**
1. DON'T wait 1-3 seconds for NO_MATCH/BUSY (user voices get missed)
2. DON'T treat ERROR_NO_MATCH same as other errors (it's acoustic beep feedback)
3. DON'T ignore ERROR_RECOGNIZER_BUSY (frequent on low-end devices)
4. DON'T forget the 500ms restart delay constant (document it clearly)
5. DON'T use Thread.sleep() for delays (blocks main thread)
6. DON'T assume beep is always present (some devices may skip it)
7. DON'T hardcode delay value in multiple places (use constant)
8. DON'T process beep errors in isTransientError() path (handle first)
9. DON'T log without emoji prefix (makes scanning logs harder)
10. DON'T forget to call listener.onDebugLog() for visibility
11. DON'T test on silent phone (turn on volume to hear beep)
12. DON'T assume error order (process 7 and 8 before checking transient)
13. DON'T change 500ms delay without thorough user testing
14. DON'T forget isListening = false (prevents duplicate startListening calls)
15. DON'T skip error logging (need details for troubleshooting)

### Testing Checklist for Beep Sound Fix

- [ ] Install app on device with volume on
- [ ] Trigger incoming call
- [ ] Listen for "tu tu tu" beep sound
- [ ] Beep should be audible (not silent)
- [ ] Within 500-600ms after beep, device should accept speech input
- [ ] Speak a sentence IMMEDIATELY after beep (within 1 second)
- [ ] Verify app detects your speech (Toast: "📢 Heard: [word]")
- [ ] Verify debug log shows "🔄 Beep heard, listening resumed in 500ms..."
- [ ] Verify final result appears: "✅ Final: [your sentence]"
- [ ] Test 5 times with different sentences
- [ ] Verify at least 4 out of 5 times app captures all words
- [ ] Test on low-end device (may have slower microphone driver)
- [ ] Enable debug log for timestamp visibility
- [ ] Verify error code [7] or [8] shown in logs
- [ ] Verify beep occurs every ~2 seconds during listening
- [ ] Check device microphone is not blocked (no case obstruction)
- [ ] Test with noise in background (ambient noise handling)
- [ ] Test with multiple consecutive calls (restart behavior)
- [ ] Verify no "tu tu tu" after first successful recognition
- [ ] Check Logcat for proper error code logging

### Troubleshooting Beep Sound Issues

**Problem:** Beep sound doesn't play
- **Check 1:** Verify device volume is ON (not muted)
- **Check 2:** Verify permission RECORD_AUDIO is granted
- **Check 3:** Check if microphone is physically blocked
- **Check 4:** Verify speech recognizer initialized: "✅ Google On-Device Speech Recognizer created"
- **Solution:** Check volume, grant permission, unblock microphone

**Problem:** App restarts listening too slowly after beep
- **Check 1:** Verify 500ms restart delay used for NO_MATCH/BUSY
- **Check 2:** Check if exception in autoRestartListening() (causing longer delay)
- **Check 3:** Verify handler not blocked by other tasks
- **Check 4:** Check Logcat for "🔄 Beep heard, listening resumed in 500ms..."
- **Solution:** Verify error code handling, check for exceptions

**Problem:** Voice still not captured after beep
- **Check 1:** Verify microphone not muted (not in "call audio" mode)
- **Check 2:** Check if speech recognition timing out before user speaks
- **Check 3:** Verify beep is NOT the end-of-speech indicator (some devices play beep at end)
- **Check 4:** Check app permission level on device (restricted on some Moto ROM versions)
- **Solution:** Test on different device, check microphone permissions in Settings

**Problem:** Too many ERROR_NO_MATCH (beeping constantly)
- **Check 1:** Verify device is in actual phone call (not just app open)
- **Check 2:** Check if microphone capturing background noise as speech
- **Check 3:** Verify speech recognition timeout set correctly
- **Solution:** Test with actual scam keyword phrase, adjust timeout if needed

---

## Crash Safety Fixes for WindowManager and Speech Recognition (Moto/Redmi)

### Overview
ScamShield crashed with "tu tu tu" beep sound followed by immediate close on Moto/Redmi devices due to:
1. **NullPointerException** in WindowManager.addView() without null checks
2. **Unsafe Activity Context** in WindowManager causing lifecycle crashes
3. **Race Condition** between WindowManager initialization and SpeechRecognizer startup
4. **Double Initialization** of SpeechRecognizer without proper cleanup
5. **Missing Hardware Acceleration** causing overlay rendering failures
6. **Silent Crashes** with no user feedback about the actual error

### Root Causes Analysis (Based on AGENTS.md DO's & DON'Ts)

**AGENTS.md Line 2821 (DON'T):** "DON'T assume windowManager is available (always check for null)"
- **Problem:** Original code called `windowManager.addView()` without null check
- **Impact:** NullPointerException on devices with restricted WindowManager access
- **Solution:** Added explicit `if (windowManager == null) return` check before initialization

**AGENTS.md Line 2819 (DON'T):** "DON'T call addView() multiple times without removeView() first (causes crash)"
- **Problem:** Re-initialization attempted to add window without removing old one
- **Impact:** BadTokenException on Moto/Redmi devices with strict view tracking
- **Solution:** Check `isWindowAdded` flag and removeView() before adding new window

**AGENTS.md Line 2804 (DO):** "Use WindowManager from Context.getSystemService()"
- **Problem:** Used Activity Context instead of Application Context
- **Impact:** Memory leaks and crashes when Activity destroyed while WindowManager still active
- **Solution:** Changed to `activity.getApplicationContext().getSystemService()`

**AGENTS.md Line 2813 (DO):** "Use handler.post() for all UI updates (thread safety)"
- **Problem:** SpeechRecognizer started immediately in onCreate, conflicting with WindowManager
- **Impact:** Race condition causing resource conflicts on slow devices
- **Solution:** Added 1-second delay using `handler.postDelayed(this::start, 1000)`

### Implementation Details

#### Fix 1: Safe WindowManager Access in DebugLogWindow.java

**Change 1: Use Application Context**
```java
public DebugLogWindow(AppCompatActivity activity) {
    this.activity = activity;
    // CRITICAL: Use Application Context for WindowManager to survive Activity lifecycle
    this.windowManager = (WindowManager) activity.getApplicationContext()
        .getSystemService(Context.WINDOW_SERVICE);
    
    if (windowManager == null) {
        Log.e(TAG, "❌ CRITICAL: WindowManager is null - overlay will not work");
    }
}
```

**Benefit:** WindowManager survives Activity destruction, preventing memory leaks and crashes

**Change 2: Comprehensive Null & Safety Checks**
```java
public void initialize(ViewGroup parentContainer) {
    // SAFETY CHECK 1: Verify windowManager is available
    if (windowManager == null) {
        Log.e(TAG, "❌ ERROR: WindowManager is null");
        return;
    }
    
    // SAFETY CHECK 2: Verify activity context is valid
    if (activity == null || activity.isDestroyed()) {
        Log.e(TAG, "❌ ERROR: Activity is destroyed");
        return;
    }
    
    // ... rest of initialization
}
```

**Benefit:** Prevents NullPointerException and IllegalStateException crashes

**Change 3: Try-Catch Wrapper for addView()**
```java
try {
    Log.d(TAG, "🔧 About to add window (type=" + windowParams.type + ")");
    windowManager.addView(debugContainer, windowParams);
    isWindowAdded = true;
} catch (WindowManager.BadTokenException e) {
    Log.e(TAG, "❌ BadTokenException - invalid token: " + e.getMessage());
    isWindowAdded = false;
    return;
} catch (IllegalArgumentException e) {
    Log.e(TAG, "❌ IllegalArgumentException - invalid params: " + e.getMessage());
    isWindowAdded = false;
    return;
} catch (Exception e) {
    Log.e(TAG, "❌ Unexpected: " + e.getClass().getSimpleName() 
            + " - " + e.getMessage());
    isWindowAdded = false;
    return;
}
```

**Benefit:** Catches all possible WindowManager exceptions with detailed logging for debugging

**Change 4: Proper Window Removal in destroy()**
```java
public void destroy() {
    if (isWindowAdded && debugContainer != null && windowManager != null) {
        try {
            windowManager.removeView(debugContainer);
            isWindowAdded = false;
        } catch (IllegalArgumentException e) {
            // Expected if already removed
            Log.w(TAG, "Window already removed: " + e.getMessage());
            isWindowAdded = false;
        } catch (Exception e) {
            Log.e(TAG, "Error removing window: " + e.getMessage());
            isWindowAdded = false;
        }
    }
    // Cleanup references
    debugLogTextView = null;
    debugScroll = null;
    debugContainer = null;
    windowParams = null;
}
```

**Benefit:** Handles all removal exceptions to prevent memory leaks

#### Fix 2: Delayed SpeechRecognizer Start in GoogleSpeechRecognizer.java

**Change: Add 1-second Delay**
```java
@Override
public void start() {
    if (speechRecognizer == null) {
        Log.e(TAG, "❌ Speech recognizer not initialized");
        return;
    }
    
    if (isListening) {
        Log.w(TAG, "⚠️ Already listening");
        return;
    }
    
    // SAFETY: Add 1-second delay before listening
    // Prevents race condition with WindowManager initialization
    Log.d(TAG, "⏳ Delaying speech start by 1 second (crash safety)...");
    handler.postDelayed(() -> {
        if (speechRecognizer == null) {
            Log.e(TAG, "❌ Recognizer became null during delay");
            return;
        }
        
        try {
            isListening = true;
            speechRecognizer.startListening(recognizerIntent);
            Log.i(TAG, "📢 Started listening");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting: " + e.getMessage());
            isListening = false;
        }
    }, 1000);  // 1-second delay
}
```

**Benefit:** Allows OS to set up audio resources after WindowManager overlay is created

#### Fix 3: Proper SpeechRecognizer Cleanup

**Change: Enhanced destroy() Method**
```java
public void destroy() {
    try {
        // Cancel ALL pending handler tasks FIRST
        handler.removeCallbacksAndMessages(null);
        
        if (speechRecognizer != null) {
            // Stop listening
            if (isListening) {
                speechRecognizer.stopListening();
            }
            // Destroy recognizer COMPLETELY
            speechRecognizer.destroy();
            speechRecognizer = null;  // CRITICAL: Set to null immediately
        }
        
        isListening = false;
        Log.i(TAG, "✅ Cleaned up completely");
    } catch (Exception e) {
        Log.e(TAG, "Error during cleanup: " + e.getMessage());
    }
}
```

**Benefit:** Prevents "Double Initialization" crashes by completely releasing resources

#### Fix 4: Hardware Acceleration in AndroidManifest.xml

**Change: Add Hardware Acceleration Flag**
```xml
<application
    android:name="com.shreyanshi.scamshield.ScamApplication"
    android:hardwareAccelerated="true"
    ...>
```

**Benefit:** Enables GPU rendering for overlays on Redmi/MIUI devices (fixes rendering crashes)

#### Fix 5: Crash Log Toast Display in ScamApplication.java

**Key Enhancement: Global Uncaught Exception Handler with Toast**
```java
private void setupGlobalExceptionHandler() {
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
        // Extract crash details
        String exceptionClass = throwable.getClass().getSimpleName();
        String crashLocation = "Unknown";
        if (throwable.getStackTrace() != null && throwable.getStackTrace().length > 0) {
            StackTraceElement element = throwable.getStackTrace()[0];
            crashLocation = element.getClassName() + ":" + element.getLineNumber();
        }
        
        // Log to file
        File f = new File(dir, "last_crash.txt");
        FileWriter fw = new FileWriter(f, true);
        fw.write("Exception: " + exceptionClass + "\n");
        fw.write("Location: " + crashLocation + "\n");
        // ... full stack trace
        
        // SAFETY FIX: Show Toast before crash
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            String toastMessage = "⚠️ CRASH: " + exceptionClass + "\n" + 
                    "📍 " + crashLocation.substring(0, Math.min(50, crashLocation.length()));
            Toast.makeText(getApplicationContext(), toastMessage, Toast.LENGTH_LONG).show();
        });
        
        // Give Toast 500ms to display
        Thread.sleep(500);
        
        // Then crash
        defaultHandler.uncaughtException(thread, throwable);
    });
}
```

**Benefit:** User sees crash details (Exception type, line number) before app closes - critical for debugging

### DO's & DON'Ts for Crash Safety (Based on AGENTS.md)

**DO:**
1. Always check windowManager for null before calling methods (AGENTS.md 2821)
2. Check `isWindowAdded` flag before adding duplicate windows (AGENTS.md 2819)
3. Use Application Context for WindowManager (AGENTS.md 2804)
4. Wrap all WindowManager operations in try-catch with specific exception handling
5. Remove old window before adding new one (prevents BadTokenException)
6. Cancel all handler tasks before destroying SpeechRecognizer (prevents leaked callbacks)
7. Set speechRecognizer = null immediately after destroy() (prevents reuse)
8. Add 1-second delay before startListening() to allow OS resource setup
9. Check if activity is destroyed before using it
10. Log exact line number and exception class in crash handler
11. Show Toast with crash details before app closes (user feedback)
12. Verify all resources cleaned up in onDestroy() (memory leaks otherwise)
13. Test on actual Moto/Redmi devices (Emulator won't show all issues)
14. Enable hardware acceleration for GPU-based overlays
15. Handle IllegalArgumentException specifically for already-removed windows

**DON'T:**
1. DON'T assume windowManager is available without null check (AGENTS.md 2821)
2. DON'T call addView() multiple times without removeView() first (AGENTS.md 2819)
3. DON'T use Activity Context for WindowManager (causes lifecycle crashes)
4. DON'T ignore exceptions in addView() without logging details
5. DON'T forget to set speechRecognizer = null after destroy()
6. DON'T leave pending handler tasks after service stops
7. DON'T start SpeechRecognizer immediately in onCreate()
8. DON'T assume activity is valid before using it (check !activity.isDestroyed())
9. DON'T log generic Exception messages (log getClass().getSimpleName())
10. DON'T crash silently without showing user what went wrong
11. DON'T test on emulator only (behavior differs on real Moto/Redmi)
12. DON'T skip hardware acceleration for overlay rendering
13. DON'T assume all devices support TYPE_APPLICATION_OVERLAY (provide TYPE_PHONE fallback)
14. DON'T remove windowManager reference if it's still needed
15. DON'T skip cleanup in destroy() (can cause crashes on Activity recreation)

### Testing Checklist for Crash Safety Fixes

- [ ] Build app: `./gradlew clean assembleDebug`
- [ ] Install on Moto/Redmi device (not emulator)
- [ ] Open app and check Logcat for initialization logs
- [ ] Verify "✅ WindowManager obtained from Application Context" appears
- [ ] Verify "✅ Global exception handler installed" appears
- [ ] Enable debug log in Settings (should appear at bottom-left)
- [ ] Trigger incoming call with scam keyword
- [ ] Verify app does NOT crash with "tu tu tu" beep
- [ ] Listen for beep and speak immediately after (within 1 second)
- [ ] Verify speech is captured (Toast: "📢 Heard: [word]")
- [ ] Force a crash: Press "Force Crash" button or trigger NPE
- [ ] Verify Toast appears showing "⚠️ CRASH: [ExceptionType]" and line number
- [ ] Verify crash is logged to: `/sdcard/Android/data/[package]/files/logs/last_crash.txt`
- [ ] Verify window appears and disappears correctly
- [ ] Test rapid enable/disable of debug log (toggle 10 times quickly)
- [ ] Verify no "BadTokenException" in Logcat
- [ ] Test backgrounding app (press home button)
- [ ] Press back button to return - app should not crash
- [ ] Rotate screen - window should persist or gracefully recover
- [ ] Verify memory usage is stable (no leaks) with `adb shell dumpsys meminfo`
- [ ] Check file-based crash log exists with full stack trace

### Troubleshooting Crash Safety Issues

**Problem:** "BadTokenException: Unable to add window" crash
- **Check 1:** Verify windowManager not null: "✅ WindowManager obtained"
- **Check 2:** Verify `isWindowAdded` flag prevents duplicates
- **Check 3:** Check if Activity was destroyed before initialize() called
- **Check 4:** Logcat should show "❌ BadTokenException" with details
- **Solution:** Ensure null checks pass, never call initialize() on destroyed activity

**Problem:** App crashes on activity recreation (screen rotation)
- **Check 1:** Verify window is removed in onDestroy()
- **Check 2:** Verify `debugLogWindow.destroy()` is called in MainActivity.onDestroy()
- **Check 3:** Check for lingering windowManager references
- **Solution:** Ensure destroy() is called for all overlays before activity destroyed

**Problem:** SpeechRecognizer starts but immediately crashes
- **Check 1:** Verify 1-second delay implemented: "⏳ Delaying speech start"
- **Check 2:** Verify speechRecognizer is not null after delay
- **Check 3:** Check for audio permission errors in Logcat
- **Solution:** Verify delay is in place and permission is granted

**Problem:** Crash silent - no Toast or log entry
- **Check 1:** Verify ScamApplication is declared in AndroidManifest.xml
- **Check 2:** Verify global exception handler is installed: "✅ Global exception handler installed"
- **Check 3:** Check if exception handler method is being called
- **Solution:** Ensure ScamApplication.onCreate() runs by checking Logcat at startup

**Problem:** WindowManager null despite getSystemService() call
- **Check 1:** Verify context.getSystemService() returned non-null
- **Check 2:** Check device ROM version (some Moto ROMs restrict WindowManager)
- **Check 3:** Verify SYSTEM_ALERT_WINDOW permission is optional in manifest
- **Solution:** Fallback to non-overlay approach for restricted devices

### Files Modified for Crash Safety

| File | Changes | Line Changes |
|------|---------|--------------|
| **DebugLogWindow.java** | Safe WindowManager access with null checks | +40 lines of safety code |
| **GoogleSpeechRecognizer.java** | 1-second startup delay + enhanced cleanup | +15 lines of safety code |
| **ScamApplication.java** | Enhanced crash handler with Toast display | +50 lines of safety code |
| **AndroidManifest.xml** | Added hardware acceleration flag | 1 line change |

### Crash Safety Summary

Before fixes: **Crashes immediately with "tu tu tu" beep on Moto/Redmi** (NullPointerException in WindowManager.addView)

After fixes:
✅ WindowManager initialized safely with null checks
✅ Application Context used (survives lifecycle)
✅ Speech Recognizer delayed 1 second (allows resource setup)
✅ Proper cleanup prevents double initialization
✅ Hardware acceleration enables overlay rendering
✅ User sees crash details in Toast before app closes

---

## Internal Debug Terminal - Failsafe UI Logging (Android 7.0+)

### Overview
ScamShield implements an **Internal Debug Terminal** (UI-based in-app logger) that displays real-time speech recognition events and service lifecycle logs directly within the app without requiring Logcat access or overlay permissions. This replaces the WindowManager overlay approach on Redmi/Moto devices where SYSTEM_ALERT_WINDOW restrictions cause crashes.

**Key Benefit:** No WindowManager overlay permission needed, works on all devices including Moto/Redmi with strict ROM restrictions.

### Architecture Pattern

**Components:**
1. **activity_main.xml** - Added ScrollView + TextView (bottom 40% of screen)
2. **MainActivity.appendLog()** - Timestamped log appending with auto-scroll
3. **LocalBroadcastManager** - Safe service-to-UI communication
4. **ScamMonitorService.sendDebugLogBroadcast()** - Event broadcasting
5. **GoogleSpeechRecognizer** - All callbacks send debug events via listener

**Communication Flow:**
```
ScamMonitorService (background)
    ↓ (sends Intent via LocalBroadcast)
LocalBroadcastManager
    ↓ (broadcasts ACTION_DEBUG_LOG with message)
MainActivity.debugReceiver (foreground)
    ↓ (receives and processes)
MainActivity.appendLog(message)
    ↓ (UI update via handler.post())
TextView in activity_main.xml
    ↓ (display to user with [HH:mm:ss] timestamp)
Internal Debug Terminal (bottom 40% of MainActivity)
```

### Implementation Details

#### 1. Layout Changes (activity_main.xml)

**Added to bottom of MainActivity:**
```xml
<ScrollView
    android:id="@+id/debug_scroll_view"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="0.4"
    android:layout_below="@id/bottomNavigation"
    android:background="#000000">
    
    <TextView
        android:id="@+id/internal_debug_log"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="#00FF00"
        android:textSize="10sp"
        android:typeface="monospace"
        android:padding="8dp"
        android:scrollbars="vertical"
        android:maxLines="50" />
</ScrollView>
```

**Properties:**
- **Height:** 40% of MainActivity (0dp with layout_weight=0.4)
- **Position:** Bottom of screen, above BottomNavigationView
- **Background:** Pure black (#000000) for contrast
- **Text:** Green (#00FF00) monospace, 10sp size
- **Max Lines:** 50 (auto-removes oldest entries when exceeded)
- **Scroll:** Vertical scrollbar enabled, auto-scroll to bottom

#### 2. MainActivity Debug Logging Setup

**Initialization (onCreate):**
```java
private ScrollView debugScrollView;
private TextView internalDebugLog;
private LocalBroadcastManager localBroadcastManager;
private static final int MAX_DEBUG_LINES = 50;
private BroadcastReceiver debugReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ScamMonitorService.ACTION_DEBUG_LOG.equals(intent.getAction())) {
            String message = intent.getStringExtra(ScamMonitorService.EXTRA_DEBUG_MESSAGE);
            appendLog(message);
        }
    }
};

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    // Initialize debug terminal
    debugScrollView = findViewById(R.id.debug_scroll_view);
    internalDebugLog = findViewById(R.id.internal_debug_log);
    localBroadcastManager = LocalBroadcastManager.getInstance(this);
}
```

**Appending Logs (Main Thread Safe):**
```java
private void appendLog(String message) {
    handler.post(() -> {
        try {
            // Add timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
            String timestamp = "[" + sdf.format(new Date()) + "] ";
            String logLine = timestamp + message;
            
            // Get current text
            String currentText = internalDebugLog.getText().toString();
            
            // Append new line
            if (currentText.isEmpty()) {
                internalDebugLog.setText(logLine);
            } else {
                internalDebugLog.setText(currentText + "\n" + logLine);
            }
            
            // Keep only last 50 lines (AGENTS.md rule line 1615)
            String[] lines = internalDebugLog.getText().toString().split("\n");
            if (lines.length > MAX_DEBUG_LINES) {
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - MAX_DEBUG_LINES; i < lines.length; i++) {
                    if (i > lines.length - MAX_DEBUG_LINES) sb.append("\n");
                    sb.append(lines[i]);
                }
                internalDebugLog.setText(sb.toString());
            }
            
            // Auto-scroll to bottom (AGENTS.md rule line 1631)
            debugScrollView.post(() -> debugScrollView.fullScroll(View.FOCUS_DOWN));
        } catch (Exception e) {
            Log.e(TAG, "Error appending log: " + e.getMessage());
        }
    });
}
```

**Register/Unregister (onStart/onStop):**
```java
@Override
public void onStart() {
    super.onStart();
    // Register LocalBroadcast receiver
    IntentFilter filter = new IntentFilter(ScamMonitorService.ACTION_DEBUG_LOG);
    localBroadcastManager.registerReceiver(debugReceiver, filter);
    appendLog("✅ MainActivity resumed");
}

@Override
public void onStop() {
    super.onStop();
    // Unregister to prevent leaks
    localBroadcastManager.unregisterReceiver(debugReceiver);
}
```

#### 3. ScamMonitorService - Sending Debug Events

**Add LocalBroadcast Constants:**
```java
public static final String ACTION_DEBUG_LOG = "com.shreyanshi.scamshield.DEBUG_LOG";
public static final String EXTRA_DEBUG_MESSAGE = "log_message";
```

**Send Debug Messages:**
```java
private void sendDebugLogBroadcast(String message) {
    try {
        Intent intent = new Intent(ACTION_DEBUG_LOG);
        intent.putExtra(EXTRA_DEBUG_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        // Also send to direct listener if MainActivity is active
        if (debugListener != null) {
            try {
                debugListener.onDebugLog(message);
            } catch (Exception e) {
                Log.d(TAG, "Direct debug listener failed: " + e.getMessage());
            }
        }
    } catch (Exception e) {
        Log.e(TAG, "Error sending debug broadcast: " + e.getMessage());
    }
}
```

**Send Events from All Critical Methods:**
- `onStartCommand()` - "✅ ScamMonitorService started"
- `startForegroundWithNotification()` - "🔧 Creating foreground notification...", "✅ Foreground notification created"
- `initializeSpeechRecognition()` - "✅ RECORD_AUDIO permission verified", "🎤 Initializing Google Speech Recognizer...", "✅ Speech Recognizer initialized"
- `showScamAlert()` - "✅ Alert shown for: [keyword]", "❌ Alert failed, fallback triggered"
- `onDestroy()` - "🛑 Service destroying...", "✅ Foreground notification removed"

#### 4. GoogleSpeechRecognizer - Callback Logging

**All RecognitionListener callbacks send debug events:**
```java
// In onReadyForSpeech()
listener.onDebugLog("🎤 Ready for speech input");

// In onPartialResults()
listener.onDebugLog("📢 Partial: " + text);

// In onResults()
listener.onDebugLog("✅ Final: " + text);

// In onError()
listener.onDebugLog("❌ Error [" + errorCode + "]: " + errorMessage);
listener.onDebugLog("🔄 Beep heard, listening resumed in 500ms...");
listener.onDebugLog("🔄 Auto-restarting after error...");
listener.onDebugLog("⚠️ Retrying in 3 seconds...");
```

### Emoji Prefixes (Consistent Color-coding)

| Emoji | Color | Meaning | Examples |
|-------|-------|---------|----------|
| ✅ | Green | Success | Permission granted, recognizer started, alert shown |
| ❌ | Red | Error | Permission missing, recognizer failed, alert failed |
| 🎤 | Blue | Speech Events | Ready for speech, microphone active |
| 📢 | Yellow | Recognition Results | Partial/final speech results |
| 🔄 | Cyan | State Transitions | Auto-restarting, retrying, reconnecting |
| 🚨 | Red | Scam Detection | Keyword matched, alert triggered |
| 🛑 | Purple | Stopping | Service stopped, resources freed |
| ⚠️ | Orange | Warnings | Permission denied, retry in progress |
| 🔧 | Gray | Configuration | Notification setup, intent configuration |
| ℹ️ | Cyan | Info | General information, status updates |

### DO's & DON'Ts for Internal Debug Terminal

**DO:**
1. Use LocalBroadcastManager for service-to-UI communication (safe, works even if MainActivity not running)
2. Always call handler.post() for all TextView updates from background threads (AGENTS.md line 1631)
3. Use SimpleDateFormat("HH:mm:ss") for all log timestamps (AGENTS.md line 1614)
4. Limit debug log to 50 lines max (AGENTS.md line 1615)
5. Register BroadcastReceiver in onStart() and unregister in onStop() (prevent leaks)
6. Send debug messages from all critical lifecycle methods (startup, permissions, initialization)
7. Include emoji prefixes for quick visual scanning (✅ ❌ 🎤 📢 🔄 🚨 🛑 ⚠️)
8. Check if listener/receiver is null before using (may be destroyed)
9. Use try-catch around all debug callback methods
10. Auto-scroll to bottom after each append (shows latest events immediately)
11. Make debug terminal visible by default (helps users understand what app is doing)
12. Allow user to scroll through old logs without blocking new logs
13. Use monospace font for aligned columns and better readability
14. Use green text on black background for high contrast
15. Verify LocalBroadcastManager initialized before sending (never null if added in onCreate)

**DON'T:**
1. DON'T use direct Toast messages for debug logging (use debug terminal only)
2. DON'T call TextView.setText() directly from background threads (always use handler.post)
3. DON'T assume Exception.getMessage() is not null (check before logging - AGENTS.md line 1638)
4. DON'T forget to unregister BroadcastReceiver in onStop() (memory leaks)
5. DON'T assume LocalBroadcast sender and receiver are on same process (they are, but design for safety)
6. DON'T hardcode timestamps - use SimpleDateFormat with Locale.US
7. DON'T use colored text for individual messages (green only, consistent with original style)
8. DON'T log sensitive information (audio content, full phone numbers)
9. DON'T send debug messages from tight loops (causes performance issues)
10. DON'T assume MainActivity is running (LocalBroadcast handles gracefully)
11. DON'T exceed 50 log lines (DebugTerminal auto-removes oldest entries)
12. DON'T use Log.d() for user-visible events (use onDebugLog() instead)
13. DON'T forget to flush logs on app exit (they're UI-based, cleared naturally)
14. DON'T initialize debug UI before setContentView() (causes NPE)
15. DON'T send duplicate messages within 100ms (can spam the log)

### Testing Checklist for Internal Debug Terminal

- [ ] Install app on Android device
- [ ] Open MainActivity and verify debug terminal appears at bottom
- [ ] Verify green text on black background is readable
- [ ] Verify timestamped entries: "[HH:mm:ss] message"
- [ ] Trigger incoming call and verify events appear in real-time:
  - "✅ ScamMonitorService started"
  - "🔧 Creating foreground notification..."
  - "✅ RECORD_AUDIO permission verified"
  - "🎤 Initializing Google Speech Recognizer..."
  - "✅ Speech Recognizer initialized and listening"
- [ ] Speak non-scam words and verify "📢 Partial: [text]" appears
- [ ] Speak scam keyword and verify:
  - "✅ Final: [text]"
  - "🚨 SCAM KEYWORD DETECTED: [keyword]"
  - "✅ Alert shown for: [keyword]"
- [ ] Verify scroll behavior: new logs appear at bottom, auto-scroll to latest
- [ ] Scroll up to see old logs without blocking new ones
- [ ] Verify debug log persists across configuration changes (rotate screen)
- [ ] Close app and reopen: verify logs are cleared (UI-based, not persistent)
- [ ] Test error scenario: disconnect WiFi during call
  - Verify "❌ Error [code]: message" appears
  - Verify "🔄 Auto-restarting..." message
- [ ] Test with multiple incoming calls in sequence
- [ ] Verify no "null pointer" or "undefined" messages (null checks working)
- [ ] Check memory usage doesn't increase with time (max 50 lines limit working)
- [ ] Verify emoji prefixes are consistent and visible

### Troubleshooting Internal Debug Terminal Issues

**Problem:** Debug terminal doesn't appear
- **Check 1:** Verify R.id.debug_scroll_view exists in activity_main.xml
- **Check 2:** Verify R.id.internal_debug_log exists in activity_main.xml
- **Check 3:** Verify findViewById() in onCreate() succeeds
- **Check 4:** Check Logcat for "Error appending log" exceptions
- **Solution:** Verify layout IDs match exactly, check for NPE in appendLog()

**Problem:** Messages don't appear in real-time
- **Check 1:** Verify BroadcastReceiver registered in onStart()
- **Check 2:** Verify MainActivity is in foreground (active)
- **Check 3:** Check if exception in onReceive() callback
- **Check 4:** Verify ACTION_DEBUG_LOG constant matches between service and activity
- **Solution:** Verify receiver registration, check MainActivity is active, verify intent action name

**Problem:** Auto-scroll not working (can't see latest messages)
- **Check 1:** Verify debugScrollView.post() called after setText()
- **Check 2:** Verify fullScroll(View.FOCUS_DOWN) is correct method
- **Check 3:** Check if ScrollView parent layout has conflicting gravity
- **Solution:** Ensure ScrollView wraps TextView, call scroll in post() block

**Problem:** Old messages appear multiple times (not removed at 50 lines)
- **Check 1:** Verify MAX_DEBUG_LINES = 50 constant is set
- **Check 2:** Verify split("\n") and StringBuilder logic is correct
- **Check 3:** Check if line count calculation off-by-one error
- **Solution:** Add debug logging in trim logic to verify line count

**Problem:** Debug terminal takes up too much space (needs more app UI visible)
- **Check 1:** Verify layout_weight="0.4" (40% of screen)
- **Check 2:** Can reduce to layout_weight="0.25" (25% of screen)
- **Check 3:** Can use different distribution between fragments and log
- **Solution:** Adjust layout_weight based on UX needs

### Benefits Over Floating WindowManager Overlay

| Feature | WindowManager Overlay | Internal Debug Terminal |
|---------|----------------------|--------------------------|
| **Moto/Redmi Compat** | ❌ Crashes | ✅ Works perfectly |
| **Permission** | ❌ SYSTEM_ALERT_WINDOW required | ✅ No overlay permission |
| **Lifecycle** | ⚠️ Complex cleanup | ✅ Simple UI lifecycle |
| **Rendering** | ⚠️ GPU issues on MIUI | ✅ Standard Android view |
| **User Control** | ❌ Hard to dismiss | ✅ User can scroll/interact |
| **Thread Safety** | ⚠️ Complex sync | ✅ handler.post() pattern |
| **Testing** | ❌ Difficult to debug | ✅ Easy to inspect |
| **Performance** | ✅ Lightweight | ✅ Lightweight |
| **Debuggability** | ❌ Invisible on some ROMs | ✅ Always visible |

---

## Build Commands
```bash
./gradlew assembleDebug    # Debug build
./gradlew assembleRelease # Release build
```

## App Icon Setup
Custom icon located at: `icon/shieldicon.jpeg`
- Icon sizes are auto-generated for all mipmap folders (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- Adaptive icon configured in `mipmap-anydpi-v26/`
- Background color: Green (#1B5E20) for ScamShield branding
- To regenerate icons after changing source image, run:
```python
from PIL import Image
img = Image.open('icon/shieldicon.jpeg').convert('RGBA')
for folder, size in [('mipmap-mdpi',48), ('mipmap-hdpi',72), ('mipmap-xhdpi',96), ('mipmap-xxhdpi',144), ('mipmap-xxxhdpi',192)]:
    img.resize((size,size), Image.Resampling.LANCZOS).save(f'app/src/main/res/{folder}/ic_launcher.webp', 'WEBP')
    img.resize((size,size), Image.Resampling.LANCZOS).save(f'app/src/main/res/{folder}/ic_launcher_round.webp', 'WEBP')
```

## UI Changes Made
- Home renamed to "Dialer" in bottom navigation
- System status panel moved to Settings only
- Dark mode toggle removed (app uses default dark theme)
- News now shows images, title, description with "Read More" button (only button opens news)
- Contacts now have: star (favorite - gold when active), block (red when blocked, toggle unblock), delete button
- Call history shows time only, Google Dialer style
- Dialer shows contact suggestions when typing numbers with quick call button
- App now uses pure dark theme (#121212 background)

## Bug Fixes
- Fixed SQLiteException in HomeFragment search (using correct column names from ContactsContract)
- Fixed ClassCastException in ContactsAdapter (ImageView vs ImageButton type mismatch)

---

## Call-Time Speech Detection Fixes (Phone Call Microphone Access)

### Problem
ScamShield service stays alive during a phone call, but it doesn't "hear" anything. The speech recognition gets silence because the Phone app is using the microphone, blocking other apps from accessing it.

### Root Cause
The default audio source for SpeechRecognizer conflicts with the Phone app during active calls. When another app holds the microphone, SpeechRecognizer receives silence or ERROR_RECOGNIZER_BUSY (Error 8).

### Solution Overview
Three key fixes implemented in GoogleSpeechRecognizer.java:
1. **Audio Source Change** - Use VOICE_COMMUNICATION for call-time detection
2. **Continuous Listening Loop** - Restart immediately after onEndOfSpeech()
3. **Microphone Busy Retry** - 2-second delay for ERROR_RECOGNIZER_BUSY (Error 8)
4. **Volume Log** - Real-time mic level display in debug terminal

### Implementation Details

#### Fix 1: Change Audio Source (Already Implemented)
```java
// In setupRecognizerIntent() - uses VOICE_COMMUNICATION for call-time detection
recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, 
    android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION);
```
- VOICE_COMMUNICATION is designed to work during active VoIP or telephony calls
- Allows parallel microphone access with the Phone app
- Fallback: VOICE_RECOGNITION can also be tried if VOICE_COMMUNICATION fails

#### Fix 2: Continuous Listening Loop (Already Implemented)
```java
// In onEndOfSpeech() - immediately restart listening after silence
@Override
public void onEndOfSpeech() {
    Log.d(TAG, "🔇 End of speech detected, restarting listening immediately...");
    
    if (listener != null) {
        try {
            listener.onDebugLog("🔇 Silence detected, resuming listening...");
        } catch (Exception e) {
            Log.d(TAG, "Debug log callback failed: " + e.getMessage());
        }
    }
    
    // Restart listening immediately (200ms delay) to capture next phrase
    if (autoRestartEnabled && speechRecognizer != null) {
        handler.postDelayed(this::start, 200);
    }
}
```
- Without this, gaps in conversation would pause monitoring
- During phone calls, there are many pauses (user listening, other person talking)
- Must restart immediately to not miss scam keywords in the next phrase

#### Fix 3: Handle Microphone Busy - ERROR_RECOGNIZER_BUSY (Error 8) (Already Implemented)
```java
// In onError() - separate handling for ERROR_RECOGNIZER_BUSY (Error 8)
if (errorCode == SpeechRecognizer.ERROR_NO_MATCH 
        || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
    if (autoRestartEnabled) {
        // ERROR_RECOGNIZER_BUSY: Mic busy during call - use 2-second retry
        // ERROR_NO_MATCH: Normal silence - use 500ms retry
        long retryDelay = (errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) ? 2000 : 500;
        String retryMsg = (errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 
            ? "🔄 Mic busy (call in progress), retrying in 2 seconds..." 
            : "🔄 Beep heard (NO_MATCH), restarting in 500ms...";
        
        Log.i(TAG, retryMsg);
        if (listener != null) {
            listener.onDebugLog(retryMsg);
        }
        handler.postDelayed(this::autoRestartListening, retryDelay);
    }
    return;
}
```
- ERROR_RECOGNIZER_BUSY (Error 8): Mic is busy during call, retry after 2 seconds
- ERROR_NO_MATCH (Error 7): Normal silence, retry after 500ms
- Also handle ERROR_INSUFFICIENT_PERMISSIONS (Error 9) with 2-second retry

#### Fix 4: Volume Log - Real-time Mic Level (Already Implemented)
```java
// In onRmsChanged() - log microphone level for debugging
@Override
public void onRmsChanged(float rmsdB) {
    long currentTime = System.currentTimeMillis();
    
    // Throttle logging to prevent spam (200ms minimum between logs)
    if (currentTime - lastRmsLogTime < RMS_LOG_THROTTLE_MS) {
        return;
    }
    
    // Only log if RMS value changed significantly (>2dB difference)
    if (Math.abs(rmsdB - lastRmsValue) < 2.0f) {
        return;
    }
    
    lastRmsLogTime = currentTime;
    lastRmsValue = rmsdB;
    
    // Format: "🎤 Mic Level: -10.5dB"
    String micMessage = String.format("🎤 Mic Level: %.1fdB", rmsdB);
    
    // Send to debug terminal
    if (listener != null) {
        try {
            listener.onDebugLog(micMessage);
        } catch (Exception e) {
            Log.d(TAG, "Debug log callback failed: " + e.getMessage());
        }
    }
    
    Log.d(TAG, micMessage);
}
```
- Shows "🎤 Mic Level: -10.5dB" in debug terminal
- If level stays at 0 or very negative (-100), mic is muted by system
- Helps diagnose why app can't hear during calls
- Throttled to prevent log spam (200ms between updates)

### Debug Terminal Expected Output During Call
```
[14:25:10] 🎤 Mic Level: -12.3dB
[14:25:11] 🎤 Mic Level: -8.5dB
[14:25:12] 🎤 Mic Level: -3.2dB
[14:25:13] 🎤 Mic Level: 0.0dB  <- User is speaking
[14:25:14] 🎤 Mic Level: -2.1dB
[14:25:15] 🔇 Silence detected, resuming listening...
[14:25:16] 🎤 Mic Level: -100.0dB  <- Mic muted by system (PROBLEM!)
```

### DO's & DON'Ts for Call-Time Detection

**DO:**
1. Use VOICE_COMMUNICATION audio source for call-time detection
2. Implement immediate restart in onEndOfSpeech() for continuous monitoring
3. Use 2-second retry for ERROR_RECOGNIZER_BUSY (Error 8)
4. Log microphone levels in onRmsChanged() for debugging
5. Check if RMS stays at -100 (mic muted by system)
6. Use handler.postDelayed() for all retry delays (never Thread.sleep)
7. Log "🔄 Mic busy (call in progress)" message for user visibility
8. Test on real phone call (not just app open)

**DON'T:**
1. DON'T use default audio source (will be blocked by Phone app)
2. DON'T stop listening after onEndOfSpeech() (gaps miss scam keywords)
3. DON'T use same retry delay for all errors (2s for busy, 500ms for no match)
4. DON'T skip onRmsChanged() logging (critical for debugging mic issues)
5. DON'T assume mic is working if RMS shows -100 (system muted it)
6. DON'T test with app in foreground only (must test during real call)
7. DON'T use Thread.sleep() for delays (blocks main thread)
8. DON'T ignore ERROR_RECOGNIZER_BUSY (needs special handling)

### Testing Checklist for Call-Time Detection

- [ ] Install app on Android device
- [ ] Enable debug log in Settings
- [ ] Make a real phone call (not VoIP)
- [ ] Verify debug terminal shows "🎤 Mic Level" values
- [ ] Speak during call and verify levels change (e.g., -12dB to -3dB)
- [ ] Verify levels return to negative when you stop speaking
- [ ] Check if levels show -100dB (mic muted by system)
- [ ] End call and verify listening continues
- [ ] Trigger ERROR_RECOGNIZER_BUSY during call
- [ ] Verify "🔄 Mic busy (call in progress), retrying in 2 seconds..." message
- [ ] Verify retry happens after 2 seconds
- [ ] Speak scam keyword during call
- [ ] Verify app detects keyword and triggers alert

### Troubleshooting Call-Time Detection

**Problem:** Debug terminal shows "🎤 Mic Level: -100dB" constantly
- **Root Cause:** Mic is muted by the system during the call
- **Check 1:** Is this a real phone call (not VoIP)?
- **Check 2:** Does Phone app have mic permission?
- **Check 3:** Is there a call recording restriction on this device?
- **Solution:** Cannot be fixed in app - system is blocking mic access

**Problem:** Debug terminal shows ERROR_RECOGNIZER_BUSY every few seconds
- **Root Cause:** Phone app is continuously holding the mic
- **Check 1:** Is the call still active?
- **Check 2:** Is another app using the mic?
- **Solution:** Normal during calls - 2-second retry should eventually work

**Problem:** No speech recognition during call, but works when not in call
- **Root Cause:** Audio source conflict with Phone app
- **Check 1:** Verify VOICE_COMMUNICATION is set in intent
- **Check 2:** Check if ERROR_RECOGNIZER_BUSY occurs
- **Solution:** Ensure 2-second retry is implemented for busy errors

---

## Android 14 SecurityException Fix (Foreground Service Microphone)

### Problem
Android 14 (API 34) throws SecurityException regarding "eligible state/exemptions" for Foreground Service Microphone. The log shows the service starting twice in one second - causing the service to destroy itself.

### Root Causes
1. **Double-start issue**: Service starts twice in rapid succession (within 1 second)
2. **Missing service type flag**: startForeground() called without FOREGROUND_SERVICE_TYPE_MICROPHONE on Android 14
3. **Wrong API level check**: Using Build.VERSION_CODES.Q instead of Build.VERSION_CODES.UPSIDE_DOWN_CAKE
4. **startForeground not first**: Waiting for RECORD_AUDIO check or channel setup before startForeground

### Implementation Details

#### Fix 1: "Stop-Before-Start" Safety Check - FIRST LINE IN onStartCommand()
```java
// In ScamMonitorService.onStartCommand()
@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    long startTime = System.currentTimeMillis();
    
    // CRITICAL FIX #1: Move startForeground to VERY FIRST LINE
    // Android 14 requires startForeground() to be called within 5 seconds
    // BEFORE any other operations including RECORD_AUDIO check
    
    // CRITICAL FIX #2: "Stop-Before-Start" - Prevent double-start self-destruction
    if (isServiceRunning) {
        Log.w(TAG, "⚠️ Service already running, returning START_STICKY");
        sendDebugLogBroadcast("⚠️ Service already running, skipping duplicate start");
        return START_STICKY;
    }
    
    // Set running flag IMMEDIATELY before calling startForeground
    isServiceRunning = true;
    sendDebugLogBroadcast("🔴 Starting foreground service NOW");
    
    // CRITICAL FIX #3: Call startForeground IMMEDIATELY as first operation
    startForegroundWithNotification();
    
    long elapsed = System.currentTimeMillis() - startTime;
    Log.i(TAG, "⏱️ startForeground completed in " + elapsed + "ms (must be <5000ms)");
    
    // NOW check SharedPreferences AFTER foreground is running
}
```

#### Fix 2: Correct API Level for Service Type
```java
// In ScamMonitorService.startForegroundWithNotification()
// CRITICAL FIX: Use UPSIDE_DOWN_CAKE (API 33) as per Android 14 requirement
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
} else {
    startForeground(NOTIFICATION_ID, notification);
}
```

#### Fix 3: Manifest Verification
```xml
<!-- AndroidManifest.xml - Ensure these are at the top of manifest -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<!-- Service declaration with microphone type -->
<service
    android:name="com.shreyanshi.scamshield.services.ScamMonitorService"
    android:foregroundServiceType="microphone|specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Real-time scam detection and alerting during calls" />
</service>
```

### DO's & DON'Ts for Android 14 Foreground Service

**DO:**
1. Use Build.VERSION_CODES.UPSIDE_DOWN_CAKE (33) for Android 14 service type check
2. Add isServiceRunning check at start of onStartCommand() to prevent double-start
3. Start service from MainActivity (not Fragment or BroadcastReceiver)
4. Call startForeground() within 5 seconds of onStartCommand()
5. Include ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE flag on Android 14+
6. Call startForeground as FIRST LINE in onStartCommand() - before any checks

**DON'T:**
1. DON'T use Build.VERSION_CODES.Q (29) for microphone service type (use UPSIDE_DOWN_CAKE = 33)
2. DON'T start service from Fragment (causes SecurityException on Android 14)
3. DON'T start service from BroadcastReceiver (eligible state not met)
4. DON'T skip isServiceRunning check (causes double-start)
5. DON'T use deprecated startForeground() without type flag on Android 14
6. DON'T wait for RECORD_AUDIO or channel setup before calling startForeground

---

## Final Activation: Mic Wakeup & Logic Link

### Problem
ScamShield app works perfectly when the app is open, but the microphone goes silent during a real phone call. Need to bypass the Android Call-Privacy block.

### Root Causes
1. **Audio Source**: MIC is blocked during calls - need VOICE_COMMUNICATION
2. **Audio Focus**: Need to request AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK when has ROLE_DIALER
3. **Call Settling Time**: ERROR_RECOGNIZER_BUSY (Error 8) needs 2-second retry

### Implementation Details

#### Fix 1: Switch to Communication Mode
```java
// In GoogleSpeechRecognizer.setupRecognizerIntent()
// Use VOICE_COMMUNICATION for real phone call detection
// Standard MIC is blocked during calls. VOICE_COMMUNICATION is designed
// to "share" audio during a telephony session.
recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, 
    android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION);
```

#### Fix 2: Request "Role Manager" Priority
```java
// In MainActivity.onActivityResult() when ROLE_DIALER is granted
private void requestAudioFocusForCallDetection() {
    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    int result = audioManager.requestAudioFocus(
        listener,
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    );
    // Result: AUDIOFOCUS_REQUEST_GRANTED means success
}
```

#### Fix 3: Handle "Audio Busy" Error (Error 8)
```java
// In GoogleSpeechRecognizer.onError()
// If ERROR_RECOGNIZER_BUSY (Error 8), wait 2 seconds before retry
// This gives the Phone app time to "settle" before ScamShield asks for audio stream
if (errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
    long retryDelay = 2000;  // 2 seconds
    handler.postDelayed(this::autoRestartListening, retryDelay);
}
```

### Expected Debug Terminal Output During Call
```
[14:25:10] 🎤 Mic Level: -12.3dB
[14:25:12] 🔄 Mic busy (call in progress), retrying in 2 seconds...
[14:25:14] 🎤 Mic Signal Detected...
[14:25:16] 📢 Partial: verify your OTP
[14:25:18] ✅ Final: verify your OTP
[14:25:18] 🚨 SCAM KEYWORD DETECTED: otp
```

### DO's & DON'Ts for Call Detection

**DO:**
1. Use VOICE_COMMUNICATION audio source for actual call detection
2. Request AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK when ROLE_DIALER granted
3. Use 2-second retry for ERROR_RECOGNIZER_BUSY (Error 8)
4. Test during real phone call (not just app open)

**DON'T:**
1. DON'T use MIC during actual calls (will be blocked by Phone app)
2. DON'T skip audio focus request (Phone app will mute mic)
3. DON'T retry immediately on Error 8 (call needs time to settle)

---

## Hard-Lock: Prevent Service Self-Destruct

### Problem
ScamMonitorService starts and immediately calls onDestroy() (self-destructing) at the same timestamp. Need to force service to stay alive.

### Root Causes
1. **Auto-Stop**: SharedPreferences check triggers stopSelf() prematurely
2. **Unknown Killer**: Need to identify if Android System or bug is destroying service
3. **Audio Bridge**: Need to ensure onPartialResults sends text to service for analysis

### Implementation Details

#### Fix 1: Disable Auto-Stop
```java
// In ScamMonitorService.onStartCommand()
// FIX: Disable auto-stop for debugging - force service to stay alive
// Commented out to verify mic works without being killed
/*
if (!scamAlertsEnabled) {
    stopSelf();
    return START_NOT_STICKY;
}
*/
```

#### Fix 2: Log the "Killer"
```java
// In ScamMonitorService.onDestroy()
String destroyer = (googleSpeechRecognizer == null) ? "System/Low Memory" : "User/Code Action";
Log.i(TAG, "🛑 DESTROY TRIGGERED BY: " + destroyer);
sendDebugLogBroadcast("🛑 DESTROY TRIGGERED BY: " + destroyer);
```

#### Fix 3: Keyword "Test" Toast
```java
// In ScamMonitorService.onSpeechRecognized()
// Show toast for testing - confirms analysis logic is connected
android.widget.Toast.makeText(this, "Checking: " + text, android.widget.Toast.LENGTH_SHORT).show();
```

### Expected Behavior
- Terminal shows: "✅ Speech Recognizer initialized"
- Terminal NEVER shows: "🛑 Service destroying" until user closes app
- Toast shows: "Checking: [text]" for every recognized word

### DO's & DON'Ts for Service Hard-Lock

**DO:**
1. Comment out stopSelf() calls during debugging
2. Log destroy trigger source to identify what's killing service
3. Add test Toast to confirm analysis logic is linked

**DON'T:**
1. DON'T leave stopSelf() active during testing (kills service prematurely)
2. DON'T skip logging in onDestroy() (can't identify killer)

---

## Virtual Mic: Use InCallService for Call Audio Access

### Problem
App is waiting for Phone app to release the mic. Need to use InCallService to access the audio stream during active calls.

### Root Cause
Standard microphone sources are blocked during active phone calls. Need to use Telecom framework's InCallService to get audio stream access.

### Implementation Details

#### Fix 1: Start Service from InCallService
```java
// In ScamShieldInCallService.onCallAdded()
// When call is detected, start ScamMonitorService for scam detection
@Override
public void onCallAdded(Call call) {
    super.onCallAdded(call);
    // Start monitoring service when call is detected
    startScamMonitorServiceForCall();
}

private void startScamMonitorServiceForCall() {
    Intent serviceIntent = new Intent(this, ScamMonitorService.class);
    serviceIntent.putExtra("from_call", true);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(serviceIntent);
    } else {
        startService(serviceIntent);
    }
}
```

#### Fix 2: Use VOICE_RECOGNITION Audio Source
```java
// In GoogleSpeechRecognizer.setupRecognizerIntent()
// Use VOICE_RECOGNITION for broader compatibility
recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, 
    android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION);
```

### DO's & DON'Ts for Virtual Mic

**DO:**
1. Start ScamMonitorService from InCallService when call is detected
2. Use VOICE_RECOGNITION audio source for call-time detection
3. Pass "from_call" extra to service for context

**DON'T:**
1. DON'T rely on CallReceiver alone for call detection (InCallService is more reliable)
2. DON'T use MIC source during calls (will be blocked)

---

## Bypass Dialer Role (Moto/Redmi Restricted Settings)

### Problem
Motorola phone is blocking the "Default Dialer" role with Restricted Settings. Need to bypass and use standard Broadcast monitoring instead.

### Root Causes
1. **Restricted Settings**: Moto/Redmi devices have additional restrictions on Dialer role
2. **System Permissions**: VOICE_COMMUNICATION requires system-level permissions

### Implementation Details

#### Fix 1: Disable Dialer Role Request
```java
// In MainActivity.requestDefaultDialerRole()
// FIX: Log warning that Dialer Role is bypassed on restricted devices
Log.i(TAG, "⚠️ Dialer Role bypassed - using standard Broadcast monitoring");
appendLog("⚠️ Dialer Role bypassed - using standard Broadcast monitoring");
// Old role request code commented out - CallReceiver handles detection
```

#### Fix 2: CallReceiver Already Handles Standard Monitoring
```java
// In CallReceiver.java - already implemented
// Uses PHONE_STATE broadcast to detect calls
// Starts ScamMonitorService when RINGING or OFFHOOK state detected
```

#### Fix 3: Change to MIC Audio Source
```java
// In GoogleSpeechRecognizer.setupRecognizerIntent()
// Use MIC for standard monitoring - doesn't need System Phone permissions
recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, 
    android.media.MediaRecorder.AudioSource.MIC);
```

### Expected Behavior
- App starts listening via CallReceiver when PHONE_STATE changes
- No Dialer role required
- Uses standard RECORD_AUDIO permission
- During active calls, MIC might be blocked (expected behavior)

### DO's & DON'Ts

**DO:**
1. Log "⚠️ Dialer Role bypassed - using standard Broadcast monitoring" 
2. Use CallReceiver for call detection (works without Dialer role)
3. Use MIC audio source (doesn't need special permissions)
4. Add START PROTECTION button for manual foreground service start
5. Start service from button click (satisfies Android 14 eligible state)

**DON'T:**
1. DON'T request Dialer role on restricted devices (will fail)
2. DON'T use VOICE_COMMUNICATION without Dialer role
3. DON'T start service from background (will fail on Android 14)

### Additional Implementation: START PROTECTION Button

#### Foreground Start (MainActivity)
```java
// In MainActivity.onCreate()
private void addStartProtectionButton() {
    // Create START PROTECTION button
    android.widget.Button startBtn = new android.widget.Button(this);
    startBtn.setText("🛡️ START PROTECTION");
    startBtn.setBackgroundColor(0xFF4CAF50);  // Green
    
    startBtn.setOnClickListener(v -> {
        Log.i(TAG, "🛡️ START PROTECTION button clicked");
        appendLog("🛡️ START PROTECTION button clicked");
        
        // Start the service while app is in foreground
        startScamMonitorService();
        
        // Show bypass message
        appendLog("🛡️ Security Bypass Active: Listening via Standard Mic");
    });
}
```

### Expected Behavior After Changes
- User opens app → sees green "🛡️ START PROTECTION" button at top
- User clicks button → service starts in foreground
- Terminal shows: "🛡️ Security Bypass Active: Listening via Standard Mic"
- User speaks → words appear in terminal in real-time

---

## Simple Dialer UI & Automatic Mic Wake-up

### Problem
Want to make calls directly from ScamShield instead of using system dialer. Need simple dialer UI and logic to keep ScamMonitorService active during calls.

### Implementation Details

#### Simple Dialer UI
```java
// In MainActivity.addDialerButton()
// Create EditText for phone number input
android.widget.EditText phoneInput = new android.widget.EditText(this);
phoneInput.setHint("Enter phone number");
phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

// Create CALL button
android.widget.Button callBtn = new android.widget.Button(this);
callBtn.setText("📞 CALL");
callBtn.setBackgroundColor(0xFF2196F3);  // Blue
```

#### Call Initiation Logic
```java
// Step 1: Start ScamMonitorService first (to hook into audio)
startScamMonitorService();

// Step 2: Make the call via Intent.ACTION_CALL
Intent callIntent = new Intent(Intent.ACTION_CALL);
callIntent.setData(android.net.Uri.parse("tel:" + phoneNumber));
startActivity(callIntent);
```

#### Automatic Mic Wake-up
```java
// When CALL button is pressed, call startScamMonitorService() first
// This ensures ScamMonitorService is running when the call connects
// The service then starts GoogleSpeechRecognizer for real-time monitoring
```

### Permissions
```xml
<!-- Already in AndroidManifest.xml -->
<uses-permission android:name="android.permission.CALL_PHONE" />
```

### Expected Behavior
- User opens app → sees "📞 CALL" button with phone input
- User enters number and clicks CALL
- Service starts → call initiates
- During call, ScamMonitorService detects scam keywords in real-time
- If scam detected, alert pops up over calling screen

### DO's & DON'Ts for Dialer UI

**DO:**
1. Start ScamMonitorService BEFORE making call (to hook into audio)
2. Use Intent.ACTION_CALL for direct calling
3. Add CALL_PHONE permission to manifest

**DON'T:**
1. DON'T make call before starting service (audio won't be monitored)
2. DON'T skip error handling for call failure

### Problem
Make ScamShield compatible with Android 12 through Android 16.

### Implementation Details

#### Fix 1: Dynamic startForeground (API 31-36)
```java
// In ScamMonitorService.startForegroundWithNotification()
// UNIVERSAL FIX: Dynamic startForeground for API 31-36 (Android 12-16)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    // Android 14, 15, 16 (API 34-36): Use microphone type flag
    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    // Android 12, 13 (API 31-33): Use microphone type flag
    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
} else {
    // Android 11 and below: No type flag needed
    startForeground(NOTIFICATION_ID, notification);
}
```

#### Fix 2: Universal Notification Permission
```java
// In MainActivity.checkAndRequestPermissions()
// Already includes POST_NOTIFICATIONS permission in the array
// Android 13+ will prompt for it
String[] perms = {
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.POST_NOTIFICATIONS  // Android 13+ required
};
```

#### Fix 3: Ziddi Manufacturers Fix (Samsung/Oppo/Redmi)
```java
// In MainActivity.checkBatteryOptimization()
android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
    showBatteryOptimizationDialog();
}
```

### DO's & DON'Ts for Android 12-16 Compatibility

**DO:**
1. Use Build.VERSION_CODES.S (31) for Android 12 checks
2. Use Build.VERSION_CODES.UPSIDE_DOWN_CAKE (33) for Android 14+ checks
3. Request POST_NOTIFICATIONS permission for Android 13+
4. Check battery optimization for Ziddi manufacturers
5. **DO** call startForeground() as FIRST LINE in onStartCommand()
6. **DO** include ALL required types: mediaProjection | microphone | phoneCall
7. **DO** use timing logs to track elapsed time

**DON'T:**
1. DON'T use hardcoded API numbers (use Build.VERSION_CODES constants)
2. DON'T skip POST_NOTIFICATIONS (service won't start on Android 13+)
3. DON'T ignore battery optimization on Samsung/Oppo/Redmi
4. **DON'T** call startForeground() after >5 seconds elapsed from onStartCommand()
5. **DON'T** wait for RECORD_AUDIO or channel setup before calling startForeground
6. **DON'T** start service from Fragment (causes SecurityException on Android 14)

## Context Menu Implementation

### History & Contacts Context Menu
Added PopupMenu with Edit/Block/Call options:

- **HistoryAdapter.java** - Added long-click context menu
- **ContactsAdapter.java** - Added click context menu + quick call button
- Null checks for Cursor operations to prevent crashes

### DO (Context Menu)
1. **DO** use PopupMenu for contextual actions
2. **DO** add null checks for list/model in bindViewHolder
3. **DO** use protected call method (startProtectedCall) for phone calls

### DON'T (Context Menu)
1. **DON'T** assume model is never null in bindViewHolder
2. **DON'T** skip null checks on Cursor operations

### Overview
ScamShield now includes a Recording Settings screen to manage call recording and transcription. Users can choose to record all calls, only unknown calls (not in contacts), or disable recording. Transcripts can be saved as .txt files.

### Architecture Components

**RecordingSettings.java** - Manages SharedPreferences for recording preferences:
```java
public class RecordingSettings {
    // Keys: KEY_RECORD_ALL_CALLS, KEY_RECORD_UNKNOWN_ONLY, KEY_SAVE_TRANSCRIPTS
    public boolean shouldRecordCall(String phoneNumber, boolean isKnownContact);
}
```

**ContactChecker.java** - Queries system contacts to check if number is known:
```java
public class ContactChecker {
    public boolean isKnownContact(String phoneNumber);
    // Uses ContactsContract.PhoneLookup for efficient lookup
}
```

**TranscriptManager.java** - Saves transcripts to Documents/ScamShield/Transcripts/:
```java
public class TranscriptManager {
    public String saveTranscript(String phoneNumber, String transcriptText);
    // Android 10+: Uses MediaStore API
    // Android 9 and below: Uses File system
    // File naming: CallTranscript_[Number]_[Date]_[Time].txt
}
```

### DO (Recording Settings)
1. **DO** use SharedPreferences for settings storage with clear keys
2. **DO** use ContactsContract.PhoneLookup for contact matching
3. **DO** check READ_CONTACTS permission before querying contacts
4. **DO** use MediaStore API for Android 10+ file saving
5. **DO** use app-specific directory for transcripts
6. **DO** append transcript in onSpeechRecognized() callback
7. **DO** save transcript in stopListeningForScams() when recording enabled

### DON'T (Recording Settings)
1. **DON'T** assume contacts permission always granted
2. **DON'T** block main thread with file operations
3. **DON'T** save audio files permanently (transcripts only)
4. **DON'T** record without checking user preferences
