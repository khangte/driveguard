package com.example.drowsydrive.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public class DriverMonitorRealertTest {
    private static int closed(DriverMonitor m, long session, int id, long t) {
        return m.onFaces(session, Collections.singletonList(new DriverMonitor.FaceObservation(
                id, 100, 0.1f, 0.1f, 0.9f, 100f, 95f, 90f)), t);
    }

    @Test
    public void stopRearmsCurrentSessionAndReplayRemainsTraceable() {
        DriverMonitor m = new DriverMonitor();
        long s = m.startSession();
        for (long t = 0; t < 1000; t += 250) assertEquals(0, closed(m, s, 1, t));
        assertEquals(DriverMonitor.ALERT_EYES_CLOSED, closed(m, s, 1, 1000));
        assertTrue(m.onEyesAlarmStopped(s, 1250));
        for (long t = 1250; t < 4250; t += 250) assertEquals(0, closed(m, s, 1, t));
        DetectionTrace trace = new DetectionTrace();
        int alerts = m.onFaces(s, Collections.singletonList(new DriverMonitor.FaceObservation(
                1, 100, 0.1f, 0.1f, 0.9f, 100f, 95f, 90f)), 4250, trace);
        assertEquals(DriverMonitor.ALERT_EYES_CLOSED, alerts);
        assertTrue(trace.eyes.alertFired);
        assertEquals(Long.valueOf(4250), trace.eyes.elapsedMs);
    }

    @Test
    public void targetChangeCancelsOldReplay() {
        DriverMonitor m = new DriverMonitor();
        long s = m.startSession();
        for (long t = 0; t <= 1000; t += 250) closed(m, s, 1, t);
        assertTrue(m.onEyesAlarmStopped(s, 1250));
        for (long t = 1500; t < 2500; t += 250) assertEquals(0, closed(m, s, 2, t));
        assertEquals(DriverMonitor.ALERT_EYES_CLOSED, closed(m, s, 2, 2500));
        for (long t = 2750; t <= 4500; t += 250) assertEquals(0, closed(m, s, 2, t));
    }

    @Test
    public void faceLossOrInferenceFailureCancelsReplay() {
        for (boolean failure : new boolean[]{false, true}) {
            DriverMonitor m = new DriverMonitor();
            long s = m.startSession();
            for (long t = 0; t <= 1000; t += 250) closed(m, s, 1, t);
            assertTrue(m.onEyesAlarmStopped(s, 1250));
            if (failure) m.onFailure(s);
            else m.onFaces(s, Collections.emptyList(), 1250);
            for (long t = 1500; t < 2500; t += 250) assertEquals(0, closed(m, s, 1, t));
            assertEquals(DriverMonitor.ALERT_EYES_CLOSED, closed(m, s, 1, 2500));
            for (long t = 2750; t <= 4500; t += 250) assertEquals(0, closed(m, s, 1, t));
        }
    }

    @Test
    public void oldOrUnappliedSessionCannotScheduleReplay() {
        DriverMonitor m = new DriverMonitor();
        long old = m.startSession();
        for (long t = 0; t <= 1000; t += 250) closed(m, old, 1, t);
        m.stopSession();
        assertFalse(m.onEyesAlarmStopped(old, 1250));
        long current = m.startSession();
        assertFalse(m.onEyesAlarmStopped(current, 1250));
        assertFalse(m.onEyesAlarmStopped(old, 1250));
        for (long t = 1500; t < 2500; t += 250) assertEquals(0, closed(m, current, 1, t));
        assertEquals(DriverMonitor.ALERT_EYES_CLOSED, closed(m, current, 1, 2500));
        assertFalse(m.onEyesAlarmStopped(old, 2750));
        for (long t = 2750; t <= 6000; t += 250) assertEquals(0, closed(m, current, 1, t));
    }
}
