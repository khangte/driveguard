package com.example.drowsydrive.ui.main;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.example.drowsydrive.BuildConfig;
import com.example.drowsydrive.R;
import com.example.drowsydrive.alert.WarningAlarmController;
import com.example.drowsydrive.alert.WarningAlarmController.AlertType;
import com.example.drowsydrive.detection.CameraController;
import com.example.drowsydrive.detection.FaceDetectionManager;
import com.example.drowsydrive.location.LocationProvider;
import com.example.drowsydrive.permission.PermissionManager;
import com.example.drowsydrive.sensor.BluetoothCo2Client;
import com.example.drowsydrive.sensor.Co2AlertPolicy;
import com.example.drowsydrive.ui.dialog.AlarmDialog;
import com.example.drowsydrive.ui.map.KakaoMapSearchLauncher;
import com.example.drowsydrive.ui.map.MapActivity;
import com.kakao.vectormap.LatLng;

public class MainActivity extends AppCompatActivity implements
        FaceDetectionManager.Callback,
        LocationProvider.Listener,
        BluetoothCo2Client.Listener,
        WarningAlarmController.Ui,
        MainDialogs.Listener {
    private static final String TAG = "MainActivity";

    private PermissionManager permissionManager;
    private CameraController cameraController;
    private FaceDetectionManager faceDetectionManager;
    private LocationProvider locationProvider;
    private BluetoothCo2Client bluetoothCo2Client;
    private BluetoothDevicePicker bluetoothDevicePicker;
    private final Co2AlertPolicy co2AlertPolicy = new Co2AlertPolicy();
    private WarningAlarmController warningAlarmController;
    private MainDialogs mainDialogs;

    private TextView co2TextView;
    private LatLng currentPosition; // 최근 수신한 현재 위치

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PreviewView cameraPreviewView = findViewById(R.id.camera_preview);
        co2TextView = findViewById(R.id.co2_text);
        co2TextView.setVisibility(BuildConfig.CO2_SENSOR_ENABLED ? View.VISIBLE : View.GONE);

        permissionManager = new PermissionManager(this);
        faceDetectionManager = new FaceDetectionManager(this);
        cameraController = new CameraController(this, cameraPreviewView, faceDetectionManager);
        locationProvider = new LocationProvider(this, this);
        if (BuildConfig.CO2_SENSOR_ENABLED) {
            bluetoothCo2Client = new BluetoothCo2Client(this);
            bluetoothDevicePicker = new BluetoothDevicePicker(this);
        }
        warningAlarmController = new WarningAlarmController(this, this);
        mainDialogs = new MainDialogs(this, this);

        findViewById(R.id.fab_menu).setOnClickListener(mainDialogs::toggleMenuDialog);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                mainDialogs.showExitDialog();
            }
        });

        // 권한이 모두 있으면 onResume에서 바로 기능을 시작한다.
        permissionManager.requestMissingPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 권한 요청 결과 이후와 설정 화면에서 돌아온 경우 모두 onResume이 호출되므로 여기서 기능을 시작한다.
        if (permissionManager.hasCamera()) {
            cameraController.startCamera();
        }
        if (permissionManager.hasLocation()) {
            locationProvider.start();
        }
        if (BuildConfig.CO2_SENSOR_ENABLED && permissionManager.hasBluetooth()) {
            BluetoothDevice selected = bluetoothDevicePicker.getSelectedDevice();
            if (selected != null) {
                bluetoothCo2Client.start(selected); // 사용자가 저장한 센서에만 재연결
            }
        }
    }

    @Override
    protected void onPause() {
        if (bluetoothDevicePicker != null) {
            bluetoothDevicePicker.dismiss();
        }
        locationProvider.stop();
        cameraController.stopCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (bluetoothCo2Client != null) {
            bluetoothCo2Client.close();
        }
        faceDetectionManager.close();
        warningAlarmController.release();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQUEST_CODE) {
            permissionManager.showDeniedDialogIfNeeded();
        }
    }

    // ---- 졸음 감지 ----

    @Override
    public void onEyesClosed() {
        warningAlarmController.playWarningAlarm(AlertType.DROWSINESS);
    }

    @Override
    public void onYawnDetected() {
        warningAlarmController.playWarningAlarm(AlertType.YAWN);
    }

    // ---- 위치 ----

    @Override
    public void onLocationChanged(@NonNull LatLng position) {
        currentPosition = position;
    }

    @Override
    public void onLocationError(@NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ---- CO2 센서 ----

    @SuppressLint("SetTextI18n")
    @Override
    public void onCo2Received(int co2Ppm) {
        if (!BuildConfig.CO2_SENSOR_ENABLED) return;
        co2TextView.setText("CO2: " + co2Ppm + " ppm");
        if (co2AlertPolicy.shouldAlert(co2Ppm, SystemClock.elapsedRealtime())) {
            warningAlarmController.playWarningAlarm(AlertType.CO2);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onConnectionStateChanged(BluetoothCo2Client.State state) {
        Log.d(TAG, "블루투스 상태: " + state);
        if (state != BluetoothCo2Client.State.CONNECTED) {
            co2TextView.setText("CO2: -- ppm");
        }
    }

    // ---- 경고 UI ----

    @Override
    public void showAlarmDialog(AlertType alertType, Runnable onStop) {
        AlarmDialog.show(this, alertType.title, alertType.message, onStop);
    }

    @Override
    public void showGuideDialog() {
        mainDialogs.showGuideDialog();
    }

    // ---- 메뉴/안내 선택 ----

    @Override
    public void onBluetoothMenuSelected() {
        if (!BuildConfig.CO2_SENSOR_ENABLED) return;
        if (!permissionManager.hasBluetooth()) {
            permissionManager.requestMissingPermissions();
            Toast.makeText(this, R.string.bluetooth_permission_required, Toast.LENGTH_LONG).show();
            return;
        }
        bluetoothDevicePicker.show(device -> {
            bluetoothCo2Client.close();
            bluetoothCo2Client = new BluetoothCo2Client(this);
            bluetoothCo2Client.start(device);
        });
    }

    @Override
    public boolean onMapMenuSelected() {
        if (currentPosition == null) {
            Toast.makeText(this, "위치를 불러오는 중입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
            return false;
        }
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_LATITUDE, currentPosition.getLatitude());
        intent.putExtra(MapActivity.EXTRA_LONGITUDE, currentPosition.getLongitude());
        startActivity(intent);
        return true;
    }

    @Override
    public void onGuideKeywordSelected(String keyword) {
        KakaoMapSearchLauncher.openKakaoMapWithKeyword(this, keyword, currentPosition);
    }

    @Override
    public void onExitConfirmed() {
        finishAffinity();
    }
}
