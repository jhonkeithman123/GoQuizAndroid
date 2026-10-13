-- ====================================================
-- GoQuiz Adventure — Supabase Database Schema
-- Paste this script into your Supabase project's SQL Editor
-- ====================================================

-- 1. Create Leaderboard Table
CREATE TABLE IF NOT EXISTS leaderboard (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_name TEXT NOT NULL UNIQUE,
    score INTEGER NOT NULL DEFAULT 0,
    gender TEXT DEFAULT 'Unknown',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for fast ranking and leaderboard lookups
CREATE INDEX IF NOT EXISTS idx_leaderboard_score ON leaderboard (score DESC, player_name ASC);

-- 2. Create Match History Table (Audit Log for 1v1 Duels)
CREATE TABLE IF NOT EXISTS match_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_code TEXT NOT NULL,
    host_name TEXT NOT NULL,
    guest_name TEXT NOT NULL,
    winner_name TEXT,
    host_score INTEGER DEFAULT 0,
    guest_score INTEGER DEFAULT 0,
    language TEXT NOT NULL,
    difficulty TEXT NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_match_history_finished_at ON match_history (finished_at DESC);

-- 3. Row Level Security (RLS) Configuration
ALTER TABLE leaderboard ENABLE ROW LEVEL SECURITY;
ALTER TABLE match_history ENABLE ROW LEVEL SECURITY;

-- Allow public read access to leaderboard and match history
CREATE POLICY "Public Read Leaderboard" 
ON leaderboard FOR SELECT 
USING (true);

CREATE POLICY "Public Read Match History" 
ON match_history FOR SELECT 
USING (true);

-- Allow service role or authenticated backend to insert/update
CREATE POLICY "Backend Upsert Leaderboard" 
ON leaderboard FOR ALL 
USING (true)
WITH CHECK (true);

CREATE POLICY "Backend Insert Match History" 
ON match_history FOR ALL 
USING (true)
WITH CHECK (true);

-- Insert initial admin/starter high scores (optional)
INSERT INTO leaderboard (player_name, score, gender)
VALUES 
    ('131fgh', 40, 'Boy')
ON CONFLICT (player_name) 
DO UPDATE SET score = GREATEST(leaderboard.score, EXCLUDED.score);
