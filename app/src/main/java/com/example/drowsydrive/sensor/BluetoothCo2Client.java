package com.example.drowsydrive.sensor;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/**
 * 화면에서 선택한 아두이노(HC-05/06 등 SPP)와의 연결/수신/종료만 담당한다.
 * 블로킹 연결과 수신은 작업 스레드에서 수행하고, CO2 값과 연결 상태는 메인 스레드 콜백으로 전달한다.
 * 호출 전 BLUETOOTH_CONNECT 권한(Android 12+)을 화면에서 확인해야 한다.
 */
public class BluetoothCo2Client {
    private static final String TAG = "BluetoothCo2Client";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public enum State { CONNECTING, CONNECTED, DISCONNECTED }

    public interface Listener {
        void onCo2Received(int co2Ppm);
        void onConnectionStateChanged(State state);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;

    private Thread worker;
    private BluetoothSocket socket;
    private volatile boolean closed = false;

    public BluetoothCo2Client(Listener listener) {
        this.listener = listener;
    }

    /** 연결 중이 아니면 연결을 시작한다. 이미 연결 중이거나 close() 이후면 무시한다. */
    public synchronized void start(BluetoothDevice device) {
        Objects.requireNonNull(device, "device");
        if (closed || worker != null) {
            return;
        }
        worker = new Thread(() -> connectAndListen(device), TAG);
        worker.start();
    }

    /** 소켓을 닫아 블로킹 연결/수신을 깨우고 스레드를 정리한다. 이후 콜백은 전달하지 않는다. */
    public void close() {
        BluetoothSocket socketToClose;
        Thread workerToStop;
        synchronized (this) {
            closed = true;
            socketToClose = socket;
            workerToStop = worker;
        }
        closeQuietly(socketToClose);
        if (workerToStop != null) {
            workerToStop.interrupt();
        }
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "블루투스 연결 종료");
    }

    @SuppressLint("MissingPermission")
    private void connectAndListen(BluetoothDevice device) {
        BluetoothSocket connectingSocket = null;
        try {
            Log.d(TAG, "선택한 센서에 연결 시도 중");
            deliverState(State.CONNECTING);

            connectingSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            synchronized (this) {
                if (closed) {
                    return;
                }
                socket = connectingSocket;
            }
            connectingSocket.connect();
            deliverState(State.CONNECTED);
            Log.d(TAG, "선택한 센서에 연결 성공");

            Co2Parser parser = new Co2Parser();
            InputStream inputStream = connectingSocket.getInputStream();
            byte[] buffer = new byte[1024];
            int bytes;
            while (!closed && (bytes = inputStream.read(buffer)) != -1) {
                for (int co2Ppm : parser.feed(buffer, bytes)) {
                    Log.d(TAG, "수신된 CO2 농도: " + co2Ppm + " ppm");
                    mainHandler.post(() -> {
                        if (!closed) listener.onCo2Received(co2Ppm);
                    });
                }
            }
        } catch (IOException | SecurityException e) {
            if (!closed) {
                Log.e(TAG, "블루투스 연결/수신 오류", e);
            }
        } finally {
            closeQuietly(connectingSocket);
            synchronized (this) {
                socket = null;
                worker = null; // 연결이 끊기면 다음 start()에서 다시 연결할 수 있다.
            }
            deliverState(State.DISCONNECTED);
        }
    }

    private void deliverState(State state) {
        mainHandler.post(() -> {
            if (!closed) listener.onConnectionStateChanged(state);
        });
    }

    private static void closeQuietly(BluetoothSocket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "소켓 해제 중 오류 발생", e);
        }
    }
}
