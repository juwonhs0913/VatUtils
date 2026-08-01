/**
 * 나의 비행 기록.
 *
 * VATSIM은 지난 비행의 출도착 공항을 공개하지 않습니다. members/{cid}/history에는
 * 콜사인과 접속 시각만 있고, 비행계획은 접속이 끊기는 순간 공개 피드에서 사라집니다.
 * 그래서 과거를 복원할 방법이 없고, 직접 관찰해 쌓는 수밖에 없습니다.
 *
 * 다행히 이 Worker는 관제사 알림 때문에 이미 1분마다 피드 전체를 받습니다.
 * 그 안에 pilots[].flight_plan이 들어 있으므로 추가 요청 없이 기록할 수 있습니다.
 */

/** 이 고도를 넘긴 적이 있어야 "비행했다"로 봅니다. 접속만 했다 끊은 세션을 거릅니다. */
const AIRBORNE_ALTITUDE_FT = 10_000;

/** 피드에서 이만큼 사라져 있으면 비행이 끝난 것으로 간주합니다. */
const STALE_MS = 15 * 60 * 1000;

/** 앱이 CID를 저장할 때 부릅니다. */
export async function registerLogbook(env, body) {
  const cid = String(body.cid || '').trim();
  if (!/^\d{6,10}$/.test(cid)) return { ok: false, error: 'invalid cid' };

  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO logbook_watch (cid, created_at, seen_at) VALUES (?, ?, ?)
     ON CONFLICT(cid) DO UPDATE SET seen_at = excluded.seen_at`
  ).bind(cid, now, now).run();

  return { ok: true, since: now };
}

/** 앱이 기록을 읽어 갑니다. */
export async function fetchLogbook(env, cid) {
  if (!/^\d{6,10}$/.test(cid)) return { ok: false, error: 'invalid cid' };

  const watch = await env.DB.prepare(
    'SELECT created_at FROM logbook_watch WHERE cid = ?'
  ).bind(cid).first();

  const { results } = await env.DB.prepare(
    `SELECT callsign, departure, arrival, aircraft, started_at, ended_at, landed, airborne
       FROM logbook_flight
      WHERE cid = ? AND airborne = 1
      ORDER BY started_at DESC
      LIMIT 500`
  ).bind(cid).all();

  return {
    ok: true,
    since: watch ? watch.created_at : null,
    flights: results || [],
  };
}

/**
 * 매 분 실행. 등록된 CID가 피드에 있으면 비행을 잇고, 없으면 닫습니다.
 * @param pilots 피드의 pilots 배열
 */
export async function recordFlights(env, pilots) {
  const { results: watches } = await env.DB.prepare(
    'SELECT cid FROM logbook_watch'
  ).all();
  if (!watches || watches.length === 0) return 0;

  const watched = new Set(watches.map((w) => String(w.cid)));
  const now = Date.now();
  let touched = 0;

  for (const pilot of pilots) {
    const cid = String(pilot.cid);
    if (!watched.has(cid)) continue;

    const plan = pilot.flight_plan;
    if (!plan) continue;
    const departure = String(plan.departure || '').trim().toUpperCase();
    const arrival = String(plan.arrival || '').trim().toUpperCase();
    if (departure.length !== 4 || arrival.length !== 4 || departure === arrival) continue;

    const airborne = (pilot.altitude ?? 0) >= AIRBORNE_ALTITUDE_FT ? 1 : 0;

    // 같은 구간의 열린 비행이 있으면 잇고, 없으면 새로 엽니다.
    const open = await env.DB.prepare(
      `SELECT id, airborne FROM logbook_flight
        WHERE cid = ? AND departure = ? AND arrival = ? AND ended_at IS NULL
        ORDER BY started_at DESC LIMIT 1`
    ).bind(cid, departure, arrival).first();

    if (open) {
      await env.DB.prepare(
        `UPDATE logbook_flight
            SET last_lat = ?, last_lon = ?, airborne = MAX(airborne, ?), callsign = ?
          WHERE id = ?`
      ).bind(pilot.latitude, pilot.longitude, airborne, String(pilot.callsign || ''), open.id).run();
    } else {
      await env.DB.prepare(
        `INSERT INTO logbook_flight
           (cid, callsign, departure, arrival, aircraft, started_at,
            last_lat, last_lon, airborne)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
      ).bind(
        cid, String(pilot.callsign || ''), departure, arrival,
        plan.aircraft_short || null, now,
        pilot.latitude, pilot.longitude, airborne
      ).run();
    }
    touched++;
  }

  // 피드에서 사라진 지 오래된 비행을 닫습니다.
  // 곧바로 닫지 않는 이유는 접속이 잠깐 끊겼다 붙는 경우가 흔하기 때문입니다.
  const onlineCids = new Set(pilots.map((p) => String(p.cid)));
  const { results: openFlights } = await env.DB.prepare(
    'SELECT id, cid, started_at FROM logbook_flight WHERE ended_at IS NULL'
  ).all();

  for (const flight of openFlights || []) {
    if (onlineCids.has(String(flight.cid))) continue;
    if (now - flight.started_at < STALE_MS) continue;
    await env.DB.prepare(
      'UPDATE logbook_flight SET ended_at = ?, landed = 1 WHERE id = ?'
    ).bind(now, flight.id).run();
  }

  return touched;
}
