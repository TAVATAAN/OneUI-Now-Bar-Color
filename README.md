# Now Bar Color

<p align="center">
  <b>Customize the collapsed Now Bar capsule background on Samsung One UI 8.0+ (Lock Screen)</b><br>
  <i>LSPosed Module for Samsung Galaxy Devices</i>
</p>

<p align="center">
  <img src="res/drawable/ic_launcher.png" width="128" height="128" alt="Now Bar Color Icon" />
</p>

---

## Overview

**Now Bar Color** is an advanced LSPosed module designed for **Samsung Galaxy devices running One UI 8.0 and above** (tested up to One UI 9.0+). It gives you complete control over the background styling of the collapsed **Now Bar** capsules on the lock screen—allowing you to apply solid colors, blur effects, smooth gradients, and adaptive wallpaper color palettes without altering album artwork, notification icons, text colors, or native system animations.

It features a companion configuration app built with a native One UI interface, interactive live capsule previews with dynamic audio waveforms, per-category toggles, and independent opacity sliders. Under the hood, deep hooks in `SystemUI` and `FaceWidget/AOD` ensure zero-lag, instant styling from frame 0 with complete suppression of Samsung's default chip gradient overlays.

---

## Screenshots and Examples

Examples showing how custom colors blend dynamically with various lock screen wallpapers and categories:

<p align="center">
  <img src="docs/screenshots/preview_1.jpg" width="32%" alt="Now Bar Customization Example 1" />
  <img src="docs/screenshots/preview_2.jpg" width="32%" alt="Now Bar Customization Example 2" />
  <img src="docs/screenshots/preview_3.jpg" width="32%" alt="Now Bar Customization Example 3" />
</p>

---

## Features

- **Visual Finish Options**:
  - **Pure Solid Color**: Vibrant, high-contrast backgrounds with subtle smooth gradient blending.
  - **Color Blur**: Translucent frosted-glass blur effect with adaptive color mixing.
- **Adaptive Wallpaper Palette Extraction**:
  - **Bottom Zone**: Accurately detects and applies the exact color sampled from the wallpaper where the Now Bar rests.
  - **Monet Accent**: Uses the system's dynamic Material You color scheme.
  - **Dominant Background**: Matches the overall primary lock screen tone.
- **Granular Per-Category Control**:
  Each Now Bar activity capsule has its own dedicated switch and independent opacity control:
  - **Music & Media Playback** (`NowBarMediaCardView`)
  - **Now Brief / AI Suggestions** (`NowBarBriefCardView`)
  - **Live Battery & Charging** (`NowBarBatteryCardView`)
  - **Clock, Timer & Stopwatch**
  - **Flashlight**
  - **Voice Recorder & Audio Memos**
  - **Ongoing Phone Calls**
  - **Modes & Routines**
- **Zero-Lag Hot Creation (Frame-0 Styling)**:
  - Deep hooks in `NowBarCardStackView` (`addView` and `q`) intercept newly created capsules in real time.
  - Full suppression of Samsung's native notification chip gradient overlay (`NowBarCardGradientView`), ensuring that suddenly triggered capsules (such as turning on the flashlight while on the lock screen) render with your custom palette from the very first frame without flicker or needing a screen cycle.
- **Native One UI Companion App**:
  - Built-in live preview showing real-time audio waveforms, playback controls, and background changes.
  - Interactive HSV color picker and native One UI sliders.

---

## Compatibility & Requirements

### Supported Environments
- **Samsung Galaxy devices running One UI 8.0 and above** (One UI 8.0, 8.5, and One UI 9.0+).
- **Root Access**: Magisk v26+, KernelSU, or APatch.
- **LSPosed Framework**: Modern Zygisk release or JingMatrix fork.

### Crucial Version Dependency Notice
This module targets and was developed against Samsung's **`AlwaysOnDisplay` version `8.9.65.48`** (which originates from One UI 9).
- **If you are already running One UI 9.0+:** No extra steps are needed. Simply install `NowBarColor.apk` and activate it in LSPosed.
- **If you are running One UI 8.0 or One UI 8.5:** `AlwaysOnDisplay_8.9.65.48` **does not work natively** on One UI 8.x and will cause system crashes unless patched. You **must** follow the tutorial below before installing Now Bar Color.

---

## Compatibility Tutorial (For One UI 8.0 & One UI 8.5)

If you are running One UI 8.0 or 8.5, install the prerequisites in this exact sequence:

1. **Install `AlwaysOnDisplay_8.9.65.48.apk`**:
   - Download the APK from the [Releases section](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/tag/v1.0.0).
   - Install it as an update to the system Always On Display app.
2. **Install `LockScreenFix_OU9_2.1.6.apk`**:
   - Download the companion LSPosed module developed by **TAVATAAN** from [Releases](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/tag/v1.0.0) or from the repository `compatibility/` directory.
   - This module stabilizes AOD v8.9.65.48 and bridges missing framework APIs on One UI 8.x.
3. **Activate `LockScreenFix` in LSPosed**:
   - Open the LSPosed app and enable the **LockScreenFix** module.
   - Recommended scope: `System UI` (`com.android.systemui`) and `Always On Display` (`com.samsung.android.app.aodservice`).
   - Restart System UI (`su -c killall com.android.systemui`) or reboot the device. Ensure your lock screen and AOD operate normally.
4. **Install `NowBarColor.apk`**:
   - Download `NowBarColor.apk` from [Releases](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/tag/v1.0.0).
   - Activate it in LSPosed with the same scope (`System UI` and `Always On Display`).
   - Restart System UI.
   - Open the **Now Bar Color** app from your launcher to customize your colors and styles!

*(Note: Users already on One UI 9.0+ can skip steps 1-3 and install `NowBarColor.apk` directly).*

---

## Download

All necessary APK files are available on the [Releases page](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases):

| File | Description | Required For |
|---|---|---|
| `NowBarColor.apk` | Main Now Bar Color customization module | All users |
| `LockScreenFix_OU9_2.1.6.apk` | Companion stabilization module by TAVATAAN | One UI 8.0 / 8.5 users only |
| `AlwaysOnDisplay_8.9.65.48.apk` | Samsung AOD v8.9.65.48 base package | One UI 8.0 / 8.5 users only |

---

## Building from Source

The repository includes a lightweight, self-contained Python build script (`build.py`) compatible with Linux/Android (Termux/AGY) and desktop environments with Android SDK tools:

```bash
# 1. Clone the repository
git clone https://github.com/TAVATAAN/OneUI-Now-Bar-Color.git
cd OneUI-Now-Bar-Color

# 2. Build and sign the APK
python3 build.py
```

The signed APK will be generated at:
```
build/NowBarColor.apk
```

---

## Project Structure

```
├── AndroidManifest.xml          # Package definition, permissions, and LSPosed metadata
├── assets/                      # Module bootstrap (xposed_init) and assets
├── compatibility/               # Stabilization APK and guide for One UI 8.x users
├── docs/screenshots/            # Collage preview images
├── meta_xposed/                 # LSPosed repository metadata (module.prop, scope.list)
├── res/                         # UI resources (layouts, strings, launcher icons, themes)
├── src/io/github/tavataan/nowbarcolor/
│   ├── MainActivity.java        # One UI settings activity with Live Preview & HSV Pickers
│   ├── NowBarHook.java          # Core LSPosed hooking engine for SystemUI & FaceWidget/AOD
│   ├── ConfigManager.java       # Configuration persistence and per-category data models
│   ├── WallpaperColorHelper.java# Real-time wallpaper color extractor (Monet, Bottom Zone)
│   ├── OneUIMediaCapsuleView.java# Dynamic preview component with live audio waveform bars
│   ├── OneUISwitch.java         # Custom Samsung One UI-styled toggle switch
│   └── HsvColorPickerView.java  # Touch-responsive HSV color picker
└── build.py                     # Standalone compilation and packaging pipeline
```

---

## Feedback, Bug Reports & Support

If you encounter any bugs, have feature suggestions, or need help with setup, you can reach out through the Telegram group or open an issue on GitHub:

- Telegram Community: [https://t.me/tavadump](https://t.me/tavadump)
- GitHub Issues: Report issues or submit pull requests directly on the repository tracker.

---

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---

## Author

Developed by **tavataan** ([Telegram: @tavadump](https://t.me/tavadump)).

