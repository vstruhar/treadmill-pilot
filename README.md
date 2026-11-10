# TreadmillPilot

TreadmillPilot is an open-source Android interval trainer that drives a Bluetooth treadmill for you. Build a Workout from timed Intervals (Warm up, Training, Recover, Cool down), press Start, and the app sets the belt speed for each Interval. It announces each change before it happens and records every Run.

Tested on the **Ring RX135** treadmill. It should work with any treadmill that exposes the standard Bluetooth Fitness Machine Service with a writable control point.

## Features

- **Workouts**: build and edit Workouts from Intervals. Times step by 5 seconds and speeds by 0.1 km/h, kept within the speed range the treadmill reports. Drag to reorder, duplicate, or delete. A starter Workout called "Default" is included.
- **Automatic speed control**: the app starts the belt and sets the target speed for every Interval.
- **Cues**: a spoken "Next: Recover, 4.0 kilometers per hour" 10 seconds before each change, then beeps at 3, 2, and 1. Voice can be turned off and the beeps stay.
- **Countdown with the treadmill**: Start sends the start command first, so the app's 3-2-1 runs together with the treadmill's own countdown.
- **Run controls**: Skip, Pause and Resume, and Stop with a confirmation.
- **Remote override**: if you change speed with the treadmill remote, that speed holds until the next Interval.
- **Reconnects**: if Bluetooth drops, the timer pauses, the app says "Connection lost", reconnects, and continues.
- **Keeps running in the background**: a foreground service with a notification keeps the run, sound, and Bluetooth going with the screen locked.
- **History and Stats**: every Run is saved with duration, distance, average and maximum speed, energy (if the treadmill sends it), a speed chart, and per-Interval results. Stats shows totals and a 12-week distance chart. Runs can be deleted.
- **Backup and restore**: one JSON file through the Android file picker. Restore merges and skips records that already exist.
- **Accent color**: choose the app's accent color in Settings.

## Protocol

The app uses the **Bluetooth SIG Fitness Machine Service (FTMS)**, a published Bluetooth Low Energy standard. It does not use the private protocol of the FitShow app or any other vendor protocol.

| What | UUID |
| --- | --- |
| Fitness Machine Service | `0x1826` |
| Treadmill Data (speed, distance, energy, heart rate) | `0x2ACD` |
| Fitness Machine Control Point | `0x2AD9` |
| Fitness Machine Status | `0x2ADA` |
| Supported Speed Range | `0x2AD4` |
| Fitness Machine Feature | `0x2ACC` |

The app writes only these control point opcodes:

| Opcode | Meaning |
| --- | --- |
| `0x00` | Request Control |
| `0x02` | Set Target Speed (uint16, little-endian, 0.01 km/h) |
| `0x07` | Start or Resume |
| `0x08 0x01` / `0x08 0x02` | Stop / Pause |

Every write is checked against this allow-list in [`FtmsControl.kt`](app/src/main/java/com/threadmill/connect/ble/FtmsControl.kt). If the treadmill refuses Request Control, the workout does not start. Status events from `0x2ADA` (a pause or stop on the console, the safety key, a speed change from the remote) are fed back into the run. The reasoning is in [docs/adr/0001-control-via-ftms-control-point.md](docs/adr/0001-control-via-ftms-control-point.md).

### Tested treadmills

| Treadmill | Result |
| --- | --- |
| Ring RX135 | Connects, the app takes control, and the belt follows the workout |

If you try another treadmill, please open an issue or a pull request with the result.

## Safety

FTMS has no watchdog. If the phone dies or the app is killed during a run, the treadmill keeps going at its last speed. **Always use the treadmill's safety clip and know where its stop key is.** The app sends Stop when a run ends or is stopped, and when its service is destroyed. It is not a safety device. Use it at your own risk.

## Requirements

- An Android phone with Bluetooth Low Energy, running Android 8.0 (API 26) or newer
- A treadmill with an FTMS control point
- Permissions: Nearby devices (Bluetooth scan and connect), and notifications on Android 13 and newer

## Build

### Prerequisites

- **JDK 17 or newer.** JDK 21 is tested. Gradle 8.11 cannot run on JDK 24 or newer.
- **Android SDK** with Platform 35 (Android 15) and Build-Tools 35. The easiest way is [Android Studio](https://developer.android.com/studio), or install the [command-line tools](https://developer.android.com/studio#command-line-tools-only) and run:

  ```bash
  sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
  ```

### Steps

1. Clone the repository:

   ```bash
   git clone https://github.com/<your-account>/TreadmillPilot.git
   cd TreadmillPilot
   ```

2. Tell Gradle where the Android SDK is. Either set `ANDROID_HOME`, or create `local.properties` in the project root:

   ```properties
   sdk.dir=/path/to/Android/sdk
   ```

   Typical locations are `~/Library/Android/sdk` on macOS, `~/Android/Sdk` on Linux, and `%LOCALAPPDATA%\Android\Sdk` on Windows.

3. Make sure `JAVA_HOME` points at JDK 17 to 23. On macOS with Homebrew:

   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   ```

4. Run the unit tests and build the debug APK:

   ```bash
   ./gradlew :app:testDebugUnitTest :app:assembleDebug
   ```

   On Windows use `gradlew.bat` instead of `./gradlew`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

5. Install it on a phone with USB debugging enabled:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

You can also open the project folder in Android Studio and press Run.

The release build is not signed. To publish it, add your own signing config in `app/build.gradle.kts`.

## Usage

1. Turn on the treadmill and Bluetooth on the phone.
2. Open the app, tap the gear icon, and choose your treadmill. It is remembered.
3. Open a Workout, or create one with the + button.
4. Tap **Start Workout**. The belt starts with the 3-2-1 countdown, then follows the plan.

## Project structure

```
app/src/main/java/com/threadmill/connect/
  ble/      Bluetooth scanning, GATT queue, FTMS parser and control point
  run/      Run engine (pure timing logic), controller, cues, recorder, foreground service
  data/     Room database, repository, DataStore settings
  backup/   JSON backup and merge restore
  model/    Domain types and the starter workout
  ui/       Jetpack Compose screens
app/src/test/  Unit tests for the engine, recorder, FTMS encoding, and backup
docs/adr/      Architecture decision records
GLOSSARY.md    Domain terms used across the code (Workout, Interval, Stage, Run, Cue)
```

Built with Kotlin, Jetpack Compose (Material 3), Navigation Compose, Room, DataStore, and kotlinx.serialization.

## Vibecoded

This app was vibecoded: it was written with an AI coding assistant, guided and tested by a human on a real treadmill. The code has unit tests for the run engine, recorder, FTMS encoding, and backup, but it has not had a traditional line-by-line review. Read the [Safety](#safety) section before you use it.

## Contributing

Issues and pull requests are welcome, especially reports from other treadmill models. Please run `./gradlew :app:testDebugUnitTest` before opening a pull request.

## Disclaimer

This project is not affiliated with, endorsed by, or connected to Ring, FitShow, or any treadmill manufacturer. All product names and trademarks belong to their owners.

## License

[MIT](LICENSE)
