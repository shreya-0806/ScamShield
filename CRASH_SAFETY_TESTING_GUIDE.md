# ScamShield Crash Safety Testing Guide

## Overview
This document provides step-by-step instructions for testing the crash safety fixes implemented for Moto/Redmi devices.

**Build Status:** ✅ BUILD SUCCESSFUL (33 seconds)
**APK Location:** `app/build/outputs/apk/debug/app-debug.apk` (5.96 MB)
**Git Commit:** `77c05fb` - "fix: implement comprehensive crash safety fixes for Moto/Redmi devices"

---

## Crash Safety Fixes Implemented

### Fix #1: Application Context for WindowManager
- **File:** `DebugLogWindow.java` line 75
- **Change:** `activity.getApplicationContext().getSystemService(Context.WINDOW_SERVICE)`
- **Purpose:** Survive Activity lifecycle, prevent crashes when Activity destroyed while overlay active
- **Verification:** Check for "✅ WindowManager obtained from Application Context" in logcat

### Fix #2: WindowManager Null Checks
- **File:** `DebugLogWindow.java` lines 75, 94, 113
- **Change:** Multiple `if (windowManager == null)` checks before all operations
- **Purpose:** Prevent NullPointerException on devices with restricted WindowManager access
- **Verification:** No "❌ CRASH: NullPointerException" in logcat

### Fix #3: Exception Handling for WindowManager.addView()
- **File:** `DebugLogWindow.java` lines 162-181
- **Change:** Try-catch with specific exception types:
  - `WindowManager.BadTokenException` (invalid token)
  - `IllegalArgumentException` (invalid params)
  - `IllegalStateException` (invalid state)
  - General `Exception` (catch-all)
- **Purpose:** Graceful error handling with detailed logging
- **Verification:** Check for "❌ CRASH FIX: BadTokenException" or similar in logcat

### Fix #4: Prevent Double Window Addition
- **File:** `DebugLogWindow.java` line 65, 113-124, 376-392
- **Change:** Check `isWindowAdded` flag and call `removeView()` before `addView()`
- **Purpose:** Prevent BadTokenException from duplicate window additions
- **Verification:** No "BadTokenException" on rapid enable/disable of debug log

### Fix #5: 1-Second SpeechRecognizer Startup Delay
- **File:** `GoogleSpeechRecognizer.java` line 127-141
- **Change:** `handler.postDelayed(() -> { ... }, 1000)` before first `startListening()`
- **Purpose:** Allow OS to set up audio resources after WindowManager overlay created
- **Verification:** Check for "⏳ Delaying speech start by 1 second" in logcat

### Fix #6: Proper SpeechRecognizer Cleanup
- **File:** `GoogleSpeechRecognizer.java` lines 444, 448, 467
- **Change:** 
  1. `handler.removeCallbacksAndMessages(null)` FIRST
  2. `speechRecognizer.stopListening()` 
  3. `speechRecognizer.destroy()`
  4. `speechRecognizer = null`
- **Purpose:** Release all resources to prevent double-initialization crashes
- **Verification:** Check for "✅ Cleaned up completely" in logcat

### Fix #7: Hardware Acceleration
- **File:** `AndroidManifest.xml` line 30
- **Change:** Added `android:hardwareAccelerated="true"` to `<application>` tag
- **Purpose:** Enable GPU rendering for overlays on Redmi/MIUI devices
- **Verification:** App runs smoothly with debug overlay visible

### Fix #8: Enhanced Crash Handler with Toast Feedback
- **File:** `ScamApplication.java` (complete rewrite)
- **Change:** Global uncaught exception handler that shows Toast before crash
- **Purpose:** User sees crash details instead of silent failure
- **Verification:** Force a crash and see Toast: "⚠️ CRASH: [ExceptionType]\n📍 [ClassName:LineNumber]"

### Fix #9: Crash Logging to File
- **File:** `ScamApplication.java` 
- **Change:** Write full crash stack trace to `/sdcard/Android/data/[package]/files/logs/last_crash.txt`
- **Purpose:** Enable post-mortem debugging
- **Verification:** Check file exists with full stack trace after intentional crash

---

## Testing Checklist

### Phase 1: Pre-Installation Verification
- [ ] APK file exists: `app/build/outputs/apk/debug/app-debug.apk` (5.96 MB)
- [ ] Build completed successfully: "BUILD SUCCESSFUL in 33s"
- [ ] Git commit created: `77c05fb` with all fixes
- [ ] All source files modified correctly:
  - DebugLogWindow.java (415 lines)
  - GoogleSpeechRecognizer.java (476 lines)
  - ScamApplication.java (120+ lines)
  - AndroidManifest.xml (hardware acceleration added)

### Phase 2: Installation & Startup (Moto/Redmi Device Only)
- [ ] Device model: _________________ (Moto/Redmi device required)
- [ ] Android version: _____________
- [ ] adb device connected: `adb devices` shows device online
- [ ] Install APK: `adb install app/build/outputs/apk/debug/app-debug.apk`
- [ ] Grant permissions when prompted (RECORD_AUDIO, POST_NOTIFICATIONS, etc.)
- [ ] Open logcat to monitor events: `adb logcat | findstr ScamShield`

### Phase 3: Startup Event Verification (First Time Only)
**Expected logcat output:**
```
✅ WindowManager obtained from Application Context
✅ Global exception handler installed
✅ Debug listener registered
🔧 Creating foreground notification...
✅ Foreground notification created
✅ RECORD_AUDIO permission verified
⏳ Delaying speech start by 1 second (crash safety)...
📢 Started listening for speech input
✅ Google On-Device Speech Recognizer created
```

**Checklist:**
- [ ] No "❌ CRASH" messages in logcat
- [ ] No "NullPointerException" errors
- [ ] "⏳ Delaying speech start by 1 second" appears
- [ ] "📢 Started listening for speech input" appears
- [ ] App does NOT crash after beep sound

### Phase 4: Core Functionality Test - Speech Recognition
**Procedure:**
1. Trigger incoming call (use test call or Google Dialer test)
2. Listen for "tu tu tu" beep sound (indicates recognizer ready)
3. Immediately after beep (within 1 second), speak a test phrase
4. Example phrases:
   - Non-scam: "Hello, how are you?"
   - Scam: "Verify your OTP" or "Bank security team"

**Checklist:**
- [ ] Beep sound plays (device volume must be ON)
- [ ] Within ~600ms after beep, app is ready to listen
- [ ] Speaking after beep, Toast appears: "📢 Heard: [word]" for each partial result
- [ ] Final result appears: "✅ Final result: [your phrase]"
- [ ] App does NOT crash after beep
- [ ] App does NOT miss voice input (test at least 3 times)

**Expected logcat:**
```
[14:25:10] 🎤 Ready for speech input
[14:25:12] 🔄 Partial result: 'verify'
[14:25:13] 🔄 Partial result: 'verify your'
[14:25:14] 🔄 Partial result: 'verify your otp'
[14:25:15] ✅ Final result: 'verify your OTP'
[14:25:15] 🔄 Auto-restarting listening after onResults()
```

### Phase 5: Debug Window Lifecycle Test
**Procedure:**
1. Open MainActivity (main app screen)
2. Open Settings (gear icon in bottom nav)
3. Find "Scam Detection" section
4. Toggle debug log ON/OFF 5 times rapidly

**Checklist:**
- [ ] Debug log window appears when toggled ON
- [ ] Debug log window disappears when toggled OFF
- [ ] No "BadTokenException" errors in logcat
- [ ] No crashes during rapid toggle
- [ ] Window positioning is correct (bottom-left of screen)
- [ ] Text is visible (green #00FF00 on dark background)

**Expected logcat:**
```
✅ Debug log initialized
🔧 About to add window (type=2009)
✅ Debug window added successfully
```

### Phase 6: Screen Rotation Test
**Procedure:**
1. Enable debug log
2. Rotate device from portrait to landscape and back
3. Repeat 5 times

**Checklist:**
- [ ] Debug window persists after rotation
- [ ] OR gracefully re-initializes if Activity destroyed
- [ ] No "BadTokenException" after rotation
- [ ] App does NOT crash
- [ ] Window position adjusts to new orientation

### Phase 7: Backgrounding/Foreground Test
**Procedure:**
1. Enable debug log
2. App is in foreground (MainActivity visible)
3. Press Home button (app goes to background)
4. Wait 10 seconds
5. Tap app icon to return to foreground

**Checklist:**
- [ ] App returns to foreground without crash
- [ ] Debug window still present (if it was on)
- [ ] Speech recognition resumes automatically
- [ ] No resource leaks (monitor with adb shell dumpsys meminfo)

### Phase 8: Crash Feedback Test (Intentional)
**Procedure:**
1. Create a test crash scenario:
   - Option A: Force NullPointerException via dev menu (if available)
   - Option B: Manually trigger crash by modifying code temporarily
   - Option C: Use app debugger to throw exception

**OR** Skip this if not able to safely trigger crash

**Checklist:**
- [ ] Toast appears showing: "⚠️ CRASH: [ExceptionType]\n📍 [Location]"
- [ ] Toast visible for at least 1 second before app closes
- [ ] Crash log file created: `/sdcard/Android/data/com.shreyanshi.scamshield/files/logs/last_crash.txt`
- [ ] File contains full stack trace with line numbers
- [ ] Logcat shows exception details

**Expected logcat:**
```
❌ UNCAUGHT EXCEPTION: NullPointerException at ClassName:123
Exception logged to: /sdcard/Android/data/com.shreyanshi.scamshield/files/logs/last_crash.txt
📍 Crash location: com.shreyanshi.scamshield.utils.DebugLogWindow:165
```

### Phase 9: Memory Leak Check
**Procedure:**
1. Enable debug log
2. Toggle debug log ON/OFF 20 times rapidly
3. Use adb to check memory usage:
   ```bash
   adb shell dumpsys meminfo com.shreyanshi.scamshield | findstr "TOTAL"
   ```
4. Wait 1 minute
5. Run dumpsys again
6. Compare memory values

**Checklist:**
- [ ] Memory usage does NOT increase significantly (<10% growth)
- [ ] No "memory leak" warnings in logcat
- [ ] Window references properly cleaned up (check via debugger)

### Phase 10: Stress Test - Rapid Enable/Disable
**Procedure:**
1. Go to Settings > Scam Detection
2. Toggle debug log ON
3. Toggle OFF immediately
4. Repeat 20 times as fast as possible
5. Monitor logcat for exceptions

**Checklist:**
- [ ] No "BadTokenException" errors
- [ ] No "NullPointerException" errors
- [ ] App remains responsive (no ANR - Application Not Responding)
- [ ] No crashes occur
- [ ] All 20 toggles complete successfully

**Expected behavior:** App handles rapid toggles gracefully without errors

---

## Troubleshooting Guide

### Issue: App crashes immediately after beep
**Diagnosis:**
- Check logcat for: "NullPointerException" or "BadTokenException"
- Verify Fix #1: WindowManager using Application Context
- Verify Fix #5: 1-second startup delay implemented

**Resolution:**
1. Check `DebugLogWindow.java` line 75: `activity.getApplicationContext().getSystemService()`
2. Check `GoogleSpeechRecognizer.java` line 127: `handler.postDelayed(..., 1000)`
3. Rebuild and reinstall APK

### Issue: Debug window invisible or not appearing
**Diagnosis:**
- Check logcat for: "CRASH FIX: BadTokenException"
- Verify Fix #2: windowManager null checks
- Verify Fix #3: Exception handling

**Resolution:**
1. Open logcat and look for error messages
2. Check if exception details show the root cause
3. Verify `isWindowAdded` flag logic in DebugLogWindow.java
4. On Moto/Redmi devices with restrictions, app may fall back to Frame Layout approach

### Issue: App crashes on screen rotation
**Diagnosis:**
- Check logcat for: "Activity destroyed" or "BadTokenException"
- Verify Activity.onDestroy() calls debugLogWindow.destroy()
- Verify window properly removed before Activity destruction

**Resolution:**
1. Ensure `DebugLogWindow.destroy()` called in `MainActivity.onDestroy()`
2. Verify `removeView()` properly handles IllegalArgumentException
3. Check for lingering references to destroyed Activity

### Issue: Toast doesn't appear when app crashes
**Diagnosis:**
- Check if ScamApplication.onCreate() called (verify in logcat)
- Verify global exception handler installed: "✅ Global exception handler installed"
- Check if exception is caught somewhere else before reaching handler

**Resolution:**
1. Verify `AGENTS.md` line 2804-2829 exception handling is in place
2. Ensure Handler(Looper.getMainLooper()) used for Toast on non-UI thread
3. Check if exception happens in onCreate() before handler installed

### Issue: Speech recognition misses voice after beep
**Diagnosis:**
- Check logcat for: "⏳ Delaying speech start"
- Verify timing: "📢 Started listening" should appear ~1000ms after beep
- User speaking at ~500ms might miss recognition

**Resolution:**
1. Verify 1-second delay still in place: `handler.postDelayed(..., 1000)`
2. Increase delay to 1500ms if device is slow
3. Test with device at normal temperature (not hot)

### Issue: Memory usage increases with toggle
**Diagnosis:**
- Run: `adb shell dumpsys meminfo com.shreyanshi.scamshield`
- Check "TOTAL" value before and after 20 toggles
- Verify less than 10% growth

**Resolution:**
1. Check `DebugLogWindow.destroy()` properly nulls all references
2. Verify `GoogleSpeechRecognizer.destroy()` calls `removeCallbacksAndMessages()`
3. Use Android Studio Profiler to check heap snapshots

---

## Device Requirements

**Minimum Requirements:**
- Android version: 7.0 (API 24) or higher
- **Preferred test devices:** Moto G, Redmi Note, Redmi Pro (Moto/Redmi ROM variants)
- Screen size: 4.5" or larger
- Storage: 200 MB free space
- Microphone: Functional (for speech recognition testing)
- Volume: Device volume MUST be ON for beep sound

**Testing Environment:**
- adb installed and configured
- Device in developer mode with USB debugging enabled
- Device connected via USB to development machine
- Logcat available for real-time monitoring

---

## Success Criteria

**All tests pass if:**
1. ✅ No "NullPointerException" crashes on beep
2. ✅ Speech recognized correctly after beep (test 3+ times)
3. ✅ Debug window appears/disappears without BadTokenException
4. ✅ Rapid toggle (20x) completes without crashes
5. ✅ App survives screen rotation
6. ✅ Memory usage stable (<10% growth after 20 toggles)
7. ✅ No resource leaks detected
8. ✅ Crash handler shows Toast with error details (if tested)

**If all criteria met: FIXES SUCCESSFUL** ✅

---

## Real-Device Test Evidence

Please record the following for validation:
1. Device model and Android version
2. Logcat screenshots showing:
   - "⏳ Delaying speech start by 1 second"
   - "✅ WindowManager obtained from Application Context"
   - "📢 Final result: [your test phrase]"
3. Number of successful speech recognition tests (test at least 3)
4. Debug window toggle count without crashes
5. Any exceptions found during testing

---

## Next Steps After Testing

If all tests pass:
1. ✅ Document test results (device, Android version, success count)
2. ✅ Create new git branch: `feature/crash-safety-verified`
3. ✅ Commit test evidence to branch
4. ✅ Create Pull Request documenting test environment and results
5. ✅ Prepare for production build and Play Store release

If issues found:
1. ❌ Document exact error messages and logcat output
2. ❌ Identify which Fix (#1-9) is not working
3. ❌ Create git branch: `fix/crash-safety-debug-[issue-name]`
4. ❌ Debug and apply targeted fix
5. ❌ Rebuild and re-test

---

## Contact & Support

For questions about crash safety fixes, refer to:
- **AGENTS.md:** Section "Crash Safety Fixes for WindowManager and Speech Recognition"
- **Source files:** Each file has detailed comments explaining fixes
- **Git commit:** `77c05fb` contains complete implementation

**Questions about specific fixes:**
- Fix #1 (Application Context): See DebugLogWindow.java constructor
- Fix #5 (Startup delay): See GoogleSpeechRecognizer.java start() method
- Fix #8 (Hardware acceleration): See AndroidManifest.xml application tag
- Fix #9 (Crash handler): See ScamApplication.java global exception handler

