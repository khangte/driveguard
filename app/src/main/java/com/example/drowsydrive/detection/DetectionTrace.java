package com.example.drowsydrive.detection;

import java.util.ArrayList;
import java.util.List;

/**
 * 관측 1회를 처리하는 동안 DriverMonitor/DrowsinessDetector가 실제로 내린 판정과 초기화 사유를 담는다.
 * 측정 로그를 켰을 때만 만들어지며, 판정은 이 값을 읽지 않는다(진단 전용). 분석 실행기 스레드에서만 쓴다.
 */
final class DetectionTrace {
    // 초기화 사유
    static final String RESET_SESSION_CHANGED = "analysis_session_changed";
    static final String RESET_NO_FACE = "no_face";
    static final String RESET_TARGET_CHANGED = "target_changed";
    static final String RESET_ANALYSIS_FAILURE = "analysis_failure";

    // 대상 선택 사유
    static final String TARGET_KEPT_TRACKING_ID = "kept_tracking_id";
    static final String TARGET_KEPT_SINGLE_UNTRACKED = "kept_single_untracked_face";
    static final String TARGET_NEW = "new_target";
    static final String TARGET_CHANGED = "target_changed";

    // 판정 입력 상태
    static final String INPUT_PRESENT = "present";
    static final String INPUT_MISSING = "missing";
    static final String INPUT_NO_FACE = "no_face";

    /** 판정 상태를 비우기 전에 진행 중이던 에피소드가 있었는지 함께 남긴다. */
    static final class Reset {
        final String reason;
        final boolean eyesWasActive;
        final boolean yawnWasActive;

        Reset(String reason, boolean eyesWasActive, boolean yawnWasActive) {
            this.reason = reason;
            this.eyesWasActive = eyesWasActive;
            this.yawnWasActive = yawnWasActive;
        }
    }

    /** 선택 대상의 눈 감김 또는 하품 판정 결과. */
    static final class Episode {
        String input;
        /** 이번 갱신 직전(대상 선택에 따른 초기화 이후)에 진행 중이던 에피소드가 있었는지 */
        boolean activeBefore;
        DrowsinessDetector.Outcome outcome;
        boolean active;
        boolean alerted;
        /** 진행 중이면 에피소드 시작부터 이번 관측까지의 시간, 진행 중이 아니면 null */
        Long elapsedMs;
        boolean alertFired;
        /** 하품만 사용. 계산하지 못했으면 null */
        Float ratio;

        void capture(String input, boolean activeBefore, DrowsinessDetector.EpisodeSnapshot snapshot,
                     boolean alertFired) {
            this.input = input;
            this.activeBefore = activeBefore;
            this.outcome = snapshot.lastOutcome;
            this.active = snapshot.active;
            this.alerted = snapshot.alerted;
            this.elapsedMs = snapshot.active ? snapshot.lastObservationMs - snapshot.startMs : null;
            this.alertFired = alertFired;
        }

        /** 조건 충족 여부. 값이 없거나 무효라 판단하지 않았으면 null. */
        Boolean conditionMet() {
            if (outcome == null || input == null || !INPUT_PRESENT.equals(input)) return null;
            switch (outcome) {
                case STARTED:
                case GAP_RESTARTED:
                case CONTINUED:
                    return true;
                case CONDITION_NOT_MET:
                case CONDITION_CLEARED:
                    return false;
                default:
                    return null;
            }
        }
    }

    /** 현재 분석 세션의 결과로 판정에 반영됐으면 true. */
    boolean applied;
    int selectedIndex = -1;
    String targetReason;
    final List<Reset> resets = new ArrayList<>();
    final Episode eyes = new Episode();
    final Episode yawn = new Episode();
}
