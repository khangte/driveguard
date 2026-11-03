# 졸음 감지 측정 로그 (개발용)

졸음 판단 기준을 고치기 전에 실제 기기에서 나오는 ML Kit 값과 판정 흐름을 모으는 디버그 전용 기능입니다.
DB·서버 없이 앱 내부에 세션별 JSONL 파일로 저장하고, Android Studio에서 PC로 가져와 분석합니다.

- 측정값은 모델 출력일 뿐 실제 졸음의 정답이 아닙니다.
- 수집 여부와 관계없이 감지 알고리즘·임계값·분석 주기·알람 동작은 같습니다. 수집이 실패해도 감지는 계속됩니다.
- 얼굴 사진·영상·음성, 위치, 블루투스 주소, 기기 고유 ID는 저장하지 않습니다.

## 1. 사용 방법

1. **debug 빌드**를 설치하고 카메라 권한을 허용합니다. release 빌드에는 제어 UI가 없고 기록기·파일·스레드를 만들지 않습니다.
2. 메인 화면 아래쪽 **측정 로그 (개발용)** 카드에서 시나리오 이름을 입력하거나 예시 칩(`eyes_closed_1s` 등)을 고릅니다.
3. 카메라 미리보기가 "카메라 연결됨" 상태일 때 **수집 시작**을 누릅니다. 권한이 없거나 분석이 아직 시작되지 않았으면 이유가 표시되고 시작하지 않습니다.
4. 정차 상태에서 행동을 수행하며, 관찰자가 **행동 이름**을 입력하고 **행동 시작 / 행동 종료** 마커를 누릅니다.
   수집 중에는 졸음 경고창과 휴식 안내창 안에서도 같은 행동 시작·종료 버튼을 사용할 수 있습니다. 마커 조작은 알람을 끄지 않습니다.
   마커는 사람의 반응 지연이 있는 참고 기록입니다. 시나리오 이름만으로 매 순간의 행동이 확정되지 않습니다.
5. **수집 중지**를 누르면 세션 종료 기록을 쓰고 파일을 닫습니다.

상태 표시는 `중지 / 수집 중 / 오류`이며, 현재 파일 이름과 `기록 N줄 · 유실 M건`을 보여 줍니다(약 1초마다 갱신).

### 생명주기

| 상황 | 동작 |
| --- | --- |
| 화면 중단(onPause) | 현재 세션을 `screen_paused`로 닫습니다. |
| 수집 중이던 상태로 화면 복귀 | **새 세션**(새 파일)으로 시작합니다. 이전 파일·판정 타이머에 이어 붙이지 않습니다. |
| 사용자가 중지한 뒤 복귀 | 자동으로 다시 수집하지 않습니다. |
| 앱 프로세스 재시작 | 기본은 중지 상태입니다. |
| 저장 오류 | 수집만 오류 상태로 멈추고 감지·알람은 계속합니다. 복귀해도 자동 재시작하지 않습니다. |
| 화면 회전 등 Activity 재생성 | ViewModel에 수집 의도·시나리오와 기록 작업자를 유지합니다. 기존 세션 닫기 뒤 새 세션을 같은 작업자에서 열며 이전 Activity의 상태 콜백은 해제합니다. |
| 실제 화면 종료 | ViewModel 해제 시 받아 둔 기록을 처리하고 기록 스레드를 끝냅니다(메인 스레드는 기다리지 않음). |

이전 분석 세션의 늦은 ML Kit 결과는 판정에 반영되지 않으며, 닫힌 수집 세션의 늦은 기록은 어느 파일에도 쓰지 않습니다.

## 2. 저장 위치와 PC로 가져오기

```text
<context.noBackupFilesDir>/detection-logs/
  session_<yyyyMMdd-HHmmss>_<임의6자리>_part001.jsonl
  session_<yyyyMMdd-HHmmss>_<임의6자리>_part002.jsonl
```

일반적인 기기에서는 `/data/data/com.example.drowsydrive/no_backup/detection-logs/`입니다(코드는 경로를 하드코딩하지 않고 Android API로 구합니다).

- **Device Explorer:** Android Studio `View > Tool Windows > Device Explorer` → `data/data/com.example.drowsydrive/no_backup/detection-logs` → 파일 또는 폴더 우클릭 → **Save As...**. debug(디버깅 가능) 빌드여야 열 수 있습니다.
- **명령줄(대안):**
  ```bash
  adb exec-out run-as com.example.drowsydrive tar c no_backup/detection-logs > detection-logs.tar
  ```

주의:

- 이 위치는 Android 자동 백업에서 제외됩니다. **앱 삭제나 앱 데이터 초기화 시 로그가 사라집니다.** 필요한 파일은 먼저 PC로 옮기세요.
- 캐시·공용 Downloads에는 저장하지 않으며 저장소·네트워크 권한을 추가하지 않았습니다.

## 3. 보관 상한과 유실

| 항목 | 값 (`DetectionLogRecorder` 상수) |
| --- | --- |
| 파일당 상한 | 10 MiB (`MAX_PART_BYTES`) — 넘기 전에 다음 `partNNN` 파일로 분할 |
| 전체 상한 | 100 MiB (`MAX_TOTAL_BYTES`) |
| 관측 대기열 | 256건 (`QUEUE_CAPACITY`) |
| flush 간격 | 약 1초 (`FLUSH_INTERVAL_MS`) |

- 새 분할 파일을 열기 전에 닫힌 파일 합계가 `전체 상한 - 파일 상한` 이하가 되도록 **가장 오래된 닫힌 파일부터** 지웁니다. 쓰는 중인 파일은 지우지 않으며, 같은 세션의 닫힌 이전 분할도 지워질 수 있습니다. 지운 파일은 새 파일의 `retention_cleanup` 기록과 종료 기록의 `deleted_own_files`에 남습니다.
- 정리에 실패해 상한을 지킬 수 없으면 수집을 오류 상태로 바꿉니다(감지는 계속).
- 파일 분할 전 버퍼 쓰기·닫기에 실패하면 다음 파일을 열지 않고 오류 상태로 멈춥니다. 불완전한 세션에 정상 종료 기록을 추가하지 않습니다.
- 기록 스레드가 밀려 대기열이 가득 차면 분석을 기다리게 하지 않고 **관측 기록을 버린 뒤 유실 수를 셉니다**(`frame_records_dropped_queue_full`, 화면의 "유실"). 세션 열기·닫기, 알람·마커 이벤트는 버리지 않습니다.
- flush는 버퍼를 OS로 넘길 뿐이며, 전원 차단까지 포함한 영구 저장을 보장하지 않습니다. 매 관측마다 디스크 동기화는 하지 않습니다.

### 강제 종료된 파일

프로세스가 강제 종료되면 `session_end` 기록이 없고 마지막 줄이 잘릴 수 있습니다.

- 줄바꿈(`\n`)으로 끝나지 않는 마지막 줄은 불완전한 줄이므로 버리세요.
- 마지막 분할 파일에 `"type":"session_end"`가 없으면 **완전한 세션으로 간주하지 마세요.** 마지막 약 1초 분량이 없을 수 있습니다.

## 4. JSONL 형식 (schema_version 1)

UTF-8, 한 줄에 JSON 객체 하나. 모든 기록에 공통 필드가 있습니다.

| 필드 | 의미 |
| --- | --- |
| `type` | `header`, `observation`, `analysis_failure`, `alert_dispatched`, `alert_discarded`, `alarm`, `eyes_realert_scheduled`, `marker`, `retention_cleanup`, `session_end` |
| `schema_version` | 로그 형식 버전 (1) |
| `session_id`, `part` | 수집 세션 ID, 분할 번호 |
| `seq` | 세션 전체에서 **파일에 쓴 순서** (분할을 넘어 이어짐) |
| `t_ms` | 이벤트 **발생 시각**, `SystemClock.elapsedRealtime()` 기준 세션 시작 후 경과 ms |

여러 스레드에서 기록하므로 `seq`(쓴 순서)와 `t_ms`(발생 시각)가 다를 수 있습니다. 시간 분석에는 `t_ms`·`analysis_*_ms`를 쓰세요.
벽시계 시각은 헤더(`started_wall_ms`, `started_wall_time`)에만 있으며 지속 시간 계산에 쓰지 않습니다.

### header (모든 분할 파일의 첫 줄)

`scenario`, `started_wall_ms`, `started_wall_time`, `started_elapsed_realtime_ms`, `limits`, `environment`
(`app_version_name/code`, `build_type`, `manufacturer`, `model`, `sdk_int`,
`detection_config` = 실제 코드 상수에서 읽은 분석 간격·눈 감김/하품 임계값과 지속 시간·허용 관측 공백·ML Kit 옵션·`config_version`).

### observation (분석 결과 1회당 1줄)

| 필드 | 의미 |
| --- | --- |
| `observation_id` | 관측 ID. 실패·알람 전달 이벤트와 연결 |
| `analysis_session` | 분석 세션 토큰 (화면 재개마다 바뀜) |
| `analysis_start_ms` | **분석 시작 시각** (촬영 시각 아님. 코드의 `frameTime`) |
| `analysis_end_ms`, `processing_ms` | 결과 처리 시작 시각, 분석 시작부터의 지연 |
| `interval_from_prev_start_ms` | 이전 분석 시작과의 간격 (첫 분석이면 null) |
| `camera_timestamp_ns` | CameraX 이미지 타임스탬프 원본. 시계 기준을 확인하지 않았으므로 다른 시각과 빼지 마세요 |
| `image` | `width`, `height`(버퍼 기준), `rotation_degrees` |
| `face_count`, `faces[]` | 얼굴 수(미감지면 0, 정상 기록), 후보 목록 |
| `faces[].tracking_id`, `bbox` | 추적 ID(없으면 null), 얼굴 영역 `left/top/width/height` (ML Kit 회전 보정 좌표계) |
| `faces[].left_eye_open`, `right_eye_open`, `smiling` | 분류 확률. 누락·NaN은 null |
| `faces[].landmarks` | 현재 하품 식에 쓰는 `mouth_bottom`, `nose_base`, `right_eye`의 `x/y`. 없으면 null |
| `faces[].euler_x/y/z` | 얼굴 자세. **기록만 하고 판정에 쓰지 않음** |
| `faces[].value_issues` | null로 쓴 값의 사유: `missing` 또는 `non_finite` (예: `{"left_eye_open":"missing"}`) |
| `faces[].selected`, `unused_reason` | 판정 대상 여부, 미사용 사유(`not_selected`) |
| `selected_index`, `target_reason` | 선택 대상, 사유: `new_target`, `kept_tracking_id`, `kept_single_untracked_face`, `target_changed` |
| `resets[]` | 이번 관측에서 판정을 초기화한 사유와 직전 진행 여부: `analysis_session_changed`, `no_face`, `target_changed` (+ `eyes_was_active`, `yawn_was_active`) |
| `eyes`, `yawn` | 선택 대상의 판정 상태 (아래) |
| `alert_eyes_closed`, `alert_yawn` | 이번 관측에서 **판정 이벤트**가 발생했는지 |
| `used_for_detection` | 현재 세션 결과로 판정에 반영됐는지 |

`eyes` / `yawn` 객체:

| 필드 | 의미 |
| --- | --- |
| `input` | `present`, `missing`(필요한 값 누락), `no_face` |
| `outcome` | `started`, `continued`, `gap_restarted`(허용 관측 공백 초과로 새로 시작), `condition_not_met`, `condition_cleared`(진행 중이던 조건 해제), `invalidated`(누락으로 초기화), `invalid_input`(NaN 등), `reference_too_small`(하품: 입-눈 거리 부족). 판단하지 않았으면 null |
| `condition_met` | 조건 충족 여부. 값이 없거나 무효면 null |
| `ratio` | (하품) 계산된 입-코/입-눈 비율. 계산하지 못했으면 null |
| `active_before`, `active` | 갱신 직전/직후 에피소드 진행 여부 |
| `elapsed_ms` | 진행 중이면 에피소드 시작부터 경과 시간(시작 직후는 0), 진행 중이 아니면 null |
| `alerted_in_episode`, `alert_fired` | 이 에피소드에서 이미 경고했는지, 이번 관측에서 경고가 발생했는지 |

### 기타 기록

| type | 내용 |
| --- | --- |
| `analysis_failure` | ML Kit 실패·처리 예외. `stage`(`mlkit_failure`, `result_processing_exception`, `request_exception`), `error`, `resets`(`analysis_failure`). 관측 공백과 구분됩니다 |
| `alert_dispatched` | 판정 이벤트를 메인 스레드에서 알람 쪽으로 전달함 (`observation_id`, `eyes_closed`, `yawn`) |
| `alert_discarded` | 전달 직전에 분석 세션이 끝나 버림 |
| `alarm` | `WarningAlarmController` 흐름. `event`: `requested`, `suppressed_duplicate`(같은 경고 표시 중), `queued_pending`(다른 경고 표시 중이라 대기), `preempted_active`(졸음 경고가 표시 중 경고를 대체), `sound_unavailable`, `dialog_posted`(경고창 표시 요청), `stopped_by_user`, `stop_ignored_stale`. `alert`, `active_before` |
| `eyes_realert_scheduled` | 유효한 지속 감김에서 졸음 알람을 끈 뒤 재경고 대기 시작. `analysis_session`, `stopped_at_ms`(세션 경과 시간), `delay_ms`(3,000ms) |
| `marker` | `phase`: `start`/`end`/`abandoned`(마커가 열린 채 세션 종료), `marker_id`, `label` |
| `retention_cleanup` | 상한 때문에 지운 파일 목록 |
| `session_end` | `reason`(`user_stop`, `screen_paused`, `recorder_released`), `observations_started`, `frame_records_offered`, `frame_records_dropped_queue_full`, `records_written`, `files`, `deleted_own_files`, `complete` |

`alarm`의 `requested`·`dialog_posted`는 요청·표시 요청 시점이며 **경고음/TTS 재생 완료를 뜻하지 않습니다.**
분석 흐름은 `observation.alert_*` → `alert_dispatched` → `alarm requested` → `dialog_posted` → `stopped_by_user` 순으로 연결합니다.

알람 기록에는 다음 추적 필드가 추가됩니다(기존 schema 1에 선택 필드 추가).

| 필드 | 의미 |
| --- | --- |
| `alarm_id` | 요청부터 대기·표시·중지까지 유지되는 알람 요청 ID |
| `source_session_id`, `source_observation_id` | 원인 관측의 수집 세션과 관측 ID. 수집 중이 아니었던 관측이나 CO₂ 등은 null |
| `related_alarm_id` | 중복 억제·대기 합치기·대체·오래된 중지 콜백과 관련된 기존 알람 ID. 관련 대상이 없으면 null |

원인 관측은 `(source_session_id, source_observation_id)`로 찾고, 알람 흐름은 `alarm_id`로 묶습니다. 같은 종류의 대기 요청이 합쳐지면 최초 요청을 유지하고 후속 요청에 `related_alarm_id`를 남깁니다. 대기 재개 시 `requested`가 같은 ID로 다시 기록될 수 있으므로 새 알람 수는 ID 기준으로 집계하세요. 알람 도중 수집 세션이 바뀌어도 원인 세션 ID는 바뀌지 않습니다. 수집 시작 전 이벤트는 소급 기록하지 않습니다.

### 예시 (줄바꿈은 보기 위해 넣음, 실제로는 한 줄)

```json
{"type":"observation","schema_version":1,"session_id":"20261103-142501_3f9a1c","part":1,"seq":42,"t_ms":10318,
 "observation_id":40,"analysis_session":3,"analysis_start_ms":10251,"analysis_end_ms":10318,"processing_ms":67,
 "interval_from_prev_start_ms":251,"camera_timestamp_ns":123456789012345,
 "image":{"width":640,"height":480,"rotation_degrees":270},"face_count":1,
 "faces":[{"index":0,"tracking_id":7,"bbox":{"left":120,"top":88,"width":210,"height":210},
   "left_eye_open":0.08,"right_eye_open":0.11,"smiling":0.02,
   "landmarks":{"mouth_bottom":{"x":224.1,"y":260.3},"nose_base":{"x":222.0,"y":205.7},"right_eye":{"x":180.4,"y":150.2}},
   "euler_x":-4.2,"euler_y":3.1,"euler_z":0.7,"value_issues":{},"selected":true,"unused_reason":null}],
 "selected_index":0,"target_reason":"kept_tracking_id","resets":[],
 "eyes":{"input":"present","outcome":"continued","condition_met":true,"active_before":true,"active":true,
   "alerted_in_episode":true,"elapsed_ms":1003,"alert_fired":true},
 "yawn":{"input":"present","outcome":"condition_not_met","condition_met":false,"ratio":0.4955,"active_before":false,
   "active":false,"alerted_in_episode":false,"elapsed_ms":null,"alert_fired":false},
 "alert_eyes_closed":true,"alert_yawn":false,"used_for_detection":true}
```

## 5. 권장 시나리오 (정차 상태)

| 시나리오 | 목적 |
| --- | --- |
| `front_open_blink` | 평상시 값과 흔들림 |
| `eyes_closed_0.5s`, `eyes_closed_1s`, `eyes_closed_2s` | 감김 시간과 경고 지연 |
| `look_left_right`, `head_down` | 얼굴 방향과 분류값 변화 |
| `talking`, `smiling`, `yawning` | 현재 하품 지표의 반응 |
| `face_covered`, `multiple_faces` | 누락 처리와 대상 선택 |

## 6. 성능 비교

debug 빌드는 수집 여부와 관계없이 약 10초마다 Logcat(`FaceDetectionManager` 태그)에 `분석 시간 요약`(분석 횟수·그중 수집 횟수·평균 간격·평균/최대 처리 시간)을 남깁니다.
수집 켬/끔 상태를 같은 조건에서 비교하세요. 실기기 측정 전에는 처리 시간·배터리 영향이 없다고 단정하지 않습니다.
수집 시작·중지·오류도 Logcat `DetectionLog` 태그에 한 줄씩 남습니다(관측 전체 데이터는 Logcat에 출력하지 않음).

## 7. 다음 단계

PC에서 JSONL을 CSV로 바꿔 눈 확률·얼굴 각도·관측 간격·초기화 사유·경고 시점을 비교합니다. 분석 프로그램은 이번 범위가 아닙니다.

## 8. 지속 눈 감김 재경고 (config_version 2)

- 첫 경고는 기존처럼 양쪽 눈 열림 확률이 모두 0.2 미만으로 1초 이상 유지될 때 발생합니다.
- 사용자가 졸음 알람을 끈 시점에도 최근의 감김 판정이 유효하면, 그 시점부터 3초 뒤 재경고를 대기합니다.
- 유효한 감김 관측이 계속 들어올 때, 3초가 지난 첫 관측에서 재경고합니다. 실제 표시 시점에는 분석 간격·처리 지연이 추가됩니다.
- 알람이 켜져 있는 동안에는 반복 경고 이벤트를 발생시키지 않습니다. 재경고도 사용자가 끈 뒤에만 다음 재경고를 대기합니다.
- 눈 뜸, 눈 값 누락·무효, 1초 초과 관측 공백, 얼굴 미감지, 대상 변경, 분석 실패, 분석 세션 변경은 대기 상태를 해제합니다.
- 하품·CO₂ 알람 중지와 오래된 알람 종료 콜백은 눈 감김 재경고를 예약하지 않습니다.
- 디버그 수집 여부와 관계없이 debug/release에 동일하게 적용합니다. 하품 판정 기준은 바꾸지 않습니다.

**3초는 안전성이 검증된 기준이 아닌 초기 실험값**입니다. 헤더의 `eyes_realert_after_stop_ms`와 `config_version = 2`로 적용 설정을 확인하세요. JSON 형식 버전(`schema_version = 1`)과 판정 설정 버전은 별개입니다.

정차 실험에서 시나리오를 `eyes_closed_realert`로 직접 입력하고, 관찰자가 첫 경고를 끈 뒤에도 눈 감김을 유지하는 경우를 확인하세요. `eyes_realert_scheduled` 이후 약 3초 뒤 새 관측 ID와 새 `alarm_id`의 경고가 발생해야 합니다. 중간에 눈을 뜨거나 얼굴을 가리면 이전 재경고 대기는 취소되고, 감김이 다시 관측되면 첫 경고 기준부터 새로 판단합니다.
