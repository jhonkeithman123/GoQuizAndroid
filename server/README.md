# GoQuiz Adventure — Global Multiplayer Cloud Server

A lightweight, real-time 1v1 PvP duel gateway, room matchmaker, and global leaderboard server built with **Node.js, Express, WebSockets, Bun, and Supabase PostgreSQL**. Designed for instant deployment on **Render.com** (Free Tier).

---

## ⚡ Tech Stack

- **Runtime & Package Manager:** [Bun](https://bun.sh) (v1.4+)
- **HTTP Web Framework:** [Express](https://expressjs.com)
- **Real-Time Duels:** RFC 6455 WebSockets (`ws`)
- **Database & Persistence:** [Supabase](https://supabase.com) (PostgreSQL) with automatic local JSON fallback
- **Cloud Hosting:** [Render](https://render.com) Web Service (Free Tier, Port 443 / 10000)

---

## 🚀 Quick Start (Local Development)

### 1. Install Dependencies
```bash
bun install
```

### 2. Configure Environment (Optional)
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Fill in your `SUPABASE_URL` and `SUPABASE_KEY` if you want cloud persistence. If left empty, the server automatically uses local disk caching in `data/leaderboard.json`.

### 3. Run the Server
```bash
# Start with Bun
bun run start

# Or with live reload
bun run dev
```

Visit the live status dashboard at: [**http://localhost:5050**](http://localhost:5050)

---

## 🗄️ Supabase Setup (30 Seconds)

1. Go to your [Supabase Dashboard](https://supabase.com/dashboard) and create a free project.
2. Open the **SQL Editor** tab.
3. Open [`server/schema.sql`](./schema.sql), copy its contents, and click **Run**.
4. Go to **Project Settings -> API** and copy:
   - **Project URL** (`SUPABASE_URL`)
   - **anon public** or **service_role secret** key (`SUPABASE_KEY`)
5. Paste them into your `.env` file or Render Environment Variables.

---

## ☁️ Deploying to Render (Free Tier)

### Option A: Automatic via Render Blueprint
1. Push your repository to GitHub.
2. Log into [Render.com](https://render.com).
3. Click **New +** -> **Blueprint**.
4. Select your GitHub repository. Render will automatically read `render.yaml` and configure:
   - **Root Directory:** `server`
   - **Build Command:** `bun install`
   - **Start Command:** `bun run server.js`
5. When prompted for `SUPABASE_URL` and `SUPABASE_KEY`, paste your Supabase credentials.
6. Click **Apply**. Your server will be live at `https://<your-app>.onrender.com`!

### Option B: Manual Web Service Setup
1. On Render, click **New +** -> **Web Service**.
2. Connect your GitHub repository.
3. Configure the settings:
   - **Name:** `goquiz-server`
   - **Language:** `Node`
   - **Root Directory:** `server`
   - **Build Command:** `bun install`
   - **Start Command:** `bun run server.js`
   - **Instance Type:** `Free`
4. Under **Environment Variables**, add:
   - `PORT`: `10000`
   - `SUPABASE_URL`: `https://your-project.supabase.co`
   - `SUPABASE_KEY`: `your-supabase-key`
5. Click **Create Web Service**.

---

## 📡 API & WebSocket Specification

### REST Endpoints
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/` | Web Status Dashboard (Live players, rooms, top 15 leaderboard, WS & Keep-Alive testers) |
| `GET` | `/api/keep-alive` | **Anti-Sleep Cron Ping** (Prevents Render free tier from sleeping; returns JSON status) |
| `GET` | `/ping` | Alias for `/api/keep-alive` |
| `GET` | `/health` | Cloud health check (uptime, rooms count, players count, keep-alive stats) |
| `GET` | `/api/leaderboard` | Global leaderboard (JSON or `?format=raw` pipe-delimited) |
| `POST` | `/api/score` | Update player high score (`{ "name": "Alice", "score": 100 }`) |
| `GET` | `/api/rooms` | List active open rooms waiting for challengers |
| `GET` | `/api/stats` | Server metrics and memory usage |

---

## ⏰ How to Prevent Render Free-Tier From Sleeping

Render's free web services automatically spin down after 15 minutes of inactivity. To keep your GoQuiz server awake **24/7 with zero downtime**:

### Option 1: Free External Cron Job (Recommended)
1. Sign up for a free account at [**cron-job.org**](https://cron-job.org) or [**uptimerobot.com**](https://uptimerobot.com).
2. Create a new cron job / monitor:
   - **URL:** `https://<your-app-name>.onrender.com/api/keep-alive`
   - **Schedule:** Every **10 minutes** (or 5 minutes)
   - **Method:** `GET`
3. That's it! Your server will receive a keep-alive ping every 10 minutes and never spin down.

### Option 2: Automated Self-Ping
Render automatically sets `RENDER_EXTERNAL_URL` in its environment. When detected, the server automatically pings its own `/api/keep-alive` endpoint every 10 minutes in the background!
You can also manually set `SELF_PING_URL=https://<your-app-name>.onrender.com` in your Render Environment Variables.

---

### Real-Time WebSocket Protocol
Clients connect to `wss://<your-render-url>` and send line-delimited commands:
- `HELLO|<name>|<gender>` $\rightarrow$ `OK`
- `ROOM_CREATE|<name>|<gender>|<lang>|<diff>|<payload>` $\rightarrow$ `ROOM_CREATED|<code>|<lang>|<diff>`
- `ROOM_LIST` $\rightarrow$ `ROOM_LIST_RESP|<code>|<hostName>|...`
- `ROOM_JOIN|<code>|<guestName>|<guestGender>` $\rightarrow$ `MATCH_START|...`
- `DUEL_ACTION|<code>|<sender>|<qIndex>|<isCorrect>|<hp>|<maxHp>|<score>|<combo>` $\rightarrow$ `OPPONENT_UPDATE|...`
- `DUEL_FINISH|<code>|<sender>|<score>|<hp>` $\rightarrow$ `OPPONENT_FINISH|...`
- `PING` $\rightarrow$ `PONG`

