package com.example.drowsydrive.alert;

import android.content.Context;
import android.util.Log;

import com.example.drowsydrive.R;

import java.util.EnumMap;
import java.util.UUID;

/**
 * 경고음/TTS 흐름을 담당하고, 다이얼로그/안내창 표시는 Ui 콜백으로 화면에 요청한다.
 * 메인 스레드에서 호출해야 한다.
 */
public class WarningAlarmController {
    private static final String TAG = "WarningAlarmController";

    public enum AlertType {
        YAWN("하품 경고", "하품이 감지되었습니다.\n졸음에 주의하세요!"),
        DROWSINESS("졸음 경고", "졸음이 감지되었습니다.\n휴식을 취하세요!"),
        CO2("CO₂ 경고", "CO₂ 농도가 위험 수준입니다.\n환기하세요!");

        public final String title;
        public final String message;

        AlertType(String title, String message) {
            this.title = title;
            this.message = message;
        }
    }

    public interface Ui {
        /** 경고 다이얼로그를 표시하고, 사용자가 알람을 끄면 onStop을 호출해야 한다. */
        void showAlarmDialog(AlertType alertType, Runnable onStop);
        /** 휴게소/졸음쉼터 안내창을 표시한다. */
        void showGuideDialog();
    }

    /**
     * 개발용 측정 로그에 알람 흐름을 남기기 위한 관찰자. 메인 스레드에서 호출된다.
     * 요청·표시 요청 시점을 알릴 뿐 경고음/TTS 재생 완료를 뜻하지 않는다.
     */
    public interface EventListener {
        /**
         * @param event requested, suppressed_duplicate, queued_pending, preempted_active,
         *              sound_unavailable, dialog_posted, stopped_by_user, stop_ignored_stale
         * @param activeBefore 이 이벤트 직전에 표시 중이던 경고(없으면 null)
         */
        void onAlarmEvent(String event, AlertType alertType, AlertType activeBefore,
                          AlarmTrace trace, String relatedAlarmId);
    }

    /** 요청부터 대기·표시·종료까지 유지하는 개발용 추적 정보. */
    public static final class AlarmTrace {
        public final String alarmId = UUID.randomUUID().toString();
        public final String sourceSessionId;
        public final Long sourceObservationId;

        AlarmTrace(String sourceSessionId, Long sourceObservationId) {
            this.sourceSessionId = sourceSessionId;
            this.sourceObservationId = sourceObservationId;
        }
    }

    private final SoundManager soundManager;
    private final TTSManager ttsManager;
    private final Ui ui;
    private AlertType activeAlert;
    private final EnumMap<AlertType, AlarmTrace> pendingAlerts = new EnumMap<>(AlertType.class);
    private AlarmTrace activeTrace;
    private long alarmGeneration = 0;
    private EventListener eventListener;
    private Runnable onDrowsinessAlarmStopped;

    public WarningAlarmController(Context context, Ui ui) {
        this.ui = ui;
        this.ttsManager = new TTSManager(context);
        this.soundManager = new SoundManager(context, R.raw.alarm_sound);
    }

    /** 유효한 졸음 알람을 사용자가 끈 경우에만 호출한다. 디버그 기록 여부와 무관하다. */
    public void setOnDrowsinessAlarmStopped(Runnable listener) {
        onDrowsinessAlarmStopped = listener;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    private void notifyEvent(String event, AlertType alertType, AlertType activeBefore,
                             AlarmTrace trace, String relatedAlarmId) {
        if (eventListener != null) {
            eventListener.onAlarmEvent(event, alertType, activeBefore, trace, relatedAlarmId);
        }
    }

    public void playWarningAlarm(AlertType alertType) {
        playWarningAlarm(alertType, null, null);
    }

    /** 원인 관측은 수집 세션과 함께 전달한다. 수집하지 않은 관측은 null이다. */
    public void playWarningAlarm(AlertType alertType, String sourceSessionId, Long sourceObservationId) {
        AlarmTrace trace = eventListener == null ? null : new AlarmTrace(sourceSessionId, sourceObservationId);
        playWarningAlarm(alertType, trace);
    }

    private void playWarningAlarm(AlertType alertType, AlarmTrace trace) {
        notifyEvent("requested", alertType, activeAlert, trace, null);
        if (activeAlert == alertType) {
            notifyEvent("suppressed_duplicate", alertType, activeAlert, trace, id(activeTrace));
            return;
        }
        if (activeAlert != null && alertType != AlertType.DROWSINESS) {
            // 같은 종류의 대기는 기존처럼 하나로 합치되 최초 요청의 원인을 보존한다.
            AlarmTrace pending = pendingAlerts.get(alertType);
            if (!pendingAlerts.containsKey(alertType)) pendingAlerts.put(alertType, trace);
            notifyEvent("queued_pending", alertType, activeAlert, trace, id(pending));
            return;
        }
        if (activeAlert != null) {
            notifyEvent("preempted_active", alertType, activeAlert, trace, id(activeTrace));
            soundManager.stopAlarm();
        }
        activeAlert = alertType;
        activeTrace = trace;
        pendingAlerts.remove(alertType);
        long generation = ++alarmGeneration;
        if (!soundManager.playAlarm()) {
            Log.w(TAG, "경고음을 재생할 수 없음: " + alertType);
            notifyEvent("sound_unavailable", alertType, null, trace, null);
        }
        ttsManager.speak(alertType.message);
        ui.showAlarmDialog(alertType, () -> {
            if (generation != alarmGeneration || activeAlert == null) {
                notifyEvent("stop_ignored_stale", alertType, activeAlert, trace, id(activeTrace));
                return;
            }
            notifyEvent("stopped_by_user", alertType, activeAlert, trace, null);
            soundManager.stopAlarm();
            ttsManager.cancelPending();
            activeAlert = null;
            activeTrace = null;
            if (alertType == AlertType.DROWSINESS && onDrowsinessAlarmStopped != null) {
                onDrowsinessAlarmStopped.run();
            }
            if (alertType == AlertType.DROWSINESS || alertType == AlertType.YAWN) {
                ui.showGuideDialog();
            }
            if (!pendingAlerts.isEmpty()) {
                AlertType next = pendingAlerts.containsKey(AlertType.CO2)
                        ? AlertType.CO2 : pendingAlerts.keySet().iterator().next();
                // 대기 당시의 ID/원인으로 재개하며 새 알람 ID를 만들지 않는다.
                playWarningAlarm(next, pendingAlerts.get(next));
            }
        });
        notifyEvent("dialog_posted", alertType, null, trace, null);
    }

    private static String id(AlarmTrace trace) {
        return trace == null ? null : trace.alarmId;
    }

    public void release() {
        ++alarmGeneration;
        activeAlert = null;
        activeTrace = null;
        pendingAlerts.clear();
        onDrowsinessAlarmStopped = null;
        soundManager.release();
        ttsManager.shutdown();
    }
}
