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
