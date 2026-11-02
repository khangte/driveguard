package com.example.drowsydrive;

import android.app.Application;
import android.util.Log;

import com.getkeepsafe.relinker.MissingLibraryException;

import com.kakao.vectormap.KakaoMapSdk;

public class DriveGuardApplication extends Application {
    private boolean kakaoMapAvailable;
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            KakaoMapSdk.init(this, BuildConfig.NATIVE_APP_KEY);
            kakaoMapAvailable = true;
        } catch (MissingLibraryException | UnsatisfiedLinkError error) {
            // 지도 네이티브 라이브러리를 로드하지 못해도 메인 화면은 사용할 수 있어야 한다.
            Log.e("DriveGuardApplication", "카카오맵 네이티브 라이브러리를 로드할 수 없습니다.", error);
        }
    }

    public boolean isKakaoMapAvailable() {
        return kakaoMapAvailable;
    }
}
