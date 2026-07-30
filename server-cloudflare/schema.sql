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

-- VATSIM Connect (OAuth2) 로그인 진행 중 상태.
-- state는 CSRF 방지용이며, 콜백에서 한 번 쓰고 지웁니다.
CREATE TABLE IF NOT EXISTS auth_state (
  state      TEXT PRIMARY KEY,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);

-- 로그인으로 확인된 CID와 앱이 들고 다닐 불투명 토큰.
--
-- VATSIM 액세스 토큰을 저장하지 않는 이유:
-- 한 번 CID를 확인하고 나면 다시 쓸 일이 없습니다. 계속 들고 있으면
-- 유출 시 그 사람의 VATSIM 계정 정보까지 읽을 수 있는 물건이 됩니다.
CREATE TABLE IF NOT EXISTS auth_link (
  token      TEXT PRIMARY KEY,
  cid        TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS index_auth_state_expires ON auth_state(expires_at);
CREATE INDEX IF NOT EXISTS index_auth_link_cid ON auth_link(cid);
