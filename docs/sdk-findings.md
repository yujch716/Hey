# Pleos SDK 검증 결과 (3단계)

차량 앱(`car-app/`)이 Pleos Connect SDK에서 실제로 무엇을 받을 수 있는지 정리한다.
제품 기능은 만들지 않았고, `car-app`에는 "SDK 검증" 화면만 있다(`com.choi.nawa.sdkcheck`). 버튼마다 API를 부르고, 결과를 화면 로그와 `adb logcat -s NAWA_SDK`에 남긴다.

- **검증 환경**: Pleos Connect Emulator **v2.1.0** (`Pleos/crp_avd_arm64:14/RELEASE.release_connect_v2.1.0`, API 34, 2560×1440, user 빌드), 2026-11-15
- **검증 방법**: 에뮬레이터에 검증 앱을 설치하고 `adb`로 버튼을 눌러 logcat을 수집했다. 반환 형식은 SDK 아티팩트(소스·클래스)로도 대조했다.
- 기본 내비 앱(`ai.umos.maps.android.navigation.app`)의 차량 위치는 성남 시흥동(37.4127, 127.0947)이고, 에뮬레이터 GPS 기본값은 미국 마운틴뷰(37.422, -122.084)다.

## SDK 버전과 설정

| SDK | 좌표 | 비고 |
| --- | --- | --- |
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` | |
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` | 데이터 클래스는 `navi-data:3.0.4` |
| FusedLocation | `ai.pleos.playground:FusedLocation:1.1.3` | **문서는 1.1.2인데 저장소에 없다**(1.0.4, 1.1.3만 있음) |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` | SpeechToText는 넣지 않았다 |

- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (`settings.gradle.kts`에서 `ai.pleos.*` 그룹만 받게 제한)
- 평문 http는 `res/xml/network_security_config.xml`로 `10.0.2.2`(에뮬레이터에서 본 맥북)만 허용했다.

### 권한

| 권한 | 용도 | 설치 후 상태 |
| --- | --- | --- |
| `pleos.car.permission.CAR_DOORS` / `CAR_DRIVE` / `CAR_INFO` | 문, 기어·브레이크, VIN·차량등록번호 | 자동 허용 |
| `pleos.car.permission.NAVI_ROUTE` / `NAVI_ROUTE_SEARCH` | `get*Info()`·`requestTrip()` / `requestRoute()`·`cancelRoute()` | 자동 허용 |
| `pleos.car.permission.TTS_SERVICE` | TTS | 자동 허용 |
| `pleos.car.permission.FUSED_LOCATION` | FusedLocation | **런타임 요청 필요**(dangerous) |
| `android.permission.ACCESS_FINE_LOCATION` | LocationManager 대체 확인용 | 런타임 요청 필요 |
| `android.permission.INTERNET` | 서버·WebView | 자동 |

> AAOS는 사용자가 여러 명이다(시스템 0번, 운전자 10번). `adb shell pm grant`는 `--user 10`을 붙여야 적용된다.
> CLAUDE.md의 사전 승인 목록에 없는 권한: `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `TTS_SERVICE`(목록엔 "NaviHelper", "TTS"로만 적혀 있음), `ACCESS_FINE_LOCATION`.

## 결과

결과 표기: **가능** / **불가** / **제한**(되지만 조건이 있음) / **미확인**(에뮬레이터에서 재현 못함)

### 1. NaviHelper

| 항목 | 결과 | 실제 반환값 예시 | 우리 설계에 미치는 영향 |
| --- | --- | --- | --- |
| `initialize` | 가능 | `onConnected` 즉시(약 10ms) | 연결 후 리스너를 붙인다 |
| `requestRoute`로 안내 시작 | 가능 | `poiId = ""`로도 됨. 약 6초 뒤 `onRouteStarted(RouteStartInfo(duration=1020, distance=13000, tollFare=700, name=강남역, …))` → `onRouteEnded` → `onNavigationStatus(ROUTE_DRIVE)` → `onRouteStateInfo` → `onTBTInfo` → `onDrivingInfo` | 좌표와 이름만으로 정류지를 목적지로 넣을 수 있다. **요청하면 내비 앱이 메인 화면으로 올라오고 우리 앱은 뒤로 간다**(리스너는 계속 동작) |
| `getRouteStateInfo` 필드 | 가능 (ETA 없음) | `RouteStateInfo(termsAgreement=true, naviMode=ROUTE_GUIDING, isRouting=false, routeOption=RECOMMENDED, remainDestinationChargingInt=75644, remainWaypointChargingInt=[0, 0], trafficStatus=TrafficStatus(summary=TrafficCongestionSummary(totalCongestionDist=890.0, totalCongestionTime=180, …), …))` | **남은 시간·거리·도착 시각이 없다.** 안내 중에도 `isRouting=false`였다. 안내 여부는 `naviMode`(`SAFE_GUIDING`↔`ROUTE_GUIDING`)나 `onNavigationStatus`로 판단해야 한다 |
| 남은 시간·거리 | **가능 (1분·1km 단위)** | `getDestinationInfo()` → `DestinationInfo(name=강남역, remainDistance=13000, remainTime=1020, …)`<br>`onDrivingInfo(DrivingInfo(destination=DrivingState(name=강남역, duration=1020, distance=13000, …)))`<br>`requestTrip(역삼역)` → `TripInfo(distance=14216, eta=1025, …)` | 단위는 초·미터다. **`remainTime`/`duration`은 60초, `remainDistance`/`distance`는 1000m 단위로 반올림돼 온다**(1020/1080초, 13000/14000m). `TripInfo`는 더 세밀하다(14216m, 1025초) |
| 도착 예정 시각(절대 시각) | 불가 | 어떤 데이터에도 없다(내비 화면에는 "1:31 PM"이 보임) | 남은 시간으로 계산한다 |
| 경로 좌표(폴리라인) | **불가** | 좌표를 주는 API·필드 없음. `CurrentLocationInfo.routeId="6b38ddd2-…"`, `currentRouteIndex=0`만 있음 | 지도 경로는 서버의 카카오 길찾기 결과를 그대로 쓴다(2.5단계 결정 유지) |
| `getCurrentLocationInfo` | 가능 | `CurrentLocationInfo(latitude=37.4127212, longitude=127.094681, heading=0.0, address=수정구 시흥동, routeId=…, currentRouteIndex=0)` | **에뮬레이터 GPS(`geo fix`)를 따라가지 않는다.** 내비는 자기 위치를 따로 가진다 |
| 목적지 도착 콜백 | 미확인 | 정의상 `onDestinationArrived(DestinationArrivedInfo(duration, distance, name, address))` | 에뮬레이터에서 내비 차량을 움직일 방법을 찾지 못해 재현 못함(아래 "직접 확인") |
| `cancelRoute` → `requestRoute`로 목적지 변경 | 가능 | `onRouteCancelled` → `onNavigationStatus(FREE_DRIVE)` → `onDestinationInfo(빈 값)` → 새 `onRouteStarted(역삼역, duration=1080, distance=14000)` | 정류지마다 목적지를 바꾸는 흐름이 된다 |
| 안내 중 `requestRoute`만 다시 호출 | 가능 | 내비가 스스로 `onRouteCancelled` 후 새 경로 시작 | `cancelRoute`를 따로 부르지 않아도 된다 |
| `onCurrentRoadSpeedLimit` | 가능 | 등록 직후부터 **1초마다** `0` | 주기 신호로 쓸 수는 있지만 값 자체는 쓸모 없음 |
| `onDrivingInfo` 수신 주기 | 제한 | 정지 상태에서는 안내 시작 시 1회만 옴 | 주행 중 주기는 차량을 움직여 봐야 안다. ETA는 `getDestinationInfo()` 폴링을 기본으로 두는 게 안전하다 |

### 2. 위치

| 항목 | 결과 | 실제 반환값 예시 | 우리 설계에 미치는 영향 |
| --- | --- | --- | --- |
| FusedLocation `isSupported` | **가능** (`true`) | `isSupported() = true` → `initialize()` | 문서상 "준비 중"이지만 이 에뮬레이터에서는 동작한다. 실차에서도 `isSupported()` 분기를 유지한다 |
| FusedLocation `getLastKnownLocation` | 가능 | `LocationData(latitude=37.4219…, longitude=-122.084, altitude=5.0, heading=0.0, confidence=5.0, timestampMillis=…)` | **속도 필드가 없다** |
| FusedLocation 콜백 주기 | 가능 | **약 1초**(996~1011ms), 위치 변화가 없어도 계속 옴 | 3~5초 전송 간격에는 충분하다. 앱에서 솎아서 보낸다 |
| `geo fix`로 위치 이동 | 가능 | 1초 안에 Fused·LocationManager 모두 새 좌표 반영 | 개발 중 차 위치를 움직이는 방법으로 쓸 수 있다(내비는 안 따라감) |
| 대체: NaviHelper 위치 | 가능 | 위 `getCurrentLocationInfo`와 같음 | GPS와 따로 노는 위치라 대체로 쓰기 애매하다 |
| 대체: AAOS `LocationManager` | 가능 | providers=`[passive, fused, gps]` 모두 켜짐. `gps` 1초 간격, `speed=0.0m/s`(geo fix는 속도를 주지 않음) | `ACCESS_FINE_LOCATION` 필요. 속도를 주는 유일한 출처지만 에뮬레이터에선 0이라 실차 확인이 필요하다 |

### 3. Vehicle

| 항목 | 결과 | 실제 반환값 예시 | 우리 설계에 미치는 영향 |
| --- | --- | --- | --- |
| 초기화 | 제한 | `Vehicle.initialize()`는 완료 콜백이 없다. 초기화 전 호출은 `ERROR_SERVICE_CONNECTION_FAILURE` / `Service connection failed` | 차량 앱은 초기화 후 첫 호출을 재시도해야 한다 |
| 문별 상태 | **가능** | `checkDoorPositionCapabilities: READ_WRITE areas=[ROW_1_LEFT=0..1, ROW_1_RIGHT=0..1, ROW_2_LEFT=0..1, ROW_2_RIGHT=0..1, REAR=0..100, HOOD=0..100]`<br>`getCurrentDoorPosition(ROW_1_LEFT)=0` … 모두 0 | 문 4개는 **0=닫힘, 1=열림**으로 따로 읽힌다. 운전석은 `ROW_1_LEFT`(한국 기준)다 |
| 문 변경 콜백 | 가능 (변경 시점은 미확인) | `registerDoorPosition`하면 현재 값이 즉시 1회 옴: `◀ door ROW_1_RIGHT position=0` 등 | 실제로 문을 열었을 때 오는지는 Virtual Console로 확인해야 한다. VHAL 주입은 user 빌드라 막혀 있다(`inject-vhal-event requires non-user build`) |
| 기어 | 가능 | `gear=GEAR_PARK`, 등록 시 즉시 `◀ gear=GEAR_PARK` | 정차 판정에 쓴다. Gear에는 `checkXXXCapabilities`가 없다 |
| 브레이크 | 제한 | `checkBrakePedalStatusCapabilities: READ`, `brakePedal=false` | **주차 브레이크가 아니라 브레이크 페달** 상태다 |
| 속도 | **불가** | Vehicle SDK에 차량 속도 API 없음 | 속도가 필요하면 위치에서 계산한다 |
| 주차 브레이크 | **불가** | API 없음 | 정차 판정은 기어 P 기준으로 한다 |
| 주행 모드 | 불가 | `ERROR_NOT_SUPPORTED_PROPERTY` (`property 0x21400410 not supported`) | 쓰지 않는다 |
| CarInfo | 제한 | `checkVINInfoCapabilities: READ`, `checkCRNInfoCapabilities: READ`, `VIN="invalid"`, `CRN="invalid"`(에뮬레이터 값). **차종·색상 API 없음** | 차종·색상은 운전자가 입력해야 한다. 차량등록번호(CRN)는 실차에서 번호판 자동 입력에 쓸 수 있는지 확인이 필요하다 |
| 문 잠금 capability | 가능 | `checkDoorLockCapabilities: READ_WRITE areas=[[HOOD], [ROW_1_LEFT, ROW_1_RIGHT, ROW_2_LEFT, ROW_2_RIGHT]]` | 쓰지 않는다 |

### 4. TextToSpeech

| 항목 | 결과 | 실제 반환값 예시 | 우리 설계에 미치는 영향 |
| --- | --- | --- | --- |
| 초기화 (`ON_DEVICE`) | 가능 | `initialize onSuccess` → `setLocale(ko-KR)` | `ON_DEVICE` 모드는 Client ID/Secret 등록이 필요 없다 |
| 한국어 발화 | 가능 (소리는 직접 확인 필요) | `speak("나왔어요")` → `onReady` → `onStart` → 약 2.3초 뒤 `onDone`. 시스템 오디오에 재생 시작·정지가 기록됨 | 빠른 응답을 차 안에서 읽어 줄 수 있다. 여러 개가 연달아 오면 `onDone`을 기다렸다가 다음 것을 읽는다 |

### 5. 네트워크 / 6. WebView

| 항목 | 결과 | 실제 반환값 예시 | 우리 설계에 미치는 영향 |
| --- | --- | --- | --- |
| `http://10.0.2.2:3000` 서버 | 가능 | `GET /share/health-check → 404 in 70ms {"message":"share not found",…}` (헬스체크 엔드포인트가 없어서 404 JSON을 성공으로 봄) | 에뮬레이터에서 맥북 서버로 접속된다 |
| `http://10.0.2.2:5173` 웹 | 제한 | 처음엔 `ConnectException`. vite가 IPv6 `localhost`에만 떠 있어서다. `--host 127.0.0.1`로 띄우니 `200 in 26ms` | 에뮬레이터 테스트 때는 웹 dev 서버를 IPv4로 띄워야 한다 |
| WebView 존재 | **가능** | provider=`ai.pleos.android.webview` 131.0.6752.0 | 앱 안 WebView를 쓸 수 있다 |
| WebView로 탑승자 웹 표시 | **가능 (조건 있음)** | 처음엔 회색 화면만 보임: 페이지는 로드됐지만 `100dvh`/`100vh`가 **0px**로 계산됨. WebView를 `MATCH_PARENT`로 지정하자 `frame=440px`, 화면 정상 표시 | Compose `AndroidView`의 기본 `WRAP_CONTENT` 때문이다. **WebView는 반드시 `MATCH_PARENT`로 넣는다**(검증 코드에 반영함) |
| WebView 안 카카오맵 | 미확인 | `net::ERR_CLEARTEXT_NOT_PERMITTED http://dapi.kakao.com/…` (http 페이지라 SDK도 http로 요청 → 앱의 평문 허용 범위 밖) | https 배포 환경에서는 생기지 않는 문제다. 카카오 JS 키에 WebView가 여는 도메인도 등록해야 한다 |

### 7. 6단계 추가 검증 (탑승자 메시지, 문 판정, 주행 중 입력)

| 항목 | 결과 | 실제 반환값 예시 | 우리 설계에 미치는 영향 |
| --- | --- | --- | --- |
| 메시지를 TTS로 순서대로 읽기 | 가능 | 세 메시지가 겹치지 않고 재생됨(오디오 플레이어 38.9→41.8초, 41.9→44.1초, 44.1→47.3초). 같은 메시지를 10초 안에 다시 보내면 재생 안 됨 | `TtsPort.speak`가 `onDone`까지 기다리게 하고 큐로 하나씩 읽는다. 데모 모드도 Pleos TTS(ON_DEVICE)를 쓴다 |
| 서비스에서 운행 패널 Activity를 앞으로 띄우기 | **제한** | 첫 시도는 OS가 막음: `Background activity launch blocked … callingUidProcState: FOREGROUND_SERVICE … (BAL_BLOCK)`. 두 번의 실행 모두 헤드업 알림을 한 번 띄운 뒤의 메시지에서는 앱이 앞으로 왔다 | 포그라운드 서비스만으로는 Android 14의 백그라운드 실행 예외가 아니다. **띄우기를 시도하고, 3초 안에 앞에 없으면 헤드업 알림**(중요도 높음 채널 `replies`, 누르면 운행 패널)으로 보여준다. 알림은 `dumpsys notification`에서 `importance=4`로 게시된 것을 확인했고, 화면에 헤드업으로 뜨는 모습은 캡처하지 못했다 |
| 문 열림으로 탑승 판정 (데모 `FakeVehicle`) | 가능 | 2번 정류지 도착 후 조수석 열기→닫기 → 3초 뒤 `stopCompleted(by: door)` → 3번으로 `requestRoute`, 안내 음성 재생. 운전석 문만 열고 닫으면 6초가 지나도 완료되지 않음. 마지막 정류지는 "운행을 마쳤어요" 후 서비스 종료 | 실제 문 이벤트(`registerDoorPosition`)는 3단계에서 확인한 대로 문별 0/1로 온다. Virtual Console로 실제 이벤트를 넣어 보는 확인은 남아 있다 |
| 주행 중 입력 제한 | 가능 | 데모 기어 D에서 운행 준비 화면에 "정차 중에만 입력할 수 있어요", 검색창·검색·운행 시작 비활성 | 기어는 `VehiclePort.gear`(Pleos: `registerGearSelection`). 운행 패널의 큰 버튼은 주행 중에도 누를 수 있고 [운행 종료]는 막힌다 |
| Pleos Virtual Console | 미확인 | Virtual Console 사용 가이드에 Door Position(문별 열림)·Gear Selection(P/R/N/D) 카드가 있다. 연결(설정) 가이드는 문서 사이트에서 찾지 못했다 | 실차 같은 문·기어 이벤트 확인은 Virtual Console을 연결해서 해야 한다(아래 "직접 확인할 것") |

### 화면 구성 (관찰)

- 화면은 **왼쪽 차량 위젯 패널(고정)** + **오른쪽 메인 영역**이다. 우리 앱과 내비는 같은 메인 영역을 번갈아 쓴다.
- `requestRoute`를 부르면 내비가 메인 영역을 가져간다. 우리 앱으로 돌아오려면 다시 실행해야 한다.

## 세 가지 질문

- **(a) 현재 목적지까지 ETA를 내비에서 받을 수 있나?**
  → **예, 다만 1분 단위로.** `getRouteStateInfo`에는 없다. `getDestinationInfo()`의 `remainTime`(초)이나 `onDrivingInfo`의 `destination.duration`(초)으로 받는다. 값이 60초 단위로 반올림돼 오고(예: 1020), 정지 상태에서 `onDrivingInfo`는 안내 시작 때 한 번만 왔다. 서버의 `currentLegEtaSeconds`는 이 값을 쓰되, 갱신은 `getDestinationInfo()` 폴링을 기본으로 한다.
- **(b) 탑승자 쪽 문을 운전석과 구분해서 감지할 수 있나?**
  → **예.** `DoorArea(ROW_1_LEFT / ROW_1_RIGHT / ROW_2_LEFT / ROW_2_RIGHT)`로 문마다 0(닫힘)/1(열림)을 읽고 콜백도 문마다 등록된다. 다만 **실제로 문을 열었을 때 콜백이 오는지는 Virtual Console로 확인이 남았다**(VHAL 주입은 user 빌드라 막힘).
- **(c) 운전자 지도를 WebView로 갈 수 있나?**
  → **예.** Pleos WebView(Chromium 131)가 있고, 탑승자 웹이 그대로 표시된다. 조건은 두 가지다.
  - WebView를 `MATCH_PARENT`로 넣어야 한다. 아니면 `dvh` 높이가 0이 된다.
  - 카카오맵이 https 페이지에서 로드돼야 한다. 카카오맵이 실제로 그려지는 것까지는 아직 확인하지 못했다.

## 직접 확인할 것

- [ ] **멀티 윈도우**: 이 앱과 내비가 나란히 뜨는지. 관찰상 둘은 같은 메인 영역을 번갈아 쓰고, `requestRoute` 때 내비가 앞으로 나온다. 분할 화면 방법이 있는지 확인이 필요하다
- [ ] **6단계 실제 모드 탑승 판정**: 실제 모드(데모 끔)로 운행하고 2번 정류지 도착 후 Virtual Console의 Door Position에서 조수석이나 뒷좌석 문을 열었다 닫으면 3초 뒤 자동으로 다음 정류지로 넘어가는지. 운전석(앞 왼쪽)만 열고 닫으면 넘어가지 않아야 한다
- [ ] **헤드업 알림 모양**: 앱이 뒤에 있을 때 탑승자 메시지가 오면 화면 위에 헤드업 알림이 보이는지, 누르면 운행 패널이 열리는지
- [ ] **Virtual Console로 문 열기/닫기**: 검증 앱에서 Vehicle `initialize` → "문 콜백 등록"을 누른 뒤 문을 바꾸면 `adb logcat -s NAWA_SDK`에 `◀ door ROW_2_RIGHT position=1`처럼 찍히는지
- [ ] **Virtual Console로 기어 변경**: "기어·브레이크 콜백 등록" 후 P→D로 바꾸면 `◀ gear=GEAR_DRIVE`가 찍히는지. D에서 AAOS 주행 중 UX 제한으로 우리 화면이 막히는지
- [ ] **차량 위치 움직이기**
  - `adb emu geo fix <lng> <lat>`(또는 Extended Controls > Location 경로 재생)는 FusedLocation·LocationManager에만 반영되고 **내비는 따라가지 않았다.**
  - 내비 차량을 움직이는 방법(Virtual Console 경로 재생이나 내비 모의 주행)이 있는지 확인이 필요하다. 그래야 `onDestinationArrived`와 `onDrivingInfo` 갱신 주기를 확인할 수 있다
- [ ] **TTS 소리**: "나왔어요"를 눌렀을 때 실제로 한국어 음성이 들리는지(로그상 재생 완료)

## 설계 변경이 필요한 것

1. **차종·색상은 SDK에서 오지 않는다.** 운행 생성 화면에서 운전자가 입력하거나 저장해 둔 값을 쓴다. 번호판은 CRN 자동 입력을 실차에서 확인한다.
2. **정차 판정은 기어(`GEAR_PARK`)로 한다.** 속도와 주차 브레이크 API가 없다. CLAUDE.md의 "정차한 상태에서 문이 열리면"은 "기어 P에서 운전석이 아닌 문이 0→1→0"으로 구체화한다.
3. **ETA 갱신은 `getDestinationInfo()` 폴링(위치 전송 주기에 맞춰)을 기본으로 한다.** 값이 1분 단위라 서버의 `near`(≤120초) 판정이 1분 단위로 움직인다는 점을 감안한다. `getRouteStateInfo`는 ETA 용도로 쓰지 않는다.
4. **안내 여부는 `isRouting`이 아니라 `onNavigationStatus`(`ROUTE_DRIVE`/`FREE_DRIVE`)로 판단한다.** 안내 중에도 `isRouting=false`였다.
5. **정류지 목적지 변경은 `requestRoute`만 다시 부르면 된다**(`cancelRoute` 불필요). 너무 가까운 정류지는 `CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE`를 처리해야 한다.
6. **`requestRoute` 때 내비가 화면을 가져간다.** 운행 화면(QR, 진행 상태)은 내비 위로 다시 띄우는 흐름이 필요하다. 정차 중에만 조작한다는 원칙과도 맞물린다.
7. **위치 출처는 FusedLocation(1초)을 쓴다.** `isSupported()`가 false일 때만 LocationManager로 내려간다. 내비 위치(`getCurrentLocationInfo`)는 GPS와 따로 놀 수 있어 서버 전송용으로 쓰지 않는다.
8. **운전자 지도는 WebView로 갈 수 있다.** WebView를 `MATCH_PARENT`로 두고, https로 배포된 웹을 연다. 웹 쪽에도 `100dvh`가 0일 때를 대비한 높이 보정을 넣는 게 안전하다(이번 단계에서 web은 수정하지 않음).
9. **Vehicle 초기화 완료 콜백이 없다** → 첫 호출 실패(`Service connection failed`) 시 재시도한다.
10. **FusedLocation 1.1.3**을 쓴다(문서의 1.1.2는 배포돼 있지 않음).
