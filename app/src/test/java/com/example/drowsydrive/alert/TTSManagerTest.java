package com.example.drowsydrive.alert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.speech.tts.TextToSpeech;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** 가짜 엔진으로 외부 초기화 콜백 순서를 제어해 TTSManager의 연결 경로를 검증한다. */
public class TTSManagerTest {

    private static final class FakeEngine implements TTSManager.Engine {
        int languageResult = TextToSpeech.LANG_AVAILABLE;
        int setLanguageCalls = 0;
        final List<String> spoken = new ArrayList<>();
        boolean shutdown = false;

        @Override
        public int setLanguage(Locale locale) {
            setLanguageCalls++;
            return languageResult;
        }

        @Override
        public void speak(String message) {
            spoken.add(message);
        }

        @Override
        public void stop() {}

        @Override
        public void shutdown() {
            shutdown = true;
        }
    }

    private FakeEngine engine;
    private IntConsumer onInit;
    private TTSManager manager;

    @Before
    public void setUp() {
        engine = new FakeEngine();
        // 메인 스레드 전달을 즉시 실행으로 대체한다.
        manager = new TTSManager(Runnable::run, callback -> {
            onInit = callback;
            return engine;
        });
    }

    @Test
    public void requestBeforeInit_isSpokenOnceAfterInit() {
        manager.speak("졸음");
        assertTrue(engine.spoken.isEmpty());
        onInit.accept(TextToSpeech.SUCCESS);
        assertEquals(Collections.singletonList("졸음"), engine.spoken);
    }

    @Test
    public void replacedRequestBeforeInit_onlyLatestIsSpoken() {
        manager.speak("하품");
        manager.speak("졸음");
        onInit.accept(TextToSpeech.SUCCESS);
        assertEquals(Collections.singletonList("졸음"), engine.spoken);
    }

    @Test
    public void cancelledRequestBeforeInit_isNotSpoken() {
        manager.speak("하품");
        manager.cancelPending();
        onInit.accept(TextToSpeech.SUCCESS);
        assertTrue(engine.spoken.isEmpty());
    }

    @Test
    public void initSuccessAfterShutdown_doesNotTouchEngine() {
        manager.speak("졸음");
        manager.shutdown();
        onInit.accept(TextToSpeech.SUCCESS);
        assertEquals(0, engine.setLanguageCalls);
        assertTrue(engine.spoken.isEmpty());
        assertTrue(engine.shutdown);
    }

    @Test
    public void failureThenDuplicateCallbacksAfterShutdown_doNotTouchEngine() {
        manager.shutdown();
        onInit.accept(TextToSpeech.ERROR);
        onInit.accept(TextToSpeech.SUCCESS);
        manager.speak("졸음");
        assertEquals(0, engine.setLanguageCalls);
        assertTrue(engine.spoken.isEmpty());
    }

    @Test
    public void duplicateSuccessCallback_setsLanguageOnceAndSpeaksOnce() {
        manager.speak("졸음");
        onInit.accept(TextToSpeech.SUCCESS);
        onInit.accept(TextToSpeech.SUCCESS);
        assertEquals(1, engine.setLanguageCalls);
        assertEquals(Collections.singletonList("졸음"), engine.spoken);
    }

    @Test
    public void successAfterFailure_doesNotRevertFailedState() {
        manager.speak("졸음");
        onInit.accept(TextToSpeech.ERROR);
        onInit.accept(TextToSpeech.SUCCESS);
        manager.speak("하품");
        assertEquals(0, engine.setLanguageCalls);
        assertTrue(engine.spoken.isEmpty());
    }

    @Test
    public void unsupportedLanguage_dropsPendingAndLaterRequests() {
        engine.languageResult = TextToSpeech.LANG_NOT_SUPPORTED;
        manager.speak("졸음");
        onInit.accept(TextToSpeech.SUCCESS);
        manager.speak("하품");
        assertTrue(engine.spoken.isEmpty());
    }

    @Test
    public void afterInit_requestsAreSpokenImmediately() {
        onInit.accept(TextToSpeech.SUCCESS);
        manager.speak("졸음");
        assertEquals(Collections.singletonList("졸음"), engine.spoken);
    }
}
