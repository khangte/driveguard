package com.example.drowsydrive.detection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DrowsinessDetectorTest {

    private static final long STEP = 250; // 분석 주기와 같은 관측 간격

    /** from부터 to 직전까지 STEP 간격으로 눈 감김을 관측하고, 경고가 없었는지 확인한다. */
    private static void eyesClosedWithoutAlert(DrowsinessDetector d, long from, long to) {
        for (long t = from; t < to; t += STEP) {
            assertFalse("t=" + t, d.updateEyes(0.1f, 0.1f, t));
        }
    }

    @Test
    public void eyesClosed_alertsOnlyAfterThresholdDuration() {
        DrowsinessDetector detector = new DrowsinessDetector();
        assertFalse(detector.updateEyes(0.1f, 0.1f, 0)); // 감김 시작
        assertFalse(detector.updateEyes(0.1f, 0.1f, 500));
        assertFalse(detector.updateEyes(0.1f, 0.1f, 999));
        assertTrue(detector.updateEyes(0.1f, 0.1f, 1000)); // 1000ms 경계 포함
    }

    @Test
    public void eyesClosed_alertsOncePerEpisode() {
        DrowsinessDetector detector = new DrowsinessDetector();
        eyesClosedWithoutAlert(detector, 0, 1000);
        assertTrue(detector.updateEyes(0.1f, 0.1f, 1000));
        eyesClosedWithoutAlert(detector, 1250, 5000); // 같은 에피소드에서는 다시 경고하지 않는다
    }

    @Test
    public void eyesClosed_rearmsAfterEyesOpen() {
        DrowsinessDetector detector = new DrowsinessDetector();
        eyesClosedWithoutAlert(detector, 0, 1000);
        assertTrue(detector.updateEyes(0.1f, 0.1f, 1000));
        assertFalse(detector.updateEyes(0.9f, 0.9f, 1250)); // 정상 상태
        eyesClosedWithoutAlert(detector, 1500, 2500);
        assertTrue(detector.updateEyes(0.1f, 0.1f, 2500));
    }

    @Test
    public void eyesClosed_rearmsAfterReset() {
        DrowsinessDetector detector = new DrowsinessDetector();
        eyesClosedWithoutAlert(detector, 0, 1000);
        assertTrue(detector.updateEyes(0.1f, 0.1f, 1000));
        detector.reset();
        eyesClosedWithoutAlert(detector, 1250, 2250);
        assertTrue(detector.updateEyes(0.1f, 0.1f, 2250));
    }

    @Test
    public void eyesClosed_requiresBothEyesBelowThreshold() {
        DrowsinessDetector detector = new DrowsinessDetector();
        assertFalse(detector.updateEyes(0.1f, 0.2f, 0)); // 0.2는 감김 아님
        assertFalse(detector.updateEyes(0.1f, 0.2f, 500));
        assertFalse(detector.updateEyes(0.1f, 0.2f, 1000));
    }

    @Test
    public void eyesOpen_resetsTimer() {
        DrowsinessDetector detector = new DrowsinessDetector();
        detector.updateEyes(0.1f, 0.1f, 0);
        detector.updateEyes(0.9f, 0.9f, 500); // 눈 뜸
        assertFalse(detector.updateEyes(0.1f, 0.1f, 1000)); // 다시 감김 시작
        assertFalse(detector.updateEyes(0.1f, 0.1f, 1500));
        assertFalse(detector.updateEyes(0.1f, 0.1f, 1999));
        assertTrue(detector.updateEyes(0.1f, 0.1f, 2000));
    }

    @Test
    public void eyesClosed_gapLongerThanAllowedRestartsEpisode() {
        DrowsinessDetector detector = new DrowsinessDetector();
        assertFalse(detector.updateEyes(0.1f, 0.1f, 0));
        // 허용 간격(1000ms)을 넘는 공백은 지속 시간에 포함하지 않는다.
        assertFalse(detector.updateEyes(0.1f, 0.1f, 1001));
        assertFalse(detector.updateEyes(0.1f, 0.1f, 1500));
        assertTrue(detector.updateEyes(0.1f, 0.1f, 2001));
    }

    @Test
    public void eyesClosed_gapAtAllowedLimitStaysContinuous() {
        DrowsinessDetector detector = new DrowsinessDetector();
        assertFalse(detector.updateEyes(0.1f, 0.1f, 0));
        assertTrue(detector.updateEyes(0.1f, 0.1f, DrowsinessDetector.MAX_OBSERVATION_GAP_MS));
    }

    @Test
    public void eyesClosed_timeGoingBackwardsRestartsEpisode() {
        DrowsinessDetector detector = new DrowsinessDetector();
        assertFalse(detector.updateEyes(0.1f, 0.1f, 5000));
        assertFalse(detector.updateEyes(0.1f, 0.1f, 4000));
        assertFalse(detector.updateEyes(0.1f, 0.1f, 4750));
        assertTrue(detector.updateEyes(0.1f, 0.1f, 5000));
    }

    @Test
    public void eyesClosed_missingObservationIsNotCounted() {
        DrowsinessDetector detector = new DrowsinessDetector();
        eyesClosedWithoutAlert(detector, 0, 1000);
        detector.invalidateEyes(); // 눈 확률 누락
        eyesClosedWithoutAlert(detector, 1000, 2000);
        assertTrue(detector.updateEyes(0.1f, 0.1f, 2000));
    }

    @Test
    public void eyesClosed_nonFiniteProbabilityIsInvalidObservation() {
        DrowsinessDetector detector = new DrowsinessDetector();
        eyesClosedWithoutAlert(detector, 0, 1000);
        assertFalse(detector.updateEyes(Float.NaN, 0.1f, 1000));
        assertFalse(detector.updateEyes(0.1f, Float.NEGATIVE_INFINITY, 1250));
        eyesClosedWithoutAlert(detector, 1500, 2500);
        assertTrue(detector.updateEyes(0.1f, 0.1f, 2500));
    }

    // 입-코 거리 9, 입-눈 거리 10 => 비율 0.9 (하품 조건)
    private static boolean yawn(DrowsinessDetector d, float smile, long now) {
        return d.updateYawn(smile, 100f, 91f, 90f, now);
    }

    private static void yawnWithoutAlert(DrowsinessDetector d, long from, long to) {
        for (long t = from; t < to; t += STEP) {
            assertFalse("t=" + t, yawn(d, 0.1f, t));
        }
    }

    @Test
    public void yawn_alertsOnlyAfterThresholdDuration() {
        DrowsinessDetector detector = new DrowsinessDetector();
        yawnWithoutAlert(detector, 0, 2500);
        assertFalse(yawn(detector, 0.1f, 2499));
        assertTrue(yawn(detector, 0.1f, 2500));
    }

    @Test
    public void yawn_alertsOncePerEpisodeAndRearmsAfterNormal() {
        DrowsinessDetector detector = new DrowsinessDetector();
        yawnWithoutAlert(detector, 0, 2500);
        assertTrue(yawn(detector, 0.1f, 2500));
        yawnWithoutAlert(detector, 2750, 6000);
        // 비율 0.7: 정상 상태
        assertFalse(detector.updateYawn(0.1f, 100f, 93f, 90f, 6000));
        yawnWithoutAlert(detector, 6250, 8750);
        assertTrue(yawn(detector, 0.1f, 8750));
    }

    @Test
    public void yawn_notDetectedWhenSmilingOrMouthRatioLow() {
        DrowsinessDetector detector = new DrowsinessDetector();
        assertFalse(yawn(detector, 0.2f, 0)); // 웃음 확률 0.2는 하품 아님
        assertFalse(yawn(detector, 0.2f, 1000));
        assertFalse(yawn(detector, 0.2f, 2000));
        assertFalse(yawn(detector, 0.2f, 3000));
        // 비율 0.7 (기준 0.80 미만)
        for (long t = 4000; t <= 8000; t += STEP) {
            assertFalse(detector.updateYawn(0.1f, 100f, 93f, 90f, t));
        }
    }

    @Test
    public void yawn_ratioBoundaryUnchanged() {
        DrowsinessDetector detector = new DrowsinessDetector();
        // 입-코 7.5, 입-눈 10 => 비율 0.75: 기준 0.80 미만이므로 하품 아님
        for (long t = 0; t <= 3000; t += STEP) {
            assertFalse(detector.updateYawn(0.1f, 100f, 92.5f, 90f, t));
        }
        // 눈이 입보다 아래에 있어도 절댓값 거리 비율은 같다 (기존 계산 유지)
        for (long t = 3250; t < 5750; t += STEP) {
            assertFalse(detector.updateYawn(0.1f, 100f, 109f, 110f, t));
        }
        assertTrue(detector.updateYawn(0.1f, 100f, 109f, 110f, 5750));
    }

    @Test
    public void yawn_zeroOrNearZeroDenominatorIsInvalid() {
        DrowsinessDetector detector = new DrowsinessDetector();
        yawnWithoutAlert(detector, 0, 2000);
        assertFalse(detector.updateYawn(0.1f, 100f, 91f, 100f, 2000)); // 입-눈 거리 0
        assertFalse(detector.updateYawn(0.1f, 100f, 91f, 100.5f, 2250)); // 근접 0
        // 무효 관측으로 이전 하품 시간이 이어지지 않는다.
        yawnWithoutAlert(detector, 2500, 5000);
        assertTrue(yawn(detector, 0.1f, 5000));
    }

    @Test
    public void yawn_nonFiniteValuesAreInvalid() {
        DrowsinessDetector detector = new DrowsinessDetector();
        float[][] invalid = {
                {Float.NaN, 100f, 91f, 90f},
                {0.1f, Float.POSITIVE_INFINITY, 91f, 90f},
                {0.1f, 100f, Float.NaN, 90f},
                {0.1f, 100f, 91f, Float.NEGATIVE_INFINITY},
                {0.1f, 3e38f, -3e38f, 90f}, // 거리 계산이 무한대가 되는 좌표
        };
        for (float[] v : invalid) {
            yawnWithoutAlert(detector, 0, 2500);
            assertFalse(detector.updateYawn(v[0], v[1], v[2], v[3], 2500));
            // 무효 관측 직후 유효 관측은 새 에피소드의 시작이다.
            assertFalse(yawn(detector, 0.1f, 2750));
            detector.reset();
        }
    }

    @Test
    public void yawn_missingObservationIsNotCounted() {
        DrowsinessDetector detector = new DrowsinessDetector();
        yawnWithoutAlert(detector, 0, 2500);
        detector.invalidateYawn();
        yawnWithoutAlert(detector, 2500, 5000);
        assertTrue(yawn(detector, 0.1f, 5000));
    }

    @Test
    public void reset_clearsOngoingDetection() {
        DrowsinessDetector detector = new DrowsinessDetector();
        eyesClosedWithoutAlert(detector, 0, 750);
        yawnWithoutAlert(detector, 0, 750);
        detector.reset();
        assertFalse(detector.updateEyes(0.1f, 0.1f, 1000));
        assertFalse(yawn(detector, 0.1f, 1000));
        assertFalse(detector.updateEyes(0.1f, 0.1f, 1250)); // 초기화 전 시작 시각 기준이면 경고됐을 시점
    }
}
