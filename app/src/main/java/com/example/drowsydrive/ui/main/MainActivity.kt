package com.example.drowsydrive.ui.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.example.drowsydrive.BuildConfig
import com.example.drowsydrive.DriveGuardApplication
import com.example.drowsydrive.R
import com.example.drowsydrive.alert.WarningAlarmController
import com.example.drowsydrive.alert.WarningAlarmController.AlertType
import com.example.drowsydrive.detection.CameraController
import com.example.drowsydrive.detection.DetectionLogRecorder
import com.example.drowsydrive.detection.FaceDetectionManager
import com.example.drowsydrive.location.LocationFix
import com.example.drowsydrive.location.LocationProvider
import com.example.drowsydrive.location.LocationUpdatePolicy
import com.example.drowsydrive.permission.PermissionManager
import com.example.drowsydrive.sensor.BluetoothCo2Client
import com.example.drowsydrive.sensor.Co2AlertPolicy
import com.example.drowsydrive.ui.map.KakaoMapSearchLauncher
import com.example.drowsydrive.ui.map.MapActivity

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
    private lateinit var locationGate: PreciseLocationGate<LocationAction>

    // 개발용 측정 로그. 디버그 빌드에서만 만들며, 릴리스에서는 null이라 파일·스레드가 생기지 않는다.
    private var detectionLogRecorder: DetectionLogRecorder? = null
    private var detectionLogSession: DetectionLogRecorder.Session? = null
    /** 사용자가 수집을 켠 상태. 화면 복귀 시 새 세션으로 다시 시작한다. 프로세스 재시작 시 false. */
    private val detectionLogPreferences by lazy {
        ViewModelProvider(this)[DetectionLogPreferences::class.java]
    }
    private var detectionLogWanted: Boolean
        get() = detectionLogPreferences.wanted
        set(value) { detectionLogPreferences.wanted = value }
    private var detectionLogScenario: String
        get() = detectionLogPreferences.scenario
        set(value) { detectionLogPreferences.scenario = value }
    private var markerSequence = 0L
    private var openMarkerId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        supportActionBar?.hide()
        permissionManager = PermissionManager(this)
        faceDetectionManager = FaceDetectionManager(this, ContextCompat.getMainExecutor(this))
        locationProvider = LocationProvider(this, this, LocationUpdatePolicy.MAIN)
        locationGate = PreciseLocationGate(
            nowNanos = SystemClock::elapsedRealtimeNanos,
            requestPrecise = { requestId -> locationProvider.requestCurrentLocation(preciseLocationListener(requestId)) },
            cancelPrecise = { locationProvider.cancelCurrentLocation() },
            perform = ::performLocationAction,
            onFailed = { action -> screenState.showDialog(MainDialog.LocationFailed(action)) },
            onPendingChanged = screenState::updateLocationChecking,
        )
        if (BuildConfig.CO2_SENSOR_ENABLED) {
            bluetoothCo2Client = BluetoothCo2Client(this)
            bluetoothDevicePicker = BluetoothDevicePicker(this)
        }
        warningAlarmController = WarningAlarmController(this, this)
        if (BuildConfig.DEBUG) setUpDetectionLog()
        screenState.updatePermissions(permissionManager.hasCamera(), permissionManager.hasLocation())
        setContentView(
            MainScreenUi.createView(
                this, screenState, this, ::requestScreenPermissions,
                ::showExitDialog, ::onPreviewReady,
                detectionLogActions = if (BuildConfig.DEBUG) {
                    DetectionLogActions(
                        onStart = ::startDetectionLog,
                        onStop = ::stopDetectionLog,
                        onMarkerStart = ::startMarker,
                        onMarkerEnd = ::endMarker,
                    )
                } else {
                    null
                },
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
        // 재개 시 새 분석 세션으로 시작해 중단 전 판정 상태를 이어 쓰지 않는다.
        faceDetectionManager.start()
        if (BuildConfig.DEBUG && detectionLogWanted) {
            // 이전 세션 파일·타이머에 이어 붙이지 않고 새 수집 세션으로 시작한다.
            if (permissionManager.hasCamera()) {
                openDetectionLog()
            } else {
                detectionLogWanted = false
                updateDetectionLogState { copy(message = getString(R.string.debug_log_need_camera)) }
            }
        }
        if (permissionManager.hasCamera()) cameraController?.startCamera()
        if (permissionManager.hasLocation()) locationProvider.start() else locationGate.cancel()
        if (BuildConfig.CO2_SENSOR_ENABLED && permissionManager.hasBluetooth()) {
            bluetoothDevicePicker?.selectedDevice?.let { bluetoothCo2Client?.start(it) }
        }
    }

    override fun onPause() {
        if (screenState.dialog is MainDialog.SensorPicker) screenState.dismissDialog()
        // 중단 후 늦게 도착한 정밀 위치가 검색 앱이나 지도 화면을 열지 않도록 대기 동작을 먼저 취소한다.
        locationGate.cancel()
        locationProvider.stop()
        // 진행 중인 비동기 분석 결과가 중단 이후 판정/알람에 반영되지 않도록 세션을 먼저 끝낸다.
        faceDetectionManager.stop()
        closeDetectionLog("screen_paused")
        cameraController?.stopCamera()
        screenState.updateCameraStreaming(false)
        super.onPause()
    }

    override fun onDestroy() {
        bluetoothCo2Client?.close()
        cameraController?.stopCamera()
        faceDetectionManager.close()
        warningAlarmController.release()
        // 회전 시 작업자는 유지하되 이전 Activity 참조는 끊는다. 실제 화면 종료는 ViewModel이 해제한다.
        detectionLogRecorder?.setListener(null)
        detectionLogRecorder = null
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
            if (!permissionManager.hasLocation()) locationGate.cancel()
            val deniedMessage = permissionManager.deniedMessageRes()
            if (deniedMessage != 0) screenState.showDialog(MainDialog.PermissionSettings(deniedMessage))
        }
    }

    private fun onPreviewReady(previewView: PreviewView) {
        cameraController = CameraController(
            this, previewView, faceDetectionManager, faceDetectionManager.analysisExecutor,
        )
        previewView.previewStreamState.observe(this) { state ->
            screenState.updateCameraStreaming(state == PreviewView.StreamState.STREAMING)
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && permissionManager.hasCamera()) {
            cameraController?.startCamera()
        }
    }

    private fun setUpDetectionLog() {
        val environment = linkedMapOf<String, Any?>(
            "app_version_name" to BuildConfig.VERSION_NAME,
            "app_version_code" to BuildConfig.VERSION_CODE,
            "build_type" to BuildConfig.BUILD_TYPE,
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "sdk_int" to Build.VERSION.SDK_INT,
            "detection_config" to FaceDetectionManager.detectionConfig(),
        )
        detectionLogRecorder = detectionLogPreferences.recorder
            ?: DetectionLogRecorder.create(applicationContext, environment, ::onDetectionLogStatus).also {
                detectionLogPreferences.recorder = it
            }
        detectionLogRecorder?.setListener(::onDetectionLogStatus)
        screenState.updateDetectionLog(DetectionLogUiState())
        warningAlarmController.setEventListener { event, alertType, activeBefore, trace, relatedAlarmId ->
            detectionLogSession?.recordEvent(
                "alarm", "event", event, "alert", alertType.name, "active_before", activeBefore?.name,
                "alarm_id", trace?.alarmId, "source_session_id", trace?.sourceSessionId,
                "source_observation_id", trace?.sourceObservationId, "related_alarm_id", relatedAlarmId,
            )
        }
    }

    private inline fun updateDetectionLogState(change: DetectionLogUiState.() -> DetectionLogUiState) {
        screenState.detectionLog?.let { screenState.updateDetectionLog(it.change()) }
    }

    private fun startDetectionLog(scenario: String) {
        val blocked = when {
            !permissionManager.hasCamera() -> R.string.debug_log_need_camera
            !screenState.cameraStreaming || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ->
                R.string.debug_log_need_analysis
            else -> 0
        }
        if (blocked != 0) {
            updateDetectionLogState { copy(message = getString(blocked)) }
            return
        }
        detectionLogWanted = true
        detectionLogScenario = scenario
        openDetectionLog()
    }

    private fun stopDetectionLog() {
        detectionLogWanted = false
        closeDetectionLog("user_stop")
    }

    private fun openDetectionLog() {
        val recorder = detectionLogRecorder ?: return
        if (detectionLogSession != null) return
        val session = recorder.start(detectionLogScenario)
        if (session == null) {
            detectionLogWanted = false
            updateDetectionLogState { copy(message = getString(R.string.debug_log_unavailable)) }
            return
        }
        detectionLogSession = session
        openMarkerId = null
        faceDetectionManager.setLogSession(session)
        updateDetectionLogState { copy(scenario = detectionLogScenario, openMarker = null, message = null) }
    }

    private fun closeDetectionLog(reason: String) {
        val session = detectionLogSession ?: return
        faceDetectionManager.setLogSession(null)
        openMarkerId?.let {
            session.recordEvent("marker", "phase", "abandoned", "marker_id", it, "reason", reason)
        }
        openMarkerId = null
        detectionLogSession = null
        detectionLogRecorder?.stop(reason)
        updateDetectionLogState { copy(openMarker = null) }
    }

    private fun startMarker(label: String) {
        val session = detectionLogSession ?: return
        if (openMarkerId != null) return
        val id = ++markerSequence
        val name = label.ifBlank { "marker_$id" }
        openMarkerId = id
        session.recordEvent("marker", "phase", "start", "marker_id", id, "label", name)
        updateDetectionLogState { copy(openMarker = name) }
    }

    private fun endMarker() {
        val session = detectionLogSession ?: return
        val id = openMarkerId ?: return
        session.recordEvent("marker", "phase", "end", "marker_id", id)
        openMarkerId = null
        updateDetectionLogState { copy(openMarker = null) }
    }

    private fun onDetectionLogStatus(status: DetectionLogRecorder.Status) {
        if (status.state == DetectionLogRecorder.State.ERROR) {
            // 기록만 멈추고 감지·알람은 계속한다. 복귀 시 자동으로 다시 시작하지 않는다.
            detectionLogWanted = false
            faceDetectionManager.setLogSession(null)
            detectionLogSession = null
            openMarkerId = null
        }
        updateDetectionLogState {
            copy(
                state = status.state,
                sessionId = status.sessionId,
                fileName = status.fileName,
                recordsWritten = status.recordsWritten,
                dropped = status.observationsDropped,
                message = status.errorMessage
                    ?: message.takeIf { status.state != DetectionLogRecorder.State.ERROR },
                openMarker = if (status.state == DetectionLogRecorder.State.RECORDING) openMarker else null,
            )
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

    override fun onEyesClosed(sourceSessionId: String, observationId: Long) {
        warningAlarmController.playWarningAlarm(AlertType.DROWSINESS, sourceSessionId, observationId)
    }

    override fun onYawnDetected(sourceSessionId: String, observationId: Long) {
        warningAlarmController.playWarningAlarm(AlertType.YAWN, sourceSessionId, observationId)
    }

    override fun onLocationChanged(fix: LocationFix) {
        locationGate.onContinuousLocation(fix)
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

    override fun onMapMenuSelected(): Boolean = requestLocationAction(LocationAction.OpenMap)

    override fun onGuideKeywordSelected(keyword: String) {
        requestLocationAction(LocationAction.Search(keyword))
    }

    override fun onLocationRetry(action: LocationAction) {
        requestLocationAction(action)
    }

    /** 위치가 필요한 동작을 요청한다. 위치가 오래됐거나 부정확하면 정밀 위치를 받은 뒤 실행한다. */
    private fun requestLocationAction(action: LocationAction): Boolean {
        if (action == LocationAction.OpenMap && !(application as DriveGuardApplication).isKakaoMapAvailable) {
            Toast.makeText(this, R.string.map_native_unavailable, Toast.LENGTH_LONG).show()
            return false
        }
        if (!permissionManager.hasLocation()) {
            permissionManager.requestMissingPermissions()
            Toast.makeText(this, R.string.dialog_permission_location, Toast.LENGTH_SHORT).show()
            return false
        }
        return when (locationGate.request(action)) {
            PreciseLocationGate.Result.PERFORMED, PreciseLocationGate.Result.WAITING -> true
            PreciseLocationGate.Result.IGNORED -> {
                if (locationGate.isPending) {
                    Toast.makeText(this, R.string.location_checking_toast, Toast.LENGTH_SHORT).show()
                }
                false
            }
        }
    }

    private fun performLocationAction(action: LocationAction, fix: LocationFix) {
        // 화면을 떠났거나 권한을 잃은 뒤에는 외부 앱·지도 화면을 열지 않는다.
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !permissionManager.hasLocation()) return
        when (action) {
            LocationAction.OpenMap -> startActivity(
                Intent(this, MapActivity::class.java)
                    .putExtra(MapActivity.EXTRA_LATITUDE, fix.latitude)
                    .putExtra(MapActivity.EXTRA_LONGITUDE, fix.longitude),
            )
            is LocationAction.Search ->
                KakaoMapSearchLauncher.openKakaoMapWithKeyword(this, action.keyword, fix.toLatLng())
        }
    }

    /** 정밀 요청 번호를 붙여 결과를 게이트로 전달한다. 취소·교체된 요청의 결과는 게이트가 무시한다. */
    private fun preciseLocationListener(requestId: Long) = object : LocationProvider.Listener {
        override fun onLocationChanged(fix: LocationFix) {
            locationGate.onPreciseLocation(requestId, fix)
        }

        override fun onLocationError(message: String) {
            Log.w(TAG, "정밀 위치 요청 실패: " + message)
            locationGate.onPreciseFailed(requestId)
        }
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
