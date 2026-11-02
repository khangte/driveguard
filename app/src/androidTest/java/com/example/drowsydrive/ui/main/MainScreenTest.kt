package com.example.drowsydrive.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun connectionStatusReflectsPreviewStateAndDisabledCo2IsHidden() {
        val state = MainScreenState()
        compose.setContent {
            MaterialTheme {
                MainScreen(state, {}, {}, {}, {}, {}, camera = { Box(androidx.compose.ui.Modifier) })
            }
        }
        compose.onNodeWithText("카메라 권한을 허용해주세요").assertIsDisplayed()
        compose.onNodeWithText("차량 내 CO₂").assertDoesNotExist()
        compose.runOnIdle {
            state.updatePermissions(true, true)
            state.updateCameraStreaming(true)
        }
        compose.onNodeWithText("카메라 연결됨").assertIsDisplayed()
        compose.onNodeWithText("카메라 권한을 허용해주세요").assertDoesNotExist()
    }

    @Test
    fun shortcutsDeliverMapAndSearchActions() {
        val state = MainScreenState().apply {
            updatePermissions(true, true)
            updateLocationAvailable(true)
        }
        var mapClicks = 0
        val searches = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                MainScreen(state, { mapClicks++ }, { searches.add(it) }, {}, {}, {},
                    camera = { Box(androidx.compose.ui.Modifier) })
            }
        }
        compose.onNodeWithText("내 주변 지도 보기").performScrollTo().performClick()
        compose.onNodeWithText("휴게소").performScrollTo().performClick()
        compose.onNodeWithText("졸음 쉼터").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, mapClicks)
            assertEquals(listOf(MainDialogs.KEYWORD_REST_AREA, MainDialogs.KEYWORD_REST_STOP), searches)
        }
    }
}
