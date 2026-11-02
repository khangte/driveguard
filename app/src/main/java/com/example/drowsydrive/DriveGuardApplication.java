package com.example.drowsydrive;

import android.app.Application;

import com.kakao.vectormap.KakaoMapSdk;

public class DriveGuardApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        KakaoMapSdk.init(this, BuildConfig.NATIVE_APP_KEY); // 카카오 SDK 초기화
    }
}
