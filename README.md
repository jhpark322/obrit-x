# ORBIT X

ORBIT X는 Pleos Connect에서 주행 효율을 확인하고 차량 기능을 체험하는 Android Automotive 앱입니다. PLAY에서 드라이브와 미션을 진행하고, X-LAB에서 기능을 비교하거나 의견을 남기면 MY ORBIT에서 참여 기록과 시연 포인트를 확인할 수 있습니다.

[빌드 및 연결](#빌드-및-연결) · [효율 리그](#주행-효율-리그) · [드라이브](#드라이브) · [공조와 미션](#공조와-미션) · [기록 초기화](#기록-초기화)

## 빌드 및 연결

1. Android Studio에서 저장소 루트를 열고 Android SDK API 37을 설치합니다. Gradle Wrapper는 9.6.0이고 프로젝트의 Gradle 데몬 JVM은 Java 25를 사용합니다.
2. Android Studio Device Manager에서 **Pleos Connect v2.1.0** 가상 기기를 실행합니다. Gradle을 동기화하고 Run 대상으로 Pleos 가상 기기를 선택해 **app**을 실행합니다.
3. 터미널에서 다음 명령으로 디버그 APK를 빌드하거나, 빌드된 APK를 설치할 수 있습니다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
adb devices
adb -s emulator-5554 install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
```

기기 번호와 사용자 번호는 환경에 맞춰 바꿉니다. 활성 사용자 번호는 `adb -s emulator-5554 shell am get-current-user`로 확인합니다. `adb`를 찾을 수 없으면 Android SDK의 `platform-tools` 폴더에서 실행하거나 해당 폴더를 PATH에 추가합니다. Windows 기본 SDK 경로는 `%LOCALAPPDATA%\Android\Sdk`입니다.

### Virtual Console 열기

에뮬레이터를 켜 둔 상태에서 포트를 연결한 뒤 PC 브라우저에서 [Pleos Virtual Console](http://127.0.0.1:8088/)을 엽니다. 가상 기기가 하나만 연결돼 있으면 `-s emulator-5554`를 생략할 수 있습니다.

```powershell
adb -s emulator-5554 forward tcp:8088 tcp:8088
```

콘솔에서 **Emulation Controls**를 열고 왼쪽 도메인을 선택하거나 검색창에 아래 카드 이름을 입력합니다. ORBIT X는 에뮬레이터 화면에 띄워 둡니다. [Pleos 콘솔 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)

| 확인할 기능 | 콘솔에서 조작할 카드 | ORBIT X에서 볼 화면 |
| --- | --- | --- |
| 전기차 추정 전비 | **Motion → Odometer**, **Energy → Battery Energy** | PLAY → 주행 효율 리그 |
| 저잔량 추천 | **Energy → Battery SOC** | PLAY → 드라이브 |
| 동승자 추천 및 공조 | **Convenience → Seat Occupancy** | PLAY → 드라이브 또는 X-LAB → 착석 연동 공조 |
| 주행 제한 | **Motion → Gear Selection** | ORBIT X 화면을 띄운 상태에서 P → D → P |
| 앞좌석 SYNC 감지 | Pleos 순정 공조의 **Sync**. 콘솔 **Climate → HVAC Dual**로도 변경을 시도할 수 있습니다. | PLAY → 미션 → 앞좌석 온도 동기화 써보기 |
| 도어 잠금 OTA 평가와 모의 도착 | ORBIT X 화면 안의 버튼 | X-LAB → OTA 업데이트 평가 또는 PLAY → 드라이브 |

카드별 설정 범위는 공식 콘솔 가이드에서 확인할 수 있습니다. [Pleos 콘솔 카드 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)

## 주행 효율 리그

### 아이오닉 5 추정 전비 측정

**PLAY → 주행 효율 리그 → 이달 내 주행 기록**을 띄웁니다. 기어는 **P**에 두면 됩니다. 화면의 **계기판 N km**가 누적거리 기준이고, **이달 주행거리**는 ORBIT X가 이번 달에 집계한 거리입니다.

1. **Energy → Battery Energy**에 **60.0 kWh**를 설정합니다. 카드의 적용 결과를 확인한 뒤 약 5초 기다려 앱이 시작 값을 읽게 합니다.
2. **Motion → Odometer**를 현재 계기판 값보다 **12 km 높게** 설정하고, **Battery Energy**를 **58.0 kWh**로 낮춥니다. 앱은 약 3초 간격으로 신호를 읽으므로 두 값을 가능한 한 연달아 바꿉니다.
3. 몇 초 뒤 **이달 추정 전비**와 계산에 포함된 거리를 봅니다. 기존 월간 측정이 없고 두 변경을 같은 구간으로 읽었다면 **12 km ÷ 2.0 kWh = 6.0 km/kWh**입니다. 기존 측정이 있으면 월간 합계에 더해집니다.
4. 충전을 시연하려면 거리를 그대로 두고 **Battery Energy**를 **70.0 kWh**로 올린 뒤 기다립니다. 이후 거리를 **6 km** 더 올리고 배터리를 **68.0 kWh**로 낮추면, 위 두 구간의 합계는 **18 km ÷ 4.0 kWh = 4.5 km/kWh**입니다.

**Odometer에 HTTP 400이 뜨면** 먼저 앱의 **계기판** 값을 확인하고 그 값보다 큰 정수 km를 입력합니다. 예를 들어 현재 12,000 km면 12,012 km를 사용합니다. 이미 늘린 누적거리를 0으로 되돌리면 이 환경에서 요청이 거절될 수 있습니다. 화면 아래의 슬라이더 숫자만 바뀌고 오류가 남아 있으면 적용된 거리로 보지 말고 앱의 계기판 값으로 확인합니다.

거리만 늘고 전비가 계속 수집 중이면 다음 구간을 다시 입력합니다. 거리 증가와 배터리 감소가 서로 다른 샘플에 들어가면 소비량이 해당 거리와 짝지어지지 않을 수 있습니다. 앱을 화면에 유지한 채 현재 값에서 새 기준을 잡고 두 값을 연달아 바꿉니다. 콘솔에서 **Battery SOC**, **Remaining Range**, **Battery Capacity**, **Cruise Target Speed**는 각각 다른 신호입니다. 전비 시연에는 **Odometer와 Battery Energy**를 사용합니다.

### 가솔린·하이브리드 연비와 순위

지원 차량에서는 **연료량 읽기 허용**을 누른 뒤 AAOS `FUEL_LEVEL` 신호가 들어오면, 월간 관측 거리와 같은 구간의 추정 소비 연료를 사용해 **km/L**를 계산합니다. 예를 들어 12 km를 이동하며 1.0 L를 사용한 구간은 12.0 km/L입니다. 아이오닉 5 기본 Pleos 에뮬레이터의 위 콘솔 카드로는 **km/kWh**를 재현할 수 있습니다. 가솔린·하이브리드 연비 측정에는 연료량을 제공하는 차량 또는 별도 테스트 환경이 필요합니다. [AAOS 차량 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds#FUEL_LEVEL)

- **월 500 km**는 순위 참여를 설명하는 거리 기준입니다. 추정치는 소비량이 충분하면 그 전부터 표시됩니다. 현재 표시 기준은 전기 0.2 kWh 이상, 연료 0.5 L 이상입니다.
- **내 차종 리그**는 차량 기록과 해당 차종의 시연 순위를 보여줍니다. **다른 리그 구경하기**에서는 다른 차종의 예시 순위를 확인합니다.
- 순위만 빠르게 보여주려면 **예시 기록으로 시연 → 예시 주행 +24 km**를 이용합니다. 500 km에 도달하면 저장한 이름과 예시 전비 6.0 km/kWh가 시연 순위에 표시됩니다.
- 월간 거리는 월별 기준점부터 집계합니다. 효율은 연속해서 읽은 구간으로 계산하며 앱이 중단됐거나 신호가 끊긴 동안의 소비량은 소급 계산하지 않습니다.

## 드라이브

1. 기어를 **P**에 두고 **PLAY → 드라이브**를 엽니다.
2. 저잔량 추천을 보려면 **Energy → Battery SOC**를 **20%**로 설정합니다. 앱은 25% 이하에서 **충전 들르기**를 우선 추천합니다.
3. 동승자 추천을 보려면 SOC를 25%보다 높게 바꾸고 **Convenience → Seat Occupancy → Passenger**를 **Occupied**로 설정합니다. 앱에서 동승자 추천이 뜨는지 확인합니다. 기본 풍경 추천은 **Passenger → Empty** 상태에서 확인할 수 있습니다.
4. 순정 내비를 시연하려면 모드를 시작하고 내비에서 길 안내를 시작합니다. ORBIT X로 돌아와 **현재 목적지 연결**을 누릅니다. 연결된 목적지의 실제 도착 이벤트가 들어오면 도착 기록을 남깁니다.
5. 내비 연결 없이 완료 흐름을 보여주려면 목적지를 연결하기 전에 **도착 시연하기**를 누르고 화면 안내에 따라 기록을 완료합니다. 결과는 **MY ORBIT → 최근 참여 내역**에서 확인합니다.

기어에 따른 화면 전환만 확인하려면 **Motion → Gear Selection**에서 **D**로 바꿔 주행 제한을 확인한 다음 **P**로 돌아옵니다. 실제 내비의 목적지 연결과 도착 이벤트는 에뮬레이터의 내비 서비스 상태에 따라 달라집니다.

## 공조와 미션

### 착석 연동 공조

1. **Motion → Gear Selection**을 **P**로 둡니다.
2. **X-LAB → 착석 연동 공조 → 자동 공조**에서 **착석 연동 공조** 스위치를 켭니다.
3. 콘솔 **Convenience → Seat Occupancy**에서 운전석과 동승석을 **Empty**에서 **Occupied**로 한 자리씩 바꿉니다. 뒷좌석도 좌우를 따로 확인할 수 있습니다.
4. ORBIT X의 좌석별 착석 표시와 **A/C ON/OFF**를 확인합니다. 공조는 차량이 제어를 허용한 구역 단위로 동작합니다. 같은 구역을 공유하는 좌석은 함께 반응할 수 있습니다.
5. 장기 미션의 첫날을 보여주려면 주차 중 자동 제어를 켜고, 착석한 구역의 공조가 확인된 상태로 이 화면을 **1분간** 유지합니다. **PLAY → 미션**에서 진행도를 확인합니다. 하루 한 번, 서로 다른 7일을 기록한 뒤 의견을 제출하는 미션입니다.

### 수동 전환과 자동 전환 비교

**X-LAB → 착석 연동 공조 → 개선안 비교 → A/B 비교 시작**을 선택합니다. 앱에 표시된 순서로 두 방식을 진행합니다.

- **수동 전환**은 화면의 탑승·하차 시연과 앞좌석 전체·운전석 전용 변경 버튼을 사용합니다.
- **자동 전환**은 콘솔에서 동승석을 먼저 **Empty**로 두고, 화면 안내에 맞춰 **Occupied**, 다시 **Empty**로 바꿉니다. 화면의 **탑승 시연**과 **하차 시연**으로도 완료할 수 있습니다.
- 두 방식을 마친 뒤 선호와 이유를 선택하면 조작 횟수와 시간이 기록됩니다. 비교 화면의 공조 전환은 앱 내 시연입니다.

### 앞좌석 SYNC와 도어 잠금 OTA

- **앞좌석 온도 동기화**는 **PLAY → 미션 → 앞좌석 온도 동기화 써보기**에서 현재 상태가 표시될 때까지 기다립니다. Pleos 하단의 온도 숫자를 눌러 순정 공조를 열고 **Sync**를 한 번 바꿉니다. 앱에서 **SYNC 변경 확인**이 뜨면 이유를 선택하고 응답을 제출합니다.
- **도어 잠금 OTA**는 **X-LAB → OTA 업데이트 평가** 또는 PLAY 미션에서 엽니다. **업데이트 후** 화면에서 전체 도어 잠금과 해제를 한 번씩 체험하고 평가를 제출합니다. 이 화면은 업데이트 UI를 앱에서 재현합니다.
- **아이디어·피드백**은 **X-LAB → 아이디어·피드백**에서 의견을 작성하고 제출합니다. 시연 포인트와 제출 내역은 **MY ORBIT**에서 확인합니다.

## 기록 초기화

**MY ORBIT → 기록 관리 → 기록 초기화**를 누르고 확인 창에서 **초기화**를 선택합니다. 미션 진행 및 완료 내역, 체험 의견, 시연 포인트, 앱의 이달 주행·리그 기록이 초기화됩니다. 저장한 이름과 착석 연동 공조 설정은 유지됩니다. 다음 측정은 콘솔에서 현재 계기판 값부터 시작합니다.

## SDK와 검증 범위

| 연동 | 사용하는 신호와 화면 |
| --- | --- |
| Pleos Vehicle SDK 2.3.2 | 기어, 착석, 공조, 누적거리, 배터리, 차량 제공 효율 |
| Pleos NaviHelper 3.0.5 | 순정 내비 화면 요청, 목적지 연결, 도착 이벤트 |
| Android Automotive OS | 주행 제한, 차량 모델, 지원 차량의 연료량 |

리그 순위와 포인트는 시연용입니다. 순정 내비의 전체 도착 흐름과 실차 연료량 기반 연비는 추가 검증이 필요합니다. 블루멤버스 전환과 순정 내비 내 추천 카드는 향후 제휴·플랫폼 연동 범위입니다. [Pleos Connect SDK](https://document.pleos.ai/en/api-reference/connect-sdk-pleos) · [NaviHelper](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/intro)
