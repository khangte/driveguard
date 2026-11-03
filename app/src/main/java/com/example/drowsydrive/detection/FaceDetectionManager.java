package com.example.drowsydrive.detection;

import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.example.drowsydrive.BuildConfig;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * ML Kit FaceDetection 연동을 담당하고, 운전자 선택/졸음·하품 판정은 DriverMonitor에 위임한다.
 *
 * <p>스레드 구조: CameraX 분석 진입({@link #analyze})과 ML Kit 결과 처리는 이 클래스가 소유한
 * 단일 분석 실행기에서 실행된다. 경고 콜백만 메인 실행기로 전달한다. ML Kit 추론 자체는 ML Kit
 * 내부 스레드에서 실행된다. {@link #start()}/{@link #stop()}/{@link #close()}는 메인 스레드에서 호출한다.
 */
public class FaceDetectionManager implements ImageAnalysis.Analyzer {
    private static final String TAG = "FaceDetectionManager";
    /** 분석 간격. 1000ms에서 낮춘 측정 전 실험값이며 정확도/배터리 영향은 아직 측정하지 않았다. */
    static final long ANALYSIS_INTERVAL_MS = 250;
    /** 측정 로그에 남기는 판정 설정 버전. 임계값·분석 간격·ML Kit 옵션을 바꾸면 올린다. */
    static final int DETECTION_CONFIG_VERSION = 1;
    private static final int PERFORMANCE_MODE = FaceDetectorOptions.PERFORMANCE_MODE_FAST;
    private static final int LANDMARK_MODE = FaceDetectorOptions.LANDMARK_MODE_ALL;
    private static final int CLASSIFICATION_MODE = FaceDetectorOptions.CLASSIFICATION_MODE_ALL;

    /** 메인 스레드에서 호출된다. */
    public interface Callback {
        void onEyesClosed(); // 눈 감김 지속 시 호출
        void onYawnDetected(); // 하품 지속 시 호출

        default void onEyesClosed(String sourceSessionId, long observationId) { onEyesClosed(); }
        default void onYawnDetected(String sourceSessionId, long observationId) { onYawnDetected(); }
    }

    private final FaceDetector faceDetector;
    private final DriverMonitor driverMonitor = new DriverMonitor();
    private final Callback callback;
    private final Executor mainExecutor;
    private final ExecutorService analysisExecutor =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "FaceAnalysis"));
    /** 종료 후 도착한 ML Kit 결과는 호출 스레드에서 이미지 해제만 하도록 거부 시 직접 실행한다. */
    private final Executor resultExecutor = runnable -> {
        try {
            analysisExecutor.execute(runnable);
        } catch (RejectedExecutionException e) {
            runnable.run();
        }
    };
    private volatile boolean closed = false;
    private long lastAnalyzedTime = 0; // 분석 실행기 스레드에서만 접근한다.
    /** 개발용 측정 로그 세션. null이면 기록하지 않으며 판정 경로는 동일하다. 메인 스레드에서 바꾼다. */
    @Nullable private volatile DetectionLogRecorder.Session logSession;

    public FaceDetectionManager(Callback callback, Executor mainExecutor) {
        this.callback = callback;
        this.mainExecutor = mainExecutor;
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(PERFORMANCE_MODE) // 빠른 처리 모드
                .setLandmarkMode(LANDMARK_MODE) // 얼굴 랜드마크 감지
                .setClassificationMode(CLASSIFICATION_MODE) // 눈 열림/웃음 분류
                .enableTracking() // 프레임 간 운전자 대상 유지를 위한 tracking ID
                .build();
        faceDetector = FaceDetection.getClient(options);
    }

    /** 측정 로그 헤더에 남길 실제 적용 설정. 판정 코드가 쓰는 상수를 그대로 참조한다. */
    public static Map<String, Object> detectionConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("config_version", DETECTION_CONFIG_VERSION);
        config.put("analysis_interval_ms", ANALYSIS_INTERVAL_MS);
        config.put("eye_open_probability_threshold", DrowsinessDetector.EYE_OPEN_PROBABILITY_THRESHOLD);
        config.put("eyes_closed_duration_ms", DrowsinessDetector.EYES_CLOSED_DURATION_THRESHOLD_MS);
        config.put("yawn_mouth_ratio_threshold", DrowsinessDetector.YAWN_MOUTH_RATIO_THRESHOLD);
        config.put("yawn_smiling_probability_threshold",
                DrowsinessDetector.YAWN_SMILING_PROBABILITY_THRESHOLD);
        config.put("yawn_duration_ms", DrowsinessDetector.YAWN_DURATION_THRESHOLD_MS);
        config.put("max_observation_gap_ms", DrowsinessDetector.MAX_OBSERVATION_GAP_MS);
        config.put("min_yawn_reference_distance_px", DrowsinessDetector.MIN_YAWN_REFERENCE_DISTANCE);
        Map<String, Object> mlkit = new LinkedHashMap<>();
        mlkit.put("performance_mode",
                PERFORMANCE_MODE == FaceDetectorOptions.PERFORMANCE_MODE_FAST ? "fast" : "accurate");
        mlkit.put("landmark_mode", LANDMARK_MODE == FaceDetectorOptions.LANDMARK_MODE_ALL ? "all" : "none");
        mlkit.put("classification_mode",
                CLASSIFICATION_MODE == FaceDetectorOptions.CLASSIFICATION_MODE_ALL ? "all" : "none");
        mlkit.put("contour_mode", "none");
        mlkit.put("tracking", true);
        mlkit.put("min_face_size", "default");
        config.put("mlkit", mlkit);
        return config;
    }

    /** 측정 로그 세션을 연결하거나(null이면) 끊는다. 메인 스레드에서 호출한다. */
    public void setLogSession(@Nullable DetectionLogRecorder.Session session) {
        logSession = session;
    }

    /** CameraX ImageAnalysis에 등록할 분석 실행기. */
    public Executor getAnalysisExecutor() {
        return analysisExecutor;
    }

    /** 분석을 시작한다. 이전 판정 상태는 새 세션의 첫 결과에서 초기화된다. */
    public void start() {
        if (closed) return;
        driverMonitor.startSession();
    }

    /** 분석을 중단한다. 이미 시작된 비동기 결과는 판정과 경고에 반영되지 않는다. */
    public void stop() {
        driverMonitor.stopSession();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        boolean handedOff = false;
        long session = driverMonitor.currentSession();
        FrameLog frameLog = null;
        try {
            if (closed || !driverMonitor.isCurrent(session)) return;
            long frameTime = SystemClock.elapsedRealtime();
            // 분석 간격 제한: 너무 자주 분석하지 않도록 프레임을 건너뛴다.
            if (frameTime - lastAnalyzedTime < ANALYSIS_INTERVAL_MS) return;
            Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) return;
            long previousAnalysisStart = lastAnalyzedTime;
            lastAnalyzedTime = frameTime;
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            DetectionLogRecorder.Session log = logSession;
            if (log != null) {
                // 이미지 객체는 보관하지 않고 필요한 값만 복사한다.
                frameLog = new FrameLog(log, log.nextObservationId(), session, frameTime,
                        previousAnalysisStart, imageProxy.getWidth(), imageProxy.getHeight(), rotation,
                        imageProxy.getImageInfo().getTimestamp());
            }
            final FrameLog frame = frameLog;

            InputImage image = InputImage.fromMediaImage(mediaImage, rotation);
            Task<List<Face>> task = faceDetector.process(image);
            task.addOnCompleteListener(resultExecutor, completed -> {
                long completedAt = SystemClock.elapsedRealtime();
                if (BuildConfig.DEBUG) {
                    recordTiming(frameTime, previousAnalysisStart, completedAt, frame != null);
                }
                try {
                    if (completed.isSuccessful()) {
                        handleFaces(session, completed.getResult(), frameTime, frame, completedAt);
                    } else {
                        Log.e(TAG, "얼굴 감지 실패", completed.getException());
                        onFailure(session, frame, "mlkit_failure", completed.getException(), completedAt);
                    }
                } catch (RuntimeException e) {
                    Log.e(TAG, "얼굴 감지 결과 처리 실패", e);
                    onFailure(session, frame, "result_processing_exception", e, completedAt);
                } finally {
                    imageProxy.close();
                }
            });
            handedOff = true;
        } catch (RuntimeException e) {
            Log.e(TAG, "얼굴 감지 요청 실패", e);
            onFailure(session, frameLog, "request_exception", e, SystemClock.elapsedRealtime());
        } finally {
            if (!handedOff) imageProxy.close();
        }
    }

    /**
     * 분석을 멈추고 ML Kit 감지기와 분석 실행기를 정리한다. 카메라 분석기를 해제한 뒤 호출한다.
     * 실행 중인 ML Kit 작업의 결과는 무시되고 이미지만 해제된다.
     */
    public void close() {
        if (closed) return;
        closed = true;
        driverMonitor.stopSession();
        // 분석 진입과 같은 실행기에서 닫아 process() 호출과 겹치지 않게 한다.
        analysisExecutor.execute(faceDetector::close);
        analysisExecutor.shutdown();
    }

    // 디버그 전용 분석 시간 요약. 수집 on/off의 실제 분석 간격과 처리 지연을 Logcat에서 비교한다.
    private static final long TIMING_WINDOW_MS = 10_000;
    private long timingWindowStart = 0;
    private int timingCount = 0;
    private int timingLoggedCount = 0;
    private long timingIntervalSum = 0;
    private int timingIntervalCount = 0;
    private long timingProcessingSum = 0;
    private long timingProcessingMax = 0;

    private void recordTiming(long start, long previousStart, long completedAt, boolean logging) {
        if (timingWindowStart == 0) timingWindowStart = completedAt;
        long processing = completedAt - start;
        timingCount++;
        if (logging) timingLoggedCount++;
        if (previousStart != 0) {
            timingIntervalSum += start - previousStart;
            timingIntervalCount++;
        }
        timingProcessingSum += processing;
        timingProcessingMax = Math.max(timingProcessingMax, processing);
        if (completedAt - timingWindowStart < TIMING_WINDOW_MS) return;
        Log.d(TAG, "분석 시간 요약: 분석 " + timingCount + "회(로그 수집 " + timingLoggedCount + "회)"
                + ", 평균 간격 " + (timingIntervalCount == 0 ? "-" : timingIntervalSum / timingIntervalCount)
                + "ms, 평균 처리 " + timingProcessingSum / timingCount + "ms, 최대 처리 "
                + timingProcessingMax + "ms");
        timingWindowStart = completedAt;
        timingCount = 0;
        timingLoggedCount = 0;
        timingIntervalSum = 0;
        timingIntervalCount = 0;
        timingProcessingSum = 0;
        timingProcessingMax = 0;
    }

    private void onFailure(long session, @Nullable FrameLog frame, String stage, Exception error,
                           long completedAt) {
        DetectionTrace trace = frame != null ? new DetectionTrace() : null;
        driverMonitor.onFailure(session, trace);
        if (frame == null || !trace.applied) return;
        Map<String, Object> fields = frame.baseFields(completedAt);
        fields.put("stage", stage);
        fields.put("error", error == null ? null : error.getClass().getName() + ": " + error.getMessage());
        fields.put("resets", resetsToJson(trace.resets));
        frame.log.recordFrame("analysis_failure", completedAt, fields);
    }

    private void handleFaces(long session, List<Face> faces, long frameTime, @Nullable FrameLog frame,
                             long completedAt) {
        if (!driverMonitor.isCurrent(session)) return;
        List<DriverMonitor.FaceObservation> observations = new ArrayList<>(faces.size());
        for (Face face : faces) {
            observations.add(toObservation(face));
        }
        DetectionTrace trace = frame != null ? new DetectionTrace() : null;
        int alerts = driverMonitor.onFaces(session, observations, frameTime, trace);
        if (frame != null && trace.applied) {
            frame.log.recordFrame("observation", completedAt,
                    observationFields(frame, faces, trace, alerts, completedAt));
        }
        if (alerts == DriverMonitor.ALERT_NONE) return;
        mainExecutor.execute(() -> {
            // 메인 스레드에서 stop()과 순서가 정해지므로 여기서 다시 확인한다.
            if (!driverMonitor.isCurrent(session)) {
                if (frame != null) {
                    frame.log.recordEvent("alert_discarded", "observation_id", frame.observationId,
                            "reason", "analysis_session_ended");
                }
                return;
            }
            if (frame != null) {
                frame.log.recordEvent("alert_dispatched", "observation_id", frame.observationId,
                        "eyes_closed", (alerts & DriverMonitor.ALERT_EYES_CLOSED) != 0,
                        "yawn", (alerts & DriverMonitor.ALERT_YAWN) != 0);
            }
            if ((alerts & DriverMonitor.ALERT_EYES_CLOSED) != 0) {
                Log.d(TAG, "졸음 감지!!");
                if (frame == null) callback.onEyesClosed();
                else callback.onEyesClosed(frame.log.id(), frame.observationId);
            }
            if ((alerts & DriverMonitor.ALERT_YAWN) != 0) {
                Log.d(TAG, "하품 감지됨!");
                if (frame == null) callback.onYawnDetected();
                else callback.onYawnDetected(frame.log.id(), frame.observationId);
            }
        });
    }

    private static DriverMonitor.FaceObservation toObservation(Face face) {
        Rect box = face.getBoundingBox();
        float area = (float) box.width() * box.height();
        return new DriverMonitor.FaceObservation(
                face.getTrackingId(), area,
                face.getLeftEyeOpenProbability(), face.getRightEyeOpenProbability(),
                face.getSmilingProbability(),
                landmarkY(face, FaceLandmark.MOUTH_BOTTOM),
                landmarkY(face, FaceLandmark.NOSE_BASE),
                landmarkY(face, FaceLandmark.RIGHT_EYE));
    }

    /** 분석 시작 시점에 복사한 기록용 값. CameraX 이미지 객체는 담지 않는다. */
    private static final class FrameLog {
        final DetectionLogRecorder.Session log;
        final long observationId;
        final long analysisSession;
        final long analysisStart;
        final long previousAnalysisStart;
        final int width;
        final int height;
        final int rotation;
        final long cameraTimestampNs;

        FrameLog(DetectionLogRecorder.Session log, long observationId, long analysisSession,
                 long analysisStart, long previousAnalysisStart, int width, int height, int rotation,
                 long cameraTimestampNs) {
            this.log = log;
            this.observationId = observationId;
            this.analysisSession = analysisSession;
            this.analysisStart = analysisStart;
            this.previousAnalysisStart = previousAnalysisStart;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.cameraTimestampNs = cameraTimestampNs;
        }

        Map<String, Object> baseFields(long completedAt) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("observation_id", observationId);
            f.put("analysis_session", analysisSession);
            // frameTime은 촬영 시각이 아니라 분석 시작 시각이다.
            f.put("analysis_start_ms", log.relativeMs(analysisStart));
            f.put("analysis_end_ms", log.relativeMs(completedAt));
            f.put("processing_ms", completedAt - analysisStart);
            f.put("interval_from_prev_start_ms",
                    previousAnalysisStart == 0 ? null : analysisStart - previousAnalysisStart);
            // 카메라 타임스탬프는 시계 기준을 확인하지 않았으므로 다른 시각과 빼지 않는다.
            f.put("camera_timestamp_ns", cameraTimestampNs);
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("width", width);
            image.put("height", height);
            image.put("rotation_degrees", rotation);
            f.put("image", image);
            return f;
        }
    }

    private static Map<String, Object> observationFields(FrameLog frame, List<Face> faces,
                                                         DetectionTrace trace, int alerts, long completedAt) {
        Map<String, Object> f = frame.baseFields(completedAt);
        f.put("face_count", faces.size());
        List<Object> list = new ArrayList<>(faces.size());
        for (int i = 0; i < faces.size(); i++) {
            list.add(faceFields(faces.get(i), i, i == trace.selectedIndex));
        }
        f.put("faces", list);
        f.put("selected_index", trace.selectedIndex < 0 ? null : trace.selectedIndex);
        f.put("target_reason", trace.targetReason);
        f.put("resets", resetsToJson(trace.resets));
        f.put("eyes", episodeFields(trace.eyes, false));
        f.put("yawn", episodeFields(trace.yawn, true));
        f.put("alert_eyes_closed", (alerts & DriverMonitor.ALERT_EYES_CLOSED) != 0);
        f.put("alert_yawn", (alerts & DriverMonitor.ALERT_YAWN) != 0);
        f.put("used_for_detection", trace.applied);
        return f;
    }

    private static Map<String, Object> faceFields(Face face, int index, boolean selected) {
        Map<String, Object> f = new LinkedHashMap<>();
        Map<String, Object> issues = new LinkedHashMap<>();
        f.put("index", index);
        f.put("tracking_id", face.getTrackingId());
        Rect box = face.getBoundingBox();
        Map<String, Object> bbox = new LinkedHashMap<>();
        bbox.put("left", box.left);
        bbox.put("top", box.top);
        bbox.put("width", box.width());
        bbox.put("height", box.height());
        f.put("bbox", bbox);
        putChecked(f, issues, "left_eye_open", face.getLeftEyeOpenProbability(), "left_eye_open");
        putChecked(f, issues, "right_eye_open", face.getRightEyeOpenProbability(), "right_eye_open");
        putChecked(f, issues, "smiling", face.getSmilingProbability(), "smiling");
        Map<String, Object> landmarks = new LinkedHashMap<>();
        landmarks.put("mouth_bottom", landmarkFields(face, FaceLandmark.MOUTH_BOTTOM, "mouth_bottom", issues));
        landmarks.put("nose_base", landmarkFields(face, FaceLandmark.NOSE_BASE, "nose_base", issues));
        landmarks.put("right_eye", landmarkFields(face, FaceLandmark.RIGHT_EYE, "right_eye", issues));
        f.put("landmarks", landmarks);
        // 얼굴 각도는 기록만 하며 판정 필터로 쓰지 않는다.
        putChecked(f, issues, "euler_x", face.getHeadEulerAngleX(), "euler_x");
        putChecked(f, issues, "euler_y", face.getHeadEulerAngleY(), "euler_y");
        putChecked(f, issues, "euler_z", face.getHeadEulerAngleZ(), "euler_z");
        f.put("value_issues", issues);
        f.put("selected", selected);
        f.put("unused_reason", selected ? null : "not_selected");
        return f;
    }

    private static Map<String, Object> landmarkFields(Face face, int type, String name,
                                                      Map<String, Object> issues) {
        FaceLandmark landmark = face.getLandmark(type);
        PointF p = landmark == null ? null : landmark.getPosition();
        if (p == null) {
            issues.put(name, "missing");
            return null;
        }
        Map<String, Object> point = new LinkedHashMap<>();
        putChecked(point, issues, "x", p.x, name + ".x");
        putChecked(point, issues, "y", p.y, name + ".y");
        return point;
    }

    /** 누락은 null + missing, NaN/Infinity는 null + non_finite로 남긴다. 0으로 대체하지 않는다. */
    static void putChecked(Map<String, Object> target, Map<String, Object> issues, String key,
                           Float value, String issueKey) {
        if (value == null) {
            issues.put(issueKey, "missing");
            target.put(key, null);
        } else if (value.isNaN() || value.isInfinite()) {
            issues.put(issueKey, "non_finite");
            target.put(key, null);
        } else {
            target.put(key, value);
        }
    }

    private static Map<String, Object> episodeFields(DetectionTrace.Episode e, boolean includeRatio) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("input", e.input);
        f.put("outcome", e.outcome);
        f.put("condition_met", e.conditionMet());
        if (includeRatio) f.put("ratio", e.ratio);
        f.put("active_before", e.activeBefore);
        f.put("active", e.active);
        f.put("alerted_in_episode", e.alerted);
        f.put("elapsed_ms", e.elapsedMs);
        f.put("alert_fired", e.alertFired);
        return f;
    }

    private static List<Object> resetsToJson(List<DetectionTrace.Reset> resets) {
        List<Object> list = new ArrayList<>(resets.size());
        for (DetectionTrace.Reset r : resets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reason", r.reason);
            m.put("eyes_was_active", r.eyesWasActive);
            m.put("yawn_was_active", r.yawnWasActive);
            list.add(m);
        }
        return list;
    }

    private static Float landmarkY(Face face, int landmarkType) {
        FaceLandmark landmark = face.getLandmark(landmarkType);
        if (landmark == null) return null;
        PointF position = landmark.getPosition();
        return position == null ? null : position.y;
    }
}
