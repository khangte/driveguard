package com.example.drowsydrive.alert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReadyGateTest {

    @Test
    public void requestBeforeReady_isDeliveredOnceWhenReady() {
        ReadyGate<String> gate = new ReadyGate<>();
        assertFalse(gate.submit("졸음"));
        assertEquals("졸음", gate.markReady());
        // 이미 넘겨준 요청은 다시 나오지 않는다.
        assertNull(gate.markReady());
    }

    @Test
    public void replacedRequest_onlyLatestIsDelivered() {
        ReadyGate<String> gate = new ReadyGate<>();
        gate.submit("하품");
        gate.submit("졸음");
        assertEquals("졸음", gate.markReady());
    }

    @Test
    public void cancelledRequest_isNotDeliveredWhenReady() {
        ReadyGate<String> gate = new ReadyGate<>();
        gate.submit("하품");
        gate.cancelPending();
        assertNull(gate.markReady());
    }

    @Test
    public void afterReady_requestsRunImmediately() {
        ReadyGate<String> gate = new ReadyGate<>();
        gate.markReady();
        assertTrue(gate.submit("졸음"));
        assertNull(gate.markReady());
    }

    @Test
    public void failedGate_dropsRequestsAndIgnoresLateReady() {
        ReadyGate<String> gate = new ReadyGate<>();
        gate.submit("졸음");
        gate.markFailed();
        assertTrue(gate.isFailed());
        assertFalse(gate.submit("하품"));
        assertNull(gate.markReady());
    }

    @Test
    public void releasedGate_ignoresLateReadyCallback() {
        ReadyGate<String> gate = new ReadyGate<>();
        gate.submit("졸음");
        gate.release();
        assertNull(gate.markReady());
        assertFalse(gate.submit("하품"));
        assertFalse(gate.isFailed());
    }
}
