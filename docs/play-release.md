# Play Store 배포 체크리스트

## 1. 릴리스 키스토어 (아직 안 됨)

프로젝트 루트에서:

```bash
keytool -genkeypair -v -keystore vatradar-release.jks -alias vatradar -keyalg RSA -keysize 4096 -validity 10000
```

그다음 `keystore.properties.example`을 `keystore.properties`로 복사해 값을 채웁니다.

> **이 키를 잃어버리면 앱을 다시는 업데이트할 수 없습니다.**
> `.jks` 파일과 비밀번호를 클라우드 등 별도 장소에 백업하세요.
> Play Console에서 **Play App Signing**을 함께 켜두면 업로드 키를 분실해도 재발급이 가능합니다.

키스토어가 없으면 릴리스 빌드는 디버그 키로 서명됩니다. 실행·검증은 되지만 **Play 업로드는 거부**됩니다.

## 2. 빌드

Play는 APK가 아니라 AAB(Android App Bundle)를 받습니다.

```bash
./gradlew bundleRelease
```

결과물: `app/build/outputs/bundle/release/app-release.aab`

R8 매핑 파일(`app/build/outputs/mapping/release/mapping.txt`)을 Play Console에 함께 올리면
난독화된 크래시 로그가 원래 코드로 복원되어 보입니다.

## 3. Maps API 키 제한 (아직 안 됨)

현재 키에 제한이 걸려 있지 않으면 **누구나 도용해 회원님 할당량을 쓸 수 있습니다.**

Google Cloud Console → 사용자 인증 정보 → 해당 키 →

- **애플리케이션 제한**: Android 앱
- 패키지 이름: `com.vatradar.app`
- SHA-1 인증서 지문: 아래 명령으로 확인

```bash
keytool -list -v -keystore vatradar-release.jks -alias vatradar
```

> Play App Signing을 쓰면 Play가 앱을 **재서명**합니다. 이 경우 Play Console →
> 설정 → 앱 무결성에 표시된 **앱 서명 키의 SHA-1**도 함께 등록해야 지도가 나옵니다.
> 업로드 키 SHA-1만 등록하면 스토어에서 받은 앱에서 지도가 회색으로 나옵니다.

- **API 제한**: Maps SDK for Android 만 허용

## 4. 개인정보처리방침 URL (문서는 작성됨, 호스팅 필요)

`docs/privacy-policy.md`를 웹에 올리고 그 주소를 Play Console에 입력합니다.
GitHub 저장소를 공개로 두고 GitHub Pages를 켜는 게 가장 간단합니다
(저장소 Settings → Pages → Source: main / docs).

현재 저장소는 **비공개**라 그대로는 Pages를 쓸 수 없습니다. 선택지:
- 저장소를 공개로 전환
- 개인정보처리방침만 별도 공개 저장소나 Gist로 분리
- 개인 웹사이트/노션 공개 페이지에 게시

## 5. 데이터 안전성 양식 (Play Console)

코드 기준으로 정확한 답변입니다.

| 질문 | 답변 |
|---|---|
| 데이터를 수집하거나 공유합니까? | **예** (아래 항목만) |
| 앱 활동 → 기타 사용자 생성 콘텐츠 | 수집 안 함 |
| 개인 정보 (이름·이메일 등) | **수집 안 함** (VATSIM 로그인 시 CID만 받고 이름·이메일 권한은 요청하지 않음) |
| 위치 | **수집 안 함** |
| 기기 또는 기타 ID | **수집함** — FCM 등록 토큰, VATSIM CID |
| ↳ 수집 목적 | 앱 기능 (알림 전송, 비행 기록) |
| ↳ 필수 여부 | 선택 (알림을 켜거나 CID를 등록한 경우에만) |
| ↳ 제3자 공유 | 아니요 |
| ↳ 전송 중 암호화 | 예 |
| ↳ 삭제 요청 가능 여부 | 예 (앱 삭제 또는 알림 끄기) |

SimBrief ID는 사용자가 직접 입력해 SimBrief로 보내는 값이므로 "수집"에 해당하지 않지만,
심사에서 질문이 올 수 있으니 앱 설명에 SimBrief 연동을 명시해 두는 편이 좋습니다.

## 6. 스토어 등록 자산

| 항목 | 요구 사항 | 상태 |
|---|---|---|
| 앱 아이콘 | 512×512 PNG | 미작성 (앱 내 adaptive icon은 있음) |
| 피처 그래픽 | 1024×500 PNG | 미작성 |
| 휴대전화 스크린샷 | 최소 2장, 1080×2400 가능 | `docs/screenshots/` 참고 |
| 짧은 설명 | 80자 이내 | 아래 초안 |
| 자세한 설명 | 4000자 이내 | 아래 초안 |

### 짧은 설명 초안

```
VATSIM 실시간 관제·트래픽 지도, 무작위 경로 추천, 기상 정보를 한 앱에서.
```

### 자세한 설명 초안

```
VATRadar는 VATSIM 네트워크를 이용하는 가상 조종사를 위한 컴패니언 앱입니다.

■ 실시간 지도
전 세계 항공기와 관제사를 지도 위에서 확인합니다. 관제 구역은 VATSpy 경계를 그대로
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
관심 있는 관제소를 등록해 두면 접속하는 순간 알림을 받습니다.

■ 공식 이벤트
VATSIM 공식 이벤트 일정을 한국 시간으로 확인합니다.

한국어·영어·중국어·독일어·포르투갈어를 지원합니다.

VATRadar는 VATSIM과 공식적으로 제휴하지 않은 비공식 앱입니다.
```

## 7. 콘텐츠 등급 설문

폭력·성적 콘텐츠·도박 요소가 없으므로 전체 이용가로 분류됩니다.
"사용자 간 소통 기능"은 **없음**으로 답하면 됩니다 (앱에 채팅 기능 없음).

## 8. 배포 전 최종 확인

- [ ] `./gradlew testDebugUnitTest` 통과
- [ ] `./gradlew connectedDebugAndroidTest` 통과
- [ ] 릴리스 빌드 실기기 확인 (지도·경로·이벤트·알림)
- [ ] Maps 키 제한 후 릴리스 빌드에서 지도가 나오는지 재확인
- [ ] `versionCode` 증가

## 알려진 제약 (심사와 무관하지만 사용자에게 영향)

- Cloudflare Worker가 멈추면 알림이 15분 폴링으로 내려갑니다 (기능은 유지).
- 공항 데이터는 OurAirports 스냅샷이라 신설 공항이 빠질 수 있습니다.
- VATSpy 경계 데이터도 스냅샷이므로 FIR 개편 시 갱신이 필요합니다.
