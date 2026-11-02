package com.example.drowsydrive.ui.main

import com.example.drowsydrive.location.LocationFix

/**
 * 검색·지도 진입처럼 위치가 필요한 동작을 실행하기 전에 보관 중인 위치가 쓸 만한지 확인한다.
 * 최신이고 충분히 정확하면 바로 실행하고, 아니면 고정밀 위치를 한 번 요청한 뒤 그 결과가 같은 기준을 통과할 때만 실행한다.
 *
 * - 저전력 연속 업데이트([onContinuousLocation])는 캐시만 갱신하며 대기 중인 동작을 실행하지 않는다.
 * - 정밀 요청은 번호로 구분해, 취소·교체된 요청의 늦은 결과는 실행·실패 통지·캐시 갱신 모두에서 무시한다.
 * - 대기 중이거나 직전에 실행한 경우 추가 요청을 무시해 요청·화면 실행이 중복되지 않게 한다.
 *
 * 메인 스레드에서만 사용한다. Android 의존성이 없어 단위 테스트로 콜백 순서를 검증할 수 있다.
 */
class PreciseLocationGate<A : Any>(
    private val nowNanos: () -> Long,
    private val requestPrecise: (requestId: Long) -> Unit,
    private val cancelPrecise: () -> Unit,
    private val perform: (action: A, fix: LocationFix) -> Unit,
    private val onFailed: (action: A) -> Unit,
    private val onPendingChanged: (pending: Boolean) -> Unit = {},
) {
    /** 요청 처리 결과. 화면은 Ignored일 때 사용자에게 진행 중임을 알릴 수 있다. */
    enum class Result { PERFORMED, WAITING, IGNORED }

    var latest: LocationFix? = null
        private set
    private var pendingAction: A? = null
    private var pendingRequestId = 0L
    private var nextRequestId = 0L
    private var lastPerformedNanos: Long? = null

    val isPending: Boolean get() = pendingAction != null

    /** 저전력 연속 업데이트 결과. 캐시만 갱신한다. */
    fun onContinuousLocation(fix: LocationFix) = remember(fix)

    fun request(action: A): Result {
        if (pendingAction != null) return Result.IGNORED
        val now = nowNanos()
        lastPerformedNanos?.let { if (now - it < DUPLICATE_WINDOW_NANOS) return Result.IGNORED }
        val cached = latest
        if (cached != null && isUsable(cached, now)) {
            run(action, cached, now)
            return Result.PERFORMED
        }
        val requestId = ++nextRequestId
        pendingAction = action
        pendingRequestId = requestId
        onPendingChanged(true)
        requestPrecise(requestId)
        return Result.WAITING
    }

    /**
     * 정밀 요청 결과. 대기 중인 같은 번호의 요청일 때만 처리한다.
     * 정밀 결과도 캐시와 같은 나이·정확도 기준([isUsable])을 통과해야 실행하며, 통과하지 못하면 실패로 전달한다.
     * 취소·교체·중복 결과와 기준 미달 결과는 캐시를 바꾸지 않는다.
     */
    fun onPreciseLocation(requestId: Long, fix: LocationFix) {
        val action = takePending(requestId) ?: return
        val now = nowNanos()
        if (!isUsable(fix, now)) {
            onFailed(action)
            return
        }
        remember(fix)
        run(action, fix, now)
    }

    fun onPreciseFailed(requestId: Long) {
        val action = takePending(requestId) ?: return
        onFailed(action)
    }

    /** 화면 중단·권한 상실 시 호출한다. 대기 중인 동작과 정밀 요청을 취소한다. */
    fun cancel() {
        if (pendingAction == null) return
        pendingAction = null
        pendingRequestId = 0L
        onPendingChanged(false)
        cancelPrecise()
    }

    private fun takePending(requestId: Long): A? {
        if (requestId != pendingRequestId) return null
        val action = pendingAction ?: return null
        pendingAction = null
        pendingRequestId = 0L
        onPendingChanged(false)
        return action
    }

    private fun run(action: A, fix: LocationFix, now: Long) {
        lastPerformedNanos = now
        perform(action, fix)
    }

    private fun remember(fix: LocationFix) {
        val current = latest
        if (current == null || fix.elapsedRealtimeNanos >= current.elapsedRealtimeNanos) latest = fix
    }

    companion object {
        /** 캐시·정밀 결과 위치를 쓸 수 있는 최대 나이. 실기기 검증 전 실험값이다. */
        const val MAX_AGE_MS = 30_000L
        /** 캐시·정밀 결과 위치를 쓸 수 있는 최대 정확도 반경(m). 두 경로에 같은 기준을 쓴다. 실기기 검증 전 실험값이다. */
        const val MAX_ACCURACY_METERS = 100f
        /** 실행 직후 같은 화면이 다시 실행되지 않도록 추가 요청을 무시하는 시간. */
        const val DUPLICATE_WINDOW_MS = 1_000L
        private const val DUPLICATE_WINDOW_NANOS = DUPLICATE_WINDOW_MS * 1_000_000L

        /** 위치 나이와 정확도가 모두 기준 이내인지 확인한다. 정확도 정보가 없으면 쓸 수 없다. */
        fun isUsable(fix: LocationFix, nowNanos: Long): Boolean =
            fix.ageMillis(nowNanos) <= MAX_AGE_MS &&
                fix.hasAccuracy() && fix.accuracyMeters <= MAX_ACCURACY_METERS
    }
}
