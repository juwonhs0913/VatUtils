# VATSIM Connect 로그인 설정

앱에 "Sign in with VATSIM" 버튼은 이미 들어가 있지만, **VATSIM에서 OAuth 클라이언트를
승인받기 전까지는 동작하지 않습니다.** 아래는 승인 후 연결하는 방법입니다.

## 왜 앱이 아니라 서버가 로그인을 하는가

VATSIM Connect는 아직 PKCE를 지원하지 않습니다
([vatsimnetwork/discussions#17](https://github.com/orgs/vatsimnetwork/discussions/17) —
"We are working on improvements to Connect that will allow for PKCE. I don't currently
have a timeline"). 즉 `client_secret`이 필요한 confidential client만 가능합니다.

APK에 넣은 시크릿은 누구나 `apktool`로 꺼낼 수 있으므로 시크릿이 아닙니다.
그래서 Cloudflare Worker가 OAuth 클라이언트 역할을 하고, 앱은 서버가 발급한
불투명 토큰만 받습니다.

```
앱 ──Custom Tab──> Worker /auth/start ──> VATSIM 로그인 화면
                                              │
                   Worker /auth/callback <────┘  (code)
                        │  code를 토큰으로 교환 (client_secret 사용)
                        │  /api/user 로 CID 확인
                        ▼
                   vatradar://auth?token=…&cid=…  ──> 앱
```

**이게 부정 방지의 핵심입니다.** 감시를 등록할 때 서버는 앱이 보낸 `cid`를 보지 않고
이 토큰을 CID로 바꿔 씁니다. 남의 CID로는 감시를 걸 수 없습니다.

## 1. 조직과 클라이언트 등록

1. https://auth.vatsim.net 에 로그인
2. https://auth.vatsim.net/manage/new 에서 조직 등록
   — **승인은 즉시 되지 않습니다.** 며칠 걸릴 수 있습니다.
3. 승인 후 Organizations → 해당 조직 → **OAuth clients** → 새 클라이언트 추가
4. Redirect URI에 정확히 이 주소를 넣습니다 (오타 한 글자도 안 됩니다):

```
https://vatradar-watcher.juwonhs2004.workers.dev/auth/callback
```

승인을 기다리는 동안 샌드박스(https://auth-dev.vatsim.net)로 먼저 시험할 수 있습니다.

## 2. Worker에 자격증명 등록

```bash
cd server-cloudflare
npx wrangler secret put VATSIM_CLIENT_ID
npx wrangler secret put VATSIM_CLIENT_SECRET
```

샌드박스로 시험하려면 `wrangler.toml`의 `[vars]`에 다음을 넣습니다:

```toml
VATSIM_AUTH_BASE = "https://auth-dev.vatsim.net"
```

운영으로 옮길 때는 이 줄을 지우면 기본값 `https://auth.vatsim.net`이 쓰입니다.

## 3. 배포

```bash
npx wrangler deploy
```

## 설정하면 달라지는 것

`VATSIM_CLIENT_ID`와 `VATSIM_CLIENT_SECRET`이 **둘 다** 들어오는 순간,
서버는 로그인하지 않은 감시 등록을 거부하기 시작합니다
(`{"ok":false,"error":"vatsim account not linked"}`).

플래그가 따로 없는 이유는, 로그인할 방법이 없는 상태에서 로그인을 요구하면
아무도 챌린지를 못 하기 때문입니다. 설정이 들어오면 느슨한 경로가 자동으로 닫힙니다.

> 이미 진행 중인 챌린지를 들고 있는 사용자는 다시 로그인해야 감시가 등록됩니다.
> 사용자가 늘어난 뒤에 설정을 넣는다면 이 점을 공지하세요.

## 남는 한계

로그인은 **CID가 본인 것임**만 증명합니다. 실제로 비행했는지는 여전히 VATSIM
공개 피드 관측으로 판정하므로, 네트워크에 정상 접속해 실제로 날아야 합니다.
반대로 로그인해도 시뮬레이터에서 순간이동(슬루)하는 것까지는 막지 못합니다.
