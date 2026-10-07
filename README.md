# GoQuiz Adventure — Android Project

This is a native Android rebuild based on the uploaded QuizGame.java question bank and feature requirements.

## Features
- GOQUIZ ADVENTURE title
- Student registration/login with Grade 11/12 and section
- Grade 11 hides Java
- Easy 4 hearts, Medium 3, Hard 2
- Separate progress per student + difficulty + language
- Badges show Easy/Medium/Hard progress together
- 5 levels with locked/unlocked progression
- Difficulty-specific source tiers for HTML, CSS, JavaScript, and Java
- Responsive ScrollView UI for phones/tablets
- Shared leaderboard client using QuizServer protocol on port 5050

## Build
Open this folder in Android Studio and let Gradle sync. Select an Android device and press Run.

### CLI Build Commands:
- **Debug APK**:
  ```powershell
  .\gradlew.bat assembleDebug
  ```
  Output: `app/build/outputs/apk/debug/app-debug.apk`

- **Release APK**:
  ```powershell
  .\gradlew.bat assembleRelease
  ```
  Output: `app/build/outputs/apk/release/app-release.apk`

- **Build & Export Both to Root `apks/` Folder**:
  ```powershell
  .\gradlew.bat exportApks
  ```
  Output: `apks/app-release.apk` and `apks/app-debug.apk`

- **Online Leaderboard**:
  - The Android app can connect to a PC/server running [server/run-server.bat](file:///c:/Users/131fgh/Desktop/GoQuizAndroidRebuilt/server/run-server.bat).
  - Alternatively, the Android app can **host its own server directly on the phone** by tapping **HOST SERVER ON THIS DEVICE** in the Leaderboard screen! Other phones can then connect to that phone's IP address.
  - The desktop Swing version can be run on PC via [desktop/run-desktop.bat](file:///c:/Users/131fgh/Desktop/GoQuizAndroidRebuilt/desktop/run-desktop.bat).

For complete prerequisites, SDK setup, and detailed file map, see [REQUIREMENTS.md](file:///c:/Users/131fgh/Desktop/GoQuizAndroidRebuilt/REQUIREMENTS.md).
