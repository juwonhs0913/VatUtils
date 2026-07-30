/**
 * VATSIM Connect (OAuth2) — 서버가 클라이언트 역할을 합니다.
 *
 * 왜 앱이 직접 하지 않는가:
 * VATSIM Connect는 아직 PKCE를 지원하지 않아 client_secret이 필요한
 * confidential client만 됩니다. 시크릿을 APK에 넣으면 누구나 꺼낼 수 있으니
 * 그건 시크릿이 아닙니다. 그래서 이 Worker가 대신 클라이언트가 되고,
 * 앱에는 이 서버만 아는 불투명 토큰을 줍니다.
 *
 * 흐름:
 *   앱 → /auth/start → VATSIM 로그인 → /auth/callback
 *     → (서버가 code를 토큰으로 교환하고 CID를 읽음)
 *     → vatradar://auth?token=... 로 앱에 돌려줌
 *
 * 이게 부정 방지의 핵심입니다. 감시 등록에서 CID를 앱이 고르는 게 아니라
 * 서버가 이 토큰을 CID로 바꿔 씁니다. 앱이 뭘 보내든 남의 CID로는 못 겁니다.
 */

const LINK_SCHEME = 'vatradar://auth';
const STATE_TTL_MS = 10 * 60 * 1000;

/** 로그인 시작. 앱이 브라우저(Custom Tab)로 이 주소를 엽니다. */
export async function authStart(env, url) {
  const config = readConfig(env);
  if (!config) return htmlError('VATSIM Connect가 아직 설정되지 않았습니다.');

  const state = randomToken();
  await env.DB.prepare(
    'INSERT INTO auth_state (state, created_at, expires_at) VALUES (?, ?, ?)'
  ).bind(state, Date.now(), Date.now() + STATE_TTL_MS).run();

  const authorize = new URL(`${config.base}/oauth/authorize`);
  authorize.searchParams.set('client_id', config.clientId);
  authorize.searchParams.set('redirect_uri', redirectUri(url));
  authorize.searchParams.set('response_type', 'code');
  // 필요한 건 CID뿐입니다. CID는 어떤 scope에도 속하지 않는 기본 정보라
  // 이름·이메일까지 달라고 할 이유가 없습니다.
  authorize.searchParams.set('scope', '');
  authorize.searchParams.set('state', state);

  return Response.redirect(authorize.toString(), 302);
}

/** VATSIM이 사용자를 여기로 돌려보냅니다. */
export async function authCallback(env, url) {
  const config = readConfig(env);
  if (!config) return htmlError('VATSIM Connect가 아직 설정되지 않았습니다.');

  if (url.searchParams.get('error')) {
    return htmlError('로그인이 취소되었습니다.');
  }

  const code = url.searchParams.get('code');
  const state = url.searchParams.get('state');
  if (!code || !state) return htmlError('잘못된 응답입니다.');

  // state는 한 번만 유효합니다. 지운 행 수로 판정하면 경쟁 조건에서도 안전합니다.
  await env.DB.prepare('DELETE FROM auth_state WHERE expires_at < ?')
    .bind(Date.now()).run();
  const consumed = await env.DB.prepare('DELETE FROM auth_state WHERE state = ?')
    .bind(state).run();
  if (!consumed.meta || consumed.meta.changes === 0) {
    return htmlError('로그인 요청이 만료되었습니다. 다시 시도해 주세요.');
  }

  let cid;
  try {
    const accessToken = await exchangeCode(config, code, redirectUri(url));
    cid = await fetchCid(config, accessToken);
  } catch (error) {
    console.warn(`VATSIM 로그인 실패: ${error}`);
    return htmlError('VATSIM 인증에 실패했습니다.');
  }

  if (!/^\d{6,10}$/.test(cid)) return htmlError('CID를 읽지 못했습니다.');

  // 같은 사람이 다시 로그인하면 이전 토큰은 무효가 됩니다
  // (기기를 잃어버렸을 때 재로그인이 곧 회수 수단이 됩니다).
  await env.DB.prepare('DELETE FROM auth_link WHERE cid = ?').bind(cid).run();

  const token = randomToken();
  await env.DB.prepare(
    'INSERT INTO auth_link (token, cid, created_at) VALUES (?, ?, ?)'
  ).bind(token, cid, Date.now()).run();

  // 앱으로 돌아갑니다. 토큰이 주소창에 남지만 커스텀 스킴이라
  // 브라우저 이력에만 남고 외부로 나가지 않습니다.
  return Response.redirect(
    `${LINK_SCHEME}?token=${encodeURIComponent(token)}&cid=${cid}`,
    302
  );
}

/**
 * 앱이 보낸 토큰을 CID로 바꿉니다.
 * 감시 등록은 반드시 이 함수를 거쳐야 합니다.
 */
export async function cidForToken(env, token) {
  if (!token) return null;
  const row = await env.DB.prepare('SELECT cid FROM auth_link WHERE token = ?')
    .bind(String(token)).first();
  return row ? row.cid : null;
}

/** 앱에서 연결을 끊을 때. */
export async function authRevoke(env, body) {
  const token = String(body.token || '');
  if (!token) return { ok: false };
  await env.DB.prepare('DELETE FROM auth_link WHERE token = ?').bind(token).run();
  return { ok: true };
}

/** 설정이 들어와 있으면 감시 등록에 로그인을 요구합니다. */
export function isAuthConfigured(env) {
  return readConfig(env) != null;
}

// ---------------------------------------------------------------- 내부

function readConfig(env) {
  if (!env.VATSIM_CLIENT_ID || !env.VATSIM_CLIENT_SECRET) return null;
  return {
    clientId: env.VATSIM_CLIENT_ID,
    clientSecret: env.VATSIM_CLIENT_SECRET,
    // 승인 전에는 샌드박스(auth-dev)로 붙여 볼 수 있습니다.
    base: (env.VATSIM_AUTH_BASE || 'https://auth.vatsim.net').replace(/\/$/, ''),
  };
}

/** VATSIM에 등록한 redirect URI와 글자 하나까지 같아야 합니다. */
function redirectUri(url) {
  return `${new URL(url).origin}/auth/callback`;
}

async function exchangeCode(config, code, redirect) {
  const response = await fetch(`${config.base}/oauth/token`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Accept: 'application/json',
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: config.clientId,
      client_secret: config.clientSecret,
      redirect_uri: redirect,
      code,
    }),
  });

  const text = await response.text();   // 본문은 항상 소비합니다
  if (!response.ok) throw new Error(`토큰 교환 실패: ${response.status} ${text}`);

  const token = JSON.parse(text).access_token;
  if (!token) throw new Error('응답에 access_token이 없습니다');
  return token;
}

async function fetchCid(config, accessToken) {
  const response = await fetch(`${config.base}/api/user`, {
    headers: { Authorization: `Bearer ${accessToken}`, Accept: 'application/json' },
  });

  const text = await response.text();
  if (!response.ok) throw new Error(`사용자 조회 실패: ${response.status} ${text}`);

  // { "data": { "cid": "1234567", ... } }
  const payload = JSON.parse(text);
  return String(payload?.data?.cid ?? payload?.cid ?? '').trim();
}

/** 128비트 난수를 16진수로. 토큰과 state 모두 추측이 불가능해야 합니다. */
function randomToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * 실패는 사람이 보는 화면입니다. 앱이 아니라 브라우저 탭에 뜹니다.
 * 원인을 그대로 노출하지 않는 이유는 이 페이지가 공개 주소이기 때문입니다.
 */
function htmlError(message) {
  const escaped = message.replace(/[<>&]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' }[c]));
  return new Response(
    `<!doctype html><meta charset="utf-8">
     <meta name="viewport" content="width=device-width,initial-scale=1">
     <div style="font:16px/1.6 system-ui;padding:2rem;text-align:center">
       <p>${escaped}</p>
       <p style="color:#888">이 창을 닫고 앱으로 돌아가세요.</p>
     </div>`,
    { status: 400, headers: { 'Content-Type': 'text/html; charset=utf-8' } }
  );
}
