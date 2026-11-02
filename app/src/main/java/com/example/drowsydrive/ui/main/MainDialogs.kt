package com.example.drowsydrive.ui.main

import android.app.Activity
import android.app.Dialog
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.example.drowsydrive.R

/**
 * 안내/종료 다이얼로그를 표시하고, 사용자 선택은 Listener로 전달한다.
 * 위치 조회와 화면 이동은 Activity가 처리한다.
 */
class MainDialogs(private val activity: Activity, private val listener: Listener) {
    interface Listener {
        /** 지도를 선택했다. 처리했으면 true를 반환한다. */
        fun onMapMenuSelected(): Boolean
        fun onBluetoothMenuSelected()
        fun onGuideKeywordSelected(keyword: String)
        fun onExitConfirmed()
    }

    fun showExitDialog() {
        AlertDialog.Builder(activity)
            .setMessage("앱을 종료하시겠습니까?")
            .setPositiveButton("예") { _, _ -> listener.onExitConfirmed() }
            .setNegativeButton("아니요") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    fun showGuideDialog() {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.notification_dialog)
        dialog.setTitle("안내 요청")
        dialog.findViewById<Button>(R.id.btn_no).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.btn_rest_area).setOnClickListener {
            listener.onGuideKeywordSelected(KEYWORD_REST_AREA)
            dialog.dismiss()
        }
        dialog.findViewById<Button>(R.id.btn_rest_stop).setOnClickListener {
            listener.onGuideKeywordSelected(KEYWORD_REST_STOP)
            dialog.dismiss()
        }
        dialog.show()
    }

    companion object {
        const val KEYWORD_REST_AREA = "휴게소"
        const val KEYWORD_REST_STOP = "졸음쉼터"
    }
}
