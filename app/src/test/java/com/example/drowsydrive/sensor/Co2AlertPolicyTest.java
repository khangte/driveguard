package com.example.drowsydrive.sensor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Co2AlertPolicyTest {

    @Test
    public void alertsOnlyAboveThreshold() {
        Co2AlertPolicy policy = new Co2AlertPolicy();
        assertFalse(policy.shouldAlert(2000, 0)); // 2000ppm은 경고 아님
        assertTrue(policy.shouldAlert(2001, 0));
    }

    @Test
    public void firstAlertFiresEvenRightAfterBoot() {
        Co2AlertPolicy policy = new Co2AlertPolicy();
        assertTrue(policy.shouldAlert(2500, 10)); // elapsedRealtime이 작아도 첫 경고는 울린다
    }

    @Test
    public void suppressesRealertWithinOneMinute() {
        Co2AlertPolicy policy = new Co2AlertPolicy();
        assertTrue(policy.shouldAlert(2500, 1000));
        assertFalse(policy.shouldAlert(2500, 60999)); // 59,999ms 경과
        assertTrue(policy.shouldAlert(2500, 61000)); // 60,000ms 경과
        assertFalse(policy.shouldAlert(2500, 61001));
    }

    @Test
    public void normalReadingDoesNotResetInterval() {
        Co2AlertPolicy policy = new Co2AlertPolicy();
        assertTrue(policy.shouldAlert(2500, 0));
        assertFalse(policy.shouldAlert(500, 10000));
        assertFalse(policy.shouldAlert(2500, 20000));
    }
}
