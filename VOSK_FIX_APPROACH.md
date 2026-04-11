# ScamShield - Vosk Model Integration Fix & Notification Crash Resolution

## STEP 1: Extract DO & DON'T from AGENTS.md

### Relevant DO Rules
1. ✅ **Always bundle Vosk model in APK assets** (path: `assets/vosk-model/`)
2. ✅ **Use `startForegroundService()` for monitoring services on Android O+**
3. ✅ **Handle all permissions gracefully with fallback UI**
4. ✅ **Add meaningful logging with TAG constants**
5. ✅ **Use NotificationCompat for backward compatibility**

### Relevant DON'T Rules
1. ❌ **DON'T assume permissions are granted** - always check and request
2. ❌ **DON'T block the main thread** - use ExecutorService for background tasks
3. ❌ **DON'T use launcher icons for notification small icons**
4. ❌ **DON'T use colored icons for notifications** - MUST be pure white (#FFFFFF) monochrome
5. ❌ **DON'T assume Vosk model is loaded** - always check with isAvailable()

---

## STEP 2: Approach Explanation

### The Problem
The app shows "Vosk Model / Offline Model Loading" in settings, which means:

1. **Vosk initialization is async** - Model loads in background thread
2. **Settings reads model status immediately** - Before async loading completes
3. **No callback mechanism** - Settings doesn't know when model finishes loading
4. **Race condition** - Service starts before model is ready
5. **Fallback doesn't activate** - Google Speech not triggered when Vosk not available

### Root Cause Chain
```
BroadcastReceiver (Incoming Call)
    ↓ (immediately triggers)
ScamMonitorService.onStartCommand()
    ↓ (within ~50ms)
SettingsFragment queries model status
    ↓ (model still in AsyncTask - takes ~1-2 seconds)
Vosk model NOT ready yet
    ↓
onSpeechRecognized() receives null from Vosk
    ↓
App cannot detect scam keywords
    ↓
Alert never triggers = User unprotected
```

### Fix Strategy (5 Layers)

**Layer 1: Model Loading**
- Improve async loading with progress callbacks
- Add model ready state tracking
- Implement retry mechanism for failed loads

**Layer 2: Service Initialization**
- Wait for model availability before starting listening
- Add timeout fallback to Google Speech
- Log detailed initialization status

**Layer 3: Settings UI**
- Show real-time model loading status with spinner
- Update status when model becomes available
- Display estimated loading time

**Layer 4: Error Recovery**
- If Vosk fails, automatically switch to Google Speech
- Log which engine is active
- Retry Vosk periodically

**Layer 5: Notification Crash**
- Already fixed in previous response (icon validation)
- Ensure icon resource exists before use
- Add defensive checks

---

## STEP 3: Implementation Plan

### Change 1: Enhanced VoskProcessor with Callbacks
- Add ModelLoadingListener interface
- Implement progress tracking
- Add model ready notification

### Change 2: Improved ScamMonitorService
- Wait for Vosk availability before starting speech recognition
- Add timeout to switch to Google Speech fallback
- Log which engine is active

### Change 3: Updated SettingsFragment
- Show loading spinner while model initializes
- Display "Ready" or "Loading..." status
- Update in real-time as model loads

### Change 4: Better Error Handling
- Retry model loading if fails
- Validate model structure before use
- Add detailed logging

### Change 5: Updated AGENTS.md
- Add Vosk integration best practices
- Document async loading pattern
- Explain model ready state tracking

---

## Expected Outcome

**Before Fix:**
```
Settings: "Vosk Model / Offline Model Loading" (stuck)
Service: Cannot detect scam keywords
User: Unprotected from scam calls
```

**After Fix:**
```
Settings: "Vosk Model: Ready" (with checkmark)
Service: Detects scam keywords within 2-3 seconds
User: Protected from scam calls
Fallback: Google Speech auto-activates if Vosk unavailable
```

---

## Implementation Sequence

1. ✅ Update VoskProcessor - Add callbacks & model ready state
2. ✅ Improve ScamMonitorService - Wait for model & timeout logic
3. ✅ Fix SettingsFragment - Real-time status updates
4. ✅ Add error recovery - Retry & fallback mechanisms
5. ✅ Update AGENTS.md - Document best practices
6. ✅ Build and test - Verify all fixes work together

---

