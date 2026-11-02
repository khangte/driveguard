package com.example.drowsydrive.detection;

/**
 * 눈 감김/하품 지속 시간을 판단하는 순수 Java 로직.
 * ML Kit, Activity, Canvas에 의존하지 않고 전달받은 값과 시간(ms)으로만 판단한다.
 */
public final class DrowsinessDetector {
    static final double EYE_OPEN_PROBABILITY_THRESHOLD = 0.2; // 두 눈 열림 확률이 이보다 작으면 감김
    static final long EYES_CLOSED_DURATION_THRESHOLD_MS = 1000; // 눈 감김 지속 시간 임계값
    static final double YAWN_MOUTH_RATIO_THRESHOLD = 0.80; // (입-코 거리)/(입-오른쪽 눈 거리) 비율
    static final double YAWN_SMILING_PROBABILITY_THRESHOLD = 0.2; // 웃음 확률이 이보다 작아야 하품
    static final long YAWN_DURATION_THRESHOLD_MS = 2500; // 하품 지속 시간 임계값

    private boolean eyesClosed = false;
    private long eyesClosedStartMs = 0;
    private boolean yawning = false;
    private long yawnStartMs = 0;

    /** 눈 상태를 갱신하고, 눈 감김이 임계 시간 이상 지속되면 true를 반환한다. */
    public boolean updateEyes(float leftEyeOpenProbability, float rightEyeOpenProbability, long nowMs) {
        if (leftEyeOpenProbability < EYE_OPEN_PROBABILITY_THRESHOLD
                && rightEyeOpenProbability < EYE_OPEN_PROBABILITY_THRESHOLD) {
            if (!eyesClosed) {
                eyesClosed = true;
                eyesClosedStartMs = nowMs;
                return false;
            }
            return nowMs - eyesClosedStartMs >= EYES_CLOSED_DURATION_THRESHOLD_MS;
        }
        eyesClosed = false;
        eyesClosedStartMs = 0;
        return false;
    }

    /** 입 상태를 갱신하고, 하품이 임계 시간 이상 지속되면 true를 반환한다. */
    public boolean updateYawn(float smilingProbability, float mouthBottomY, float noseBaseY,
                              float rightEyeY, long nowMs) {
        float lipToNoseDistance = Math.abs(mouthBottomY - noseBaseY);
        float lipToRightEyeDistance = Math.abs(mouthBottomY - rightEyeY);
        float relativeLipToNoseDistance = lipToNoseDistance / lipToRightEyeDistance;

        if (relativeLipToNoseDistance > YAWN_MOUTH_RATIO_THRESHOLD
                && smilingProbability < YAWN_SMILING_PROBABILITY_THRESHOLD) {
            if (!yawning) {
                yawning = true;
                yawnStartMs = nowMs;
                return false;
            }
            return nowMs - yawnStartMs >= YAWN_DURATION_THRESHOLD_MS;
        }
        yawning = false;
        yawnStartMs = 0;
        return false;
    }

    /** 얼굴이 감지되지 않으면 진행 중인 판정을 초기화한다. */
    public void reset() {
        eyesClosed = false;
        eyesClosedStartMs = 0;
        yawning = false;
        yawnStartMs = 0;
    }
}
