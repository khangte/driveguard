package com.example.drowsydrive.alert;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class TTSManager {
    private TextToSpeech tts;
    private boolean isInitialized = false;

    public TTSManager(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.KOREAN);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTSManager", "언어가 지원되지 않습니다.");
                } else {
                    isInitialized = true;
                }
            } else {
                Log.e("TTSManager", "TTS 초기화 실패");
            }
        });
    }

    public void speak(String message) {
        if (isInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            Log.e("TTSManager", "TTS가 초기화되지 않았습니다.");
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
