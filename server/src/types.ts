import type { WebSocket } from 'ws';
import type { Socket as TcpSocket } from 'net';

// Leaderboard Record
export interface LeaderboardEntry {
  score: number;
  gender: string;
  updatedAt: string;
}

export interface SortedLeaderboardItem {
  rank?: number;
  name: string;
  score: number;
  gender: string;
}

// Database schema types for Supabase
export interface DbLeaderboardRow {
  id?: string;
  player_name: string;
  score: number;
  gender?: string;
  updated_at?: string;
}

export interface DbMatchHistoryRow {
  id?: string;
  room_code: string;
  host_name: string;
  guest_name: string;
  winner_name: string;
  host_score: number;
  guest_score: number;
  language: string;
  difficulty: string;
  finished_at?: string;
}

// Client Session Abstraction (WebSocket or raw TCP)
export type SessionTransport = 'ws' | 'tcp';

export interface IClientSession {
  type: SessionTransport;
  socket: WebSocket | TcpSocket;
  remoteAddress: string;
  playerName: string;
  playerGender: string;
  currentRoom: IDuelRoom | null;
  connectedAt: number;
  lastPing: number;

  getStatus(): string;
  send(message: string): void;
  close(): void;
  cleanup(): void;
  handleMessage(line: string): void;
}

// Duel Room Interface
export interface IDuelRoom {
  code: string;
  hostName: string;
  hostGender: string;
  language: string;
  difficulty: string;
  seed: number;
  questionsPayload: string;
  hostSession: IClientSession | null;
  guestSession: IClientSession | null;
  guestName: string;
  guestGender: string;
  hostScore: number;
  guestScore: number;
  active: boolean;
  finished: boolean;
  createdAt: number;
  hostEvents: string[];
  guestEvents: string[];
  hostPollResolver?: ((events: string[]) => void) | null;
  guestPollResolver?: ((events: string[]) => void) | null;
  lastHostPing?: number;
  lastGuestPing?: number;
  hostClientId?: string;
  guestClientId?: string;
  state?: 'waiting' | 'ready_check' | 'active' | 'finished';
  hostReady?: boolean;
  guestReady?: boolean;
  readyCheckExpiresAt?: number;
  pushEvent(role: 'host' | 'guest', event: string): void;
}

export interface RoomSummary {
  code: string;
  hostName: string;
  hostGender: string;
  language: string;
  difficulty: string;
  active: boolean;
  waiting: boolean;
  createdAt: number;
}

// Keep-Alive / Health Metrics
export interface KeepAliveResponse {
  status: 'awake';
  message: string;
  pingCount: number;
  uptimeSeconds: number;
  database: string;
  activeRooms: number;
  connectedPlayers: number;
  timestamp: string;
}
