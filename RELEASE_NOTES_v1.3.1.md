# 🎮 GoQuiz Adventure v1.3.1 (Stable) — Release Notes

> **Tag:** `v1.3.1` • **Build:** `9` • **Channel:** `Stable` • **Release Date:** October 2026  
> **Platforms:** Android (Mobile/Tablet) & Windows (Desktop/PC)

---

## 🌟 Overview

**GoQuiz Adventure v1.3.1** is a targeted stability and maintenance update addressing the **Admin Security Access** prompt and hardware gesture combo mechanics on both Android and Desktop. This update eliminates unintended prompt re-triggers, isolates hardware vibration motor feedback from the accelerometer sensor, and hardens dialog lifecycle state management.

---

## 🛠️ Key Fixes & Improvements

### 🔐 1. Admin Security Access Stability
* **Re-trigger Loop Resolved:** Fixed a bug where closing the "🔐 SECURITY ACCESS" dialog (via Cancel or device Back) caused the dialog to immediately re-open in an endless loop.
* **Active Dialog State Management:** Added active dialog state tracking (`activeSecurityDialog`) and an automatic **2.5-second cooldown** upon dismissal to prevent race conditions and duplicate dialog creation.
* **Auto-Disarm on Consumption:** Completely disarms combo states (`isVolumeComboArmed`, rocker timestamps, and tap counts) the moment the security menu is launched or dismissed.

### 📳 2. Hardware Physical Combo Polish
* **Haptic Vibration Isolation:** The accelerometer shake detection now suppresses sensor processing while the device's physical vibration motor is active (`lastVibrateEndTime + 250ms`), eliminating self-triggering feedback loops caused by motor vibration.
* **Calibrated Wrist Shake Threshold:** Tuned the shake threshold to a deliberate **1.85G** Euclidean acceleration (`delta > 7.5 m/s²`) and filtered out initial `(0, 0, 0)` sensor calibration deltas.
* **Clean System Volume Observer:** Removed phantom triggers from `ContentObserver` by strictly verifying that the stream music volume has actually changed (`curVol != lastObservedStreamVolume`) before processing button events.
* **De-duplicated Key Event Processing:** Eliminated redundant volume event handlers between `dispatchKeyEvent` and `onKeyDown`, ensuring each key press generates exactly one tactile tick.

### 💻 3. Desktop Hotkey Re-trigger Protection
* **Dialog Concurrency Guard:** Guarded `showSecretPasswordPrompt()` in Desktop `QuizGame` with an `isSecretPromptShowing` lock and cooldown, preventing rapid hotkey repeats from stacking multiple modal authentication prompts.

---

## 📦 Distribution Packages

* **Android Package:** [`GoQuiz-v1.3.1.apk`](dist/v1.3.1/GoQuiz-v1.3.1.apk)
* **Desktop Executable:** [`GoQuiz-v1.3.1.exe`](dist/v1.3.1/GoQuiz-v1.3.1.exe)
* **Release Channel:** `Stable Release (v1.3.1)`
* **Git Tag:** `v1.3.1`

---

## 🔑 Quick Verification Summary

| Platform | Trigger Gesture / Shortcut | Authorization Password |
| :--- | :--- | :--- |
| **Mobile (Android)** | Press/rock **Volume Up + Volume Down**, then **Shake** | `"GQA-12345"` |
| **Desktop (Windows / PC)** | `Ctrl + Shift + ~` (Squiggle / Tilde) | `"GQA-12345"` |
