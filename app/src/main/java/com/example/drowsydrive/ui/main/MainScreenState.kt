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
    var co2Ppm: Int? by mutableStateOf(null)
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

    fun updateCo2(ppm: Int?) {
        co2Ppm = ppm
    }
}
