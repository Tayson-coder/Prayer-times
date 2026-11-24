# ZG — Android Service Tool

A desktop service tool for Android devices, built on open-source utilities
(adb, fastboot, Heimdall, scrcpy, libusb). Graphite "bench equipment" UI with
a live device status and a terminal-style console.

## Features

- **Device** — device info, MTP info, screenshot, 30s screen recording, scrcpy
  mirroring, and reboot to recovery / bootloader / fastbootd / download / EDL.
- **Apps** — list user/system apps with search; remove-for-user (root-free
  debloat, reversible), restore, full uninstall, disable/enable, clear data,
  extract APK; install APK / split APKs; push & pull files.
- **Fastboot** — `getvar all`, bootloader unlock/lock, flash a partition,
  boot an image once (no flash), erase a partition, set active A/B slot, reboot.
- **Samsung flash** — Odin-style flashing (BL / AP / CP / CSC / HOME_CSC) through
  **Heimdall** (open-source), plus PIT read and a partition table.
- **Emergency recovery (EUB)** — unbrick an **Exynos 9610 / Galaxy A50 (A505)**
  that is **already in EUB mode** by uploading a bootloader over USB. This uses
  the open-source `smdk-usbdl` protocol. It does **not** put a device into EUB and
  does **not** bypass any lock — it just loads a bootloader to recover a brick.

## Download

Prebuilt Windows packages are produced automatically by GitHub Actions and
attached to the **"ZG latest build"** release (see the Releases page). Each
package is a zip with `ZG.exe` and the bundled tools.

## Build it yourself

### On Windows (local)
```
build.bat
```
Produces `dist\ZG.exe`. Place the helper tools next to it (see below).

### Via GitHub Actions (cloud, recommended)
Push to `main` or run the **Build ZG (Windows)** workflow manually. It builds
`ZG.exe` on a Windows runner, downloads the helper tools, and publishes a zip.

## Bundled tools (next to ZG.exe)

| File | Used by | Source |
|------|---------|--------|
| `adb.exe`, `fastboot.exe` | Device / Apps / Fastboot | Google platform-tools |
| `heimdall.exe` | Samsung flash | Heimdall (open source) |
| `libusb-1.0.dll` | EUB recovery (pyusb) | libusb |
| `scrcpy.exe` (+ its files) | Screen mirroring | Genymobile/scrcpy |

The app searches for these next to `ZG.exe` (and in a `platform-tools` subfolder),
then on `PATH`. A missing tool only disables its own feature.

## Drivers

The installer (`ZG-Setup.exe`) can install the USB drivers **during setup** — tick
"Install USB drivers now". This needs Administrator rights (a UAC prompt), and uses
`wdi-simple` (from libwdi, the engine behind Zadig) to install **WinUSB** for:

- **Samsung download mode (Heimdall)** — `04E8:685D`
- **Exynos EUB emergency mode** — `04E8:1234`

Notes and fallbacks:

- **adb / fastboot (normal mode):** Windows 10/11 usually installs the ADB interface
  automatically via Windows Update. For older Windows, install the **Samsung USB Driver**
  (drop `SAMSUNG_USB_Driver.exe` next to the installer to have it run automatically).
- The **EUB** driver binds best when the phone is already in EUB mode during install;
  otherwise it is pre-staged and binds when the phone is connected.
- Manual fallback: `zadig-2.9.exe` is included — in **Zadig**, *Options ▸ List All Devices*,
  pick the device for that mode, install **WinUSB / libusb-win32**.

> Install a driver **only** while the phone is in that specific mode.

## Safety notes

- Flash firmware for your **exact** model; keep the battery charged; don't unplug.
- Bootloader unlock and `erase` **wipe data**. Those buttons are red and ask first.
- EUB recovery is **A505 / Exynos 9610 only** — a wrong `sboot.bin` can brick further.

## Credits / licences

- adb, fastboot — Android SDK Platform-Tools (Google)
- Heimdall — Benjamin Dobell (open source)
- scrcpy — Genymobile (Apache-2.0)
- libusb / pyusb — libusb project
- EUB protocol — based on Ben Dooks' `smdk-usbdl` and
  `astarasikov/exynos9610-usb-emergency-recovery`
