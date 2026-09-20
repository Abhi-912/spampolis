# SpamPolis — auto-decline calls matching chosen digits (Android)

Blocks spam by **prefix or pattern**: any incoming number matching your list
(e.g. `140`, `160`, `080 4602 XXXX`) is auto-declined (busy tone, no ringing).
Everything else rings normally.

## Pattern syntax

- Plain digits = prefix: `140` blocks anything starting with 140.
- `X` = exactly one digit, `*` = any digits: `080 4602 XXXX` blocks only
  080-4602-0000…9999 (e.g. Shaadi.com) while other `080` callers
  (Amazon/Flipkart/Blinkit deliveries) ring through. `*4602*` blocks
  anything containing 4602 anywhere.
- Spaces/dashes/`+` ignored: `080 4602 XXXX` = `080-4602-xxxx`.
- `080…` and `+91-80…` spellings of the same number are treated as equal
  (national-form matching in `PhoneNormalize`).

## How it works

- `PrefixBlockService` extends `CallScreeningService`. On each incoming call
  `onScreenCall()` normalizes the number (digits only) and checks it against
  stored prefixes. Match → `setDisallowCall(true)` + `setRejectCall(true)`
  (busy tone instead of voicemail, API 29+). No match → call allowed.
- Matching is trunk-aware: `140…` also matches `0140…`, `+91-140…`,
  `91140…`, `0091-140…` — see `PhoneNormalize.variants()`.
- Prefixes + on/off toggle + blocked-call log live in `SharedPreferences`
  (`PrefixStore`, `BlockLogStore`). Longest prefix wins.
- `MainActivity` asks for the **Call Screening role** (`RoleManager`,
  Android 10+) and phone permissions, and lets you add/remove prefixes,
  test a number, and view the blocked log.

## One-time setup on the phone

1. Install the APK, open **SpamPolis**.
2. Grant phone permissions when asked.
3. Tap **Set as Call Screening app** → **Allow**. (Android 10+ required for
   one-tap setup. Without this role Android never calls the service and
   nothing is blocked — the status line tells you.)
4. Add prefixes (`140`, `160` are pre-loaded). Toggle blocking on/off.

## Build (needs Android SDK)

```bash
# 1. Install SDK pieces (macOS):
brew install android-commandlinetools
export ANDROID_HOME=~/Library/Android/sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 2. Build:
cd spampolis
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 (`brew install openjdk@17`). AGP 8.5.2 / Kotlin 1.9.24 /
compileSdk + targetSdk 34 / minSdk 26.

## Files

```
app/src/main/AndroidManifest.xml                      — permissions + CallScreeningService registration
app/src/main/java/com/abhi912/spampolis/
  PrefixBlockService.kt  — screen + auto-decline
  PhoneNormalize.kt      — digits-only + trunk-aware prefix match
  PrefixStore.kt         — prefixes + enabled flag
  BlockLogStore.kt       — last 100 blocked calls
  MainActivity.kt        — role request, prefix UI, test box, log
app/src/main/res/layout/activity_main.xml
```

## Limits

- Android only — iOS CallKit requires full numbers, no wildcard/prefix API.
- The system may still write a missed-call entry (we keep call-log, skip
  notification). Behavior varies by OEM/dialer.
- Dual-SIM/private numbers: hidden numbers have no digits to match, so they
  always ring through.
