package com.example.drowsydrive.detection;

import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;
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

    /** 메인 스레드에서 호출된다. */
    public interface Callback {
        void onEyesClosed(); // 눈 감김 지속 시 호출
        void onYawnDetected(); // 하품 지속 시 호출
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

    public FaceDetectionManager(Callback callback, Executor mainExecutor) {
        this.callback = callback;
        this.mainExecutor = mainExecutor;
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // 빠른 처리 모드
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // 얼굴 랜드마크 감지
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // 눈 열림/웃음 분류
                .enableTracking() // 프레임 간 운전자 대상 유지를 위한 tracking ID
                .build();
        faceDetector = FaceDetection.getClient(options);
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
        try {
            if (closed || !driverMonitor.isCurrent(session)) return;
            long frameTime = SystemClock.elapsedRealtime();
            // 분석 간격 제한: 너무 자주 분석하지 않도록 프레임을 건너뛴다.
            if (frameTime - lastAnalyzedTime < ANALYSIS_INTERVAL_MS) return;
            Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) return;
            lastAnalyzedTime = frameTime;

            InputImage image = InputImage.fromMediaImage(mediaImage,
                    imageProxy.getImageInfo().getRotationDegrees());
            Task<List<Face>> task = faceDetector.process(image);
            task.addOnCompleteListener(resultExecutor, completed -> {
                try {
                    if (completed.isSuccessful()) {
                        handleFaces(session, completed.getResult(), frameTime);
                    } else {
                        Log.e(TAG, "얼굴 감지 실패", completed.getException());
                        driverMonitor.onFailure(session);
                    }
                } catch (RuntimeException e) {
                    Log.e(TAG, "얼굴 감지 결과 처리 실패", e);
                    driverMonitor.onFailure(session);
                } finally {
                    imageProxy.close();
                }
            });
            handedOff = true;
        } catch (RuntimeException e) {
            Log.e(TAG, "얼굴 감지 요청 실패", e);
            driverMonitor.onFailure(session);
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

    private void handleFaces(long session, List<Face> faces, long frameTime) {
        if (!driverMonitor.isCurrent(session)) return;
        List<DriverMonitor.FaceObservation> observations = new ArrayList<>(faces.size());
        for (Face face : faces) {
            observations.add(toObservation(face));
        }
        int alerts = driverMonitor.onFaces(session, observations, frameTime);
        if (alerts == DriverMonitor.ALERT_NONE) return;
        mainExecutor.execute(() -> {
            // 메인 스레드에서 stop()과 순서가 정해지므로 여기서 다시 확인한다.
            if (!driverMonitor.isCurrent(session)) return;
            if ((alerts & DriverMonitor.ALERT_EYES_CLOSED) != 0) {
                Log.d(TAG, "졸음 감지!!");
                callback.onEyesClosed();
            }
            if ((alerts & DriverMonitor.ALERT_YAWN) != 0) {
                Log.d(TAG, "하품 감지됨!");
                callback.onYawnDetected();
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

    private static Float landmarkY(Face face, int landmarkType) {
        FaceLandmark landmark = face.getLandmark(landmarkType);
        if (landmark == null) return null;
        PointF position = landmark.getPosition();
        return position == null ? null : position.y;
    }
}
