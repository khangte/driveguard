package com.example.drowsydrive.detection;

import static com.example.drowsydrive.detection.DriverMonitor.ALERT_EYES_CLOSED;
import static com.example.drowsydrive.detection.DriverMonitor.ALERT_NONE;
import static com.example.drowsydrive.detection.DriverMonitor.ALERT_YAWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.drowsydrive.detection.DriverMonitor.FaceObservation;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DriverMonitorTest {

    private static final long STEP = 250;

    private static FaceObservation closedEyes(Integer id, float area) {
        return new FaceObservation(id, area, 0.1f, 0.1f, 0.9f, 100f, 95f, 90f);
    }

    private static FaceObservation openEyes(Integer id, float area) {
        return new FaceObservation(id, area, 0.9f, 0.9f, 0.9f, 100f, 95f, 90f);
    }

    private static FaceObservation yawning(Integer id, float area) {
        return new FaceObservation(id, area, 0.9f, 0.9f, 0.1f, 100f, 91f, 90f);
    }

    private static List<FaceObservation> faces(FaceObservation... faces) {
        return Arrays.asList(faces);
    }

    /** from부터 to 직전까지 같은 프레임을 반복 처리하며 경고가 없었는지 확인한다. */
    private static void noAlert(DriverMonitor m, long session, List<FaceObservation> frame,
                                long from, long to) {
        for (long t = from; t < to; t += STEP) {
            assertEquals("t=" + t, ALERT_NONE, m.onFaces(session, frame, t));
        }
    }

    @Test
    public void singleFace_alertsAfterThresholdOnce() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        List<FaceObservation> frame = faces(closedEyes(1, 100));
        noAlert(monitor, session, frame, 0, 1000);
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(session, frame, 1000));
        noAlert(monitor, session, frame, 1250, 3000);
    }

    @Test
    public void yawn_alertsForSelectedFace() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        List<FaceObservation> frame = faces(yawning(1, 100));
        noAlert(monitor, session, frame, 0, 2500);
        assertEquals(ALERT_YAWN, monitor.onFaces(session, frame, 2500));
    }

    @Test
    public void multipleFaces_doNotShareState() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        // 큰 얼굴(운전자 후보)은 눈을 뜨고, 작은 얼굴만 눈을 감는다. 예전에는 상태를 공유해 경고될 수 있었다.
        List<FaceObservation> frame = faces(closedEyes(2, 50), openEyes(1, 100));
        noAlert(monitor, session, frame, 0, 3000);
    }

    @Test
    public void multipleFaces_interleavedObservationsDoNotAccumulate() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        List<FaceObservation> frame = faces(openEyes(2, 50), closedEyes(1, 100));
        noAlert(monitor, session, frame, 0, 1000);
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(session, frame, 1000));
    }

    @Test
    public void trackedTarget_isKeptEvenWhenAnotherFaceBecomesLarger() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        monitor.onFaces(session, faces(closedEyes(1, 100)), 0);
        // 다른 얼굴이 더 커져도 tracking ID 1을 계속 추적해 감김 시간이 이어진다.
        List<FaceObservation> frame = faces(openEyes(2, 500), closedEyes(1, 100));
        noAlert(monitor, session, frame, 250, 1000);
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(session, frame, 1000));
    }

    @Test
    public void targetChange_resetsDetection() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        noAlert(monitor, session, faces(closedEyes(1, 100)), 0, 1000);
        // 대상 1이 사라지고 다른 얼굴 2가 대상이 되면 이전 감김 시간을 이어받지 않는다.
        List<FaceObservation> other = faces(closedEyes(2, 100));
        noAlert(monitor, session, other, 1000, 2000);
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(session, other, 2000));
    }

    @Test
    public void targetLost_resetsDetection() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        List<FaceObservation> frame = faces(closedEyes(1, 100));
        noAlert(monitor, session, frame, 0, 1000);
        assertEquals(ALERT_NONE, monitor.onFaces(session, Collections.<FaceObservation>emptyList(), 1000));
        noAlert(monitor, session, frame, 1250, 2250);
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(session, frame, 2250));
    }

    @Test
    public void withoutTrackingId_singleFaceIsTreatedAsSameTarget() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        List<FaceObservation> frame = faces(closedEyes(null, 100));
        noAlert(monitor, session, frame, 0, 1000);
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(session, frame, 1000));
    }

    @Test
    public void withoutTrackingId_multipleFacesCannotConfirmTarget() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        // tracking ID 없이 여러 얼굴이면 같은 대상인지 알 수 없어 매번 초기화한다.
        List<FaceObservation> frame = faces(closedEyes(null, 100), openEyes(null, 50));
        noAlert(monitor, session, frame, 0, 3000);
    }

    @Test
    public void missingEyeObservation_isNotCounted() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        List<FaceObservation> closed = faces(closedEyes(1, 100));
        noAlert(monitor, session, closed, 0, 1000);
        FaceObservation missingEyes = new FaceObservation(1, 100, null, 0.1f, 0.9f, 100f, 95f, 90f);
        assertEquals(ALERT_NONE, monitor.onFaces(session, faces(missingEyes), 1000));
        noAlert(monitor, session, closed, 1250, 2250);
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(session, closed, 2250));
    }

    @Test
    public void missingYawnLandmark_isNotCounted() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        List<FaceObservation> yawn = faces(yawning(1, 100));
        noAlert(monitor, session, yawn, 0, 2500);
        FaceObservation missingNose = new FaceObservation(1, 100, 0.9f, 0.9f, 0.1f, 100f, null, 90f);
        assertEquals(ALERT_NONE, monitor.onFaces(session, faces(missingNose), 2500));
        noAlert(monitor, session, yawn, 2750, 5250);
        assertEquals(ALERT_YAWN, monitor.onFaces(session, yawn, 5250));
    }

    @Test
    public void inferenceFailure_isNotCounted() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        List<FaceObservation> frame = faces(closedEyes(1, 100));
        noAlert(monitor, session, frame, 0, 1000);
        monitor.onFailure(session);
        noAlert(monitor, session, frame, 1000, 2000);
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(session, frame, 2000));
    }

    @Test
    public void observationGap_isNotCounted() {
        DriverMonitor monitor = new DriverMonitor();
        long session = monitor.startSession();
        List<FaceObservation> frame = faces(closedEyes(1, 100));
        assertEquals(ALERT_NONE, monitor.onFaces(session, frame, 0));
        assertEquals(ALERT_NONE, monitor.onFaces(session, frame, 1500)); // 공백 1500ms
        assertEquals(ALERT_NONE, monitor.onFaces(session, frame, 2000));
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(session, frame, 2500));
    }

    @Test
    public void stopAndResume_resetsDetection() {
        DriverMonitor monitor = new DriverMonitor();
        long first = monitor.startSession();
        List<FaceObservation> frame = faces(closedEyes(1, 100));
        noAlert(monitor, first, frame, 0, 1000);
        monitor.stopSession();
        long second = monitor.startSession();
        // 중단 직후 바로 재개해 관측 간격이 짧아도 이전 세션의 감김 시간을 이어받지 않는다.
        noAlert(monitor, second, frame, 1000, 2000);
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(second, frame, 2000));
    }

    @Test
    public void staleResult_isIgnoredAndDoesNotAffectNewSession() {
        DriverMonitor monitor = new DriverMonitor();
        long first = monitor.startSession();
        List<FaceObservation> frame = faces(closedEyes(1, 100));
        noAlert(monitor, first, frame, 0, 750);
        monitor.stopSession();
        assertFalse(monitor.isCurrent(first));
        // 중단 전에 시작된 비동기 결과가 늦게 도착해도 무시된다.
        assertEquals(ALERT_NONE, monitor.onFaces(first, frame, 1000));
        monitor.onFailure(first);

        long second = monitor.startSession();
        assertTrue(monitor.isCurrent(second));
        assertFalse(monitor.isCurrent(first));
        // 재개 후에 도착한 이전 세션 결과도 무시되고 새 세션 상태에 섞이지 않는다.
        assertEquals(ALERT_NONE, monitor.onFaces(first, frame, 1250));
        noAlert(monitor, second, frame, 1250, 2250);
        assertEquals(ALERT_EYES_CLOSED, monitor.onFaces(second, frame, 2250));
    }

    @Test
    public void staleSessionToken_afterStopIsNotCurrent() {
        DriverMonitor monitor = new DriverMonitor();
        assertEquals(0, monitor.currentSession());
        assertFalse(monitor.isCurrent(0));
        long session = monitor.startSession();
        assertEquals(session, monitor.currentSession());
        monitor.stopSession();
        assertEquals(0, monitor.currentSession());
        assertFalse(monitor.isCurrent(session));
    }
}
