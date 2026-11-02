package com.example.drowsydrive.alert;

import android.content.Context;

import com.example.drowsydrive.R;

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
    private boolean alarmActive = false; // 경고 다이얼로그가 떠 있는 동안 중복 경고를 막는다.

    public WarningAlarmController(Context context, Ui ui) {
        this.ui = ui;
        this.ttsManager = new TTSManager(context);
        this.soundManager = new SoundManager(context, R.raw.alarm_sound);
    }

    public void playWarningAlarm(AlertType alertType) {
        if (alarmActive) return;
        alarmActive = true;

        soundManager.playAlarm();
        ttsManager.speak(alertType.message);
        ui.showAlarmDialog(alertType, () -> {
            soundManager.stopAlarm();
            alarmActive = false;
            if (alertType == AlertType.DROWSINESS || alertType == AlertType.YAWN) {
                ui.showGuideDialog();
            }
        });
    }

    public void release() {
        soundManager.release();
        ttsManager.shutdown();
    }
}
