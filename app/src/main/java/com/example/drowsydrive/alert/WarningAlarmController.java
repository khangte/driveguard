package com.example.drowsydrive.alert;

import android.content.Context;

import com.example.drowsydrive.R;

import java.util.EnumSet;

/**
 * 경고음/TTS 흐름을 담당하고, 다이얼로그/안내창 표시는 Ui 콜백으로 화면에 요청한다.
 * 메인 스레드에서 호출해야 한다.
 */
public class WarningAlarmController {

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

    private final SoundManager soundManager;
    private final TTSManager ttsManager;
    private final Ui ui;
    private AlertType activeAlert;
    private final EnumSet<AlertType> pendingAlerts = EnumSet.noneOf(AlertType.class);
    private long alarmGeneration = 0;

    public WarningAlarmController(Context context, Ui ui) {
        this.ui = ui;
        this.ttsManager = new TTSManager(context);
        this.soundManager = new SoundManager(context, R.raw.alarm_sound);
    }

    public void playWarningAlarm(AlertType alertType) {
        if (activeAlert == alertType) return;
        if (activeAlert != null && alertType != AlertType.DROWSINESS) {
            // 판정기는 에피소드당 한 번만 통지하므로 겹친 경고를 버리지 않는다.
            pendingAlerts.add(alertType);
            return;
        }
        // 졸음 경고는 현재 표시 중인 하품/CO2 경고보다 우선한다.
        if (activeAlert != null) soundManager.stopAlarm();
        activeAlert = alertType;
        pendingAlerts.remove(alertType);
        long generation = ++alarmGeneration;
        soundManager.playAlarm();
        ttsManager.speak(alertType.message);
        ui.showAlarmDialog(alertType, () -> {
            if (generation != alarmGeneration || activeAlert == null) return;
            soundManager.stopAlarm();
            activeAlert = null;
            if (alertType == AlertType.DROWSINESS || alertType == AlertType.YAWN) {
                ui.showGuideDialog();
            }
            if (!pendingAlerts.isEmpty()) {
                AlertType next = pendingAlerts.contains(AlertType.CO2)
                        ? AlertType.CO2 : pendingAlerts.iterator().next();
                playWarningAlarm(next);
            }
        });
    }

    public void release() {
        ++alarmGeneration;
        activeAlert = null;
        pendingAlerts.clear();
        soundManager.release();
        ttsManager.shutdown();
    }
}
