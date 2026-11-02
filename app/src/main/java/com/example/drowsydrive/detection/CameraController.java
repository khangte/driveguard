package com.example.drowsydrive.detection;

import android.content.Context;
import android.util.Log;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class CameraController {
    private static final String TAG = "CameraController";

    private final Context context;
    private final PreviewView previewView;
    private final ImageAnalysis.Analyzer imageAnalyzer;

    public CameraController(Context context, PreviewView previewView, ImageAnalysis.Analyzer imageAnalyzer) {
        this.context = context;
        this.previewView = previewView;
        this.imageAnalyzer = imageAnalyzer;
    }

    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCamera(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "카메라 시작 오류: ", e); // 에러 로그 기록
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCamera(ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), imageAnalyzer);

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview, imageAnalysis);
    }

    public void stopCamera() {
        // 카메라 프로바이더를 가져옵니다.
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();  // 모든 바인딩을 해제하여 카메라 리소스를 해제합니다.
                // 추가적인 리소스 정리 작업이 필요하다면 여기에 넣을 수 있습니다.
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "카메라 시작 오류: ", e); // 에러 로그 기록
            }
        }, ContextCompat.getMainExecutor(context));
    }
}