package com.example.drowsydrive.ui.main

import com.example.drowsydrive.location.LocationFix
import com.example.drowsydrive.ui.main.PreciseLocationGate.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 정밀 요청·외부 실행을 기록하는 가짜 대역으로 검색·지도 진입 흐름의 콜백 순서를 검증한다. */
class PreciseLocationGateTest {
    private var now = 1_000_000L * MS
    private val preciseRequests = mutableListOf<Long>()
    private var cancels = 0
    private val performed = mutableListOf<Pair<String, LocationFix>>()
    private val failed = mutableListOf<String>()
    private val pendingStates = mutableListOf<Boolean>()
    private lateinit var gate: PreciseLocationGate<String>

    @Before
    fun setUp() {
        gate = PreciseLocationGate(
            nowNanos = { now },
            requestPrecise = { preciseRequests += it },
            cancelPrecise = { cancels++ },
            perform = { action, fix -> performed += action to fix },
            onFailed = { failed += it },
            onPendingChanged = { pendingStates += it },
        )
    }

    private fun fix(ageMs: Long, accuracy: Float = 10f, lat: Double = 37.0) =
        LocationFix(lat, 127.0, now - ageMs * MS, accuracy)

    @Test
    fun freshAccurateCache_performsWithoutPreciseRequest() {
        val cached = fix(ageMs = 5_000)
        gate.onContinuousLocation(cached)
        assertEquals(Result.PERFORMED, gate.request("search"))
        assertEquals(listOf("search" to cached), performed)
        assertTrue(preciseRequests.isEmpty())
    }

    @Test
    fun staleCache_requestsPreciseThenPerformsOnceWithResult() {
        gate.onContinuousLocation(fix(ageMs = PreciseLocationGate.MAX_AGE_MS + 1))
        assertEquals(Result.WAITING, gate.request("search"))
        assertEquals(1, preciseRequests.size)
        val precise = fix(ageMs = 0, lat = 37.5)
        gate.onPreciseLocation(preciseRequests[0], precise)
        assertEquals(listOf("search" to precise), performed)
        assertEquals(listOf(true, false), pendingStates)
    }

    @Test
    fun inaccurateCache_requestsPrecise() {
        gate.onContinuousLocation(fix(ageMs = 0, accuracy = PreciseLocationGate.MAX_ACCURACY_METERS + 1))
        assertEquals(Result.WAITING, gate.request("map"))
        assertEquals(1, preciseRequests.size)
    }

    @Test
    fun cacheWithoutAccuracy_requestsPrecise() {
        gate.onContinuousLocation(fix(ageMs = 0, accuracy = Float.NaN))
        assertEquals(Result.WAITING, gate.request("map"))
    }

    @Test
    fun noCache_requestsPreciseThenPerforms() {
        assertEquals(Result.WAITING, gate.request("map"))
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0))
        assertEquals(1, performed.size)
    }

    @Test
    fun preciseFailure_reportsFailureWithoutPerforming() {
        gate.request("search")
        gate.onPreciseFailed(preciseRequests[0])
        assertTrue(performed.isEmpty())
        assertEquals(listOf("search"), failed)
        assertFalse(gate.isPending)
    }

    @Test
    fun stalePreciseResult_reportsFailureWithoutPerforming() {
        gate.request("search")
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = PreciseLocationGate.MAX_AGE_MS + 1))
        assertTrue(performed.isEmpty())
        assertEquals(listOf("search"), failed)
    }

    @Test
    fun retryAfterFailure_startsNewPreciseRequest() {
        gate.request("search")
        gate.onPreciseFailed(preciseRequests[0])
        assertEquals(Result.WAITING, gate.request("search"))
        assertEquals(2, preciseRequests.size)
    }

    @Test
    fun repeatedTapsWhileWaiting_doNotDuplicateRequestOrPerform() {
        gate.request("search")
        assertEquals(Result.IGNORED, gate.request("search"))
        assertEquals(Result.IGNORED, gate.request("map"))
        assertEquals(1, preciseRequests.size)
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0))
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0))
        assertEquals(1, performed.size)
    }

    @Test
    fun repeatedTapsRightAfterPerform_areIgnored() {
        gate.onContinuousLocation(fix(ageMs = 0))
        gate.request("map")
        now += 500 * MS
        assertEquals(Result.IGNORED, gate.request("map"))
        now += PreciseLocationGate.DUPLICATE_WINDOW_MS * MS
        assertEquals(Result.PERFORMED, gate.request("map"))
        assertEquals(2, performed.size)
    }

    @Test
    fun lateResultAfterCancel_doesNotPerform() {
        gate.request("search")
        gate.cancel()
        assertEquals(1, cancels)
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0))
        gate.onPreciseFailed(preciseRequests[0])
        assertTrue(performed.isEmpty())
        assertTrue(failed.isEmpty())
    }

    @Test
    fun resultOfReplacedRequest_doesNotPerformNewAction() {
        gate.request("search")
        gate.cancel()
        gate.request("map")
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0))
        assertTrue(performed.isEmpty())
        val result = fix(ageMs = 0, lat = 38.0)
        gate.onPreciseLocation(preciseRequests[1], result)
        assertEquals(listOf("map" to result), performed)
    }

    @Test
    fun continuousLocationWhileWaiting_doesNotPerformOrClearPending() {
        gate.request("search")
        gate.onContinuousLocation(fix(ageMs = 0))
        assertTrue(performed.isEmpty())
        assertTrue(gate.isPending)
        val precise = fix(ageMs = 0, lat = 37.7)
        gate.onPreciseLocation(preciseRequests[0], precise)
        assertEquals(listOf("search" to precise), performed)
    }

    @Test
    fun olderLocation_doesNotOverwriteNewerCache() {
        val newer = fix(ageMs = 1_000)
        gate.onContinuousLocation(newer)
        gate.onContinuousLocation(fix(ageMs = 5_000))
        assertEquals(newer, gate.latest)
    }

    @Test
    fun cancelWithoutPending_doesNotCancelProvider() {
        gate.cancel()
        assertEquals(0, cancels)
    }

    // D. 정밀 결과에도 나이·정확도 기준을 적용한다.

    @Test
    fun inaccuratePreciseResult_failsWithoutPerforming() {
        gate.request("search")
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0, accuracy = 500f))
        assertTrue(performed.isEmpty())
        assertEquals(listOf("search"), failed)
        assertFalse(gate.isPending)
    }

    @Test
    fun preciseResultWithoutAccuracy_failsWithoutPerforming() {
        gate.request("map")
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0, accuracy = Float.NaN))
        assertTrue(performed.isEmpty())
        assertEquals(listOf("map"), failed)
    }

    @Test
    fun rejectedCacheReturnedAsPreciseResult_isNotPerformed() {
        val rejected = fix(ageMs = 0, accuracy = 300f)
        gate.onContinuousLocation(rejected)
        assertEquals(Result.WAITING, gate.request("search"))
        gate.onPreciseLocation(preciseRequests[0], rejected)
        assertTrue(performed.isEmpty())
        assertEquals(listOf("search"), failed)
    }

    @Test
    fun preciseResultAtAccuracyBoundary_performs() {
        gate.request("search")
        val boundary = fix(ageMs = 0, accuracy = PreciseLocationGate.MAX_ACCURACY_METERS)
        gate.onPreciseLocation(preciseRequests[0], boundary)
        assertEquals(listOf("search" to boundary), performed)
        assertEquals(boundary, gate.latest)
    }

    @Test
    fun preciseResultJustOverAccuracyBoundary_fails() {
        gate.request("search")
        gate.onPreciseLocation(
            preciseRequests[0],
            fix(ageMs = 0, accuracy = Math.nextUp(PreciseLocationGate.MAX_ACCURACY_METERS)),
        )
        assertTrue(performed.isEmpty())
        assertEquals(listOf("search"), failed)
    }

    @Test
    fun preciseResultAtAgeBoundary_performs() {
        gate.request("search")
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = PreciseLocationGate.MAX_AGE_MS))
        assertEquals(1, performed.size)
    }

    @Test
    fun retryAfterInaccurateResult_performsWithValidResult() {
        gate.request("search")
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0, accuracy = 500f))
        assertEquals(Result.WAITING, gate.request("search"))
        val valid = fix(ageMs = 0, lat = 37.3)
        gate.onPreciseLocation(preciseRequests[1], valid)
        assertEquals(listOf("search" to valid), performed)
        assertEquals(listOf("search"), failed)
    }

    // E. 현재 요청의 유효한 결과만 캐시에 반영한다.

    @Test
    fun lateResultAfterCancel_keepsCache() {
        val cached = fix(ageMs = PreciseLocationGate.MAX_AGE_MS + 1)
        gate.onContinuousLocation(cached)
        gate.request("search")
        gate.cancel()
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0, lat = 38.0))
        assertTrue(performed.isEmpty())
        assertTrue(failed.isEmpty())
        assertEquals(cached, gate.latest)
    }

    @Test
    fun replacedRequestResult_keepsPendingAndCache() {
        val cached = fix(ageMs = PreciseLocationGate.MAX_AGE_MS + 1)
        gate.onContinuousLocation(cached)
        gate.request("search")
        gate.cancel()
        gate.request("map")
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0, lat = 38.0))
        assertTrue(gate.isPending)
        assertEquals(cached, gate.latest)
        assertTrue(performed.isEmpty())
    }

    @Test
    fun duplicateResultOfHandledRequest_doesNotChangeCache() {
        gate.request("search")
        val first = fix(ageMs = 0, lat = 37.1)
        gate.onPreciseLocation(preciseRequests[0], first)
        now += 1 * MS
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0, lat = 38.0))
        assertEquals(first, gate.latest)
        assertEquals(1, performed.size)
    }

    @Test
    fun validCurrentResult_isPerformedOnceAndCached() {
        gate.request("map")
        val precise = fix(ageMs = 0, lat = 37.9)
        gate.onPreciseLocation(preciseRequests[0], precise)
        assertEquals(listOf("map" to precise), performed)
        assertEquals(precise, gate.latest)
    }

    @Test
    fun unusableCurrentResult_failsAndKeepsCache() {
        val cached = fix(ageMs = 1_000, accuracy = 300f)
        gate.onContinuousLocation(cached)
        gate.request("search")
        gate.onPreciseLocation(preciseRequests[0], fix(ageMs = 0, accuracy = 500f, lat = 38.0))
        assertEquals(listOf("search"), failed)
        assertEquals(cached, gate.latest)
    }

    private companion object {
        const val MS = 1_000_000L
    }
}
