# 🎮 GoQuiz Adventure v1.2.1 — Release Notes

> **Tag:** `v1.2.1` • **Release Date:** October 2026 • **Platforms:** Android (Mobile/Tablet) & Windows (Desktop/PC)

---

## 🌟 Overview

**GoQuiz Adventure v1.2.1** is a critical stability, distribution, and runtime enhancement release. It delivers a **100% zero-dependency, self-contained desktop experience** on Windows while building upon the cross-platform 1v1 PvP multiplayer, high-resolution vector visual polish, and story campaign features introduced in v1.2.0.

This update completely eliminates the *"Java Virtual Machine Launcher: A Java Exception has occurred"* startup error on PCs with legacy Java runtimes (such as Java 8) and packages all sound effects, visual novel cutscenes, and curriculum questions directly with the game.

---

## 🚀 What's New in v1.2.1

### ⚡ Zero-Dependency Portable Windows Runtime (`jre/`)
- **Self-Contained Execution:** Bundled a streamlined, modular Java 17 LTS runtime (`java.base`, `java.desktop`) generated via `jlink`.
- **Zero Prerequisites:** Runs out of the box on Windows 10 and Windows 11 (64-bit) without requiring Java, JDK, or any third-party software to be installed on the host computer.
- **Eliminated Class Version Errors:** Completely prevents the *"Java Virtual Machine Launcher: A Java Exception has occurred"* dialog by isolating the game from outdated system Java 8 (JRE 1.8) installations.

### 📦 Complete Offline Media & Asset Packaging
- **Bundled Sound Effects & BGM:** All audio files (`sounds/click.wav`, `sounds/damage.wav`, `sounds/menu_theme.wav`) are now included inside the desktop bundle.
- **Full Story & Character Sprites:** Cinematic visual novel cutscenes and character frames (`images/Boy/`, `images/Girl/`) load seamlessly offline.
- **Offline Question Bank:** Complete 4-module curriculum (`questions.json`) bundled for offline study and duels.

### 🛡️ Smart Native Launcher Safeguards (`GoQuiz.exe`)
- **Unextracted ZIP Detection:** Automatically detects if a user opened the game directly inside an uncompressed Windows `.zip` viewer and displays clear instructions to right-click and choose **"Extract All..."**.
- **Accurate Runtime Probing:** Probes candidate Java versions via `java.exe -version` to prevent Windows GUI buffering issues from masking valid runtimes.
- **Clean Distribution Archive:** The Windows release bundle only presents the native executable and required assets, preventing users from accidentally double-clicking loose `.jar` files with obsolete system Java runtimes.
- **Startup Crash Interceptor:** Monitors JVM initialization and catches early exit codes to provide descriptive troubleshooting guidance.

---

## ⚔️ Key Features (from v1.2.0)

### ⚔️ Cross-Platform 1v1 PvP Live Duels
- **Cross-Platform LAN & Hotspot Matchmaking:** Connect, host, and duel between **Android to Android**, **PC to PC**, or **Android to PC**.
- **UDP Zero-Configuration Discovery:** Auto-detects active QuizServers on port `5052` across local Wi-Fi or mobile hotspots without typing manual IP addresses.
- **Synchronized Cinematics:** Dramatic pre-duel match cards, dynamic round transitions, and animated victory/defeat podiums.
- **Live Telemetry & HP Sync:** Real-time opponent health, score, and combo tracking during duels.
- **Solo Continuation & Forfeit Safety:** If an opponent disconnects or forfeits, finish answering the quiz at your own pace without losing score or progress.

### 💎 High-DPI Vector GUI & Graphic Polish
- **Glossy Vector Ruby Hearts:** Custom Java 2D vector-drawn hearts with radial ruby gradients, specular highlights, and crimson borders. Completely eliminates missing font box glyphs (`[]` or `?`) on Windows.
- **Custom Game Icon:** Window titlebar and taskbar proudly feature the official GoQuiz Adventure crest.

### 📜 Shared In-Game Version Control & Changelog
- **Unified Versioning (`version.json`):** Shared release metadata between mobile and desktop clients.
- **In-Game Credits & Version Screen:** View live build information, game credits, and complete release history directly from the main menu.

---

## 📦 Downloads & Artifacts

| Asset | Platform | Description |
| :--- | :--- | :--- |
| **`GoQuiz-v1.2.1-Windows.zip`** | Windows 10 / 11 (64-bit) | **Recommended for PC.** Zero-setup bundle with portable Java 17, launcher, and full assets. |
| **`GoQuiz-v1.2.1.exe`** | Windows 10 / 11 (64-bit) | Standalone native Windows launcher with embedded game JAR. |
| **`GoQuiz-v1.2.1.jar`** | Windows / macOS / Linux | Cross-platform standalone runnable Java JAR package (requires Java 17+). |
| **`GoQuiz-v1.2.1.apk`** | Android 6.0+ (API 23–35) | Full production release package for smartphones and tablets. |

---

## 📥 Installation Instructions

### 🖥️ Windows Desktop (Zero-Setup Bundle)
1. Download **`GoQuiz-v1.2.1-Windows.zip`**.
2. **Right-click** the downloaded ZIP file and select **"Extract All..."**.
3. Choose your destination folder and click **Extract**.
4. Open the extracted folder and double-click **`GoQuiz-v1.2.1.exe`** to play.
   *(No Java installation required!)*

### 📱 Android
1. Download **`GoQuiz-v1.2.1.apk`**.
2. Tap the downloaded file on your Android device to install (allow *"Install unknown apps"* if prompted).
3. Alternatively, install via ADB:
   ```bash
   adb install -r GoQuiz-v1.2.1.apk
   ```

---

## 🏆 Curriculum Modules Included
- **HTML:** Semantic Structure, Forms, Tables, Media, and Modern Elements
- **CSS:** Flexbox, Grid, Animations, Selectors, Box Model, and Typography
- **JavaScript:** ES6+ Syntax, DOM Manipulation, Async/Await, and Logic
- **Java:** Object-Oriented Programming, Data Structures, Sockets, and Concurrency

---

*Developed with ❤️ by the GoQuiz Adventure Team.*
