package com.example.drowsydrive.ui.main;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.Lifecycle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.example.drowsydrive.BuildConfig;
import com.example.drowsydrive.DriveGuardApplication;
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

    private final MainScreenState screenState = new MainScreenState();
    private LatLng currentPosition; // 최근 수신한 현재 위치

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        permissionManager = new PermissionManager(this);
        faceDetectionManager = new FaceDetectionManager(this);
        locationProvider = new LocationProvider(this, this);
        if (BuildConfig.CO2_SENSOR_ENABLED) {
            bluetoothCo2Client = new BluetoothCo2Client(this);
            bluetoothDevicePicker = new BluetoothDevicePicker(this);
        }
        warningAlarmController = new WarningAlarmController(this, this);
        mainDialogs = new MainDialogs(this, this);

        screenState.updatePermissions(permissionManager.hasCamera(), permissionManager.hasLocation());
        setContentView(MainScreenUi.createView(this, screenState, this,
                this::requestScreenPermissions, mainDialogs::showExitDialog, this::onPreviewReady));
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
        screenState.updatePermissions(permissionManager.hasCamera(), permissionManager.hasLocation());
        if (cameraController != null && permissionManager.hasCamera()) {
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
        if (cameraController != null) cameraController.stopCamera();
        screenState.updateCameraStreaming(false);
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
            screenState.updatePermissions(permissionManager.hasCamera(), permissionManager.hasLocation());
            permissionManager.showDeniedDialogIfNeeded();
        }
    }

    private void onPreviewReady(PreviewView previewView) {
        cameraController = new CameraController(this, previewView, faceDetectionManager);
        previewView.getPreviewStreamState().observe(this, state ->
                screenState.updateCameraStreaming(state == PreviewView.StreamState.STREAMING));
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
                && permissionManager.hasCamera()) {
            cameraController.startCamera();
        }
    }

    private void requestScreenPermissions() {
        if (!permissionManager.requestMissingPermissions()) {
            Toast.makeText(this, R.string.main_permissions_granted, Toast.LENGTH_SHORT).show();
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
        screenState.updateLocationAvailable(true);
    }

    @Override
    public void onLocationError(@NonNull String message) {
        screenState.updateLocationAvailable(false);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ---- CO2 센서 ----

    @Override
    public void onCo2Received(int co2Ppm) {
        if (!BuildConfig.CO2_SENSOR_ENABLED) return;
        screenState.updateCo2(co2Ppm);
        if (co2AlertPolicy.shouldAlert(co2Ppm, SystemClock.elapsedRealtime())) {
            warningAlarmController.playWarningAlarm(AlertType.CO2);
        }
    }

    @Override
    public void onConnectionStateChanged(BluetoothCo2Client.State state) {
        Log.d(TAG, "블루투스 상태: " + state);
        if (state != BluetoothCo2Client.State.CONNECTED) {
            screenState.updateCo2(null);
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
        if (!((DriveGuardApplication) getApplication()).isKakaoMapAvailable()) {
            Toast.makeText(this, R.string.map_native_unavailable, Toast.LENGTH_LONG).show();
            return false;
        }
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
