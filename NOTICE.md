# 출처 및 라이선스 고지

VATRadar는 VATSIM 네트워크를 이용하는 가상 조종사를 위한 **비공식** 앱입니다.
VATSIM, SimBrief를 비롯한 어떤 실제 항공사·공항과도 제휴하거나 승인받은 관계가 아닙니다.

앱 안에서도 같은 내용을 **설정 → 정보 → 출처 및 라이선스**에서 볼 수 있습니다.

## 조건이 붙는 데이터

### VAT-Spy Data Project — CC BY-SA 4.0

- 원본: https://github.com/vatsimnetwork/vatspy-data-project
- 라이선스: [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
- **변경했습니다.** 원본 `Boundaries.geojson`(약 2MB, 1,097개 구역)을 Douglas-Peucker로
  단순화해 좌표를 67% 줄였고, `VATSpy.dat`을 `firs.txt` / `uirs.txt`로 나눠 담았습니다.
- 앱에 들어간 파일은 다음과 같습니다.
  - `app/src/main/assets/fir_boundaries.txt`
  - `app/src/main/assets/firs.txt`
  - `app/src/main/assets/uirs.txt`
- ShareAlike 조항에 따라 **이 세 파일은 원본과 같은 CC BY-SA 4.0으로 제공합니다.**
  앱의 소스 코드는 이 데이터의 2차적 저작물이 아니므로 이 조항의 적용을 받지 않습니다.

### 오픈소스 라이브러리 — 대부분 Apache License 2.0

빌드에 실제로 들어간 라이브러리 목록과 라이선스 원문은 APK 안에 함께 들어 있습니다
(`oss-licenses-plugin`이 의존성 POM에서 뽑아 넣습니다). 앱의
**설정 → 정보 → 출처 및 라이선스 → 전체 목록과 라이선스 원문**에서 볼 수 있습니다.

Google Play services 이용 약관이 요구하는 법적 고지도 같은 화면에 포함됩니다.

## 조건이 없는 데이터 (퍼블릭 도메인)

의무는 없지만 출처를 밝힙니다.

| 데이터 | 쓰임 | 출처 |
|---|---|---|
| OurAirports | 전 세계 공항 데이터베이스 | https://ourairports.com/data/ |
| Natural Earth (110m) | 나의 비행 지도의 나라 경계 | https://www.naturalearthdata.com |
| NOAA Aviation Weather Center | METAR·TAF | https://aviationweather.gov |

## 조회만 하는 서비스

앱이 데이터를 내려받아 보여줄 뿐, 재배포하지 않습니다.

- **VATSIM** 공개 데이터 피드 (`data.vatsim.net`) — 실시간 항공기·관제사·이벤트
- **SimBrief** 공개 API — 사용자 **본인 계정**의 비행계획만 조회

## 앱 자체 저작물

앱 아이콘(`ic_launcher_foreground.xml`), 알림 아이콘, 스토어 그래픽은 이 프로젝트에서
직접 만든 것으로 제3자 소재를 쓰지 않았습니다. VATSIM 로고를 비롯한 타사 상표는
앱 어디에도 쓰지 않습니다.
