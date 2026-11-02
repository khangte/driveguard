package com.example.drowsydrive.ui.main

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** 화면 테스트는 Kakao 네이티브 SDK와 하드웨어 연결 없이 실행한다. 실제 앱 초기화는 바꾸지 않는다. */
class MainScreenTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, Application::class.java.name, context)
}
