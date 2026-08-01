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


-- 나의 비행 기록.
--
-- VATSIM은 과거 비행의 출도착 공항을 공개하지 않습니다 (members/{cid}/history에는
-- 콜사인과 시각만 있습니다). 그래서 이미 1분마다 받는 피드에서 직접 기록합니다.
-- 백필은 불가능하고, 등록한 시점부터만 쌓입니다.
CREATE TABLE IF NOT EXISTS logbook_watch (
  cid        TEXT PRIMARY KEY,
  created_at INTEGER NOT NULL,
  seen_at    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS logbook_flight (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  cid         TEXT    NOT NULL,
  callsign    TEXT    NOT NULL,
  departure   TEXT    NOT NULL,
  arrival     TEXT    NOT NULL,
  aircraft    TEXT,
  started_at  INTEGER NOT NULL,
  ended_at    INTEGER,
  -- 마지막으로 본 위치. 도착 판정과, 앱이 가장 가까운 공항을 찾는 데 씁니다.
  last_lat    REAL,
  last_lon    REAL,
  landed      INTEGER NOT NULL DEFAULT 0,
  -- 순항 고도를 한 번이라도 넘겼는지. 접속만 했다 끊은 세션을 걸러냅니다.
  airborne    INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS index_logbook_flight_cid ON logbook_flight(cid, started_at);
CREATE UNIQUE INDEX IF NOT EXISTS index_logbook_open
  ON logbook_flight(cid, departure, arrival, started_at);
