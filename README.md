# JARVIS Phone

A Stark-style voice assistant for Android, built to run entirely on your phone.

## What it does
- Tap the HUD core and speak; JARVIS listens, replies out loud, and shows the transcript
- Phone commands that work offline and instantly:
  - "call <name or number>" (contacts lookup, opens the dialer)
  - "set an alarm for 7:30 am"
  - "turn the torch on / off"
  - "open youtube / whatsapp / camera / settings"
  - "what time is it", "battery", "note remember to..."
- Everything else goes to Gemini (gemini-2.0-flash) with a short-term conversation memory

## Setup
1. Install the debug APK (from GitHub Actions artifacts, or build it yourself with `gradle assembleDebug`)
2. On first launch, paste your Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey). It is stored in the app's private storage only and never sent anywhere but Google's API
3. Allow microphone access when prompted
4. Tap the core and say "what time is it"

## Build it yourself
Open the project in Android Studio, or run:
```bash
gradle assembleDebug
```
The APK lands in `app/build/outputs/apk/debug/`.

## Notes
- No third-party dependencies: Kotlin stdlib + Android framework only, so builds stay fast and reliable
- The contact lookup asks the READ_CONTACTS permission the first time you say "call someone"
