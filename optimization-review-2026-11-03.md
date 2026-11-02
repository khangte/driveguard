# DrowsyDrive 추가 최적화 검토

검토일: 2026-11-03

## 핵심 요약

추가 개선점은 알람 준비 상태, 위치 요청의 실패 처리, 지도 초기화 중 위치 전달, 위치 갱신 비용에 집중되어 있다. 먼저 경고 유실과 위치 상태 오류를 보완하고, 이후 전력 사용과 미사용 코드를 정리하는 순서를 권장한다.

1~6번 항목은 정적 코드 검토 결과다. 검토 당시에는 코드 수정, 테스트 재실행, 실기기 CPU·발열·배터리 측정을 수행하지 않았다. 이후 같은 날 검토 내용을 코드에 반영했으며, 그 결과는 아래 [반영 결과](#반영-결과-2026-11-03) 절에 따로 정리했다. 성능 향상률과 전력 절감률은 반영 후에도 측정하지 않았다.

## 반영 결과 (2026-11-03)

브랜치 `refactor/20261102`의 작업 트리에 반영했다. 아직 커밋하지 않았다.

### 검증 범위

- 단위 테스트, `assembleDebug`, `lintDebug` 모두 통과했다. lint의 UnusedResources 경고는 0건이다.
- 새 테스트를 추가했다.
  - `ReadyGateTest`(6개): 준비 완료 시 한 번 전달, 교체·취소·실패·해제 후 늦은 콜백 무시
  - `LocationUpdatePolicyTest`(3개): 유효시간 경계, 정상 주기 위치 보존
- 실기기 테스트와 전력 측정은 하지 않았다. 단위 테스트는 순수 로직만 다루며, SoundPool·TTS·FusedLocationProvider·KakaoMap과 연결되는 경로는 검증하지 않았다.
- 저장소에 `gradle/wrapper/gradle-wrapper.jar`가 없어 `gradlew`가 실행되지 않는다. `~/.gradle`에 캐시된 Gradle 9.6.0과 Android Studio JBR로 빌드했다.

### 항목별 반영 내용

| 항목 | 상태 | 변경 내용 | 남은 확인 |
|---|---|---|---|
| 1. 알람 초기화 전 재생 요청 유실 | 반영 | 새 `ReadyGate`가 준비 상태(대기/완료/실패/해제)와 마지막 요청 한 개를 관리한다. `SoundManager`는 `OnLoadCompleteListener`, `TTSManager`는 초기화 콜백(메인 스레드로 전달)에서 보관한 요청을 재생한다. 새 경고가 이전 예약을 교체하고, 경고 종료(`stopAlarm()`·`cancelPending()`)나 해제 시 예약을 취소한다. `WarningAlarmController`는 `playAlarm()` 실패를 로그로 남긴다. | 실기기에서 첫 경고가 초기화보다 빠른 경우 재현 |
| 2. 위치 요청 실패 후 상태 복구 | 반영 | `LocationProvider`가 `start()`/`stop()`마다 요청 세대를 증가시킨다. 비동기 실패 시 요청 중 상태를 풀어 다음 `start()`에서 재요청할 수 있다. 이전 세대의 늦은 완료는 무시하고, `stop()` 이후 위치 콜백은 차단한다. 등록 해제 실패는 로그만 남기고 재시도하지 않는다. | 요청 실패 후 재개, 요청 완료 전 중단 시나리오 실기기 확인 |
| 3. 지도 로딩 중 최신 위치 전달 | 반영 | `MapManager`가 최신 위치를 보관하고, `onMapReady()`에서 그 좌표로 마커를 생성한다. | 좌표 A로 시작 → 준비 전 좌표 B 수신 → 마커가 B에 표시되는지 확인 |
| 4. 메인 화면 고정밀 위치 비용 | 부분 반영 | 새 `LocationUpdatePolicy`로 화면별 정책을 나눴다. 메인은 `PRIORITY_BALANCED_POWER_ACCURACY`·10초 간격, 지도는 `PRIORITY_HIGH_ACCURACY`·5초 간격이다. | 전력 절감 효과 미측정. 정확도가 약 100m 수준으로 낮아지므로 휴게소 검색 정확도 확인 필요. 캐시가 오래된 경우 검색 직전에 고정밀로 다시 받는 기능은 구현하지 않았다. |
| 5. 위치 배치와 유효시간 기준 | 반영 | 배치 전달을 끄고(`setMaxUpdateDelayMillis(0)`) 수신 목록 중 최신 위치 한 개만 전달한다. 유효시간은 요청 간격의 2배로 맞췄다. 위치 나이는 `elapsedRealtimeNanos` 기준으로 계산하고, 이 값이 없는 위치만 시스템 시각으로 판정한다. | 지도 화면 배치 해제로 인한 전력 증가 여부 측정 |
| 6. 미사용 코드와 리소스 정리 | 반영 | `activity_main.xml`, `activity_map.xml`, `ic_menu.png`, `REST_API_KEY` BuildConfig 필드, `MapManager.onDestroy()`와 호출부를 삭제했다. 레이아웃 삭제로 미사용이 된 `current_location` 문자열과 이전부터 미사용이던 `request_guide` 문자열도 삭제했다. | `local.properties`의 `REST_API_KEY` 값은 그대로 두었다. APK 크기 감소량은 측정하지 않았다. |

### 변경 파일

- 추가: `alert/ReadyGate.java`, `location/LocationUpdatePolicy.java`, `test/.../alert/ReadyGateTest.java`, `test/.../location/LocationUpdatePolicyTest.java`
- 수정: `SoundManager.java`, `TTSManager.java`, `WarningAlarmController.java`, `LocationProvider.java`, `MainActivity.kt`, `MapActivity.kt`, `MapManager.kt`, `app/build.gradle`, `res/values/strings.xml`
- 삭제: `res/layout/activity_main.xml`, `res/layout/activity_map.xml`, `res/drawable/ic_menu.png`

## 1. 알람 초기화 전 재생 요청 유실

우선순위: 높음 — 조건부로 발생 가능한 경고 전달 문제

관련 파일:

- [SoundManager.java](app/src/main/java/com/example/drowsydrive/alert/SoundManager.java)
- [TTSManager.java](app/src/main/java/com/example/drowsydrive/alert/TTSManager.java)
- [WarningAlarmController.java](app/src/main/java/com/example/drowsydrive/alert/WarningAlarmController.java)

### 확인한 코드

- SoundPool 로드 완료를 확인하는 리스너가 없다.
- playAlarm()은 재생 실패 시 false를 반환하지만 알람 컨트롤러는 반환값을 사용하지 않는다.
- TTSManager.speak()는 초기화 전 요청을 로그만 남기고 버린다.
- 감지기는 에피소드당 한 번만 경고를 전달하므로 다음 프레임의 자동 재시도를 기대할 수 없다.

### 영향

사운드 로드 또는 음성 엔진 초기화가 첫 경고보다 늦으면 다이얼로그만 표시되고 경고음·음성이 누락될 수 있다. 실제 발생 빈도는 측정하지 않았다.

### 권장 조치

- 사운드 로드와 TTS 초기화 성공·실패 상태를 관리한다.
- 준비 전 요청은 현재 유효한 경고로 보관하고 준비 완료 시 재생한다.
- 경고가 종료·교체되거나 컨트롤러가 해제되면 이전 재생 예약을 무효화한다.
- 메인 스레드를 기다리게 하거나 무제한 재시도하지 않는다.

### 검증

- 준비 전 경고 후 초기화 완료 시 유효한 경고가 한 번 재생되는지 확인한다.
- 초기화 전에 경고를 종료·교체했을 때 이전 음성이 재생되지 않는지 확인한다.
- 초기화 실패 및 리소스 해제 후 콜백 경로를 검증한다.

참고: [SoundPool.OnLoadCompleteListener](https://developer.android.com/reference/android/media/SoundPool.OnLoadCompleteListener)

## 2. 위치 업데이트 요청 실패 후 상태 복구 누락

우선순위: 중간 — 비동기 오류 처리

관련 파일: [LocationProvider.java](app/src/main/java/com/example/drowsydrive/location/LocationProvider.java)

### 확인한 코드

requestLocationUpdates()의 반환 Task를 확인하지 않고 requestingLocationUpdates를 즉시 true로 설정한다. 동기 SecurityException은 처리하지만 비동기 실패는 처리하지 않는다. 이후 start()는 요청 중 플래그가 true이면 반환한다.

### 영향

비동기 요청 실패 시 실제 업데이트가 등록되지 않았는데 요청 중으로 남아 같은 화면 세션의 재시작을 막을 수 있다.

### 권장 조치

- 요청 대기·성공·실패 상태를 구분하고 실패 시 재요청 가능한 상태로 복구한다.
- stop() 이후 이전 요청의 성공·실패 콜백이 새 상태를 덮어쓰지 않도록 요청 세대를 확인한다.
- 이미 대기 중이거나 중단 전에 큐에 들어온 위치 콜백이 비활성 화면에 전달되지 않도록 가드한다.
- 등록 해제 실패도 확인하고 무제한 재시도는 피한다.

### 검증

요청 실패 후 재요청, 요청 완료 전 중단, 중단 직후 재개, 이전 요청의 늦은 완료·위치 콜백을 검증한다.

참고: [FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)

## 3. 지도 로딩 중 최신 위치 전달 누락

우선순위: 중간 — 지도 준비 순서에 따른 위치 표시 오류

관련 파일:

- [MapActivity.kt](app/src/main/java/com/example/drowsydrive/ui/map/MapActivity.kt)
- [MapManager.kt](app/src/main/java/com/example/drowsydrive/ui/map/MapManager.kt)

### 확인한 코드

- isStarted는 지도 준비 완료 전, start 요청 시점부터 true다.
- 준비 중 위치 갱신은 updateLabelPosition()으로 전달되지만 centerLabel이 없으면 무시된다.
- onMapReady()에서는 초기 startPosition으로 마커를 생성한다.

### 영향

지도 로딩 중 받은 최신 좌표가 초기 마커에 반영되지 않는다. 다음 위치 콜백까지 과거 좌표를 표시할 수 있다.

### 권장 조치

최신 위치를 보관하고 마커를 생성한 직후 반영한다. 지도 시작 요청과 준비 완료를 구분하며, 필요 이상으로 상태 변수를 중복하지 않는다.

### 검증

좌표 A로 초기화 → 준비 전 좌표 B 수신 → 지도 준비 완료 순서를 재현하고, 추가 위치 콜백 없이 마커가 B에 표시되는지 확인한다.

## 4. 메인 화면의 고정밀 위치 갱신 비용

우선순위: 최적화 후보 — 효과 측정 필요

관련 파일:

- [MainActivity.kt](app/src/main/java/com/example/drowsydrive/ui/main/MainActivity.kt)
- [LocationProvider.java](app/src/main/java/com/example/drowsydrive/location/LocationProvider.java)

### 확인한 코드

메인 화면도 PRIORITY_HIGH_ACCURACY로 5초 간격의 위치 업데이트를 요청한다. 메인 화면에서 위치는 검색과 지도 진입에 사용하며 실시간 경로를 표시하지 않는다.

### 선택지 비교

| 선택지 | 장점 | 비용·한계 |
|---|---|---|
| 현재 5초 고정밀 갱신 유지 | 검색·지도 진입 시 비교적 최근 좌표를 바로 사용 | 위치 사용 빈도와 관계없이 높은 정확도를 요청 |
| 메인은 저전력 위치·캐시, 검색·지도 진입 시 필요하면 정밀 갱신 | 사용 목적에 맞춰 고정밀 요청을 줄일 수 있음 | 캐시가 오래되면 버튼 선택 후 위치 대기 시간이 생길 수 있음 |

두 번째 방식을 권장한다. 캐시의 나이와 정확도를 함께 확인하고, 고정밀 연속 갱신은 지도 화면 등 필요한 구간에 제한한다. 위치를 바꾸는 정책은 휴게소 검색의 정확도를 유지하는 조건으로 검증해야 한다.

5초는 요청 간격이며 실제 수신 빈도를 보장하지 않는다. 절감률은 실기기 비교 전에는 제시할 수 없다.

### 검증

동일 기기·이동 조건·사용 시간에서 위치 콜백 수, 검색 대기 시간, 검색 좌표의 나이·정확도, 전력 사용을 비교한다.

참고: [Android 위치와 배터리 지침](https://developer.android.com/develop/sensors-and-location/location/battery)

## 5. 위치 배치와 유효시간 기준 정리

우선순위: 중간 — 버리는 작업 감소 및 시간 판정 개선

관련 파일: [LocationProvider.java](app/src/main/java/com/example/drowsydrive/location/LocationProvider.java)

### 확인한 코드

- 최대 10초의 묶음 전달을 허용한다.
- 5초 이상 지난 위치는 폐기한다.
- 수신 목록의 위치를 모두 순회하며 유효한 위치마다 화면 콜백을 호출한다.
- 위치 나이를 System.currentTimeMillis()와 Location.getTime()으로 계산한다.

### 영향

묶음 전달이 발생하면 의도적으로 지연 수신한 위치 중 일부를 다시 버릴 수 있다. 현재 위치만 필요한 화면에 여러 과거 좌표를 전달하는 작업도 발생한다. 시스템 시각이 변경되면 위치 나이 판정이 부정확해질 수 있다.

### 권장 조치

- 현재 위치가 필요한 화면에는 최신 유효 위치 한 개만 전달한다.
- 지도용 배치 지연과 위치 유효시간을 일관되게 설정한다.
- 위치 나이는 SystemClock.elapsedRealtimeNanos()와 Location.getElapsedRealtimeNanos()처럼 같은 단조 증가 시계로 비교한다.
- 절전 목적의 배치를 줄이면 전력 비용이 늘 수 있으므로, 4번 정책과 함께 조정한다.

### 검증

배치 위치 여러 개, 오래된 위치만 있는 경우, 시스템 시각 변경, 유효시간 경계를 검증한다.

## 6. 미사용 코드와 리소스 정리

우선순위: 낮음 — 유지보수와 빌드 산출물 정리

### 제거 후보

| 대상 | 확인 근거 | 조치 |
|---|---|---|
| activity_main.xml, activity_map.xml | 기존 lint 보고서에서 미사용 표시, 앱 소스 참조 검색에서도 사용을 발견하지 못함 | 동적 리소스 참조 여부까지 확인한 뒤 제거 |
| ic_menu.png | 기존 lint 보고서에서 미사용 표시 | 참조 확인 후 제거 |
| REST_API_KEY BuildConfig 필드 | 앱 소스에서 사용을 발견하지 못함 | app/build.gradle의 생성 선언 제거 검토 |
| MapManager.onDestroy()의 destroyDrawingCache() | 지도 엔진 종료 API가 아닌 View 캐시 정리 호출만 수행 | 이 호출과 이를 전달하기만 하는 메서드 정리 검토 |

미사용 리소스 확인에 사용한 것은 기존 lint 결과이며, 이번 검토에서 lint를 새로 실행하지 않았다. 삭제 전 최신 lint와 코드 참조를 확인해야 한다. 이 정리의 APK 크기 감소량은 측정하지 않았다.

MapView는 기본 설정에서 포함된 Activity가 종료될 때 자동 종료된다고 SDK 문서에 명시되어 있다. destroyDrawingCache()가 없다는 이유로 지도 누수를 단정하거나 finish()를 무조건 추가하지 않는다.

참고: [Kakao MapView 수명주기 API](https://apis.map.kakao.com/android_v2/reference/com/kakao/vectormap/MapView.html)

## 유지할 부분

[DriveGuardApplication.java](app/src/main/java/com/example/drowsydrive/DriveGuardApplication.java)의 Application 초기화 위치는 카카오 권장 방식이다. 시작 시간 측정 없이 지도 초기화를 다른 스레드로 옮길 근거는 없다. 네이티브 라이브러리 로드 실패 시 메인 화면을 사용할 수 있게 하는 예외 처리도 유지한다.

참고: [KakaoMaps 지도 시작하기](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/)

## 권장 실행 순서

1. 알람 준비 전 요청 보관과 오래된 재생 요청 무효화.
2. 위치 요청 실패·중단·재개 상태와 오래된 콜백 처리.
3. 지도 준비 완료 시 최신 위치 적용.
4. 위치 최신값 선택·유효시간·배치 정책 정리.
5. 메인 화면의 저전력 위치 정책 비교 측정.
6. 미사용 리소스와 BuildConfig 필드 정리.

각 항목은 관련 회귀 테스트와 빌드로 검증한다. 문서상의 최적화 후보를 구현 완료나 측정된 성능 개선으로 해석하지 않는다.

---

## 반영 코드 후속 리뷰 및 추가 수정 요청 (2026-11-03)

이 절은 위 반영 결과에 대한 후속 코드 리뷰다. 앞선 1~6번은 최초 검토 당시의 기록이며, 추가 수정 시에는 이 절의 남은 작업과 최신 코드를 기준으로 진행한다.

### 결론

알람 준비 요청 보관, 지도 준비 전 최신 위치 보관, 미사용 리소스 삭제는 반영되어 있다. 다만 위치 결과 콜백의 세대 구분과 TTS 종료 후 초기화 콜백 처리가 불완전하다. 검색 직전 위치 유효성 확인·필요 시 정밀 갱신은 여전히 미구현이다.

아래 내용은 코드에서 확인한 처리 누락과 그로 인해 가능한 실행 경로를 구분해 설명한다. 위치 콜백 경합과 TTS 종료 경합을 실기기에서 재현하거나 크래시를 확인한 것은 아니다.

### A. 위치 결과 콜백도 요청 세대로 구분

우선순위: 중간

관련 파일: [LocationProvider.java](app/src/main/java/com/example/drowsydrive/location/LocationProvider.java)

확인한 내용:

- 위치 요청 성공·실패 콜백에는 requestGeneration 검사가 있다.
- 실제 위치를 전달하는 onLocationResult()는 공유 locationCallback에서 requestingLocationUpdates만 확인한다.
- stop() 직후 start()하면 플래그가 다시 true가 되므로, 이전 요청의 결과가 그 이후 전달되는 경우 코드 자체로 차단하지 못한다.
- 따라서 앞선 반영 표의 '중단 이후 위치 콜백 차단'은 재개 전에는 성립하지만, 이전 콜백과 새 세션을 구분하는 보장은 부족하다.

추가 수정:

1. start()마다 해당 세대를 캡처한 LocationCallback을 만들거나, 동등하게 이전 요청과 새 요청의 결과를 구분한다.
2. 위치 전달 전에 현재 세대와 활성 상태를 함께 확인한다.
3. stop()에서는 해당 요청에 등록했던 콜백을 해제한다. 이전 요청의 해제가 새 요청의 콜백에 영향을 주지 않도록 한다.
4. 요청 실패·등록 해제 실패·빠른 재시작에서 상태와 콜백 참조가 일관되게 정리되도록 한다. 무제한 재시도는 추가하지 않는다.

회귀 검증:

- 요청 A 시작 → 중단 → 요청 B 시작 → A의 위치 결과 전달: 화면 콜백 0회.
- 같은 상태에서 B의 유효한 위치 결과 전달: 화면 콜백 1회.
- A의 늦은 성공·실패가 B의 요청 상태를 바꾸지 않음.
- A 해제 경로가 B의 등록을 해제하지 않음.

### B. TTS 종료 후 초기화 콜백 진입 차단

우선순위: 낮음 — 종료 수명주기 보완

관련 파일:

- [TTSManager.java](app/src/main/java/com/example/drowsydrive/alert/TTSManager.java)
- [ReadyGate.java](app/src/main/java/com/example/drowsydrive/alert/ReadyGate.java)

확인한 내용:

- onInit()은 ReadyGate의 해제 상태가 반영되기 전에 tts.setLanguage()를 호출한다.
- shutdown()의 removeCallbacksAndMessages(null)은 이미 큐에 있는 작업을 제거한다. 외부 초기화 콜백이 그 이후 새 작업을 post하는 경로까지 막지는 않는다.
- ReadyGate는 해제 후 보관 요청의 음성 재생을 막지만, 그 전에 수행되는 해제된 TTS 엔진에 대한 API 호출은 차단하지 않는다.
- 이로 인한 크래시 발생 여부는 확인하지 않았다.

추가 수정:

1. onInit() 진입 시 종료 여부를 먼저 검사한다.
2. 종료 상태이면 setLanguage(), speak() 등 엔진 API를 호출하지 않는다.
3. 종료 표시를 먼저 확정한 뒤 기존 자원을 해제한다. 현재의 메인 스레드 상태 관리 방식은 유지한다.
4. 종료 가드를 위해 불필요한 범용 프레임워크나 새 의존성을 추가하지 않는다.

회귀 검증:

- shutdown() 이후 외부 초기화 성공 콜백이 새로 전달돼도 엔진 설정·재생 호출이 0회임을 확인한다.
- 초기화 실패 콜백과 중복 콜백도 종료 상태를 되돌리지 않음을 확인한다.
- 기존 준비 전 요청 보관·교체·취소 동작을 유지한다.
- ReadyGate 단독 테스트만으로 TTSManager 전체 연결 경로가 검증됐다고 보고하지 않는다.

### C. 검색·지도 진입 직전 위치 유효성 확인과 정밀 갱신 완성

우선순위: 중간 — 기존 최적화 항목의 미완료 부분

관련 파일:

- [MainActivity.kt](app/src/main/java/com/example/drowsydrive/ui/main/MainActivity.kt)
- [LocationProvider.java](app/src/main/java/com/example/drowsydrive/location/LocationProvider.java)
- [LocationUpdatePolicy.java](app/src/main/java/com/example/drowsydrive/location/LocationUpdatePolicy.java)
- [KakaoMapSearchLauncher.kt](app/src/main/java/com/example/drowsydrive/ui/map/KakaoMapSearchLauncher.kt)

확인한 내용:

- 메인 화면은 저전력 정확도·10초 주기로 변경됐다.
- MainActivity는 LatLng만 보관하며 위치 시각·정확도 정보를 보관하지 않는다.
- 외부 검색과 지도 진입 시 마지막 좌표를 바로 사용한다.
- LocationProvider의 유효시간 검사는 수신 시점에만 수행된다. 수신 후 오래 보관된 좌표가 검색 시점에도 유효하다는 뜻은 아니다.
- 수신이 중단되거나 화면을 오래 떠났다가 복귀하면 마지막 좌표는 더 오래 남을 수 있다.
- 예시: 90km/h = 25m/s이므로 20초 이동 거리는 500m다. 이는 실제 위치 오차 측정값이 아니라 이동 거리를 설명하는 계산이다.
- 문서의 '부분 반영' 표시는 정확하다. 저전력 위치의 정확도는 기기·환경에 따라 달라지며 약 100m로 보장되는 값은 아니다.

추가 수정:

1. 위치 좌표와 함께 관측 시각·정확도를 보관해 사용 시점에 검증할 수 있게 한다.
2. 검색·지도 진입 시 위치 나이와 정확도를 확인한다. 허용 기준은 상수로 명시하고, 실기기 검증 전에는 실험값임을 표시한다.
3. 오래되거나 부정확하거나 없는 위치는 고정밀 현재 위치를 한 번 요청한 뒤 해당 동작을 이어간다.
4. 최신의 충분히 정확한 위치가 있으면 불필요한 정밀 요청 없이 기존 동작을 수행한다.
5. 연속 탭으로 요청이나 화면 실행이 중복되지 않도록 처리한다. 대기 상태와 실패 시 재시도 가능한 UI를 제공하고 메인 스레드를 블로킹하지 않는다.
6. 화면 중단·종료, 권한 상실, 요청 교체 후 늦은 결과가 검색 앱이나 지도 화면을 열지 않도록 한다.
7. 일회성 정밀 요청의 결과에도 필요한 유효성 검사를 적용한다. 저전력 연속 업데이트와 정밀 요청의 결과가 서로 상태를 잘못 덮어쓰지 않도록 한다.

회귀 검증:

- 최신·정확한 캐시: 정밀 요청 없이 검색/지도 실행 1회.
- 오래된 캐시·정확도 부족·캐시 없음: 정밀 요청 후 유효 결과로 실행 1회.
- 실패·취소·권한 없음: 외부 앱/지도 실행 0회, 사용자에게 상태 전달.
- 중복 탭 및 중단 후 늦은 결과: 요청·실행 중복 없음.
- 일회성 정밀 요청 중 저전력 위치가 들어와도 요청한 검색의 실행 조건이 깨지지 않음.

### 후속 리뷰에서 직접 실행한 검증

Android Studio Gradle 도구로 다음 명령을 실행했다.

| 명령 | 결과 |
|---|---|
| :app:testDebugUnitTest | 54개 통과, 실패 0 |
| :app:assembleDebug | 성공 |
| :app:lintDebug | 성공, 오류 0건·경고 17건 |
| :app:assembleDebugAndroidTest | 성공 |

계측 테스트 APK 빌드 성공은 기기에서 테스트를 실행했다는 뜻이 아니다. 이번 후속 리뷰에서는 connectedDebugAndroidTest, 실기기 재현, 전력 측정을 수행하지 않았다. 새로 추가된 테스트 9개는 순수 보조 로직을 검증하며 위 비동기 연결 경로까지 보장하지 않는다. 후속 리뷰에서는 코드나 기존 반영 결과를 수정하지 않았다.

### Claude 후속 작업 완료 조건

- 먼저 최신 파일과 저장소 지침을 확인하고 사용자 변경을 보존한다.
- A → B → C 순서로 수정하되 관련 없는 리팩터링·라이브러리 업그레이드는 포함하지 않는다.
- 해당 경합과 검색 흐름을 검증하는 회귀 테스트를 추가한다. 외부 콜백 순서를 제어할 수 있는 작은 테스트 대역을 우선하고, 테스트 편의를 위한 과도한 추상화는 피한다.
- 단위 테스트, 디버그 빌드, lint, 계측 테스트 APK 빌드를 실행한다. 기기에서 실행한 테스트와 빌드만 한 테스트를 구분해 보고한다.
- 위 A/B/C별 해결 내용, 실제 검증 명령·결과, 남은 한계를 이 문서의 새 완료 기록으로 추가한다. 최초 검토·이전 반영 결과·후속 리뷰 기록은 구분해서 보존한다.
- 측정하지 않은 성능·정확도·배터리 개선을 단정하지 않는다.
- 커밋·푸시는 별도 사용자 요청 없이 수행하지 않는다.

---

## 후속 수정 완료 기록 (2026-11-03)

위 '반영 코드 후속 리뷰 및 추가 수정 요청'의 A/B/C를 순서대로 반영한 기록이다. 브랜치 `refactor/20261102`의 작업 트리에 반영했으며 커밋·푸시는 하지 않았다. 최초 검토(1~6번), 이전 반영 결과, 후속 리뷰 절은 수정하지 않았다.

### A. 위치 결과 콜백 세대 구분

변경 내용:

- 새 `LocationUpdateSessions`(순수 Java)가 연속 위치 업데이트 세션 번호와 활성 상태를 관리한다.
- `LocationProvider.start()`는 세션마다 그 번호를 캡처한 새 `LocationCallback`을 만들어 등록한다. 위치 결과, 요청 성공·실패 콜백은 모두 현재 활성 세션인지 확인한 뒤 반영한다.
- `stop()`은 현재 세션에 등록한 콜백(`activeCallback`)만 해제한다. 세션마다 콜백 객체가 다르므로 이전 세션의 해제가 새 세션 등록을 해제하지 않는다.
- 현재 세션의 요청이 실패하면 세션과 콜백 참조를 정리해 다음 `start()`에서 다시 요청할 수 있다. 등록 해제 실패는 로그만 남긴다. 무제한 재시도는 추가하지 않았다.

회귀 테스트: `LocationUpdateSessionsTest` 5개

- A 시작 → 중단 → B 시작 후 A 결과 차단, B 결과 허용
- A의 늦은 실패가 B 상태를 바꾸지 않음
- 현재 세션 실패 후 재시작 가능
- 중단 후 재개 전 결과 차단
- 중복 `stop()` 무시

한계: 세션 판정 로직만 단위 테스트로 검증했다. 'A 해제가 B 등록을 해제하지 않음'은 세션별 콜백 객체를 분리한 구조로 보장하며, FusedLocationProviderClient와 연결된 실제 경로는 테스트하지 않았다.

### B. TTS 종료 후 초기화 콜백 차단

변경 내용:

- `TTSManager`에 `released` 플래그를 두었다. `shutdown()`은 이 플래그를 먼저 확정한 뒤 엔진을 정지·해제한다.
- `onInit()`은 진입 즉시 종료 여부와 `ReadyGate.isPending()`을 확인한다. 종료 후 콜백과 결과가 이미 확정된 뒤의 중복 콜백에서는 `setLanguage()`·`speak()`를 호출하지 않는다. `ReadyGate`에는 `isPending()`만 추가했다.
- 메인 스레드 전달은 `Handler` 대신 `ContextCompat.getMainExecutor()`를 쓴다. 종료 플래그가 늦게 post된 작업도 차단하므로 `removeCallbacksAndMessages()`는 제거했다.
- 테스트에서 외부 초기화 콜백 순서를 제어하기 위해 package-private `Engine`/`EngineFactory` 경계를 두었다. 운영 코드는 `TextToSpeech`를 감싼 내부 구현만 사용한다. 새 의존성은 추가하지 않았다.

회귀 테스트: `TTSManagerTest` 9개(가짜 엔진과 즉시 실행 Executor 사용)

- `shutdown()` 이후 성공 콜백: 엔진 설정·재생 0회
- `shutdown()` 이후 실패·성공 중복 콜백: 엔진 설정·재생 0회
- 실패 후 성공 콜백이 실패 상태를 되돌리지 않음
- 중복 성공 콜백에서 언어 설정 1회·재생 1회
- 언어 미지원 처리
- 준비 전 요청의 보관·교체·취소 동작 유지

한계: 실제 `TextToSpeech` 엔진과 연결되는 부분(`TextToSpeechEngine`)은 단위 테스트 대상이 아니며 기기에서도 실행하지 않았다.

### C. 검색·지도 진입 직전 위치 유효성 확인과 정밀 갱신

변경 내용:

- 새 `LocationFix`가 좌표와 함께 관측 시각(`elapsedRealtimeNanos` 기준)과 정확도(없으면 NaN)를 보관한다. `LocationProvider.Listener.onLocationChanged()`는 `LatLng` 대신 `LocationFix`를 전달한다. 단조 시각이 없는 위치는 수신 시점에 벽시계 나이로 환산한다.
- 새 `PreciseLocationGate`(Android 의존성 없는 Kotlin 클래스)가 검색·지도 진입 전에 캐시 위치를 확인한다.
  - 나이 30초 이하이고 정확도 100m 이하이면 정밀 요청 없이 바로 실행한다. 두 기준은 상수로 두었으며, 코드 주석에 실기기 검증 전 실험값임을 표시했다.
  - 캐시가 없거나 오래됐거나 정확도가 부족하거나 정확도 정보가 없으면 고정밀 현재 위치를 한 번 요청하고, 그 결과로 실행한다.
  - 정밀 결과도 나이 30초 이하인지 검사한다. 정확도는 현재 측위의 최선값으로 보고 거부하지 않는다.
- 일회성 정밀 요청은 `CurrentLocationRequest`로 보낸다. 고정밀, 캐시 허용 나이 10초, 최대 대기 15초이며, 시간이 초과되면 실패로 처리한다. 요청할 때 결과를 받을 Listener를 지정하므로 연속 업데이트 결과와 섞이지 않는다.
- 요청 번호를 붙여 취소·교체된 요청의 늦은 결과는 무시한다. 저전력 연속 업데이트는 캐시만 갱신하고, 대기 중인 동작을 실행하거나 취소하지 않는다. 캐시는 더 새로운 위치로만 갱신한다.
- 중복 방지:
  - 대기 중 추가 탭은 무시하고 '정확한 위치를 확인하고 있어요' 토스트를 표시한다.
  - 실행 직후 1초 안의 추가 탭은 무시한다.
- 대기·실패 UI:
  - 대기 중에는 위치 상태 표시가 '정확한 위치 확인 중'으로 바뀐다.
  - 실패하면 '위치를 확인하지 못했어요' 다이얼로그를 띄우고, '다시 시도'로 같은 동작을 재요청할 수 있다(`MainDialog.LocationFailed`, `MainDialogs.Listener.onLocationRetry`).
- 수명주기·권한:
  - `onPause()`, 권한 거부 결과, `onResume()` 시 권한이 없으면 대기 동작과 정밀 요청을 취소한다.
  - 실행 직전에도 화면이 RESUMED 상태이고 위치 권한이 있는지 다시 확인한다.
  - 권한이 없는 상태에서 요청하면 권한 요청과 안내 토스트만 표시한다.
- `MapActivity`는 바뀐 Listener에 맞췄고, 일회성 요청 결과를 자신에게 전달받는다.

회귀 테스트: `PreciseLocationGateTest` 15개

- 최신·정확한 캐시: 정밀 요청 없이 실행 1회
- 오래된 캐시·정확도 부족·정확도 없음·캐시 없음: 정밀 요청 후 실행 1회
- 정밀 실패·오래된 정밀 결과: 실행 0회, 실패 통지
- 실패 후 재시도
- 대기 중 중복 탭·실행 직후 중복 탭·같은 결과 중복 전달: 요청·실행 중복 없음
- 취소 후 늦은 결과·교체된 요청의 결과: 실행 0회
- 대기 중 저전력 위치 수신: 실행 조건 유지
- 과거 위치가 캐시를 덮어쓰지 않음

계측 테스트: `MainDialogsTest`에 실패 다이얼로그의 '다시 시도' 전달 테스트 1개를 추가했다. 아래와 같이 빌드만 확인했다.

한계:

- 캐시 기준(30초·100m), 정밀 요청 대기 15초, 중복 무시 1초는 실험값이다. 실기기에서 검색 대기 시간과 위치 정확도를 측정하지 않았다.
- 저전력 정책은 정확도가 기기·환경에 따라 달라서 캐시 기준을 자주 넘지 못할 수 있다. 그러면 검색·지도 진입 때 정밀 요청이 자주 발생한다. 실제 빈도와 전력 영향은 측정하지 않았다.
- MainActivity와 FusedLocationProvider 사이의 연결(Listener 생성, 취소 호출 순서)은 단위 테스트 대상이 아니며 기기에서 실행하지 않았다.

### 빌드 설정 변경

- `app/build.gradle`에 `testOptions.unitTests.returnDefaultValues = true`를 추가했다. TTSManager 실패 경로의 `android.util.Log` 호출이 로컬 단위 테스트에서 예외를 내지 않게 하기 위해서다. 다른 Android 스텁 호출도 예외 대신 기본값을 반환하게 되므로, 단위 테스트가 Android API 호출 결과를 검증하지는 않는다.

### 실제 검증 명령과 결과

`gradle-wrapper.jar`가 없어 `~/.gradle`에 캐시된 Gradle 9.6.0과 Android Studio JBR로 실행했다.

| 명령 | 구분 | 결과 |
|---|---|---|
| :app:testDebugUnitTest | 로컬 JVM 실행 | 83개 통과, 실패 0(기존 54 + 신규 29) |
| :app:assembleDebug | 빌드 | 성공 |
| :app:lintDebug | 정적 분석 | 성공, 오류 0건·경고 17건(후속 리뷰 시점과 같은 수. 신규·변경 코드에서 새 경고 없음) |
| :app:assembleDebugAndroidTest | 빌드만 | 성공 |
| :app:connectedDebugAndroidTest | 기기 실행 | 미완료. 에뮬레이터(Medium_Phone_API_37.0)가 adb 명령에 응답하지 않아 10분 넘게 멈춰 중단했다. 결과 파일이 생성되지 않아 계측 테스트는 실행되지 않은 것으로 본다. |

계측 테스트, 실기기 시나리오 재현, 전력·정확도 측정은 수행하지 않았다. 위 성능·전력 관련 항목은 개선을 단정하지 않는다.

### 변경 파일

- 추가:
  - 운영 코드: `location/LocationUpdateSessions.java`, `location/LocationFix.java`, `ui/main/PreciseLocationGate.kt`
  - 테스트: `test/.../location/LocationUpdateSessionsTest.java`, `test/.../alert/TTSManagerTest.java`, `test/.../ui/main/PreciseLocationGateTest.kt`
- 수정:
  - `location/LocationProvider.java`, `alert/TTSManager.java`, `alert/ReadyGate.java`
  - `ui/main/MainActivity.kt`, `ui/main/MainDialogs.kt`, `ui/main/MainScreen.kt`, `ui/main/MainScreenState.kt`, `ui/map/MapActivity.kt`
  - `res/values/strings.xml`, `app/build.gradle`, `androidTest/.../ui/main/MainDialogsTest.kt`

### 남은 작업

1. 에뮬레이터를 Cold Boot로 재시작하거나 실기기를 연결한 뒤 `connectedDebugAndroidTest`를 실행한다.
2. 실기기에서 아래 순서를 재현한다.
   - 위치 요청 중단 → 재개 → 이전 결과 도착
   - TTS 초기화 전 종료
   - 검색·지도 진입 시 정밀 요청과 실패 다이얼로그
3. 같은 조건에서 검색 대기 시간, 검색 좌표의 나이·정확도, 정밀 요청 빈도, 전력 사용을 측정하고 실험값(30초·100m)을 조정한다.

---

## 후속 수정 재검토: 추가 보완점 (2026-11-03)

상태: 아래 2건은 추가 수정 요청이며, 구현 완료 기록이 아니다. 이전 A(위치 결과 콜백의 세대 구분)와 B(TTS 종료 후 초기화 콜백 차단)는 코드에 반영되었다. C(검색 전 위치 갱신)에는 다음 보완점이 남아 있다.

### D. 정밀 요청 결과에도 정확도 기준 적용

우선순위: 중간

관련 파일:

- [PreciseLocationGate.kt](app/src/main/java/com/example/drowsydrive/ui/main/PreciseLocationGate.kt)
- [LocationProvider.java](app/src/main/java/com/example/drowsydrive/location/LocationProvider.java)
- [PreciseLocationGateTest.kt](app/src/test/java/com/example/drowsydrive/ui/main/PreciseLocationGateTest.kt)

확인한 내용:

- isUsableCache()는 위치 나이와 정확도 100m 기준을 확인한다.
- onPreciseLocation()은 위치 나이만 검사하고 정확도는 확인하지 않는다.
- 따라서 정밀 결과의 정확도가 500m·1km이거나 정보가 없어도 검색·지도 진입을 실행한다.
- CurrentLocationRequest는 최대 10초 된 캐시를 허용한다. 정확도가 부족해서 거절했던 최근 위치를 다시 받아, 정밀 요청의 결과라는 이유만으로 사용할 수 있다.
- 고정밀 우선순위는 정확한 새 위치의 반환을 보장하지 않는다. 공식 API는 허용되는 과거 위치가 없을 때 위치를 구하는 우선순위라고 설명한다.
- 이전 완료 기록의 '현재 측위의 최선값으로 보고 거부하지 않는다'는 정책은 원래의 사용 시점 정확도 확인 요구를 충분히 충족하지 못한다.

추가 수정 방향:

1. 정밀 결과도 사용 가능한 나이와 정확도를 함께 검증한다.
2. 우선 기존 100m 기준을 일관되게 적용하는 방식을 권장한다. 정밀 결과에 다른 기준이 필요하면 별도 상수와 근거를 명시하고, 실기기 검증 전 실험값임을 표시한다.
3. 정확도 정보가 없거나 허용 기준을 벗어나면 성공으로 처리하지 말고 실패·재시도 경로로 전달한다. 부정확한 위치를 그대로 사용하는 별도 UX 정책은 임의로 추가하지 않는다.
4. 고정밀 요청이 거절했던 최근 저정밀 캐시를 그대로 반환할 수 있다는 점을 고려해 캐시 허용 정책을 검토한다. 캐시 허용 나이를 0으로 바꾸더라도 정확도 검사를 생략해서는 안 된다.
5. 무제한 재요청이나 메인 스레드 대기는 추가하지 않는다.

회귀 검증:

- 나이는 유효하지만 정확도가 500m인 정밀 결과: 검색·지도 실행 0회, 실패 통지.
- 정확도 정보가 없는 정밀 결과: 실행 0회, 실패 통지.
- 정확도 부족으로 거절한 캐시와 같은 위치가 정밀 결과로 돌아옴: 실행 0회.
- 나이·정확도가 모두 기준을 충족하는 정밀 결과: 실행 1회.
- 허용 정확도의 경계와 기준 초과 값을 검증한다.
- 실패 후 다시 시도해 유효한 결과를 받으면 정상 실행한다.

참고: [CurrentLocationRequest 공식 API](https://developers.google.com/android/reference/com/google/android/gms/location/CurrentLocationRequest)

### E. 취소·교체된 정밀 결과가 캐시를 바꾸지 않도록 수정

우선순위: 낮음

관련 파일:

- [PreciseLocationGate.kt](app/src/main/java/com/example/drowsydrive/ui/main/PreciseLocationGate.kt)
- [PreciseLocationGateTest.kt](app/src/test/java/com/example/drowsydrive/ui/main/PreciseLocationGateTest.kt)

확인한 내용:

- onPreciseLocation()은 takePending(requestId)로 현재 요청인지 확인하기 전에 remember(fix)를 호출한다.
- 취소되거나 교체된 요청은 화면 실행을 막더라도 latest 캐시를 변경할 수 있다.
- 현재 LocationProvider의 취소 토큰 검사도 보호하지만, Gate 자체의 '이전 요청 결과 무시' 계약은 불완전하다.
- 기존 취소·교체 테스트는 실행·실패 통지 여부를 확인하지만 캐시 불변 여부는 확인하지 않는다.

추가 수정 방향:

1. 현재 활성 요청 번호인지 먼저 확인한다.
2. D의 유효성 검사까지 통과한 현재 요청의 결과만 캐시에 반영한다.
3. 취소·교체·중복 결과가 캐시나 다른 요청의 대기 상태에 영향을 주지 않게 한다.
4. 저전력 연속 업데이트가 캐시만 갱신하는 기존 동작은 유지한다.

회귀 검증:

- 유효 캐시 보관 → 요청 A 시작 → 취소 → A의 늦은 결과: 실행·실패 통지 0회, 캐시 유지.
- A 취소 → B 시작 → A 결과 도착: B의 대기 상태와 캐시 유지.
- 정상 처리한 요청의 중복 결과가 다른 좌표를 담아도 캐시 변경 없음.
- 현재 요청의 유효 결과만 한 번 실행하고 캐시에 반영됨.
- 현재 요청의 부적합 결과는 실패로 처리하고 기존 캐시를 오염시키지 않음.
- 기존의 '과거 위치가 더 새로운 캐시를 덮어쓰지 않음' 동작 유지.

### 재검토에서 직접 실행한 검증

Android Studio Gradle 도구로 검증했다.

| 명령 | 이번 결과 |
|---|---|
| :app:testDebugUnitTest | 83개 통과, 실패 0 |
| :app:assembleDebug | 성공. 계측 APK 빌드 실패 후 별도로 재확인 |
| :app:lintDebug | 성공. 계측 APK 빌드 실패 후 별도로 재확인 |
| :app:assembleDebugAndroidTest | 실패. 기존 app-debug-androidTest.apk를 삭제할 수 없어 packageDebugAndroidTest가 종료됨 |
| :app:connectedDebugAndroidTest | 이번 재검토에서는 미실행 |

계측 APK 오류는 코드 컴파일 오류가 아니라 빌드 출력 파일 삭제 실패다. 오류 메시지는 다른 프로세스의 파일 점유 가능성을 제시했지만, 점유 프로세스는 확인하지 않았다. 이전 완료 기록의 APK 빌드 성공과 이번 실패는 각각 다른 실행 시점의 결과로 구분한다. APK나 파일을 점유한 것으로 의심되는 프로세스를 임의로 강제 종료하지 않았다.

단위 테스트 83개 통과만으로 D/E가 해결되었다고 판단하지 않는다. 현재 테스트에는 위 정확도 우회와 캐시 불변 조건에 대한 검증이 빠져 있다. 기기 시나리오 재현, 실제 위치 정확도·전력 측정은 수행하지 않았다.

### Claude 추가 작업 완료 조건

- 최신 코드와 기존 사용자 변경을 확인한 뒤 D/E 범위만 수정한다.
- D의 정확도 검증과 E의 처리 순서 수정에 대한 회귀 테스트를 추가한다.
- 단위 테스트·디버그 빌드·lint를 실행한다. 계측 APK 빌드는 파일 점유 문제를 안전하게 해결한 뒤 재시도하고, 해결하지 못하면 환경 제약을 명시한다.
- 기기에서 실행하지 않은 테스트를 통과했다고 보고하지 않는다.
- 이 절을 덮어쓰지 말고, D/E별 수정 내용과 실제 검증 결과를 새로운 완료 기록으로 추가한다.
- 별도 요청 없이 커밋·푸시는 수행하지 않는다.


## 추가 보완점 D/E 수정 완료 기록 (2026-11-03)

위 '후속 수정 재검토: 추가 보완점' 절은 그대로 두고, D/E 범위의 수정 내용과 실제 검증 결과만 기록한다.

### D. 정밀 요청 결과에도 정확도 기준 적용

변경 내용:

- `PreciseLocationGate.isUsableCache()`를 `isUsable()`로 이름을 바꿨다. 캐시와 정밀 결과에 같은 기준(나이 30초 이하, 정확도 정보가 있고 100m 이하)을 적용한다. 정밀 결과용 별도 상수는 두지 않았다.
- `onPreciseLocation()`은 결과가 `isUsable()`을 통과할 때만 실행한다. 정확도 정보가 없거나 기준을 넘으면 `onFailed`로 전달해 기존의 실패·재시도 대화상자 경로를 탄다. 부정확한 위치를 쓰는 별도 UX는 추가하지 않았다.
- 이전 기록의 '정밀 결과는 정확도와 관계없이 최선값으로 사용' 정책은 폐기했다.
- 캐시 허용 정책: `LocationProvider.CURRENT_LOCATION_MAX_AGE_MS`를 10초에서 0으로 바꿨다.
  - 이유: 정밀 요청은 보관 위치가 오래됐거나 부정확할 때만 보낸다. 캐시를 허용하면 방금 거절한 저정밀 위치가 그대로 돌아와 항상 실패할 수 있다.
  - 정확도 검사는 0으로 바꾼 뒤에도 Gate에서 그대로 수행한다.
  - 이 변경은 지도 화면(`MapActivity`)의 일회성 위치 요청에도 적용된다. 요청마다 새로 측위하므로 응답 시간과 전력이 늘 수 있으며, 실기기에서 측정하지 않았다.
- 무제한 재요청이나 메인 스레드 대기는 추가하지 않았다. 재요청은 사용자가 실패 대화상자에서 다시 시도할 때만 한 번 일어난다.

회귀 테스트(`PreciseLocationGateTest`):

| 테스트 | 검증 내용 |
|---|---|
| inaccuratePreciseResult_failsWithoutPerforming | 나이는 유효하지만 500m인 결과: 실행 0회, 실패 1회, 대기 해제 |
| preciseResultWithoutAccuracy_failsWithoutPerforming | 정확도 정보가 없는 결과: 실행 0회, 실패 1회 |
| rejectedCacheReturnedAsPreciseResult_isNotPerformed | 정확도 부족으로 거절한 캐시와 같은 위치가 정밀 결과로 돌아옴: 실행 0회 |
| preciseResultAtAccuracyBoundary_performs | 정확도가 정확히 100m: 실행 1회, 캐시 반영 |
| preciseResultJustOverAccuracyBoundary_fails | 100m를 아주 조금 넘는 값(`Math.nextUp`): 실패 |
| preciseResultAtAgeBoundary_performs | 나이가 정확히 30초: 실행 1회 |
| retryAfterInaccurateResult_performsWithValidResult | 부정확한 결과로 실패한 뒤 재시도해 유효한 결과를 받으면 실행 1회 |

### E. 취소·교체된 정밀 결과가 캐시를 바꾸지 않도록 수정

변경 내용:

- `onPreciseLocation()`의 처리 순서를 바꿨다. 먼저 `takePending(requestId)`로 현재 대기 중인 요청인지 확인하고, 그다음 D의 유효성 검사를 한다. 둘 다 통과한 결과만 `remember()`로 캐시에 반영한다.
- 취소·교체·중복 결과는 실행, 실패 통지, 캐시, 다른 요청의 대기 상태 어디에도 영향을 주지 않는다.
- 저전력 연속 업데이트(`onContinuousLocation`)가 캐시만 갱신하는 동작과, 과거 위치가 더 새로운 캐시를 덮어쓰지 않는 동작은 그대로 유지했다.

회귀 테스트(`PreciseLocationGateTest`):

| 테스트 | 검증 내용 |
|---|---|
| lateResultAfterCancel_keepsCache | 캐시 보관 → A 시작 → 취소 → A의 늦은 결과: 실행·실패 0회, 캐시 유지 |
| replacedRequestResult_keepsPendingAndCache | A 취소 → B 시작 → A 결과 도착: B 대기 유지, 캐시 유지 |
| duplicateResultOfHandledRequest_doesNotChangeCache | 처리 완료한 요청의 중복 결과가 다른 좌표를 담아도 캐시 변경 없음 |
| validCurrentResult_isPerformedOnceAndCached | 현재 요청의 유효한 결과는 한 번 실행되고 캐시에 반영됨 |
| unusableCurrentResult_failsAndKeepsCache | 현재 요청의 부적합 결과는 실패 처리되고 기존 캐시는 바뀌지 않음 |
| olderLocation_doesNotOverwriteNewerCache (기존) | 과거 위치가 더 새로운 캐시를 덮어쓰지 않음 |

한계: Gate 단위의 처리 순서만 검증했다. `MainActivity`와 실제 위치 서비스를 연결한 경로는 단위 테스트로 검증하지 않았다.

### 계측 APK 빌드 실패 원인과 조치

- 원인: 이전 세션에서 시작한 `:app:connectedDebugAndroidTest` 실행의 프로세스가 남아 있었다. 당시 셸만 종료되고 다음 세 프로세스가 계속 실행 중이었다.
  - Gradle 클라이언트(PID 20892)
  - 테스트 워커(PID 9940)
  - 응답 없는 에뮬레이터에 APK를 설치하던 `adb -s emulator-5554 install ... app-debug-androidTest.apk`(PID 6024)
- 프로세스 명령줄로 이 세 개가 모두 해당 테스트 실행에서 나온 것임을 확인한 뒤 이 세 개만 종료했다. Android Studio, 에뮬레이터, adb 서버 프로세스는 종료하지 않았다.
- 종료한 뒤 파일 잠금이 풀린 것을 확인했고, `:app:assembleDebugAndroidTest`가 성공했다.

### 실제 검증 명령과 결과

| 명령 | 결과 |
|---|---|
| :app:testDebugUnitTest | 95개 통과, 실패 0 (기존 83 + D/E 신규 12) |
| :app:assembleDebug | 성공 |
| :app:lintDebug | 성공. 오류 0, 경고 17 (이전과 같음, 신규 코드 경고 없음) |
| :app:assembleDebugAndroidTest | 성공 (위의 남은 프로세스를 정리한 뒤 재시도) |
| :app:connectedDebugAndroidTest | 미실행. emulator-5554가 계속 응답하지 않아(`adb shell dumpsys` 호출이 계속 쌓임) 실행하지 않았다 |

기기에서 실행하지 않은 계측 테스트는 통과로 보고하지 않는다. 실제 위치 정확도, 응답 시간, 전력 측정은 수행하지 않았다.

### 변경 파일

- app/src/main/java/com/example/drowsydrive/ui/main/PreciseLocationGate.kt
- app/src/main/java/com/example/drowsydrive/location/LocationProvider.java (`CURRENT_LOCATION_MAX_AGE_MS` 10초 → 0, 주석)
- app/src/test/java/com/example/drowsydrive/ui/main/PreciseLocationGateTest.kt (테스트 12개 추가)

### 남은 작업

- 에뮬레이터를 Cold Boot로 다시 켜거나 실기기를 연결한 뒤 `:app:connectedDebugAndroidTest`를 실행한다.
- 실기기에서 실내·지하처럼 정확도가 100m를 넘기 쉬운 환경을 재현해, 실패 대화상자가 얼마나 자주 뜨는지 확인한다.
- 캐시 허용 나이 0으로 늘어난 측위 응답 시간과 전력을 측정한다(메인 화면과 지도 화면 모두). 그 결과로 30초·100m·15초 값을 조정한다.
- 커밋·푸시는 수행하지 않았다.
