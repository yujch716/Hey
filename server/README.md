# hey server

차량 앱(운전자)이 보내는 위치·이벤트를 받아, 탑승자 웹에 실시간 상태(`TripSnapshot`)를 푸시하는 NestJS 서버.
지금은 메모리 저장소라 서버를 재시작하면 운행이 사라진다.

## 실행

```bash
cd server
cp .env.example .env
yarn install
yarn build        # dist/ 생성 + scripts 타입 검사
yarn start        # http://localhost:3000
```

개발 중에는 `yarn start:dev` (tsc watch + node --watch).

### 가짜 운전자로 확인하기

서버를 켠 상태에서 다른 터미널 두 개를 연다.

```bash
yarn fake-driver                 # 운행 생성 → 공유 URL·토큰 출력 → 3초마다 위치 전송
yarn watch-share <token> [late]  # 탑승자처럼 GET /share/:token 후 socket.io로 snapshot 수신
```

- `fake-driver`는 `web/src/data/dummyTrip.ts`의 출발점과 정류지를 쓴다. 차량 앱처럼 경로는 보내지 않고, `POST /trips` 응답의 경로를 따라 움직인다.
  정류지마다 `stopArrived` → (카페는 잠시 대기) → `stopCompleted`를 보내고, 마지막에 `tripEnded`로 운행을 끝낸다.
  위치에는 현재 목적지까지의 ETA(`currentLegEtaSeconds`)만 담는다.
- 탑승자 응답이 오면 `fake-driver` 콘솔에 TTS로 읽을 문장이 찍힌다.
- `TICK_MS=500 yarn fake-driver`처럼 간격을 줄이면 빨리 돌려 볼 수 있다. `SERVER_URL`로 서버 주소를 바꾼다.

### 실제 폰(또는 실차)으로 테스트하기

폰과 맥북이 같은 Wi-Fi에 있어야 한다. 맥북 IP는 `ipconfig getifaddr en0`로 확인한다(예: `192.168.0.10`).

1. `server/.env`
   ```
   WEB_BASE_URL=http://192.168.0.10:5173        # 공유 링크·QR이 이 주소로 나온다
   CORS_ORIGIN=http://localhost:5173,http://192.168.0.10:5173,http://10.0.2.2:5173
   ```
   서버를 다시 시작한다(`yarn start`). 서버는 모든 인터페이스(`*:3000`)에서 받는다.
2. 웹: `cd web && yarn dev --host` (같은 네트워크의 폰이 `http://192.168.0.10:5173`로 접속할 수 있게)
   - 웹은 `VITE_API_BASE_URL`이 비어 있으면 페이지를 연 호스트의 3000번 포트를 서버로 쓴다. 폰에서 IP로 열면 서버도 IP로 찾아간다.
3. 차량 앱: `car-app/local.properties`에 `nawa.devHost=192.168.0.10`을 넣고 `./gradlew installLocal`
   (debug 빌드는 에뮬레이터용 `10.0.2.2`를 쓴다)
4. 카카오맵을 쓰려면 카카오 개발자 콘솔의 JavaScript 키 웹 도메인에 `http://192.168.0.10:5173`을 추가한다.
   에뮬레이터 WebView로 보려면 `http://10.0.2.2:5173`도 추가한다.

주의: 폰 브라우저는 https가 아닌 주소에서 위치 권한을 막는다. 탑승자 위치 공유는 이 방법으로는 시험할 수 없다.

### 테스트

```bash
yarn test
```

- `src/trips/phase.spec.ts`: phase 계산(120초 경계, 앞 정류지 대기, 내 차례 이후, 뒤 순번 탑승자)과 정류지별 ETA 계산
- `src/trips/trips.service.spec.ts`: 경로 계획, 공유 토큰 발급·만료, 정류지 단위 진행, 탑승자 위치 전달, 운전자 조회, 동시 요청 순서
- `src/directions/route-planner.spec.ts`: 카카오 응답 해석, 실패·키 없음일 때 직선 대체
- `src/places/places.service.spec.ts`: 장소 검색(카카오 응답 해석, 실패 시 502, 키 없음 더미)
- `src/common/geo.spec.ts`: 경로 위 진행 위치(`traveledIndex`)

## 설정 (.env)

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `PORT` | `3000` | |
| `CORS_ORIGIN` | `http://localhost:5173` | HTTP와 socket.io 둘 다. 쉼표로 여러 개. 차량 앱 WebView는 `http://10.0.2.2:5173`(에뮬레이터)이나 맥북 IP에서 열린다 |
| `WEB_BASE_URL` | `http://localhost:5173` | 공유 링크 = `WEB_BASE_URL/t/{token}` |
| `KAKAO_REST_API_KEY` | (없음) | 카카오모빌리티 길찾기·카카오 로컬 검색. 없으면 직선 대체 경로·더미 장소 |

## HTTP API

에러는 Nest 기본 형식(`{ statusCode, message, error }`)이다. 요청 body는 `class-validator`로 검사하고, 정의되지 않은 필드가 있으면 400을 돌려준다.

### 운전자 (차량 앱)

`POST /trips`를 뺀 나머지는 `Authorization: Bearer <driverToken>`이 필요하다.

| 메서드 | 경로 | body | 응답 |
| --- | --- | --- | --- |
| POST | `/trips` | `{ vehicle, origin, stops, route? }` | 201 `{ tripId, driverToken, shares: [{ stopId, token, url }], route, stops }` |
| GET | `/trips/:tripId` | | 200 운전자 지도용 상태(아래) |
| POST | `/trips/:tripId/location` | `{ lat, lng, heading, currentLegEtaSeconds, currentStopOrder, ts }` | 204 |
| POST | `/trips/:tripId/events` | `{ type, stopOrder?, by? }` | 204 |

- `vehicle`: `{ model, colorName, colorHex, bodyType: 'sedan'\|'suv', plate }`
- `origin`: 운행을 시작할 때 차 위치. 첫 구간의 출발점이다.
- `stops[]`: `{ order, kind: 'waypoint'\|'pickup', label, position: LatLng, routeIndex? }`
  - `label`은 12자 이내. 이름·전화번호를 넣지 않는다.
  - 탑승자 시점 문구("내 위치", "다음 픽업")를 넣지 않는다. 웹이 탑승자마다 내 정류지는 "내 위치", 다른 픽업은 "픽업"으로 바꿔 보여주고, 경유지만 `label`을 그대로 쓴다.
- `route`(선택): 화면용 폴리라인. 차량 앱은 보내지 않는다. 보내면 각 정류지에 `routeIndex`가 있어야 하고, 구간 시간은 그래도 서버가 계산한다.
- 경로: 운행을 만들 때 `origin → 정류지들`을 순서대로 카카오모빌리티 다중 경유지 길찾기로 한 번 호출해 폴리라인과 구간별 소요 시간을 저장한다.
  키가 없거나 호출이 실패하면 정류지를 직선으로 잇고 `직선거리 × 1.3 ÷ 25km/h`로 구간 시간을 추정한다(개발·테스트용).
- `shares`는 pickup 정류지마다 하나씩 만들어진다. 토큰은 128비트 랜덤 값이다.
- 위치: 내장 내비에는 다음 정류지 하나만 목적지로 넣으므로, `currentLegEtaSeconds`는 `currentStopOrder`까지의 내비 ETA다. `ts`는 epoch ms.
- 이벤트 `type`
  - `stopArrived` (stopOrder): 그 정류지에 도착. 해당 탑승자는 `arrived`, 뒤 순번은 `atEarlierStop`.
  - `stopCompleted` (stopOrder, by): 그 정류지 완료. 픽업이면 공유 토큰이 만료되고, `currentStopOrder`가 다음 정류지로 넘어간다.
    - `by`: `door`(정차 중 운전석이 아닌 문이 열렸다 닫힘) · `button`(운전자의 [탑승 완료]/[다음으로]) · `departed`(경유지 도착 후 다시 출발)
  - `tripEnded`: 운행 종료. 모든 공유 토큰이 만료되고, 이후 위치·이벤트는 409.
- `stopCompleted`가 유실돼도 `currentStopOrder`가 그 정류지를 넘어가면 서버가 `by: departed`로 완료 처리한다.
- 운전자 지도용 응답(`POST /trips`의 `route`·`stops`, `GET /trips/:tripId`)
  - `GET`: `{ tripId, ended, routeSource: 'kakao'|'straight'|'client', route, car, currentStopOrder, currentLegEtaSeconds, stops }`
  - `stops[]`: `{ order, kind, label, position, routeIndex, legSeconds, status: 'upcoming'|'current'|'arrived'|'completed', etaSeconds, completedBy?, passengerLocation? }`
  - `etaSeconds`는 아래 "정류지별 ETA"와 같은 계산이다. `POST /trips` 응답에서는 위치가 없으므로 계획 ETA다.
  - `passengerLocation { lat, lng, at }`은 그 탑승자가 위치를 공유하는 동안에만 있다.
- 오류: 없는 운행 404, 토큰 불일치 401, 끝난 운행 409, 잘못된 body·stopOrder 400.

### 장소 검색 (차량 앱)

| 메서드 | 경로 | 응답 |
| --- | --- | --- |
| GET | `/places/search?query=&lat=&lng=` | 200 `[{ name, address, lat, lng }]` |

- 카카오 로컬 키워드 검색 프록시. `lat`/`lng`를 주면 그 위치에서 가까운 순으로 정렬한다.
- `KAKAO_REST_API_KEY`가 없으면 강남역 주변 고정 더미 장소를 돌려준다(이름이 맞는 것, 없으면 전부).
- 키가 있는데 카카오 호출이 실패하면 502. 장소를 지어내지 않는다.

### 탑승자 (웹)

| 메서드 | 경로 | body | 응답 |
| --- | --- | --- | --- |
| GET | `/share/:token` | | 200 `{ vehicle, route, stops, snapshot }` |
| POST | `/share/:token/replies` | `{ type: 'out'\|'late'\|'waitHere', position?: LatLng }` | 204 |
| POST | `/share/:token/location` | `{ lat, lng }` | 204 |
| DELETE | `/share/:token/location` | | 204 |

- `stops`는 모든 정류지를 순서대로 담고, 내 정류지만 `isMine: true`다.
- `snapshot.currentStop`은 차가 지금 향하거나 머무는 정류지 `{ order, kind, label }`다.
- 타입 기준은 `src/common/types.ts`다. `web/src/types.ts`는 이 파일과 똑같이 맞춘다.
- 탑승자 위치는 운전자 WS로 바로 넘기고, 운전자 `GET /trips/:tripId`용으로 마지막 한 점만 메모리에 둔다. 위치 공유를 끄면(`DELETE`) 또는 그 정류지가 끝나면 지운다.
- 없는 토큰 404, 만료된 토큰(탑승 완료·운행 종료) 410.

## WebSocket (socket.io, namespace `/live`)

연결한 뒤 `join`을 보낸다. 성공하면 ack로 `{ ok: true }`가 오고, 실패하면 `joinError { status, message }`를 받은 뒤 연결이 끊긴다.

| 역할 | 보내는 것 | 받는 것 |
| --- | --- | --- |
| 탑승자 | `join { role: 'passenger', token }` | `snapshot` (TripSnapshot). join 직후 한 번, 이후 위치·이벤트가 들어올 때마다 |
| 운전자 | `join { role: 'driver', tripId, driverToken }` | `reply { stopOrder, type, position? }`<br>`passengerLocation { stopOrder, lat, lng }`<br>`passengerLocationStopped { stopOrder }` |

- 토큰이 만료되면(`stopCompleted`, 차가 다음 정류지로 출발, 운행 종료) 마지막 `snapshot`을 보낸 뒤 서버가 연결을 끊고, 운전자에게 `passengerLocationStopped`를 보낸다.

## 정류지별 ETA

`src/trips/phase.ts`의 `etaToStop`(순수 함수)이 계산한다. 내비는 현재 목적지까지의 ETA만 주기 때문이다.

- 현재 정류지: `currentLegEtaSeconds`. 첫 위치 전이나 정류지를 막 완료한 직후에는 계획된 구간 시간을 쓴다.
- 그 뒤 정류지 N: `currentLegEtaSeconds` + (현재 정류지 다음부터 N까지의 구간 시간 합) + 앞 정류지마다 정차 여유 60초
  - 현재 정류지에 이미 도착해 있으면, 그 정류지의 정차 여유에서 기다린 시간을 뺀다.

## phase 규칙

`src/trips/phase.ts`의 `computePhase`(순수 함수)가 탑승자마다 정한다. 위에서부터 먼저 맞는 것을 쓴다.

1. 내 정류지에 `stopCompleted` → `boarded` (토큰 만료)
2. 차가 내 정류지를 지나 다음 정류지로 감(`currentStopOrder` > 내 순번) → `boarded`
3. 내 정류지에 `stopArrived` → `arrived`
4. 다음 정류지가 나이고 ETA ≤ 120초 → `near`
5. 다음 정류지가 내 앞 순번(경유지든 다른 탑승자든)이고 `stopArrived` 후 아직 `stopCompleted` 전 → `atEarlierStop`
6. 첫 정류지로 가는 중이고 출발점에서 150m 안(또는 첫 위치 전) → `departed`
7. 다음 정류지가 나 → `toMe`, 내 앞 순번 정류지 → `toEarlierStop`

## 구조

```
src/
  main.ts                     ValidationPipe, CORS(HTTP + socket.io)
  common/                     types.ts(web과 같은 타입), geo.ts, tokens.ts
  directions/
    kakao-directions.client.ts  KakaoDirectionsClient 인터페이스 + REST 구현
    route-planner.ts            카카오 → 실패 시 직선 대체
  trips/
    trips.controller.ts       /trips (운전자)
    share.controller.ts       /share (탑승자)
    driver-auth.guard.ts      Bearer driverToken 검사
    trips.service.ts          운행 상태 변경. 운행별 잠금으로 동시 요청을 순서대로 처리
    phase.ts                  computePhase, etaToStop, buildSnapshot (순수 함수)
    driver-view.ts            운전자 지도용 응답 (순수 함수)
    trip.repository.ts        TripRepository 인터페이스
    in-memory-trip.repository.ts
  live/live.gateway.ts        /live namespace
scripts/
  fake-driver.ts, watch-share.ts
```

저장소를 PostgreSQL·Redis로 바꿀 때는 `TripRepository`를 구현하고 `trips.module.ts`의 `TRIP_REPOSITORY` provider만 바꾸면 된다.
서버를 여러 대로 늘리면 운행별 잠금과 socket.io 방송도 프로세스 밖(Redis 등)으로 옮겨야 한다.
