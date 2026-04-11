# ScamShield Refactoring Plan
## Vosk → Google On-Device Speech Recognition Migration

**Date**: April 10, 2026  
**Status**: PLANNING PHASE  
**Objective**: Remove Vosk dependency and implement Google On-Device Speech Recognition

---

## PHASE 0: Analysis Summary

### Current Architecture (Vosk-Based)
- **Primary STT**: VoskProcessor (offline, 67.61 MB model in assets)
- **Fallback STT**: Google Speech Recognizer (online)
- **Main Service**: ScamMonitorService (orchestrates both engines)
- **Keyword Detection**: 62 scam keywords in HashSet (O(1) lookup)
- **Permission**: RECORD_AUDIO verified at runtime

### Vosk Components to Remove
1. **Dependency**: `com.alphacephei:vosk-android:0.3.47` in build.gradle
2. **Classes**:
   - `VoskProcessor.java` (374 lines)
   - `ModelLoadingCallback` interface
   - `SpeechProcessor.java` (interface, keep for abstraction)
3. **Model Files**: `assets/vosk-model/` (67.61 MB, 14 files)
4. **UI Updates**: SettingsFragment Vosk status display
5. **Service Logic**: VoskProcessor initialization in ScamMonitorService

### Proposed New Architecture (Google On-Device Speech)
- **Single STT Engine**: Google On-Device Speech Recognizer
- **Advantages**:
  - ✅ No 67.61 MB model bundle (reduces APK size significantly)
  - ✅ No async model loading delays
  - ✅ Built-in to Android (API 20+)
  - ✅ Faster on modern devices (neural networks optimized)
  - ✅ Automatic improvements via Google Play Services
- **Disadvantages**:
  - ❌ Requires online mode (with EXTRA_PREFER_OFFLINE fallback)
  - ❌ May not work without Google services (rare devices)
  - ✅ MITIGATION: Graceful degradation with error handling

---

## PHASE 1: DO & DON'T EXTRACTION FROM AGENTS.MD

### Current DO Rules (Vosk-Centric)
1. ✅ Always bundle Vosk model in APK assets
2. ✅ Use startForegroundService() for services
3. ✅ Handle all permissions gracefully
4. ✅ Use dark theme (#121212 background)
5. ✅ Process audio locally - never upload without consent
6. ✅ Add meaningful logging with TAG constants
7. ✅ Register all services in AndroidManifest.xml
8. ✅ Test on Moto/Stock Android devices
9. ✅ Use Activity-based alerts (not overlay)
10. ✅ Fetch scam news from Google News RSS
11. ✅ Read call history from CallLog provider
12. ✅ Use custom app icon (shieldicon.jpeg)
13. ✅ Dark mode applies theme immediately
14. ✅ Use BlockedNumberDatabase for contacts
15. ❌ Implement callback mechanism for Vosk model loading
16. ❌ Register/unregister callbacks in Fragment lifecycle

### Rules to Update (After Migration)
**Remove from DO**:
- Item 1: "Always bundle Vosk model in APK assets"
- Item 15: "Implement callback mechanism for Vosk model loading"
- Item 16: "Register/unregister model loading callbacks"

**Keep in DO**:
- Items 2-14 (all still applicable)

**Add New DO**:
1. Use Google On-Device Speech Recognizer via SpeechRecognizer.createOnDeviceSpeechRecognizer()
2. Set RecognizerIntent.EXTRA_PREFER_OFFLINE = true for offline fallback
3. Auto-restart listening in onResults() and onError() for continuous monitoring
4. Implement immediate fallback UI if Google Speech unavailable

### Current DON'T Rules
1. ✅ DON'T require SYSTEM_ALERT_WINDOW
2. ✅ DON'T permanently save audio recordings
3. ✅ DON'T assume permissions are granted
4. ✅ DON'T block the main thread
5. ✅ DON'T hardcode URLs or API keys
6. ✅ DON'T use deprecated APIs without fallback
7. ✅ DON'T require overlay permission
8. ✅ DON'T use launcher icons for notification
9. ✅ DON'T use colored notification icons

### New DON'T Rules (After Migration)
**Add**:
1. DON'T assume Google Speech is available - always check availability
2. DON'T leave SpeechRecognizer listening indefinitely - implement auto-restart mechanism
3. DON'T ignore onError() callbacks - implement proper error handling with logging
4. DON'T process speech without permission check first

---

## PHASE 2: Implementation Approach

### Step 1: Remove Vosk Dependency (10 minutes)
**File**: `app/build.gradle`
- Delete: `implementation 'com.alphacephei:vosk-android:0.3.47'`
- Verify: No other Vosk imports remain

### Step 2: Delete Vosk-Related Classes (5 minutes)
**Files to Delete**:
- `app/src/main/java/.../stt/VoskProcessor.java` (374 lines)

**Files to Keep** (abstraction):
- `SpeechProcessor.java` (interface)
- `SpeechListener.java` (interface)

### Step 3: Create GoogleSpeechRecognizer Class (30 minutes)
**New File**: `GoogleSpeechRecognizer.java`
- Implements `SpeechProcessor` interface
- Uses `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)`
- Implements `RecognitionListener`
- Auto-restart mechanism in onResults() and onError()
- Detailed logging with timestamps

### Step 4: Update ScamMonitorService (20 minutes)
**Changes**:
- Remove VoskProcessor initialization
- Remove polling timeout logic for Vosk
- Replace with immediate GoogleSpeechRecognizer initialization
- Remove `usingVosk` flag logic
- Keep keyword detection and alert logic
- Keep permission checking
- Keep debounce mechanism (30 seconds)

### Step 5: Update SettingsFragment (10 minutes)
**Changes**:
- Remove Vosk status display ("LOADING...", "READY ✓", "FAILED")
- Add Google Speech status display (simpler: "ENABLED" or "UNAVAILABLE")
- Remove callback registration/unregistration

### Step 6: Delete Model Files (5 minutes)
**Deletion**:
- `app/src/main/assets/vosk-model/` (entire directory)
- Saves 67.61 MB from APK size

### Step 7: Update AndroidManifest.xml (5 minutes)
**Changes**:
- Keep foregroundServiceType="microphone|specialUse"
- Add <queries> tag for RECOGNIZE_SPEECH intent (Android 11+)
- Keep all permissions

### Step 8: Update AGENTS.md (15 minutes)
**Changes**:
- Update Project Overview (replace Vosk with Google On-Device)
- Remove Vosk-specific rules
- Add new rules for Google Speech
- Update Core Components
- Remove "Vosk Model Integration Best Practices" section
- Add "Google On-Device Speech Recognition Best Practices" section

---

## PHASE 3: Code Patterns

### GoogleSpeechRecognizer Structure
```java
public class GoogleSpeechRecognizer implements SpeechProcessor, RecognitionListener {
    
    private static final String TAG = "GoogleSpeech";
    private SpeechRecognizer speechRecognizer;
    private RecognizerIntent recognizerIntent;
    private SpeechListener listener;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isListening = false;
    private static final long AUTO_RESTART_DELAY_MS = 1000;
    
    // Initialization
    public GoogleSpeechRecognizer(Context context, SpeechListener listener) {
        this.listener = listener;
        initializeSpeechRecognizer(context);
    }
    
    private void initializeSpeechRecognizer(Context context) {
        // Check if Google Speech is available
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "❌ Google Speech Recognition not available");
            return;
        }
        
        // Create speech recognizer
        speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(this);
        
        // Setup intent
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        
        Log.i(TAG, "✅ Google Speech Recognizer initialized");
    }
    
    @Override
    public void start() {
        if (speechRecognizer == null) return;
        
        try {
            isListening = true;
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "📢 Started listening");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting listening: " + e.getMessage(), e);
            isListening = false;
        }
    }
    
    @Override
    public void stop() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            isListening = false;
            Log.d(TAG, "⏹️ Stopped listening");
        }
    }
    
    @Override
    public boolean isRunning() {
        return isListening;
    }
    
    // RecognitionListener implementations
    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> results = partialResults.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION);
        if (results != null && !results.isEmpty()) {
            String text = results.get(0);
            if (!text.isEmpty() && listener != null) {
                Log.d(TAG, "📢 Partial: " + text);
                listener.onSpeechRecognized(text);
            }
        }
    }
    
    @Override
    public void onResults(Bundle results) {
        // Process final results
        ArrayList<String> matches = results.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0);
            Log.i(TAG, "✅ Final: " + text);
            if (listener != null) {
                listener.onSpeechRecognized(text);
            }
        }
        
        // Auto-restart listening for continuous monitoring
        autoRestartListening();
    }
    
    @Override
    public void onError(int error) {
        Log.e(TAG, "❌ Error: " + getErrorString(error));
        
        // Auto-restart on transient errors
        if (error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
            error == SpeechRecognizer.ERROR_AUDIO ||
            error == SpeechRecognizer.ERROR_NO_MATCH) {
            autoRestartListening();
        }
    }
    
    private void autoRestartListening() {
        handler.postDelayed(() -> {
            if (!isListening && speechRecognizer != null) {
                Log.d(TAG, "🔄 Auto-restarting listening...");
                start();
            }
        }, AUTO_RESTART_DELAY_MS);
    }
    
    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "✅ Destroyed");
    }
}
```

### ScamMonitorService Changes
```java
// BEFORE: Complex Vosk polling logic (50+ lines)
private void initializeSpeechRecognition() {
    final long maxWaitTime = 10000;  // 10 seconds
    final long pollInterval = 500;
    // ... 50+ lines of polling logic ...
}

// AFTER: Simple direct initialization (10 lines)
private void initializeSpeechRecognition() {
    // Check permission
    if (!hasRecordAudio()) {
        Log.e(TAG, "❌ RECORD_AUDIO permission missing");
        return;
    }
    
    // Initialize Google Speech directly
    googleSpeech = new GoogleSpeechRecognizer(this, this);
    googleSpeech.start();
    Log.i(TAG, "✅ Google Speech initialized");
}
```

---

## PHASE 4: File Changes Summary

| File | Action | Details |
|------|--------|---------|
| `build.gradle` | DELETE | Remove `com.alphacephei:vosk-android:0.3.47` |
| `VoskProcessor.java` | DELETE | 374-line Vosk manager |
| `SpeechProcessor.java` | KEEP | Interface (abstraction) |
| `SpeechListener.java` | KEEP | Interface (callback) |
| `GoogleSpeechRecognizer.java` | CREATE | 250-300 lines, new class |
| `ScamMonitorService.java` | MODIFY | Remove Vosk logic (~100 lines), keep keyword detection |
| `SettingsFragment.java` | MODIFY | Remove Vosk status UI |
| `CallReceiver.java` | MODIFY | Update logging |
| `AndroidManifest.xml` | MODIFY | Add queries tag, keep service declaration |
| `AGENTS.md` | MODIFY | Update guidelines (50+ lines) |
| `assets/vosk-model/` | DELETE | 67.61 MB directory |

---

## PHASE 5: Testing Checklist

- [ ] Build compiles without errors
- [ ] APK size reduced by ~67 MB
- [ ] App launches without crashing
- [ ] Permission request works
- [ ] Incoming call triggers Google Speech
- [ ] Scam keywords detected and alert shown
- [ ] 30-second debounce works
- [ ] Handler cleanup proper (no leaks)
- [ ] Works on Android 7.0+ (minSdkVersion 24)
- [ ] Works on Android 14 (targetSdkVersion 34)
- [ ] Settings page loads without errors
- [ ] Call history and contacts work

---

## Time Estimate
- Phase 1 (Deletion): 20 minutes
- Phase 2 (GoogleSpeechRecognizer): 45 minutes
- Phase 3 (ScamMonitorService update): 30 minutes
- Phase 4 (Other updates): 30 minutes
- Phase 5 (Testing): 20 minutes
- **Total**: ~2.5 hours

---

## Success Criteria
✅ All Vosk code removed
✅ Google Speech integrated and working
✅ Keyword detection working
✅ Build compiles, no errors
✅ APK size reduced by 67+ MB
✅ AGENTS.md updated
✅ All tests passing
