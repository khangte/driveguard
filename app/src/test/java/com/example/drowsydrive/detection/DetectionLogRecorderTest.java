package com.example.drowsydrive.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class DetectionLogRecorderTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final AtomicLong elapsed = new AtomicLong(10_000);
    private final List<DetectionLogRecorder.Status> statuses =
            Collections.synchronizedList(new ArrayList<>());

    private final DetectionLogRecorder.Clock clock = new DetectionLogRecorder.Clock() {
        @Override public long elapsedMs() { return elapsed.get(); }
        @Override public long wallMs() { return 1_700_000_000_000L; }
    };

    private static final Executor NEW_THREAD = r -> new Thread(r, "test-writer").start();

    private DetectionLogRecorder recorder(File dir, long maxPart, long maxTotal, int capacity, Executor starter) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("detection_config", FaceDetectionManager.detectionConfig());
        return new DetectionLogRecorder(dir, env, maxPart, maxTotal, capacity, clock, Runnable::run,
                starter, statuses::add);
    }

    private DetectionLogRecorder recorder(File dir) {
        return recorder(dir, DetectionLogRecorder.MAX_PART_BYTES, DetectionLogRecorder.MAX_TOTAL_BYTES,
                DetectionLogRecorder.QUEUE_CAPACITY, NEW_THREAD);
    }

    private static Map<String, Object> observation(long id) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("observation_id", id);
        f.put("face_count", 0);
        f.put("faces", new ArrayList<>());
        f.put("padding", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        return f;
    }

    private static List<File> logFiles(File dir) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".jsonl"));
        List<File> list = new ArrayList<>(files == null ? Collections.<File>emptyList() : Arrays.asList(files));
        Collections.sort(list);
        return list;
    }

    /** 모든 줄이 개행으로 끝나는 완전한 JSON인지 확인하고 파싱한다. */
    private static List<Map<String, Object>> readRecords(File file) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue("마지막 줄도 개행으로 끝나야 함: " + file, text.isEmpty() || text.endsWith("\n"));
        List<Map<String, Object>> records = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) continue;
            records.add(MiniJson.object(line));
        }
        return records;
    }

    private static void finish(DetectionLogRecorder r) throws InterruptedException {
        r.release();
        assertTrue("작업자 스레드가 끝나야 함", r.awaitTermination(5000));
    }

    @Test
    public void writesHeaderObservationsEventsAndFooterAsValidJson() throws Exception {
        File dir = new File(temp.getRoot(), DetectionLogRecorder.DIRECTORY_NAME);
        DetectionLogRecorder r = recorder(dir);
        DetectionLogRecorder.Session s = r.start("eyes_closed_1s");
        assertNotNull(s);

        Map<String, Object> obs = new LinkedHashMap<>();
        obs.put("observation_id", s.nextObservationId());
        obs.put("face_count", 0);
        obs.put("faces", new ArrayList<>());
        obs.put("missing", null);
        obs.put("nan", Float.NaN);
        obs.put("inf", Double.POSITIVE_INFINITY);
        obs.put("prob", 0.15f);
        obs.put("text", "따옴표\" 역슬래시\\ 줄바꿈\n 제어\u0001");
        obs.put("outcome", DrowsinessDetector.Outcome.GAP_RESTARTED);
        elapsed.addAndGet(250);
        s.recordFrame("observation", elapsed.get(), obs);
        s.recordEvent("marker", "phase", "start", "marker_id", 1L, "label", "눈 감기");
        r.stop("user_stop");
        finish(r);

        List<File> files = logFiles(dir);
        assertEquals(1, files.size());
        assertTrue(files.get(0).getName().matches("session_.+_part001\\.jsonl"));
        List<Map<String, Object>> records = readRecords(files.get(0));
        assertEquals(4, records.size());

        Map<String, Object> header = records.get(0);
        assertEquals("header", header.get("type"));
        assertEquals(1.0, header.get("schema_version"));
        assertEquals(1.0, header.get("part"));
        assertEquals("eyes_closed_1s", header.get("scenario"));
        assertNotNull(header.get("environment"));

        Map<String, Object> o = records.get(1);
        assertEquals("observation", o.get("type"));
        assertEquals(250.0, o.get("t_ms"));
        assertEquals(0.0, o.get("face_count"));
        assertTrue(o.containsKey("missing"));
        assertNull(o.get("missing"));
        assertTrue(o.containsKey("nan"));
        assertNull(o.get("nan"));
        assertNull(o.get("inf"));
        assertEquals(0.15, (Double) o.get("prob"), 1e-6);
        assertEquals("따옴표\" 역슬래시\\ 줄바꿈\n 제어\u0001", o.get("text"));
        assertEquals("gap_restarted", o.get("outcome"));

        assertEquals("marker", records.get(2).get("type"));
        assertEquals("눈 감기", records.get(2).get("label"));

        Map<String, Object> end = records.get(3);
        assertEquals("session_end", end.get("type"));
        assertEquals("user_stop", end.get("reason"));
        assertEquals(Boolean.TRUE, end.get("complete"));
        assertEquals(0.0, end.get("frame_records_dropped_queue_full"));
        for (int i = 0; i < records.size(); i++) {
            assertEquals(s.id(), records.get(i).get("session_id"));
            assertEquals((double) (i + 1), records.get(i).get("seq"));
        }
    }

    @Test
    public void rotatesPartsAndDeletesOldestClosedFilesWithinLimit() throws Exception {
        File dir = temp.newFolder("logs");
        File old = new File(dir, "session_00000000-000000_aaaaaa_part001.jsonl");
        Files.write(old.toPath(), new byte[6000]);
        assertTrue(old.setLastModified(1000));
        long maxPart = 4000;
        long maxTotal = 10000;
        DetectionLogRecorder r = recorder(dir, maxPart, maxTotal, 1000, NEW_THREAD);
        DetectionLogRecorder.Session s = r.start("rotation");
        for (int i = 0; i < 100; i++) {
            elapsed.addAndGet(250);
            s.recordFrame("observation", elapsed.get(), observation(s.nextObservationId()));
        }
        r.stop("user_stop");
        finish(r);

        assertFalse("오래된 닫힌 파일부터 지워야 함", old.exists());
        List<File> files = logFiles(dir);
        long total = 0;
        for (File f : files) {
            total += f.length();
            assertTrue("파일당 상한: " + f.getName() + " " + f.length(), f.length() <= maxPart);
            Map<String, Object> first = readRecords(f).get(0);
            assertEquals("header", first.get("type"));
            assertTrue(f.getName().endsWith(String.format("_part%03d.jsonl", ((Double) first.get("part")).intValue())));
        }
        assertTrue("전체 상한: " + total, total <= maxTotal);
        List<Map<String, Object>> last = readRecords(files.get(files.size() - 1));
        Map<String, Object> end = last.get(last.size() - 1);
        assertEquals("session_end", end.get("type"));
        List<?> parts = (List<?>) end.get("files");
        List<?> deletedOwn = (List<?>) end.get("deleted_own_files");
        assertTrue("여러 파일로 분할돼야 함", parts.size() > 2);
        assertFalse("같은 세션의 닫힌 이전 분할도 정리 대상", deletedOwn.isEmpty());
        assertEquals(parts.size() - deletedOwn.size(), files.size());
    }

    @Test
    public void rotationCloseFailureReportsErrorAndDoesNotMarkSessionComplete() throws Exception {
        File dir = temp.newFolder("close_failure");
        DetectionLogRecorder r = new DetectionLogRecorder(dir, Collections.emptyMap(),
                1500, 10000, 100, clock, Runnable::run, NEW_THREAD, statuses::add,
                file -> new java.io.FilterOutputStream(new java.io.FileOutputStream(file)) {
                    @Override public void close() throws IOException {
                        super.close();
                        throw new IOException("injected rotation close failure");
                    }
                });
        DetectionLogRecorder.Session s = r.start("close_failure");
        for (int i = 0; i < 20; i++) {
            s.recordFrame("observation", elapsed.get(), observation(s.nextObservationId()));
        }
        finish(r);
        assertEquals("닫기 실패 후 새 분할 파일을 열면 안 됨", 1, logFiles(dir).size());
        synchronized (statuses) {
            assertTrue(statuses.stream().anyMatch(st -> st.state == DetectionLogRecorder.State.ERROR
                    && st.errorMessage.contains("분할 전")));
        }
        for (Map<String, Object> rec : readRecords(logFiles(dir).get(0))) {
            assertFalse("불완전한 세션을 완료로 기록하면 안 됨", "session_end".equals(rec.get("type")));
        }
    }

    @Test
    public void storageFailureReportsErrorWithoutThrowing() throws Exception {
        File notADirectory = temp.newFile("blocked");
        DetectionLogRecorder r = recorder(notADirectory);
        DetectionLogRecorder.Session s = r.start("failure");
        r.stop("user_stop");
        // 실패 후 기록 요청은 조용히 무시된다.
        s.recordFrame("observation", elapsed.get(), observation(1));
        s.recordEvent("alarm", "event", "requested");
        finish(r);
        boolean error = false;
        synchronized (statuses) {
            for (DetectionLogRecorder.Status st : statuses) {
                if (st.state == DetectionLogRecorder.State.ERROR) error = true;
            }
        }
        assertTrue("오류 상태가 통지돼야 함", error);
    }

    @Test
    public void fullQueueDropsObservationsButKeepsEventsAndClose() throws Exception {
        File dir = temp.newFolder("queue");
        AtomicReference<Runnable> worker = new AtomicReference<>();
        DetectionLogRecorder r = recorder(dir, DetectionLogRecorder.MAX_PART_BYTES,
                DetectionLogRecorder.MAX_TOTAL_BYTES, 5, worker::set);
        DetectionLogRecorder.Session s = r.start("queue");
        // 작업자가 아직 돌지 않아 대기열이 비워지지 않는다. 기록 요청은 막히지 않고 반환해야 한다.
        for (int i = 0; i < 8; i++) s.recordFrame("observation", elapsed.get(), observation(s.nextObservationId()));
        s.recordEvent("marker", "phase", "start");
        r.stop("user_stop");
        Thread t = new Thread(worker.get());
        t.start();
        finish(r);

        List<Map<String, Object>> records = readRecords(logFiles(dir).get(0));
        int observations = 0;
        for (Map<String, Object> rec : records) if ("observation".equals(rec.get("type"))) observations++;
        assertEquals(5, observations);
        assertEquals("marker", records.get(records.size() - 2).get("type"));
        Map<String, Object> end = records.get(records.size() - 1);
        assertEquals("session_end", end.get("type"));
        assertEquals(3.0, end.get("frame_records_dropped_queue_full"));
        assertEquals(8.0, end.get("frame_records_offered"));
    }

    @Test
    public void repeatedSessionsUseSeparateFilesAndIgnoreLateRecords() throws Exception {
        File dir = temp.newFolder("lifecycle");
        DetectionLogRecorder r = recorder(dir);
        List<DetectionLogRecorder.Session> sessions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            DetectionLogRecorder.Session s = r.start("repeat");
            assertTrue("수집 중 start()는 같은 세션을 반환", s == r.start("other"));
            s.recordFrame("observation", elapsed.get(), observation(s.nextObservationId()));
            r.stop(i % 2 == 0 ? "screen_paused" : "user_stop");
            // 중지 이후 이전 세션으로 늦게 도착한 기록은 어느 파일에도 쓰지 않는다.
            s.recordFrame("observation", elapsed.get(), observation(99));
            sessions.add(s);
        }
        finish(r);
        assertNull("해제 후에는 새 세션을 열지 않음", r.start("after"));

        List<File> files = logFiles(dir);
        assertEquals(5, files.size());
        for (File f : files) {
            List<Map<String, Object>> records = readRecords(f);
            assertEquals(3, records.size());
            Object id = records.get(0).get("session_id");
            for (Map<String, Object> rec : records) {
                assertEquals(id, rec.get("session_id"));
                assertFalse(Double.valueOf(99).equals(rec.get("observation_id")));
            }
            assertEquals("session_end", records.get(2).get("type"));
        }
    }

    @Test
    public void releaseWhileRecordingClosesSession() throws Exception {
        File dir = temp.newFolder("release");
        DetectionLogRecorder r = recorder(dir);
        DetectionLogRecorder.Session s = r.start("release");
        s.recordFrame("observation", elapsed.get(), observation(s.nextObservationId()));
        finish(r);
        List<Map<String, Object>> records = readRecords(logFiles(dir).get(0));
        Map<String, Object> end = records.get(records.size() - 1);
        assertEquals("session_end", end.get("type"));
        assertEquals("recorder_released", end.get("reason"));
    }
}
