package com.example.drowsydrive.location;

/**
 * 연속 위치 업데이트 요청을 세션 단위로 구분한다. start()마다 새 세션 번호를 발급하고,
 * 콜백은 발급받은 번호가 현재 활성 세션일 때만 결과를 반영한다.
 * 스레드 안전하지 않으므로 메인 스레드에서만 사용한다.
 */
final class LocationUpdateSessions {
    private long current = 0;
    private boolean active = false;

    /** 새 세션을 시작하고 번호를 반환한다. */
    long start() {
        active = true;
        return ++current;
    }

    /** 활성 세션을 끝낸다. 끝낼 세션이 있었으면 true. 이후 이전 세션의 모든 콜백은 무시된다. */
    boolean stop() {
        if (!active) return false;
        active = false;
        current++;
        return true;
    }

    /** 요청 실패를 반영한다. 현재 세션의 실패여서 상태를 바꿨으면 true, 이미 끝난 세션이면 false. */
    boolean fail(long session) {
        if (!isCurrent(session)) return false;
        active = false;
        return true;
    }

    boolean isActive() {
        return active;
    }

    boolean isCurrent(long session) {
        return active && session == current;
    }
}
