import "dotenv/config";
import express, { type Request, type Response } from "express";
import cors from "cors";
import { createServer, type Server as HttpServer } from "http";
import { WebSocketServer, WebSocket, type RawData } from "ws";
import net, { type Socket as TcpSocket, type Server as TcpServer } from "net";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import type {
  LeaderboardEntry,
  SortedLeaderboardItem,
  DbLeaderboardRow,
  DbMatchHistoryRow,
  SessionTransport,
  IClientSession,
  IDuelRoom,
  RoomSummary,
  KeepAliveResponse,
} from "./types.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, "..");

// ==========================================
// CONFIGURATION & ENVIRONMENT
// ==========================================
const HTTP_PORT: number = parseInt(process.env.PORT || "5050", 10);
const TCP_PORT: number = parseInt(process.env.TCP_PORT || "5052", 10);
const DATA_DIR: string = path.join(ROOT_DIR, "data");
const LEADERBOARD_JSON: string = path.join(DATA_DIR, "leaderboard.json");
const PROP_LEADERBOARD: string = path.join(
  ROOT_DIR,
  "online_leaderboard.properties",
);
const ROOT_PROP_LEADERBOARD: string = path.join(
  ROOT_DIR,
  "..",
  "online_leaderboard.properties",
);

if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// ==========================================
// SUPABASE CLIENT INITIALIZATION
// ==========================================
const SUPABASE_URL: string = process.env.SUPABASE_URL || "";
const SUPABASE_KEY: string =
  process.env.SUPABASE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY || "";
let supabase: SupabaseClient | null = null;

if (SUPABASE_URL && SUPABASE_KEY) {
  try {
    supabase = createClient(SUPABASE_URL, SUPABASE_KEY);
    console.log(`[Database] Connected to Supabase Cloud: ${SUPABASE_URL}`);
  } catch (err: any) {
    console.warn(
      "[Database] Failed to initialize Supabase client:",
      err.message,
    );
  }
} else {
  console.log(
    "[Database] Supabase credentials not provided. Using local file storage fallback.",
  );
}

// ==========================================
// PERSISTENT LEADERBOARD (Supabase + Local Cache)
// ==========================================
const leaderboard: Map<string, LeaderboardEntry> = new Map();

async function loadLeaderboard(): Promise<void> {
  // 1. Try loading from Supabase first
  if (supabase) {
    try {
      const { data, error } = await supabase
        .from("leaderboard")
        .select("player_name, score, gender, updated_at")
        .order("score", { ascending: false });

      if (!error && Array.isArray(data)) {
        leaderboard.clear();
        for (const row of data as DbLeaderboardRow[]) {
          leaderboard.set(row.player_name, {
            score: Math.max(0, row.score || 0),
            gender: row.gender || "Unknown",
            updatedAt: row.updated_at || new Date().toISOString(),
          });
        }
        console.log(
          `[Leaderboard] Synced ${leaderboard.size} entries from Supabase PostgreSQL`,
        );
        saveLocalLeaderboard();
        return;
      } else if (error) {
        console.warn("[Leaderboard] Supabase fetch error:", error.message);
      }
    } catch (err: any) {
      console.warn(
        "[Leaderboard] Supabase load failed, falling back to local:",
        err.message,
      );
    }
  }

  // 2. Fallback to local JSON
  try {
    if (fs.existsSync(LEADERBOARD_JSON)) {
      const raw = fs.readFileSync(LEADERBOARD_JSON, "utf-8");
      const data: Record<string, any> = JSON.parse(raw);
      for (const [name, val] of Object.entries(data)) {
        const score =
          typeof val === "object" && val !== null ? val.score : Number(val);
        const gender =
          typeof val === "object" && val !== null
            ? val.gender || "Unknown"
            : "Unknown";
        leaderboard.set(name, {
          score: Math.max(0, score || 0),
          gender,
          updatedAt: new Date().toISOString(),
        });
      }
      console.log(
        `[Leaderboard] Loaded ${leaderboard.size} entries from local JSON`,
      );
      return;
    }
  } catch (err: any) {
    console.warn(
      "[Leaderboard] Failed to parse JSON leaderboard:",
      err.message,
    );
  }

  // 3. Fallback to legacy properties file
  const propFile = fs.existsSync(PROP_LEADERBOARD)
    ? PROP_LEADERBOARD
    : fs.existsSync(ROOT_PROP_LEADERBOARD)
      ? ROOT_PROP_LEADERBOARD
      : null;

  if (propFile) {
    try {
      const content = fs.readFileSync(propFile, "utf-8");
      content.split(/\r?\n/).forEach((line: string) => {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith("#")) return;
        const eqIdx = trimmed.indexOf("=");
        if (eqIdx > 0) {
          const name = trimmed.substring(0, eqIdx).trim();
          const score = parseInt(trimmed.substring(eqIdx + 1).trim(), 10) || 0;
          if (name) {
            leaderboard.set(name, {
              score: Math.max(0, score),
              gender: "Unknown",
              updatedAt: new Date().toISOString(),
            });
          }
        }
      });
      console.log(
        `[Leaderboard] Imported ${leaderboard.size} entries from properties file: ${propFile}`,
      );
      saveLocalLeaderboard();
    } catch (err: any) {
      console.warn(
        "[Leaderboard] Failed to read properties file:",
        err.message,
      );
    }
  }
}

function saveLocalLeaderboard(): void {
  try {
    const obj: Record<string, LeaderboardEntry> = {};
    for (const [name, data] of leaderboard.entries()) {
      obj[name] = data;
    }
    fs.writeFileSync(LEADERBOARD_JSON, JSON.stringify(obj, null, 2), "utf-8");

    // Also update legacy properties file
    let propContent =
      "# GoQuiz Shared Online Leaderboard\n# Updated " +
      new Date().toISOString() +
      "\n";
    for (const [name, data] of leaderboard.entries()) {
      propContent += `${name}=${data.score}\n`;
    }
    fs.writeFileSync(PROP_LEADERBOARD, propContent, "utf-8");
  } catch (err: any) {
    console.error("[Leaderboard] Local save error:", err.message);
  }
}

async function updateScore(
  name: string,
  score: number | string,
  gender: string = "Unknown",
): Promise<void> {
  if (!name || typeof name !== "string") return;
  const cleanName = clean(name).trim();
  if (!cleanName) return;
  const newScore = Math.max(
    0,
    typeof score === "number" ? score : parseInt(score, 10) || 0,
  );
  const current = leaderboard.get(cleanName);

  if (!current || newScore > current.score) {
    const entry: LeaderboardEntry = {
      score: newScore,
      gender:
        gender && gender !== "Unknown" ? gender : current?.gender || "Unknown",
      updatedAt: new Date().toISOString(),
    };
    leaderboard.set(cleanName, entry);
    saveLocalLeaderboard();

    // Async sync to Supabase Cloud
    if (supabase) {
      try {
        const row: DbLeaderboardRow = {
          player_name: cleanName,
          score: newScore,
          gender: entry.gender,
          updated_at: entry.updatedAt,
        };
        const { error } = await supabase
          .from("leaderboard")
          .upsert(row, { onConflict: "player_name" });

        if (error) {
          console.warn(
            `[Supabase] Leaderboard upsert error for ${cleanName}:`,
            error.message,
          );
        } else {
          console.log(
            `[Supabase] Synced high score for ${cleanName}: ${newScore} pts`,
          );
        }
      } catch (err: any) {
        console.warn(`[Supabase] Sync failed:`, err.message);
      }
    }
  }
}

async function recordMatchHistory(
  room: IDuelRoom,
  hostScore: number,
  guestScore: number,
  winnerName: string,
): Promise<void> {
  if (!supabase || !room) return;
  try {
    const row: DbMatchHistoryRow = {
      room_code: room.code,
      host_name: room.hostName,
      guest_name: room.guestName,
      winner_name: winnerName,
      host_score: hostScore,
      guest_score: guestScore,
      language: room.language,
      difficulty: room.difficulty,
      finished_at: new Date().toISOString(),
    };
    const { error } = await supabase.from("match_history").insert(row);
    if (error)
      console.warn("[Supabase] Match history log error:", error.message);
  } catch (err: any) {
    console.warn("[Supabase] Match history log failed:", err.message);
  }
}

function getSortedLeaderboard(): SortedLeaderboardItem[] {
  return Array.from(leaderboard.entries())
    .map(([name, data]) => ({ name, score: data.score, gender: data.gender }))
    .sort((a, b) => b.score - a.score || a.name.localeCompare(b.name));
}

function getLeaderboardMessage(): string {
  const sorted = getSortedLeaderboard();
  let msg = "BOARD";
  for (const entry of sorted) {
    msg += `|${clean(entry.name)}|${entry.score}`;
    if (msg.length > 7000) break;
  }
  return msg;
}

function clean(str: string | null | undefined): string {
  if (!str) return "";
  return String(str).replace(/[|\r\n]/g, " ");
}

// Initial load
loadLeaderboard();

// ==========================================
// LIVE ROOM & SESSION MANAGEMENT
// ==========================================
class DuelRoom implements IDuelRoom {
  code: string;
  hostName: string;
  hostGender: string;
  language: string;
  difficulty: string;
  seed: number;
  questionsPayload: string = "";
  hostSession: IClientSession | null;
  guestSession: IClientSession | null = null;
  guestName: string = "";
  guestGender: string = "";
  hostScore: number = 0;
  guestScore: number = 0;
  active: boolean = false;
  finished: boolean = false;
  createdAt: number = Date.now();
  hostClientId: string = "";
  guestClientId: string = "";

  // Cloud REST Relay Queues & Listeners
  hostEvents: string[] = [];
  guestEvents: string[] = [];
  hostPollResolver: ((events: string[]) => void) | null = null;
  guestPollResolver: ((events: string[]) => void) | null = null;
  lastHostPing: number = Date.now();
  lastGuestPing: number = Date.now();

  constructor(
    code: string,
    hostName: string,
    hostGender: string,
    language: string,
    difficulty: string,
    hostSession: IClientSession | null,
  ) {
    this.code = code;
    this.hostName = hostName;
    this.hostGender = hostGender;
    this.language = language;
    this.difficulty = difficulty;
    this.seed = Date.now();
    this.hostSession = hostSession;
  }

  pushEvent(role: "host" | "guest", event: string): void {
    if (role === "host") {
      if (this.hostSession) {
        this.hostSession.send(event);
      }
      if (this.hostPollResolver) {
        const resolve = this.hostPollResolver;
        this.hostPollResolver = null;
        resolve([event]);
      } else {
        this.hostEvents.push(event);
      }
    } else {
      if (this.guestSession) {
        this.guestSession.send(event);
      }
      if (this.guestPollResolver) {
        const resolve = this.guestPollResolver;
        this.guestPollResolver = null;
        resolve([event]);
      } else {
        this.guestEvents.push(event);
      }
    }
  }
}

const rooms: Map<string, IDuelRoom> = new Map();
const activeSessions: Set<IClientSession> = new Set();
let totalDuelsCompleted: number = 0;

function generateRoomCode(): string {
  const chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  let code = "";
  let attempts = 0;
  do {
    code = "";
    for (let i = 0; i < 4; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    attempts++;
  } while (rooms.has(code) && attempts < 100);

  if (rooms.has(code)) {
    code = String(1000 + Math.floor(Math.random() * 9000));
  }
  return code;
}

function getConnectedClientsMessage(): string {
  const list = Array.from(activeSessions);
  let res = `CLIENT_LIST|${list.length}`;
  for (const s of list) {
    res += `|${clean(s.remoteAddress)}|${clean(s.playerName || "Guest")}|${clean(s.playerGender || "Unknown")}|${clean(s.getStatus())}`;
  }
  return res;
}

// ==========================================
// UNIFIED CLIENT SESSION (WebSocket or TCP)
// ==========================================
class ClientSession implements IClientSession {
  type: SessionTransport;
  socket: WebSocket | TcpSocket;
  remoteAddress: string;
  playerName: string = "";
  playerGender: string = "";
  currentRoom: IDuelRoom | null = null;
  connectedAt: number = Date.now();
  lastPing: number = Date.now();

  constructor(
    type: SessionTransport,
    socket: WebSocket | TcpSocket,
    remoteAddress: string,
  ) {
    this.type = type;
    this.socket = socket;
    this.remoteAddress = remoteAddress || "Unknown";
  }

  getStatus(): string {
    if (this.currentRoom) {
      if (this === this.currentRoom.hostSession) {
        return this.currentRoom.active
          ? `In Duel #${this.currentRoom.code}`
          : `Hosting Room #${this.currentRoom.code}`;
      } else {
        return `In Duel #${this.currentRoom.code}`;
      }
    }
    return "Connected in Lobby";
  }

  send(msg: string): void {
    try {
      if (this.type === "ws") {
        const ws = this.socket as WebSocket;
        if (ws && ws.readyState === WebSocket.OPEN) {
          ws.send(msg);
        }
      } else if (this.type === "tcp") {
        const tcp = this.socket as TcpSocket;
        if (tcp && !tcp.destroyed) {
          tcp.write(msg + "\n");
        }
      }
      const isProbe =
        this.remoteAddress.startsWith("::1") ||
        this.remoteAddress.startsWith("127.0.0.1");
      if (!isProbe || (msg !== "PONG" && msg !== "OK")) {
        const preview = msg.length > 70 ? msg.slice(0, 70) + "..." : msg;
        console.log(
          `[${this.type.toUpperCase()} ${this.remoteAddress}] ⬅ SENT: ${preview}`,
        );
      }
    } catch (err: any) {
      console.warn(`[Session ${this.remoteAddress}] Send error:`, err.message);
    }
  }

  close(): void {
    try {
      if (this.type === "ws") (this.socket as WebSocket).close();
      if (this.type === "tcp") (this.socket as TcpSocket).destroy();
    } catch (_) {}
  }

  cleanup(): void {
    activeSessions.delete(this);
    if (this.currentRoom) {
      const room = this.currentRoom;
      this.currentRoom = null;
      if (!room.finished) {
        room.finished = true;
        const opponent =
          this === room.hostSession ? room.guestSession : room.hostSession;
        const leftName =
          this === room.hostSession ? room.hostName : room.guestName;
        if (opponent) {
          opponent.send(`OPPONENT_LEFT|${clean(leftName)}`);
        }
      }
      rooms.delete(room.code);
    }
  }

  handleMessage(line: string): void {
    this.lastPing = Date.now();
    const raw = (line || "").trim();
    if (!raw) return;

    const p = raw.split("|");
    const cmd = p[0];

    const isProbe =
      this.remoteAddress.startsWith("::1") ||
      this.remoteAddress.startsWith("127.0.0.1");
    if (!isProbe || cmd !== "PING") {
      const preview = raw.length > 70 ? raw.slice(0, 70) + "..." : raw;
      console.log(
        `[${this.type.toUpperCase()} ${this.remoteAddress}] ➔ RECV [${cmd}]: ${preview}`,
      );
    }

    switch (cmd) {
      case "HELLO": {
        if (p.length > 1) this.playerName = clean(p[1]);
        if (p.length > 2) this.playerGender = clean(p[2]);
        this.send("OK");
        break;
      }

      case "CLIENT_LIST": {
        this.send(getConnectedClientsMessage());
        break;
      }

      case "SCORE": {
        if (p.length < 3) {
          this.send("ERROR|Invalid SCORE command.");
          return;
        }
        updateScore(p[1], p[2], this.playerGender);
        this.send("OK");
        break;
      }

      case "LEADERBOARD":
      case "BOARD": {
        this.send(getLeaderboardMessage());
        break;
      }

      case "INFO": {
        const dbInfo = supabase ? "Supabase" : "Local";
        this.send(`INFO|GoQuiz-RenderCloud|${dbInfo}|${rooms.size}`);
        break;
      }

      case "PING": {
        this.send("PONG");
        break;
      }

      case "ROOM_CREATE": {
        const hostName = p.length > 1 ? clean(p[1]) : "Host";
        const hostGender = p.length > 2 ? clean(p[2]) : "Boy";
        const language = p.length > 3 ? clean(p[3]) : "HTML";
        const difficulty = p.length > 4 ? clean(p[4]) : "Easy";
        const questionsPayload = p.length > 5 ? p[5] : "";
        this.playerName = hostName;
        this.playerGender = hostGender;

        const code = generateRoomCode();
        const room = new DuelRoom(
          code,
          hostName,
          hostGender,
          language,
          difficulty,
          this,
        );
        room.questionsPayload = questionsPayload;
        this.currentRoom = room;
        rooms.set(code, room);

        console.log(
          `[Room] Created Room #${code} by ${hostName} (${language} - ${difficulty})`,
        );
        this.send(`ROOM_CREATED|${code}|${language}|${difficulty}`);
        break;
      }

      case "ROOM_LIST": {
        let resp = "ROOM_LIST_RESP";
        for (const r of rooms.values()) {
          if (!r.active && !r.finished && !r.guestSession) {
            if (r.hostSession === this) continue;
            if (
              this.playerName &&
              this.playerName.toLowerCase() === r.hostName.toLowerCase()
            )
              continue;
            resp += `|${r.code}|${clean(r.hostName)}|${clean(r.hostGender)}|${clean(r.language)}|${clean(r.difficulty)}`;
          }
        }
        this.send(resp);
        break;
      }

      case "ROOM_JOIN": {
        if (p.length < 4) {
          this.send("ERROR|Invalid ROOM_JOIN command.");
          return;
        }
        const code = (p[1] || "").trim().toUpperCase();
        const guestName = clean(p[2]);
        const guestGender = clean(p[3]);
        this.playerName = guestName;
        this.playerGender = guestGender;

        const room = rooms.get(code);
        if (!room || room.finished) {
          this.send(`ERROR|Room ${code} not found or expired.`);
          return;
        }
        if (room.active || room.guestSession) {
          this.send(`ERROR|Room ${code} is already full.`);
          return;
        }
        if (
          room.hostSession === this ||
          (room.hostName &&
            room.hostName.toLowerCase() === guestName.toLowerCase())
        ) {
          this.send("ERROR|Host cannot join their own room.");
          return;
        }

        room.guestSession = this;
        room.guestName = guestName;
        room.guestGender = guestGender;
        room.state = 'ready_check';
        room.hostReady = false;
        room.guestReady = false;
        room.readyCheckExpiresAt = Date.now() + 20000;
        this.currentRoom = room;

        console.log(
          `[Duel Ready Check] Room #${room.code}: ${room.hostName} VS ${room.guestName} (20s timeout)`,
        );

        const checkMsg = `MATCH_READY_CHECK|${room.code}|${room.hostName}|${room.hostGender}|${room.guestName}|${room.guestGender}|${room.language}|${room.difficulty}|${room.seed}|${room.questionsPayload || 'NONE'}|20`;
        if (room.hostSession) room.hostSession.send(checkMsg);
        if (room.guestSession) room.guestSession.send(checkMsg);
        break;
      }

      case "PLAYER_READY": {
        if (this.currentRoom && !this.currentRoom.finished && !this.currentRoom.active) {
          const room = this.currentRoom;
          if (this === room.hostSession) {
            room.hostReady = true;
            if (room.guestSession) room.guestSession.send(`OPPONENT_READY|${room.code}`);
          } else {
            room.guestReady = true;
            if (room.hostSession) room.hostSession.send(`OPPONENT_READY|${room.code}`);
          }

          if (room.hostReady && room.guestReady) {
            room.active = true;
            room.state = 'active';
            const matchStartMsg = `MATCH_START|${room.code}|${room.hostName}|${room.hostGender}|${room.guestName}|${room.guestGender}|${room.language}|${room.difficulty}|${room.seed}${room.questionsPayload ? `|${room.questionsPayload}` : ""}`;
            if (room.hostSession) room.hostSession.send(matchStartMsg);
            if (room.guestSession) room.guestSession.send(matchStartMsg);
          }
        }
        break;
      }

      case "DECLINE_MATCH": {
        if (this.currentRoom && !this.currentRoom.finished && !this.currentRoom.active) {
          const room = this.currentRoom;
          room.finished = true;
          room.state = 'finished';
          const cancelMsg = `MATCH_CANCELLED|${room.code}|Match declined by ${this.playerName || 'opponent'}.`;
          const opponent = (this === room.hostSession) ? room.guestSession : room.hostSession;
          if (opponent) opponent.send(cancelMsg);
          rooms.delete(room.code);
        }
        break;
      }

      case "DUEL_ACTION": {
        if (this.currentRoom && p.length >= 9) {
          const opponent =
            this === this.currentRoom.hostSession
              ? this.currentRoom.guestSession
              : this.currentRoom.hostSession;

          if (opponent) {
            const sender = p[2];
            const qIndex = p[3];
            const isCorrect = p[4];
            const hp = p[5];
            const maxHp = p[6];
            const score = p[7];
            const combo = p[8];

            if (this === this.currentRoom.hostSession) {
              this.currentRoom.hostScore =
                parseInt(score, 10) || this.currentRoom.hostScore;
            } else {
              this.currentRoom.guestScore =
                parseInt(score, 10) || this.currentRoom.guestScore;
            }

            opponent.send(
              `OPPONENT_UPDATE|${sender}|${qIndex}|${isCorrect}|${hp}|${maxHp}|${score}|${combo}`,
            );
          }
        }
        break;
      }

      case "DUEL_FINISH": {
        if (this.currentRoom && p.length >= 5) {
          const opponent =
            this === this.currentRoom.hostSession
              ? this.currentRoom.guestSession
              : this.currentRoom.hostSession;

          const sender = p[2];
          const score = parseInt(p[3], 10) || 0;
          const hp = p[4];

          if (this === this.currentRoom.hostSession) {
            this.currentRoom.hostScore = score;
          } else {
            this.currentRoom.guestScore = score;
          }

          if (opponent) {
            opponent.send(`OPPONENT_FINISH|${sender}|${score}|${hp}`);
          }

          totalDuelsCompleted++;

          // Determine winner and record to Supabase
          const hostS = this.currentRoom.hostScore;
          const guestS = this.currentRoom.guestScore;
          let winner = "Tie";
          if (hostS > guestS) winner = this.currentRoom.hostName;
          else if (guestS > hostS) winner = this.currentRoom.guestName;

          recordMatchHistory(this.currentRoom, hostS, guestS, winner);
        }
        break;
      }

      case "LEAVE_ROOM": {
        this.cleanup();
        this.send("LEFT_ROOM");
        break;
      }

      default:
        this.send("ERROR|Unknown command.");
        break;
    }
  }
}

// Periodic cleanup of stale rooms and 20s ready check watchdog
setInterval(() => {
  const now = Date.now();
  for (const [code, room] of rooms.entries()) {
    // 20-second Ready Check timeout
    if (room.state === 'ready_check' && !room.active && !room.finished) {
      if (room.readyCheckExpiresAt && now > room.readyCheckExpiresAt) {
        room.finished = true;
        room.state = 'finished';
        const cancelMsg = `MATCH_CANCELLED|${room.code}|Match cancelled: One or more players failed to ready up within 20 seconds.`;
        if (room.hostSession) room.hostSession.send(cancelMsg);
        if (room.guestSession) room.guestSession.send(cancelMsg);
        if (room.pushEvent) {
          room.pushEvent('host', cancelMsg);
          room.pushEvent('guest', cancelMsg);
        }
        console.log(`[Duel Watchdog] Room #${code} ready check expired. Room closed.`);
        rooms.delete(code);
        continue;
      }
    }

    if (now - room.createdAt > 20 * 60 * 1000) {
      if (!room.active || room.finished) {
        rooms.delete(code);
      }
    }
  }
}, 1000);

// ==========================================
// EXPRESS APP & REST API
// ==========================================
const app = express();
app.use(cors());
app.use(express.json());

// Graceful JSON syntax error handler
app.use((err: any, req: Request, res: Response, next: any) => {
  if (err instanceof SyntaxError && "body" in err) {
    console.warn(
      `[HTTP 400] Malformed JSON in ${req.method} ${req.url}: ${err.message}`,
    );
    return res.status(400).json({ error: "Malformed JSON payload" });
  }
  next(err);
});

// HTTP Request Logger Middleware
app.use((req: Request, res: Response, next) => {
  const start = Date.now();
  const ip =
    (req.headers["x-forwarded-for"] as string) ||
    req.socket.remoteAddress ||
    "unknown";
  res.on("finish", () => {
    const duration = Date.now() - start;
    const status = res.statusCode;
    const method = req.method;
    const url = req.originalUrl || req.url;
    // Suppress spamming console on automated background self-pings
    if (
      url.startsWith("/api/keep-alive") ||
      url === "/ping" ||
      url === "/health"
    ) {
      return;
    }
    console.log(`[HTTP ${method}] ${url} -> ${status} (${duration}ms) [${ip}]`);
  });
  next();
});

// Anti-Sleep Keep-Alive & Cron Job Endpoints
let lastKeepAlivePing: number = Date.now();
let keepAliveCount: number = 0;

app.get(["/api/keep-alive", "/ping"], (req: Request, res: Response) => {
  lastKeepAlivePing = Date.now();
  keepAliveCount++;
  const clientIp =
    (req.headers["x-forwarded-for"] as string) ||
    req.socket.remoteAddress ||
    "cron";
  console.log(
    `[KeepAlive] Ping received #${keepAliveCount} from ${clientIp} (${new Date().toLocaleTimeString()})`,
  );

  const responsePayload: KeepAliveResponse = {
    status: "awake",
    message:
      "GoQuiz Adventure server is awake and active! Render sleep prevented.",
    pingCount: keepAliveCount,
    uptimeSeconds: Math.floor(process.uptime()),
    database: supabase ? "Supabase PostgreSQL" : "Local Disk Backup",
    activeRooms: rooms.size,
    connectedPlayers: activeSessions.size,
    timestamp: new Date().toISOString(),
  };

  res.status(200).json(responsePayload);
});

// Health Check Endpoint (For Render monitoring & pingers)
app.get("/health", (req: Request, res: Response) => {
  res.json({
    status: "ok",
    service: "GoQuiz Global Multiplayer Gateway",
    database: supabase ? "Supabase PostgreSQL" : "Local Disk Backup",
    uptimeSeconds: Math.floor(process.uptime()),
    activeRooms: rooms.size,
    connectedPlayers: activeSessions.size,
    keepAlivePings: keepAliveCount,
    lastKeepAliveAt: new Date(lastKeepAlivePing).toISOString(),
    totalDuelsCompleted,
    timestamp: new Date().toISOString(),
  });
});

// REST Leaderboard Endpoints
app.get("/api/leaderboard", (req: Request, res: Response) => {
  if (req.query.format === "raw") {
    res.type("text/plain").send(getLeaderboardMessage());
    return;
  }
  const sorted = getSortedLeaderboard();
  res.json({
    total: sorted.length,
    database: supabase ? "Supabase" : "Local",
    leaderboard: sorted.map((entry, index) => ({
      rank: index + 1,
      name: entry.name,
      score: entry.score,
      gender: entry.gender,
    })),
  });
});

app.post("/api/score", async (req: Request, res: Response) => {
  const { name, score, gender } = req.body || {};
  if (!name || score === undefined) {
    res.status(400).json({ error: "Missing name or score" });
    return;
  }
  await updateScore(name, score, gender);
  res.json({
    success: true,
    name,
    score: leaderboard.get(clean(name))?.score || score,
  });
});

// Active Open Rooms Endpoint
app.get("/api/rooms", (req: Request, res: Response) => {
  const format = req.query.format as string;
  console.log(
    `[API] GET /api/rooms (format=${format || "json"}, activeRooms=${rooms.size})`,
  );

  if (format === "raw") {
    let resp = "ROOM_LIST_RESP";
    for (const r of rooms.values()) {
      if (!r.finished && !r.guestSession && !r.guestName && !r.active) {
        resp += `|${r.code}|${clean(r.hostName)}|${clean(r.hostGender)}|${clean(r.language)}|${clean(r.difficulty)}|${clean(r.hostClientId)}`;
      }
    }
    res.type("text/plain").send(resp);
    return;
  }

  const roomList: RoomSummary[] = [];
  for (const r of rooms.values()) {
    if (!r.finished && !r.guestSession && !r.guestName && !r.active) {
      roomList.push({
        code: r.code,
        hostName: r.hostName,
        hostGender: r.hostGender,
        language: r.language,
        difficulty: r.difficulty,
        active: r.active,
        waiting: true,
        createdAt: r.createdAt,
      });
    }
  }
  res.json({ rooms: roomList });
});

// Create Room via REST
app.post("/api/room/create", (req: Request, res: Response) => {
  const { hostName, hostGender, language, difficulty, questionsPayload, hostClientId } =
    req.body || {};
  const code = generateRoomCode();
  const room = new DuelRoom(
    code,
    clean(hostName || "Host"),
    clean(hostGender || "Boy"),
    clean(language || "HTML"),
    clean(difficulty || "Easy"),
    null as any,
  );
  room.questionsPayload = questionsPayload || "";
  room.hostClientId = String(hostClientId || "");
  rooms.set(code, room);
  console.log(
    `[Room REST] Created Room #${code} by ${hostName} (${language} - ${difficulty}) [hostClient: ${room.hostClientId}]`,
  );
  res.json({
    success: true,
    code,
    language: room.language,
    difficulty: room.difficulty,
  });
});

// Lookup Room Details by Code
app.get("/api/room/:code", (req: Request, res: Response) => {
  const paramCode = req.params.code;
  const code = (Array.isArray(paramCode) ? paramCode[0] : paramCode || "")
    .trim()
    .toUpperCase();
  console.log(`[API] GET /api/room/${code}`);
  const room = rooms.get(code);
  if (!room || room.finished) {
    res.status(404).json({ error: `Room ${code} not found or expired` });
    return;
  }
  res.json({
    code: room.code,
    hostName: room.hostName,
    hostGender: room.hostGender,
    language: room.language,
    difficulty: room.difficulty,
    active: room.active,
    waiting: !room.guestSession && !room.guestName,
  });
});

// Join Room via REST (Cloud Relay)
app.post("/api/room/join", (req: Request, res: Response) => {
  const { code, guestName, guestGender, guestClientId } = req.body || {};
  const cleanCode = String(code || "")
    .trim()
    .toUpperCase();
  const cleanGuest = clean(guestName || "Challenger");
  const cleanGender = clean(guestGender || "Boy");
  const room = rooms.get(cleanCode);

  if (!room || room.finished) {
    res.status(404).json({ error: `Room #${cleanCode} not found or expired` });
    return;
  }
  if (room.active || room.guestName) {
    res.status(400).json({ error: `Room #${cleanCode} is already full` });
    return;
  }
  if (guestClientId && room.hostClientId && String(guestClientId) === String(room.hostClientId)) {
    res.status(400).json({ error: `You are hosting Room #${cleanCode}. Waiting for another player to challenge you!` });
    return;
  }

  room.guestName = cleanGuest;
  room.guestGender = cleanGender;
  room.guestClientId = String(guestClientId || "");
  room.state = 'ready_check';
  room.hostReady = false;
  room.guestReady = false;
  room.readyCheckExpiresAt = Date.now() + 20000;

  const readyCheckMsg = `MATCH_READY_CHECK|${room.code}|${room.hostName}|${room.hostGender}|${room.guestName}|${room.guestGender}|${room.language}|${room.difficulty}|${room.seed}|${room.questionsPayload || 'NONE'}|20`;

  // Notify Host via poll
  room.pushEvent("host", readyCheckMsg);

  console.log(
    `[Duel Ready Check REST] Room #${room.code}: ${room.hostName} VS ${room.guestName} (20s timeout)`,
  );
  res.json({
    success: true,
    code: room.code,
    hostName: room.hostName,
    hostGender: room.hostGender,
    guestName: room.guestName,
    guestGender: room.guestGender,
    language: room.language,
    difficulty: room.difficulty,
    seed: room.seed,
    questionsPayload: room.questionsPayload,
    readyCheckMsg,
    timeoutSeconds: 20
  });
});

// Relay In-Game Action (Score updates, question answers, completion)
app.post("/api/room/:code/action", (req: Request, res: Response) => {
  const paramCode = req.params.code;
  const code = (Array.isArray(paramCode) ? paramCode[0] : paramCode || "")
    .trim()
    .toUpperCase();
  const { role, message } = req.body || {};
  const room = rooms.get(code);

  if (!room || room.finished) {
    res.status(404).json({ error: `Room #${code} not found or expired` });
    return;
  }

  const targetRole = role === "host" ? "guest" : "host";

  if (typeof message === "string" && message.startsWith("PLAYER_READY")) {
    if (role === "host") {
      room.hostReady = true;
      room.pushEvent("guest", `OPPONENT_READY|${room.code}`);
    } else {
      room.guestReady = true;
      room.pushEvent("host", `OPPONENT_READY|${room.code}`);
    }

    if (room.hostReady && room.guestReady) {
      room.active = true;
      room.state = "active";
      const matchStartMsg = `MATCH_START|${room.code}|${room.hostName}|${room.hostGender}|${room.guestName}|${room.guestGender}|${room.language}|${room.difficulty}|${room.seed}${room.questionsPayload ? `|${room.questionsPayload}` : ""}`;
      room.pushEvent("host", matchStartMsg);
      room.pushEvent("guest", matchStartMsg);
      console.log(`[Duel Start REST] Room #${room.code}: Both players READY! Match started.`);
    }
    res.json({ success: true, hostReady: room.hostReady, guestReady: room.guestReady });
    return;
  }

  if (typeof message === "string" && message.startsWith("DECLINE_MATCH")) {
    room.finished = true;
    room.state = "finished";
    const cancelMsg = `MATCH_CANCELLED|${room.code}|Match declined by ${role === "host" ? room.hostName : room.guestName}.`;
    room.pushEvent(targetRole, cancelMsg);
    res.json({ success: true, cancelled: true });
    return;
  }

  room.pushEvent(targetRole, message);

  // Track match score if updating
  if (typeof message === "string" && message.startsWith("OPPONENT_UPDATE")) {
    const parts = message.split("|");
    if (parts.length >= 8) {
      const score = parseInt(parts[6], 10) || 0;
      if (role === "host") room.hostScore = Math.max(room.hostScore, score);
      else room.guestScore = Math.max(room.guestScore, score);
    }
  }

  res.json({ success: true });
});

// Long-Poll Endpoint for In-Game Events
app.get("/api/room/:code/poll", (req: Request, res: Response) => {
  const paramCode = req.params.code;
  const code = (Array.isArray(paramCode) ? paramCode[0] : paramCode || "")
    .trim()
    .toUpperCase();
  const role = req.query.role === "guest" ? "guest" : "host";
  const room = rooms.get(code);

  if (!room || room.finished) {
    res
      .status(404)
      .json({ error: `Room #${code} closed`, closed: true, events: [] });
    return;
  }

  const queue = role === "host" ? room.hostEvents : room.guestEvents;
  if (role === "host") room.lastHostPing = Date.now();
  else room.lastGuestPing = Date.now();

  // If there are already queued events, return them immediately
  if (queue.length > 0) {
    const events = [...queue];
    queue.length = 0;
    res.json({ events });
    return;
  }

  // Long poll: hold response up to 3500ms waiting for event
  let timer: any = null;
  const resolver = (evts: string[]) => {
    if (timer) clearTimeout(timer);
    res.json({ events: evts });
  };

  if (role === "host") room.hostPollResolver = resolver;
  else room.guestPollResolver = resolver;

  timer = setTimeout(() => {
    if (role === "host" && room.hostPollResolver === resolver)
      room.hostPollResolver = null;
    if (role === "guest" && room.guestPollResolver === resolver)
      room.guestPollResolver = null;
    res.json({ events: [] });
  }, 3500);
});

// Leave / Forfeit Room via REST
app.post("/api/room/:code/leave", (req: Request, res: Response) => {
  const paramCode = req.params.code;
  const code = (Array.isArray(paramCode) ? paramCode[0] : paramCode || "")
    .trim()
    .toUpperCase();
  const { role, name } = req.body || {};
  const room = rooms.get(code);

  if (room && !room.finished) {
    room.finished = true;
    const targetRole = role === "host" ? "guest" : "host";
    if (room.state === 'ready_check' && !room.active) {
      room.pushEvent(targetRole, `MATCH_CANCELLED|${room.code}|Opponent left the room before accepting match.`);
    } else {
      room.pushEvent(targetRole, `OPPONENT_LEFT|${clean(name || "Opponent")}`);
    }
  }
  res.json({ success: true });
});

// Stats summary
app.get("/api/stats", (req: Request, res: Response) => {
  res.json({
    playersOnline: activeSessions.size,
    activeRooms: rooms.size,
    leaderboardEntries: leaderboard.size,
    totalDuelsCompleted,
    database: supabase ? "Supabase PostgreSQL" : "Local Fallback",
    memoryUsageMB: Math.round(process.memoryUsage().rss / (1024 * 1024)),
  });
});

// Restricted Console / Terminal Theme Interface
app.get("/", (req: Request, res: Response) => {
  const sorted = getSortedLeaderboard().slice(0, 10);
  const openRooms = Array.from(rooms.values()).filter((r) => !r.finished);

  const leaderboardRows =
    sorted.length > 0
      ? sorted
          .map((entry, i) => {
            const rankStr = String(i + 1).padStart(2, "0");
            const nameStr = clean(entry.name).padEnd(16, " ").slice(0, 16);
            const scoreStr = `${entry.score.toLocaleString()} pts`.padStart(
              10,
              " ",
            );
            return `[${rankStr}]  ${nameStr}  ${scoreStr}  (${entry.gender})`;
          })
          .join("\n")
      : "[--]  No entries recorded in database yet.";

  const roomsListText =
    openRooms.length > 0
      ? openRooms
          .map((r) => {
            const status = r.active ? "IN_MATCH" : "WAITING";
            return `> ROOM #${r.code.padEnd(5, " ")} HOST: ${clean(r.hostName).padEnd(12, " ")} LANG: ${r.language.padEnd(10, " ")} DIFF: ${r.difficulty.padEnd(8, " ")} [${status}]`;
          })
          .join("\n")
      : "> No active rooms currently open on this node.";

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>GoQuiz Adventure — Gateway Telemetry Console</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600;700;800&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #090c10;
      --terminal-bg: #0d1117;
      --border: #30363d;
      --green: #2ea043;
      --bright-green: #3fb950;
      --amber: #d29922;
      --red: #f85149;
      --cyan: #58a6ff;
      --muted: #8b949e;
      --text: #c9d1d9;
      --glow: rgba(46, 160, 67, 0.15);
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background-color: var(--bg);
      color: var(--text);
      font-family: 'JetBrains Mono', monospace;
      padding: 24px 16px;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      line-height: 1.5;
    }
    .wrapper {
      max-width: 980px;
      width: 100%;
    }
    /* ACCESS WARNING BANNER */
    .warning-banner {
      background: rgba(248, 81, 73, 0.1);
      border: 1px solid var(--red);
      border-left: 5px solid var(--red);
      border-radius: 6px;
      padding: 14px 18px;
      margin-bottom: 20px;
      font-size: 13px;
    }
    .warning-title {
      color: var(--red);
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .warning-body {
      color: #e6edf3;
      font-size: 12.5px;
    }
    .warning-body strong { color: #fff; }

    /* CONSOLE WINDOW */
    .terminal-window {
      background: var(--terminal-bg);
      border: 1px solid var(--border);
      border-radius: 8px;
      box-shadow: 0 16px 45px rgba(0, 0, 0, 0.7);
      overflow: hidden;
      margin-bottom: 20px;
    }
    .terminal-header {
      background: #161b22;
      border-bottom: 1px solid var(--border);
      padding: 10px 14px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      user-select: none;
    }
    .terminal-buttons {
      display: flex;
      gap: 7px;
    }
    .btn-dot {
      width: 11px;
      height: 11px;
      border-radius: 50%;
    }
    .dot-red { background: #ff5f56; }
    .dot-yellow { background: #ffbd2e; }
    .dot-green { background: #27c93f; }
    .terminal-title {
      font-size: 12px;
      color: var(--muted);
      font-weight: 600;
    }
    .terminal-body {
      padding: 20px;
      font-size: 13px;
    }

    /* ASCII ART */
    .ascii-art {
      color: var(--bright-green);
      font-size: 11.5px;
      line-height: 1.15;
      white-space: pre;
      margin-bottom: 16px;
      user-select: none;
    }

    /* TELEMETRY GRIDS */
    .status-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
      gap: 12px;
      margin: 18px 0;
    }
    .status-card {
      background: rgba(255, 255, 255, 0.02);
      border: 1px solid var(--border);
      border-radius: 6px;
      padding: 12px 14px;
    }
    .status-label {
      font-size: 11px;
      color: var(--muted);
      text-transform: uppercase;
      letter-spacing: 0.5px;
      margin-bottom: 4px;
    }
    .status-value {
      font-size: 17px;
      font-weight: 700;
      color: #fff;
    }
    .status-value.ok { color: var(--bright-green); }
    .status-value.cyan { color: var(--cyan); }
    .status-value.amber { color: var(--amber); }

    /* SECTION BOXES */
    .section-title {
      color: var(--cyan);
      font-size: 13px;
      font-weight: 700;
      margin: 18px 0 8px 0;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .code-block {
      background: #040608;
      border: 1px solid #21262d;
      border-radius: 6px;
      padding: 12px 14px;
      font-size: 12.5px;
      color: #e6edf3;
      white-space: pre-wrap;
      overflow-x: auto;
      margin-bottom: 14px;
    }

    /* ACTIONS BAR */
    .actions-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 14px;
    }
    .cmd-btn {
      background: #21262d;
      color: #e6edf3;
      border: 1px solid var(--border);
      padding: 8px 14px;
      border-radius: 6px;
      font-family: 'JetBrains Mono', monospace;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.15s ease;
    }
    .cmd-btn:hover {
      background: #30363d;
      border-color: #8b949e;
      color: #fff;
    }
    .cmd-btn.primary {
      background: rgba(46, 160, 67, 0.2);
      border-color: var(--green);
      color: var(--bright-green);
    }
    .cmd-btn.primary:hover {
      background: rgba(46, 160, 67, 0.35);
    }
    .live-feed {
      background: #020406;
      border: 1px solid #161b22;
      border-radius: 6px;
      padding: 12px;
      margin-top: 10px;
      font-size: 12px;
      color: var(--bright-green);
      min-height: 50px;
      max-height: 160px;
      overflow-y: auto;
      white-space: pre-wrap;
    }
    .prompt { color: var(--amber); font-weight: 700; }

    footer {
      text-align: center;
      font-size: 11.5px;
      color: var(--muted);
      margin-top: 16px;
    }
  </style>
</head>
<body>
  <div class="wrapper">
    <!-- RESTRICTED ACCESS NOTICE -->
    <div class="warning-banner">
      <div class="warning-title">
        <span>🛑 RESTRICTED ACCESS — BACKEND INFRASTRUCTURE SERVICE</span>
      </div>
      <div class="warning-body">
        This host is a dedicated <strong>WebSocket & REST Gateway Relay</strong> for the GoQuiz Adventure game engine.<br>
        <strong>Not for direct web browser gaming:</strong> Regular users and students cannot play from this page. To play, launch the native <strong>Android APK</strong> or <strong>Windows Desktop (.exe)</strong> client.
      </div>
    </div>

    <!-- TERMINAL / CONSOLE WINDOW -->
    <div class="terminal-window">
      <div class="terminal-header">
        <div class="terminal-buttons">
          <span class="btn-dot dot-red"></span>
          <span class="btn-dot dot-yellow"></span>
          <span class="btn-dot dot-green"></span>
        </div>
        <div class="terminal-title">admin@goquiz-relay-core: ~ (PID: ${process.pid || 1})</div>
        <div style="font-size: 11px; color: var(--bright-green);">● ONLINE</div>
      </div>

      <div class="terminal-body">
        <!-- ASCII HEADER -->
        <div class="ascii-art">
   ______        ____        _         ___       __                 __                  
  / ____/____   / __ \ __  _(_)____   /   | ____/ /   _____  ____  / /___  __________   
 / / __ / __ \ / / / // / / // /_  /  / /| |/ __  / | / / _ \/ __ \/ __/ / / / ___/ _ \  
/ /_/ // /_/ // /_/ // /_/ // / / /_ / ___ / /_/ /| |/ /  __/ / / / /_/ /_/ / /  /  __/  
\____/ \____/ \___\_\\__,_//_/ /___//_/  |_\__,_/ |___/\___/_/ /_/\__/\__,_/_/   \___/   
========================================================================================
[GATEWAY NODE // CLOUD MULTIPLAYER & LEADERBOARD RELAY] • VER: 1.2.1 • PLATFORM: RENDER</div>

        <!-- TELEMETRY METRICS GRID -->
        <div class="status-grid">
          <div class="status-card">
            <div class="status-label">Gateway Status</div>
            <div class="status-value ok">● ACTIVE (HTTP/WS)</div>
          </div>
          <div class="status-card">
            <div class="status-label">Database Store</div>
            <div class="status-value cyan">${supabase ? "SUPABASE POSTGRESQL" : "LOCAL CACHE"}</div>
          </div>
          <div class="status-card">
            <div class="status-label">Live Sockets</div>
            <div class="status-value amber">${activeSessions.size} CONNECTED</div>
          </div>
          <div class="status-card">
            <div class="status-label">Active Duels</div>
            <div class="status-value">${rooms.size} OPEN ROOMS</div>
          </div>
          <div class="status-card">
            <div class="status-label">Process Memory</div>
            <div class="status-value">${Math.round(process.memoryUsage().rss / (1024 * 1024))} MB RSS</div>
          </div>
          <div class="status-card">
            <div class="status-label">Duels Completed</div>
            <div class="status-value ok">${totalDuelsCompleted} MATCHES</div>
          </div>
        </div>

        <!-- CLIENT APPLICATION INSTRUCTIONS -->
        <div class="section-title">📦 NATIVE CLIENT APPLICATIONS</div>
        <div class="code-block">┌─────────────────────────────────────────────────────────────────────────────────┐
│ [Android] Run GoQuiz-v1.2.1.apk on phones/tablets (Connects via WebSocket/WSS)  │
│ [Windows] Run GoQuiz-v1.2.1.exe on desktop/laptop PCs                           │
│ [Server]  Hosted on Render Web Services • Port 443 / 10000                      │
└─────────────────────────────────────────────────────────────────────────────────┘</div>

        <!-- CRON & KEEP-ALIVE INTERFACE -->
        <div class="section-title">⏰ ANTI-SLEEP CRON CONFIGURATION (KEEP RENDER AWAKE)</div>
        <div class="code-block" id="cron-target-box">$ curl -X GET <span id="keep-alive-endpoint" style="color:var(--bright-green);">https://goquiz-gateway.onrender.com/api/keep-alive</span>
# Frequency: Every 10 minutes via cron-job.org / UptimeRobot</div>

        <!-- LIVE DIAGNOSTIC CONTROLS -->
        <div class="section-title">⚡ SYSTEM DIAGNOSTICS & TELEMETRY PROBES</div>
        <div class="actions-bar">
          <button class="cmd-btn primary" onclick="runKeepAliveProbe()">$ probe --keep-alive</button>
          <button class="cmd-btn" onclick="runWsProbe()">$ probe --websocket</button>
          <button class="cmd-btn" onclick="runStatsProbe()">$ fetch --sysinfo</button>
          <button class="cmd-btn" onclick="clearConsole()">$ clear</button>
        </div>
        <div class="live-feed" id="terminal-feed">> System initialized. Ready for telemetry commands...</div>

        <!-- LEADERBOARD DATA DUMP -->
        <div class="section-title">🏆 PERSISTENT HALL OF FAME [TOP SNAPSHOT]</div>
        <div class="code-block" style="color: #58a6ff;"># RANK  PLAYER NAME       SCORE       GENDER
===================================================
${leaderboardRows}</div>

        <!-- ACTIVE ROOMS DUMP -->
        <div class="section-title">⚔️ OPEN ROOMS REGISTRY</div>
        <div class="code-block" style="color: var(--amber);">${roomsListText}</div>
      </div>
    </div>

    <footer>
      GoQuiz Adventure Infrastructure Core • Node.js, Express, Bun & TypeScript • Render Cloud
    </footer>
  </div>

  <script>
    const feed = document.getElementById('terminal-feed');
    const endpointSpan = document.getElementById('keep-alive-endpoint');
    endpointSpan.innerText = window.location.origin + '/api/keep-alive';

    function log(msg, color) {
      const time = new Date().toLocaleTimeString();
      const line = '[' + time + '] ' + msg;
      feed.innerText += '\\n' + line;
      feed.scrollTop = feed.scrollHeight;
    }

    function clearConsole() {
      feed.innerText = '> Console buffer cleared.';
    }

    async function runKeepAliveProbe() {
      log('Executing: GET /api/keep-alive ...');
      try {
        const res = await fetch('/api/keep-alive');
        const data = await res.json();
        log('HTTP ' + res.status + ' OK: ' + JSON.stringify(data));
      } catch (err) {
        log('Probe failed: ' + err.message);
      }
    }

    async function runStatsProbe() {
      log('Executing: GET /api/stats ...');
      try {
        const res = await fetch('/api/stats');
        const data = await res.json();
        log('SYSINFO: ' + JSON.stringify(data));
      } catch (err) {
        log('Sysinfo fetch failed: ' + err.message);
      }
    }

    let wsProbe;
    function runWsProbe() {
      const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = proto + '//' + window.location.host;
      log('Connecting WebSocket: ' + wsUrl + ' ...');
      wsProbe = new WebSocket(wsUrl);
      wsProbe.onopen = () => {
        log('WS OPEN: Sending HELLO|ConsoleProbe|Sysadmin ...');
        wsProbe.send('HELLO|ConsoleProbe|Sysadmin');
        setTimeout(() => {
          if (wsProbe.readyState === WebSocket.OPEN) {
            log('WS TX: PING');
            wsProbe.send('PING');
          }
        }, 300);
      };
      wsProbe.onmessage = (e) => {
        log('WS RX: ' + e.data);
      };
      wsProbe.onerror = (e) => {
        log('WS ERROR: ' + (e.message || 'Connection failure'));
      };
      wsProbe.onclose = () => {
        log('WS CLOSED: Probe finished.');
      };
    }
  </script>
</body>
</html>`;

  res.type("text/html").send(html);
});

// ==========================================
// CREATE SERVERS (HTTP + WEBSOCKET + TCP)
// ==========================================
const server: HttpServer = createServer(app);

// WebSocket Server attached to HTTP server (Required for Render)
const wss = new WebSocketServer({ server });

wss.on("connection", (ws: WebSocket, req: any) => {
  const ip =
    (req.headers["x-forwarded-for"] as string) ||
    req.socket.remoteAddress ||
    "Unknown";
  const session = new ClientSession("ws", ws, ip);
  activeSessions.add(session);
  console.log(
    `[WS] Client connected from ${ip} (Total active: ${activeSessions.size})`,
  );

  ws.on("message", (data: RawData) => {
    try {
      const text = data.toString();
      session.handleMessage(text);
    } catch (err: any) {
      console.warn(`[WS ${ip}] Message handling error:`, err.message);
    }
  });

  ws.on("close", () => {
    console.log(`[WS] Client disconnected: ${ip}`);
    session.cleanup();
  });

  ws.on("error", (err: Error) => {
    console.warn(`[WS ${ip}] Socket error:`, err.message);
  });
});

// Start HTTP Server
server.listen(HTTP_PORT, () => {
  console.log("====================================================");
  console.log(`  GoQuiz Adventure — Global Multiplayer Cloud Server`);
  console.log("====================================================");
  console.log(`[HTTP/WS] Server listening on port ${HTTP_PORT}`);
  console.log(`[HTTP/WS] Public Dashboard: http://localhost:${HTTP_PORT}`);
  console.log(`[HTTP/WS] WebSocket endpoint: ws://localhost:${HTTP_PORT}`);
  console.log(
    `[Storage] Mode: ${supabase ? "Supabase PostgreSQL" : "Local Disk Backup"}`,
  );
  console.log(
    `[KeepAlive] Endpoint active: http://localhost:${HTTP_PORT}/api/keep-alive`,
  );

  // Render Anti-Sleep Automated Self-Pinger
  const selfPingTarget =
    process.env.RENDER_EXTERNAL_URL || process.env.SELF_PING_URL || "";
  if (selfPingTarget) {
    const intervalMinutes = Math.max(
      3,
      Math.min(
        14,
        parseInt(process.env.SELF_PING_INTERVAL_MINUTES || "10", 10),
      ),
    );
    const pingUrl = `${selfPingTarget.replace(/\/$/, "")}/api/keep-alive`;
    console.log(
      `[KeepAlive] Automated self-pinger initialized for ${pingUrl} (every ${intervalMinutes} mins)`,
    );

    setInterval(
      async () => {
        try {
          const res = await fetch(pingUrl, {
            headers: { "User-Agent": "GoQuiz-AntiSleep-SelfPinger/1.0" },
          });
          if (res.ok) {
            console.log(
              `[KeepAlive] Automated self-ping SUCCESS at ${new Date().toLocaleTimeString()} (Server kept awake)`,
            );
          }
        } catch (err: any) {
          console.warn(`[KeepAlive] Automated self-ping error:`, err.message);
        }
      },
      intervalMinutes * 60 * 1000,
    );
  }
});

// Optional: Start raw TCP Socket Server if running locally or on a VPS with open TCP ports
if (process.env.ENABLE_TCP !== "false" && TCP_PORT !== HTTP_PORT) {
  try {
    const tcpServer: TcpServer = net.createServer((socket: TcpSocket) => {
      const isLocalProbe =
        socket.remoteAddress === "::1" || socket.remoteAddress === "127.0.0.1";
      const ip = `${socket.remoteAddress}:${socket.remotePort}`;
      const session = new ClientSession("tcp", socket, ip);
      activeSessions.add(session);
      if (!isLocalProbe) {
        console.log(`[TCP] Client connected from ${ip}`);
      }

      let buffer = "";
      socket.on("data", (chunk: Buffer) => {
        buffer += chunk.toString("utf-8");
        const lines = buffer.split(/\r?\n/);
        buffer = lines.pop() || "";
        for (const line of lines) {
          session.handleMessage(line);
        }
      });

      socket.on("close", () => {
        if (!isLocalProbe) {
          console.log(`[TCP] Client disconnected: ${ip}`);
        }
        session.cleanup();
      });

      socket.on("error", (err: any) => {
        if (err.code === "ECONNRESET" || err.code === "EPIPE") {
          // Silent for port probes / abrupt TCP drop
          return;
        }
        console.warn(`[TCP ${ip}] Error:`, err.message);
      });
    });

    tcpServer.listen(TCP_PORT, () => {
      console.log(`[TCP] Raw socket server listening on port ${TCP_PORT}`);
    });
  } catch (err: any) {
    console.warn("[TCP] Could not start TCP listener:", err.message);
  }
}
