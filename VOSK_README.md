Vosk integration (Option B)

This project includes a reflection-based `VoskProcessor` stub so the app compiles without Vosk libraries; to enable Vosk:

1) Add Vosk Android dependency to `app/build.gradle` (example):

   implementation 'org.vosk:vosk-android:0.3.38'

   (Pick latest stable version from Vosk releases.)

2) Download an appropriate Vosk model (small English model recommended for testing):
   - e.g., `https://alphacephei.com/vosk/models` choose a model and download.

3) Place the model folder under the app's files directory at runtime, i.e. `context.getFilesDir()/vosk-model`.
   - On development device, you can push the model using `adb push` to the app's file directory:
     - First install the app on your device/emulator.
     - Use `adb shell run-as com.shreyanshi.scamshield mkdir -p /data/data/com.shreyanshi.scamshield/files/vosk-model`
     - Unzip the model on your PC and then `adb push <model-folder> /data/data/com.shreyanshi.scamshield/files/vosk-model`.

4) Run the app and enable Scam Detection in Settings; the app will detect that Vosk classes & model are present and use the `VoskProcessor`.

Notes:
- Vosk models are large; consider implementing an in-app download flow or hosting models on your server.
- The reflection approach avoids adding a hard dependency; once you add the Vosk dependency, the calls will work without further code changes.
- Testing: when Vosk detects keywords it broadcasts `com.shreyanshi.scamshield.VOSK_DETECTED` which `LiveDetectionService` listens for and shows the overlay.

Legal: Ensure user consent before recording/transcribing calls.
