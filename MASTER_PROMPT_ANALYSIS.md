# Master Prompt Analysis: ScamShield "Hollow" App Fix

## Problem Statement
The app shows a notification (external visibility), but the Default Dialer role is not active, and Speech Recognition is not hearing anything (internal logic failure). We need to:
1. Fix the hollow logic (Brain) - ensure services properly initialize
2. Add visible debug logging (Voice) - see what's happening in real-time

## Phase 1: Extract DO & DON'T Rules from AGENTS.md

### CORE DO RULES (from lines 34-54):
1. ✅ Use `startForegroundService()` for monitoring services on Android O+
2. ✅ Handle all permissions gracefully with fallback UI
3. ✅ Add meaningful logging with TAG constants
4. ✅ Register all services in AndroidManifest.xml
5. ✅ Use Google On-Device Speech Recognizer via `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)`
6. ✅ Set `RecognizerIntent.EXTRA_PREFER_OFFLINE = true` for offline-first operation
7. ✅ Implement auto-restart listening in `onResults()` and `onError()` methods
8. ✅ Display persistent notification with "ScamShield Active" title
9. ✅ Show Toast feedback "📢 Heard: [text]" for every recognized word
10. ✅ Use white monochrome notification icon (ic_notification.xml)

### CORE DON'T RULES (from lines 56-71):
1. ❌ DON'T assume permissions are granted - always check and request
2. ❌ DON'T block the main thread - use ExecutorService for background tasks
3. ❌ DON'T assume Google Speech is available - always check with `SpeechRecognizer.isRecognitionAvailable()`
4. ❌ DON'T leave SpeechRecognizer listening indefinitely - implement auto-restart with bounded delays
5. ❌ DON'T ignore `onError()` callbacks - implement graceful error handling with logging
6. ❌ DON'T assume Toast will work in background - always use handler.post() for UI updates
7. ❌ DON'T use deprecated WindowManager flags without API level fallback

### DEFAULT DIALER DO RULES (from lines 2186-2201):
1. ✅ Register InCallService with `android:permission="android.permission.BIND_INCALL_SERVICE"`
2. ✅ Add BIND_INCALL_SERVICE and MANAGE_ONGOING_CALLS permissions to manifest
3. ✅ Check Build.VERSION_CODES.Q before using RoleManager
4. ✅ Store PREF_DIALER_ROLE_REQUESTED to avoid showing request dialog repeatedly
5. ✅ Check SharedPreferences in onStartCommand() to respect toggle state
6. ✅ Call stopForeground(STOP_FOREGROUND_REMOVE) in onDestroy()
7. ✅ Call speechRecognizer.destroy() to release microphone before service stops
8. ✅ Use startForegroundService() on Android O+
9. ✅ Log all service lifecycle events with emoji prefixes
10. ✅ Remove all handler callbacks in onDestroy()
11. ✅ Check permission at runtime before initializing speech recognition
12. ✅ Create NotificationChannel before building notification (Android 8.0+)

### DEFAULT DIALER DON'T RULES (from lines 2203-2218):
1. ❌ DON'T forget to export InCallService: `android:exported="true"`
2. ❌ DON'T assume RoleManager is available - always check API level first
3. ❌ DON'T call startForegroundService() on Android O without proper notification
4. ❌ DON'T block the main thread during service startup
5. ❌ DON'T hardcode permission checks - use ContextCompat.checkSelfPermission()
6. ❌ DON'T forget to call stopForeground() when service is destroyed
7. ❌ DON'T leave handler callbacks pending - call removeCallbacks() in onDestroy()
8. ❌ DON'T assume speech recognizer.stop() releases microphone - must call destroy()
9. ❌ DON'T ignore onDestroy() - cleanup all resources
10. ❌ DON'T call requestDefaultDialerRole() every time - track with SharedPreferences
11. ❌ DON'T ignore TelecomManager fallback for Android 7-9

## Phase 2: Implementation Approach

### Problem 1: Default Dialer Role Not Active
**Root Causes:**
- MainActivity.requestDefaultDialerRole() not being called
- AndroidManifest.xml missing required intent-filters (ACTION_DIAL, ACTION_CALL_BUTTON, tel: scheme)
- RoleManager/TelecomManager not properly handling the intent
- SharedPreferences not tracking the request

**Fix:**
1. Ensure onCreate() calls requestDefaultDialerRole()
2. Add required intent-filters to MainActivity in AndroidManifest.xml
3. Use proper API checks (Build.VERSION_CODES.Q for RoleManager)
4. Track with SharedPreferences key: PREF_DIALER_ROLE_REQUESTED
5. Log each step with emoji prefixes for debugging

### Problem 2: Speech Recognition Not Hearing
**Root Causes:**
- GoogleSpeechRecognizer not initialized in ScamMonitorService.onStartCommand()
- Service not checking RECORD_AUDIO permission at runtime
- SpeechRecognizer.startListening() not called on main thread
- onResults()/onError() callbacks not auto-restarting listening
- Handler race conditions during initialization

**Fix:**
1. In onStartCommand(), use Handler(Looper.getMainLooper()).post() to init on main thread
2. Check RECORD_AUDIO permission with ContextCompat.checkSelfPermission()
3. Call startForeground() IMMEDIATELY at top of onStartCommand() (within 5 seconds)
4. Ensure GoogleSpeechRecognizer auto-restarts in both onResults() and onError()
5. Verify SpeechRecognizer.isRecognitionAvailable() before creating

### Problem 3: No Debug Visibility (Can't See What's Happening)
**Root Cause:**
- Only Logcat shows what's happening - user can't see it
- Service runs in background without user-visible feedback
- When something fails, it's silent

**Fix:**
1. Create DebugLogWindow class with TYPE_APPLICATION_OVERLAY (API 26+) OR use in-app TextView
2. Implement logToScreen(String message) method
3. Show real-time events:
   - "LOG: Service started"
   - "LOG: Permission check: PASSED/FAILED"
   - "LOG: Dialer role granted: TRUE/FALSE"
   - "LOG: Speech engine: READY/ERROR"
   - "LOG: Heard: [text]"
   - "LOG: Error code: [code]"
4. Use Handler to post UI updates from background threads
5. Make it semi-transparent + draggable + dismissible

## Phase 3: Implementation Plan

### Step 1: Fix AndroidManifest.xml
```
- Verify BIND_INCALL_SERVICE permission
- Verify MANAGE_ONGOING_CALLS permission
- Add intent-filter to MainActivity:
  - ACTION_DIAL (scheme="tel")
  - ACTION_CALL_BUTTON
  - Metadata: android.app.dialer.default=true
```

### Step 2: Enhance MainActivity.requestDefaultDialerRole()
```
- Call on onCreate() (line ~65)
- Check Build.VERSION_CODES.Q
- Use RoleManager.createRequestRoleIntent()
- Fallback to TelecomManager for API 24-29
- Track with SharedPreferences: PREF_DIALER_ROLE_REQUESTED
- Log all steps with emoji: "✅ Dialer role granted", "❌ Error: [msg]"
```

### Step 3: Fix ScamMonitorService.onStartCommand()
```
- Call startForeground() FIRST (before initialization)
- Use Handler(Looper.getMainLooper()).post() to init on main thread
- Check RECORD_AUDIO permission with ContextCompat.checkSelfPermission()
- Initialize GoogleSpeechRecognizer in background
- Ensure auto-restart in onResults() and onError()
- Log all steps
```

### Step 4: Create Debug Log System
```
Option A (Recommended): In-app floating TextView
- Create DebugLogWindow class
- Store in MainActivity
- Show real-time service status
- Make draggable (TYPE_APPLICATION_OVERLAY on API 26+)

Option B (Simpler): In-activity TextView
- Add to MainActivity layout
- Update via handler.post()
- Limited to when app is open
```

### Step 5: Implement logToScreen() Method
```
- Format: "[HH:mm:ss] LOG: [message]"
- Queue last 20 messages
- Show emoji prefixes: ✅ 📢 ❌ 🎤 🔄
- Use Handler to ensure main thread
```

### Step 6: Update AGENTS.md
```
- Add section: "Visible Debug Logging Architecture"
- Document DebugLogWindow implementation
- Add 15+ DO/DON'Ts for debug logging
- Add troubleshooting guide for "silent failures"
```

## Phase 4: Testing Approach

### Test Case 1: Default Dialer Role
- [ ] App launch → RoleManager dialog appears
- [ ] Dialog → "Set as default" → System confirms
- [ ] Settings > Apps > Default → Shows ScamShield
- [ ] Close/reopen app → Dialog should NOT appear again (SharedPreferences tracked)

### Test Case 2: Speech Recognition
- [ ] Service toggle ON → "LOG: Service started"
- [ ] Incoming call → "LOG: Listening for scam keywords"
- [ ] Speak: "Please verify your OTP" → "LOG: 📢 Heard: please verify your OTP"
- [ ] Keyword match → "LOG: 🚨 SCAM DETECTED"

### Test Case 3: Permission Handling
- [ ] Deny RECORD_AUDIO → "LOG: ❌ Permission denied"
- [ ] Grant RECORD_AUDIO → "LOG: ✅ Permission granted"
- [ ] Toggle OFF → "LOG: 🛑 Service stopped"

### Test Case 4: Error Recovery
- [ ] Unplug WiFi → "LOG: 🔄 Network error, retrying..."
- [ ] No speech → "LOG: ⚠️ No match, listening..."
- [ ] Replug WiFi → "LOG: 🎤 Listening resumed"

## Phase 5: Validation Checklist

Before submitting:
- [ ] App builds without errors
- [ ] All DO rules from AGENTS.md are followed
- [ ] All DON'T rules are avoided
- [ ] Debug log shows all lifecycle events
- [ ] Service properly initializes on main thread
- [ ] SpeechRecognizer auto-restarts after each onResults()
- [ ] Permissions are checked at runtime
- [ ] Handler cleanup happens in onDestroy()
- [ ] No memory leaks (handler tasks removed)
- [ ] SharedPreferences tracks PREF_DIALER_ROLE_REQUESTED
- [ ] Notification appears immediately (within 5 seconds)
