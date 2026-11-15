# hey

<img src="docs/assets/icon.png" alt="hey 아이콘" width="120" />

## 1. 앱 이름과 한 줄 소개

**hey**: 픽업하러 가는 운전자의 위치·도착 예정 시간을 탑승자가 링크 하나로 실시간 확인하는 Pleos 차량 앱

- 운전자: Pleos Connect 차량 앱. 정차 중에 정류지를 넣고 출발하면, 주행 중에는 조작하지 않아도 운행이 진행됩니다.
- 탑승자: 앱 설치 없이 공유 링크로 여는 모바일 웹. 차가 어디쯤인지, 몇 분 뒤 도착하는지, 지금 어느 정류지에 들르는지 봅니다.

### 주요 기능

- 정류지 여러 곳(경유지 + 픽업) 순서대로 안내: 내비에는 다음 정류지 하나씩만 넣고, 끝나면 다음 정류지로 넘어갑니다.
- 탑승자 실시간 화면: 차 위치, 도착 예정 시간, 진행 단계(앞 정류지 들르는 중 → 나에게 오는 중 → 곧 도착 → 도착)
- 픽업마다 따로 만드는 공유 링크: 차 화면의 QR → 운전자 폰의 공유 시트. 그 정류지가 끝나면 링크가 만료됩니다.
- 탑승자 빠른 응답 [나왔어요] [5분 늦어요] [여기서 기다릴게요] → 차에서 TTS로 읽어 줌
- 자동 진행: 정류지 80m 안에 들어오면 도착, 픽업은 조수석·뒷좌석 문이 열렸다 닫히면 탑승 완료, 경유지는 다시 출발하면 완료
- 주행 중 입력 제한: 기어가 P가 아니면 차량 등록·운행 준비 화면을 잠급니다.
- 데모 모드: 가상 차량이 실제 경로를 따라 달립니다(에뮬레이터 시연용).

## 2. 실행 방법

심사용 APK는 맥북에서 띄운 서버와 웹에 붙습니다(에뮬레이터에서 본 맥북 주소 `10.0.2.2`). 아래 순서대로 실행해 주세요.

### 2-1. 서버 (포트 3000)

```bash
cd server
cp .env.example .env     # 카카오 키는 비워 둬도 동작합니다(아래 3장)
yarn install
yarn build
yarn start               # http://localhost:3000
```

### 2-2. 웹 (포트 5173)

```bash
cd web
cp .env.example .env
yarn install
yarn dev --host 127.0.0.1   # http://localhost:5173
```

`--host 127.0.0.1`은 에뮬레이터 안 차량 앱이 `http://10.0.2.2:5173`으로 웹(운전자 지도)을 열 수 있게 하는 옵션입니다.

### 2-3. 차량 앱

1. Pleos Connect 에뮬레이터를 **콜드 부트**로 켭니다(아래 3장).
2. APK를 설치합니다.
   ```bash
   adb install -r apk/choi_hey_release.apk
   ```
3. 앱 목록에서 **hey**를 엽니다. 위치·알림 권한을 허용해 주세요.

### 2-4. 데모 모드로 운행 한 바퀴

APK는 **데모 모드가 켜진 상태**로 시작합니다. 에뮬레이터의 내비 차량은 움직이지 않아서, 가상 차량이 서버가 만든 경로를 따라 달립니다. 데모 모드는 설정에서 끌 수 있습니다.

1. **차량 등록**: 차종, 색상, 차량번호를 넣고 [저장]
2. **정류지 추가**: 장소 검색(예: `스타벅스`) → 결과를 눌러 3곳을 추가합니다.
   - 첫 번째는 [경유지]로 바꿉니다(이름은 자동으로 채워짐).
   - 나머지 두 곳은 [픽업] 그대로 둡니다.
3. [운행 시작] → 공유 화면(픽업마다 QR) → [출발]
4. 자동 진행
   - 정류지에 닿으면 자동으로 도착 처리됩니다.
   - 경유지는 10초 뒤 가상 차량이 다시 출발해 자동 완료됩니다.
   - 픽업은 패널의 데모 버튼 [조수석 문 열기] → [조수석 문 닫기]를 누르면 3초 뒤 탑승 완료됩니다. [탑승 완료] 버튼도 됩니다.
   - 설정 > 데모 속도 **5x**로 바꾸면 빨리 볼 수 있습니다(운행 중에는 바꿀 수 없음).
5. 마지막 정류지가 끝나면 "운행이 끝났어요"가 나옵니다.

### 2-5. 탑승자 화면 보기

- **운행 중인 탑승자 화면**
  - 공유 화면의 QR은 `http://localhost:5173/send/{token}`을 담고 있습니다. 폰 카메라로 비추면 주소가 보이니, 그 주소를 맥 브라우저에 입력합니다.
  - 열린 페이지에 탑승자 링크 `http://localhost:5173/t/{token}`가 나옵니다. 그 링크를 열면 탑승자 화면입니다.
  - 브라우저 개발자 도구에서 모바일 화면 크기로 보면 실제와 같습니다.
  - 탑승자 화면의 [나왔어요] 등을 누르면 차량 앱이 소리 내어 읽습니다.
- **서버 없이 탑승자 화면만 보기**: 웹만 켜고 데모 링크를 엽니다.

  | 링크 | 상태 |
  | --- | --- |
  | http://localhost:5173/?state=toWaypoint&freeze=1 | 경유지(카페)로 가는 중 |
  | http://localhost:5173/?state=toMe&freeze=1 | 나에게 오는 중 |
  | http://localhost:5173/?state=near&freeze=1&me=1 | 곧 도착 (내 위치 공유) |
  | http://localhost:5173/?state=arrived&freeze=1&me=1 | 도착 |
  | http://localhost:5173/?state=boarded&freeze=1 | 탑승 완료 |

  `freeze=1`을 빼면 그 상태부터 시간이 흐릅니다.

## 3. 테스트 환경과 필요한 설정

| 항목 | 버전 / 값 |
| --- | --- |
| Node.js | 23.7.0에서 확인 (20 이상 권장) |
| yarn | 4.18.1 (`server/`, `web/`의 `packageManager`). `corepack enable` 후 각 폴더에서 `yarn` |
| yarn 설정 | `.yarnrc.yml`의 `nodeLinker: node-modules` |
| Android 빌드 | Gradle 9.4.1 (wrapper 포함, 데몬은 Java 21: `car-app/gradle/gradle-daemon-jvm.properties`), compileSdk 37 / targetSdk 34 / minSdk 28 |
| OS | macOS (Apple Silicon)에서 확인 |

### `.env`

`server/.env` (`server/.env.example` 복사)

| 키 | 설명 |
| --- | --- |
| `PORT` | 기본 3000 |
| `CORS_ORIGIN` | 웹 출처. 쉼표로 여러 개. 에뮬레이터 WebView를 위해 `http://10.0.2.2:5173`을 포함해야 합니다(예시 파일에 들어 있음). |
| `WEB_BASE_URL` | 공유 링크 앞부분. 기본 `http://localhost:5173` |
| `KAKAO_REST_API_KEY` | 카카오모빌리티 길찾기·장소 검색 REST 키. **비워 두면** 정류지를 직선으로 잇고 구간 시간을 추정하며, 장소 검색은 강남역 근처 더미 장소를 돌려줍니다. |

`web/.env` (`web/.env.example` 복사)

| 키 | 설명 |
| --- | --- |
| `VITE_KAKAO_MAP_KEY` | 카카오맵 JavaScript 키. **비워 두면** 카카오맵 대신 데모 지도(그림)로 보입니다. 키를 쓰면 카카오 개발자 콘솔의 웹 도메인에 `http://localhost:5173`, `http://10.0.2.2:5173`을 등록해야 합니다. |
| `VITE_API_BASE_URL`, `VITE_WS_URL` | 비워 두면 페이지를 연 호스트의 3000번 포트를 씁니다. 보통 비워 둡니다. |

### 에뮬레이터

- **콜드 부트로 켜 주세요** (빠른 부트 스냅샷에서 깨우지 않기).
  - Android Studio Device Manager에서 ⋮ > **Cold Boot Now**
  - 명령줄: `emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load`
- 차량 앱은 사용자 10(운전자)으로 실행됩니다. 권한 창이 뜨지 않으면:
  ```bash
  adb shell pm grant --user 10 com.choi.nawa pleos.car.permission.FUSED_LOCATION
  adb shell pm grant --user 10 com.choi.nawa android.permission.ACCESS_FINE_LOCATION
  adb shell pm grant --user 10 com.choi.nawa android.permission.POST_NOTIFICATIONS
  ```
- 패키지명은 `com.choi.nawa`입니다(개발 초기 이름. 앱 이름은 hey).

## 4. 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격

AVD 설정 파일(`~/.android/avd/Pleos_Connect_v2.1.0.avd/config.ini`)과 시스템 이미지 정보에서 읽은 값입니다.

| 항목 | 값 |
| --- | --- |
| AVD | Pleos Connect v2.1.0 (`Pleos_Connect_v2.1.0`) |
| 시스템 이미지 | `system-images/android-34/pleos_car_emulator/arm64-v8a` (Automotive, 42dot Inc.) |
| Android / API | Android 14 / API 34 |
| ABI | arm64-v8a |
| 해상도 | 2560 × 1440 (가로) |
| 밀도 | 160 dpi (mdpi) |
| RAM | 4096 MB |

## 5. APK 위치

[`apk/choi_hey_release.apk`](apk/choi_hey_release.apk) (release 빌드, 약 45MB)

- 서버·웹 주소는 에뮬레이터용 `http://10.0.2.2:3000` / `http://10.0.2.2:5173`
- 처음 실행할 때 데모 모드가 켜진 상태

## 6. 시연 영상

[<VIDEO_URL>](https://drive.google.com/file/d/1F4w6MMAA2GJgViHZHDfkeBA6YCG-b183/view?usp=sharing)

---

## 레포 구조

| 폴더 | 내용 |
| --- | --- |
| `car-app/` | 차량 앱. Kotlin, Jetpack Compose, Pleos Connect SDK (NaviHelper, Vehicle, FusedLocation, TextToSpeech) |
| `server/` | NestJS 서버. 운행·정류지·공유 링크, 카카오모빌리티 길찾기, socket.io 실시간 푸시 (메모리 저장소) |
| `web/` | 탑승자 모바일 웹과 운전자 지도. React, Vite, TypeScript, Tailwind |
| `apk/` | 제출용 release APK |
| `docs/` | [Pleos SDK 검증 결과](docs/sdk-findings.md), 아이콘 원본(`docs/assets/icon.png`) |

Pleos SDK가 에뮬레이터에서 실제로 어떻게 동작하는지(내비 도착 콜백, 문·기어 값, TTS, 위치)는 [docs/sdk-findings.md](docs/sdk-findings.md)에 정리했습니다.
