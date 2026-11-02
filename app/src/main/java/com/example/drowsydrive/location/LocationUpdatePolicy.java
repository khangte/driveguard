package com.example.drowsydrive.location;

import com.google.android.gms.location.Priority;

/**
 * 화면별 연속 위치 업데이트 정책. 두 정책 모두 현재 위치 한 개만 필요하므로 배치 전달을 쓰지 않고,
 * 유효시간은 요청 간격의 2배로 맞춰 정상 주기의 위치가 버려지지 않게 한다.
 */
public enum LocationUpdatePolicy {
    /** 메인 화면: 검색·지도 진입용 좌표만 필요하므로 저전력 정확도로 요청한다. */
    MAIN(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L, 5_000L),
    /** 지도 화면: 현재 위치 마커를 실시간으로 표시하므로 고정밀로 요청한다. */
    MAP(Priority.PRIORITY_HIGH_ACCURACY, 5_000L, 2_000L);

    @Priority
    public final int priority;
    public final long intervalMillis;
    public final long minUpdateIntervalMillis;
    public final long maxLocationAgeMillis;

    LocationUpdatePolicy(@Priority int priority, long intervalMillis, long minUpdateIntervalMillis) {
        this.priority = priority;
        this.intervalMillis = intervalMillis;
        this.minUpdateIntervalMillis = minUpdateIntervalMillis;
        this.maxLocationAgeMillis = intervalMillis * 2;
    }

    /** 같은 단조 증가 시계(elapsedRealtimeNanos)로 측정한 두 시각으로 위치가 유효한지 판정한다. */
    public boolean isFresh(long nowElapsedNanos, long locationElapsedNanos) {
        return nowElapsedNanos - locationElapsedNanos < maxLocationAgeMillis * 1_000_000L;
    }
}
