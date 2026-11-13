# ORBIT X

ORBIT X는 운전자가 Pleos Connect에서 차량 기능을 체험하고 의견을 남기면 포인트를 받는 차량용 앱입니다. 고객의 사용 경험을 현대자동차그룹의 기능 개선에 활용하는 모델을 제안합니다.

## 주요 기능

- **PLAY**에서 내 차종 리그, 이번 달 주행거리와 추정 전비·연비를 확인합니다. 월 500 km는 순위 등록 거리 기준입니다. 다른 차종의 순위는 별도 화면에서 둘러볼 수 있습니다.
- **드라이브**는 착석, 배터리, 최근 방문 패턴에 따라 모드를 추천합니다. 사용자가 시작하고 Pleos 내비 목적지를 연결합니다.
- **X-LAB**은 착석 연동 공조, 개선안 A/B 비교, 도어 잠금 OTA 체험 및 평가를 제공합니다. OTA 체험은 앱 안에서 화면을 재현합니다.
- **MY ORBIT**에서 미션 완료 기록과 시연 포인트를 확인합니다. 착석 연동 공조 장기 미션은 서로 다른 7일의 사용을 기록합니다.

## 개발 환경

- Android Studio와 Gradle Wrapper 9.6.0을 사용합니다. Android SDK API 37을 설치합니다.
- Pleos Connect v2.1.0 에뮬레이터와 Virtual Console을 사용합니다.
- Vehicle SDK 2.3.2와 NaviHelper 3.0.5는 Pleos Maven 저장소에서 가져옵니다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. Pleos 에뮬레이터를 실행한 뒤 Android Studio에서 `app`을 실행합니다.

## 시연 방법

1. 기어를 P에 두고 **PLAY → 주행 효율 리그**를 엽니다. 차량 신호를 사용할 수 있으면 이달 주행 기록이 표시됩니다. 예시 데이터는 **예시 기록으로 시연**에서 확인합니다.
2. **X-LAB → 착석 연동 공조**에서 스위치를 켠 뒤 Virtual Console의 착석 값을 바꿉니다. 지원 구역의 공조와 A/C 상태를 확인합니다.
3. **X-LAB → OTA 업데이트 평가**에서 잠금과 해제를 체험하고 의견을 남깁니다. 적립 결과는 **MY ORBIT**에서 볼 수 있습니다.
4. 기어를 D로 전환하면 주행 제한 화면이 표시됩니다. 조작을 이어가려면 P로 돌아갑니다.

## SDK와 검증 범위

Vehicle SDK로 기어, 좌석, 공조, 누적 주행거리, 배터리와 차량 제공 효율 신호를 읽습니다. AAOS의 `INFO_MODEL`은 차종 식별에 사용하고, 지원 차종에서 `FUEL_LEVEL` 읽기 권한을 허용하면 월 추정 연비를 계산합니다. 차량이 자체 집계한 전비·연비는 참고값으로 표시합니다.

NaviHelper 연동은 순정 내비 요청, 목적지 조회와 도착 이벤트를 사용합니다. 실제 내비의 전체 도착 흐름과 가솔린·하이브리드 실차 연비는 추가 검증이 필요합니다. 리그 순위와 포인트는 시연용이며, 블루멤버스 전환과 순정 내비 내 추천 카드는 향후 제휴·플랫폼 연동 범위입니다.

- [Pleos Connect SDK](https://document.pleos.ai/en/api-reference/connect-sdk-pleos)
- [Pleos Connect Virtual Console](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)
