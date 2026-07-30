-- 관제사 접속 상태를 한 행에만 기록합니다.
-- 목적은 "직전 주기에 누가 접속해 있었는지"를 아는 것뿐이라 이력은 남기지 않습니다.
CREATE TABLE IF NOT EXISTS watch_state (
  id         INTEGER PRIMARY KEY,
  callsigns  TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
