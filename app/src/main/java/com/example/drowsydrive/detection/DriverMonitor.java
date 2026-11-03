package com.example.drowsydrive.detection;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 프레임별 얼굴 관측에서 운전자 후보 1명을 고르고, 분석 세션을 구분해 DrowsinessDetector에 전달한다.
 * ML Kit/Android에 의존하지 않는 순수 Java 로직이다.
 *
 * <p>스레드 규칙: {@link #startSession()}, {@link #stopSession()}, {@link #isCurrent(long)}은
 * 어느 스레드에서나 호출할 수 있다. {@link #onFaces}와 {@link #onFailure}는 분석 실행기 스레드
 * 하나에서만 호출해야 한다(오래된 세션 토큰이면 상태를 건드리지 않고 바로 반환한다).
 *
 * <p>운전자 선택 규칙: 이전에 고른 대상의 tracking ID가 이번 프레임에 있으면 그 얼굴을 유지한다.
 * 없으면 바운딩 박스 면적이 가장 큰 얼굴을 새 대상으로 고르고 판정을 초기화한다.
 * tracking ID가 없는 경우에는 이전/현재 프레임 모두 얼굴이 하나뿐일 때만 같은 대상으로 본다.
 * 가장 큰 얼굴이 실제 운전자라는 보장은 없다(예: 뒷좌석 승객이 카메라에 더 가까운 경우).
 */
final class DriverMonitor {
    static final int ALERT_NONE = 0;
    static final int ALERT_EYES_CLOSED = 1;
    static final int ALERT_YAWN = 1 << 1;

    private static final long NO_SESSION = 0;

    /** 프레임 하나에서 얻은 얼굴 관측값. 누락된 값은 null이다. */
    static final class FaceObservation {
        final Integer trackingId;
        final float area;
        final Float leftEyeOpenProbability;
        final Float rightEyeOpenProbability;
        final Float smilingProbability;
        final Float mouthBottomY;
        final Float noseBaseY;
        final Float rightEyeY;

        FaceObservation(Integer trackingId, float area,
                        Float leftEyeOpenProbability, Float rightEyeOpenProbability,
                        Float smilingProbability, Float mouthBottomY, Float noseBaseY, Float rightEyeY) {
            this.trackingId = trackingId;
            this.area = area;
            this.leftEyeOpenProbability = leftEyeOpenProbability;
            this.rightEyeOpenProbability = rightEyeOpenProbability;
            this.smilingProbability = smilingProbability;
            this.mouthBottomY = mouthBottomY;
            this.noseBaseY = noseBaseY;
            this.rightEyeY = rightEyeY;
        }
    }

    private final AtomicLong sessionSequence = new AtomicLong(NO_SESSION);
    private final AtomicLong currentSession = new AtomicLong(NO_SESSION);

    // 아래 상태는 분석 실행기 스레드에서만 접근한다.
    private final DrowsinessDetector detector = new DrowsinessDetector();
    private long appliedSession = NO_SESSION;
    private boolean hasTarget = false;
    private Integer targetTrackingId = null;
    private int lastFaceCount = 0;

    /** 새 분석 세션을 시작하고 토큰을 반환한다. 이전 세션의 결과는 이후 무시된다. */
    long startSession() {
        long session = sessionSequence.incrementAndGet();
        currentSession.set(session);
        return session;
    }

    /** 분석 세션을 끝낸다. 진행 중이던 비동기 결과는 무시된다. */
    void stopSession() {
        currentSession.set(NO_SESSION);
    }

    /** 현재 분석 중이면 세션 토큰을, 아니면 0을 반환한다. */
    long currentSession() {
        return currentSession.get();
    }

    boolean isCurrent(long session) {
        return session != NO_SESSION && session == currentSession.get();
    }

    /**
     * 한 프레임의 얼굴 목록을 처리하고 발생한 경고를 비트 플래그로 반환한다.
     *
     * @param frameTimeMs 프레임 분석을 시작한 시각(단조 증가 시계)
     */
    int onFaces(long session, List<FaceObservation> faces, long frameTimeMs) {
        return onFaces(session, faces, frameTimeMs, null);
    }

    /**
     * {@link #onFaces(long, List, long)}와 같고, trace가 null이 아니면 실제 판정 과정을 기록한다.
     * trace는 판정 결과에 영향을 주지 않는다.
     */
    int onFaces(long session, List<FaceObservation> faces, long frameTimeMs, DetectionTrace trace) {
        if (!enterSession(session, trace)) return ALERT_NONE;
        if (trace != null) trace.applied = true;
        if (faces.isEmpty()) {
            recordReset(trace, DetectionTrace.RESET_NO_FACE);
            clearTarget();
            if (trace != null) {
                trace.eyes.capture(DetectionTrace.INPUT_NO_FACE, false, detector.eyesSnapshot(), false);
                trace.yawn.capture(DetectionTrace.INPUT_NO_FACE, false, detector.yawnSnapshot(), false);
                trace.eyes.outcome = null;
                trace.yawn.outcome = null;
            }
            return ALERT_NONE;
        }

        int selected = selectDriver(faces, trace);
        if (trace != null) trace.selectedIndex = selected;
        FaceObservation driver = faces.get(selected);
        int alerts = ALERT_NONE;
        boolean eyesActiveBefore = detector.isEyesEpisodeActive();
        boolean eyesAlert = false;
        boolean eyesPresent = driver.leftEyeOpenProbability != null && driver.rightEyeOpenProbability != null;
        if (eyesPresent) {
            eyesAlert = detector.updateEyes(driver.leftEyeOpenProbability, driver.rightEyeOpenProbability,
                    frameTimeMs);
            if (eyesAlert) alerts |= ALERT_EYES_CLOSED;
        } else {
            detector.invalidateEyes();
        }
        if (trace != null) {
            trace.eyes.capture(eyesPresent ? DetectionTrace.INPUT_PRESENT : DetectionTrace.INPUT_MISSING,
                    eyesActiveBefore, detector.eyesSnapshot(), eyesAlert);
        }

        boolean yawnActiveBefore = detector.isYawnEpisodeActive();
        boolean yawnAlert = false;
        boolean yawnPresent = driver.smilingProbability != null && driver.mouthBottomY != null
                && driver.noseBaseY != null && driver.rightEyeY != null;
        if (yawnPresent) {
            yawnAlert = detector.updateYawn(driver.smilingProbability, driver.mouthBottomY, driver.noseBaseY,
                    driver.rightEyeY, frameTimeMs);
            if (yawnAlert) alerts |= ALERT_YAWN;
        } else {
            detector.invalidateYawn();
        }
        if (trace != null) {
            trace.yawn.capture(yawnPresent ? DetectionTrace.INPUT_PRESENT : DetectionTrace.INPUT_MISSING,
                    yawnActiveBefore, detector.yawnSnapshot(), yawnAlert);
            trace.yawn.ratio = detector.lastYawnRatio();
        }
        return alerts;
    }

    /** 사용자 중지 알림도 분석 스레드에서 처리한다. 이전 세션의 알림은 상태를 바꾸지 않는다. */
    boolean onEyesAlarmStopped(long session, long stoppedAtMs) {
        return onEyesAlarmStopped(session, stoppedAtMs, stoppedAtMs);
    }

    boolean onEyesAlarmStopped(long session, long stoppedAtMs, long appliedAtMs) {
        return isCurrent(session) && appliedSession == session
                && detector.onEyesAlarmStopped(stoppedAtMs, appliedAtMs);
    }

    /** 추론 실패/예외로 관측을 얻지 못했으면 진행 중인 판정을 초기화한다. */
    void onFailure(long session) {
        onFailure(session, null);
    }

    void onFailure(long session, DetectionTrace trace) {
        if (!enterSession(session, trace)) return;
        if (trace != null) trace.applied = true;
        recordReset(trace, DetectionTrace.RESET_ANALYSIS_FAILURE);
        detector.reset();
    }

    /** 현재 세션의 결과인지 확인하고, 세션이 바뀌었으면 이전 세션의 판정/대상을 버린다. */
    private boolean enterSession(long session, DetectionTrace trace) {
        if (!isCurrent(session)) return false;
        if (appliedSession != session) {
            appliedSession = session;
            recordReset(trace, DetectionTrace.RESET_SESSION_CHANGED);
            clearTarget();
        }
        return true;
    }

    private void recordReset(DetectionTrace trace, String reason) {
        if (trace == null) return;
        trace.resets.add(new DetectionTrace.Reset(reason,
                detector.isEyesEpisodeActive(), detector.isYawnEpisodeActive()));
    }

    private int selectDriver(List<FaceObservation> faces, DetectionTrace trace) {
        int selected = -1;
        if (hasTarget && targetTrackingId != null) {
            for (int i = 0; i < faces.size(); i++) {
                if (targetTrackingId.equals(faces.get(i).trackingId)) {
                    selected = i;
                    break;
                }
            }
        }
        boolean sameTarget = selected >= 0;
        String reason = DetectionTrace.TARGET_KEPT_TRACKING_ID;
        if (!sameTarget) {
            selected = largestFace(faces);
            sameTarget = hasTarget && targetTrackingId == null
                    && faces.get(selected).trackingId == null
                    && lastFaceCount == 1 && faces.size() == 1;
            reason = DetectionTrace.TARGET_KEPT_SINGLE_UNTRACKED;
        }
        if (!sameTarget) {
            reason = hasTarget ? DetectionTrace.TARGET_CHANGED : DetectionTrace.TARGET_NEW;
            if (hasTarget) recordReset(trace, DetectionTrace.RESET_TARGET_CHANGED);
            detector.reset(); // 대상이 바뀌었거나 같은 대상인지 확인할 수 없다.
        }
        if (trace != null) trace.targetReason = reason;
        hasTarget = true;
        targetTrackingId = faces.get(selected).trackingId;
        lastFaceCount = faces.size();
        return selected;
    }

    private static int largestFace(List<FaceObservation> faces) {
        int largest = 0;
        for (int i = 1; i < faces.size(); i++) {
            if (faces.get(i).area > faces.get(largest).area) largest = i;
        }
        return largest;
    }

    private void clearTarget() {
        hasTarget = false;
        targetTrackingId = null;
        lastFaceCount = 0;
        detector.reset();
    }
}
