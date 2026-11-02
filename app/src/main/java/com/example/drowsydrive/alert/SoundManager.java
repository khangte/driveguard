package com.example.drowsydrive.alert;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

/**
 * 경고음 재생/정지만 담당한다. 다이얼로그와 TTS는 다루지 않는다.
 * 메인 스레드에서 생성·호출해야 한다(로드 완료 콜백도 생성 스레드의 Looper로 전달된다).
 */
public class SoundManager {
    private static final String TAG = "SoundManager";

    private final SoundPool soundPool;
    private final int alarmSound; // 로드된 사운드 ID
    private final ReadyGate<Boolean> loadGate = new ReadyGate<>();
    private int alarmStreamId = 0; // 재생 중인 스트림 ID (stop은 스트림 ID로 해야 한다)

    public SoundManager(Context context, int alarmSoundResId) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA) // 미디어 재생 용도 설정
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION) // 효과음 컨텐츠 타입 설정
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build();
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            if (status != 0) {
                Log.e(TAG, "경고음 로드 실패: " + status);
                loadGate.markFailed();
                return;
            }
            // 로드 전에 들어온 경고가 아직 유효하면 지금 재생한다.
            if (loadGate.markReady() != null) startStream();
        });

        alarmSound = soundPool.load(context, alarmSoundResId, 1);
    }

    /**
     * 경고음을 재생한다. 아직 로드 중이면 로드 완료 시 재생하도록 예약한다.
     * 재생했거나 예약했으면 true, 로드 실패 등으로 재생할 수 없으면 false를 반환한다.
     */
    public boolean playAlarm() {
        if (loadGate.submit(Boolean.TRUE)) return startStream();
        if (loadGate.isFailed()) return false;
        Log.d(TAG, "경고음 로드 전이라 재생을 예약함");
        return true;
    }

    private boolean startStream() {
        alarmStreamId = soundPool.play(alarmSound, 1, 1, 1, 0, 1);
        if (alarmStreamId == 0) {
            Log.d(TAG, "알람이 재생되고 있지 않음");
            return false;
        }
        Log.d(TAG, "알람이 재생되고 있음");
        return true;
    }

    /** 재생 중인 경고음과 로드 완료 후 재생 예약을 모두 취소한다. */
    public void stopAlarm() {
        loadGate.cancelPending();
        if (alarmStreamId != 0) {
            soundPool.stop(alarmStreamId);
            alarmStreamId = 0;
        }
        Log.d(TAG, "알람이 정지됨");
    }

    public void release() {
        loadGate.release();
        soundPool.release();
    }
}
