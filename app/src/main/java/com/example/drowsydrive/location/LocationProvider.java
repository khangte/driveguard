package com.example.drowsydrive.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.kakao.vectormap.LatLng;

/**
 * 현재 위치와 위치 오류를 Listener로 제공한다. 화면을 직접 참조하지 않으며,
 * 화면이 onResume/onPause에서 start()/stop()을 호출해 위치 업데이트 수명을 관리한다.
 * 호출 전 위치 권한을 화면에서 확인해야 한다.
 */
public class LocationProvider {
    private static final String TAG = "LocationProvider";
    private static final long MAX_LOCATION_AGE_MS = 5000; // 5초 이내 위치 정보만 사용

    public interface Listener {
        void onLocationChanged(@NonNull LatLng position);
        void onLocationError(@NonNull String message);
    }

    private final FusedLocationProviderClient fusedLocationClient;
    private final LocationRequest locationRequest;
    private final Listener listener;
    private final LocationCallback locationCallback;
    private CancellationTokenSource currentLocationRequest;
    private boolean requestingLocationUpdates = false;

    public LocationProvider(Context context, Listener listener) {
        this.listener = listener;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context.getApplicationContext());
        this.locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L) // 업데이트 간격 5초
                .setMinUpdateIntervalMillis(2000L) // 최소 업데이트 간격 2초
                .setMaxUpdateDelayMillis(10000L)  // 최대 지연 시간 10초
                .build();
        this.locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location == null) continue;
                    if (System.currentTimeMillis() - location.getTime() < MAX_LOCATION_AGE_MS) {
                        listener.onLocationChanged(toLatLng(location));
                    } else {
                        Log.w(TAG, "오래된 위치 정보 수신, 무시됨.");
                    }
                }
            }
        };
    }

    /** 위치 업데이트를 시작한다. 이미 요청 중이면 무시한다. */
    @SuppressLint("MissingPermission")
    public void start() {
        if (requestingLocationUpdates) return;
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            requestingLocationUpdates = true;
            Log.d(TAG, "위치 업데이트 요청 성공");
        } catch (SecurityException e) {
            Log.e(TAG, "위치 업데이트 요청 실패: 권한 없음", e);
            listener.onLocationError("위치 권한이 필요합니다.");
        }
    }

    /** 위치 업데이트와 진행 중인 현재 위치 요청을 중지한다. */
    public void stop() {
        if (currentLocationRequest != null) {
            currentLocationRequest.cancel();
            currentLocationRequest = null;
        }
        if (!requestingLocationUpdates) return;
        fusedLocationClient.removeLocationUpdates(locationCallback);
        requestingLocationUpdates = false;
        Log.d(TAG, "위치 업데이트 중지 완료");
    }

    /** 현재 위치를 한 번 요청한다. 결과는 onLocationChanged 또는 onLocationError로 전달된다. */
    @SuppressLint("MissingPermission")
    public void requestCurrentLocation() {
        if (currentLocationRequest != null) {
            currentLocationRequest.cancel();
        }
        CancellationTokenSource request = new CancellationTokenSource();
        currentLocationRequest = request;
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, request.getToken())
                    .addOnSuccessListener(location -> {
                        if (request.getToken().isCancellationRequested()) return;
                        if (location != null) {
                            listener.onLocationChanged(toLatLng(location));
                        } else {
                            Log.w(TAG, "현재 위치 정보를 가져오지 못했습니다.");
                            listener.onLocationError("현재 위치를 가져올 수 없습니다.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (request.getToken().isCancellationRequested()) return;
                        Log.e(TAG, "현재 위치 요청 중 오류 발생", e);
                        listener.onLocationError("위치 요청 실패");
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "현재 위치 요청 실패: 권한 없음", e);
            listener.onLocationError("위치 권한이 필요합니다.");
        }
    }

    private static LatLng toLatLng(Location location) {
        return LatLng.from(location.getLatitude(), location.getLongitude());
    }
}
