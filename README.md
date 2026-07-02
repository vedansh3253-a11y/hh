# BLE BMS Controller

A generic Android BLE app for exploring and controlling your Smart Bluetooth
Lithium Battery (the type used with the BAT-BMS app, on e-tricycles, e-bikes,
golf carts, etc). Use this only with a battery/device you own.

## What it does right now

- Scans for nearby BLE devices and lets you tap one to connect.
- After connecting, lists every GATT service/characteristic the device
  exposes, with its properties (READ / WRITE / NOTIFY).
- Tap a characteristic to select it as the **write target**.
- Long-press a characteristic to **read** its current value (logged as hex).
- Any characteristic that supports NOTIFY is auto-subscribed, so status
  updates from the battery stream into the log automatically.
- Type raw hex bytes and hit **Send** to write to the selected characteristic.
- **ON (preset)** / **OFF (preset)** buttons send the hex strings defined in
  `CommandPresets.kt` — currently placeholders (`AA0101` / `AA0100`), not real
  commands.

## How to build it

### Option A — Android Studio (builds straight onto your phone)

1. Install [Android Studio](https://developer.android.com/studio).
2. Open this folder (`BleBmsController/`) as an existing project.
3. Let Gradle sync (first sync downloads dependencies — needs internet).
4. Plug in your Android phone (enable Developer Options + USB debugging), hit
   **Run**.

### Option B — GitHub Actions (builds an installable APK, no Android Studio needed)

This repo includes `.github/workflows/build.yml`, which builds a debug APK
automatically and lets you download it.

1. Create a new **empty** repository on [github.com](https://github.com)
   (any name, e.g. `ble-bms-controller`). Don't add a README/gitignore —
   just create it empty.
2. Upload this whole `BleBmsController` folder's contents to that repo. The
   simplest way if you don't use git: on the repo's GitHub page, click
   **Add file → Upload files**, then drag in everything from inside the
   `BleBmsController` folder (so `app/`, `.github/`, `build.gradle`, etc. end
   up at the repo root — not nested inside another `BleBmsController`
   folder). Commit.
3. Go to the **Actions** tab of your repo. A workflow run called "Build
   debug APK" should start automatically (takes 3-5 minutes). If it doesn't,
   click **Build debug APK** on the left, then **Run workflow**.
4. Once it finishes (green checkmark), click into that run, scroll to
   **Artifacts**, and download **BleBmsController-debug-apk** — this is a
   zip containing `app-debug.apk`.
5. Transfer `app-debug.apk` to your phone (email it to yourself, use Google
   Drive, or a USB cable) and tap it to install.
6. Your phone will warn about "installing from unknown sources" — you'll
   need to allow it for the app you use to open the file (Settings prompt
   guides you through this). This warning is normal for any app installed
   outside the Play Store.

Every time you push a change (e.g. after editing `CommandPresets.kt` with
real command bytes), the workflow reruns automatically and a fresh APK
artifact appears in Actions.

## Finding the real ON/OFF command bytes

The app above is a fully working BLE explorer, but it doesn't know your
battery's actual "turn on/off" command yet — that's proprietary to Grenergy
and isn't published. The most reliable way to find it, using only your own
hardware and the official BAT-BMS app, is to capture a Bluetooth HCI snoop
log while you press the on/off controls yourself:

1. On your phone: **Settings → System → Developer options → Enable Bluetooth
   HCI snoop log** (wording varies by phone brand; on some phones it's under
   Settings → Developer options → "Enable Bluetooth HCI snoop log" or
   similar).
2. Open the official **BAT-BMS** app, connect to your battery.
3. Toggle the charge/discharge (on/off) controls a few times, spaced apart,
   so the relevant packets are easy to spot.
4. Turn off the snoop log setting (or just reboot) to flush the log file to
   `/sdcard/Android/data/btsnoop_hci.log` (path varies by OS version — some
   put it under internal storage root, some require `adb bugreport`).
5. Pull the file: `adb pull /sdcard/Android/data/btsnoop_hci.log .`
6. Open it in [Wireshark](https://www.wireshark.org/) (it understands
   btsnoop format natively). Filter on `btatt` to see only GATT
   Attribute Protocol traffic.
7. Look for **Write Request** / **Write Command** packets sent right after
   you pressed on/off in the app — the `Value` field is the command byte
   sequence, and the `Handle` maps to a characteristic UUID (Wireshark shows
   this once it's seen the "Read By Type" service discovery exchange).
8. Cross-reference the handle/UUID against what this app lists when you
   connect (they should match) to confirm.

Once you have the real bytes and UUID:

- Update `CommandPresets.kt` with the real `ON_HEX` / `OFF_HEX` values.
- In the app, connect, tap the matching characteristic to select it, and the
  preset buttons will now send the correct commands.

## Safety note

Only connect to and control batteries/devices you own. Many of these BMS
units gate charge and discharge circuits for safety reasons (over-current,
thermal protection, etc.) — toggling them incorrectly, or on a vehicle that's
in use, can be dangerous. Test with the vehicle stationary and unloaded
first.
