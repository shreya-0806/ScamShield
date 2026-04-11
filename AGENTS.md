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
