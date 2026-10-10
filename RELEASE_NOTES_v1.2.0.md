# 🎮 GoQuiz Adventure v1.2.0 — Release Notes

> **Tag:** `v1.2.0` • **Release Date:** October 2026 • **Platforms:** Android (Mobile/Tablet) & Windows (Desktop/PC)

---

## 🌟 Overview

**GoQuiz Adventure v1.2.0** brings exciting cross-platform multiplayer gameplay, a standalone Windows desktop executable, high-resolution vector visual polish, and a unified build and distribution pipeline across Android and PC.

Whether you are studying web development and computer science on an Android phone or battling friends in real-time on desktop, version 1.2.0 delivers a seamless, synchronized experience!

---

## 🚀 What's New in v1.2.0

### ⚔️ Cross-Platform 1v1 PvP Live Duels
- **Cross-Platform LAN & Hotspot Matchmaking:** Connect, host, and duel between **Android to Android**, **PC to PC**, or **Android to PC**.
- **UDP Zero-Configuration LAN Discovery:** Auto-discovers active QuizServers on port `5052` across local Wi-Fi or mobile hotspots without typing IP addresses.
- **Synchronized Cinematics:** Experience dramatic pre-duel vs. match cards, dynamic round transitions, and animated victory/defeat podiums.
- **Live Telemetry & HP Sync:** Real-time opponent health, score, and combo tracking during duels.
- **Solo Continuation & Forfeit Safety:** If an opponent disconnects or you choose to forfeit, smoothly continue answering the quiz at your own pace without losing progress.

### 🖥️ Native Windows Desktop Executable (`GoQuiz.exe`)
- **Direct Double-Click Launch:** Native Windows launcher (`.exe`) with the official high-resolution game icon embedded.
- **No Annoying Console Windows:** Launches cleanly without popping up a black terminal/command prompt.
- **Smart Java Runtime Discovery:** Automatically detects local JRE/JDK installations (`Adoptium`, `Oracle`, `Zulu`, `BellSoft`, `Microsoft Corretto`) across `Program Files` and system `PATH`.
- **Friendly Guided Feedback:** If Java 17+ is not installed, displays a helpful one-click prompt to download Java directly from Adoptium Temurin.

### 💎 High-DPI Vector GUI & Graphic Polish
- **Glossy Vector Ruby Hearts:** Replaced system font characters (`♥` / `❤️`) with custom Java 2D vector-drawn hearts with radial ruby gradients, specular highlights, and crimson borders. Completely eliminates missing box glyphs (`[]` or `?`) on Windows.
- **Official Window & Taskbar Icon:** Window titlebar and Windows taskbar now feature the custom GoQuiz Adventure crest.
- **Character Asset Resolution:** Fixed background loading for female hero profiles.

### 📜 Shared In-Game Version Control & Changelog
- **Unified Versioning (`version.json`):** Shared release metadata between mobile and desktop clients.
- **In-Game Credits & Version Screen:** View live build information, game credits, and complete release history directly from the main menu.

### 🛠️ One-Click Distribution Build System
- **`goquiz dist`**: Compiles and outputs versioned release packages in one command:
  - `GoQuiz-v1.2.0.apk` (Android Production Signed Release)
  - `GoQuiz-v1.2.0.exe` (Windows Desktop Executable)
  - `GoQuiz-v1.2.0.jar` (Cross-Platform Java Runnable Package)
- **Consolidated Server Codebase:** Unified `QuizServer.java` into a single canonical source file serving Android, Desktop, and standalone dedicated server modes.

---

## 📦 Downloads & Artifacts

| Asset | Platform | Description |
| :--- | :--- | :--- |
| **`GoQuiz-v1.2.0.apk`** | Android 6.0+ (API 23–35) | Full production release package for smartphones and tablets |
| **`GoQuiz-v1.2.0-Windows.zip`** | Windows 10 / 11 (64-bit) | Compressed Desktop release bundle (contains `.exe` and `.jar`) |
| **`GoQuiz-v1.2.0.exe`** | Windows 10 / 11 (64-bit) | Standalone desktop launcher with embedded game JAR |
| **`GoQuiz-v1.2.0.jar`** | Windows / macOS / Linux | Cross-platform standalone runnable Java JAR package |

---

## 📥 Installation Instructions

### Android
1. Download **`GoQuiz-v1.2.0.apk`**.
2. Tap the downloaded file on your Android device to install (allow *"Install unknown apps"* if prompted).
3. Alternatively, install via ADB:
   ```bash
   adb install -r GoQuiz-v1.2.0.apk
   ```

### Windows Desktop
1. Download **`GoQuiz-v1.2.0.exe`** and keep **`GoQuiz-v1.2.0.jar`** in the same folder.
2. Double-click **`GoQuiz-v1.2.0.exe`** to play.
3. *Requirement:* Java 17 or higher (the launcher will guide you with a download link if Java is missing).

---

## 🏆 Curriculum Modules Included
- **HTML:** Semantic Structure, Forms, Tables, Media, and Modern Elements
- **CSS:** Flexbox, Grid, Animations, Selectors, Box Model, and Typography
- **JavaScript:** ES6+ Syntax, DOM Manipulation, Async/Await, and Logic
- **Java:** Object-Oriented Programming, Data Structures, Sockets, and Concurrency

---

*Developed with ❤️ by the GoQuiz Adventure Team.*
