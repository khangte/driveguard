package com.example.drowsydrive.detection;

import android.content.Context;
import android.util.Log;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/** 카메라 바인딩은 메인 스레드에서, 프레임 분석은 전달받은 분석 실행기에서 실행한다. */
public class CameraController {
    private static final String TAG = "CameraController";

    private final Context context;
    private final PreviewView previewView;
    private final ImageAnalysis.Analyzer imageAnalyzer;
    private final Executor analysisExecutor;
    private ImageAnalysis imageAnalysis; // 아래 바인딩 상태는 메인 스레드에서만 접근한다.
    private Preview preview;
    private ProcessCameraProvider cameraProvider;
    private final ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private long requestGeneration = 0;

    public CameraController(Context context, PreviewView previewView, ImageAnalysis.Analyzer imageAnalyzer,
                            Executor analysisExecutor) {
        this(context, previewView, imageAnalyzer, analysisExecutor,
                ProcessCameraProvider.getInstance(context));
    }

    CameraController(Context context, PreviewView previewView, ImageAnalysis.Analyzer imageAnalyzer,
                     Executor analysisExecutor,
                     ListenableFuture<ProcessCameraProvider> cameraProviderFuture) {
        this.context = context;
        this.previewView = previewView;
        this.imageAnalyzer = imageAnalyzer;
        this.analysisExecutor = analysisExecutor;
        this.cameraProviderFuture = cameraProviderFuture;
    }

    public void startCamera() {
        long generation = ++requestGeneration;
        cameraProviderFuture.addListener(() -> {
            try {
                // 중단/재시작 전 요청은 화면이 다시 RESUMED여도 실행하지 않는다.
                if (generation != requestGeneration
                        || !((LifecycleOwner) context).getLifecycle().getCurrentState()
                        .isAtLeast(Lifecycle.State.RESUMED)) {
                    return;
                }
                bindCamera(cameraProviderFuture.get());
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "카메라 시작 오류: ", e); // 에러 로그 기록
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCamera(ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        Preview newPreview = new Preview.Builder().build();
        newPreview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysis.setAnalyzer(analysisExecutor, imageAnalyzer);

        releaseBinding();
        this.cameraProvider = cameraProvider;
        imageAnalysis = analysis;
        preview = newPreview;
        cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, newPreview, analysis);
    }

    public void stopCamera() {
        ++requestGeneration;
        releaseBinding();
    }

    private void releaseBinding() {
        // 중단은 메인 스레드에서 즉시 완료한다. 다른 컨트롤러의 바인딩은 건드리지 않는다.
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
            if (cameraProvider != null) cameraProvider.unbind(imageAnalysis);
            imageAnalysis = null;
        }
        if (preview != null) {
            if (cameraProvider != null) cameraProvider.unbind(preview);
            preview = null;
        }
        cameraProvider = null;
    }
}
