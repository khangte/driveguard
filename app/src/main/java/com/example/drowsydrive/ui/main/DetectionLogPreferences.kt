package com.example.drowsydrive.ui.main

import androidx.lifecycle.ViewModel
import com.example.drowsydrive.detection.DetectionLogRecorder

/** 화면 재생성에는 수집 의도와 작업자를 유지하고 새 프로세스에서는 복원하지 않는다. */
class DetectionLogPreferences : ViewModel() {
    var wanted = false
    var scenario = ""

    // 회전 전 세션의 비동기 닫기와 새 세션 열기를 같은 작업자에서 순서대로 처리한다.
    var recorder: DetectionLogRecorder? = null

    override fun onCleared() {
        recorder?.release()
        recorder = null
    }
}
