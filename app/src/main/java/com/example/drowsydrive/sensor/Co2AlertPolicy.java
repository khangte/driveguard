package com.example.drowsydrive.sensor;

/**
 * CO2 농도 경고 정책: 기준치 초과 시 경고하되, 마지막 경고 후 1분 이내에는 다시 경고하지 않는다.
 */
public final class Co2AlertPolicy {
    static final int WARNING_THRESHOLD_PPM = 2000; // 이 값을 초과하면 위험
    static final long REALERT_INTERVAL_MS = 60000; // 재알림 간격 (1분)

    private boolean hasAlerted = false;
    private long lastAlertMs = 0;

    /** 지금 경고를 울려야 하면 true를 반환하고 마지막 경고 시각을 갱신한다. */
    public boolean shouldAlert(int co2Ppm, long nowMs) {
        if (co2Ppm <= WARNING_THRESHOLD_PPM) {
            return false;
        }
        if (hasAlerted && nowMs - lastAlertMs < REALERT_INTERVAL_MS) {
            return false;
        }
        hasAlerted = true;
        lastAlertMs = nowMs;
        return true;
    }
}
