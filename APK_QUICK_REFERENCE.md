# ScamShield APK - Quick Reference

## Installation

### Option 1: ADB Command
```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Option 2: Drag & Drop
```
Drag app-debug.apk to Android Studio emulator or connected device
```

### Option 3: File Manager
```
1. Copy app-debug.apk to device
2. Open Files app → Navigate to APK
3. Tap to install
4. Allow unknown sources if prompted
```

---

## APK Information
- **Name**: app-debug.apk
- **Size**: ~86 MB
- **Package**: com.shreyanshi.scamshield
- **Min SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)
- **Built**: April 8, 2026 23:00 UTC

---

## First Time Setup

1. **Install APK** using method above
2. **Open App** → ScamShield will request permissions
3. **Grant Permissions**:
   - ✅ RECORD_AUDIO (required for call monitoring)
   - ✅ READ_PHONE_STATE (required for detecting calls)
   - ✅ READ_CONTACTS (for contact suggestions)
   - ✅ READ_CALL_LOG (for call history)
   - ✅ POST_NOTIFICATIONS (for alerts)
4. **Wait 5 seconds** for foreground service to start
   - You'll see "ScamShield Active" notification
5. **Done!** App is ready to monitor calls

---

## Main Tabs

| Tab | Purpose |
|-----|---------|
| **Dialer** | Make calls, get contact suggestions while dialing |
| **History** | View incoming/outgoing call history with scam indicators |
| **Contacts** | Manage contacts, mark favorites, block numbers |
| **News** | Read latest scam prevention news & tips |
| **Settings** | View system status, toggle features |

---

## Key Features

### ✅ Real-Time Scam Detection
- Listens to incoming calls using microphone
- Converts speech to text (offline with Vosk)
- Compares keywords: "OTP", "bank", "verify account", etc.
- Shows alert if scam detected

### ✅ Contact Management
- ⭐ Mark contacts as favorites (gold star)
- 🚫 Block unwanted contacts (red icon)
- 🔎 Search contacts by name or number
- 📞 Quick dial with contact suggestions

### ✅ Call History
- Logs all incoming & outgoing calls
- Shows time only (no date)
- Marks scam calls with indicator
- Quick redial from history

### ✅ Scam News
- News feed about fraud prevention
- Shows images & descriptions
- "Read More" opens article in browser
- Updated from Google News RSS

### ✅ Dark Theme
- Pure dark background (#121212)
- Easy on eyes in low light
- Applied throughout app

---

## Testing Priority

### 🔴 Critical (Test First)
1. App launches without crashing
2. Notification appears within 5 seconds
3. Permissions granted successfully
4. Dialer pad works (tap numbers, see suggestions)
5. Call detection works (make test call)

### 🟡 Important (Test Next)
6. Contacts list loads & search works
7. Call history displays correctly
8. News feed loads with images
9. Scam detection triggers on keywords
10. Alert popup shows & dismisses

### 🟢 Nice to Have (Test Later)
11. Device rotation doesn't crash
12. Backgrounding works smoothly
13. Low memory handling
14. Battery impact is acceptable

---

## Troubleshooting

### App Crashes on Launch
- **Cause**: Permissions not granted
- **Fix**: Uninstall → Reinstall → Grant all permissions

### Notification Doesn't Appear
- **Cause**: Notification permission denied
- **Fix**: Settings > Apps > ScamShield > Notifications > Allow

### Call Detection Not Working
- **Cause**: READ_PHONE_STATE or RECORD_AUDIO not granted
- **Fix**: Check Settings > Apps > ScamShield > Permissions

### Contacts Not Loading
- **Cause**: READ_CONTACTS not granted
- **Fix**: Grant READ_CONTACTS in app permissions

### News Feed Empty
- **Cause**: No internet connection
- **Fix**: Connect to WiFi/Mobile data, refresh

---

## Logcat Debugging

### View Real-Time Logs
```bash
adb logcat | grep ScamShield
```

### Check for Crashes
```bash
adb logcat *:E | grep Exception
```

### Save Logs to File
```bash
adb logcat > debug_logs.txt
```

### Clear Previous Logs
```bash
adb logcat -c
```

---

## Useful Commands

```bash
# Install APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Uninstall app
adb uninstall com.shreyanshi.scamshield

# Clear app data (reset to first launch)
adb shell pm clear com.shreyanshi.scamshield

# Check if app is running
adb shell dumpsys activity | grep scamshield

# Get device info
adb shell getprop ro.build.version.release
```

---

## File Locations

| Item | Path |
|------|------|
| **APK** | `app/build/outputs/apk/debug/app-debug.apk` |
| **Source** | `app/src/main/java/com/shreyanshi/scamshield/` |
| **Layouts** | `app/src/main/res/layout/` |
| **Vosk Model** | `app/src/main/assets/vosk-model/` |
| **Testing Guide** | `TESTING_GUIDE.md` |

---

## Support

For issues, check:
1. TESTING_GUIDE.md - Detailed testing procedures
2. AGENTS.md - Architecture & development guidelines
3. Logcat output - Debug messages and errors

---

**Last Updated**: April 8, 2026
**Status**: ✅ Ready for Testing
