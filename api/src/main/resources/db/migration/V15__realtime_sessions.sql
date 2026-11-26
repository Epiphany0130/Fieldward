CREATE TABLE realtime_sessions (
 id uuid PRIMARY KEY,
 account_id bigint NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 start_request text NOT NULL UNIQUE,
 purpose text NOT NULL CHECK (purpose IN ('DIAGNOSIS', 'INTERVIEW', 'SKILLS')),
 mode text NOT NULL CHECK (mode IN ('PHONE', 'VIDEO')),
 status text NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED')),
 revision integer NOT NULL DEFAULT 0,
 summary text NOT NULL DEFAULT '',
 finish_request text,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE realtime_sessions ADD COLUMN segment integer NOT NULL DEFAULT 0;
CREATE TABLE realtime_events (
 event_id text PRIMARY KEY,
 session_id uuid NOT NULL REFERENCES realtime_sessions(id) ON DELETE CASCADE,
 segment integer NOT NULL,
 UNIQUE(session_id, segment)
);
