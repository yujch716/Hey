# 나와 (nawa) — 스마트픽업 매니저

운전자가 누군가를 픽업하러 갈 때, 픽업 대상자(탑승자)가 차량 위치·도착 예정 시간·진행 상태를 실시간으로 보는 서비스.
현대자동차그룹 차량용 앱 공모전(Pleos Playground) 출품작이다.

- 운전자: Pleos Connect 차량 앱(Android Automotive). 정차 중에 정류지를 입력하고, 주행 중에는 조작하지 않는다.
- 탑승자: 앱 설치 없이 공유 링크로 여는 모바일 웹.

## 구조 (모노레포)

| 폴더 | 내용 | 상태 |
| --- | --- | --- |
| `car-app/` | Kotlin, Jetpack Compose, Pleos Connect SDK (minSdk 28, Gradle 8+) | MVP (데모 모드, 운전자 지도, 메시지 TTS, 문 탑승 판정) |
| `server/` | NestJS (TypeScript), socket.io, 나중에 PostgreSQL·Redis, yarn 4 | MVP (메모리 저장소) |
| `web/` | React + Vite + TypeScript + Tailwind v4 + shadcn/ui, yarn 4 | 더미 데이터로 동작 |
| `apk/` | 제출용 release APK (`팀명_nawa_release.apk`) | |

car-app SDK 버전 (`car-app/app/build.gradle.kts`):
- `compileSdk = 37`: 사용하는 AndroidX 라이브러리(core 1.19, lifecycle 2.11)가 API 37 이상으로 컴파일해야 한다.
- `targetSdk = 34`: Pleos 차량 OS가 API 34라서 동작 기준을 34에 맞춘다.
- `minSdk = 28`: Pleos 보안 요건.

## 차량 앱 (car-app)

SDK 동작 근거는 `docs/sdk-findings.md`. 단일 Activity + Compose, 패키지 `com.choi.nawa`:

| 위치 | 역할 |
| --- | --- |
| `port/Ports.kt` | `NaviPort`, `LocationPort`, `VehiclePort`, `TtsPort` 인터페이스 |
| `port/pleos/` | Pleos SDK 구현. FusedLocation 미지원이면 NaviHelper 위치로 대체, Vehicle은 연결될 때까지 재시도 |
| `port/fake/` | 데모 모드 구현. `DemoDrive`가 서버 경로를 따라 시속 25km(1x/5x)로 달린다 |
| `trip/TripController.kt` | 운행 로직(단위 테스트 있음). 정류지 하나씩 `requestRoute` → 3초마다 위치·ETA 전송 → 도착(내비 콜백 또는 80m) → 완료(픽업: 문, 경유지: 150m 출발, 둘 다 버튼도 가능). 탑승자 메시지 TTS |
| `trip/TripService.kt` | 포그라운드 서비스(위치 유형). `requestRoute`로 내비가 화면을 가져가도 운행 루프와 운전자 WebSocket을 유지. 메시지가 오면 앱을 앞으로 띄우고, OS가 막으면 헤드업 알림 |
| `data/` | 서버 API(OkHttp), socket.io, DataStore(차량 정보·설정), 차종 목록 |
| `ui/` | 차량 등록 → 운행 준비 → 공유(QR) → 운행 패널, 설정 |
| `sdkcheck/` | 3단계 SDK 검증 화면. 설정 > SDK 진단 |

- 서버·웹 주소는 빌드 변형별 `BuildConfig.SERVER_URL` / `WEB_BASE_URL`:
  - `debug`: 에뮬레이터용 `http://10.0.2.2:3000` / `:5173`
  - `local`: 맥북 IP. `car-app/local.properties`에 `nawa.devHost=192.168.x.x` → `./gradlew installLocal`
  - `release`: 배포 주소(아직 미정, https만)
- 평문 http는 debug/local의 개발 주소(와 카카오 지도 타일)에만 허용한다. release는 전부 차단.
- 운행 패널의 지도는 웹 `/d/{tripId}#token={driverToken}`을 WebView(MATCH_PARENT)로 띄운다. 토큰은 해시로 넘겨 서버 로그에 남지 않게 한다.
  운전자 지도는 차·정류지 상태를 `GET /trips/:tripId`로 3초마다 조회하고, 탑승자 위치·응답은 socket.io로 받는다.
- 에뮬레이터 WebView는 `http://10.0.2.2:5173`에서 열리므로 서버 `CORS_ORIGIN`과 카카오 JavaScript 키 웹 도메인에 그 주소가 있어야 한다.
- 공유 QR은 웹의 `/send/{token}`을 연다. 운전자 폰에서 공유 시트로 `/t/{token}`을 보낸다.
- 픽업 정류지 라벨은 서버에 `픽업`으로 보내고, 내비 목적지 이름은 `N번 정류지`로 넣는다(탑승자 이름 없음).
- 테스트: `./gradlew testDebugUnitTest` (TripController, DemoDrive).

- 주행 중 입력 제한: 기어가 P가 아니면 차량 등록·운행 준비 화면의 입력과 검색을 막는다. 운행 패널의 큰 버튼 하나는 주행 중에도 누를 수 있다.
- 탑승자 메시지 TTS: "{n}번 탑승자가 나왔대요/5분 늦는대요/지금 있는 곳에서 기다린대요". 겹치지 않게 순서대로, 같은 탑승자의 같은 메시지는 10초 안에 한 번만 읽는다.

### 데모 모드 (시연 영상용)

1. 서버(`server/`)와 웹(`web/`, 에뮬레이터에서 보려면 `yarn dev --host 127.0.0.1`)을 띄운다.
2. 앱 > 설정 > **데모 모드** 켜기, 필요하면 **데모 속도 5x**. 운행 중에는 바꿀 수 없다.
3. 운행 준비에서 장소를 검색해 정류지를 넣고 [운행 시작] → 공유 화면의 QR → [출발].
4. 가상 차량이 경로를 따라 움직이고, 정류지에 닿으면 자동으로 도착 처리된다.
   경유지에서는 10초 뒤 가상 차량이 스스로 다시 출발해 150m 출발 판정으로 자동 완료된다(데모 전용). 픽업은 데모 문 버튼이나 [탑승 완료].
5. 데모 모드의 시작 위치는 강남(교대 근처, `DEMO_ORIGIN`)이다. 장소 검색도 그 근처로 나온다.
6. 패널(과 설정)의 데모 차량 버튼: 조수석·뒷좌석·운전석 문 열기/닫기(탑승 판정에 그대로 쓰임), 기어 P/D 고정(입력 제한 확인용).
7. 데모 모드에서도 TTS는 실제 Pleos TTS로 읽는다(에뮬레이터에서 소리가 난다).

## 핵심 개념

- **Trip**: 한 번의 픽업 운행. 차량 정보, 경로(`route`), 정류지 목록(`stops`)을 가진다.
  - 경로와 구간별 소요 시간은 운행 생성 시 서버가 카카오모빌리티 길찾기(다중 경유지)로 한 번 만든다. 차량 앱은 출발점과 정류지만 보낸다.
  - 카카오 키가 없거나 호출이 실패하면 정류지를 직선으로 잇고 구간 시간을 추정한다(개발·테스트용).
- **Stop**: 정류지. `kind` = `waypoint`(카페 등 경유) | `pickup`(탑승자). 방문 순서(`order`)가 있다.
  - 모든 탑승자에게 모든 정류지와 순서를 보여준다. 단, 핀에는 이름이나 전화번호 없이 순번과 짧은 라벨만 표시한다.
  - 서버의 `label`은 운전자가 붙인 중립 문구다(예: 카페, 픽업). 탑승자 시점 문구("내 위치", "다음 픽업")를 담지 않는다.
  - 웹 표시: 내 정류지는 "내 위치", 다른 픽업은 "픽업", 경유지는 `label` 그대로. 문장에서는 다른 픽업을 "{order}번 정류지"로 부른다.
- **공유 링크**: 픽업 정류지마다 하나씩 만든다. 추측할 수 없는 토큰을 쓰고, 그 정류지가 완료되면 만료된다.
- **TripSnapshot**: 서버가 웹으로 보내는 실시간 상태.
  - `phase` = `departed` | `toEarlierStop` | `atEarlierStop` | `toMe` | `near` | `arrived` | `boarded` (EarlierStop = 내 앞 순번 정류지. 경유지든 다른 탑승자든)
  - 타입 기준은 `server/src/common/types.ts`. `web/src/types.ts`는 같은 내용으로 맞춘다.
- **빠른 응답**: 탑승자의 [나왔어요] [5분 늦어요] [여기서 기다릴게요]는 단순 메시지다. 차량에서는 TTS로 읽고 앱 화면을 앞으로 띄워 보여줄 뿐, 내비 목적지나 운행 상태를 바꾸지 않는다.
  - `waitHere`에 위치가 있으면 운전자 앱 지도에 핀으로만 표시한다. STT는 쓰지 않는다.
- **정류지 진행**: 정류지 하나씩 `stopArrived` → `stopCompleted { by }` 순서로 진행한다. 완료 판정은 차량 앱이 한다.
  - 차량 앱의 문 판정(`TripController`): 현재 정류지가 픽업이고 도착했거나 80m 이내일 때, 운전석이 아닌 문(앞 오른쪽, 뒤 왼쪽·오른쪽)이 열렸다가 모두 닫힌 채 3초 유지되면 `door`. 운전석 문은 무시한다.
  - 경유지는 문으로 판정하지 않는다. 도착 후 150m 이상 멀어지면 `departed`, 또는 [다음으로]
  - 픽업: 정차 중 운전석이 아닌 문이 열렸다 닫힘(`door`), 또는 운전자의 [탑승 완료] 버튼(`button`)
  - 경유지: 운전자의 [다음으로] 버튼(`button`), 또는 도착 후 차가 다시 출발(`departed`)
- **ETA**: 내비는 현재 목적지까지의 ETA만 준다. 그 뒤 정류지의 ETA는 서버가 계산한다. 운전자 앱 지도는 `GET /trips/:tripId`로 정류지별 ETA와 탑승자 공유 위치를 받는다.
  - 현재 목적지 ETA + 그 뒤 구간 소요 시간 합 + 앞 정류지마다 정차 여유 60초

## 실시간 갱신

- 차 → 서버: 3~5초 간격으로 위치와 현재 목적지까지의 내비 ETA를 보낸다(멀면 간격을 늘림).
- 서버 → 웹: WebSocket으로 푸시한다. 웹은 위치 사이를 보간 애니메이션으로 잇는다.
- 웹에는 마지막 갱신 시각을 항상 보여준다(네트워크 끊김 대비).

## Pleos 제약 (설계 시 반드시 고려)

- **NaviHelper**: 경유지(`addWaypoint`)는 쓰지 않는다. 다음 정류지 하나만 `requestRoute`로 목적지로 넣고,
  그 정류지가 완료되면 다음 정류지를 넣는다. 정류지 순서는 앱이 관리한다.
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
