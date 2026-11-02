# Compatibility Layer (One UI 8.0 & 8.5)

This folder contains the companion stabilization module for users running **One UI 8.0 or One UI 8.5**.

## Files
- `LockScreenFix_OU9_2.1.6.apk`: Developed by **TAVATAAN**, this LSPosed module fixes and stabilizes Samsung's `AlwaysOnDisplay_8.9.65.48` on One UI 8.x by stubbing and adapting missing One UI 9 framework methods.

## How to use:
If your device is running One UI 8.0 or 8.5:
1. Update your system Always On Display app to `AlwaysOnDisplay_8.9.65.48.apk` (download from [GitHub Releases](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/releases/tag/v1.0.0)).
2. Install `LockScreenFix_OU9_2.1.6.apk` and activate it in LSPosed (scope: `System UI` and `Always On Display`).
3. Restart System UI (`su -c killall com.android.systemui`).
4. Install and configure `NowBarColor.apk`.

*(Users on One UI 9.0+ do not need this file).*
