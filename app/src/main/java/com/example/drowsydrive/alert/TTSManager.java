package com.example.drowsydrive.alert;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;

/** 경고 음성 안내를 담당한다. 메인 스레드에서 호출해야 한다. */
public class TTSManager {
    private static final String TAG = "TTSManager";

    /** 사용하는 TextToSpeech 기능만 모은 경계. 테스트에서 외부 초기화 콜백 순서를 제어하기 위해 둔다. */
    interface Engine {
        int setLanguage(Locale locale);
        void speak(String message);
        void stop();
        void shutdown();
    }

    interface EngineFactory {
        /** 엔진을 만들고, 초기화가 끝나면 onInit에 상태를 전달한다(호출 스레드는 엔진 구현에 따름). */
        Engine create(IntConsumer onInit);
    }

    private final Executor mainExecutor;
    private final ReadyGate<String> initGate = new ReadyGate<>();
    private final Engine engine;
    private boolean released = false;

    public TTSManager(Context context) {
        this(ContextCompat.getMainExecutor(context), onInit -> new TextToSpeechEngine(context, onInit));
    }

    TTSManager(Executor mainExecutor, EngineFactory factory) {
        this.mainExecutor = mainExecutor;
        // 초기화 콜백 스레드는 엔진 구현에 따라 다를 수 있으므로 메인 스레드로 옮겨 상태를 한 스레드에서만 다룬다.
        this.engine = factory.create(status -> this.mainExecutor.execute(() -> onInit(status)));
    }

    private void onInit(int status) {
        // 종료 후 또는 이미 결과가 확정된 뒤의 콜백(중복 포함)은 엔진 API를 호출하지 않고 무시한다.
        if (released || !initGate.isPending()) return;
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS 초기화 실패");
            initGate.markFailed();
            return;
        }
        int result = engine.setLanguage(Locale.KOREAN);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e(TAG, "언어가 지원되지 않습니다.");
            initGate.markFailed();
            return;
        }
        // 초기화 전에 들어온 경고 중 아직 유효한 마지막 안내만 말한다.
        String pending = initGate.markReady();
        if (pending != null) engine.speak(pending);
    }

    /** 안내를 말한다. 초기화 중이면 초기화 완료 시 말하도록 마지막 요청만 보관한다. */
    public void speak(String message) {
        if (released) return;
        if (initGate.submit(message)) {
            engine.speak(message);
        } else if (initGate.isFailed()) {
            Log.e(TAG, "TTS를 사용할 수 없어 안내를 건너뜁니다.");
        }
    }

    /** 초기화 완료 후 말하도록 보관한 안내를 취소한다. 이미 말하고 있는 안내는 유지한다. */
    public void cancelPending() {
        initGate.cancelPending();
    }

    public void shutdown() {
        if (released) return;
        // 종료 표시를 먼저 확정해, 이후 도착하는 초기화 콜백이 해제된 엔진을 건드리지 않게 한다.
        released = true;
        initGate.release();
        engine.stop();
        engine.shutdown();
    }

    private static final class TextToSpeechEngine implements Engine {
        private final TextToSpeech tts;

        TextToSpeechEngine(Context context, IntConsumer onInit) {
            tts = new TextToSpeech(context, onInit::accept);
        }

        @Override
        public int setLanguage(Locale locale) {
            return tts.setLanguage(locale);
        }

        @Override
        public void speak(String message) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
        }

        @Override
        public void stop() {
            tts.stop();
        }

        @Override
        public void shutdown() {
            tts.shutdown();
        }
    }
}
