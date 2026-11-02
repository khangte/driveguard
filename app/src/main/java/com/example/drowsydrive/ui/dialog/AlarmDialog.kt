package com.example.drowsydrive.ui.dialog

import android.app.AlertDialog
import android.content.Context

/** 경고 다이얼로그 UI. "알람 끄기"를 누르면 onStop을 호출한다. */
object AlarmDialog {
    @JvmStatic
    fun show(context: Context, title: String, message: String, onStop: Runnable) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("알람 끄기") { _, _ -> onStop.run() }
            .setCancelable(false)
            .show()
    }
}
