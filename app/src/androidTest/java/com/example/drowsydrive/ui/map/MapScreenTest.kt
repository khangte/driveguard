package com.example.drowsydrive.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MapScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun errorReplacesLoadingAndOffersReturn() {
        var loading by mutableStateOf(true)
        var error by mutableStateOf<String?>(null)
        var backs = 0
        compose.setContent {
            MaterialTheme { MapScreen(loading, error, false, { backs++ }, {}, {}) }
        }
        compose.onNodeWithText("위치와 지도를 불러오는 중입니다").assertIsDisplayed()
        compose.onNodeWithText("내 위치로").assertDoesNotExist()
        compose.runOnIdle { loading = false; error = "지도 연결 실패" }
        compose.onNodeWithText("위치와 지도를 불러오는 중입니다").assertDoesNotExist()
        compose.onNodeWithText("지도 연결 실패").assertIsDisplayed()
        compose.onNodeWithText("돌아가기").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun readyMapOffersLocationAndBackActions() {
        var locations = 0
        var backs = 0
        compose.setContent {
            MaterialTheme { MapScreen(false, null, true, { backs++ }, { locations++ }, {}) }
        }
        compose.onNodeWithText("내 주변 지도").assertIsDisplayed()
        compose.onNodeWithText("내 위치로", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("돌아가기").performClick()
        compose.runOnIdle { assertEquals(1, locations); assertEquals(1, backs) }
    }
}
