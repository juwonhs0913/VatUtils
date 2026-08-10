/**
 * VATFlight — 관심 관제소 접속 감지 (Cloudflare Workers)
 *
 * 1분마다 VATSIM 데이터 피드를 확인하고, 새로 접속한 관제소를 FCM 토픽으로 푸시합니다.
 * Firebase Cloud Functions와 같은 역할이지만 Cloudflare 무료 플랜에서 돌아갑니다
 * (Cloud Functions는 Blaze 요금제가 필요합니다).
 *
 * 토픽 규칙은 앱의 FcmTopics와 같습니다: 콜사인 접두사마다 cs_<접두사>.
 *   RKSI_TWR 이 뜨면 → cs_RKSI 와 cs_RKSI_TWR 두 토픽에 보냅니다.
 *
 * 상태 저장에 D1을 쓰는 이유:
 * "직전에 누가 접속해 있었는지"를 기억해야 접속이 유지되는 동안 매분 알림이
 * 울리는 걸 막을 수 있습니다. KV는 무료 쓰기가 하루 1,000회라 1분 주기(1,440회)에
 * 모자라지만, D1은 하루 10만 행 쓰기라 여유가 있습니다.
 */

import { registerWatch, unregisterWatch, checkFlightWatches } from './flightWatch.js';
import { registerLogbook, fetchLogbook, recordFlights } from './logbook.js';

const VATSIM_DATA_URL = 'https://data.vatsim.net/v3/vatsim-data.json';
const OBS_FACILITY = 0;
const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

/** 한 번에 띄우는 FCM 요청 수. Workers의 동시 요청 한도를 넘지 않도록 나눠 보냅니다. */
const SEND_BATCH_SIZE = 6;

/**
 * 한 번의 cron 안에서 피드를 몇 번 볼지, 그리고 그 간격(ms).
 *
 * Cloudflare cron은 1분이 최소 단위입니다. 그대로 두면 관제사가 접속한 직후에
 * 켜진 경우 최대 1분을 기다리고, 거기에 VATSIM 피드 자체의 갱신 지연(15초)이
 * 더해져 알림이 80초 넘게 늦습니다.
 *
 * 기다리는 동안에는 CPU를 쓰지 않으므로, 한 번 깨어난 김에 20초 간격으로
 * 세 번 봅니다. 최악의 경우가 60초에서 20초로 줄어듭니다.
 */
const CHECKS_PER_RUN = 3;
const CHECK_INTERVAL_MS = 20_000;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function runRepeatedly(env) {
  for (let i = 0; i < CHECKS_PER_RUN; i++) {
    if (i > 0) await sleep(CHECK_INTERVAL_MS);
    try {
      await run(env);
    } catch (error) {
      // 한 번 실패해도 남은 확인은 계속합니다. 다음 cron까지 1분을 통째로
      // 날리는 것보다 낫습니다.
      console.warn(`주기 확인 실패(${i + 1}/${CHECKS_PER_RUN}): ${error}`);
    }
  }
}

export default {
  async scheduled(event, env, ctx) {
    ctx.waitUntil(runRepeatedly(env));
  },

  async fetch(request, env) {
    const url = new URL(request.url);

    try {
      // 앱이 경로를 뽑을 때 감시를 등록합니다.
      if (url.pathname === '/watch' && request.method === 'POST') {
        const body = await request.json();
        return Response.json(await registerWatch(env, body));
      }
      if (url.pathname === '/watch' && request.method === 'DELETE') {
        return Response.json(await unregisterWatch(env, await request.json()));
      }
      // 나의 비행 기록.
      if (url.pathname === '/logbook' && request.method === 'POST') {
        return Response.json(await registerLogbook(env, await request.json()));
      }
      if (url.pathname === '/logbook' && request.method === 'GET') {
        return Response.json(
          await fetchLogbook(env, String(url.searchParams.get('cid') || '').trim())
        );
      }

      // 실제로 접속한 적이 있는 관제석 목록. 앱의 "목록에서 고르기"가 씁니다.
      if (url.pathname === '/positions' && request.method === 'GET') {
        return positionsResponse(env);
      }

      // 배포 직후 동작 확인용 수동 실행.
      //
      // 저장소를 공개로 돌리면서 잠갔습니다. 한 번 부르면 VATSIM 피드를 받고 FCM을
      // 보내고 D1에 씁니다. 누구나 부를 수 있으면 두들기는 것만으로 무료 한도를 태우고
      // 같은 알림을 반복해서 보낼 수 있습니다. 크론은 이 검사와 무관하게 계속 돕니다.
      //
      // 쓰려면:  npx wrangler secret put RUN_TOKEN
      // 그다음:  curl "https://.../run?token=<값>"
      if (url.pathname === '/run') {
        if (!env.RUN_TOKEN || url.searchParams.get('token') !== env.RUN_TOKEN) {
          return Response.json({ error: 'forbidden' }, { status: 403 });
        }
        return Response.json(await run(env));
      }
    } catch (error) {
      return Response.json({ error: String(error) }, { status: 500 });
    }

    return new Response('VATFlight watcher', { status: 200 });
  },
};

async function run(env) {
  const feed = await fetchFeed();
  const online = onlineControllers(feed);
  // 챌린지 완주 감시. 피드를 이미 받았으므로 추가 요청이 없습니다.
  const accessTokenForWatch = await getAccessToken(env);
  const projectIdForWatch = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT).project_id;
  const pilots = feed.pilots || [];
  const watchCompleted = await checkFlightWatches(
    env,
    pilots,
    (topic, data) => sendToTopic(projectIdForWatch, accessTokenForWatch, topic, data)
  );
  // 나의 비행 기록. 같은 피드를 재사용하므로 추가 요청이 없습니다.
  const logged = await recordFlights(env, pilots);

  const previous = await loadPreviousState(env);

  // 이번 주기에 새로 뜬 것만 알립니다.
  const newlyOnline = online.filter((callsign) => !previous.has(callsign));

  await saveState(env, online);
  const recorded = await recordPositions(env, online, newlyOnline);
  const harvested = await harvestPositions(env, feed.controllers || []);

  if (newlyOnline.length === 0) {
    return { online: online.length, notified: 0, watchCompleted, logged, recorded, harvested };
  }

  const accessToken = accessTokenForWatch;
  const topics = topicsFor(newlyOnline);
  const projectId = projectIdForWatch;

  // 한 번에 너무 많은 요청을 띄우면 Workers의 동시 요청 한도에 걸려
  // 오래된 응답이 취소됩니다. 나눠서 보냅니다.
  const entries = [...topics];
  let failed = 0;
  for (let i = 0; i < entries.length; i += SEND_BATCH_SIZE) {
    const batch = entries.slice(i, i + SEND_BATCH_SIZE);
    const results = await Promise.allSettled(
      batch.map(([topic, callsigns]) =>
        sendToTopic(projectId, accessToken, topic, callsigns)
      )
    );
    for (const r of results) {
      if (r.status === 'rejected') {
        failed++;
        console.warn(String(r.reason));
      }
    }
  }

  console.log(
    `새로 접속한 관제소 ${newlyOnline.length}곳 — 토픽 ${topics.size}개 발송 (실패 ${failed})`
  );

  return {
    online: online.length,
    notified: newlyOnline.length,
    topics: topics.size,
    failed,
    watchCompleted,
    logged,
    recorded,
    harvested,
  };
}

/**
 * 실제로 접속한 적이 있는 관제석을 적어 둡니다.
 *
 * 앱은 이 목록으로 "목록에서 고르기"의 어프로치 후보를 만듭니다. 공항마다
 * <ICAO>_APP을 지어내면 인천 어프로치처럼 있지도 않은 관제석이 뜨는데,
 * 어느 공항에 어느 접근관제가 붙는지를 담은 전 세계 데이터는 없습니다.
 * 피드에 뜬 것만 적으면 그 목록은 정의상 틀릴 수가 없습니다.
 *
 * 매 주기 접속 중인 것을 전부 쓰면 하루 100만 건이 넘어 D1 무료 한도를 넘깁니다.
 * 그래서 **새로 뜬 것만** 씁니다 — 어떤 관제석이든 접속하는 순간 한 번은
 * 여기에 걸리므로 결국 다 모입니다. 표가 비어 있는 첫 회차에만 통째로 담습니다.
 */
async function recordPositions(env, online, newlyOnline) {
  const seeded = await env.DB.prepare('SELECT COUNT(*) AS n FROM positions').first();
  const targets = Number(seeded?.n ?? 0) === 0 ? online : newlyOnline;
  if (targets.length === 0) return 0;

  const now = Math.floor(Date.now() / 1000);
  const statement = env.DB.prepare(
    `INSERT INTO positions (callsign, first_seen, last_seen) VALUES (?, ?, ?)
     ON CONFLICT(callsign) DO UPDATE SET last_seen = excluded.last_seen`
  );
  await env.DB.batch(targets.map((callsign) => statement.bind(callsign, now, now)));
  return targets.length;
}

/**
 * 지금 접속 중인 관제사의 **과거 관제 기록**에서 관제석 이름을 긁어 옵니다.
 *
 * 관찰만으로 목록을 채우면 한국처럼 조용한 시간대의 관제석이 며칠 동안 안 보입니다.
 * VATSIM은 CID별 관제 세션 이력을 공개하고(`/api/ratings/{cid}/atcsessions/`),
 * 거기에는 그 사람이 지금까지 앉았던 **모든** 콜사인이 들어 있습니다. 한국 관제사가
 * 한 번만 접속해도 그 사람이 다녔던 RKSS_APP·RKRR_CTR 같은 자리가 한꺼번에 들어옵니다.
 *
 * 한 주기에 한 명만 조회합니다. VATSIM API를 두드리는 양을 줄이고, D1 무료 플랜의
 * 하루 쓰기 한도(10만 건) 안에 머무르기 위해서입니다. 이미 조회한 CID는 다시 보지 않습니다.
 */
async function harvestPositions(env, controllers) {
  const candidates = controllers
    .filter((c) => c.facility !== OBS_FACILITY && c.cid)
    .map((c) => String(c.cid));
  if (candidates.length === 0) return 0;

  const known = await env.DB.prepare(
    `SELECT cid FROM harvested_cid WHERE cid IN (${candidates.map(() => '?').join(',')})`
  )
    .bind(...candidates)
    .all();
  const seen = new Set((known.results || []).map((row) => row.cid));
  const target = candidates.find((cid) => !seen.has(cid));
  if (!target) return 0;

  const now = Math.floor(Date.now() / 1000);
  // 조회에 실패해도 표시는 남깁니다. 안 그러면 같은 CID만 계속 다시 시도합니다.
  await env.DB.prepare(
    'INSERT OR REPLACE INTO harvested_cid (cid, at) VALUES (?, ?)'
  )
    .bind(target, now)
    .run();

  let callsigns;
  try {
    callsigns = await fetchSessionCallsigns(target);
  } catch (error) {
    console.warn(`관제 이력 조회 실패 (${target}): ${error}`);
    return 0;
  }
  if (callsigns.length === 0) return 0;

  // 이미 있는 것은 건드리지 않습니다 (DO NOTHING이면 쓰기가 0건으로 잡힙니다).
  const statement = env.DB.prepare(
    `INSERT INTO positions (callsign, first_seen, last_seen) VALUES (?, ?, ?)
     ON CONFLICT(callsign) DO NOTHING`
  );
  await env.DB.batch(callsigns.map((c) => statement.bind(c, now, now)));
  return callsigns.length;
}

async function fetchSessionCallsigns(cid) {
  const response = await fetch(
    `https://api.vatsim.net/api/ratings/${encodeURIComponent(cid)}/atcsessions/`,
    { headers: { 'User-Agent': 'VATFlight/1.0 (position registry)' } }
  );
  if (!response.ok) throw new Error(`응답 오류: ${response.status}`);
  const data = await response.json();
  const found = new Set();
  for (const session of data.results || []) {
    const callsign = String(session.callsign || '').toUpperCase();
    // 관찰자와 슈퍼바이저 자리는 관제석이 아닙니다.
    if (!callsign || callsign.endsWith('_OBS') || callsign.endsWith('_SUP')) continue;
    found.add(callsign);
  }
  return [...found];
}

async function positionsResponse(env) {
  const { results } = await env.DB.prepare(
    'SELECT callsign FROM positions ORDER BY callsign'
  ).all();
  return Response.json(
    {
      updated: Math.floor(Date.now() / 1000),
      positions: (results || []).map((row) => row.callsign),
    },
    // 하루에 한 번만 받아 가면 충분합니다. 목록은 천천히 자랍니다.
    { headers: { 'Cache-Control': 'public, max-age=21600' } }
  );
}

async function fetchFeed() {
  const response = await fetch(VATSIM_DATA_URL, {
    headers: { 'User-Agent': 'VATFlight/1.0 (controller watcher)' },
  });
  if (!response.ok) {
    throw new Error(`VATSIM 피드 응답 오류: ${response.status}`);
  }
  return response.json();
}

function onlineControllers(data) {
  // OBS(관찰자)는 실제 관제가 아니므로 제외합니다.
  return (data.controllers || [])
    .filter((c) => c.facility !== OBS_FACILITY)
    .map((c) => String(c.callsign || '').toUpperCase())
    .filter(Boolean);
}

/**
 * 콜사인 하나가 대표할 수 있는 이름들.
 *   RKRR_A_CTR → RKRR_A_CTR, RKRR, RKRR_CTR
 *
 * 가운데 토큰을 걷어낸 형태가 필요한 이유: 사용자는 RKRR_CTR로 등록하는데
 * 실제 접속은 섹터가 나뉘어 RKRR_A_CTR로 들어옵니다. 이게 없으면 알림이 안 갑니다.
 * 앱의 CallsignMatcher와 규칙이 같아야 합니다.
 */
function aliasesFor(callsign) {
  const parts = callsign.split('_').filter(Boolean);
  const aliases = new Set([callsign]);
  if (parts.length > 0) aliases.add(parts[0]);
  if (parts.length >= 3) aliases.add(parts[0] + '_' + parts[parts.length - 1]);
  return aliases;
}

/**
 * 콜사인 하나가 여러 토픽에 걸립니다.
 * RKSI_TWR → cs_RKSI (공항 전체를 구독한 사람), cs_RKSI_TWR (해당 석만 구독한 사람)
 */
function topicsFor(callsigns) {
  const map = new Map();
  for (const callsign of callsigns) {
    const candidates = aliasesFor(callsign);
    for (const raw of candidates) {
      if (!raw) continue;
      const topic = 'cs_' + normalizeTopic(raw);
      if (!map.has(topic)) map.set(topic, []);
      map.get(topic).push(callsign);
    }
  }
  return map;
}

/** FCM 토픽 이름 규칙: [a-zA-Z0-9-_.~%]. 앱의 FcmTopics.normalize와 같아야 합니다. */
function normalizeTopic(value) {
  return value.trim().toUpperCase().replace(/[^A-Z0-9_.~%-]/g, '_');
}

// ---------------------------------------------------------------- 상태 (D1)

async function loadPreviousState(env) {
  const row = await env.DB.prepare(
    'SELECT callsigns FROM watch_state WHERE id = 1'
  ).first();
  if (!row || !row.callsigns) return new Set();
  try {
    return new Set(JSON.parse(row.callsigns));
  } catch {
    return new Set();
  }
}

async function saveState(env, online) {
  await env.DB.prepare(
    `INSERT INTO watch_state (id, callsigns, updated_at) VALUES (1, ?, ?)
     ON CONFLICT(id) DO UPDATE SET callsigns = excluded.callsigns, updated_at = excluded.updated_at`
  )
    .bind(JSON.stringify(online), Date.now())
    .run();
}

// ---------------------------------------------------------------- FCM

/** 발급받은 액세스 토큰. 격리 인스턴스가 살아 있는 동안 재사용합니다. */
let cachedToken = null;

/**
 * FCM HTTP v1은 서비스 계정으로 서명한 JWT를 액세스 토큰으로 교환해야 합니다.
 *
 * 토큰은 1시간 유효합니다. 한 번의 cron에서 세 번 확인하도록 바꾸면서 발급도
 * 세 배가 됐는데, 매번 받으면 확인마다 구글 왕복이 하나 더 붙어 알림이 그만큼
 * 늦습니다. 만료 1분 전까지 재사용합니다.
 */
async function getAccessToken(env) {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) {
    return cachedToken.value;
  }
  const token = await requestAccessToken(env);
  cachedToken = { value: token, expiresAt: Date.now() + 3600_000 };
  return token;
}

async function requestAccessToken(env) {
  const credentials = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
  const now = Math.floor(Date.now() / 1000);

  const claim = {
    iss: credentials.client_email,
    scope: FCM_SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600,
  };

  const header = { alg: 'RS256', typ: 'JWT' };
  const unsigned =
    base64UrlEncode(JSON.stringify(header)) + '.' + base64UrlEncode(JSON.stringify(claim));

  const key = await importPrivateKey(credentials.private_key);
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(unsigned)
  );
  const jwt = unsigned + '.' + base64UrlEncodeBytes(new Uint8Array(signature));

  const response = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }),
  });

  if (!response.ok) {
    throw new Error(`액세스 토큰 발급 실패: ${response.status} ${await response.text()}`);
  }
  return (await response.json()).access_token;
}

async function importPrivateKey(pem) {
  // PEM 헤더/푸터와 줄바꿈을 걷어내고 DER 바이트로 만듭니다.
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s+/g, '');
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));

  return crypto.subtle.importKey(
    'pkcs8',
    der,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
}

async function sendToTopic(projectId, accessToken, topic, payload) {
  // 관제사 알림은 콜사인 배열, 챌린지 완주는 객체를 넘깁니다.
  const data = Array.isArray(payload)
    ? { callsigns: payload.join(',') }
    : payload;
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        message: {
          topic,
          // 알림 문구는 앱이 사용자 언어로 만들도록 data 메시지만 보냅니다.
          data,
          android: { priority: 'high' },
        },
      }),
    }
  );

  // 성공이든 실패든 본문을 반드시 소비해야 합니다.
  // 읽지 않은 응답이 쌓이면 Workers가 동시 요청 한도 때문에 오래된 것을 취소해,
  // 발송이 조용히 누락됩니다.
  const text = await response.text();

  if (!response.ok) {
    // 구독자가 없는 토픽은 정상적인 상황입니다.
    if (response.status === 404 || text.includes('NOT_FOUND')) return;
    throw new Error(`토픽 ${topic} 전송 실패: ${response.status} ${text}`);
  }
}

// ---------------------------------------------------------------- 인코딩

function base64UrlEncode(str) {
  return base64UrlEncodeBytes(new TextEncoder().encode(str));
}

function base64UrlEncodeBytes(bytes) {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
