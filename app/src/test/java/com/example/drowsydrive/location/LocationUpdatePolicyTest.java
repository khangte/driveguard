package com.example.drowsydrive.location;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationUpdatePolicyTest {

    private static final long MS = 1_000_000L;

    @Test
    public void ageIsBoundedByTwiceTheInterval() {
        for (LocationUpdatePolicy policy : LocationUpdatePolicy.values()) {
            long now = 1_000_000 * MS;
            long limit = policy.intervalMillis * 2;
            assertTrue(policy.name(), policy.isFresh(now, now - (limit - 1) * MS));
            assertFalse(policy.name(), policy.isFresh(now, now - limit * MS));
        }
    }

    @Test
    public void locationDeliveredAtNormalInterval_isNotDiscarded() {
        for (LocationUpdatePolicy policy : LocationUpdatePolicy.values()) {
            long now = 1_000_000 * MS;
            assertTrue(policy.name(), policy.isFresh(now, now - policy.intervalMillis * MS));
        }
    }

    @Test
    public void mainScreenRequestsLessFrequentlyThanMap() {
        assertTrue(LocationUpdatePolicy.MAIN.intervalMillis > LocationUpdatePolicy.MAP.intervalMillis);
    }
}
