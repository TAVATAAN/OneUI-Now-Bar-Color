# Now Bar Color

<p align="center">
  <b>Customize the collapsed Now Bar capsule background on Samsung One UI 8.0+ & One UI 9.0+ (Lock Screen)</b><br>
  <i>LSPosed Module for Samsung Galaxy Devices</i>
</p>

<p align="center">
  <img src="res/drawable/ic_launcher.png" width="128" height="128" alt="Now Bar Color Icon" />
</p>

---

## Overview

**Now Bar Color** is an advanced LSPosed module designed for **Samsung Galaxy devices running One UI 8.0, 8.5, and One UI 9.0+**. It gives you complete control over the background styling of the collapsed **Now Bar** capsules on the lock screen—allowing you to apply solid colors, frosted-glass blur effects, smooth two-stop gradients, and adaptive wallpaper color palettes without altering album artwork, notification icons, text colors, or native system animations.

It includes a companion configuration app built with a native One UI interface, interactive live capsule previews with dynamic audio waveforms, large One UI squircle vector icons with transparent cutouts that dynamically preview each capsule's chosen color or gradient, per-category toggles, independent opacity sliders, and trilingual support (English, Spanish, and Portuguese). Under the hood, deep hooks in `SystemUI` and `FaceWidget/AOD` ensure zero-lag, instant styling from frame 0 with complete suppression of Samsung's default chip gradient overlays.

---

## 🚨🔴 IMPORTANT COMPATIBILITY WARNING (READ BEFORE INSTALLING) 🔴🚨

> [!CAUTION]
> 🚨 **CRITICAL ARCHITECTURE & `AOD_Service` BUILD REQUIREMENT — ONE UI 8.0 / 8.5 vs. ONE UI 9.0+** 🚨
>
> - ⚠️ **Having One UI 9 does NOT guarantee out-of-the-box compatibility:** Samsung's **`AOD_Service` (`com.samsung.android.app.aodservice`)** changes drastically across different device branches, models, and firmware builds.
> - 🔴 **Mandatory Base `AOD_Service` Build (`896548000`):** It is **strictly mandatory** to run the exact same base build of `AOD_Service` used to develop this module—extracted from the **Galaxy S26 FE**, build **`896548000`** (`v8.9.65.48`). Running any other `AOD_Service` variant or branch will cause graphical glitches in the capsule or prevent the module from working entirely.
> - 🔴 **Running One UI 8.0 or One UI 8.5? (INSTALL THE FULL SUITE):** Users on One UI 8.0 and 8.5 **MUST** flash the complete **`NowBar_LockScreen_Suite.zip`** All-in-One root module (via **KernelSU, Magisk, or APatch**), which already includes the required S26 FE `AOD_Service` (`896548000`), companion One UI 9 lock screen apps, `LockScreenFix`, and `NowBarColor` v1.3.0 pre-activated out of the box.
> - 🔴 **Running One UI 9.0+ (Latest System Version)? (INSTALL THE STANDALONE `AOD_Service.apk`):** Users on One UI 9.0+ do **NOT** need the full Suite ZIP, but **MUST** install the standalone **`AOD_Service.apk`** (extracted from the **S26 FE**, build **`896548000`**) provided in this repository alongside **`NowBarColor.apk`**.

---

## Download

All official files are available on the [Releases page](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/tag/4-1.3.0):

| File | Target One UI Version | Description / Build | Direct Download |
|---|---|---|---|
| **`NowBarColor.apk`** | **All Supported Versions** *(Standalone LSPosed Module)* | Main Now Bar Color v1.3.0 (`versionCode 4`) APK | [Download NowBarColor.apk (v1.3.0)](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/download/4-1.3.0/NowBarColor.apk) |
| **`AOD_Service.apk`** | 🔴 **One UI 9.0+ (Mandatory Base App)** | Extracted from **Galaxy S26 FE** — Build **`896548000`** (`v8.9.65.48`) | [Download AOD_Service.apk (896548000)](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/download/4-1.3.0/AOD_Service.apk) |
| 🚨 **`NowBar_LockScreen_Suite.zip`** | 🔴 **One UI 8.0 & One UI 8.5 ONLY** | All-in-One Root Module *(KernelSU / Magisk / APatch)* with `NowBarColor` v1.3.0 + `LockScreenFix` v2.1.7 + S26 FE `AOD_Service` (`896548000`) | [Download NowBar_LockScreen_Suite.zip (v1.3.0)](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/download/4-1.3.0/NowBar_LockScreen_Suite.zip) |

---

## Installation Tutorial

### 🔴 Option A: For One UI 8.0 & One UI 8.5 Devices (1-Step All-in-One Suite)

🚨 **Mandatory for One UI 8.0 / 8.5:** Users on One UI 8.0 and 8.5 must install the complete **All-in-One Suite (`NowBar_LockScreen_Suite.zip`)** so the required S26 FE `AOD_Service` (`896548000`) and all lock screen dependencies work immediately on the very first installation without manual setup.

#### What is included inside `NowBar_LockScreen_Suite.zip`?
- **One UI 9 Core Lock Screen & AOD Apps** (`AOD_Service` build **`896548000`** / `v8.9.65.48` extracted from **Galaxy S26 FE**, plus `DressRoom`, `GalleryWidget`, and `SamsungGallery`) pre-configured for `priv-app` mounting.
- **Good Lock Companion Plugin** (`LockStar` v9.0.00.5) automatically installed with required system permissions.
- **`LockScreenFix_OU9` v2.1.7 (LSPosed Module by TAVATAAN):** Latest build that stabilizes all ported One UI 9 lock screen, AOD, media controls, and gallery components on One UI 8.0 / 8.5.
- **`NowBarColor` v1.3.0 (LSPosed Module):** Full Now Bar background customization app and engine.
- **Automatic LSPosed SQLite Activation & Permissions:** Automatically registers and enables both `NowBarColor` and `LockScreenFix_OU9` directly inside LSPosed's configuration database (`modules_config.db`) with all required target scopes (`com.android.systemui`, `com.samsung.android.app.aodservice`, etc.) and runtime permissions.

#### Steps for One UI 8.0 & 8.5:
1. Ensure **LSPosed Framework** (Zygisk or JingMatrix/Vector fork) is installed on your rooted device (**KernelSU**, **Magisk v26+**, or **APatch**).
2. Download [**`NowBar_LockScreen_Suite.zip`**](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/download/4-1.3.0/NowBar_LockScreen_Suite.zip).
3. Open **KernelSU**, **Magisk**, or **APatch** Manager, go to the **Modules** tab, and flash `NowBar_LockScreen_Suite.zip`.
4. **Reboot your device.**
5. **Done!** Both **`NowBarColor`** and **`LockScreenFix_OU9`** are **automatically activated in LSPosed** upon boot—**you do NOT need to enable them manually in LSPosed Manager**. Simply open the **Now Bar Color** app from your app drawer and customize your capsules!

---

### 🔴 Option B: For One UI 9.0+ Devices (`AOD_Service.apk` + `NowBarColor.apk`)

⚠️ **Important for One UI 9.0+ Users:** Being on One UI 9 does **not** guarantee immediate compatibility because `AOD_Service` varies drastically depending on each device's firmware branch. You **must** install the exact base **`AOD_Service.apk`** used to develop the module (extracted from the **Galaxy S26 FE**, build **`896548000`**), as any other variant will cause graphical glitches in the capsule or prevent the module from functioning.

#### Steps for One UI 9.0+:
1. Download [**`AOD_Service.apk` (S26 FE — Build `896548000`)**](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/download/4-1.3.0/AOD_Service.apk) from the repository Releases and install it as an update over your system's Always On Display service.
   > 💡 **Troubleshooting Cross-Branch Installation Failure (KernelSU):**
   > If `AOD_Service.apk` fails to install as an update due to signature/branch verification differences across Samsung firmware branches, disable the official release check first by running the following command in a terminal or ADB shell before installing the APK:
   > ```bash
   > su -c "/data/adb/ksu/bin/resetprop -n ro.build.official.release false"
   > ```
   > Then install `AOD_Service.apk` again.
2. Download and install [**`NowBarColor.apk` (v1.3.0)**](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/download/4-1.3.0/NowBarColor.apk) (or install/update directly from the LSPosed Repository).
3. Open **LSPosed Manager** and enable **Now Bar Color** with its required scopes (`System UI` and `Always On Display`).
4. Restart System UI (`su -c killall com.android.systemui`) or reboot your device.
5. Open **Now Bar Color** from your launcher to configure your colors, gradients, blur, and per-capsule styles.

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

- **Visual Finish Modes**:
  - **Pure Solid Color**: Vibrant, high-contrast backgrounds.
  - **Two-Stop Horizontal Gradients**: Smooth custom or preset color transitions per capsule or globally.
  - **Color Blur**: Translucent frosted-glass blur effect with adaptive color mixing.
- **Adaptive Wallpaper Palette Extraction**:
  - **Bottom Zone**: Accurately samples the exact wallpaper region where the Now Bar rests.
  - **Monet Accent**: Uses the system's dynamic Material You color scheme.
  - **Dominant Background**: Matches the primary lock screen tone.
- **Granular Per-Category Control with Dynamic Vector Badges**:
  Each of the 10 Now Bar capsule categories features a large One UI squircle badge with transparent vector cutouts dynamically backed by its active color or gradient, plus independent toggles, color overrides, and opacity sliders:
  - **Music & Media Playback** (`NowBarMediaCardView`)
  - **Clock, Timer & Stopwatch**
  - **Voice Recorder & Audio Memos**
  - **Flashlight**
  - **Live Battery & Charging** (`NowBarBatteryCardView`)
  - **Now Brief / Galaxy AI Suggestions** (`NowBarBriefCardView`)
  - **Ongoing Phone Calls**
  - **Modes & Routines**
  - **Sports, Maps & Samsung Health**
  - **Other Live Activities** (Interpreter, Smart View, third-party ongoing chips)
- **Zero-Lag Hot Creation (Frame-0 Styling)**:
  - Deep hooks in `NowBarCardStackView` intercept newly created capsules in real time.
  - Complete suppression of Samsung's native notification chip gradient overlay (`NowBarCardGradientView`), ensuring newly triggered capsules render with your custom palette from frame 0 without flicker.
- **Native One UI Companion App**:
  - Real-time animated media capsule preview, interactive HSV pickers, and built-in language switcher (**English**, **Spanish**, and **Portuguese**).

---

## Building from Source

The repository includes a lightweight, self-contained Python build script (`build.py`) compatible with Linux/Android and desktop environments with Android SDK tools:

```bash
# 1. Clone the repository
git clone https://github.com/TAVATAAN/OneUI-Now-Bar-Color.git
cd OneUI-Now-Bar-Color

# 2. Build and sign the APK
python3 build.py
```

The signed APK will be generated at:
```text
build/NowBarColor.apk
```

---

## Project Structure

```text
├── AndroidManifest.xml          # Package definition, permissions, and LSPosed metadata
├── assets/                      # Module bootstrap (xposed_init)
├── docs/screenshots/            # Collage preview images
├── meta_xposed/                 # LSPosed repository metadata (module.prop, scope.list)
├── res/                         # Vector capsule icons, launcher icons, strings, and arrays
├── src/io/github/tavataan/nowbarcolor/
│   ├── MainActivity.java        # One UI settings activity with dynamic vector badges & Live Preview
│   ├── NowBarHook.java          # Core LSPosed hooking engine for SystemUI & FaceWidget/AOD
│   ├── CapsuleGradient.java     # Adaptive GradientDrawable tracking internal state mutations
│   ├── CapsuleStyle.java        # Styling pipeline managing Solid, Blur, and Gradient states
│   ├── ReflectionCache.java     # High-performance reflection cache for SystemUI fields/methods
│   ├── ConfigManager.java       # Configuration persistence and 10-category metadata/icons
│   ├── WallpaperColorHelper.java# Real-time wallpaper color extractor (Monet, Bottom Zone)
│   ├── OneUIMediaCapsuleView.java# Dynamic preview component with live audio waveform bars
│   ├── OneUISwitch.java         # Custom Samsung One UI-styled toggle switch
│   ├── HsvColorPickerView.java  # Touch-responsive HSV color picker
│   └── I18n.java                # Trilingual localization manager (EN, ES, PT)
├── tests/                       # Automated host test suite
└── build.py                     # Standalone compilation and packaging pipeline
```

---

## Feedback, Bug Reports & Support

If you encounter any bugs, have feature suggestions, or need help with setup, you can reach out through Telegram or open an issue on GitHub:

- Telegram: [@Tava_Mon97](https://t.me/Tava_Mon97)
- GitHub Issues: Report issues or submit pull requests directly on the repository tracker.

---

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---

## Author

Developed by **tavataan** ([Telegram: @Tava_Mon97](https://t.me/Tava_Mon97)).
