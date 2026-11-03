package com.example.drowsydrive.detection;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 개발용 졸음 감지 측정 로그를 세션별 JSONL 파일로 저장한다. 디버그 빌드에서만 생성한다.
 *
 * <p>스레드 구조: {@link #start}/{@link #stop}/{@link #release}는 메인 스레드에서 호출한다.
 * {@link Session}의 기록 메서드는 어느 스레드에서나 호출할 수 있으며 값의 복사본을 대기열에 넣고 바로 반환한다.
 * 파일 열기·쓰기·flush·정리·닫기와 JSON 직렬화는 단일 기록 작업자 스레드에서만 실행한다.
 *
 * <p>관측 기록은 크기가 제한된 대기열을 쓰며, 가득 차면 기다리지 않고 버린 뒤 유실 수를 센다.
 * 세션 열기/닫기와 드문 이벤트(알람, 마커)는 버리지 않는다.
 */
public final class DetectionLogRecorder {
    private static final String TAG = "DetectionLog";
    static final int SCHEMA_VERSION = 1;
    static final String DIRECTORY_NAME = "detection-logs";
    static final long MAX_PART_BYTES = 10L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    static final int QUEUE_CAPACITY = 256;
    static final long FLUSH_INTERVAL_MS = 1000;

    public enum State { STOPPED, RECORDING, ERROR }

    /** 화면 표시용 상태. */
    public static final class Status {
        public final State state;
        public final String sessionId;
        public final String fileName;
        public final long recordsWritten;
        public final long observationsDropped;
        public final String errorMessage;

        Status(State state, String sessionId, String fileName, long recordsWritten,
               long observationsDropped, String errorMessage) {
            this.state = state;
            this.sessionId = sessionId;
            this.fileName = fileName;
            this.recordsWritten = recordsWritten;
            this.observationsDropped = observationsDropped;
            this.errorMessage = errorMessage;
        }
    }

    /** 상태 변경 통지. 생성 시 받은 콜백 실행기(앱에서는 메인 스레드)에서 호출된다. */
    public interface Listener {
        void onStatusChanged(Status status);
    }

    interface Clock {
        long elapsedMs();
        long wallMs();
    }

    /** 테스트에서 실제 분할/닫기 시점의 I/O 실패를 주입한다. */
    interface OutputFactory {
        OutputStream open(File file) throws IOException;
    }

    private final File directory;
    private final Map<String, Object> environment;
    private final long maxPartBytes;
    private final long maxTotalBytes;
    private final int queueCapacity;
    private final Clock clock;
    private final Executor callbackExecutor;
    private final Executor workerStarter;
    // 메인 스레드에서 연결/해제한다. 화면 재생성 시 Activity 참조를 교체한다.
    private Listener listener;
    private final OutputFactory outputFactory;

    private final LinkedBlockingQueue<Item> queue = new LinkedBlockingQueue<>();
    private final AtomicInteger pendingFrames = new AtomicInteger();
    private final CountDownLatch terminated = new CountDownLatch(1);

    // 메인 스레드 상태
    private Session current;
    private boolean workerStarted;
    private boolean released;

    /** 앱용 생성. noBackupFilesDir/detection-logs에 저장한다. */
    public static DetectionLogRecorder create(Context context, Map<String, Object> environment,
                                              Listener listener) {
        File dir = new File(context.getNoBackupFilesDir(), DIRECTORY_NAME);
        Clock clock = new Clock() {
            @Override public long elapsedMs() { return SystemClock.elapsedRealtime(); }
            @Override public long wallMs() { return System.currentTimeMillis(); }
        };
        return new DetectionLogRecorder(dir, environment, MAX_PART_BYTES, MAX_TOTAL_BYTES, QUEUE_CAPACITY,
                clock, ContextCompat.getMainExecutor(context),
                runnable -> new Thread(runnable, "DetectionLogWriter").start(), listener);
    }

    DetectionLogRecorder(File directory, Map<String, Object> environment, long maxPartBytes,
                         long maxTotalBytes, int queueCapacity, Clock clock, Executor callbackExecutor,
                         Executor workerStarter, Listener listener) {
        this(directory, environment, maxPartBytes, maxTotalBytes, queueCapacity, clock,
                callbackExecutor, workerStarter, listener,
                file -> new BufferedOutputStream(new FileOutputStream(file, false), 64 * 1024));
    }

    DetectionLogRecorder(File directory, Map<String, Object> environment, long maxPartBytes,
                         long maxTotalBytes, int queueCapacity, Clock clock, Executor callbackExecutor,
                         Executor workerStarter, Listener listener, OutputFactory outputFactory) {
        if (maxTotalBytes < maxPartBytes) throw new IllegalArgumentException("전체 상한은 파일 상한 이상이어야 함");
        this.directory = directory;
        this.environment = environment;
        this.maxPartBytes = maxPartBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.queueCapacity = queueCapacity;
        this.clock = clock;
        this.callbackExecutor = callbackExecutor;
        this.workerStarter = workerStarter;
        this.listener = listener;
        this.outputFactory = outputFactory;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 새 수집 세션을 연다. 이미 수집 중이면 그 세션을 반환한다. 해제 후에는 null. */
    public Session start(String scenario) {
        if (released) return null;
        if (current != null && current.accepting) return current;
        if (!workerStarted) {
            workerStarted = true;
            workerStarter.execute(this::runWorker);
        }
        long wall = clock.wallMs();
        String id = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(wall))
                + "_" + UUID.randomUUID().toString().substring(0, 6);
        Session session = new Session(id, scenario == null ? "" : scenario, clock.elapsedMs(), wall);
        current = session;
        queue.add(new Item(Item.OPEN, session, null, 0, null));
        Log.i(TAG, "수집 시작: " + id + " (" + session.scenario + ")");
        notifyStatus(session, State.RECORDING, null);
        return session;
    }

    /** 현재 세션을 닫는다. 이미 받은 기록은 순서대로 쓴 뒤 닫는다. 메인 스레드를 기다리게 하지 않는다. */
    public void stop(String reason) {
        Session session = current;
        if (session == null || !session.accepting) return;
        session.accepting = false;
        queue.add(new Item(Item.CLOSE, session, reason, 0, null));
        Log.i(TAG, "수집 중지 요청: " + session.id + " (" + reason + ")");
        notifyStatus(session, State.STOPPED, null);
    }

    /** 현재 세션을 닫고 기록 작업자를 끝낸다. 이후 start()는 null을 반환한다. */
    public void release() {
        if (released) return;
        stop("recorder_released");
        released = true;
        if (workerStarted) queue.add(new Item(Item.SHUTDOWN, null, null, 0, null));
        else terminated.countDown();
    }

    boolean awaitTermination(long timeoutMs) throws InterruptedException {
        return terminated.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    File directory() {
        return directory;
    }

    /** 하나의 수집 세션. 기록 메서드는 어느 스레드에서나 호출할 수 있다. */
    public final class Session {
        final String id;
        final String scenario;
        final long startElapsedMs;
        final long startWallMs;
        final AtomicLong observationIds = new AtomicLong();
        final AtomicLong framesOffered = new AtomicLong();
        final AtomicLong framesDropped = new AtomicLong();
        final AtomicLong recordsWritten = new AtomicLong();
        volatile boolean accepting = true;
        volatile String currentFile;

        Session(String id, String scenario, long startElapsedMs, long startWallMs) {
            this.id = id;
            this.scenario = scenario;
            this.startElapsedMs = startElapsedMs;
            this.startWallMs = startWallMs;
        }

        public String id() {
            return id;
        }

        public long nextObservationId() {
            return observationIds.incrementAndGet();
        }

        /** SystemClock.elapsedRealtime() 값을 세션 시작 기준 경과 시간으로 바꾼다. */
        public long relativeMs(long elapsedRealtimeMs) {
            return elapsedRealtimeMs - startElapsedMs;
        }

        /**
         * 관측·분석 실패처럼 분석마다 생기는 기록. 대기열이 가득 차면 버리고 유실 수를 센다.
         * fields는 넘긴 뒤 수정하지 않아야 한다.
         */
        public void recordFrame(String type, long eventElapsedMs, Map<String, Object> fields) {
            if (!accepting) return;
            framesOffered.incrementAndGet();
            if (pendingFrames.incrementAndGet() > queueCapacity) {
                pendingFrames.decrementAndGet();
                framesDropped.incrementAndGet();
                return;
            }
            queue.add(new Item(Item.FRAME, this, type, relativeMs(eventElapsedMs), fields));
        }

        /** 드문 이벤트. 버리지 않는다. keyValues는 이름, 값 순서로 넘긴다. */
        public void recordEvent(String type, Object... keyValues) {
            if (!accepting) return;
            long t = relativeMs(clock.elapsedMs());
            Map<String, Object> fields = new LinkedHashMap<>();
            for (int i = 0; i + 1 < keyValues.length; i += 2) {
                fields.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
            queue.add(new Item(Item.EVENT, this, type, t, fields));
        }
    }

    private static final class Item {
        static final int OPEN = 0, FRAME = 1, EVENT = 2, CLOSE = 3, SHUTDOWN = 4;
        final int kind;
        final Session session;
        /** FRAME/EVENT: 기록 종류, CLOSE: 종료 사유 */
        final String text;
        final long tMs;
        final Map<String, Object> fields;

        Item(int kind, Session session, String text, long tMs, Map<String, Object> fields) {
            this.kind = kind;
            this.session = session;
            this.text = text;
            this.tMs = tMs;
            this.fields = fields;
        }
    }

    // ---- 아래는 기록 작업자 스레드 전용 ----

    private Session open;
    private OutputStream out;
    private File partFile;
    private int part;
    private long partBytes;
    private long seq;
    private boolean dirty;
    private long lastFlushMs;
    private final List<String> partNames = new ArrayList<>();
    private final List<String> deletedNames = new ArrayList<>();

    private void runWorker() {
        try {
            while (true) {
                long wait = Math.max(1, FLUSH_INTERVAL_MS - (clock.elapsedMs() - lastFlushMs));
                Item item = queue.poll(dirty ? wait : FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (item != null) {
                    if (item.kind == Item.FRAME) pendingFrames.decrementAndGet();
                    if (item.kind == Item.SHUTDOWN) {
                        if (open != null) closeSession(open, "recorder_released");
                        return;
                    }
                    handle(item);
                }
                if (dirty && clock.elapsedMs() - lastFlushMs >= FLUSH_INTERVAL_MS) flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // 기록 실패가 감지 기능에 영향을 주지 않도록 작업자 안에서 끝낸다.
            if (open != null) fail(open, "기록 작업자 오류", e);
        } finally {
            closeStreamQuietly();
            terminated.countDown();
        }
    }

    private void handle(Item item) {
        switch (item.kind) {
            case Item.OPEN:
                if (open != null) closeSession(open, "superseded");
                openSession(item.session);
                break;
            case Item.FRAME:
            case Item.EVENT:
                // 닫혔거나 실패한 세션의 늦은 기록은 다른 세션 파일에 섞지 않고 버린다.
                if (open == item.session) write(item.session, item.text, item.tMs, item.fields);
                break;
            case Item.CLOSE:
                if (open == item.session) closeSession(item.session, item.text);
                break;
            default:
                break;
        }
    }

    private void openSession(Session session) {
        open = session;
        part = 0;
        seq = 0;
        partNames.clear();
        deletedNames.clear();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            fail(session, "로그 디렉터리를 만들 수 없음: " + directory, null);
            return;
        }
        openNextPart(session);
    }

    private void openNextPart(Session session) {
        // 정상 분할의 close는 버퍼 쓰기 실패를 감추면 안 된다.
        if (out != null) {
            try {
                out.close();
                out = null;
            } catch (IOException e) {
                fail(session, "분할 전 로그 파일 닫기 실패", e);
                return;
            }
        }
        List<String> deletedNow = enforceRetention();
        if (deletedNow == null) {
            fail(session, "보관 상한을 지키기 위한 이전 로그 정리 실패", null);
            return;
        }
        part++;
        String name = String.format(Locale.US, "session_%s_part%03d.jsonl", session.id, part);
        partFile = new File(directory, name);
        try {
            out = outputFactory.open(partFile);
        } catch (IOException e) {
            fail(session, "로그 파일 열기 실패", e);
            return;
        }
        partBytes = 0;
        partNames.add(name);
        session.currentFile = name;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("scenario", session.scenario);
        header.put("started_wall_ms", session.startWallMs);
        header.put("started_wall_time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                .format(new Date(session.startWallMs)));
        header.put("started_elapsed_realtime_ms", session.startElapsedMs);
        header.put("time_basis", "t_ms 등 *_ms 시각은 SystemClock.elapsedRealtime() 기준 세션 시작 후 경과 ms");
        header.put("environment", environment);
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("max_part_bytes", maxPartBytes);
        limits.put("max_total_bytes", maxTotalBytes);
        limits.put("queue_capacity", queueCapacity);
        limits.put("flush_interval_ms", FLUSH_INTERVAL_MS);
        header.put("limits", limits);
        writeLine(session, "header", session.relativeMs(clock.elapsedMs()), header);
        if (!deletedNow.isEmpty() && open == session) {
            Map<String, Object> retention = new LinkedHashMap<>();
            retention.put("deleted_files", deletedNow);
            writeLine(session, "retention_cleanup", session.relativeMs(clock.elapsedMs()), retention);
        }
        postStatus(session, State.RECORDING, null);
    }

    /**
     * 새 파일을 열기 전에 닫힌 파일 합계가 (전체 상한 - 파일 상한) 이하가 되도록 오래된 파일부터 지운다.
     * 실패하면 null을 반환한다.
     */
    private List<String> enforceRetention() {
        File[] files = directory.listFiles((dir, n) -> n.startsWith("session_") && n.endsWith(".jsonl"));
        if (files == null) return null;
        Arrays.sort(files, (a, b) -> {
            int byTime = Long.compare(a.lastModified(), b.lastModified());
            return byTime != 0 ? byTime : a.getName().compareTo(b.getName());
        });
        long total = 0;
        for (File f : files) total += f.length();
        List<String> deleted = new ArrayList<>();
        for (File f : files) {
            if (total <= maxTotalBytes - maxPartBytes) break;
            long size = f.length();
            if (!f.delete()) return null;
            total -= size;
            deleted.add(f.getName());
            if (partNames.contains(f.getName())) deletedNames.add(f.getName());
        }
        if (total > maxTotalBytes - maxPartBytes) return null;
        return deleted;
    }

    private void write(Session session, String type, long tMs, Map<String, Object> fields) {
        byte[] line = encode(session, type, tMs, fields);
        if (partBytes > 0 && partBytes + line.length > maxPartBytes) {
            openNextPart(session);
            if (open != session) return;
            line = encode(session, type, tMs, fields); // 분할 번호가 바뀌었다.
        }
        writeBytes(session, line);
    }

    private void writeLine(Session session, String type, long tMs, Map<String, Object> fields) {
        writeBytes(session, encode(session, type, tMs, fields));
    }

    private byte[] encode(Session session, String type, long tMs, Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"type\":");
        appendJson(sb, type);
        sb.append(",\"schema_version\":").append(SCHEMA_VERSION);
        sb.append(",\"session_id\":");
        appendJson(sb, session.id);
        sb.append(",\"part\":").append(part);
        sb.append(",\"seq\":").append(seq + 1);
        sb.append(",\"t_ms\":").append(tMs);
        if (fields != null) {
            for (Map.Entry<String, Object> e : fields.entrySet()) {
                sb.append(',');
                appendJson(sb, e.getKey());
                sb.append(':');
                appendJson(sb, e.getValue());
            }
        }
        sb.append("}\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void writeBytes(Session session, byte[] line) {
        if (out == null) return;
        try {
            out.write(line);
            seq++;
            partBytes += line.length;
            session.recordsWritten.incrementAndGet();
            dirty = true;
        } catch (IOException e) {
            fail(session, "로그 쓰기 실패", e);
        }
    }

    private void flush() {
        if (out != null) {
            try {
                out.flush(); // 버퍼를 OS로 넘길 뿐 전원 차단까지 영구 저장을 보장하지 않는다.
            } catch (IOException e) {
                fail(open, "로그 flush 실패", e);
            }
        }
        dirty = false;
        lastFlushMs = clock.elapsedMs();
        if (open != null) postStatus(open, State.RECORDING, null);
    }

    private void closeSession(Session session, String reason) {
        long t = session.relativeMs(clock.elapsedMs());
        if (partBytes + encode(session, "session_end", t, footer(session, reason)).length > maxPartBytes) {
            openNextPart(session);
            if (open != session) return;
        }
        writeLine(session, "session_end", t, footer(session, reason)); // 새 분할 파일 이름까지 포함한다.
        if (open != session) return; // 쓰기 실패로 이미 오류 처리됐다.
        try {
            out.close();
        } catch (IOException e) {
            fail(session, "로그 파일 닫기 실패", e);
            return;
        }
        out = null;
        open = null;
        dirty = false;
        Log.i(TAG, "수집 종료: " + session.id + " (" + reason + "), 파일 " + partNames
                + ", 유실 " + session.framesDropped.get());
        postStatus(session, State.STOPPED, null);
    }

    private Map<String, Object> footer(Session session, String reason) {
        Map<String, Object> end = new LinkedHashMap<>();
        end.put("reason", reason);
        end.put("observations_started", session.observationIds.get());
        end.put("frame_records_offered", session.framesOffered.get());
        end.put("frame_records_dropped_queue_full", session.framesDropped.get());
        end.put("records_written", session.recordsWritten.get() + 1);
        end.put("files", new ArrayList<>(partNames));
        end.put("deleted_own_files", new ArrayList<>(deletedNames));
        end.put("complete", true);
        return end;
    }

    private void fail(Session session, String message, Exception e) {
        Log.e(TAG, "수집 오류: " + message, e);
        closeStreamQuietly();
        if (session != null) session.accepting = false;
        open = null;
        dirty = false;
        postStatus(session, State.ERROR, e == null ? message : message + ": " + e.getMessage());
    }

    private void closeStreamQuietly() {
        if (out == null) return;
        try {
            out.close();
        } catch (IOException ignored) {
            // 이미 실패 처리 중이다.
        }
        out = null;
    }

    private void postStatus(Session session, State state, String error) {
        if (session == null) return;
        callbackExecutor.execute(() -> notifyStatus(session, state, error));
    }

    /** 메인 스레드. 화면에는 가장 최근 세션의 상태만 전달한다. */
    private void notifyStatus(Session session, State state, String error) {
        if (session != current || listener == null) return;
        // 중지 요청 후 작업자가 늦게 보낸 RECORDING은 무시한다.
        if (state == State.RECORDING && !session.accepting) state = State.STOPPED;
        listener.onStatusChanged(new Status(state, session.id, session.currentFile,
                session.recordsWritten.get(), session.framesDropped.get(), error));
    }

    // ---- JSON ----

    /** 최소 JSON 직렬화. NaN/Infinity는 JSON 숫자가 아니므로 null로 쓴다. */
    static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            appendString(sb, (String) value);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            sb.append(value);
        } else if (value instanceof Float || value instanceof Double) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
            else sb.append(value);
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                appendString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                appendJson(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Iterable<?>) value) {
                if (!first) sb.append(',');
                first = false;
                appendJson(sb, o);
            }
            sb.append(']');
        } else if (value instanceof Object[]) {
            appendJson(sb, Arrays.asList((Object[]) value));
        } else if (value instanceof Enum) {
            appendString(sb, ((Enum<?>) value).name().toLowerCase(Locale.US));
        } else {
            appendString(sb, value.toString());
        }
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
