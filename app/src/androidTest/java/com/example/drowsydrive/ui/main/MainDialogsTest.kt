package com.example.drowsydrive.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MainDialogsTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val listener = object : MainDialogs.Listener {
        override fun onMapMenuSelected(): Boolean = true
        override fun onBluetoothMenuSelected() {}
        override fun onSensorSelected(address: String) { events.add("sensor:$address") }
        override fun onGuideKeywordSelected(keyword: String) { events.add("guide:$keyword") }
        override fun onLocationRetry(action: LocationAction) { events.add("retry:$action") }
        override fun onAppSettingsSelected() { events.add("settings") }
        override fun onExitConfirmed() { events.add("exit") }
    }

    private fun host(state: MainScreenState, actions: DetectionLogActions? = null) {
        compose.setContent { MaterialTheme { MainDialogHost(state, listener, actions) } }
    }

    @Test
    fun markerRemainsUsableInsideAlarmAndFollowingGuide() {
        val state = MainScreenState()
        state.updateDetectionLog(DetectionLogUiState(
            state = com.example.drowsydrive.detection.DetectionLogRecorder.State.RECORDING,
            openMarker = "눈 감기",
        ))
        var ends = 0
        var stops = 0
        val actions = DetectionLogActions({}, {}, {
            state.updateDetectionLog(state.detectionLog!!.copy(openMarker = "다음 행동"))
        }, {
            ends++
            state.updateDetectionLog(state.detectionLog!!.copy(openMarker = null))
        })
        state.showAlarm(AlarmAlert("졸음 경고", "휴식을 취하세요!") {
            stops++
            state.showDialog(MainDialog.Guide)
        })
        host(state, actions)
        compose.onNodeWithText("행동 종료").performClick()
        compose.runOnIdle { assertEquals(1, ends); assertEquals(0, stops) }
        compose.onNodeWithText("졸음 경고").assertIsDisplayed()
        compose.onNodeWithText("알람 끄기").performClick()
        compose.onNodeWithText("행동 시작").performClick()
        compose.onNodeWithText("행동 종료").performClick()
        compose.runOnIdle {
            assertEquals(2, ends)
            assertEquals(1, stops)
            assertEquals(MainDialog.Guide, state.dialog)
        }
    }

    @Test
    fun stoppingAlarmClosesItAndRunsCallbackOnce() {
        val state = MainScreenState()
        var stops = 0
        state.showAlarm(AlarmAlert("졸음 경고", "휴식을 취하세요!") { stops++ })
        host(state)
        compose.onNodeWithText("졸음 경고").assertIsDisplayed()
        compose.onNodeWithText("알람 끄기").performClick()
        compose.runOnIdle {
            assertEquals(1, stops)
            assertNull(state.alarm)
        }
        compose.onNodeWithText("졸음 경고").assertDoesNotExist()
    }

    @Test
    fun alarmStopCanOpenGuideAndGuideDeliversKeyword() {
        val state = MainScreenState()
        state.showAlarm(AlarmAlert("하품 경고", "졸음에 주의하세요!") { state.showDialog(MainDialog.Guide) })
        host(state)
        compose.onNodeWithText("알람 끄기").performClick()
        compose.onNodeWithText("졸음 쉼터").performClick()
        compose.runOnIdle {
            assertEquals(listOf("guide:${MainDialogs.KEYWORD_REST_STOP}"), events)
            assertNull(state.dialog)
        }
    }

    @Test
    fun exitAndPermissionDialogsDeliverChoices() {
        val state = MainScreenState()
        state.showDialog(MainDialog.Exit)
        host(state)
        compose.onNodeWithText("아니요").performClick()
        compose.runOnIdle { assertNull(state.dialog); state.showDialog(MainDialog.Exit) }
        compose.onNodeWithText("예").performClick()
        compose.runOnIdle {
            state.showDialog(MainDialog.PermissionSettings(com.example.drowsydrive.R.string.dialog_permission_camera))
        }
        compose.onNodeWithText("설정").performClick()
        compose.runOnIdle { assertEquals(listOf("exit", "settings"), events) }
    }

    @Test
    fun alarmTakesPriorityOverLaterDialogRequests() {
        val state = MainScreenState()
        state.showAlarm(AlarmAlert("졸음 경고", "휴식을 취하세요!") {})
        host(state)
        compose.runOnIdle { state.showDialog(MainDialog.Exit) }
        compose.onNodeWithText("졸음 경고").assertIsDisplayed()
        compose.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()
        compose.onNodeWithText("알람 끄기").performClick()
        compose.onNodeWithText("앱을 종료할까요?").assertIsDisplayed()
        compose.onNodeWithText("아니요").performClick()
        compose.runOnIdle { assertNull(state.dialog) }
    }

    @Test
    fun locationFailedDialogRetriesSameActionOnce() {
        val state = MainScreenState()
        val action = LocationAction.Search(MainDialogs.KEYWORD_REST_AREA)
        state.showDialog(MainDialog.LocationFailed(action))
        host(state)
        compose.onNodeWithText("위치를 확인하지 못했어요").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        compose.runOnIdle {
            assertEquals(listOf("retry:$action"), events)
            assertNull(state.dialog)
        }
    }

    @Test
    fun sensorPickerDeliversSelectedAddress() {
        val state = MainScreenState()
        state.showDialog(MainDialog.SensorPicker(listOf(SensorOption("CO2", "00:11:22:33:44:55"))))
        host(state)
        compose.onNodeWithText("CO2").performClick()
        compose.runOnIdle { assertEquals(listOf("sensor:00:11:22:33:44:55"), events) }
    }
}
