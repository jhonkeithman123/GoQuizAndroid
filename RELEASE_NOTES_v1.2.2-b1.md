# 🎮 GoQuiz Adventure v1.2.2-b1 (Beta 1) — Release Notes

> **Tag:** `v1.2.2-b1` • **Build:** `7` • **Channel:** `Beta` • **Release Date:** October 2026  
> **Platforms:** Android (Mobile/Tablet) & Windows (Desktop/PC)

---

## 🌟 Overview

**GoQuiz Adventure v1.2.2-b1** adapts the **v1.2.2** release baseline and brings **synchronized 20-second matchmaking countdowns and mutual ready checks** to 1v1 PvP duels. Whether playing across the world on the **Cloud Gateway** or locally over **Wi-Fi / Hotspot LAN**, both participants must actively confirm readiness before the match launches.

This release also establishes **visual and functional UI consistency** between Android and Desktop clients, sporting matching versus podiums, avatars, dynamic countdown timers, and automated room garbage collection.

---

## 🚀 Key Features & Highlights

### ⏱️ 1. 20-Second Matchmaking Countdown & Mutual Ready Check
* **Interactive Ready Check Dialog:** Replaces instantaneous match launching with a deliberate mutual handshake.
* **Synchronized Watchdog Timers:**
  * **20-Second Countdown:** Live timer counts down from 20 seconds. Turns red at $\le 5$ seconds to warn players of imminent cancellation.
  * **Server-Side Watchdog:** Both Cloud Gateway (`server.ts`) and embedded LAN server (`QuizServer.java`) run a 20.5-second watchdog. If either player fails to confirm, the match is cancelled and the room is removed.
* **Opponent Ready Telemetry:** Instantaneous status relay (`OPPONENT_READY`) updates the opponent's podium badge to `✓ READY!` as soon as they click ready.
* **Match Start Guarantee:** `MATCH_START` is exclusively dispatched once both players have confirmed readiness.

### 🎨 2. Cross-Platform UI Consistency (Mobile & Desktop)
* **Unified Visual Presentation:**
  * **Fantasy Header:** `⚔️ MATCH FOUND! ACCEPT DUEL ⚔️` with gold filigree borders.
  * **Match Details Badge:** Displays Quiz Category, Difficulty level, and Room Number.
  * **Left Podium (You):** Player name, character avatar (Boy / Girl), and readiness status (`⏳ NOT READY` $\rightarrow$ `✓ READY!`).
  * **Center:** `⚡ VS ⚡` clashing crest.
  * **Right Podium (Opponent):** Opponent name, avatar, and live readiness status (`⏳ WAITING...` $\rightarrow$ `✓ READY!`).
  * **Action Controls:** `⚔️ I AM READY!` green confirmation button and `✕ DECLINE` red cancellation button.
* **Decline & Timeout Handling:** If either player declines or fails to ready up, both clients gracefully dismiss the dialog and display a notification before returning to the lobby.

### 🌐 3. Full Dual-Network Parity (Global Cloud & LAN)
* Operates seamlessly across both network transports:
  * **Global Cloud Gateway:** REST event relay (`/api/room/join`, `/api/room/:code/action`, `/api/room/:code/poll`) with anti-sleep keep-alives.
  * **Local LAN Server:** Low-latency TCP socket relay on port 5050 and 5052.

---

## 📦 Distribution Packages

* **Android Package:** `dist/GoQuiz-v1.2.2-b1.apk` (and `app-debug.apk`)
* **Desktop Portable Bundle:** `dist/GoQuiz-v1.2.2-b1-Windows.zip` (Zero-dependency portable bundle with embedded Java 17 runtime)
* **Desktop Standalone Executable:** `dist/GoQuiz-v1.2.2-b1.exe`
* **Cross-Platform JAR:** `dist/GoQuiz-v1.2.2-b1.jar`
