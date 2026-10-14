# 🎮 GoQuiz Adventure v1.2.1-b2 (Beta 2) — Release Notes

> **Tag:** `v1.2.1-b2` • **Build:** `6` • **Channel:** `Beta` • **Release Date:** October 2026  
> **Platforms:** Android (Mobile/Tablet) & Windows (Desktop/PC)

---

## 🌟 Overview

**GoQuiz Adventure v1.2.1-b2** is the second feature update in the Beta series. It expands the 1v1 PvP duel system with **dual-network scope controls**, allowing players to host and join duels either **globally across the internet via Cloud Gateway** or **offline locally over Wi-Fi / mobile hotspot LAN**.

This release also introduces **sub-tab lobby filtering**, a **prominent visual network boundary**, **instant local server switching**, **high-resolution telemetry loggers**, and comprehensive stability hardening across both Android and Desktop clients.

---

## 🚀 Key Features & Highlights

### 🌐 1. Host Scope Selector (`GLOBAL` vs `LAN`)
* **Scope Selector Buttons:** When creating a duel match, hosts can choose between:
  * `[ 🌐 GLOBAL (CLOUD) ]`: Publishes the room to the Cloud Gateway relay server. Generates a 4-letter room code (e.g. `#WDVM`, `#B35H`) playable with opponents anywhere in the world.
  * `[ 🏠 LOCAL LAN ]`: Hosts directly on the local network (port 5050). Generates a 4-digit code (e.g. `#4012`) for high-speed, zero-internet dueling on the same Wi-Fi or mobile hotspot.
* **Dynamic Scope Hints:** Instantly explains network requirements and opponent reach based on the selected mode.

### 🔍 2. Dual-Network Room Scanner & Sub-Tab Filters
* **Concurrent Room Discovery:** The duel lobby simultaneously scans the Cloud Gateway REST API (`/api/rooms?format=raw`) and local Wi-Fi / ARP peers in the background.
* **Sub-Tab Lobby Filters:** Filter open rooms with dedicated toggle tabs:
  * `[ ALL ROOMS ]`: Displays both cloud and local rooms with an explicit divider.
  * `[ 🌐 GLOBAL (CLOUD) ]`: Displays only worldwide internet rooms.
  * `[ 🏠 LOCAL LAN ]`: Displays only local Wi-Fi / hotspot rooms.
* **Prominent Visual Network Barrier:**
  ```
  ⚡ NETWORK BARRIER: GLOBAL CLOUD ⟷ LOCAL LAN ⚡
  Top: Worldwide Internet Rooms  •  Bottom: Same Wi-Fi / Hotspot Rooms
  ```
* **Intelligent Code Routing:** The Quick Join box automatically routes 4-letter alphanumeric codes to the Global Cloud and 4-digit numeric codes to the local LAN server.

### ⚡ 3. 1-Click Server Switching & Latency Diagnostics (Desktop & Android)
* **Local Test Server Preset:** In the Server Configuration tab, switch between `[ RENDER CLOUD (PROD) ]` and `[ LOCAL TEST (PORT 5050) ]` with a single click.
* **Test Connection Diagnostic (`⚡`):** Pings the target server (`/api/keep-alive`) and immediately displays the exact round-trip ping latency and wakefulness status.

### 🛠️ 4. Server Telemetry & High-Resolution Logging (`server/src/server.ts`)
* **HTTP Latency Middleware:** Logs every incoming API request with method, path, HTTP status code, client IP, and duration in milliseconds (e.g. `[HTTP GET] /api/rooms?format=raw -> 200 (2.8ms)`).
* **TCP Socket Message Tracking (Port 5052):** Real-time monitoring of raw socket handshakes, room creations, leaderboard queries, and pings.
* **Automated Test Suite (`server/test-local-server.js`):** End-to-end verification script testing all 8 REST and TCP socket commands locally in <50ms.
* **Type-Safe Routing:** Fixed TypeScript parameter handling (`TS2339`) on `req.params.code`.
* **Graceful JSON Parsing:** Prevents body-parser crashes on malformed requests.

### ⚔️ 5. Global Cloud Dueling Fix (Real-Time REST Event Relay)
* **Resolved Incomplete Join Bug:** Fixed an issue where joining a global room displayed *"ROOM # is active on Cloud Gateway connecting player to match..."* but did not transition the host or guest into the game.
* **Zero-Dependency Event Relay:** Built an HTTPS-compatible long-poll event relay on the Cloud Gateway (`/api/room/join`, `/api/room/:code/action`, `/api/room/:code/poll`, `/api/room/:code/leave`) that operates through firewalls, NAT, mobile networks, and Render proxies without raw TCP port dependencies.
* **Synchronized Live Duel Launch:** Both Desktop and Android `DuelClient` engines poll the relay at sub-millisecond latencies. As soon as the challenger joins, `MATCH_START` triggers automatically on both devices, synchronizing questions, live HP, and scores identically to local LAN play.

---

## 📦 Downloads & Release Artifacts

| Artifact | Size | Platform | Description |
| :--- | :--- | :--- | :--- |
| **`GoQuiz-v1.2.1-b2-debug.apk`** | 180.7 MB | Android 7.0+ (Phones & Tablets) | Native Android beta APK with global/LAN duel lobby and live room scanner. |
| **`GoQuiz-v1.2.1-b2-Windows.zip`** | 154.9 MB | Windows 10 / 11 (64-bit) | **Recommended for PC.** Zero-setup bundle with bundled portable Java 17 runtime. |
| **`GoQuiz-v1.2.1-b2.exe`** | 322 KB | Windows 10 / 11 (64-bit) | Standalone native Windows launcher with embedded game JAR. |
| **`GoQuiz-v1.2.1-b2.jar`** | 305 KB | Windows / macOS / Linux | Cross-platform runnable JAR package (requires Java 17+). |

---

## 🕹️ Quick Start Guide

### Playing Online Duels (Global Cloud):
1. Launch **GoQuiz Adventure** $\rightarrow$ Click **1v1 LIVE DUEL**.
2. Under the **HOST A DUEL** tab, select **`[ 🌐 GLOBAL (CLOUD) ]`**.
3. Choose your topic (HTML, CSS, JavaScript, Java) and difficulty, then click **HOST ROOM NOW ⚔️**.
4. Share the generated **4-letter code** with your opponent anywhere in the world.
5. The opponent opens **1v1 LIVE DUEL**, enters the code in **ENTER ROOM CODE**, and clicks **JOIN ➔**.

### Playing Offline Duels (Local Wi-Fi / Hotspot):
1. Connect both devices to the same Wi-Fi or mobile hotspot.
2. Select **`[ 🏠 LOCAL LAN ]`** and host the room.
3. The other player will see the room appear under **🏠 LOCAL LAN ROOMS** or can enter the 4-digit code directly.
