package com.example.drowsydrive.alert;

/**
 * 비동기로 준비되는 재생 자원(SoundPool 로드, TTS 초기화) 앞에서 요청을 조율한다.
 * 준비 전 요청은 마지막 한 개만 보관하고, 준비 완료 시 한 번 넘겨준다.
 * 스레드 안전하지 않으므로 한 스레드(메인)에서만 사용한다.
 */
final class ReadyGate<T> {
    private enum State { PENDING, READY, FAILED, RELEASED }

    private State state = State.PENDING;
    private T pending;

    /** 준비됐으면 true를 반환해 즉시 실행하게 한다. 준비 중이면 요청을 보관(이전 요청은 교체)하고 false를 반환한다. */
    boolean submit(T request) {
        if (state == State.READY) return true;
        if (state == State.PENDING) pending = request;
        return false;
    }

    /** 준비 완료로 전환하고 보관 중이던 요청을 반환한다. 이미 실패·해제됐거나 보관된 요청이 없으면 null. */
    T markReady() {
        if (state != State.PENDING) return null;
        state = State.READY;
        T request = pending;
        pending = null;
        return request;
    }

    void markFailed() {
        if (state == State.PENDING) state = State.FAILED;
        pending = null;
    }

    /** 경고가 종료·교체되어 보관 중인 요청이 더 이상 유효하지 않을 때 호출한다. */
    void cancelPending() {
        pending = null;
    }

    void release() {
        state = State.RELEASED;
        pending = null;
    }

    /** 아직 준비 완료·실패·해제 중 어느 것도 확정되지 않았으면 true. */
    boolean isPending() {
        return state == State.PENDING;
    }

    boolean isFailed() {
        return state == State.FAILED;
    }
}
