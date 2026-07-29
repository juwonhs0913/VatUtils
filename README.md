# VATRadar

VATSIM 올인원 컴패니언 앱 (Android / Kotlin / Jetpack Compose).

실시간 관제·트래픽 확인, 무작위 비행 경로 생성과 SimBrief 연동, 기상 정보 확인을 한 앱에서 제공합니다.

## 구현 상태

| ID | 기능 | 상태 |
|---|---|---|
| F1 | VATSIM 공식 이벤트 캘린더 | ✅ 배너·참여 공항·KST 시간, 진행중/예정 탭 분리 |
| F2 | 실시간 트래픽·관제사 라이브 맵 | ✅ VATSpy FIR 관제 구역 폴리곤, 클러스터링, 기수 방향 반영, 바텀시트, 15초 갱신 |
| F3 | 무작위 출도착 공항 생성기 | ✅ 대륙·국가·**최소 활주로 길이** 필터 (공항 13,007곳) |
| F4 | 관심 관제소 푸시 알림 | ⚠️ 앱 단독 폴링은 즉시 동작, FCM 즉시 알림은 배포 필요 (아래 참고) |
| F5 | SimBrief 원클릭 OFP | ✅ 디스패치 연동 + OFP 요약 + PDF 링크 |
| F6 | METAR / TAF 디코딩 뷰어 | ✅ 원문 + 한국어 디코딩 + 아이콘 + 비행규칙(VFR/IFR) 배지 |

## 빌드 환경 (버전이 서로 묶여 있으니 함께 올려야 합니다)

| 항목 | 버전 |
|---|---|
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.2 |
| Kotlin | 2.0.20 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| JDK | 17 |

> AGP 8.7.2는 Gradle 8.9 기준입니다. Gradle 9로 올리려면 AGP도 8.11 이상으로 함께 올려야 합니다.
> Kotlin 2.0부터 Compose 사용 시 `org.jetbrains.kotlin.plugin.compose` 플러그인이 필수입니다.

## 시작하기

1. Android Studio에서 `VatRadar/` 폴더를 **Open**
2. `local.properties.example`을 `local.properties`로 복사하고 값을 채웁니다.
   ```
   sdk.dir=C:\\Users\\<이름>\\AppData\\Local\\Android\\Sdk
   MAPS_API_KEY=<Google Maps Platform API 키>
   ```
   - **`MAPS_API_KEY`가 유효하지 않으면 지도 타일이 회색으로만 표시됩니다.** 앱은 정상 실행되고 나머지 기능도 동작하지만, F2를 제대로 보려면 실제 키가 필요합니다.
   - Google Cloud Console에서 **Maps SDK for Android**를 활성화한 뒤 키를 발급하세요.
   - `local.properties`는 `.gitignore`에 있습니다. **절대 커밋하지 마세요.**
3. Gradle Sync 후 실행

첫 실행 시 assets의 공항 CSV를 Room DB로 적재합니다(약 13,000행, 1~2초). 이후 실행에는 영향이 없습니다.

## 아키텍처

```
data/local          Room — 전 세계 공항 DB + CSV 시더
data/prefs          DataStore — SimBrief ID, 관심 관제소, 필터 설정
data/remote         Retrofit 서비스 4종 + DTO
data/repository     DTO → Domain 변환, 예외 처리
di                  NetworkModule / ServiceLocator (수동 DI)
domain/metar        METAR 디코더 (외부 라이브러리 없이 직접 구현)
domain/model        UI 친화적 모델
notification        WorkManager 폴링 + FCM 수신 + 토픽 관리
ui/{map,route,events,settings}   화면별 Compose + ViewModel
```

MVVM + 단방향 데이터 흐름. 모든 화면이 `StateFlow<UiState>`를 노출하고 Compose가 구독합니다.

### 사용하는 외부 API

| 용도 | 엔드포인트 | 인증 |
|---|---|---|
| 트래픽/관제 | `data.vatsim.net/v3/vatsim-data.json` | 불필요 |
| 이벤트 | `my.vatsim.net/api/v2/events/latest` | 불필요 |
| METAR | `metar.vatsim.net/{ICAO}` | 불필요 |
| TAF | `aviationweather.gov/api/data/taf` | 불필요 |
| OFP | `simbrief.com/api/xml.fetcher.php` | 사용자 SimBrief ID |
| 지도 | Google Maps SDK | `MAPS_API_KEY` 필요 |

## F4 푸시 알림에 대해

PRD는 FCM + Cloud Functions를 명시하고 있고, 그게 맞습니다. Android의 Doze 제약 때문에 앱 단독 주기 작업은 **최소 15분 간격**이라 "관제소가 방금 떴다"는 알림으로는 늦습니다.

앱은 두 경로를 모두 지원합니다.

- **기본 (설정 불필요)** — `ControllerWatchWorker`가 15분마다 확인합니다. 지금 바로 동작합니다.
- **즉시 알림 (배포 필요)** — `server/functions/index.js`가 1분마다 확인해 FCM으로 푸시합니다.

### 즉시 알림 켜기

Gradle 쪽은 이미 준비돼 있습니다. `app/google-services.json`이 **있을 때만** 플러그인이 적용되므로,
파일을 넣기 전에도 빌드는 그대로 통과하고 넣는 순간 FCM이 켜집니다.

**1. Firebase 콘솔에서 Android 앱 등록**

프로젝트 → 앱 추가 → Android. 패키지 이름은 정확히 이 값이어야 합니다:

```
com.vatradar.app
```

SHA-1은 FCM에 필요 없으므로 비워도 됩니다. 생성 후 `google-services.json`을 받아 `app/` 아래에 둡니다
(`.gitignore`에 있으니 커밋되지 않습니다).

**2. 요금제와 서비스 확인**

- **Blaze(종량제) 전환이 필요합니다.** 예약 함수(Cloud Scheduler)와 외부 네트워크 호출은 무료 Spark 요금제에서 동작하지 않습니다. 1분 주기 함수 하나는 무료 할당량 안에 들어가는 수준이지만, 예산 알림을 걸어두시길 권합니다.
- **Firestore를 활성화**합니다(아무 위치나 무방). 함수가 "직전에 누가 접속해 있었는지"를 여기 한 문서에 기록해, 접속이 유지되는 동안 매분 알림이 울리는 걸 막습니다.

**3. 서버 배포**

```bash
cd server && npm install && firebase login && firebase use --add && firebase deploy --only functions,firestore:rules
```

`firebase use --add`에서 방금 만든 프로젝트를 고르면 `.firebaserc`가 생성됩니다(이 파일도 커밋 대상이 아닙니다).

**4. 앱 재설치 후 확인**

앱을 다시 설치하고 알림 페이지에서 관심 관제소를 등록하면 `cs_<접두사>` 토픽을 구독합니다.
Firebase 설정 전에 등록해 둔 관제소도 앱 시작 시 다시 구독되므로 따로 지웠다 넣을 필요는 없습니다.

동작 확인은 Firebase 콘솔 → Functions → 로그에서 `새로 접속한 관제소 N곳 알림 전송`을 보면 됩니다.

설정하지 않아도 앱은 정상 동작합니다. Firebase가 초기화되지 않으면 FCM 코드는 조용히 비활성화되고 15분 폴링만 남습니다.

## 검증

```bash
./gradlew testDebugUnitTest
```

단위 테스트 38개 — VATSIM JSON 파싱 방어(비행계획 없는 조종사, 필드 누락, 좌표 0,0, 스키마 변경), METAR 디코딩(바람·돌풍·시정·구름·기상현상·기압·CAVOK·영하 기온·추이 분리), 비행규칙 분류, UTC→KST 변환.

```bash
./gradlew connectedDebugAndroidTest
```

계측 테스트 8개 (에뮬레이터/실기기 필요) — VATSpy FIR 매칭(인천 FIR 좌표 범위, 섹터 매칭, 상위 FIR 폴백, 주요 FIR 5곳), 공항 DB 시딩, F3 필터 준수 검증.

### 에뮬레이터 실측 (Pixel 7 / API 37)

- 실시간 데이터: 항공기 1,441대 · 관제사 93명 수신, FIR 관제 구역 10곳 매칭
- 이벤트: 241건 (실제 API 집계와 일치)
- F3 필터: 8,000ft + 포장 조건에서 2,397곳 (전처리 산출값과 일치)
- 공항 DB 시딩: 6.2초 (13,007행)

## 관제 구역 표시 방식 (F2)

VATSIM 데이터 피드는 관제사 좌표를 제공하지 않습니다. 그래서 콜사인에서 위치를 역추적합니다.

- **광역 관제(CTR/FSS)** — VATSpy FIR 경계 폴리곤으로 구역 전체를 칠합니다.
  `RKRR_CTR` → FIR `RKRR`, `RKRR_N_CTR` → 섹터 `RKRR-N`(없으면 상위 FIR로 폴백),
  `AFRE_CTR` 같은 UIR은 소속 FIR 폴리곤을 모두 합칩니다.
- **공항 관제(TWR/GND/DEL/APP)** — 공항 DB에서 좌표를 찾아 시설별 색 마커로 표시합니다.

경계 데이터는 VATSpy `Boundaries.geojson`(2MB, 1,097개)을 Douglas-Peucker로 단순화해
610KB로 줄여 넣었습니다(좌표 67% 감소, FIR 스케일에서는 시각 차이 없음).
파싱은 실제 접속 중인 관제소 것만 지연 수행하고 캐시합니다.

## 알려진 제약

- **SimBrief는 OFP를 서버에서 직접 생성할 수 없습니다.** 공개 API가 조회(`xml.fetcher.php`)만 제공하기 때문에, 디스패치 페이지를 파라미터로 채워 열고 사용자가 Generate를 누른 뒤 결과를 가져오는 표준 연동 흐름을 씁니다.
- 공항 DB는 OurAirports 스냅샷(2026-07 기준)입니다. 갱신하려면 원본 CSV를 다시 받아 가공해야 합니다.
- Compose 전용이라 View 기반 `com.google.android.material` 의존성이 없습니다. XML 테마는 플랫폼 테마를 쓰고, 실제 색상은 Compose `MaterialTheme`(Android 12+ 다이내믹 컬러)가 담당합니다.

## 데이터 출처

- 공항/활주로: [OurAirports](https://ourairports.com/data/) (퍼블릭 도메인)
- 트래픽/이벤트: VATSIM
- TAF: NOAA Aviation Weather Center
