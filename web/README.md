# hey — 탑승자 웹

픽업 차량의 위치, 도착 예정 시간, 진행 상태를 보여주는 탑승자용 모바일 웹입니다. 공유 링크로 열리며 앱 설치가 필요 없습니다.

지금은 서버 없이 더미 데이터(`src/data/dummyTrip.ts`)와 시뮬레이션(`src/hooks/useTripSimulation.ts`)으로 동작합니다.

## 실행

```bash
yarn
yarn dev           # http://localhost:5173
```

## 데모 상태 고정 (기획서 캡처용)

| URL | 화면 |
| --- | --- |
| `/?state=toWaypoint&freeze=1` | 경유지(카페)로 가는 중 |
| `/?state=atWaypoint&freeze=1` | 경유지에 들른 상태 |
| `/?state=toMe&freeze=1` | 나에게 오는 중 |
| `/?state=near&freeze=1&me=1` | 곧 도착, 나와 주세요 (내 위치 공유) |
| `/?state=arrived&freeze=1&me=1` | 도착 |
| `/?state=boarded&freeze=1` | 탑승 완료, 공유 종료 |

`freeze=1`을 빼면 그 상태부터 시간이 흐릅니다. 3초마다 위치가 갱신되고, 실제보다 12배 빠르게 진행됩니다.

## 카카오맵

`.env.example`을 `.env`로 복사하고 `VITE_KAKAO_MAP_KEY`에 카카오 JavaScript 키를 넣으면 실제 지도로 바뀝니다. 키가 없으면 데모 지도가 나옵니다.

- [카카오 개발자 콘솔](https://developers.kakao.com)에서 앱을 만들고 JavaScript 키를 발급받습니다.
- 플랫폼 > Web에 `http://localhost:5173`과 배포 도메인을 등록합니다.

## 구조

```
src/
  App.tsx                    모바일 프레임(최대 430px), 화면 구성
  components/StatusBanner    ETA·상태 배너, 정류지 진행 표시
  components/map/            MapView(카카오/데모 전환), 마커
  components/VehicleCard     차량 실루엣, 차종, 색상, 번호판
  components/QuickReplies    나왔어요 / 5분 늦어요 / 여기서 기다릴게요
  components/ui/             shadcn/ui 컴포넌트 (`npx shadcn add ...`로 추가)
  hooks/useTripSimulation    더미 스트림. 서버가 생기면 WebSocket 구독 훅으로 교체
  types.ts                   TripSnapshot 등 서버와 공유할 타입
```
