# Play Store 배포 체크리스트

현재 상태 요약 (v1.4 기준)

| 항목 | 상태 |
|---|---|
| 릴리스 키스토어 | ✅ 만들었습니다 (`vatradar-release.jks`) |
| AAB 빌드 | ✅ `app/build/outputs/bundle/release/app-release.aab` |
| 앱 아이콘 512×512 | ✅ `docs/store/icon-512.png` |
| 피처 그래픽 1024×500 | ✅ `docs/store/feature-1024.png` |
| 개인정보처리방침 문서 | ✅ 한국어·영어 |
| 저작권·라이선스 고지 | ✅ 앱 안 화면 + `NOTICE.md` |
| 데이터 안전성 답변 | ✅ 아래 표 |
| **Maps API 키 제한** | ❌ **직접 하셔야 합니다** (아래 3번) |
| 개인정보처리방침 호스팅 | ✅ GitHub Pages |

---

## 1. 릴리스 키스토어 ✅

`vatradar-release.jks`를 만들어 두었고 `keystore.properties`에 비밀번호가 들어 있습니다.
둘 다 `.gitignore` 대상이라 저장소에 올라가지 않습니다.

```
별칭(alias) : vatradar
알고리즘    : RSA 4096, SHA384withRSA
유효기간    : 10,000일
SHA-1       : 0B:53:BD:1F:90:16:28:F9:F8:CC:D0:56:8D:0F:CA:28:EB:77:EE:23
SHA-256     : 27:5F:57:97:AD:76:B9:5B:3B:19:96:27:93:7E:01:60:49:F2:F2:AA:35:EF:E0:A0:8C:3D:F1:49:53:48:B4:AE
```

> ⚠️ **지금 바로 백업하세요.** `vatradar-release.jks`와 `keystore.properties` 두 파일을
> 클라우드 등 이 PC가 아닌 곳에 두세요. 잃어버리면 Play에 올린 앱을 다시는 업데이트할 수 없습니다.
> Play Console에서 **Play App Signing**을 함께 켜두면 업로드 키를 분실해도 재발급이 가능합니다.
>
> 비밀번호를 직접 정한 것으로 바꾸고 싶다면 **첫 업로드 전에** 키를 지우고 다시 만들면 됩니다.
> 한 번 업로드한 뒤에는 바꿀 수 없습니다.
>
> 서명 키가 v1.3(디버그 키)과 달라졌으므로, **v1.3을 사이드로드해 둔 기기는 지우고 다시 설치**해야 합니다.

키를 다시 만들려면:

```bash
keytool -genkeypair -v -keystore vatradar-release.jks -alias vatradar -keyalg RSA -keysize 4096 -validity 10000
```

## 2. 빌드 ✅

Play는 APK가 아니라 AAB를 받습니다.

```bash
./gradlew bundleRelease
```

- 결과물: `app/build/outputs/bundle/release/app-release.aab`
- R8 매핑 파일 `app/build/outputs/mapping/release/mapping.txt`를 Play Console에 함께 올리면
  난독화된 크래시 로그가 원래 코드로 복원되어 보입니다.

JDK는 17~21만 됩니다 (Gradle 8.9가 23 이상을 거부합니다).

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew bundleRelease
```

## 3. Maps API 키 제한 ❌ (직접)

지금 키에는 제한이 없어 **누구나 도용해 할당량을 쓸 수 있습니다.**

Google Cloud Console → 사용자 인증 정보 → 해당 키 →

- **애플리케이션 제한**: Android 앱
- 패키지 이름: `com.vatradar.app`
- SHA-1 지문: **두 개를 모두** 등록해야 합니다.
  1. 위 1번의 업로드 키 SHA-1 `0B:53:...:EE:23`
  2. Play App Signing을 켰다면 Play Console → 설정 → 앱 무결성에 표시되는 **앱 서명 키 SHA-1**

  Play가 앱을 재서명하기 때문에, 업로드 키만 등록하면 스토어에서 받은 앱에서 지도가 회색으로 나옵니다.
- **API 제한**: Maps SDK for Android 만 허용

## 4. 개인정보처리방침 URL ✅

저장소를 공개로 돌리고 `docs/`를 GitHub Pages로 띄웠습니다.

| 언어 | Play Console에 넣을 주소 |
|---|---|
| 한국어 | https://juwonhs0913.github.io/VatUtils/privacy-policy.html |
| English | https://juwonhs0913.github.io/VatUtils/privacy-policy.en.html |
| 랜딩 | https://juwonhs0913.github.io/VatUtils/ |

문서를 고치고 `main`에 밀면 몇 분 뒤 자동으로 반영됩니다.

## 5. 데이터 안전성 양식 ✅

코드 기준으로 정확한 답변입니다.

| 질문 | 답변 |
|---|---|
| 데이터를 수집하거나 공유합니까? | **예** (아래 항목만) |
| 개인 정보 (이름·이메일 등) | **수집 안 함** |
| 위치 | **수집 안 함** |
| 앱 활동 | **수집 안 함** |
| 기기 또는 기타 ID | **수집함** — FCM 등록 토큰, VATSIM CID |
| ↳ 수집 목적 | 앱 기능 (알림 전송, 비행 기록) |
| ↳ 필수 여부 | 선택 (알림을 켜거나 CID를 등록한 경우에만) |
| ↳ 제3자 공유 | 아니요 |
| ↳ 전송 중 암호화 | 예 |
| ↳ 삭제 요청 가능 여부 | 예 (앱 삭제, CID 비우기, 알림 끄기) |

SimBrief ID는 사용자가 직접 입력해 SimBrief로 보내는 값이라 "수집"에 해당하지 않습니다.
심사에서 질문이 올 수 있으니 앱 설명에 SimBrief 연동을 명시해 두었습니다.

## 6. 스토어 등록 자산

| 항목 | 요구 사항 | 상태 |
|---|---|---|
| 앱 아이콘 | 512×512 PNG | ✅ `docs/store/icon-512.png` |
| 피처 그래픽 | 1024×500 PNG | ✅ `docs/store/feature-1024.png` |
| 휴대전화 스크린샷 | 최소 2장 | `docs/screenshots/` |
| 짧은 설명 | 80자 이내 | 아래 |
| 자세한 설명 | 4000자 이내 | 아래 |

그래픽은 `tools/StoreGraphics.java`가 만듭니다 (앱 아이콘 도형을 그대로 씁니다).

```bash
javac -d build/tools tools/StoreGraphics.java && java -cp build/tools StoreGraphics
```

### 짧은 설명

```
VATSIM 실시간 관제·트래픽 지도, 무작위 경로 추천, 기상 정보를 한 앱에서.
```

### 자세한 설명

```
VATRadar는 VATSIM 네트워크를 이용하는 가상 조종사를 위한 컴패니언 앱입니다.

■ 실시간 지도
전 세계 항공기와 관제사를 지도 위에서 확인합니다. 관제 구역은 VAT-Spy 경계를 그대로
표시하고, 공항 관제석은 타워·그라운드·딜리버리 배지로 구분합니다. 항공기를 누르면
조종사·고도·속도·출도착 시각과 함께 항로가 지도에 그려집니다.

■ 무작위 경로 추천
전 세계 국제공항 중에서 단거리·중거리·장거리 구간에 맞는 출도착지를 뽑아줍니다.
거리에 맞는 활주로를 갖춘 공항만 후보에 오릅니다.

■ SimBrief 연동
뽑은 경로로 SimBrief 비행계획을 만들고, 순항 고도·연료·항로 요약과 PDF를 확인합니다.

■ 기상 정보
METAR와 TAF를 원문과 함께 읽기 쉬운 형태로 풀어 보여줍니다.

■ 관제소 접속 알림
관심 있는 관제소를 등록해 두면 접속하는 순간 알림을 받습니다. 대륙과 나라를 고르면
센터·어프로치·공항 후보가 나오고, 공항을 고르면 그 공항의 관제석이 한 번에 잡힙니다.

■ 공식 이벤트
VATSIM 공식 이벤트 일정을 확인하고, 별을 눌러 둔 이벤트는 시작 한 시간 전에 알림을 받습니다.

■ 나의 비행
CID를 등록하면 그 뒤의 비행이 기록되고, 다녀온 나라가 지도에 칠해집니다.

한국어·영어·중국어·독일어·포르투갈어를 지원합니다.

VATRadar는 VATSIM과 공식적으로 제휴하지 않은 비공식 앱입니다.
데이터 출처와 라이선스는 앱 안 설정 → 정보 → 출처 및 라이선스에서 확인할 수 있습니다.
```

## 7. 콘텐츠 등급 설문

폭력·성적 콘텐츠·도박 요소가 없으므로 전체 이용가입니다.
"사용자 간 소통 기능"은 **없음**으로 답하면 됩니다 (앱에 채팅 기능 없음).

## 8. 배포 전 최종 확인

- [x] `./gradlew testDebugUnitTest` 통과 (105개)
- [ ] `./gradlew connectedDebugAndroidTest` 통과
- [x] 릴리스 빌드 실기기/에뮬레이터 확인 (지도·알림 페이지·이벤트·라이선스 화면)
- [ ] Maps 키 제한 후 릴리스 빌드에서 지도가 나오는지 재확인
- [x] `versionCode` 증가 (5)

## 9. 광고를 붙이려면 (AdMob)

**VATSIM 이사회의 사전 서면 동의가 필요합니다.** VATSIM Code of Regulations
Article I §B:

> no individual or entity is permitted to resell or make any commercial or non-commercial
> use of the VATSIM network which involves the payment of money or goods to said individual
> or entity or such party's designee **without the prior written consent of the VATSIM Inc.
> Board of Directors** or their designated agent. The prohibitions set forth in this paragraph
> expressly include any and all sales and/or solicitations of money, goods and services no
> matter for what purpose, person, group or cause, without limitation.

광고 수익은 "payment of money ... to said entity"에 해당하므로, 동의 없이 붙이면
규정 위반이고 계정 정지까지 규정되어 있습니다. 붙이려면 이사회에 서면으로 문의하세요.

나머지 출처는 광고를 막지 않습니다.

| 출처 | 상업적 이용 |
|---|---|
| VAT-Spy Data Project (CC BY-SA 4.0) | 허용 — 단 경계 파일은 계속 CC BY-SA 4.0로 제공해야 함 |
| OurAirports · Natural Earth · NOAA | 퍼블릭 도메인이라 제약 없음 |
| Google Maps Platform | 앱에 광고를 넣는 것 자체는 허용. 지도 위를 광고로 덮거나 Google 저작자 표시를 가리면 안 되고, 지도 타일·데이터를 캐시하면 안 됨 |
| 오픈소스 라이브러리 (Apache 2.0 등) | 제약 없음 |

동의를 받아 광고를 붙이게 되면 함께 고쳐야 하는 것:

- 개인정보처리방침에 **광고 ID 수집**과 AdMob을 명시
- Play 데이터 안전성 양식에 "기기 또는 기타 ID → 광고 또는 마케팅" 추가
- 앱 안 **출처 및 라이선스** 화면에 AdMob 고지 추가 (oss-licenses-plugin이 자동 처리)

## 알려진 제약 (심사와 무관하지만 사용자에게 영향)

- Cloudflare Worker가 멈추면 알림이 15분 폴링으로 내려갑니다 (기능은 유지).
- 공항 데이터는 OurAirports 스냅샷이라 신설 공항이 빠질 수 있습니다.
- VAT-Spy 경계 데이터도 스냅샷이므로 FIR 개편 시 갱신이 필요합니다.
- 알림 등록은 CID 확인 없이 누구나 할 수 있습니다 (VATSIM Connect OAuth를 뺐기 때문).
