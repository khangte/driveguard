package com.example.drowsydrive.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Activity의 실제 연결 상태만 화면에 전달한다. 감지 결과나 안전 점수를 추정하지 않는다. */
class MainScreenState {
    var cameraGranted by mutableStateOf(false)
        private set
    var cameraStreaming by mutableStateOf(false)
        private set
    var locationGranted by mutableStateOf(false)
        private set
    var locationAvailable by mutableStateOf(false)
        private set
    /** 검색·지도 진입 전에 정밀 위치를 기다리는 중이면 true. */
    var locationChecking by mutableStateOf(false)
        private set
    var co2Ppm: Int? by mutableStateOf(null)
        private set

    /** 메뉴/안내/권한 다이얼로그. 한 번에 하나만 표시한다. */
    var dialog: MainDialog? by mutableStateOf(null)
        private set

    /** 경고 다이얼로그. 다른 다이얼로그 위에 표시되며 사용자가 끌 때까지 유지된다. */
    var alarm: AlarmAlert? by mutableStateOf(null)
        private set

    /** 디버그 빌드의 측정 로그 상태. null이면 제어 UI를 표시하지 않는다(릴리스). */
    var detectionLog: DetectionLogUiState? by mutableStateOf(null)
        private set

    fun updatePermissions(camera: Boolean, location: Boolean) {
        cameraGranted = camera
        locationGranted = location
        if (!camera) cameraStreaming = false
        if (!location) locationAvailable = false
    }

    fun updateCameraStreaming(streaming: Boolean) {
        cameraStreaming = streaming
    }

    fun updateLocationAvailable(available: Boolean) {
        locationAvailable = available
    }

    fun updateLocationChecking(checking: Boolean) {
        locationChecking = checking
    }

    fun updateCo2(ppm: Int?) {
        co2Ppm = ppm
    }

    fun showDialog(dialog: MainDialog) {
        this.dialog = dialog
    }

    fun dismissDialog() {
        dialog = null
    }

    fun showAlarm(alarm: AlarmAlert) {
        this.alarm = alarm
    }

    fun dismissAlarm() {
        alarm = null
    }

    fun updateDetectionLog(state: DetectionLogUiState?) {
        detectionLog = state
    }
}
