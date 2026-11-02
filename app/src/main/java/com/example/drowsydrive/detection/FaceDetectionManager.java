package com.example.drowsydrive.detection;

import android.media.Image;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;

/**
 * ML Kit FaceDetection 연동을 담당하고, 졸음/하품 판정은 DrowsinessDetector에 위임해 결과를 콜백으로 전달한다.
 */
public class FaceDetectionManager implements ImageAnalysis.Analyzer {
    private static final String TAG = "FaceDetectionManager";
    private static final long ANALYSIS_INTERVAL = 1000; // 분석 간격 (1초)

    public interface Callback {
        void onEyesClosed(); // 눈 감김 지속 시 호출
        void onYawnDetected(); // 하품 지속 시 호출
    }

    private final FaceDetector faceDetector;
    private final DrowsinessDetector drowsinessDetector = new DrowsinessDetector();
    private final Callback callback;
    private long lastAnalyzedTime = 0;

    public FaceDetectionManager(Callback callback) {
        this.callback = callback;
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // 빠른 처리 모드
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // 얼굴 랜드마크 감지
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // 눈 열림/웃음 분류
                .build();
        faceDetector = FaceDetection.getClient(options);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        long currentTime = SystemClock.elapsedRealtime();
        Image mediaImage = imageProxy.getImage();
        // 분석 간격 제한: 너무 자주 분석하지 않도록 프레임을 건너뛴다.
        if (mediaImage == null || currentTime - lastAnalyzedTime < ANALYSIS_INTERVAL) {
            imageProxy.close();
            return;
        }
        lastAnalyzedTime = currentTime;

        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        faceDetector.process(image)
                .addOnSuccessListener(this::processFaces)
                .addOnFailureListener(e -> Log.e(TAG, "얼굴 감지 실패", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    public void close() {
        faceDetector.close();
    }

    private void processFaces(List<Face> faces) {
        if (faces.isEmpty()) {
            Log.d(TAG, "얼굴이 감지되지 않았습니다.");
            drowsinessDetector.reset();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        for (Face face : faces) {
            Float leftEyeOpen = face.getLeftEyeOpenProbability();
            Float rightEyeOpen = face.getRightEyeOpenProbability();
            if (leftEyeOpen != null && rightEyeOpen != null) {
                Log.d(TAG, "눈 열림 확률 - 왼쪽: " + leftEyeOpen + ", 오른쪽: " + rightEyeOpen);
                if (drowsinessDetector.updateEyes(leftEyeOpen, rightEyeOpen, now)) {
                    Log.d(TAG, "졸음 감지!!");
                    callback.onEyesClosed();
                }
            }

            Float smiling = face.getSmilingProbability();
            FaceLandmark mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);
            FaceLandmark noseBase = face.getLandmark(FaceLandmark.NOSE_BASE);
            if (smiling == null || mouthBottom == null || noseBase == null) {
                continue;
            }
            FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);
            if (rightEye == null) {
                Log.w(TAG, "랜드마크 데이터가 누락되었습니다.");
                continue;
            }
            if (drowsinessDetector.updateYawn(smiling, mouthBottom.getPosition().y,
                    noseBase.getPosition().y, rightEye.getPosition().y, now)) {
                Log.d(TAG, "하품 감지됨!");
                callback.onYawnDetected();
            }
        }
    }
}
