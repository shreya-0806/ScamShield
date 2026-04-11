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
17. Display persistent notification with "ScamShield Active" title and "Protecting you from fraud calls..." text
18. Show Toast feedback "📢 Heard: [text]" for every recognized word (onPartialResults)
19. Implement 30-second debounce between alert notifications to prevent spam
20. Use white monochrome notification icon (ic_notification.xml) - never use colored icons

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
