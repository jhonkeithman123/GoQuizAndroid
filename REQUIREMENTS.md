# Requirements & Setup Guide — GoQuiz Android

This document outlines all system requirements, software prerequisites, dependencies, and file structures required to build, run, and develop the **GoQuiz Adventure** Android application.

---

## 1. System & Software Prerequisites

To build and run this Android project, ensure the following software is installed on your computer:

| Software | Required Version | Purpose |
| :--- | :--- | :--- |
| **Java Development Kit (JDK)** | **JDK 17** (or JDK 21) | Required by Gradle and Android Gradle Plugin 8.x |
| **Android SDK** | **API Level 35** (Android 15) | Compiles the Android code |
| **Android Build-Tools** | **34.0.0** or **35.0.0+** | Packages and signs DEX files and resources |
| **Gradle** | **8.9** | Build automation tool (*already included via `gradlew.bat` / `gradlew`*) |
| **Android Studio** *(Optional)* | **Ladybug (2024.2+)** or newer | Recommended IDE for visual editing, emulator, and debugging |

---

## 2. Environment Configuration

### Android SDK Path (`local.properties`)
The project reads the location of your Android SDK from [local.properties](file:///c:/Users/131fgh/Desktop/GoQuizAndroidRebuilt/local.properties) in the project root:

```properties
sdk.dir=C\:\\Users\\131fgh\\AppData\\Local\\Android\\Sdk
```
*(If building on another machine, ensure this path points to that machine's Android SDK location).*

### JAVA_HOME Environment Variable
Ensure `JAVA_HOME` is configured in your system environment variables pointing to your JDK 17 installation (or have `java` available in your system `PATH`).
Verify in terminal:
```powershell
java -version
```

---

## 3. Project Specifications & Dependencies

- **Application ID / Namespace**: `com.goquiz.adventure`
- **Android Gradle Plugin (AGP)**: `8.6.1`
- **Gradle Version**: `8.9`
- **Compile SDK**: `35`
- **Target SDK**: `35`
- **Min SDK**: `23` (Supports Android 6.0 through Android 15+)
- **Network Permissions**: `android.permission.INTERNET` (configured in [AndroidManifest.xml](file:///c:/Users/131fgh/Desktop/GoQuizAndroidRebuilt/app/src/main/AndroidManifest.xml))
- **External Runtime**:
  - The in-game leaderboard connects to the desktop **QuizServer** via TCP socket on **port 5050** over Wi-Fi / LAN.

---

## 4. Directory & File Organization

The project follows the standard Android Gradle structure for maximum compatibility:

```text
GoQuizAndroidRebuilt/
├── apks/                              # Ready-to-install output APKs (Debug & Release)
│   ├── app-debug.apk                  # Development APK with debugging enabled
│   └── app-release.apk                # Optimized Release APK (signed & installable)
├── app/                               # Main Android application module
│   ├── build.gradle                   # App module build configuration, SDK versions & tasks
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml    # App manifest (permissions, main activity, theme)
│           ├── assets/
│           │   ├── questions.json     # 5-level question bank for HTML, CSS, JS, and Java
│           │   ├── images/GoQuiz.ico  # Bundled Windows icon asset
│           │   └── sounds/            # Packaged audio assets
│           │       ├── click.wav      # Button tap audio feedback
│           │       ├── damage.wav     # Heart loss sound on wrong answer
│           │       └── menu_theme.wav # Background soundtrack
│           ├── java/com/goquiz/adventure/
│           │   ├── MainActivity.java  # Core game logic, UI screens, audio engine, leaderboard
│           │   └── QuizServer.java    # Embedded leaderboard server (can host directly on Android)
│           └── res/
│               ├── drawable/          # Launcher drawables
│               ├── mipmap-*/          # Scaled app launcher icons (MDPI to XXXHDPI)
│               └── values/styles.xml  # App theme, accent colors, and status bar styles
├── desktop/                           # Desktop Java Swing edition (for PC players)
│   ├── QuizGame.java                  # Full Desktop Java Swing edition
│   ├── QuizServer.java                # Desktop embedded server
│   ├── run-desktop.bat                # 1-Click launcher for desktop edition
│   └── *.properties                   # Desktop accounts and progress storage
├── server/                            # Standalone PC Leaderboard Server
│   ├── QuizServer.java                # Pure Java socket server (port 5050)
│   ├── run-server.bat                 # 1-Click launcher to run QuizServer on PC
│   └── online_leaderboard.properties  # Stored student rankings
├── images/GoQuiz.ico                  # Master app icon source file
├── sounds/                            # Master game audio files
├── gradle/wrapper/                    # Gradle wrapper binaries & distribution configuration
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── .gitignore                         # Ignores build outputs, IDE caches, and local configurations
├── build.gradle                       # Root build script defining Android Gradle plugin version
├── gradlew                            # Gradle wrapper execution script for macOS / Linux
├── gradlew.bat                        # Gradle wrapper execution script for Windows
├── local.properties                   # Local machine Android SDK directory path
├── README.md                          # Quick project summary and feature overview
└── REQUIREMENTS.md                    # Detailed requirements, dependencies, and build instructions
```

---

## 5. Build & Export Instructions

Open a terminal (PowerShell, Command Prompt, or bash) in the root project folder:

### A. Build Release APK
```powershell
.\gradlew.bat assembleRelease
```
Produces: `app/build/outputs/apk/release/app-release.apk`

### B. Build Debug APK
```powershell
.\gradlew.bat assembleDebug
```
Produces: `app/build/outputs/apk/debug/app-debug.apk`

### C. Build & Export Both to `/apks` Folder
```powershell
.\gradlew.bat exportApks
```
Builds both variants and automatically copies them into the root [apks/](file:///c:/Users/131fgh/Desktop/GoQuizAndroidRebuilt/apks) folder for easy access.

---

## 6. How to Install on an Android Device

### Method 1: Direct File Transfer (No PC tools required)
1. Copy `apks/app-release.apk` (or `app-debug.apk`) to your phone via USB, Google Drive, or messaging.
2. On your phone, tap the APK and choose **Install** (allow *Install unknown apps* if prompted).

### Method 2: Via ADB (Command Line)
If USB debugging is enabled on your connected device:
```powershell
adb install -r apks/app-release.apk
```
