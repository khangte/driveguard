package com.example.drowsydrive.detection;

/**
 * 눈 감김/하품 지속 시간을 판단하는 순수 Java 로직.
 * ML Kit, Activity, Canvas에 의존하지 않고 전달받은 값과 시간(ms)으로만 판단한다.
 *
 * <p>지속 시간은 연속된 유효 관측으로만 계산한다. 관측 공백이 {@link #MAX_OBSERVATION_GAP_MS}를
 * 넘거나, 무효 관측(누락/비유한 값)이 들어오면 진행 중인 에피소드를 초기화한다.
 * 한 에피소드에서는 한 번만 true를 반환하고, 정상 상태 관측 또는 초기화 후 다시 경고할 수 있다.
 * 스레드 안전하지 않으므로 한 스레드에서만 호출해야 한다.
 */
public final class DrowsinessDetector {
    static final double EYE_OPEN_PROBABILITY_THRESHOLD = 0.2; // 두 눈 열림 확률이 이보다 작으면 감김
    static final long EYES_CLOSED_DURATION_THRESHOLD_MS = 1000; // 눈 감김 지속 시간 임계값
    static final double YAWN_MOUTH_RATIO_THRESHOLD = 0.80; // (입-코 거리)/(입-오른쪽 눈 거리) 비율
    static final double YAWN_SMILING_PROBABILITY_THRESHOLD = 0.2; // 웃음 확률이 이보다 작아야 하품
    static final long YAWN_DURATION_THRESHOLD_MS = 2500; // 하품 지속 시간 임계값
    /**
     * 연속 관측으로 인정하는 최대 간격. 분석 주기(250ms)의 4배이며 이전 분석 주기(1000ms)와 같다.
     * 측정 전 실험값이다.
     */
    static final long MAX_OBSERVATION_GAP_MS = 1000;
    /** 입-눈 거리가 이보다 작으면(픽셀) 비율 계산이 불안정하므로 무효 관측으로 본다. */
    static final float MIN_YAWN_REFERENCE_DISTANCE = 1.0f;

    private final Episode eyes = new Episode();
    private final Episode yawn = new Episode();

    /** 눈 상태를 갱신하고, 눈 감김이 임계 시간 이상 지속되면 에피소드당 한 번 true를 반환한다. */
    public boolean updateEyes(float leftEyeOpenProbability, float rightEyeOpenProbability, long nowMs) {
        if (!isFinite(leftEyeOpenProbability) || !isFinite(rightEyeOpenProbability)) {
            eyes.reset();
            return false;
        }
        boolean closed = leftEyeOpenProbability < EYE_OPEN_PROBABILITY_THRESHOLD
                && rightEyeOpenProbability < EYE_OPEN_PROBABILITY_THRESHOLD;
        return eyes.update(closed, nowMs, EYES_CLOSED_DURATION_THRESHOLD_MS);
    }

    /** 입 상태를 갱신하고, 하품이 임계 시간 이상 지속되면 에피소드당 한 번 true를 반환한다. */
    public boolean updateYawn(float smilingProbability, float mouthBottomY, float noseBaseY,
                              float rightEyeY, long nowMs) {
        if (!isFinite(smilingProbability) || !isFinite(mouthBottomY)
                || !isFinite(noseBaseY) || !isFinite(rightEyeY)) {
            yawn.reset();
            return false;
        }
        float lipToNoseDistance = Math.abs(mouthBottomY - noseBaseY);
        float lipToRightEyeDistance = Math.abs(mouthBottomY - rightEyeY);
        if (lipToRightEyeDistance < MIN_YAWN_REFERENCE_DISTANCE) {
            yawn.reset();
            return false;
        }
        float relativeLipToNoseDistance = lipToNoseDistance / lipToRightEyeDistance;
        if (!isFinite(relativeLipToNoseDistance)) {
            yawn.reset();
            return false;
        }

        boolean yawning = relativeLipToNoseDistance > YAWN_MOUTH_RATIO_THRESHOLD
                && smilingProbability < YAWN_SMILING_PROBABILITY_THRESHOLD;
        return yawn.update(yawning, nowMs, YAWN_DURATION_THRESHOLD_MS);
    }

    /** 눈 관측이 누락되면 진행 중인 눈 감김 판정을 초기화한다. */
    public void invalidateEyes() {
        eyes.reset();
    }

    /** 하품 관측이 누락되면 진행 중인 하품 판정을 초기화한다. */
    public void invalidateYawn() {
        yawn.reset();
    }

    /** 얼굴 미감지, 대상 변경, 추론 실패, 세션 변경 시 진행 중인 판정을 모두 초기화한다. */
    public void reset() {
        eyes.reset();
        yawn.reset();
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /** 하나의 지속 상태(눈 감김 또는 하품) 에피소드. */
    private static final class Episode {
        private boolean active = false;
        private boolean alerted = false;
        private long startMs = 0;
        private long lastObservationMs = 0;

        boolean update(boolean conditionMet, long nowMs, long thresholdMs) {
            if (!conditionMet) {
                reset();
                return false;
            }
            boolean continuous = active
                    && nowMs >= lastObservationMs
                    && nowMs - lastObservationMs <= MAX_OBSERVATION_GAP_MS;
            if (!continuous) {
                // 시작이거나 관측 공백이 길어 연속성이 깨졌으면 현재 관측부터 새로 잰다.
                active = true;
                alerted = false;
                startMs = nowMs;
                lastObservationMs = nowMs;
                return false;
            }
            lastObservationMs = nowMs;
            if (!alerted && nowMs - startMs >= thresholdMs) {
                alerted = true;
                return true;
            }
            return false;
        }

        void reset() {
            active = false;
            alerted = false;
            startMs = 0;
            lastObservationMs = 0;
        }
    }
}
