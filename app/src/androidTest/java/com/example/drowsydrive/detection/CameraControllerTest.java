package com.example.drowsydrive.detection;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CameraControllerTest {
    private static final class ResumedContext extends ContextWrapper implements LifecycleOwner {
        private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);

        ResumedContext(Context base) {
            super(base);
            lifecycle.setCurrentState(Lifecycle.State.RESUMED);
        }

        @Override public Lifecycle getLifecycle() { return lifecycle; }
    }

    /** 준비 완료 순서를 수동으로 제어한다. 실제 카메라 하드웨어는 사용하지 않는다. */
    private static final class DeferredProvider implements ListenableFuture<ProcessCameraProvider> {
        final List<Runnable> listeners = new ArrayList<>();
        int reads;

        @Override public void addListener(Runnable listener, Executor executor) {
            listeners.add(listener);
        }
        @Override public ProcessCameraProvider get() throws ExecutionException {
            reads++;
            throw new ExecutionException(new IllegalStateException("test provider unavailable"));
        }
        @Override public ProcessCameraProvider get(long timeout, TimeUnit unit)
                throws ExecutionException { return get(); }
        @Override public boolean cancel(boolean interrupt) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return false; }
    }

    @Test
    public void stopBeforeProviderReadyInvalidatesStartWithoutSchedulingAnotherCallback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ResumedContext context = new ResumedContext(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            DeferredProvider provider = new DeferredProvider();
            CameraController camera = new CameraController(
                    context, new PreviewView(context), image -> image.close(), Runnable::run, provider);
            camera.startCamera();
            camera.stopCamera();
            camera.stopCamera();
            assertEquals(1, provider.listeners.size());
            provider.listeners.get(0).run();
            assertEquals(0, provider.reads);
        });
    }

    @Test
    public void restartIgnoresOldReadyCallbackEvenWhileResumed() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ResumedContext context = new ResumedContext(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            DeferredProvider provider = new DeferredProvider();
            CameraController camera = new CameraController(
                    context, new PreviewView(context), image -> image.close(), Runnable::run, provider);
            camera.startCamera();
            camera.stopCamera();
            camera.startCamera();
            assertEquals(2, provider.listeners.size());
            provider.listeners.get(0).run();
            assertEquals(0, provider.reads);
            provider.listeners.get(1).run();
            assertEquals(1, provider.reads); // 현재 요청만 프로바이더를 사용한다.
            camera.stopCamera();
        });
    }
}
