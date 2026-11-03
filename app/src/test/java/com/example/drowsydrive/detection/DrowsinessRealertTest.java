package com.example.drowsydrive.detection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DrowsinessRealertTest {
    private static boolean closed(DrowsinessDetector d, long t) {
        return d.updateEyes(0.1f, 0.1f, t);
    }

    private static void quiet(DrowsinessDetector d, long from, long until) {
        for (long t = from; t < until; t += 250) assertFalse("t=" + t, closed(d, t));
    }

    private static DrowsinessDetector firstAlert() {
        DrowsinessDetector d = new DrowsinessDetector();
        quiet(d, 0, 1000);
        assertTrue(closed(d, 1000));
        return d;
    }

    @Test
    public void noRepeatedEventsUntilUserStopsAlarm() {
        DrowsinessDetector d = firstAlert();
        quiet(d, 1250, 16000); // 기존 로그의 긴 감김도 알람이 켜진 동안에는 중복 통지하지 않음
    }

    @Test
    public void replaysAfterThreeSecondsFromEachStopOnly() {
        DrowsinessDetector d = firstAlert();
        assertFalse(closed(d, 1250));
        assertTrue(d.onEyesAlarmStopped(1250));
        assertFalse(d.onEyesAlarmStopped(1500)); // 중복 통지로 기한을 미루지 않음
        quiet(d, 1500, 4250);
        assertFalse(closed(d, 4249));
        assertTrue(closed(d, 4250));
        quiet(d, 4500, 8000); // 다시 끄기 전에는 추가 통지하지 않음
        assertTrue(d.onEyesAlarmStopped(8000));
        quiet(d, 8000, 11000);
        assertTrue(closed(d, 11000));
    }

    @Test
    public void delayedStopMessageUsesFreshObservationAndOriginalStopTime() {
        DrowsinessDetector d = firstAlert();
        assertFalse(closed(d, 1250)); // 중지 알림보다 새 관측이 먼저 처리됨
        assertTrue(d.onEyesAlarmStopped(1100, 1250));
        quiet(d, 1500, 4001);
        assertFalse(closed(d, 4099));
        assertTrue(closed(d, 4100)); // 처리 시각 1250이 아닌 실제 중지 1100부터 3초

        DrowsinessDetector switched = firstAlert();
        assertFalse(switched.updateEyes(0.9f, 0.9f, 1125));
        quiet(switched, 1250, 2250);
        assertTrue(closed(switched, 2250));
        assertFalse(switched.onEyesAlarmStopped(1100, 2250)); // 이전 감김의 늦은 중지 알림은 무시
    }

    @Test
    public void openingEyesCancelsPendingReplayAndNewEpisodeStillAlertsAfterOneSecond() {
        DrowsinessDetector d = firstAlert();
        assertTrue(d.onEyesAlarmStopped(1250));
        assertFalse(d.updateEyes(0.9f, 0.9f, 1500));
        quiet(d, 1750, 2750);
        assertTrue(closed(d, 2750));
        quiet(d, 3000, 5000); // 이전 중지의 4250ms 기한은 폐기됨
    }

    @Test
    public void missingInvalidOrResetObservationCancelsPendingReplay() {
        for (int cause = 0; cause < 3; cause++) {
            DrowsinessDetector d = firstAlert();
            assertTrue(d.onEyesAlarmStopped(1250));
            if (cause == 0) d.invalidateEyes();
            else if (cause == 1) assertFalse(d.updateEyes(Float.NaN, 0.1f, 1400));
            else d.reset();
            quiet(d, 1500, 2500);
            assertTrue(closed(d, 2500));
            quiet(d, 2750, 4750);
        }
    }

    @Test
    public void observationGapCancelsReplayAndStartsNewEpisode() {
        DrowsinessDetector d = firstAlert();
        assertTrue(d.onEyesAlarmStopped(1000));
        quiet(d, 2251, 3251); // 첫 관측 공백이 1초를 초과
        assertTrue(closed(d, 3251));
        quiet(d, 3501, 4501); // 예전 재경고 기한 4000ms에는 재통지하지 않음
    }

    @Test
    public void stopWithoutFreshAlertedEyesIsIgnored() {
        DrowsinessDetector d = new DrowsinessDetector();
        assertFalse(d.onEyesAlarmStopped(0));
        assertFalse(closed(d, 0));
        assertFalse(d.onEyesAlarmStopped(250));
        d = firstAlert();
        assertFalse(d.onEyesAlarmStopped(999)); // 마지막 관측보다 과거인 중지
        assertFalse(d.onEyesAlarmStopped(2001)); // 마지막 관측이 오래됨
        assertFalse(d.updateEyes(0.9f, 0.9f, 1250));
        assertFalse(d.onEyesAlarmStopped(1500));
    }

    @Test
    public void eyeStopDoesNotRearmYawn() {
        DrowsinessDetector d = firstAlert();
        for (long t = 1000; t < 3500; t += 250) {
            assertFalse(d.updateYawn(0.1f, 100f, 91f, 90f, t));
        }
        assertTrue(d.updateYawn(0.1f, 100f, 91f, 90f, 3500));
        assertTrue(d.onEyesAlarmStopped(1250));
        for (long t = 3750; t < 9000; t += 250) {
            assertFalse(d.updateYawn(0.1f, 100f, 91f, 90f, t));
        }
    }
}
