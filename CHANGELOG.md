# Changelog

All notable changes to the **GoQuiz Adventure** project are documented here. This changelog is dynamically embedded into both the Android and Desktop builds.

## [1.2.0] - 2026-10-10
### Added
- **Cross-Platform Live 1v1 PvP Duels**: Real-time multiplayer duels between Android and Desktop devices over LAN or Wi-Fi hotspot.
- **Cinematic Cutscene Synchronization**: Story intro, victory, and defeat cinematics now play during live multiplayer duels with synchronized turn advancing.
- **Solo Continuation on Disconnect / Forfeit**: If an opponent leaves or disconnects, the remaining player can choose to seamlessly convert the match into solo play to finish questions without pressure, or claim an instant victory.
- **In-Duel Forfeit / Leave Option**: Players can forfeit or switch to solo play at any time using the `🏳️ LEAVE` button in the HUD or Android back button.
- **High-Resolution Vector Heart Icons**: Replaced monospaced character hearts with pure Java 2D vector heart icons with gradients and highlights, fixing missing glyphs on Windows.
- **Shared Version Control System**: Unified `version.json` and `changelog.txt` loaded dynamically by both Desktop and Android.
- **Standalone Windows Desktop Executable**: Native `GoQuiz.exe` with embedded application icon (`GoQuiz.ico`) and automatic background launch.

## [1.1.0] - 2026-10-09
### Added
- **Online Leaderboard Server**: Integrated host server and embedded leaderboard on port 5050 with automatic network discovery.
- **Dynamic Camera Framing**: Added subtle touch/mouse drift framing on background cutscenes.
- **Curriculum Expansion**: Added comprehensive questions for HTML, CSS, JavaScript, and Java.

## [1.0.0] - 2026-10-08
### Initial Release
- Native Android app & Java Swing desktop interface.
- Story campaign with animated/frame cutscenes.
- User authentication, badge progression, and local scoring.
