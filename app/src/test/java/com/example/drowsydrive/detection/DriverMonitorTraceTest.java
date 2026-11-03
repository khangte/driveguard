package com.example.drowsydrive.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.drowsydrive.detection.DriverMonitor.FaceObservation;
import com.example.drowsydrive.detection.DrowsinessDetector.Outcome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 측정 로그용 trace가 판정 결과를 바꾸지 않고 실제 내부 상태와 초기화 사유를 남기는지 확인한다. */
public class DriverMonitorTraceTest {

    private static FaceObservation closed(Integer id, float area) {
        return new FaceObservation(id, area, 0.1f, 0.1f, 0.9f, 100f, 95f, 90f);
    }

    private static FaceObservation open(Integer id, float area) {
        return new FaceObservation(id, area, 0.9f, 0.9f, 0.9f, 100f, 95f, 90f);
    }

    private static FaceObservation yawning(Integer id) {
        return new FaceObservation(id, 100, 0.9f, 0.9f, 0.1f, 100f, 91f, 90f);
    }

    private static FaceObservation missingEyes(Integer id) {
        return new FaceObservation(id, 100, null, 0.1f, 0.9f, 100f, 95f, 90f);
    }

    private static FaceObservation nanEyes(Integer id) {
        return new FaceObservation(id, 100, Float.NaN, 0.1f, 0.9f, 100f, 95f, 90f);
    }

    private static final class Frame {
        final long t;
        final List<FaceObservation> faces;
        final boolean failure;

        Frame(long t, List<FaceObservation> faces, boolean failure) {
            this.t = t;
            this.faces = faces;
            this.failure = failure;
        }
    }

    /** 감김·공백·누락·비유한 값·대상 변경·미감지·실패·하품이 섞인 대표 관측 시퀀스. */
    private static List<Frame> scenario() {
        List<Frame> frames = new ArrayList<>();
        long t = 0;
        for (int i = 0; i < 6; i++, t += 250) frames.add(new Frame(t, Arrays.asList(closed(1, 100)), false));
        t += 1500; // 허용 관측 공백 초과
        for (int i = 0; i < 6; i++, t += 250) frames.add(new Frame(t, Arrays.asList(closed(1, 100)), false));
        frames.add(new Frame(t += 250, Arrays.asList(missingEyes(1)), false));
        frames.add(new Frame(t += 250, Arrays.asList(nanEyes(1)), false));
        for (int i = 0; i < 6; i++) frames.add(new Frame(t += 250, Arrays.asList(closed(1, 100)), false));
        frames.add(new Frame(t += 250, Collections.<FaceObservation>emptyList(), false));
        for (int i = 0; i < 3; i++) frames.add(new Frame(t += 250, Arrays.asList(closed(2, 100)), false));
        frames.add(new Frame(t += 250, null, true));
        for (int i = 0; i < 6; i++) frames.add(new Frame(t += 250, Arrays.asList(open(3, 500), closed(2, 100)), false));
        for (int i = 0; i < 12; i++) frames.add(new Frame(t += 250, Arrays.asList(yawning(2)), false));
        return frames;
    }

    @Test
    public void alertsAreIdenticalWithAndWithoutTrace() {
        DriverMonitor plain = new DriverMonitor();
        DriverMonitor traced = new DriverMonitor();
        long s1 = plain.startSession();
        long s2 = traced.startSession();
        int alertCount = 0;
        for (Frame f : scenario()) {
            if (f.failure) {
                plain.onFailure(s1);
                traced.onFailure(s2, new DetectionTrace());
                continue;
            }
            int a = plain.onFaces(s1, f.faces, f.t);
            int b = traced.onFaces(s2, f.faces, f.t, new DetectionTrace());
            assertEquals("t=" + f.t, a, b);
            if (a != DriverMonitor.ALERT_NONE) alertCount++;
        }
        assertTrue("대표 시퀀스에서 경고가 발생해야 비교 의미가 있음", alertCount >= 3);
    }

    @Test
    public void traceRecordsEpisodeStateAndResetReasons() {
        DriverMonitor m = new DriverMonitor();
        long session = m.startSession();

        DetectionTrace first = new DetectionTrace();
        m.onFaces(session, Arrays.asList(closed(1, 100)), 0, first);
        assertTrue(first.applied);
        assertEquals(DetectionTrace.TARGET_NEW, first.targetReason);
        assertEquals(1, first.resets.size());
        assertEquals(DetectionTrace.RESET_SESSION_CHANGED, first.resets.get(0).reason);
        assertFalse("처음부터 비활성인 초기화", first.resets.get(0).eyesWasActive);
        assertEquals(Outcome.STARTED, first.eyes.outcome);
        assertEquals(Boolean.TRUE, first.eyes.conditionMet());
        assertEquals(Long.valueOf(0), first.eyes.elapsedMs); // 시작(0)과 미계산(null)을 구분
        assertEquals(Outcome.CONDITION_NOT_MET, first.yawn.outcome);
        assertNull(first.yawn.elapsedMs);
        assertTrue(first.yawn.ratio > 0);

        DetectionTrace alert = null;
        for (long t = 250; t <= 1000; t += 250) {
            alert = new DetectionTrace();
            m.onFaces(session, Arrays.asList(closed(1, 100)), t, alert);
        }
        assertTrue(alert.eyes.alertFired);
        assertEquals(Long.valueOf(1000), alert.eyes.elapsedMs);
        assertEquals(DetectionTrace.TARGET_KEPT_TRACKING_ID, alert.targetReason);

        DetectionTrace gap = new DetectionTrace();
        m.onFaces(session, Arrays.asList(closed(1, 100)), 2500, gap);
        assertEquals(Outcome.GAP_RESTARTED, gap.eyes.outcome);
        assertTrue(gap.eyes.activeBefore);

        DetectionTrace opened = new DetectionTrace();
        m.onFaces(session, Arrays.asList(open(1, 100)), 2750, opened);
        assertEquals(Outcome.CONDITION_CLEARED, opened.eyes.outcome);
        assertEquals(Boolean.FALSE, opened.eyes.conditionMet());

        m.onFaces(session, Arrays.asList(closed(1, 100)), 3000, new DetectionTrace());
        DetectionTrace missing = new DetectionTrace();
        m.onFaces(session, Arrays.asList(missingEyes(1)), 3250, missing);
        assertEquals(DetectionTrace.INPUT_MISSING, missing.eyes.input);
        assertEquals(Outcome.INVALIDATED, missing.eyes.outcome);
        assertTrue(missing.eyes.activeBefore);
        assertNull(missing.eyes.conditionMet());

        DetectionTrace invalid = new DetectionTrace();
        m.onFaces(session, Arrays.asList(nanEyes(1)), 3500, invalid);
        assertEquals(DetectionTrace.INPUT_PRESENT, invalid.eyes.input);
        assertEquals(Outcome.INVALID_INPUT, invalid.eyes.outcome);

        m.onFaces(session, Arrays.asList(closed(1, 100)), 3750, new DetectionTrace());
        DetectionTrace changed = new DetectionTrace();
        m.onFaces(session, Arrays.asList(closed(2, 100)), 4000, changed);
        assertEquals(DetectionTrace.TARGET_CHANGED, changed.targetReason);
        assertEquals(DetectionTrace.RESET_TARGET_CHANGED, changed.resets.get(0).reason);
        assertTrue(changed.resets.get(0).eyesWasActive);

        DetectionTrace none = new DetectionTrace();
        m.onFaces(session, Collections.<FaceObservation>emptyList(), 4250, none);
        assertEquals(-1, none.selectedIndex);
        assertEquals(DetectionTrace.RESET_NO_FACE, none.resets.get(0).reason);
        assertTrue(none.resets.get(0).eyesWasActive);
        assertEquals(DetectionTrace.INPUT_NO_FACE, none.eyes.input);
        assertNull(none.eyes.conditionMet());

        m.onFaces(session, Arrays.asList(closed(2, 100)), 4500, new DetectionTrace());
        DetectionTrace failure = new DetectionTrace();
        m.onFailure(session, failure);
        assertEquals(DetectionTrace.RESET_ANALYSIS_FAILURE, failure.resets.get(0).reason);
        assertTrue(failure.resets.get(0).eyesWasActive);
    }

    @Test
    public void staleSessionResultIsNotAppliedOrTraced() {
        DriverMonitor m = new DriverMonitor();
        long old = m.startSession();
        m.startSession();
        DetectionTrace trace = new DetectionTrace();
        assertEquals(DriverMonitor.ALERT_NONE, m.onFaces(old, Arrays.asList(closed(1, 100)), 0, trace));
        assertFalse(trace.applied);
        assertTrue(trace.resets.isEmpty());
    }
}
