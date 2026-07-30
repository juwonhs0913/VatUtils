/**
 * 챌린지 완주 감시 (서버 판정).
 *
 * 앱을 비행 중에 한 번도 켜지 않는다는 전제로 만들었습니다.
 * 기기에서 15분 주기 작업을 돌리는 방법은, 앱을 오래 열지 않으면 Android가
 * 해당 앱을 restricted 버킷에 넣어 하루 한 번 수준까지 미루기 때문에 믿을 수 없습니다.
 *
 * 그래서 이미 1분마다 도는 이 Worker가 대신 관찰합니다.
 * 판정 규칙은 앱의 FlightVerifier와 동일하게 유지해야 합니다.
 */

const MEMBER_STATS_URL = (cid) => `https://api.vatsim.net/v2/members/${cid}/stats`;

// 앱의 FlightVerifier와 같은 값이어야 합니다.
const ARRIVAL_RADIUS_NM = 8;
const ARRIVAL_MAX_GROUND_SPEED_KT = 40;
const ARRIVAL_MAX_ALTITUDE_AGL_FT = 2000;
const MIN_HOURS_DELTA = 0.2;

/**
 * 앱이 경로를 뽑을 때 호출합니다.
 *
 * cid는 호출부(index.js의 resolveCid)가 정해서 넘깁니다. 여기서 body.cid를
 * 읽으면 안 됩니다 — 로그인으로 확인한 CID를 덮어쓰는 구멍이 됩니다.
 */
export async function registerWatch(env, body, cid) {
  const challengeId = Number(body.challengeId);
  if (!/^\d{6,10}$/.test(cid) || !Number.isInteger(challengeId)) {
    return { ok: false, error: 'invalid cid or challengeId' };
  }

  // 한 사람이 동시에 들고 있을 수 있는 감시는 하루 뽑기 한도와 같습니다.
  // 무한정 쌓여 저장소를 채우는 걸 막습니다.
  const { count } = await env.DB.prepare(
    'SELECT COUNT(*) AS count FROM flight_watch WHERE cid = ? AND expires_at > ?'
  ).bind(cid, Date.now()).first();
  if (count >= 5) {
    return { ok: false, error: 'too many active watches' };
  }

  await env.DB.prepare(
    `INSERT INTO flight_watch
       (cid, challenge_id, origin, destination, arr_lat, arr_lon, arr_elev_ft,
        baseline_hours, seen_enroute, expires_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
     ON CONFLICT(cid, challenge_id) DO UPDATE SET
       origin = excluded.origin,
       destination = excluded.destination,
       expires_at = excluded.expires_at`
  ).bind(
    cid, challengeId,
    String(body.origin || '').toUpperCase(),
    String(body.destination || '').toUpperCase(),
    Number(body.arrLat), Number(body.arrLon), Number(body.arrElevFt) || 0,
    body.baselineHours == null ? null : Number(body.baselineHours),
    Date.now() + 48 * 60 * 60 * 1000
  ).run();

  return { ok: true };
}

/** 앱이 이미 완주를 확인했거나 챌린지가 사라졌을 때 정리합니다. */
export async function unregisterWatch(env, cid, rawChallengeId) {
  const challengeId = Number(rawChallengeId);
  if (!cid || !Number.isInteger(challengeId)) return { ok: false };

  await env.DB.prepare(
    'DELETE FROM flight_watch WHERE cid = ? AND challenge_id = ?'
  ).bind(cid, challengeId).run();
  return { ok: true };
}

/**
 * 매 분 실행. 활성 감시를 VATSIM 피드와 대조합니다.
 * @param pilots 피드의 pilots 배열
 * @param send   (topic, data) => Promise — FCM 발송 함수
 */
export async function checkFlightWatches(env, pilots, send) {
  await env.DB.prepare('DELETE FROM flight_watch WHERE expires_at < ?')
    .bind(Date.now()).run();

  const { results: watches } = await env.DB.prepare(
    'SELECT * FROM flight_watch'
  ).all();
  if (!watches || watches.length === 0) return 0;

  // CID로 빠르게 찾기 위해 한 번만 인덱싱합니다 (조종사 수천 명 × 감시 수십 건).
  const byCid = new Map();
  for (const p of pilots) byCid.set(String(p.cid), p);

  let completed = 0;
  for (const watch of watches) {
    const pilot = byCid.get(String(watch.cid));

    if (pilot && matchesRoute(pilot, watch)) {
      if (!watch.seen_enroute) {
        await env.DB.prepare(
          'UPDATE flight_watch SET seen_enroute = 1 WHERE cid = ? AND challenge_id = ?'
        ).bind(watch.cid, watch.challenge_id).run();
      }
      if (hasArrived(pilot, watch)) {
        await complete(env, watch, send, 'arrival');
        completed++;
      }
      continue;
    }

    // 피드에 없다 = 접속 종료. 비행 중인 걸 본 적이 있어야만 시간 증가를 인정합니다.
    if (watch.seen_enroute) {
      const hours = await fetchPilotHours(watch.cid);
      if (
        hours != null &&
        watch.baseline_hours != null &&
        hours - watch.baseline_hours >= MIN_HOURS_DELTA
      ) {
        await complete(env, watch, send, 'hours');
        completed++;
      }
    }
  }
  return completed;
}

function matchesRoute(pilot, watch) {
  const plan = pilot.flight_plan;
  if (!plan) return false;
  const dep = String(plan.departure || '').trim().toUpperCase();
  const arr = String(plan.arrival || '').trim().toUpperCase();
  return dep === watch.origin && arr === watch.destination;
}

function hasArrived(pilot, watch) {
  const distance = greatCircleNm(
    pilot.latitude, pilot.longitude, watch.arr_lat, watch.arr_lon
  );
  if (distance > ARRIVAL_RADIUS_NM) return false;
  if ((pilot.groundspeed ?? 0) > ARRIVAL_MAX_GROUND_SPEED_KT) return false;
  const aboveField = (pilot.altitude ?? 0) - watch.arr_elev_ft;
  return aboveField <= ARRIVAL_MAX_ALTITUDE_AGL_FT;
}

async function complete(env, watch, send, reason) {
  // 완주 사실은 앱이 알아야 포인트가 지급됩니다.
  // cid_<CID> 토픽으로 보내면 FCM 토큰을 서버에 저장하지 않아도 됩니다.
  await send(`cid_${watch.cid}`, {
    type: 'challenge_complete',
    challengeId: String(watch.challenge_id),
    origin: watch.origin,
    destination: watch.destination,
  });

  await env.DB.prepare(
    'DELETE FROM flight_watch WHERE cid = ? AND challenge_id = ?'
  ).bind(watch.cid, watch.challenge_id).run();

  console.log(
    `챌린지 완주(${reason}): CID ${watch.cid} ${watch.origin}→${watch.destination}`
  );
}

async function fetchPilotHours(cid) {
  try {
    const response = await fetch(MEMBER_STATS_URL(cid));
    const text = await response.text();   // 본문을 반드시 소비합니다
    if (!response.ok) return null;
    return JSON.parse(text).pilot ?? null;
  } catch {
    return null;
  }
}

function greatCircleNm(lat1, lon1, lat2, lon2) {
  const R = 3440.065;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}
