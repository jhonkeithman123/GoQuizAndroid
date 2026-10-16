# Graph Report - GoQuizAndroidRebuilt  (2026-10-16)

## Corpus Check
- 31 files · ~3,273,061 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 24 file(s) not represented in the graph (top: .bat 10, .xml 5, .properties 3)

## Summary
- 908 nodes · 2310 edges · 60 communities (30 shown, 30 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 117 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `35f0f10b`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- MainActivity
- Override
- QuizGame
- .createStyledTextView
- .showQuestionIntroCinematic
- QuizServer
- src/server.ts
- .showDatabaseInspector
- .showLiveDuelLobbyScreen
- .handleDuelMessage
- Requirements & Setup Guide — GoQuiz Android
- BackgroundPanel
- GoQuiz Adventure — Global Online Multiplayer Architecture Plan
- .showDatabaseInspector
- .handleDuelMessage
- package.json
- GoQuiz Adventure — Global Multiplayer Cloud Server
- FantasyButton
- 🎮 GoQuiz Adventure v1.2.1 — Release Notes
- Override
- Screen
- Changelog
- compilerOptions
- InteractiveContainerLayout
- .onKeyDown
- launcher.cs
- .handleMessage
- .resetSecretComboState
- SimpleJson
- dependencies
- .dispatchKeyEvent
- .executeOnlineLeaderboardFetch
- IClientSession
- 🎮 GoQuiz Adventure v1.3.0 — Release Notes
- 🎮 GoQuiz Adventure v1.3.1 (Stable) — Release Notes
- 🎮 GoQuiz Adventure v1.2.2 — Release Notes
- SeekBar.OnSeekBarChangeListener
- .showInteractiveTutorialDialog
- 🎮 GoQuiz Adventure v1.3.0-b1 (Beta 1) — Release Notes
- scripts
- ClientSession
- .onItemSelected
- FramingMode
- .getDropDownView
- DesktopCameraMode
- .onAnimationEnd
- java.awt.event.WindowAdapter

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 181 edges
2. `QuizGame` - 155 edges
3. `QuizServer` - 26 edges
4. `QuizDatabase` - 19 edges
5. `QuizDatabaseHelper` - 18 edges
6. `Screen` - 15 edges
7. `FantasyButton` - 14 edges
8. `DuelClient` - 12 edges
9. `compilerOptions` - 12 edges
10. `InteractiveImageView` - 11 edges

## Surprising Connections (you probably didn't know these)
- `🛡️ 3. Multi-Channel Hardware Reliability` --references--> `ContentObserver`  [INFERRED]
  RELEASE_NOTES_v1.3.0-b1.md → app/src/goquiz/MainActivity.java
- `📳 2. Hardware Physical Combo Polish` --references--> `ContentObserver`  [INFERRED]
  RELEASE_NOTES_v1.3.1.md → app/src/goquiz/MainActivity.java
- `Phase 1: Cloud-Ready Server Packaging` --references--> `generateRoomCode()`  [INFERRED]
  PLANNING.md → server/src/server.ts
- `QuizGame` --references--> `QuizServer`  [EXTRACTED]
  desktop/QuizGame.java → app/src/goquiz/QuizServer.java
- `MainActivity` --references--> `QuizServer`  [EXTRACTED]
  app/src/goquiz/MainActivity.java → app/src/goquiz/QuizServer.java

## Import Cycles
- None detected.

## Communities (60 total, 30 thin omitted)

### Community 0 - "MainActivity"
Cohesion: 0.07
Nodes (3): MainActivity, Question, TutorialPageRenderer

### Community 1 - "Override"
Cohesion: 0.06
Nodes (9): AlphaPanel, MouseAdapter, MouseMotionAdapter, MouseWheelListener, DamageFlashPanel, FantasySliderUI, AbstractAction, javax.swing.event.DocumentListener (+1 more)

### Community 5 - ".showQuestionIntroCinematic"
Cohesion: 0.13
Nodes (4): Runnable, Runnable, Runnable, Runnable

### Community 6 - "QuizServer"
Cohesion: 0.11
Nodes (4): ClientHandler, ConnectedDeviceInfo, DuelRoom, QuizServer

### Community 7 - "src/server.ts"
Cohesion: 0.08
Nodes (23): ws, activeSessions, app, DATA_DIR, __dirname, __filename, HTTP_PORT, lastKeepAlivePing (+15 more)

### Community 9 - ".showDatabaseInspector"
Cohesion: 0.17
Nodes (5): ColumnMeta, QuizDatabase, DefaultTableModel, DefaultTableModel, StudentAccount

### Community 13 - "Requirements & Setup Guide — GoQuiz Android"
Cohesion: 0.10
Nodes (18): Features, GoQuiz Adventure — Android Project, GoQuiz CLI Runner ([`goquiz.bat`](goquiz.bat)):, Quick Commands (Super Beginner Friendly), 1. System & Software Prerequisites, 2. Environment Configuration, 3. Project Specifications & Dependencies, 4. Directory & File Organization (+10 more)

### Community 14 - "BackgroundPanel"
Cohesion: 0.16
Nodes (4): BackgroundPanel, FantasyPanel, MouseAdapter, TransitionRootPanel

### Community 16 - "GoQuiz Adventure — Global Online Multiplayer Architecture Plan"
Cohesion: 0.11
Nodes (17): 1. Executive Summary & Objective, 2. Why LAN Multiplayer Fails Over the Global Internet, 3. Architecture Comparison: 5 Global Strategies, 4. The Recommended Architecture: Central Cloud Relay + Room Codes, 5.1 Room Creation Flow, 5.2 Heartbeats & Reconnection Resilience, 5. Protocol Design & Room Lifecycle, 6. Infrastructure & Hosting Options (Cost & Practicality) (+9 more)

### Community 19 - "package.json"
Cohesion: 0.12
Nodes (16): cors, dotenv, express, @supabase/supabase-js, @types/cors, @types/express, @types/node, @types/ws (+8 more)

### Community 20 - "GoQuiz Adventure — Global Multiplayer Cloud Server"
Cohesion: 0.12
Nodes (16): 1. Install Dependencies, 2. Configure Environment (Optional), 3. Run the Server, 📡 API & WebSocket Specification, ☁️ Deploying to Render (Free Tier), GoQuiz Adventure — Global Multiplayer Cloud Server, ⏰ How to Prevent Render Free-Tier From Sleeping, Option 1: Free External Cron Job (Recommended) (+8 more)

### Community 21 - "FantasyButton"
Cohesion: 0.22
Nodes (3): FantasyButton, GoldBorder, TutorialStep

### Community 22 - "🎮 GoQuiz Adventure v1.2.1 — Release Notes"
Cohesion: 0.12
Nodes (15): 📱 Android, 📦 Complete Offline Media & Asset Packaging, ⚔️ Cross-Platform 1v1 PvP Live Duels, 🏆 Curriculum Modules Included, 📦 Downloads & Artifacts, 🎮 GoQuiz Adventure v1.2.1 — Release Notes, 💎 High-DPI Vector GUI & Graphic Polish, 📥 Installation Instructions (+7 more)

### Community 23 - "Override"
Cohesion: 0.23
Nodes (3): InteractiveImageView, View.OnAttachStateChangeListener, CountDownTimer

### Community 24 - "Screen"
Cohesion: 0.13
Nodes (15): Screen, BADGES, CINEMATIC, CREDITS, DIFFICULTY, DUEL_LOBBY, DUEL_QUIZ, HOME (+7 more)

### Community 25 - "Changelog"
Cohesion: 0.14
Nodes (13): [1.0.0] - 2026-10-08, [1.1.0] - 2026-10-09, [1.2.0] - 2026-10-10, [1.2.2-b1] - 2026-10-14, [1.3.0] - 2026-10-14, [1.3.1] - 2026-10-15, Added, Added (+5 more)

### Community 26 - "compilerOptions"
Cohesion: 0.14
Nodes (13): compilerOptions, allowSyntheticDefaultImports, esModuleInterop, forceConsistentCasingInFileNames, lib, module, moduleResolution, noEmit (+5 more)

### Community 27 - "InteractiveContainerLayout"
Cohesion: 0.21
Nodes (3): InteractiveContainerLayout, GestureDetector.SimpleOnGestureListener, ScaleGestureDetector.SimpleOnScaleGestureListener

### Community 28 - ".onKeyDown"
Cohesion: 0.18
Nodes (9): 🔍 1. Secret Mobile-Scale Database Inspector, 📳 2. Rock-Solid Physical Gesture Combo (Volume Up + Down + Shake), 🛡️ 3. Multi-Channel Hardware Key Detection, 📦 Distribution Packages, 🎮 GoQuiz Adventure v1.3.1 — Release Notes, 🚀 Key Features & Highlights, 🔐 Master Secret Trigger Summary, 🌟 Overview (+1 more)

### Community 30 - ".handleMessage"
Cohesion: 0.22
Nodes (10): Phase 1: Cloud-Ready Server Packaging, clean(), generateRoomCode(), getConnectedClientsMessage(), getLeaderboardMessage(), getSortedLeaderboard(), loadLeaderboard(), recordMatchHistory() (+2 more)

### Community 35 - "dependencies"
Cohesion: 0.18
Nodes (11): dependencies, cors, dotenv, express, @supabase/supabase-js, @types/cors, @types/express, @types/node (+3 more)

### Community 36 - ".dispatchKeyEvent"
Cohesion: 0.29
Nodes (6): ContentObserver, 🔍 1. Secret Mobile-Scale Database Inspector, 📳 2. Hardware Physical Gesture Combo (Volume Up + Down + Shake), 🛡️ 3. Multi-Channel Hardware Reliability, 🚀 Key Features & Highlights, 📳 2. Hardware Physical Combo Polish

### Community 38 - "IClientSession"
Cohesion: 0.25
Nodes (3): DuelRoom, IClientSession, IDuelRoom

### Community 39 - "🎮 GoQuiz Adventure v1.3.0 — Release Notes"
Cohesion: 0.25
Nodes (7): 🔍 1. Secret Mobile-Scale Database Inspector, 📳 2. Rock-Solid Physical Gesture Combo (Volume Up + Down + Shake), 📦 Distribution Packages, 🎮 GoQuiz Adventure v1.3.0 — Release Notes, 🚀 Key Features & Highlights, 🔐 Master Secret Trigger Summary, 🌟 Overview

### Community 40 - "🎮 GoQuiz Adventure v1.3.1 (Stable) — Release Notes"
Cohesion: 0.25
Nodes (7): 🔐 1. Admin Security Access Stability, 💻 3. Desktop Hotkey Re-trigger Protection, 📦 Distribution Packages, 🎮 GoQuiz Adventure v1.3.1 (Stable) — Release Notes, 🛠️ Key Fixes & Improvements, 🌟 Overview, 🔑 Quick Verification Summary

### Community 41 - "🎮 GoQuiz Adventure v1.2.2 — Release Notes"
Cohesion: 0.29
Nodes (6): ⏱️ 1. 20-Second LAN Matchmaking Countdown & Mutual Ready Check, 🎨 2. Cross-Platform UI Consistency (Mobile & Desktop), 📦 Distribution Packages, 🎮 GoQuiz Adventure v1.2.2 — Release Notes, 🚀 Key Features & Highlights, 🌟 Overview

### Community 44 - "🎮 GoQuiz Adventure v1.3.0-b1 (Beta 1) — Release Notes"
Cohesion: 0.40
Nodes (4): 📦 Distribution Packages, 🎮 GoQuiz Adventure v1.3.0-b1 (Beta 1) — Release Notes, 🔐 Master Secret Trigger Summary, 🌟 Overview

### Community 45 - "scripts"
Cohesion: 0.40
Nodes (5): scripts, build, dev, start, typecheck

### Community 48 - "FramingMode"
Cohesion: 0.50
Nodes (4): FramingMode, FIT_LETTERBOX, FOLLOW_STORY, FREE_PAN

### Community 50 - "DesktopCameraMode"
Cohesion: 0.50
Nodes (4): DesktopCameraMode, FIT_LETTERBOX, FOLLOW_STORY, FREE_PAN

## Knowledge Gaps
- **25 isolated node(s):** `LOGIN`, `REGISTER`, `HOME`, `DIFFICULTY`, `LANGUAGE` (+20 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 253 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **30 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `.transitionToScreenWithFade`, `.createStyledTextView`, `.dispatchKeyEvent`, `.showQuestionIntroCinematic`, `QuizServer`, `MainActivity.java`, `.showLiveDuelLobbyScreen`, `.showInteractiveTutorialDialog`, `FramingMode`, `.showDatabaseInspector`, `.handleDuelMessage`, `Override`, `Screen`, `InteractiveContainerLayout`, `.onKeyDown`, `.resetSecretComboState`?**
  _High betweenness centrality (0.377) - this node is a cross-community bridge._
- **What connects `LOGIN`, `REGISTER`, `HOME` to the rest of the system?**
  _25 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `MainActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.07315233785822021 - nodes in this community are weakly interconnected._
- **Why does `QuizGame` connect `QuizGame` to `Override`, `.getHeartsIcon`, `SimpleJson`, `.showLiveDuelLobby`, `.executeOnlineLeaderboardFetch`, `QuizServer`, `.showDatabaseInspector`, `QuizGame.java`, `.handleDuelMessage`, `BackgroundPanel`, `.checkAnswer`, `DesktopCameraMode`, `FantasyButton`?**
  _High betweenness centrality (0.330) - this node is a cross-community bridge._
- **Should `Override` be split into smaller, more focused modules?**
  _Cohesion score 0.0596078431372549 - nodes in this community are weakly interconnected._
- **Why does `QuizServer` connect `QuizServer` to `MainActivity`, `MainActivity.java`, `QuizGame`, `QuizGame.java`?**
  _High betweenness centrality (0.281) - this node is a cross-community bridge._
- **Should `QuizGame` be split into smaller, more focused modules?**
  _Cohesion score 0.08019323671497584 - nodes in this community are weakly interconnected._