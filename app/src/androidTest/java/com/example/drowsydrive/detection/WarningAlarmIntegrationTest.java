package com.example.drowsydrive.detection;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.drowsydrive.alert.WarningAlarmController;
import com.example.drowsydrive.alert.WarningAlarmController.AlertType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class WarningAlarmIntegrationTest {
    private static final class RecordingUi implements WarningAlarmController.Ui {
        final List<AlertType> alerts = new ArrayList<>();
        final List<Runnable> stops = new ArrayList<>();
        int guides;

        @Override public void showAlarmDialog(AlertType type, Runnable stop) {
            alerts.add(type);
            stops.add(stop);
        }

        @Override public void showGuideDialog() { guides++; }
    }

    @Test
    public void oneShotDrowsinessReplacesYawnAndOldStopCannotDismissIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecordingUi ui = new RecordingUi();
            WarningAlarmController alarms = new WarningAlarmController(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), ui);
            try {
                alarms.playWarningAlarm(AlertType.YAWN);
                DriverMonitor monitor = new DriverMonitor();
                long session = monitor.startSession();
                DriverMonitor.FaceObservation face = new DriverMonitor.FaceObservation(
                        1, 100, 0.1f, 0.1f, 0.9f, 100f, 95f, 90f);
                int signals = 0;
                for (long time = 0; time <= 2000; time += 250) {
                    if ((monitor.onFaces(session, Collections.singletonList(face), time)
                            & DriverMonitor.ALERT_EYES_CLOSED) != 0) {
                        signals++;
                        alarms.playWarningAlarm(AlertType.DROWSINESS);
                    }
                }
                assertEquals(1, signals);
                assertEquals(Arrays.asList(AlertType.YAWN, AlertType.DROWSINESS), ui.alerts);
                ui.stops.get(0).run(); // 교체 전 다이얼로그에서 늦게 도착한 종료 콜백
                assertEquals(0, ui.guides);
                ui.stops.get(1).run();
                assertEquals(1, ui.guides);
                ui.stops.get(1).run(); // 중복 종료도 무시한다.
                assertEquals(1, ui.guides);
            } finally {
                alarms.release();
            }
        });
    }

    @Test
    public void queuedAlarmKeepsFirstOriginThroughDuplicateRequestsAndStop() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecordingUi ui = new RecordingUi();
            WarningAlarmController alarms = new WarningAlarmController(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), ui);
            List<WarningAlarmController.AlarmTrace> queued = new ArrayList<>();
            List<WarningAlarmController.AlarmTrace> shown = new ArrayList<>();
            List<WarningAlarmController.AlarmTrace> stopped = new ArrayList<>();
            List<String> related = new ArrayList<>();
            alarms.setEventListener((event, type, active, trace, relatedId) -> {
                if ("queued_pending".equals(event)) { queued.add(trace); related.add(relatedId); }
                if ("dialog_posted".equals(event)) shown.add(trace);
                if ("stopped_by_user".equals(event)) stopped.add(trace);
            });
            try {
                alarms.playWarningAlarm(AlertType.DROWSINESS, "session_a", 1L);
                alarms.playWarningAlarm(AlertType.YAWN, "session_a", 2L);
                alarms.playWarningAlarm(AlertType.YAWN, "session_a", 3L);
                alarms.playWarningAlarm(AlertType.CO2);
                assertEquals(queued.get(0).alarmId, related.get(1));
                ui.stops.get(0).run(); // CO2 먼저
                ui.stops.get(1).run(); // 그 다음 최초 YAWN 요청
                assertEquals(Arrays.asList(AlertType.DROWSINESS, AlertType.CO2, AlertType.YAWN), ui.alerts);
                assertEquals(queued.get(0).alarmId, shown.get(2).alarmId);
                assertEquals("session_a", shown.get(2).sourceSessionId);
                assertEquals(Long.valueOf(2), shown.get(2).sourceObservationId);
                ui.stops.get(2).run();
                assertEquals(shown.get(2).alarmId, stopped.get(2).alarmId);
            } finally {
                alarms.release();
            }
        });
    }

    @Test
    public void overlappingLowerPriorityAlertsAreDeliveredAfterCurrentAlarm() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecordingUi ui = new RecordingUi();
            WarningAlarmController alarms = new WarningAlarmController(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), ui);
            Runnable lastStop;
            try {
                alarms.playWarningAlarm(AlertType.DROWSINESS);
                alarms.playWarningAlarm(AlertType.YAWN);
                alarms.playWarningAlarm(AlertType.YAWN);
                alarms.playWarningAlarm(AlertType.CO2);
                assertEquals(Collections.singletonList(AlertType.DROWSINESS), ui.alerts);
                ui.stops.get(0).run();
                assertEquals(Arrays.asList(AlertType.DROWSINESS, AlertType.CO2), ui.alerts);
                ui.stops.get(0).run(); // 이전 종료가 보류 경고를 건드리지 않는다.
                assertEquals(2, ui.alerts.size());
                ui.stops.get(1).run();
                assertEquals(Arrays.asList(AlertType.DROWSINESS, AlertType.CO2, AlertType.YAWN),
                        ui.alerts);
                lastStop = ui.stops.get(2);
            } finally {
                alarms.release();
            }
            lastStop.run(); // 해제 후 콜백도 무시한다.
            assertEquals(1, ui.guides);
        });
    }
}
