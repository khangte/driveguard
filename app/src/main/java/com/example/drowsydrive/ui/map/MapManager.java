package com.example.drowsydrive.ui.map;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.drowsydrive.R;
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelLayer;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.label.LabelManager;
import com.kakao.vectormap.label.TrackingManager;

public class MapManager {
    private static final String TAG = "MapManager";

    public interface Listener {
        void onMapReady();
        void onMapError(Exception error);
        void onMapDestroyed();
    }

    private final MapView mapView;
    private final Listener listener;
    private KakaoMap kakaoMap; // KakaoMap 인스턴스
    private Label centerLabel; // 현재 위치를 표시하는 레이블

    private boolean started = false; // mapView.start()는 한 번만 호출한다.

    public MapManager(MapView mapView, Listener listener) {
        this.mapView = mapView;
        this.listener = listener;
    }

    // 지도를 초기화하는 메서드
    public void initializeMap(LatLng startPosition, int startZoomLevel) {
        if (started) return;
        started = true;
        mapView.start(new MapLifeCycleCallback() { // 지도 생명주기 콜백 설정
            @Override
            public void onMapDestroy() {
                Log.d(TAG, "onMapDestroy"); // 지도 파괴 로그
                listener.onMapDestroyed();
            }

            @Override
            public void onMapError(Exception error) {
                Log.e(TAG, "onMapError", error); // 오류 로그
                listener.onMapError(error);
            }
        }, new KakaoMapReadyCallback() { // 지도 준비 완료 콜백 설정
            @Override
            public void onMapReady(@NonNull KakaoMap map) {
                kakaoMap = map; // KakaoMap 인스턴스 저장
                listener.onMapReady();

                addCurrentLocationLabel(startPosition);
            }

            @NonNull
            @Override
            public LatLng getPosition() {
                return startPosition; // 시작 위치 반환
            }

            @Override
            public int getZoomLevel() {
                return startZoomLevel; // 시작 줌 레벨 반환
            }
        });
    }

    private void addCurrentLocationLabel(LatLng position) {
        LabelManager labelManager = kakaoMap.getLabelManager();
        if (labelManager == null) return;
        LabelLayer layer = labelManager.getLayer();
        if (layer == null) return;

        centerLabel = layer.addLabel(LabelOptions.from("centerLabel", position)
                .setStyles(LabelStyle.from(R.drawable.current_location).setAnchorPoint(0.5f, 0.5f))
                .setRank(1));
        TrackingManager trackingManager = kakaoMap.getTrackingManager();
        if (centerLabel != null && trackingManager != null) {
            trackingManager.startTracking(centerLabel);
        }
        kakaoMap.setOnCameraMoveStartListener((map, gestureType) -> {
            TrackingManager manager = map.getTrackingManager();
            if (manager != null) {
                manager.stopTracking();
            }
        });
    }

    public boolean isStarted() {
        return started;
    }

    public void onResume() {
        mapView.resume();     // MapView 의 resume 호출
    }

    public void onPause() {
        mapView.pause();    // MapView 의 pause 호출
    }

    public void onDestroy() {
        if (mapView != null) {
            mapView.destroyDrawingCache(); // MapView 자원 해제
        }
    }

    // 레이블의 위치를 업데이트하는 메서드
    public void updateLabelPosition(LatLng newPosition) {
        if (centerLabel != null) {
            centerLabel.moveTo(newPosition); // 레이블을 새로운 위치로 이동
        }
    }

    // 현재 위치로 카메라 이동하는 메서드
    public void moveCameraToCurrentLocation(LatLng currentPosition) {
        if (kakaoMap != null) {
            kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(currentPosition)); // 현재 위치로 카메라 이동
        } else {
            Log.e(TAG, "KakaoMap is null"); // KakaoMap이 null인 경우 로그
        }
    }
}
