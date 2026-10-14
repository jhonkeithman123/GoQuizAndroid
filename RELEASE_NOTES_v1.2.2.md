# 🎮 GoQuiz Adventure v1.2.2 — Release Notes

> **Tag:** `v1.2.2` • **Build:** `5` • **Channel:** `Production Release` • **Release Date:** October 2026  
> **Platforms:** Android (Mobile/Tablet) & Windows (Desktop/PC)

---

## 🌟 Overview

**GoQuiz Adventure v1.2.2** is a quality-of-life and stability update for the official production release line. It introduces **synchronized 20-second matchmaking countdowns and mutual ready checks** to 1v1 PvP LAN duels, ensuring both players actively accept and confirm readiness before a match begins.

This release also establishes **seamless UI consistency** between Android and Desktop clients, with identical versus podiums, avatars, dynamic countdown timers, and automated room garbage collection.

---

## 🚀 Key Features & Highlights

### ⏱️ 1. 20-Second LAN Matchmaking Countdown & Mutual Ready Check
* **Interactive Ready Check Dialog:** Replaces instantaneous match launching with a deliberate mutual handshake.
* **Synchronized Watchdog Timers:**
  * **20-Second Countdown:** Live timer counts down from 20 seconds. Turns red at $\le 5$ seconds to warn players of imminent cancellation.
  * **Server-Side Watchdog:** The embedded LAN server (`QuizServer.java`) runs a 20.5-second watchdog. If either player fails to confirm, the match is cancelled and the room is removed.
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

---

## 📦 Distribution Packages

All build artifacts for this release are organized under `dist/v1.2.2/`:

* **Android Package:** `dist/v1.2.2/GoQuiz-v1.2.2.apk`
* **Desktop Portable Bundle:** `dist/v1.2.2/GoQuiz-v1.2.2-Windows.zip` (Zero-dependency portable bundle with embedded Java 17 runtime)
* **Desktop Standalone Executable:** `dist/v1.2.2/GoQuiz-v1.2.2.exe`
* **Cross-Platform JAR:** `dist/v1.2.2/GoQuiz-v1.2.2.jar`
