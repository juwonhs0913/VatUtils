-- 관제사 접속 상태를 한 행에만 기록합니다.
-- 목적은 "직전 주기에 누가 접속해 있었는지"를 아는 것뿐이라 이력은 남기지 않습니다.
CREATE TABLE IF NOT EXISTS watch_state (
  id         INTEGER PRIMARY KEY,
  callsigns  TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);

-- 챌린지 완주 감시.
-- 앱이 경로를 뽑을 때 등록하고, Worker가 1분마다 VATSIM 피드와 대조합니다.
-- 앱이 꺼져 있어도 판정이 되도록 하는 것이 목적입니다.
--
-- FCM 토큰을 저장하지 않는 이유: 완주 알림을 cid_<CID> 토픽으로 보내면
-- 토큰 관리(회전·만료)를 FCM에 맡길 수 있고 서버에 남는 개인 정보가 줄어듭니다.
CREATE TABLE IF NOT EXISTS flight_watch (
  cid            TEXT    NOT NULL,
  challenge_id   INTEGER NOT NULL,
  origin         TEXT    NOT NULL,
  destination    TEXT    NOT NULL,
  arr_lat        REAL    NOT NULL,
  arr_lon        REAL    NOT NULL,
  arr_elev_ft    INTEGER NOT NULL,
  baseline_hours REAL,
  seen_enroute   INTEGER NOT NULL DEFAULT 0,
  expires_at     INTEGER NOT NULL,
  PRIMARY KEY (cid, challenge_id)
);

CREATE INDEX IF NOT EXISTS index_flight_watch_expires ON flight_watch(expires_at);
