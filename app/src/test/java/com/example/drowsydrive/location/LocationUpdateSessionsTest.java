package com.example.drowsydrive.location;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 요청 A 시작 → 중단 → 요청 B 시작 순서에서 A의 늦은 콜백이 B에 영향을 주지 않는지 검증한다. */
public class LocationUpdateSessionsTest {

    @Test
    public void resultOfStoppedSessionIsIgnoredAfterRestart() {
        LocationUpdateSessions sessions = new LocationUpdateSessions();
        long a = sessions.start();
        sessions.stop();
        long b = sessions.start();
        assertNotEquals(a, b);
        assertFalse("A의 위치 결과는 전달하지 않는다", sessions.isCurrent(a));
        assertTrue("B의 위치 결과는 전달한다", sessions.isCurrent(b));
    }

    @Test
    public void lateFailureOfOldSessionDoesNotChangeNewSession() {
        LocationUpdateSessions sessions = new LocationUpdateSessions();
        long a = sessions.start();
        sessions.stop();
        long b = sessions.start();
        assertFalse(sessions.fail(a));
        assertTrue(sessions.isActive());
        assertTrue(sessions.isCurrent(b));
    }

    @Test
    public void failureOfCurrentSessionAllowsRestart() {
        LocationUpdateSessions sessions = new LocationUpdateSessions();
        long a = sessions.start();
        assertTrue(sessions.fail(a));
        assertFalse(sessions.isActive());
        assertFalse(sessions.isCurrent(a));
        long b = sessions.start();
        assertTrue(sessions.isCurrent(b));
    }

    @Test
    public void stopWithoutActiveSessionDoesNothing() {
        LocationUpdateSessions sessions = new LocationUpdateSessions();
        assertFalse(sessions.stop());
        long a = sessions.start();
        assertTrue(sessions.stop());
        assertFalse(sessions.stop());
        assertFalse(sessions.isCurrent(a));
    }

    @Test
    public void resultsAreBlockedAfterStopBeforeRestart() {
        LocationUpdateSessions sessions = new LocationUpdateSessions();
        long a = sessions.start();
        sessions.stop();
        assertFalse(sessions.isCurrent(a));
    }
}
