package com.example.drowsydrive.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import com.example.drowsydrive.BuildConfig
import com.example.drowsydrive.DriveGuardApplication
import com.example.drowsydrive.R
import com.example.drowsydrive.alert.WarningAlarmController
import com.example.drowsydrive.alert.WarningAlarmController.AlertType
import com.example.drowsydrive.detection.CameraController
import com.example.drowsydrive.detection.FaceDetectionManager
import com.example.drowsydrive.location.LocationProvider
import com.example.drowsydrive.permission.PermissionManager
import com.example.drowsydrive.sensor.BluetoothCo2Client
import com.example.drowsydrive.sensor.Co2AlertPolicy
import com.example.drowsydrive.ui.map.KakaoMapSearchLauncher
import com.example.drowsydrive.ui.map.MapActivity
import com.kakao.vectormap.LatLng

class MainActivity : AppCompatActivity(), FaceDetectionManager.Callback, LocationProvider.Listener,
    BluetoothCo2Client.Listener, WarningAlarmController.Ui, MainDialogs.Listener {

    private lateinit var permissionManager: PermissionManager
    private var cameraController: CameraController? = null
    private lateinit var faceDetectionManager: FaceDetectionManager
    private lateinit var locationProvider: LocationProvider
    private var bluetoothCo2Client: BluetoothCo2Client? = null
    private var bluetoothDevicePicker: BluetoothDevicePicker? = null
    private val co2AlertPolicy = Co2AlertPolicy()
    private lateinit var warningAlarmController: WarningAlarmController
    private val screenState = MainScreenState()
    private var currentPosition: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        supportActionBar?.hide()
        permissionManager = PermissionManager(this)
        faceDetectionManager = FaceDetectionManager(this)
        locationProvider = LocationProvider(this, this)
        if (BuildConfig.CO2_SENSOR_ENABLED) {
            bluetoothCo2Client = BluetoothCo2Client(this)
            bluetoothDevicePicker = BluetoothDevicePicker(this)
        }
        warningAlarmController = WarningAlarmController(this, this)
        screenState.updatePermissions(permissionManager.hasCamera(), permissionManager.hasLocation())
        setContentView(
            MainScreenUi.createView(
                this, screenState, this, ::requestScreenPermissions,
                ::showExitDialog, ::onPreviewReady,
            ),
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })
        // 권한이 모두 있으면 onResume에서 바로 기능을 시작한다.
        permissionManager.requestMissingPermissions()
    }

    override fun onResume() {
        super.onResume()
        screenState.updatePermissions(permissionManager.hasCamera(), permissionManager.hasLocation())
        if (permissionManager.hasCamera()) cameraController?.startCamera()
        if (permissionManager.hasLocation()) locationProvider.start()
        if (BuildConfig.CO2_SENSOR_ENABLED && permissionManager.hasBluetooth()) {
            bluetoothDevicePicker?.selectedDevice?.let { bluetoothCo2Client?.start(it) }
        }
    }

    override fun onPause() {
        if (screenState.dialog is MainDialog.SensorPicker) screenState.dismissDialog()
        locationProvider.stop()
        cameraController?.stopCamera()
        screenState.updateCameraStreaming(false)
        super.onPause()
    }

    override fun onDestroy() {
        bluetoothCo2Client?.close()
        faceDetectionManager.close()
        warningAlarmController.release()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.REQUEST_CODE) {
            screenState.updatePermissions(permissionManager.hasCamera(), permissionManager.hasLocation())
            val deniedMessage = permissionManager.deniedMessageRes()
            if (deniedMessage != 0) screenState.showDialog(MainDialog.PermissionSettings(deniedMessage))
        }
    }

    private fun onPreviewReady(previewView: PreviewView) {
        cameraController = CameraController(this, previewView, faceDetectionManager)
        previewView.previewStreamState.observe(this) { state ->
            screenState.updateCameraStreaming(state == PreviewView.StreamState.STREAMING)
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && permissionManager.hasCamera()) {
            cameraController?.startCamera()
        }
    }

    private fun showExitDialog() {
        screenState.showDialog(MainDialog.Exit)
    }

    private fun requestScreenPermissions() {
        if (!permissionManager.requestMissingPermissions()) {
            Toast.makeText(this, R.string.main_permissions_granted, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onEyesClosed() {
        warningAlarmController.playWarningAlarm(AlertType.DROWSINESS)
    }

    override fun onYawnDetected() {
        warningAlarmController.playWarningAlarm(AlertType.YAWN)
    }

    override fun onLocationChanged(position: LatLng) {
        currentPosition = position
        screenState.updateLocationAvailable(true)
    }

    override fun onLocationError(message: String) {
        screenState.updateLocationAvailable(false)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onCo2Received(co2Ppm: Int) {
        if (!BuildConfig.CO2_SENSOR_ENABLED) return
        screenState.updateCo2(co2Ppm)
        if (co2AlertPolicy.shouldAlert(co2Ppm, SystemClock.elapsedRealtime())) {
            warningAlarmController.playWarningAlarm(AlertType.CO2)
        }
    }

    override fun onConnectionStateChanged(state: BluetoothCo2Client.State) {
        Log.d(TAG, "블루투스 상태: $state")
        if (state != BluetoothCo2Client.State.CONNECTED) screenState.updateCo2(null)
    }

    override fun showAlarmDialog(alertType: AlertType, onStop: Runnable) {
        screenState.showAlarm(AlarmAlert(alertType.title, alertType.message, onStop::run))
    }

    override fun showGuideDialog() {
        screenState.showDialog(MainDialog.Guide)
    }

    override fun onBluetoothMenuSelected() {
        if (!BuildConfig.CO2_SENSOR_ENABLED) return
        if (!permissionManager.hasBluetooth()) {
            permissionManager.requestMissingPermissions()
            Toast.makeText(this, R.string.bluetooth_permission_required, Toast.LENGTH_LONG).show()
            return
        }
        bluetoothDevicePicker?.pairedSensors()?.let { screenState.showDialog(MainDialog.SensorPicker(it)) }
    }

    override fun onSensorSelected(address: String) {
        val device = bluetoothDevicePicker?.select(address)
        if (device == null) {
            Toast.makeText(this, R.string.bluetooth_pair_sensor_first, Toast.LENGTH_LONG).show()
            return
        }
        bluetoothCo2Client?.close()
        bluetoothCo2Client = BluetoothCo2Client(this).also { it.start(device) }
    }

    override fun onMapMenuSelected(): Boolean {
        if (!(application as DriveGuardApplication).isKakaoMapAvailable) {
            Toast.makeText(this, R.string.map_native_unavailable, Toast.LENGTH_LONG).show()
            return false
        }
        val position = currentPosition
        if (position == null) {
            Toast.makeText(this, "위치를 불러오는 중입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        startActivity(
            Intent(this, MapActivity::class.java)
                .putExtra(MapActivity.EXTRA_LATITUDE, position.latitude)
                .putExtra(MapActivity.EXTRA_LONGITUDE, position.longitude),
        )
        return true
    }

    override fun onGuideKeywordSelected(keyword: String) {
        KakaoMapSearchLauncher.openKakaoMapWithKeyword(this, keyword, currentPosition)
    }

    override fun onAppSettingsSelected() {
        permissionManager.openAppSettings()
    }

    override fun onExitConfirmed() {
        finishAffinity()
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
