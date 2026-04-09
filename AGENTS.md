# ScamShield - Development Guidelines

## Project Overview
ScamShield is an Android app that detects scam calls in real-time using offline speech recognition (Vosk) and displays alerts to protect users from fraud.

## Architecture
- **Language**: Java (Android)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34
- **Architecture**: Fragment-based with BottomNavigationView
- **Key Libraries**: Vosk (offline STT), Material Design

## Core Components
- `ScamMonitorService` - Foreground service for call monitoring
- `ScamAlertActivity` - Alert display (Activity-based, no overlay)
- `CallReceiver` - BroadcastReceiver for call state changes
- `VoskProcessor` - Offline speech-to-text processing
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
1. Always bundle Vosk model in APK assets (path: `assets/vosk-model/`)
2. Use `startForegroundService()` for monitoring services on Android O+
3. Handle all permissions gracefully with fallback UI
4. Use dark theme (#121212 background) for consistent UI
5. Process audio locally - never upload without explicit user consent
6. Add meaningful logging with TAG constants
7. Register all services in AndroidManifest.xml
8. Test on Moto/Stock Android devices for compatibility
9. Use Activity-based alerts instead of SYSTEM_ALERT_WINDOW overlay
10. Fetch scam news from Google News RSS feed
11. Read call history from system CallLog provider
12. Use custom app icon from `icon/shieldicon.jpeg` (regenerate mipmap icons when changed)
13. Dark mode toggle in Settings applies theme immediately with recreate()
14. Contacts use BlockedNumberDatabase for block/unblock functionality

## DON'T
1. DON'T require SYSTEM_ALERT_WINDOW (causes install issues on Moto devices)
2. DON'T permanently save audio recordings - only process in memory
3. DON'T assume permissions are granted - always check and request
4. DON'T block the main thread - use ExecutorService for background tasks
5. DON'T hardcode URLs or API keys - use SharedPreferences or BuildConfig
6. DON'T use deprecated APIs without fallback for older Android versions
7. DON'T require overlay permission for any feature

## Common Issues & Solutions

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

## Build Commands
```bash
./gradlew assembleDebug    # Debug build
./gradlew assembleRelease # Release build
```

## Vosk Model Setup
The Vosk model should be placed at:
```
app/src/main/assets/vosk-model/
├── am/
├── conf/
├── graph/
├── ivector/
└── README
```

Download from: https://alphacephei.com/vosk/models
Recommended model: vosk-model-small-en-in-0.4 (for India English)

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
