package com.example.drowsydrive.detection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DrowsinessDetectorTest {

    @Test
    public void eyesClosed_alertsOnlyAfterThresholdDuration() {
        DrowsinessDetector detector = new DrowsinessDetector();
        assertFalse(detector.updateEyes(0.1f, 0.1f, 0)); // 감김 시작
        assertFalse(detector.updateEyes(0.1f, 0.1f, 999));
        assertTrue(detector.updateEyes(0.1f, 0.1f, 1000)); // 1000ms 경계 포함
        assertTrue(detector.updateEyes(0.1f, 0.1f, 2000)); // 계속 감고 있으면 계속 경고
    }

    @Test
    public void eyesClosed_requiresBothEyesBelowThreshold() {
        DrowsinessDetector detector = new DrowsinessDetector();
        assertFalse(detector.updateEyes(0.1f, 0.2f, 0)); // 0.2는 감김 아님
        assertFalse(detector.updateEyes(0.1f, 0.2f, 5000));
    }

    @Test
    public void eyesOpen_resetsTimer() {
        DrowsinessDetector detector = new DrowsinessDetector();
        detector.updateEyes(0.1f, 0.1f, 0);
        detector.updateEyes(0.9f, 0.9f, 500); // 눈 뜸
        assertFalse(detector.updateEyes(0.1f, 0.1f, 1000)); // 다시 감김 시작
        assertFalse(detector.updateEyes(0.1f, 0.1f, 1999));
        assertTrue(detector.updateEyes(0.1f, 0.1f, 2000));
    }

    // 입-코 거리 9, 입-눈 거리 10 => 비율 0.9 (하품 조건)
    private static boolean yawn(DrowsinessDetector d, float smile, long now) {
        return d.updateYawn(smile, 100f, 91f, 90f, now);
    }

    @Test
    public void yawn_alertsOnlyAfterThresholdDuration() {
        DrowsinessDetector detector = new DrowsinessDetector();
        assertFalse(yawn(detector, 0.1f, 0));
        assertFalse(yawn(detector, 0.1f, 2499));
        assertTrue(yawn(detector, 0.1f, 2500));
    }

    @Test
    public void yawn_notDetectedWhenSmilingOrMouthRatioLow() {
        DrowsinessDetector detector = new DrowsinessDetector();
        assertFalse(yawn(detector, 0.2f, 0)); // 웃음 확률 0.2는 하품 아님
        assertFalse(yawn(detector, 0.2f, 3000));
        // 비율 0.7 (기준 0.80 미만)
        assertFalse(detector.updateYawn(0.1f, 100f, 93f, 90f, 4000));
        assertFalse(detector.updateYawn(0.1f, 100f, 93f, 90f, 8000));
    }

    @Test
    public void reset_clearsOngoingDetection() {
        DrowsinessDetector detector = new DrowsinessDetector();
        detector.updateEyes(0.1f, 0.1f, 0);
        yawn(detector, 0.1f, 0);
        detector.reset();
        assertFalse(detector.updateEyes(0.1f, 0.1f, 3000));
        assertFalse(yawn(detector, 0.1f, 3000));
    }
}
