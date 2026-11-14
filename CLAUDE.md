# 나와 (nawa) — 스마트픽업 매니저

운전자가 누군가를 픽업하러 갈 때, 픽업 대상자(탑승자)가 차량 위치·도착 예정 시간·진행 상태를 실시간으로 보는 서비스.
현대자동차그룹 차량용 앱 공모전(Pleos Playground) 출품작이다.

- 운전자: Pleos Connect 차량 앱(Android Automotive). 정차 중에 정류지를 입력하고, 주행 중에는 조작하지 않는다.
- 탑승자: 앱 설치 없이 공유 링크로 여는 모바일 웹.

## 구조 (모노레포)

| 폴더 | 내용 | 상태 |
| --- | --- | --- |
| `car-app/` | Kotlin, Android Studio, Pleos Connect SDK (minSdk 28, Gradle 8+) | 프로젝트 껍데기만 있음 |
| `server/` | NestJS (TypeScript), WebSocket, 나중에 PostgreSQL·Redis | 아직 없음 |
| `web/` | React + Vite + TypeScript + Tailwind v4 + shadcn/ui, yarn 4 | 더미 데이터로 동작 |
| `apk/` | 제출용 release APK (`팀명_nawa_release.apk`) | |

car-app SDK 버전 (`car-app/app/build.gradle.kts`):
- `compileSdk = 37`: 사용하는 AndroidX 라이브러리(core 1.19, lifecycle 2.11)가 API 37 이상으로 컴파일해야 한다.
- `targetSdk = 34`: Pleos 차량 OS가 API 34라서 동작 기준을 34에 맞춘다.
- `minSdk = 28`: Pleos 보안 요건.

## 핵심 개념

- **Trip**: 한 번의 픽업 운행. 차량 정보, 경로(`route`), 정류지 목록(`stops`)을 가진다.
- **Stop**: 정류지. `kind` = `waypoint`(카페 등 경유) | `pickup`(탑승자). 방문 순서(`order`)가 있다.
  - 모든 탑승자에게 모든 정류지와 순서를 보여준다. 단, 핀에는 이름이나 전화번호 없이 순번과 짧은 라벨만 표시한다.
- **공유 링크**: 정류지마다 하나씩 만든다. 추측할 수 없는 토큰을 쓰고, 탑승 완료 시 만료된다.
- **TripSnapshot**: 서버가 웹으로 보내는 실시간 상태. 타입은 `web/src/types.ts`가 기준이다.
  - `phase` = `departed` | `toWaypoint` | `atWaypoint` | `toMe` | `near` | `arrived` | `boarded`
- **빠른 응답**: 탑승자가 [나왔어요] [5분 늦어요] [여기서 기다릴게요]를 누르면 차 안에서 TTS로 읽어준다.
- **탑승 판정**: 차가 정차한 상태에서 문이 열리면 해당 정류지를 완료로 처리한다.

## 실시간 갱신

- 차 → 서버: 3~5초 간격으로 위치를 보낸다(멀면 간격을 늘림).
- 서버 → 웹: WebSocket으로 푸시한다. 웹은 위치 사이를 보간 애니메이션으로 잇는다.
- 웹에는 마지막 갱신 시각을 항상 보여준다(네트워크 끊김 대비).

## Pleos 제약 (설계 시 반드시 고려)

- **NaviHelper**: `requestRoute`로 목적지, `addWaypoint`로 경유지를 넣는다. 경유지는 최대 2개다.
  정류지가 더 많으면 앱이 순서를 관리하면서, 도착할 때마다 다음 정류지를 넣는다.
- **Fused Location SDK**는 "준비 중"이다. `isSupported`로 확인하고, 안 되면 NaviHelper `getCurrentLocationInfo`나 AAOS `LocationManager`로 대체한다.
- 차량에서는 연락처·SMS·전화가 제한될 수 있다. 링크 공유는 차 화면의 QR → 운전자 폰의 공유 시트로 한다.
- 권한은 Pleos 앱마켓 사전 승인 범위 안에서만 쓴다. 지금 쓰는 것: `CAR_DOORS`, `CAR_DRIVE`, `CAR_INFO`, `FUSED_LOCATION`, NaviHelper, TTS.
- STT는 쓰지 않는다.

## 웹 규칙

- 모바일 웹 기준, 최대 폭 430px. 데스크톱에서도 가운데에 폰 폭으로 보여준다.
- 지도: `VITE_KAKAO_MAP_KEY`가 있으면 카카오맵, 없으면 데모 지도(`MockMapView`).
- 데모 URL 파라미터(`?state=...&freeze=1&me=1`)는 기획서 캡처와 시연용이니 유지한다.
- 패키지 매니저는 yarn 4 (`web/.yarnrc.yml`에 `nodeLinker: node-modules`). `web/`에서 `yarn build`가 통과해야 한다.

## 공모전 제출 요건

- GitHub에 소스와 APK를 함께 올린다. APK는 `apk/` 폴더에 release 빌드로 넣는다.
- README.md 필수 항목(5개):
  1. 앱 이름과 한 줄 소개
  2. 실행 방법
  3. 테스트 환경과 필요한 설정
  4. 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격
  5. APK 위치 또는 Release 링크
- 심사위원은 APK만 실행하므로 서버와 웹은 배포된 주소로 동작해야 한다.

## 작업 방식

- 한 번에 한 단계씩 작업한다. 요청받지 않은 폴더나 기능은 건드리지 않는다.
- 새 의존성을 추가할 때는 이유를 한 줄로 알려준다.
- 작업이 끝나면 빌드나 실행으로 확인하고, 확인한 방법을 보고한다.
- 커밋은 요청할 때만 한다.
