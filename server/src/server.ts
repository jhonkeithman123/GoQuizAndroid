import 'dotenv/config';
import express, { type Request, type Response } from 'express';
import cors from 'cors';
import { createServer, type Server as HttpServer } from 'http';
import { WebSocketServer, WebSocket, type RawData } from 'ws';
import net, { type Socket as TcpSocket, type Server as TcpServer } from 'net';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createClient, type SupabaseClient } from '@supabase/supabase-js';
import type {
  LeaderboardEntry,
  SortedLeaderboardItem,
  DbLeaderboardRow,
  DbMatchHistoryRow,
  SessionTransport,
  IClientSession,
  IDuelRoom,
  RoomSummary,
  KeepAliveResponse
} from './types.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');

// ==========================================
// CONFIGURATION & ENVIRONMENT
// ==========================================
const HTTP_PORT: number = parseInt(process.env.PORT || '5050', 10);
const TCP_PORT: number = parseInt(process.env.TCP_PORT || '5052', 10);
const DATA_DIR: string = path.join(ROOT_DIR, 'data');
const LEADERBOARD_JSON: string = path.join(DATA_DIR, 'leaderboard.json');
const PROP_LEADERBOARD: string = path.join(ROOT_DIR, 'online_leaderboard.properties');
const ROOT_PROP_LEADERBOARD: string = path.join(ROOT_DIR, '..', 'online_leaderboard.properties');

if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// ==========================================
// SUPABASE CLIENT INITIALIZATION
// ==========================================
const SUPABASE_URL: string = process.env.SUPABASE_URL || '';
const SUPABASE_KEY: string = process.env.SUPABASE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY || '';
let supabase: SupabaseClient | null = null;

if (SUPABASE_URL && SUPABASE_KEY) {
  try {
    supabase = createClient(SUPABASE_URL, SUPABASE_KEY);
    console.log(`[Database] Connected to Supabase Cloud: ${SUPABASE_URL}`);
  } catch (err: any) {
    console.warn('[Database] Failed to initialize Supabase client:', err.message);
  }
} else {
  console.log('[Database] Supabase credentials not provided. Using local file storage fallback.');
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
        .from('leaderboard')
        .select('player_name, score, gender, updated_at')
        .order('score', { ascending: false });

      if (!error && Array.isArray(data)) {
        leaderboard.clear();
        for (const row of (data as DbLeaderboardRow[])) {
          leaderboard.set(row.player_name, {
            score: Math.max(0, row.score || 0),
            gender: row.gender || 'Unknown',
            updatedAt: row.updated_at || new Date().toISOString()
          });
        }
        console.log(`[Leaderboard] Synced ${leaderboard.size} entries from Supabase PostgreSQL`);
        saveLocalLeaderboard();
        return;
      } else if (error) {
        console.warn('[Leaderboard] Supabase fetch error:', error.message);
      }
    } catch (err: any) {
      console.warn('[Leaderboard] Supabase load failed, falling back to local:', err.message);
    }
  }

  // 2. Fallback to local JSON
  try {
    if (fs.existsSync(LEADERBOARD_JSON)) {
      const raw = fs.readFileSync(LEADERBOARD_JSON, 'utf-8');
      const data: Record<string, any> = JSON.parse(raw);
      for (const [name, val] of Object.entries(data)) {
        const score = typeof val === 'object' && val !== null ? val.score : Number(val);
        const gender = typeof val === 'object' && val !== null ? (val.gender || 'Unknown') : 'Unknown';
        leaderboard.set(name, {
          score: Math.max(0, score || 0),
          gender,
          updatedAt: new Date().toISOString()
        });
      }
      console.log(`[Leaderboard] Loaded ${leaderboard.size} entries from local JSON`);
      return;
    }
  } catch (err: any) {
    console.warn('[Leaderboard] Failed to parse JSON leaderboard:', err.message);
  }

  // 3. Fallback to legacy properties file
  const propFile = fs.existsSync(PROP_LEADERBOARD)
    ? PROP_LEADERBOARD
    : (fs.existsSync(ROOT_PROP_LEADERBOARD) ? ROOT_PROP_LEADERBOARD : null);

  if (propFile) {
    try {
      const content = fs.readFileSync(propFile, 'utf-8');
      content.split(/\r?\n/).forEach((line) => {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) return;
        const eqIdx = trimmed.indexOf('=');
        if (eqIdx > 0) {
          const name = trimmed.substring(0, eqIdx).trim();
          const score = parseInt(trimmed.substring(eqIdx + 1).trim(), 10) || 0;
          if (name) {
            leaderboard.set(name, {
              score: Math.max(0, score),
              gender: 'Unknown',
              updatedAt: new Date().toISOString()
            });
          }
        }
      });
      console.log(`[Leaderboard] Imported ${leaderboard.size} entries from properties file: ${propFile}`);
      saveLocalLeaderboard();
    } catch (err: any) {
      console.warn('[Leaderboard] Failed to read properties file:', err.message);
    }
  }
}

function saveLocalLeaderboard(): void {
  try {
    const obj: Record<string, LeaderboardEntry> = {};
    for (const [name, data] of leaderboard.entries()) {
      obj[name] = data;
    }
    fs.writeFileSync(LEADERBOARD_JSON, JSON.stringify(obj, null, 2), 'utf-8');

    // Also update legacy properties file
    let propContent = '# GoQuiz Shared Online Leaderboard\n# Updated ' + new Date().toISOString() + '\n';
    for (const [name, data] of leaderboard.entries()) {
      propContent += `${name}=${data.score}\n`;
    }
    fs.writeFileSync(PROP_LEADERBOARD, propContent, 'utf-8');
  } catch (err: any) {
    console.error('[Leaderboard] Local save error:', err.message);
  }
}

async function updateScore(name: string, score: number | string, gender: string = 'Unknown'): Promise<void> {
  if (!name || typeof name !== 'string') return;
  const cleanName = clean(name).trim();
  if (!cleanName) return;
  const newScore = Math.max(0, typeof score === 'number' ? score : parseInt(score, 10) || 0);
  const current = leaderboard.get(cleanName);

  if (!current || newScore > current.score) {
    const entry: LeaderboardEntry = {
      score: newScore,
      gender: gender && gender !== 'Unknown' ? gender : (current?.gender || 'Unknown'),
      updatedAt: new Date().toISOString()
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
          updated_at: entry.updatedAt
        };
        const { error } = await supabase
          .from('leaderboard')
          .upsert(row, { onConflict: 'player_name' });

        if (error) {
          console.warn(`[Supabase] Leaderboard upsert error for ${cleanName}:`, error.message);
        } else {
          console.log(`[Supabase] Synced high score for ${cleanName}: ${newScore} pts`);
        }
      } catch (err: any) {
        console.warn(`[Supabase] Sync failed:`, err.message);
      }
    }
  }
}

async function recordMatchHistory(room: IDuelRoom, hostScore: number, guestScore: number, winnerName: string): Promise<void> {
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
      finished_at: new Date().toISOString()
    };
    const { error } = await supabase.from('match_history').insert(row);
    if (error) console.warn('[Supabase] Match history log error:', error.message);
  } catch (err: any) {
    console.warn('[Supabase] Match history log failed:', err.message);
  }
}

function getSortedLeaderboard(): SortedLeaderboardItem[] {
  return Array.from(leaderboard.entries())
    .map(([name, data]) => ({ name, score: data.score, gender: data.gender }))
    .sort((a, b) => b.score - a.score || a.name.localeCompare(b.name));
}

function getLeaderboardMessage(): string {
  const sorted = getSortedLeaderboard();
  let msg = 'BOARD';
  for (const entry of sorted) {
    msg += `|${clean(entry.name)}|${entry.score}`;
    if (msg.length > 7000) break;
  }
  return msg;
}

function clean(str: string | null | undefined): string {
  if (!str) return '';
  return String(str).replace(/[|\r\n]/g, ' ');
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
  questionsPayload: string = '';
  hostSession: IClientSession | null;
  guestSession: IClientSession | null = null;
  guestName: string = '';
  guestGender: string = '';
  hostScore: number = 0;
  guestScore: number = 0;
  active: boolean = false;
  finished: boolean = false;
  createdAt: number = Date.now();

  constructor(code: string, hostName: string, hostGender: string, language: string, difficulty: string, hostSession: IClientSession) {
    this.code = code;
    this.hostName = hostName;
    this.hostGender = hostGender;
    this.language = language;
    this.difficulty = difficulty;
    this.seed = Date.now();
    this.hostSession = hostSession;
  }
}

const rooms: Map<string, IDuelRoom> = new Map();
const activeSessions: Set<IClientSession> = new Set();
let totalDuelsCompleted: number = 0;

function generateRoomCode(): string {
  const chars = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
  let code = '';
  let attempts = 0;
  do {
    code = '';
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
    res += `|${clean(s.remoteAddress)}|${clean(s.playerName || 'Guest')}|${clean(s.playerGender || 'Unknown')}|${clean(s.getStatus())}`;
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
  playerName: string = '';
  playerGender: string = '';
  currentRoom: IDuelRoom | null = null;
  connectedAt: number = Date.now();
  lastPing: number = Date.now();

  constructor(type: SessionTransport, socket: WebSocket | TcpSocket, remoteAddress: string) {
    this.type = type;
    this.socket = socket;
    this.remoteAddress = remoteAddress || 'Unknown';
  }

  getStatus(): string {
    if (this.currentRoom) {
      if (this === this.currentRoom.hostSession) {
        return this.currentRoom.active ? `In Duel #${this.currentRoom.code}` : `Hosting Room #${this.currentRoom.code}`;
      } else {
        return `In Duel #${this.currentRoom.code}`;
      }
    }
    return 'Connected in Lobby';
  }

  send(msg: string): void {
    try {
      if (this.type === 'ws') {
        const ws = this.socket as WebSocket;
        if (ws && ws.readyState === WebSocket.OPEN) {
          ws.send(msg);
        }
      } else if (this.type === 'tcp') {
        const tcp = this.socket as TcpSocket;
        if (tcp && !tcp.destroyed) {
          tcp.write(msg + '\n');
        }
      }
    } catch (err: any) {
      console.warn(`[Session ${this.remoteAddress}] Send error:`, err.message);
    }
  }

  close(): void {
    try {
      if (this.type === 'ws') (this.socket as WebSocket).close();
      if (this.type === 'tcp') (this.socket as TcpSocket).destroy();
    } catch (_) {}
  }

  cleanup(): void {
    activeSessions.delete(this);
    if (this.currentRoom) {
      const room = this.currentRoom;
      this.currentRoom = null;
      if (!room.finished) {
        room.finished = true;
        const opponent = this === room.hostSession ? room.guestSession : room.hostSession;
        const leftName = this === room.hostSession ? room.hostName : room.guestName;
        if (opponent) {
          opponent.send(`OPPONENT_LEFT|${clean(leftName)}`);
        }
      }
      rooms.delete(room.code);
    }
  }

  handleMessage(line: string): void {
    this.lastPing = Date.now();
    const raw = (line || '').trim();
    if (!raw) return;

    const p = raw.split('|');
    const cmd = p[0];

    switch (cmd) {
      case 'HELLO': {
        if (p.length > 1) this.playerName = clean(p[1]);
        if (p.length > 2) this.playerGender = clean(p[2]);
        this.send('OK');
        break;
      }

      case 'CLIENT_LIST': {
        this.send(getConnectedClientsMessage());
        break;
      }

      case 'SCORE': {
        if (p.length < 3) {
          this.send('ERROR|Invalid SCORE command.');
          return;
        }
        updateScore(p[1], p[2], this.playerGender);
        this.send('OK');
        break;
      }

      case 'LEADERBOARD':
      case 'BOARD': {
        this.send(getLeaderboardMessage());
        break;
      }

      case 'INFO': {
        const dbInfo = supabase ? 'Supabase' : 'Local';
        this.send(`INFO|GoQuiz-RenderCloud|${dbInfo}|${rooms.size}`);
        break;
      }

      case 'PING': {
        this.send('PONG');
        break;
      }

      case 'ROOM_CREATE': {
        const hostName = p.length > 1 ? clean(p[1]) : 'Host';
        const hostGender = p.length > 2 ? clean(p[2]) : 'Boy';
        const language = p.length > 3 ? clean(p[3]) : 'HTML';
        const difficulty = p.length > 4 ? clean(p[4]) : 'Easy';
        const questionsPayload = p.length > 5 ? p[5] : '';
        this.playerName = hostName;
        this.playerGender = hostGender;

        const code = generateRoomCode();
        const room = new DuelRoom(code, hostName, hostGender, language, difficulty, this);
        room.questionsPayload = questionsPayload;
        this.currentRoom = room;
        rooms.set(code, room);

        console.log(`[Room] Created Room #${code} by ${hostName} (${language} - ${difficulty})`);
        this.send(`ROOM_CREATED|${code}|${language}|${difficulty}`);
        break;
      }

      case 'ROOM_LIST': {
        let resp = 'ROOM_LIST_RESP';
        for (const r of rooms.values()) {
          if (!r.active && !r.finished && !r.guestSession) {
            if (r.hostSession === this) continue;
            if (this.playerName && this.playerName.toLowerCase() === r.hostName.toLowerCase()) continue;
            resp += `|${r.code}|${clean(r.hostName)}|${clean(r.hostGender)}|${clean(r.language)}|${clean(r.difficulty)}`;
          }
        }
        this.send(resp);
        break;
      }

      case 'ROOM_JOIN': {
        if (p.length < 4) {
          this.send('ERROR|Invalid ROOM_JOIN command.');
          return;
        }
        const code = (p[1] || '').trim().toUpperCase();
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
        if (room.hostSession === this || (room.hostName && room.hostName.toLowerCase() === guestName.toLowerCase())) {
          this.send('ERROR|Host cannot join their own room.');
          return;
        }

        room.guestSession = this;
        room.guestName = guestName;
        room.guestGender = guestGender;
        room.active = true;
        this.currentRoom = room;

        console.log(`[Duel Start] Room #${room.code}: ${room.hostName} VS ${room.guestName}`);

        const matchStartMsg = `MATCH_START|${room.code}|${room.hostName}|${room.hostGender}|${room.guestName}|${room.guestGender}|${room.language}|${room.difficulty}|${room.seed}${room.questionsPayload ? `|${room.questionsPayload}` : ''}`;
        if (room.hostSession) room.hostSession.send(matchStartMsg);
        if (room.guestSession) room.guestSession.send(matchStartMsg);
        break;
      }

      case 'DUEL_ACTION': {
        if (this.currentRoom && p.length >= 9) {
          const opponent = (this === this.currentRoom.hostSession)
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
              this.currentRoom.hostScore = parseInt(score, 10) || this.currentRoom.hostScore;
            } else {
              this.currentRoom.guestScore = parseInt(score, 10) || this.currentRoom.guestScore;
            }

            opponent.send(`OPPONENT_UPDATE|${sender}|${qIndex}|${isCorrect}|${hp}|${maxHp}|${score}|${combo}`);
          }
        }
        break;
      }

      case 'DUEL_FINISH': {
        if (this.currentRoom && p.length >= 5) {
          const opponent = (this === this.currentRoom.hostSession)
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
          let winner = 'Tie';
          if (hostS > guestS) winner = this.currentRoom.hostName;
          else if (guestS > hostS) winner = this.currentRoom.guestName;

          recordMatchHistory(this.currentRoom, hostS, guestS, winner);
        }
        break;
      }

      case 'LEAVE_ROOM': {
        this.cleanup();
        this.send('LEFT_ROOM');
        break;
      }

      default:
        this.send('ERROR|Unknown command.');
        break;
    }
  }
}

// Periodic cleanup of stale inactive rooms (older than 20 minutes)
setInterval(() => {
  const now = Date.now();
  for (const [code, room] of rooms.entries()) {
    if (now - room.createdAt > 20 * 60 * 1000) {
      if (!room.active || room.finished) {
        rooms.delete(code);
      }
    }
  }
}, 60000);

// ==========================================
// EXPRESS APP & REST API
// ==========================================
const app = express();
app.use(cors());
app.use(express.json());

// Anti-Sleep Keep-Alive & Cron Job Endpoints
let lastKeepAlivePing: number = Date.now();
let keepAliveCount: number = 0;

app.get(['/api/keep-alive', '/ping'], (req: Request, res: Response) => {
  lastKeepAlivePing = Date.now();
  keepAliveCount++;
  const clientIp = (req.headers['x-forwarded-for'] as string) || req.socket.remoteAddress || 'cron';
  console.log(`[KeepAlive] Ping received #${keepAliveCount} from ${clientIp} (${new Date().toLocaleTimeString()})`);

  const responsePayload: KeepAliveResponse = {
    status: 'awake',
    message: 'GoQuiz Adventure server is awake and active! Render sleep prevented.',
    pingCount: keepAliveCount,
    uptimeSeconds: Math.floor(process.uptime()),
    database: supabase ? 'Supabase PostgreSQL' : 'Local Disk Backup',
    activeRooms: rooms.size,
    connectedPlayers: activeSessions.size,
    timestamp: new Date().toISOString()
  };

  res.status(200).json(responsePayload);
});

// Health Check Endpoint (For Render monitoring & pingers)
app.get('/health', (req: Request, res: Response) => {
  res.json({
    status: 'ok',
    service: 'GoQuiz Global Multiplayer Gateway',
    database: supabase ? 'Supabase PostgreSQL' : 'Local Disk Backup',
    uptimeSeconds: Math.floor(process.uptime()),
    activeRooms: rooms.size,
    connectedPlayers: activeSessions.size,
    keepAlivePings: keepAliveCount,
    lastKeepAliveAt: new Date(lastKeepAlivePing).toISOString(),
    totalDuelsCompleted,
    timestamp: new Date().toISOString()
  });
});

// REST Leaderboard Endpoints
app.get('/api/leaderboard', (req: Request, res: Response) => {
  if (req.query.format === 'raw') {
    res.type('text/plain').send(getLeaderboardMessage());
    return;
  }
  const sorted = getSortedLeaderboard();
  res.json({
    total: sorted.length,
    database: supabase ? 'Supabase' : 'Local',
    leaderboard: sorted.map((entry, index) => ({
      rank: index + 1,
      name: entry.name,
      score: entry.score,
      gender: entry.gender
    }))
  });
});

app.post('/api/score', async (req: Request, res: Response) => {
  const { name, score, gender } = req.body || {};
  if (!name || score === undefined) {
    res.status(400).json({ error: 'Missing name or score' });
    return;
  }
  await updateScore(name, score, gender);
  res.json({ success: true, name, score: leaderboard.get(clean(name))?.score || score });
});

// Active Open Rooms Endpoint
app.get('/api/rooms', (req: Request, res: Response) => {
  const roomList: RoomSummary[] = [];
  for (const r of rooms.values()) {
    if (!r.finished) {
      roomList.push({
        code: r.code,
        hostName: r.hostName,
        hostGender: r.hostGender,
        language: r.language,
        difficulty: r.difficulty,
        active: r.active,
        waiting: !r.guestSession,
        createdAt: r.createdAt
      });
    }
  }
  res.json({ rooms: roomList });
});

// Stats summary
app.get('/api/stats', (req: Request, res: Response) => {
  res.json({
    playersOnline: activeSessions.size,
    activeRooms: rooms.size,
    leaderboardEntries: leaderboard.size,
    totalDuelsCompleted,
    database: supabase ? 'Supabase PostgreSQL' : 'Local Fallback',
    memoryUsageMB: Math.round(process.memoryUsage().rss / (1024 * 1024))
  });
});

// Live Interactive Web Status Dashboard
app.get('/', (req: Request, res: Response) => {
  const sorted = getSortedLeaderboard().slice(0, 15);
  const openRooms = Array.from(rooms.values()).filter(r => !r.finished);

  const leaderboardRows = sorted.length > 0 ? sorted.map((entry, i) => {
    const medal = i === 0 ? '🥇' : (i === 1 ? '🥈' : (i === 2 ? '🥉' : `#${i + 1}`));
    return `
      <tr>
        <td style="font-weight: bold; color: ${i < 3 ? '#fbbf24' : '#94a3b8'};">${medal}</td>
        <td style="font-weight: 600; color: #f8fafc;">${clean(entry.name)}</td>
        <td style="font-weight: 700; color: #38bdf8; text-align: right;">${entry.score.toLocaleString()} pts</td>
      </tr>
    `;
  }).join('') : `<tr><td colspan="3" style="text-align:center; color:#64748b; padding:16px;">No high scores recorded yet!</td></tr>`;

  const roomCards = openRooms.length > 0 ? openRooms.map(r => `
    <div class="room-chip ${r.active ? 'in-duel' : 'waiting'}">
      <div class="room-code">#${r.code}</div>
      <div class="room-details">
        <strong>${clean(r.hostName)}</strong>
        <span class="badge ${r.language.toLowerCase()}">${r.language}</span>
        <span class="badge diff">${r.difficulty}</span>
      </div>
      <div class="room-status">${r.active ? '⚔️ In Duel' : '⏳ Waiting...'}</div>
    </div>
  `).join('') : '<p style="color:#64748b; font-style:italic;">No live rooms currently open. Create one in the app!</p>';

  const dbBadge = supabase
    ? `<span style="background: rgba(16, 185, 129, 0.2); border: 1px solid rgba(16, 185, 129, 0.5); color: #34d399; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 700;">⚡ Supabase Cloud PostgreSQL</span>`
    : `<span style="background: rgba(245, 158, 11, 0.2); border: 1px solid rgba(245, 158, 11, 0.5); color: #fbbf24; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 700;">📁 Local Disk Cache</span>`;

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>GoQuiz Adventure — Global Multiplayer Cloud Gateway</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700;800&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #090d16;
      --card-bg: rgba(22, 30, 49, 0.75);
      --border: rgba(56, 189, 248, 0.2);
      --gold: #f59e0b;
      --cyan: #38bdf8;
      --accent: #6366f1;
      --text: #f1f5f9;
      --muted: #94a3b8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: radial-gradient(circle at 50% 0%, #1e1b4b 0%, var(--bg) 60%);
      color: var(--text);
      font-family: 'Outfit', -apple-system, BlinkMacSystemFont, sans-serif;
      min-height: 100vh;
      padding: 30px 20px;
      display: flex;
      flex-direction: column;
      align-items: center;
    }
    .container {
      max-width: 1040px;
      width: 100%;
    }
    header {
      text-align: center;
      margin-bottom: 35px;
    }
    .pill {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      background: rgba(16, 185, 129, 0.15);
      border: 1px solid rgba(16, 185, 129, 0.4);
      color: #34d399;
      font-size: 13px;
      font-weight: 700;
      padding: 6px 14px;
      border-radius: 9999px;
      margin-bottom: 14px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .pulse-dot {
      width: 8px;
      height: 8px;
      background: #10b981;
      border-radius: 50%;
      box-shadow: 0 0 10px #10b981;
      animation: pulse 1.5s infinite;
    }
    @keyframes pulse { 0% { opacity: 0.4; } 50% { opacity: 1; } 100% { opacity: 0.4; } }
    h1 {
      font-size: clamp(2rem, 4vw, 3rem);
      font-weight: 800;
      background: linear-gradient(135deg, #ffffff 0%, #38bdf8 60%, #818cf8 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      margin-bottom: 10px;
    }
    .subtitle {
      color: var(--muted);
      font-size: 16px;
      max-width: 600px;
      margin: 0 auto;
    }
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 25px;
    }
    .metric-card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 20px;
      backdrop-filter: blur(12px);
      box-shadow: 0 8px 30px rgba(0,0,0,0.4);
    }
    .metric-label { font-size: 13px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 6px; }
    .metric-value { font-size: 32px; font-weight: 800; color: #fff; font-family: 'JetBrains Mono', monospace; }
    .main-grid {
      display: grid;
      grid-template-columns: 1.2fr 1fr;
      gap: 25px;
      margin-bottom: 25px;
    }
    @media (max-width: 768px) {
      .main-grid { grid-template-columns: 1fr; }
    }
    .panel {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 18px;
      padding: 24px;
      backdrop-filter: blur(14px);
      box-shadow: 0 10px 35px rgba(0,0,0,0.45);
    }
    .panel-title {
      font-size: 18px;
      font-weight: 700;
      margin-bottom: 16px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid rgba(255,255,255,0.08);
      padding-bottom: 12px;
    }
    table { width: 100%; border-collapse: collapse; }
    th { text-align: left; font-size: 12px; color: var(--muted); padding-bottom: 10px; text-transform: uppercase; }
    td { padding: 10px 0; border-bottom: 1px solid rgba(255,255,255,0.04); font-size: 15px; }
    .room-chip {
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 12px;
      padding: 12px 16px;
      margin-bottom: 10px;
    }
    .room-chip.waiting { border-color: rgba(56, 189, 248, 0.4); background: rgba(56, 189, 248, 0.05); }
    .room-chip.in-duel { border-color: rgba(245, 158, 11, 0.4); background: rgba(245, 158, 11, 0.05); }
    .room-code { font-family: 'JetBrains Mono', monospace; font-size: 18px; font-weight: 800; color: #fbbf24; }
    .badge {
      display: inline-block;
      font-size: 11px;
      font-weight: 700;
      padding: 2px 6px;
      border-radius: 4px;
      text-transform: uppercase;
      margin-left: 6px;
    }
    .badge.html { background: #e44d26; color: #fff; }
    .badge.css { background: #264de4; color: #fff; }
    .badge.js { background: #f7df1e; color: #000; }
    .badge.diff { background: rgba(255,255,255,0.1); color: #cbd5e1; }
    .test-box {
      background: rgba(0,0,0,0.3);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 12px;
      padding: 14px;
      margin-top: 14px;
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
    }
    .btn-ping {
      background: #38bdf8;
      color: #0f172a;
      border: none;
      font-weight: 700;
      padding: 8px 16px;
      border-radius: 8px;
      cursor: pointer;
      font-family: 'Outfit', sans-serif;
      margin-top: 8px;
    }
    .btn-ping:hover { background: #7dd3fc; }
    footer {
      text-align: center;
      font-size: 13px;
      color: var(--muted);
      margin-top: 25px;
    }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div class="pill"><span class="pulse-dot"></span> Render Cloud Relay Active</div>
      <h1>GoQuiz Adventure</h1>
      <p class="subtitle">Real-time Cross-Platform Multiplayer Gateway & Global Leaderboard for Android and Desktop.</p>
      <div style="margin-top: 12px;">${dbBadge}</div>
    </header>

    <div class="metrics-grid">
      <div class="metric-card">
        <div class="metric-label">👥 Players Online</div>
        <div class="metric-value">${activeSessions.size}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">⚔️ Active Rooms</div>
        <div class="metric-value">${rooms.size}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">🏆 Total Scores</div>
        <div class="metric-value">${leaderboard.size}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">🔥 Duels Completed</div>
        <div class="metric-value">${totalDuelsCompleted}</div>
      </div>
    </div>

    <div class="main-grid">
      <!-- LEADERBOARD PANEL -->
      <div class="panel">
        <div class="panel-title">
          <span>👑 Global Hall of Fame</span>
          <span style="font-size: 12px; color: var(--muted); font-weight: normal;">Top Champions</span>
        </div>
        <table>
          <thead>
            <tr>
              <th style="width: 50px;">Rank</th>
              <th>Player</th>
              <th style="text-align: right;">Highest Score</th>
            </tr>
          </thead>
          <tbody>
            ${leaderboardRows}
          </tbody>
        </table>
      </div>

      <!-- ACTIVE ROOMS & TEST PANEL -->
      <div class="panel">
        <div class="panel-title">
          <span>⚔️ Live Rooms</span>
          <span style="font-size: 12px; color: var(--muted); font-weight: normal;">${openRooms.length} Active</span>
        </div>
        <div class="rooms-container">
          ${roomCards}
        </div>

        <div style="margin-top: 24px;">
          <div class="panel-title" style="margin-bottom: 8px;">
            <span>📡 Live WebSocket Tester</span>
          </div>
          <p style="font-size: 13px; color: var(--muted); margin-bottom: 8px;">
            Test your real-time WebSocket connection to this server:
          </p>
          <div class="test-box" id="test-console">Connecting WebSocket...</div>
          <button class="btn-ping" onclick="pingServer()">Send Ping</button>
        </div>

        <div style="margin-top: 24px; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 16px;">
          <div class="panel-title" style="margin-bottom: 8px;">
            <span>⏰ Render Anti-Sleep Cron API</span>
          </div>
          <p style="font-size: 13px; color: var(--muted); margin-bottom: 8px;">
            Use this endpoint in free cron tools (e.g. <a href="https://cron-job.org" target="_blank" style="color:#38bdf8; text-decoration:none;">cron-job.org</a> or <a href="https://uptimerobot.com" target="_blank" style="color:#38bdf8; text-decoration:none;">UptimeRobot</a>) every 10 mins to keep Render awake 24/7:
          </p>
          <div class="test-box" style="color: #38bdf8; font-weight: bold;" id="cron-url">/api/keep-alive</div>
          <button class="btn-ping" style="background:#10b981; color:#fff;" onclick="testKeepAlive()">⚡ Test Keep-Alive API</button>
          <div class="test-box" id="cron-console" style="display:none; margin-top:8px;"></div>
        </div>
      </div>
    </div>

    <footer>
      GoQuiz Adventure v1.2.1 • Powered by Node.js, Express, TypeScript & Bun • Hosted on Render Cloud
    </footer>
  </div>

  <script>
    const logBox = document.getElementById('test-console');
    const cronConsole = document.getElementById('cron-console');
    document.getElementById('cron-url').innerText = window.location.origin + '/api/keep-alive';

    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = proto + '//' + window.location.host;
    let ws;

    function initWs() {
      logBox.innerText = 'Connecting to ' + wsUrl + '...';
      ws = new WebSocket(wsUrl);
      ws.onopen = () => {
        logBox.innerText = '✓ WebSocket Connected! Sending HELLO...';
        ws.send('HELLO|WebTester|Boy');
      };
      ws.onmessage = (e) => {
        logBox.innerText += '\\n← ' + e.data;
      };
      ws.onerror = (e) => {
        logBox.innerText = '✗ Connection error: ' + (e.message || 'Failed');
      };
      ws.onclose = () => {
        logBox.innerText += '\\n○ Connection closed. Reconnecting in 5s...';
        setTimeout(initWs, 5000);
      };
    }
    function pingServer() {
      if (ws && ws.readyState === WebSocket.OPEN) {
        logBox.innerText += '\\n→ PING';
        ws.send('PING');
      } else {
        alert('WebSocket is not connected yet.');
      }
    }
    async function testKeepAlive() {
      cronConsole.style.display = 'block';
      cronConsole.innerText = 'Calling ' + window.location.origin + '/api/keep-alive ...';
      try {
        const res = await fetch('/api/keep-alive');
        const json = await res.json();
        cronConsole.innerText = '✓ Response (HTTP ' + res.status + '):\\n' + JSON.stringify(json, null, 2);
      } catch (err) {
        cronConsole.innerText = '✗ Error: ' + err.message;
      }
    }
    initWs();
  </script>
</body>
</html>`;

  res.type('text/html').send(html);
});

// ==========================================
// CREATE SERVERS (HTTP + WEBSOCKET + TCP)
// ==========================================
const server: HttpServer = createServer(app);

// WebSocket Server attached to HTTP server (Required for Render)
const wss = new WebSocketServer({ server });

wss.on('connection', (ws: WebSocket, req: any) => {
  const ip = (req.headers['x-forwarded-for'] as string) || req.socket.remoteAddress || 'Unknown';
  const session = new ClientSession('ws', ws, ip);
  activeSessions.add(session);
  console.log(`[WS] Client connected from ${ip} (Total active: ${activeSessions.size})`);

  ws.on('message', (data: RawData) => {
    try {
      const text = data.toString();
      session.handleMessage(text);
    } catch (err: any) {
      console.warn(`[WS ${ip}] Message handling error:`, err.message);
    }
  });

  ws.on('close', () => {
    console.log(`[WS] Client disconnected: ${ip}`);
    session.cleanup();
  });

  ws.on('error', (err: Error) => {
    console.warn(`[WS ${ip}] Socket error:`, err.message);
  });
});

// Start HTTP Server
server.listen(HTTP_PORT, () => {
  console.log('====================================================');
  console.log(`  GoQuiz Adventure — Global Multiplayer Cloud Server`);
  console.log('====================================================');
  console.log(`[HTTP/WS] Server listening on port ${HTTP_PORT}`);
  console.log(`[HTTP/WS] Public Dashboard: http://localhost:${HTTP_PORT}`);
  console.log(`[HTTP/WS] WebSocket endpoint: ws://localhost:${HTTP_PORT}`);
  console.log(`[Storage] Mode: ${supabase ? 'Supabase PostgreSQL' : 'Local Disk Backup'}`);
  console.log(`[KeepAlive] Endpoint active: http://localhost:${HTTP_PORT}/api/keep-alive`);

  // Render Anti-Sleep Automated Self-Pinger
  const selfPingTarget = process.env.RENDER_EXTERNAL_URL || process.env.SELF_PING_URL || '';
  if (selfPingTarget) {
    const intervalMinutes = Math.max(3, Math.min(14, parseInt(process.env.SELF_PING_INTERVAL_MINUTES || '10', 10)));
    const pingUrl = `${selfPingTarget.replace(/\/$/, '')}/api/keep-alive`;
    console.log(`[KeepAlive] Automated self-pinger initialized for ${pingUrl} (every ${intervalMinutes} mins)`);

    setInterval(async () => {
      try {
        const res = await fetch(pingUrl, {
          headers: { 'User-Agent': 'GoQuiz-AntiSleep-SelfPinger/1.0' }
        });
        if (res.ok) {
          console.log(`[KeepAlive] Automated self-ping SUCCESS at ${new Date().toLocaleTimeString()} (Server kept awake)`);
        }
      } catch (err: any) {
        console.warn(`[KeepAlive] Automated self-ping error:`, err.message);
      }
    }, intervalMinutes * 60 * 1000);
  }
});

// Optional: Start raw TCP Socket Server if running locally or on a VPS with open TCP ports
if (process.env.ENABLE_TCP !== 'false' && TCP_PORT !== HTTP_PORT) {
  try {
    const tcpServer: TcpServer = net.createServer((socket: TcpSocket) => {
      const ip = `${socket.remoteAddress}:${socket.remotePort}`;
      const session = new ClientSession('tcp', socket, ip);
      activeSessions.add(session);
      console.log(`[TCP] Client connected from ${ip}`);

      let buffer = '';
      socket.on('data', (chunk: Buffer) => {
        buffer += chunk.toString('utf-8');
        const lines = buffer.split(/\r?\n/);
        buffer = lines.pop() || '';
        for (const line of lines) {
          session.handleMessage(line);
        }
      });

      socket.on('close', () => {
        console.log(`[TCP] Client disconnected: ${ip}`);
        session.cleanup();
      });

      socket.on('error', (err: Error) => {
        console.warn(`[TCP ${ip}] Error:`, err.message);
      });
    });

    tcpServer.listen(TCP_PORT, () => {
      console.log(`[TCP] Raw socket server listening on port ${TCP_PORT}`);
    });
  } catch (err: any) {
    console.warn('[TCP] Could not start TCP listener:', err.message);
  }
}
