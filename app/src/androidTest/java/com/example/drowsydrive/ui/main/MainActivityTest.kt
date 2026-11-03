package com.example.drowsydrive.ui.main

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.rule.GrantPermissionRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import com.example.drowsydrive.DriveGuardApplication
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** 실제 Application과 Kotlin Activity의 연결 및 네이티브 지도 실패 시 동작을 검증한다. */
class MainActivityTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        GrantPermissionRule.grant(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
    ).around(compose)

    @Test
    fun recreationRetainsCollectionIntentAndSingleRecorder() {
        lateinit var retained: DetectionLogPreferences
        compose.activityRule.scenario.onActivity {
            retained = ViewModelProvider(it)[DetectionLogPreferences::class.java]
            retained.wanted = true
            retained.scenario = "eyes_closed_2s"
        }
        val recorder = retained.recorder
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity {
            val current = ViewModelProvider(it)[DetectionLogPreferences::class.java]
            assertSame(retained, current)
            assertSame(recorder, current.recorder)
            assertTrue(current.wanted)
            assertEquals("eyes_closed_2s", current.scenario)
        }
        compose.onNodeWithText("수집 중").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun launcherShowsMainScreen() {
        compose.onNodeWithText("Drive Guard").assertIsDisplayed()
        compose.onNodeWithText("운전자 모니터링").assertIsDisplayed()
    }

    @Test
    fun unavailableNativeMapKeepsMainScreenOpen() {
        assumeFalse((compose.activity.application as DriveGuardApplication).isKakaoMapAvailable)
        compose.onNodeWithText("내 주변 지도 보기").performScrollTo().performClick()
        compose.onNodeWithText("Drive Guard").assertIsDisplayed()
        compose.onNodeWithText("내 주변 지도 보기").assertIsDisplayed()
    }
}
