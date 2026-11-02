package com.example.drowsydrive.alert;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

/**
 * 경고음 재생/정지만 담당한다. 다이얼로그와 TTS는 다루지 않는다.
 */
public class SoundManager {
    private static final String TAG = "SoundManager";

    private final SoundPool soundPool;
    private final int alarmSound; // 로드된 사운드 ID
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

        alarmSound = soundPool.load(context, alarmSoundResId, 1);
    }

    /** 경고음을 재생하고 성공 여부를 반환한다. */
    public boolean playAlarm() {
        alarmStreamId = soundPool.play(alarmSound, 1, 1, 1, 0, 1);
        if (alarmStreamId == 0) {
            Log.d(TAG, "알람이 재생되고 있지 않음");
            return false;
        }
        Log.d(TAG, "알람이 재생되고 있음");
        return true;
    }

    public void stopAlarm() {
        if (alarmStreamId != 0) {
            soundPool.stop(alarmStreamId);
            alarmStreamId = 0;
        }
        Log.d(TAG, "알람이 정지됨");
    }

    public void release() {
        soundPool.release();
    }
}
