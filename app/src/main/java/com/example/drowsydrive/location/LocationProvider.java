package com.example.drowsydrive.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/**
 * 현재 위치와 위치 오류를 Listener로 제공한다. 화면을 직접 참조하지 않으며,
 * 화면이 onResume/onPause에서 start()/stop()을 호출해 위치 업데이트 수명을 관리한다.
 * 호출 전 위치 권한을 화면에서 확인해야 하며, 메인 스레드에서 호출해야 한다.
 */
public class LocationProvider {
    private static final String TAG = "LocationProvider";
    /**
     * 일회성 정밀 요청이 허용하는 캐시 위치의 최대 나이. 0이면 캐시를 쓰지 않고 새로 측위한다.
     * 정밀 요청은 보관 위치가 오래됐거나 부정확할 때 보내므로, 캐시를 허용하면 방금 거절한 저정밀 위치가
     * 그대로 돌아올 수 있다. 결과의 정확도 검사는 호출 측(PreciseLocationGate)이 별도로 수행한다.
     */
    private static final long CURRENT_LOCATION_MAX_AGE_MS = 0L;
    /** 일회성 정밀 요청의 최대 대기 시간. 초과하면 null 결과로 끝나 실패로 전달된다. */
    private static final long CURRENT_LOCATION_TIMEOUT_MS = 15_000L;

    public interface Listener {
        void onLocationChanged(@NonNull LocationFix fix);
        void onLocationError(@NonNull String message);
    }

    private final FusedLocationProviderClient fusedLocationClient;
    private final LocationUpdatePolicy policy;
    private final LocationRequest locationRequest;
    private final CurrentLocationRequest currentLocationRequestSpec;
    private final Listener listener;
    private final LocationUpdateSessions sessions = new LocationUpdateSessions();
    /** 현재 세션에 등록한 콜백. stop()은 이 콜백만 해제하므로 이전 세션의 해제가 새 세션 등록에 영향을 주지 않는다. */
    private LocationCallback activeCallback;
    private CancellationTokenSource currentLocationRequest;

    public LocationProvider(Context context, Listener listener, LocationUpdatePolicy policy) {
        this.listener = listener;
        this.policy = policy;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context.getApplicationContext());
        this.locationRequest = new LocationRequest.Builder(policy.priority, policy.intervalMillis)
                .setMinUpdateIntervalMillis(policy.minUpdateIntervalMillis)
                .setMaxUpdateDelayMillis(0L) // 현재 위치만 쓰므로 배치 전달하지 않는다.
                .build();
        this.currentLocationRequestSpec = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(CURRENT_LOCATION_MAX_AGE_MS)
                .setDurationMillis(CURRENT_LOCATION_TIMEOUT_MS)
                .build();
    }

    /** 세션 번호를 캡처한 콜백을 만든다. 다른 세션의 결과는 화면에 전달하지 않는다. */
    private LocationCallback newCallback(long session) {
        return new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (!sessions.isCurrent(session)) return;
                // 묶음으로 전달돼도 화면에는 가장 최근 위치 한 개만 넘긴다.
                Location location = locationResult.getLastLocation();
                if (location == null) return;
                LocationFix fix = toFix(location);
                if (policy.isFresh(SystemClock.elapsedRealtimeNanos(), fix.elapsedRealtimeNanos)) {
                    listener.onLocationChanged(fix);
                } else {
                    Log.w(TAG, "오래된 위치 정보 수신, 무시됨.");
                }
            }
        };
    }

    /** 위치 업데이트를 시작한다. 이미 요청 중이면 무시한다. 요청이 실패하면 다음 start()에서 다시 요청할 수 있다. */
    @SuppressLint("MissingPermission")
    public void start() {
        if (sessions.isActive()) return;
        long session = sessions.start();
        LocationCallback callback = newCallback(session);
        activeCallback = callback;
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                    .addOnSuccessListener(unused -> {
                        if (sessions.isCurrent(session)) Log.d(TAG, "위치 업데이트 요청 성공");
                    })
                    .addOnFailureListener(e -> {
                        // 이미 중단·재시작된 이전 세션의 실패는 현재 상태에 반영하지 않는다.
                        if (!sessions.fail(session)) return;
                        if (activeCallback == callback) activeCallback = null;
                        Log.e(TAG, "위치 업데이트 요청 실패", e);
                        listener.onLocationError("위치 업데이트를 시작할 수 없습니다.");
                    });
        } catch (SecurityException e) {
            sessions.fail(session);
            activeCallback = null;
            Log.e(TAG, "위치 업데이트 요청 실패: 권한 없음", e);
            listener.onLocationError("위치 권한이 필요합니다.");
        }
    }

    /** 위치 업데이트와 진행 중인 현재 위치 요청을 중지한다. */
    public void stop() {
        cancelCurrentLocation();
        if (!sessions.stop()) return;
        LocationCallback callback = activeCallback;
        activeCallback = null;
        if (callback == null) return;
        // 해제 실패는 기록만 하고 재시도하지 않는다. 늦게 도착한 결과는 세션 번호로 차단된다.
        fusedLocationClient.removeLocationUpdates(callback)
                .addOnFailureListener(e -> Log.w(TAG, "위치 업데이트 해제 실패", e));
        Log.d(TAG, "위치 업데이트 중지 요청");
    }

    /**
     * 고정밀 현재 위치를 한 번 요청해 target으로 전달한다. 이전 요청은 취소되며,
     * 취소된 요청의 결과는 전달하지 않는다. 시간 초과·실패는 onLocationError로 전달된다.
     */
    @SuppressLint("MissingPermission")
    public void requestCurrentLocation(@NonNull Listener target) {
        cancelCurrentLocation();
        CancellationTokenSource request = new CancellationTokenSource();
        currentLocationRequest = request;
        try {
            fusedLocationClient.getCurrentLocation(currentLocationRequestSpec, request.getToken())
                    .addOnSuccessListener(location -> {
                        if (request.getToken().isCancellationRequested()) return;
                        finishCurrentLocation(request);
                        if (location != null) {
                            target.onLocationChanged(toFix(location));
                        } else {
                            Log.w(TAG, "현재 위치 정보를 가져오지 못했습니다.");
                            target.onLocationError("현재 위치를 가져올 수 없습니다.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (request.getToken().isCancellationRequested()) return;
                        finishCurrentLocation(request);
                        Log.e(TAG, "현재 위치 요청 중 오류 발생", e);
                        target.onLocationError("위치 요청 실패");
                    });
        } catch (SecurityException e) {
            finishCurrentLocation(request);
            Log.e(TAG, "현재 위치 요청 실패: 권한 없음", e);
            target.onLocationError("위치 권한이 필요합니다.");
        }
    }

    /** 진행 중인 일회성 위치 요청을 취소한다. 결과는 전달되지 않는다. */
    public void cancelCurrentLocation() {
        if (currentLocationRequest != null) {
            currentLocationRequest.cancel();
            currentLocationRequest = null;
        }
    }

    private void finishCurrentLocation(CancellationTokenSource request) {
        if (currentLocationRequest == request) currentLocationRequest = null;
    }

    private static LocationFix toFix(Location location) {
        long elapsedNanos = location.getElapsedRealtimeNanos();
        if (elapsedNanos == 0L) {
            // 단조 시각이 없는 위치(일부 모의 위치 등)만 벽시계 나이로 환산한다.
            long ageMillis = System.currentTimeMillis() - location.getTime();
            elapsedNanos = SystemClock.elapsedRealtimeNanos() - ageMillis * 1_000_000L;
        }
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : Float.NaN;
        return new LocationFix(location.getLatitude(), location.getLongitude(), elapsedNanos, accuracy);
    }
}
