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

## Quick Commands (Super Beginner Friendly)

You can run everything using your custom **`goquiz`** command:

### GoQuiz CLI Runner ([`goquiz.bat`](file:///c:/Users/131fgh/Desktop/GoQuizAndroidRebuilt/goquiz.bat)):
Type **`goquiz`** in your terminal to open an interactive menu, or pass a command directly:

| Command | Action |
| :--- | :--- |
| `goquiz` | Opens the interactive visual menu |
| `goquiz dist` | Builds **FULL Multi-Platform Release** (`dist/GoQuiz-v*.apk` & `.exe`) |
| `goquiz exe` | Builds **Desktop Windows Executable** into `dist/` |
| `goquiz release` | Builds **Release APK** into `dist/` (`dist/GoQuiz-v*.apk`) |
| `goquiz apk` *(or `goquiz build`)* | Builds **BOTH** Debug & Release APKs into `dist/` |
| `goquiz debug` | Builds **Debug APK** into `dist/` |
| `goquiz install` | Installs APK to connected phone/emulator and launches game |
| `goquiz desktop` | Compiles & runs Desktop Java game |
| `goquiz server` | Starts standalone QuizServer on port 5050 |
| `goquiz firewall` | Configures Windows Firewall for multiplayer |
| `goquiz clean` | Cleans Gradle build caches and class files |
| `goquiz help` | Shows all available commands |


All individual command scripts are located in the [`scripts/`](file:///c:/Users/131fgh/Desktop/GoQuizAndroidRebuilt/scripts) folder.

For complete prerequisites, SDK setup, and detailed file map, see [REQUIREMENTS.md](file:///c:/Users/131fgh/Desktop/GoQuizAndroidRebuilt/REQUIREMENTS.md).

