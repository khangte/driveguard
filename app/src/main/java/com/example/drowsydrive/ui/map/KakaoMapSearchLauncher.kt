package com.example.drowsydrive.ui.map

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.kakao.vectormap.LatLng

/** 외부 카카오맵 앱을 열어 현재 위치 기준으로 키워드를 검색한다. */
object KakaoMapSearchLauncher {
    private const val TAG = "KakaoMapSearchLauncher"

    @JvmStatic
    fun openKakaoMapWithKeyword(context: Context, keyword: String, currentPosition: LatLng?) {
        Log.d(TAG, "openKakaoMapWithKeyword 호출됨. 키워드: $keyword")
        if (currentPosition == null) {
            Log.w(TAG, "현재 위치를 가져올 수 없습니다.")
            Toast.makeText(context, "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // 기존 카카오맵 URI와 검색 동작을 유지한다.
            @SuppressLint("DefaultLocale")
            val url = String.format(
                "kakaomap://search?q=%s&p=%f,%f",
                keyword,
                currentPosition.latitude,
                currentPosition.longitude,
            )
            Log.d(TAG, "카카오맵 URI: $url")
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Log.e(TAG, "카카오맵이 설치되어 있지 않습니다. Play 스토어로 이동.")
            Toast.makeText(context, "카카오맵이 설치되어 있지 않습니다. Play 스토어로 이동합니다.", Toast.LENGTH_LONG).show()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=net.daum.android.map")))
        }
    }
}
