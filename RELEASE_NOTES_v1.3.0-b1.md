# 🎮 GoQuiz Adventure v1.3.0-b1 (Beta 1) — Release Notes

> **Tag:** `v1.3.0-b1` • **Build:** `8` • **Channel:** `Beta` • **Release Date:** October 2026  
> **Platforms:** Android (Mobile/Tablet) & Windows (Desktop/PC)

---

## 🌟 Overview

**GoQuiz Adventure v1.3.0-b1** debuts the **Secret Developer Database Inspector** alongside the **hardware physical gesture combo** system. This beta release allows developers, educators, and testers to inspect SQLite database tables, review schema columns, browse student account records, and execute custom SQL queries directly inside the mobile application.

Access is secured behind a master authorization challenge (`"GQA-12345"`) and guarded by a multi-channel hardware combo trigger designed for physical mobile devices.

---

## 🚀 Key Features & Highlights

### 🔍 1. Secret Mobile-Scale Database Inspector
* **Full SQLite Table Browser:**
  * Instant overview of all SQLite tables (`student_accounts`, scores, progression) and record counts.
  * **Schema Inspection:** View column definitions, data types, primary keys, and constraints.
  * **Data Grid & Pagination:** Browse records in interactive rows with previous/next page navigation.
* **Interactive SQL Query Console:**
  * Run arbitrary `SELECT`, `UPDATE`, or diagnostic queries directly in-app.
  * Instant display of execution status, affected row counts, and tabular query outputs.
* **Master Authorization Prompt:**
  * Access is secured by a dialog prompt requiring the master authorization key: `"GQA-12345"`.

---

### 📳 2. Hardware Physical Gesture Combo (Volume Up + Down + Shake)
* **Strict Multi-Condition Gate:**
  * Opening the menu strictly requires **holding/rocking Volume Up and Volume Down AND shaking the phone**.
  * **Shaking alone** will NEVER trigger the menu.
  * **Volume button clicks alone** will NEVER trigger the menu.
* **Instant Tactile Feedback:**
  * Every physical press of **Volume Up** or **Volume Down** triggers an immediate **20ms haptic tick** (`vibrateTick(20)`), confirming button registration.
* **Generous 4-Second Armed Window:**
  * Engaging both Volume Up and Volume Down (either pressed simultaneously or rocked in sequence within 4 seconds):
    * Emits a distinct **double-vibration pulse**.
    * Displays a brief toast: `"Combo Armed: Shake phone to open Database Inspector"`.
    * Allows players to comfortably grip and shake the phone without fingers slipping off the buttons.
* **Tuned Natural Shake Threshold:**
  * Calibrated to **1.45G** Euclidean acceleration threshold (`delta > 5.0`), allowing reliable activation with a comfortable, natural wrist shake.

---

### 🛡️ 3. Multi-Channel Hardware Reliability
* **Physical Rocker Switch Compatibility:**
  * Overcomes mechanical limitations on single-rocker smartphone buttons where pressing both ends simultaneously is physically blocked.
* **Dual-Channel Interception:**
  * Combines standard Activity `dispatchKeyEvent` with an Android `ContentObserver` listening directly to audio stream changes (`Settings.System.CONTENT_URI`), ensuring detection even on customized OEM Android skins (OneUI, MIUI, ColorOS) that intercept hardware keys.

---

## 📦 Distribution Packages

* **Android Beta Package:** `dist/v1.3.0-b1/GoQuiz-v1.3.0-b1.apk`
* **Release Channel:** `Beta Channel (v1.3.0-b1)`
* **Git Tag:** `v1.3.0-b1`

---

## 🔐 Master Secret Trigger Summary

| Platform | Trigger Gesture / Shortcut | Authorization Password |
| :--- | :--- | :--- |
| **Mobile (Android)** | Press/rock **Volume Up + Volume Down**, then **Shake** | `"GQA-12345"` |
| **Desktop (Windows / PC)** | `Ctrl + Shift + ~` (Squiggle / Tilde) | `"GQA-12345"` |
