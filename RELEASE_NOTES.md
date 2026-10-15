# 🎮 GoQuiz Adventure v1.3.1 — Release Notes

> **Tag:** `v1.3.1` (Stable) / `v1.3.1-b1` (Beta) • **Build:** `9` • **Release Date:** October 2026  
> **Platforms:** Android (Mobile/Tablet) & Windows (Desktop/PC)

---

## 🌟 Overview

**GoQuiz Adventure v1.3.1** is a targeted stability update resolving unintended admin security access re-triggers and polishing the physical hardware gesture combo across mobile and desktop.

This update equips developers, educators, and administrators with the ability to inspect database tables, schemas, student accounts, and test custom SQL queries directly on-device without needing external SQLite desktop tools, all while keeping the interface completely hidden from normal gameplay.

---

## 🚀 Key Features & Highlights

### 🔍 1. Secret Mobile-Scale Database Inspector
* **Full SQLite Table Browser:**
  * View all SQLite tables and record counts at a glance (`student_accounts`, quiz scores, settings).
  * **Schema Inspection:** View column names, data types, primary keys, and constraints.
  * **Data Pagination & Grid View:** Browse student account records with easy next/previous page navigation.
* **Interactive SQL Query Console:**
  * Execute custom `SELECT`, `UPDATE`, or diagnostic queries directly in-app.
  * Visual feedback on affected rows and formatted query results.
* **Master Password Protection:**
  * The inspector is protected by a master access prompt requiring authorization password: `"GQA-12345"`.

---

### 📳 2. Rock-Solid Physical Gesture Combo (Volume Up + Down + Shake)
* **Secret Trigger Requirements:**
  * The menu **strictly requires engaging both Volume Up and Volume Down AND shaking the phone**.
  * **Shaking alone** will NEVER trigger the menu.
  * **Volume button presses alone** will NEVER trigger the menu.
* **Immediate Tactile Feedback:**
  * Every press of **Volume Up** or **Volume Down** provides an instantaneous **20ms haptic tick** (`vibrateTick(20)`), confirming hardware detection immediately.
* **Generous 4-Second Armed Window:**
  * Once both Volume Up and Volume Down are engaged (either held simultaneously, or rocked in sequence within 4 seconds):
    * The phone emits a distinct **double-vibration pulse**.
    * A brief toast confirms: `"Combo Armed: Shake phone to open Database Inspector"`.
    * Gives players comfortable time to grip the phone and shake without fingers slipping off the buttons.
* **Tuned Natural Shake Threshold:**
  * Tuned to a comfortable **1.45G** Euclidean acceleration threshold (`delta > 5.0`), allowing reliable activation with a natural wrist shake without risk of dropping the device.

---

### 🛡️ 3. Multi-Channel Hardware Key Detection
* **Physical Rocker Switch Support:**
  * Overcomes mechanical limitations on modern single-rocker smartphone switches where pressing both ends at once is mechanically blocked.
* **Dual Event Interception:**
  * **Direct Activity Dispatch:** `dispatchKeyEvent`, `onKeyDown`, and `onKeyUp` capture raw hardware key events.
  * **System ContentObserver:** Monitors audio stream changes directly through `AudioManager` and `Settings.System.CONTENT_URI`, ensuring volume key presses are registered even if customized Android OEM skins (OneUI, MIUI, ColorOS) or accessibility tools consume raw key events.

---

## 📦 Distribution Packages

| Asset | Platform | Description |
| :--- | :--- | :--- |
| [**`GoQuiz-v1.3.0.apk`**](dist/v1.3.0/GoQuiz-v1.3.0.apk) | Android (API 23+) | **Official Stable Release** for mobile devices and tablets. |
| [**`GoQuiz-v1.3.0-b1.apk`**](dist/v1.3.0-b1/GoQuiz-v1.3.0-b1.apk) | Android (API 23+) | **Beta Channel Release** with early diagnostic telemetry. |

---

## 🔐 Master Secret Trigger Summary

| Action | Mobile (Android) | Desktop (Windows / PC) |
| :--- | :--- | :--- |
| **Trigger Action** | Press/rock **Volume Up + Volume Down**, then **Shake** | Special key combination (`Ctrl + Shift + ~`) |
| **Authorization Key** | `"GQA-12345"` | `"GQA-12345"` |
