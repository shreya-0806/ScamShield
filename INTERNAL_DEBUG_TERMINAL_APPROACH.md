# Internal Debug Terminal Implementation - Approach Document

## Problem Analysis

**Current Issue:** WindowManager-based floating overlay crashes on Redmi devices
- Root cause: WindowManager overlay restricted on MIUI (Redmi ROM)
- Symptom: App crashes immediately after beep sound
- The crash prevents debugging (no logcat access on user's device)

**Solution:** Replace WindowManager overlay with Internal Debug Terminal (built into app UI)
- Keep debug logs INSIDE the app, not as overlay
- Display logs in MainActivity UI (40% bottom of screen)
- No WindowManager = no crash on Redmi
- User can see errors/events scrolling in real-time on device

---

## Extracted DO's & DON'T's from AGENTS.md

### DO's (Lines 34-54, 1000-1009, 1606-1621)
1. **Line 2:** "Handle all permissions gracefully with fallback UI"
2. **Line 37:** "Use dark theme (#121212 background) for consistent UI"
3. **Line 39:** "Add meaningful logging with TAG constants"
4. **Line 42:** "Use Activity-based alerts instead of SYSTEM_ALERT_WINDOW overlay"
5. **Line 1002:** "Use Activity-based alerts instead of overlay windows"
6. **Line 1009:** "Log all alert events with timestamps and emoji markers"
7. **Line 1010-1011:** "Use handler.post() for Toast/UI updates from non-main threads"
8. **Line 1614:** "Use SimpleDateFormat for debug log timestamps"
9. **Line 1617:** "Make debug log visible by default on first install"
10. **Line 1631:** "Always call removeCallbacks() in onDestroy()"

### DON'Ts (Lines 57-71, 1012-1023, 1623-1638)
1. **Line 57:** "DON'T require SYSTEM_ALERT_WINDOW (causes install issues on Moto devices)"
2. **Line 63:** "DON'T require overlay permission for any feature"
3. **Line 69:** "DON'T assume Toast will work in background - always use handler.post()"
4. **Line 1016:** "DON'T block main thread with alert display"
5. **Line 1021:** "DON'T use deprecated WindowManager flags"
6. **Line 1631:** "DON'T append to TextViews from non-main threads without handler.post()"
7. **Line 1632:** "DON'T use Toast directly in callbacks - wrap in handler.post()"
8. **Line 1638:** "DON'T assume Exception.getMessage() is not null"

---

## Implementation Approach

### Phase 1: Update UI Layout (activity_main.xml)

**Goal:** Add Internal Debug Terminal to MainActivity UI

**Changes:**
1. Add ScrollView at bottom of activity_main.xml (40% screen height)
2. Inside ScrollView, add TextView with:
   - ID: `internal_debug_log`
   - Background: Black (#000000)
   - Text color: Green (#00FF00)
   - Font: Monospace
   - Padding: 8dp
   - Scrolling: enabled

**AGENTS.md Reference:**
- Line 37: Use dark theme (#121212 background) → Use #000000 for terminal
- Line 1614: Use SimpleDateFormat for timestamps → Will implement in Java

**Implementation:** 5 XML elements added (ScrollView, TextView with attributes)

---

### Phase 2: Create Internal Log Appender (MainActivity.java)

**Goal:** Append timestamped log entries to internal debug terminal

**Method Signature:**
```java
private void appendLog(String message) {
    // Add timestamp: [HH:mm:ss]
    // Append to TextView
    // Auto-scroll to bottom
    // Limit to 50 lines max
}
```

**Key Requirements:**
- Use SimpleDateFormat("HH:mm:ss") for timestamp (AGENTS.md line 1614)
- Must use handler.post() to ensure main thread execution (AGENTS.md line 1631)
- Should include emoji prefixes for easy visual scanning
- Auto-scroll TextView to show latest entries
- Limit to 50 lines to prevent memory bloat (AGENTS.md line 1615)

**AGENTS.md References:**
- Line 1631: "DON'T append to TextViews from non-main threads without handler.post()"
- Line 1614: "Use SimpleDateFormat for debug log timestamps"

---

### Phase 3: Redirect Events to Internal Terminal (ScamMonitorService.java)

**Goal:** Send all speech recognition events to MainActivity for logging

**Events to Log:**
1. Service startup: "✅ ScamMonitorService started"
2. Permission check results:
   - "✅ Checking Overlay Permission: [Granted/Denied]"
   - "✅ Checking Mic Permission: [Granted/Denied]"
3. Speech recognizer initialization:
   - "✅ Speech Recognizer initialized"
   - "❌ Error initializing: [exception message]"
4. SpeechRecognizer callbacks (via LocalBroadcast):
   - onReadyForSpeech(): "🎤 Ready for speech input"
   - onPartialResults(): "📢 Partial: [text]"
   - onResults(): "✅ Final: [text]"
   - onError(): "❌ Error [code]: [description]"

**Implementation Method:**
1. Create LocalBroadcastManager listener in ScamMonitorService
2. Send broadcasts with event data: `Intent.putExtra("log_message", message)`
3. MainActivity receives broadcasts and calls `appendLog(message)`

**AGENTS.md References:**
- Line 1631: Use handler.post() for UI updates from service threads
- Line 1610: Use handler.postDelayed() for delays
- Line 1628: Don't ignore onError() callbacks (implement graceful error handling)

---

### Phase 4: Permission Watchdog (ScamMonitorService.java)

**Goal:** Add permission status to terminal

**Checks to Log:**
1. RECORD_AUDIO permission:
   ```java
   boolean hasRecordAudio = ContextCompat.checkSelfPermission(this, 
       Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
   appendLog("✅ Checking Mic Permission: " + (hasRecordAudio ? "Granted" : "Denied"));
   ```

2. SYSTEM_ALERT_WINDOW permission (if needed for future):
   ```java
   boolean hasOverlay = Settings.canDrawOverlays(this);  // Android 6.0+
   appendLog("✅ Checking Overlay Permission: " + (hasOverlay ? "Granted" : "Denied"));
   ```

**AGENTS.md References:**
- Line 59: "DON'T assume permissions are granted - always check and request"
- Line 1009: "Log all alert events with timestamps and emoji markers"

---

### Phase 5: Remove WindowManager Implementation

**Goal:** Eliminate the crash source (WindowManager overlay)

**Changes:**
1. DO NOT initialize DebugLogWindow in MainActivity
2. Comment out or remove DebugLogWindow references
3. Keep class file for now (just don't use it)

**AGENTS.md Reference:**
- Line 57: "DON'T require SYSTEM_ALERT_WINDOW (causes install issues on Moto devices)"

---

### Phase 6: Wrap SpeechRecognizer in Try-Catch (GoogleSpeechRecognizer.java)

**Goal:** Catch initialization errors and send to terminal instead of crashing

**Change in initializeSpeechRecognizer():**
```java
try {
    speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
    // ... rest of initialization
} catch (Exception e) {
    Log.e(TAG, "❌ Error initializing: " + e.getMessage());
    appendLog("❌ SpeechRecognizer Error: " + e.getMessage());  // Send to terminal
    speechRecognizer = null;
}
```

**AGENTS.md References:**
- Line 1628: "DON'T ignore onError() callbacks - implement graceful error handling"
- Line 1638: "DON'T assume Exception.getMessage() is not null" (add null check)

---

## Files to Modify

### 1. activity_main.xml
- Add ScrollView + TextView at bottom (40% of screen)
- No existing code removal needed

### 2. MainActivity.java
- Add `appendLog(String message)` method
- Add `formatTimestamp()` method with SimpleDateFormat
- Initialize LocalBroadcastManager receiver in onCreate()
- Register/unregister receiver in onStart()/onStop()
- NO DebugLogWindow initialization

### 3. ScamMonitorService.java
- Add LocalBroadcastManager sender
- Log all major events:
  - Startup
  - Permission checks
  - SpeechRecognizer init
  - Error callbacks
- Send broadcasts instead of calling MainActivity directly

### 4. GoogleSpeechRecognizer.java
- Wrap initializeSpeechRecognizer() in try-catch
- Send exception details to service (which broadcasts to MainActivity)

### 5. AGENTS.md
- Add new section: "Internal Debug Terminal Architecture"
- Document the LocalBroadcast pattern
- Add DO's & DON'Ts for internal logging
- Explain why this is safer than WindowManager

---

## Benefits Over WindowManager Approach

| Aspect | WindowManager (Old) | Internal Terminal (New) |
|--------|-------------------|----------------------|
| **Redmi Compatibility** | ❌ Crashes | ✅ Works |
| **Permissions** | Requires SYSTEM_ALERT_WINDOW | No overlay permissions |
| **Crash Source** | WindowManager restricted | None (all internal) |
| **Debug Visibility** | Floating overlay | Built into app UI |
| **User Experience** | App force-closes | App stays open, shows errors |
| **Development** | Complex window management | Simple LocalBroadcast pattern |
| **Performance** | Heavy (OpenGL rendering) | Light (TextView scrolling) |

---

## Expected User Experience (After Implementation)

1. User opens ScamShield on Redmi
2. Bottom 40% of screen shows green terminal on black background
3. User triggers incoming call
4. Terminal shows:
   ```
   [14:23:45] ✅ ScamMonitorService started
   [14:23:46] ✅ Checking Mic Permission: Granted
   [14:23:46] ✅ Checking Overlay Permission: Granted
   [14:23:47] ✅ Speech Recognizer initialized
   [14:23:48] 🎤 Ready for speech input
   [14:23:50] 📢 Partial: 'verify'
   [14:23:51] 📢 Partial: 'verify your'
   [14:23:52] 📢 Partial: 'verify your OTP'
   [14:23:53] ✅ Final: 'verify your OTP'
   [14:23:54] 🚨 SCAM KEYWORD DETECTED: 'otp'
   [14:23:54] ✅ Alert displayed
   ```
5. **App does NOT crash** at any point
6. All errors visible on-screen
7. User can see exactly what app is doing

---

## No Crash Points

**Previous crash points (WindowManager removed):**
- ❌ ~~WindowManager.addView() NullPointerException~~ → Removed
- ❌ ~~BadTokenException on window add~~ → Removed
- ❌ ~~Double window initialization~~ → Removed

**New implementation (safe):**
- ✅ TextView append (safe, main thread only via handler.post())
- ✅ LocalBroadcast (safe, Android framework)
- ✅ Try-catch wrapping (safe error handling)

---

## Implementation Order

1. **Update activity_main.xml** - Add UI layout
2. **Create appendLog() in MainActivity** - Implement terminal
3. **Update ScamMonitorService** - Send events to MainActivity
4. **Update GoogleSpeechRecognizer** - Wrap in try-catch
5. **Remove DebugLogWindow initialization** - Stop using overlay
6. **Update AGENTS.md** - Document new approach
7. **Build & Test** - Verify no crashes on Redmi

---

## Success Criteria

✅ **All of these must be true:**
1. App does NOT crash on beep sound
2. Terminal shows "🎤 Ready for speech input"
3. Terminal shows permissions: Mic & Overlay status
4. Terminal shows speech results in real-time
5. Terminal shows error codes if speech fails
6. App stays open and responsive
7. No WindowManager exceptions in logcat
8. All events visible on device screen

---

## Estimated Implementation Time

- activity_main.xml: 5 minutes
- MainActivity.appendLog(): 10 minutes
- ScamMonitorService broadcasts: 15 minutes
- GoogleSpeechRecognizer try-catch: 5 minutes
- Remove DebugLogWindow: 2 minutes
- AGENTS.md documentation: 20 minutes
- Testing: 30 minutes
- **Total: ~1.5-2 hours**

---

This approach follows AGENTS.md principles:
- ✅ Use Activity-based UI (not overlay)
- ✅ Handle permissions gracefully
- ✅ Use handler.post() for all UI updates
- ✅ Log all events with emoji markers
- ✅ Dark theme (#000000 terminal)
- ✅ No WindowManager = No Moto/Redmi crashes

Ready to implement when approved.
