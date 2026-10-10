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
- **Animation & Cinematic System**:
  - **Pre-Question Cinematic Intro**: Plays a 7-panel mix-fade (crossfade) narrative sequence (`Frame1.jpg` to `Frame7.jpg`) after selecting a level before displaying Question 1, with a fair duration (~1.5s display + 600ms crossfade), interactive screen tap advance, and an instant `SKIP ⏩` button.
  - **Question Screen Background**: Uses `Frame7.jpg` as the in-game background for the questions menu, preserving cinematic continuity from the end of the cutscene straight into gameplay.
  - **Correct Answer Combat Strike Animation**: When answering an intermediate question correctly, the background animates the hero's strike (`Frame 7 -> Frame 8 -> Frame 9 -> Frame 7`) while the question card gently dims, landing a physical blow on the monster before Question 2+ glides in.
  - **Final Question Victory Sequence**: Answering the final question correctly triggers a celebratory cutscene across 6 frames (`Frame 7 -> Frame 8 -> Frame 9 -> Frame 15 -> Frame 16 -> Frame 17`), concluding with the monster's defeat and hero's victory.
  - **Wrong Answer Damage Reactions**:
    - Intermediate questions (health > 2): Plays `Frame 10` (`Frame10Alt.jpg`) and returns smoothly to `Frame 7` (`Frame7.jpg`) before moving to the next question.
    - Intermediate questions (last 2 health, `hearts == 2` or `hearts == 1`): Plays `Frame 11` (`Frame11Alt.jpg`) instead and returns smoothly to `Frame 7`.
    - **Final Question Wrong Answer with Hearts Remaining**: If the player fails the final question of a level but still has hearts left, triggers a climactic battle clash: monster attacks (`Frame 10`), hero counter-attacks (`Frame 9`), and the killing sequence plays (`Frame 15 -> Frame 16 -> Frame 17`), concluding with the level complete retry screen (`Frame 4`).
  - **Health 0 Defeat Cinematic & Game Over Menu**:
    - When health reaches 0, plays an immersive cinematic sequence from `Frame 11` to `Frame 14` (`Frame11Alt.jpg` -> `Frame12Alt.jpg` -> `Frame13Alt.jpg` -> `Frame14Alt.jpg`) with narrative defeat subtitles and skip support.
    - Displays a dedicated `"GAME OVER"` menu with `Frame14Alt.jpg` background, retry level, back to levels, and back to main menu options.
  - **Play-to-Menu Fade Transitions**: Smooth fade-out (`alpha -> 0f`) transitions whenever navigating away from gameplay or between menus (quit dialog confirm, level complete back, game over back, and navigation buttons).
  - **Level Complete Screen Background (`Frame 4`)**: When finishing a level with hearts left, the background shown on the Level Complete menu displays `Frame 4` (`Frame4.jpg`).
  - **Next Level & Level Replay Cinematic Continuation (Skip to Frame 5)**:
    - Advancing to the next level (`NEXT LEVEL`), replaying a cleared level (`REPLAY LEVEL`), or retrying for a perfect score (`TRY AGAIN FOR PERFECT SCORE`) skips the outdoor approach cutscenes (`Frame 1` to `Frame 4`).
    - Since the hero concluded the previous level inside the cave (`Frame 4`), the pre-quiz narrative resumes seamlessly at **`Frame 5`** (`Frame 5` beast roars $\rightarrow$ `Frame 6` draws blade $\rightarrow$ `Frame 7` battle begins) with dynamic progress dots and matching captions.
  - **Death / Game Over Dramatic Restart & Extended Fade-Out**:
    - When running out of hearts and retrying from the Game Over screen (`TRY AGAIN ↺`), the animation completely restarts from the beginning (**`Frame 1`** through `Frame 7`).
    - Employs an extended, deliberate **650ms fade-out** (compared to the standard 220ms menu fade) to emphasize defeat and dramatic retry.
  - **Universal Smooth Frame Switching Transitions**:
    - Every frame switch throughout the app uses smooth animated transitions:
      - Initial frames in all cutscenes and screens fade in smoothly (`alpha 0f -> 1f` in 280ms–400ms).
      - Story cutscenes (Intro 1–7, Victory 7–17, Defeat 11–14) crossfade smoothly between dual image views with `AccelerateDecelerateInterpolator` (400ms–550ms).
      - In-game attack strike (`7 -> 8 -> 9 -> 7`) and damage reactions (`7 -> 10/11 -> 7`) smoothly crossfade (180ms–200ms).
      - Screen transitions fade out both background frames and UI content together before loading destination screens.
  - **Midway Quiz Save & Resume System**:
    - When quitting a quiz in progress (via `🏠 MENU` button or Android Back button), players are presented with a styled dialog:
      - **SAVE PROGRESS & QUIT 💾**: Saves complete run state (current question index, score, correct answers count, remaining hearts, language, level, difficulty, and question pool) to `SharedPreferences` keyed per student.
      - **DISCARD PROGRESS ✕**: Clears saved midway run and returns cleanly to Main Menu.
      - **CONTINUE PLAYING ▶**: Dismisses dialog and stays in the active question without penalty.
    - **Resume Access Points**:
      - **Home Screen**: A prominent green `"RESUME QUIZ 💾 (Language • Level X • Qn/5)"` button appears dynamically above the main menu buttons.
      - **Level Selection Screen**: The specific level button is highlighted green with a dynamic badge: `"LEVEL X • RESUME SAVED (Qn/5) 💾"`.
    - **Auto-Cleanup**: Saved midway progress is automatically discarded upon level completion (both victory and retry) or game over defeat to ensure fresh runs on subsequent plays.
  - **Account Session Persistence**:
    - Student login is preserved across application closure and phone restarts via encrypted `SharedPreferences` session tracking (`loggedInStudent`).
    - Launching the app while previously logged in bypasses the login screen directly to the Main Menu with student credentials intact.
    - Session is only terminated when explicitly tapping `"LOG OUT 🚪"` in the game menus.
  - **Memory & Performance Optimization**: On-demand asynchronous decoding with hardware-accelerated dual-`ImageView` crossfading, screen-proportionate `inSampleSize`, `RGB_565` bitmap configuration, and immediate recycling of bitmaps to ensure zero OOM risk and fluid 60 FPS transitions.
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
│   ├── build.gradle                   # App module build configuration, SDK versions, path alias resolver (@/)
│   ├── assets/                        # Assets moved to local app root (accessible via @/assets)
│   │   ├── questions.json             # 480-question categorized bank across 4 languages, 3 difficulties & 5 levels
│   │   ├── images/                    # Packaged icon, wallpaper, and gender-based animation frame folders
│   │   │   ├── Boy/                   # Boy default character frames & wallpaper
│   │   │   ├── Girl/                  # Girl character frames & wallpaper
│   │   │   ├── GoQuiz.ico
│   │   │   └── Title.png
│   │   └── sounds/                    # Packaged audio assets
│   │       ├── click.wav              # Button tap audio feedback
│   │       ├── damage.wav             # Heart loss sound on wrong answer
│   │       └── menu_theme.wav         # Background soundtrack
│   └── src/                           # Cleaned source root (main folder removed)
│       ├── AndroidManifest.xml        # App manifest (permissions, main activity, theme, launcher icons)
│       ├── goquiz/                    # Un-nested Java source directory (src/goquiz)
│       │   ├── MainActivity.java      # Full Android game (all screens, difficulty engine, audio, frame engine)
│       │   └── QuizServer.java        # Embedded leaderboard server (can host directly on Android)
│       └── res/                       # Resource drawables, mipmaps, and themes
│           ├── drawable/              # UI drawables (card_panel, btn_fantasy, progress_fantasy, ic_launcher)
│           ├── drawable-nodpi/        # HD fantasy wallpaper background (quiz_adventure_background.png)
│           ├── mipmap-*/              # Scaled app launcher icons (MDPI to XXXHDPI)
│           └── values/styles.xml      # App theme, accent colors, and status bar styles
├── desktop/                           # Desktop Java Swing edition (for PC players)
│   ├── QuizGame.java                  # Full Desktop Java Swing edition
│   └── QuizServer.java                # Desktop embedded server
├── server/                            # Standalone PC Leaderboard Server
│   ├── QuizServer.java                # Pure Java socket server (port 5050)
│   └── online_leaderboard.properties  # Stored student rankings
├── scripts/                           # Centralized scripts directory
│   ├── build-apk.bat                  # Build & export Android APK
│   ├── install-app.bat                # Install & launch on Android phone/emulator
│   ├── run-desktop.bat                # Compile & run Desktop Java game
│   ├── run-server.bat                 # Run standalone QuizServer
│   ├── allow-firewall.bat             # Configure Windows Defender Firewall
│   └── clean.bat                      # Clean build caches & temporary files
├── goquiz.bat                         # Centralized GoQuiz CLI runner (run `goquiz` for menu)
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
