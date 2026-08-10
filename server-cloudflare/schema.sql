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


-- 실제로 접속한 적이 있는 관제석.
--
-- "인천 어프로치"는 존재하지 않습니다. 인천과 김포의 접근관제는 서울 어프로치
-- (RKSS_APP) 하나가 맡습니다. 그런데 공항마다 <ICAO>_APP을 만들어 보여 주면
-- 있지도 않은 관제석이 목록에 뜹니다. 어느 공항에 어느 접근관제가 붙는지를 담은
-- 전 세계 데이터는 없습니다 (VATGlasses에도 한국 파일이 없습니다).
--
-- 그래서 만들지 않고 관찰합니다. 1분마다 받는 피드에 뜬 콜사인만 적어 두면,
-- 그 목록은 정의상 "실제로 존재하는 관제석"입니다.
CREATE TABLE IF NOT EXISTS positions (
  callsign   TEXT PRIMARY KEY,
  first_seen INTEGER NOT NULL,
  last_seen  INTEGER NOT NULL
);

-- 관제 이력을 이미 긁어 온 CID. 같은 사람을 매번 다시 조회하지 않으려고 둡니다.
CREATE TABLE IF NOT EXISTS harvested_cid (
  cid TEXT PRIMARY KEY,
  at  INTEGER NOT NULL
);
