package com.example.drowsydrive.location;

import androidx.annotation.NonNull;

import com.kakao.vectormap.LatLng;

/** 좌표와 함께 관측 시각(elapsedRealtimeNanos 기준)과 정확도를 보관해, 사용 시점에 유효성을 다시 판정할 수 있게 한다. */
public final class LocationFix {
    public final double latitude;
    public final double longitude;
    /** SystemClock.elapsedRealtimeNanos()와 같은 시계로 측정한 관측 시각. */
    public final long elapsedRealtimeNanos;
    /** 수평 정확도(m, 68% 신뢰 반경). 정보가 없으면 NaN. */
    public final float accuracyMeters;

    public LocationFix(double latitude, double longitude, long elapsedRealtimeNanos, float accuracyMeters) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.elapsedRealtimeNanos = elapsedRealtimeNanos;
        this.accuracyMeters = accuracyMeters;
    }

    public boolean hasAccuracy() {
        return !Float.isNaN(accuracyMeters);
    }

    public long ageMillis(long nowElapsedNanos) {
        return (nowElapsedNanos - elapsedRealtimeNanos) / 1_000_000L;
    }

    @NonNull
    public LatLng toLatLng() {
        return LatLng.from(latitude, longitude);
    }
}
