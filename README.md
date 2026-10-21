# CriticalBatteryOverlay - Emergency Low Battery Red Overlay

**CriticalBatteryOverlay** is an ultra-low-power native Android app built with Kotlin and Jetpack Compose. It displays a full-screen, non-interactive, semi-transparent red overlay (`#FF0000` at ~35% default opacity) whenever your device battery drops to 5% (or a custom threshold) and the phone is not charging.

---

## Core Behavior & Features

- **Automatic Low Battery Overlay**: Displays a full-screen red warning overlay covering all apps, status bar, and navigation gesture bars (`FLAG_LAYOUT_IN_SCREEN`, `FLAG_LAYOUT_NO_LIMITS`).
- **Non-Interactive Passthrough**: Uses `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE` so you can continue using your phone normally beneath the overlay.
- **Auto Pause on Charger**: Hides the overlay immediately as soon as you connect your charger or when battery level rises above the threshold.
- **Ultra-Low Battery Usage (< 0.01% per day)**:
  - Uses event-driven system broadcasts (`Intent.ACTION_BATTERY_CHANGED`).
  - Zero background polling loops, zero timers, zero network, zero location tracking. Uses 0% CPU when idle.
- **Android 14/15 Compliant**: Uses Foreground Service type `specialUse` with required `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property declared in `AndroidManifest.xml`.
- **Boot Recovery**: Restarts automatically after device reboot via `RECEIVE_BOOT_COMPLETED`.
- **Test Preview Button**: Instant 3-second red overlay test button in UI.

---

## Technical Specifications

- **Language**: Kotlin 100%
- **UI Framework**: Jetpack Compose (Material3 Dark Theme)
- **Minimum SDK**: API 26 (Android 8.0+)
- **Target SDK**: API 35 (Android 15)
- **Build System**: Gradle Kotlin DSL

---

## How to Build

Requirements: JDK 17+, Android SDK API 35.

```bash
# Clone repository
git clone https://github.com/bestdayin2016/CriticalBatteryOverlay.git
cd CriticalBatteryOverlay

# Build debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## How to Test on Android Emulator (ADB)

You can easily simulate low battery and charging states on the Android Emulator or a connected USB device:

1. **Unplug simulated charger**:
   ```bash
   adb shell dumpsys battery unplug
   ```

2. **Set battery percentage to 5%**:
   ```bash
   adb shell dumpsys battery set level 5
   ```
   *(Observe the full-screen red overlay instantly appearing across all apps and status bars).*

3. **Simulate plugging into charger**:
   ```bash
   adb shell dumpsys battery set status 2
   ```
   *(Observe the red overlay immediately hiding).*

4. **Reset battery status back to normal**:
   ```bash
   adb shell dumpsys battery reset
   ```

---

## License

MIT License.
