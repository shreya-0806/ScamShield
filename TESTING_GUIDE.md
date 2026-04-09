# ScamShield Testing Guide

## APK Details
- **File**: app-debug.apk (86 MB)
- **Location**: `app/build/outputs/apk/debug/app-debug.apk`
- **Build Time**: April 8, 2026
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34

## Installation Instructions

### Via ADB
```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Via Android Studio
1. Run > Select Device/Emulator > Wait for build to complete

## Critical Features to Test

### 1. App Launch & Permissions (High Priority)
**What to test:**
- [ ] App launches without crashing
- [ ] First launch shows permission request dialog
- [ ] Grant RECORD_AUDIO permission (required)
- [ ] Grant READ_PHONE_STATE permission (required)
- [ ] Grant READ_CONTACTS permission (required for contact suggestions)
- [ ] Grant READ_CALL_LOG permission (required for call history)
- [ ] Grant POST_NOTIFICATIONS permission (for alerts)
- [ ] App loads main screen (Dialer tab) after permissions

**Expected behavior:**
- App should be fully responsive
- All tabs (Dialer, History, Contacts, News, Settings) should be accessible
- No crashes or ANR (Application Not Responding) dialogs

---

### 2. Foreground Service Startup (Critical - High Priority)
**What to test:**
- [ ] Notification appears within 5 seconds of app launch
- [ ] Notification says "ScamShield Active"
- [ ] Notification icon is a white shield (not app launcher icon)
- [ ] Notification stays visible while app is running (ongoing)
- [ ] Tapping notification opens the app

**Expected behavior:**
- Foreground service MUST start quickly to avoid ForegroundServiceDidNotStartInTimeException
- Notification should be visible in status bar immediately

**Check logcat:**
```
adb logcat | grep "ScamShield-Monitor"
```
Look for: `startForeground successful`

---

### 3. Dialer Pad (High Priority)
**What to test:**
- [ ] Number entry: tap numbers 0-9, they appear in display
- [ ] Backspace button deletes last digit
- [ ] Keypad buttons have proper styling (dark theme)
- [ ] When typing a number, contact suggestions appear below keypad
- [ ] Contact suggestions show name, number, avatar
- [ ] Tapping a suggestion fills the number and can call immediately
- [ ] Quick call button (green icon) initiates call to the number
- [ ] Call ends properly after initiated

**Expected behavior:**
- Smooth, responsive number entry
- Contact suggestions load within 1 second
- No lag when typing numbers
- Call initiates using default dialer

---

### 4. Contacts List (High Priority)
**What to test:**
- [ ] Contact list loads with all device contacts
- [ ] Each contact shows: avatar, name, number
- [ ] Star icon (favorite) - tap to toggle, gold color when active
- [ ] Block icon - tap to toggle, red color when blocked
- [ ] Delete button removes contact from app (not system contacts)
- [ ] Search bar filters contacts by name or number in real-time
- [ ] Empty state message if no contacts exist

**Expected behavior:**
- Smooth scrolling through large contact lists
- Search results update instantly as you type
- Favorite/block status persists after app restart

---

### 5. Call History (High Priority)
**What to test:**
- [ ] Call history loads when tab is tapped
- [ ] Each call shows: contact name/number, time only (no date)
- [ ] Call icon indicator (incoming/outgoing)
- [ ] Scam indicator badge/color if call was flagged
- [ ] Tapping call initiates new call
- [ ] Older calls appear below recent ones
- [ ] Empty state message if no call history exists

**Expected behavior:**
- Time format: HH:MM (e.g., "14:30")
- Calls logged for both incoming and outgoing
- History updates in real-time after new calls

---

### 6. News Feed (High Priority)
**What to test:**
- [ ] News feed loads with articles about scam prevention
- [ ] Each news item shows: image, title, description, date
- [ ] "Read More" button opens article in browser
- [ ] Images load correctly (no broken images)
- [ ] Empty state message if no news available
- [ ] Pull-to-refresh updates news (if implemented)

**Expected behavior:**
- News loads from Google News RSS feed
- Articles are relevant to scam prevention
- Browser opens externally, not in-app

---

### 7. Settings (Medium Priority)
**What to test:**
- [ ] Settings tab opens without crashing
- [ ] System Status section shows:
  - [ ] "Scam Protection: Active/Inactive"
  - [ ] "Vosk Model: Ready/Loading"
  - [ ] "Foreground Service: Running/Stopped"
- [ ] All toggles/switches work
- [ ] Dark theme is applied (background #121212)
- [ ] Text is readable in dark theme
- [ ] No dark mode toggle (always dark)

**Expected behavior:**
- Settings persist after app restart
- Status updates accurately

---

### 8. Scam Detection (High Priority)
**What to test:**
- [ ] Incoming call triggers speech recognition
- [ ] Microphone captures audio (if supported by device)
- [ ] Speech is converted to text and logged
- [ ] Text is compared against scam keywords list
- [ ] If match found, ScamAlertActivity pops up
- [ ] Alert shows: "Scam Detected!" message
- [ ] Alert has two buttons: "Dismiss" and "End Call"
- [ ] "Dismiss" closes alert
- [ ] "End Call" ends the current call

**Expected behavior:**
- Alert appears within 2-3 seconds of scam keyword match
- Alert is visible on lock screen
- Alert is non-dismissible until action taken (except Dismiss button)
- Call can be ended from alert

**Testing tips:**
- Call the device while recording a voice message with keywords like:
  - "OTP", "verify account", "bank transfer", "card number", "urgent action"
  - Use Google Text-to-Speech or similar to generate test audio
- Use Vosk (offline) or Google Speech Recognition (online)

---

### 9. Edge Cases (Medium Priority)
**What to test:**
- [ ] App backgrounding: put app in background, receive call, alert still shows
- [ ] Device rotation: rotate device mid-call, UI adjusts properly
- [ ] Low memory: simulate low memory, app doesn't crash
- [ ] Permission denial: deny RECORD_AUDIO, app shows warning, still usable
- [ ] Network loss: turn off internet, news feed shows gracefully
- [ ] Phone lock: app continues monitoring while device is locked
- [ ] App force close: kill app from recent apps, service restarts

**Expected behavior:**
- App is resilient to system interruptions
- No crashes in any scenario

---

### 10. Logcat Verification (Medium Priority)

**Check for errors:**
```bash
adb logcat *:E | grep -E "ScamShield|Exception|Error"
```

**Expected logs:**
- `ScamShield-Monitor: startForeground successful`
- `ScamShield-Receiver: Call state changed`
- `VoskProcessor: Initialized successfully`

**Should NOT see:**
- `ForegroundServiceDidNotStartInTimeException`
- `BadForegroundServiceNotificationException`
- `SQLiteException`
- `ClassCastException`
- `NullPointerException` (unless handled gracefully)

---

## Performance Testing

### Startup Time
- Measure time from app tap to seeing Dialer tab
- Target: < 3 seconds on modern devices

### Memory Usage
- Check Memory Profiler in Android Studio
- Target: < 150 MB on first launch

### Battery Impact
- Leave app running for 1 hour
- Check battery usage in Settings > Battery
- Target: < 5% battery drain for 1 hour (foreground service)

### APK Size
- Current APK: 86 MB
- Breakdown:
  - Vosk model: ~40 MB (included in assets)
  - DEX files: ~20 MB (code + libraries)
  - Resources (layouts, drawables): ~15 MB

---

## Known Limitations

1. **Call Recording**: Android 10+ restricts call audio access. App uses:
   - Microphone to capture ambient audio during calls
   - Speech recognition on captured audio
   - Keyword matching for scam detection

2. **Vosk Model**: Requires ~40 MB in APK
   - Only English (India) model included
   - Offline STT slightly less accurate than Google's

3. **Call History**: Reads from system CallLog provider
   - May not include all call types (depends on device ROM)

---

## Test Results Template

### Device Info
- Device: ___________
- Android Version: ___________
- RAM: ___________

### Test Results
| Feature | Status | Notes |
|---------|--------|-------|
| App Launch | ✓/✗ | |
| Permissions | ✓/✗ | |
| Foreground Service | ✓/✗ | |
| Dialer Pad | ✓/✗ | |
| Contacts List | ✓/✗ | |
| Call History | ✓/✗ | |
| News Feed | ✓/✗ | |
| Settings | ✓/✗ | |
| Scam Detection | ✓/✗ | |
| Alert Popup | ✓/✗ | |

### Issues Found
1. ___________
2. ___________

---

## How to Report Issues

If you find a bug:
1. Note the exact steps to reproduce
2. Capture logcat output: `adb logcat > logcat.txt`
3. Take screenshot if relevant
4. Report in the GitHub issues or send details for fixes

---

## Build Variants

If you need to rebuild:
```bash
cd ScamShield
./gradlew clean assembleDebug      # Debug APK (current)
./gradlew clean assembleRelease    # Release APK (if signing configured)
```

Debug APK is always available at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Useful ADB Commands

```bash
# Install APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Clear app data
adb shell pm clear com.shreyanshi.scamshield

# View logs
adb logcat | grep ScamShield

# Simulate incoming call
adb shell am broadcast -a com.android.intent.action.PHONE_STATE

# Check running services
adb shell dumpsys activity services com.shreyanshi.scamshield
```

---

## Next Steps After Testing

1. Fix any bugs found during testing
2. Optimize for battery/memory if needed
3. Build release APK with ProGuard obfuscation
4. Prepare Play Store metadata (screenshots, description)
5. Submit to Google Play Store

---
