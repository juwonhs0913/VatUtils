/**
 * VATRadar — 관심 관제소 접속 감지 (Cloudflare Workers)
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

const VATSIM_DATA_URL = 'https://data.vatsim.net/v3/vatsim-data.json';
const OBS_FACILITY = 0;
const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

/** 한 번에 띄우는 FCM 요청 수. Workers의 동시 요청 한도를 넘지 않도록 나눠 보냅니다. */
const SEND_BATCH_SIZE = 6;

export default {
  async scheduled(event, env, ctx) {
    ctx.waitUntil(run(env));
  },

  async fetch(request, env) {
    const url = new URL(request.url);

    try {
      // 앱이 경로를 뽑을 때 감시를 등록합니다.
      if (url.pathname === '/watch' && request.method === 'POST') {
        return Response.json(await registerWatch(env, await request.json()));
      }
      if (url.pathname === '/watch' && request.method === 'DELETE') {
        return Response.json(await unregisterWatch(env, await request.json()));
      }
      // 배포 직후 동작 확인용 수동 실행.
      if (url.pathname === '/run') {
        return Response.json(await run(env));
      }
    } catch (error) {
      return Response.json({ error: String(error) }, { status: 500 });
    }

    return new Response('VATRadar watcher', { status: 200 });
  },
};

async function run(env) {
  const feed = await fetchFeed();
  const online = onlineControllers(feed);
  // 챌린지 완주 감시. 피드를 이미 받았으므로 추가 요청이 없습니다.
  const accessTokenForWatch = await getAccessToken(env);
  const projectIdForWatch = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT).project_id;
  const watchCompleted = await checkFlightWatches(
    env,
    feed.pilots || [],
    (topic, data) => sendToTopic(projectIdForWatch, accessTokenForWatch, topic, data)
  );

  const previous = await loadPreviousState(env);

  // 이번 주기에 새로 뜬 것만 알립니다.
  const newlyOnline = online.filter((callsign) => !previous.has(callsign));

  await saveState(env, online);

  if (newlyOnline.length === 0) {
    return { online: online.length, notified: 0, watchCompleted };
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
  };
}

async function fetchFeed() {
  const response = await fetch(VATSIM_DATA_URL, {
    headers: { 'User-Agent': 'VATRadar/1.0 (controller watcher)' },
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
 * 콜사인 하나가 여러 토픽에 걸립니다.
 * RKSI_TWR → cs_RKSI (공항 전체를 구독한 사람), cs_RKSI_TWR (해당 석만 구독한 사람)
 */
function topicsFor(callsigns) {
  const map = new Map();
  for (const callsign of callsigns) {
    const candidates = new Set([callsign, callsign.split('_')[0]]);
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

/**
 * FCM HTTP v1은 서비스 계정으로 서명한 JWT를 액세스 토큰으로 교환해야 합니다.
 * 토큰은 1시간 유효하지만, 매 실행마다 새로 받아도 비용이 미미하고
 * 캐시를 두면 저장소 쓰기가 늘어나므로 그냥 매번 발급합니다.
 */
async function getAccessToken(env) {
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
