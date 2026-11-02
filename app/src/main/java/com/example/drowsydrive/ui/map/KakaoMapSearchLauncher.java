package com.example.drowsydrive.ui.map;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.kakao.vectormap.LatLng;

/**
 * 외부 카카오맵 앱을 열어 현재 위치 기준으로 키워드를 검색한다.
 */
public final class KakaoMapSearchLauncher {
    private static final String TAG = "KakaoMapSearchLauncher";

    private KakaoMapSearchLauncher() {
    }

    public static void openKakaoMapWithKeyword(Context context, String keyword, LatLng currentPosition) {
        Log.d(TAG, "openKakaoMapWithKeyword 호출됨. 키워드: " + keyword);

        if (currentPosition == null) {
            Log.w(TAG, "현재 위치를 가져올 수 없습니다.");
            Toast.makeText(context, "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 현재 위치 좌표와 함께 카카오맵에서 keyword 검색 결과를 보여주는 URI 스킴
            @SuppressLint("DefaultLocale")
            String url = String.format(
                    "kakaomap://search?q=%s&p=%f,%f",
                    keyword,
                    currentPosition.getLatitude(),
                    currentPosition.getLongitude()
            );
            Log.d(TAG, "카카오맵 URI: " + url);
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            // 카카오맵이 설치되어 있지 않은 경우 Play 스토어로 이동
            Log.e(TAG, "카카오맵이 설치되어 있지 않습니다. Play 스토어로 이동.");
            Toast.makeText(context, "카카오맵이 설치되어 있지 않습니다. Play 스토어로 이동합니다.", Toast.LENGTH_LONG).show();
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=net.daum.android.map")));
        }
    }
}
