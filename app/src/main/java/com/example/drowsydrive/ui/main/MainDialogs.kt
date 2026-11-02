package com.example.drowsydrive.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.drowsydrive.R

/** 메인 화면에서 띄우는 다이얼로그 종류. */
sealed interface MainDialog {
    data object Exit : MainDialog
    data object Guide : MainDialog
    data class PermissionSettings(@StringRes val message: Int) : MainDialog
    data class SensorPicker(val sensors: List<SensorOption>) : MainDialog
}

/** 경고 다이얼로그 내용. 사용자가 알람을 끄면 onStop을 호출한다. */
class AlarmAlert(val title: String, val message: String, val onStop: () -> Unit)

/** 메인 화면 다이얼로그의 사용자 선택을 Activity로 전달한다. 위치 조회와 화면 이동은 Activity가 처리한다. */
object MainDialogs {
    interface Listener {
        /** 지도를 선택했다. 처리했으면 true를 반환한다. */
        fun onMapMenuSelected(): Boolean
        fun onBluetoothMenuSelected()
        fun onSensorSelected(address: String)
        fun onGuideKeywordSelected(keyword: String)
        fun onAppSettingsSelected()
        fun onExitConfirmed()
    }

    const val KEYWORD_REST_AREA = "휴게소"
    const val KEYWORD_REST_STOP = "졸음쉼터"
}

private val Blocking = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

/** 한 번에 하나의 Material 3 다이얼로그를 표시한다. 경고가 있으면 다른 요청보다 우선한다. */
@Composable
internal fun MainDialogHost(state: MainScreenState, listener: MainDialogs.Listener) {
    val alarm = state.alarm
    if (alarm != null) {
        AlarmDialog(alarm.title, alarm.message, onStop = {
            if (state.alarm === alarm) {
                state.dismissAlarm()
                alarm.onStop()
            }
        })
        return
    }
    val close = state::dismissDialog
    when (val dialog = state.dialog) {
        MainDialog.Exit -> ExitDialog(onConfirm = { close(); listener.onExitConfirmed() }, onDismiss = close)
        MainDialog.Guide -> GuideDialog(onSearch = { close(); listener.onGuideKeywordSelected(it) }, onDismiss = close)
        is MainDialog.PermissionSettings -> PermissionDialog(
            dialog.message, onSettings = { close(); listener.onAppSettingsSelected() }, onDismiss = close)
        is MainDialog.SensorPicker -> SensorPickerDialog(
            dialog.sensors, onSelect = { close(); listener.onSensorSelected(it) }, onDismiss = close)
        null -> Unit
    }
}

@Composable
internal fun AlarmDialog(title: String, message: String, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = {},
        properties = Blocking,
        icon = { Icon(Icons.Default.Warning, null) },
        iconContentColor = colors.error,
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(message, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        confirmButton = {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
            ) { Text(stringResource(R.string.dialog_alarm_stop)) }
        },
        containerColor = colors.errorContainer,
        titleContentColor = colors.onErrorContainer,
        textContentColor = colors.onErrorContainer,
    )
}

@Composable
internal fun GuideDialog(onSearch: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.LocationOn, null) },
        title = { Text(stringResource(R.string.dialog_guide_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.notification_sentence))
                GuideOption(R.string.rest_area, R.string.main_rest_area_description,
                    onClick = { onSearch(MainDialogs.KEYWORD_REST_AREA) })
                GuideOption(R.string.drowsy_shelter, R.string.main_rest_stop_description,
                    onClick = { onSearch(MainDialogs.KEYWORD_REST_STOP) })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) } },
    )
}

@Composable
private fun GuideOption(@StringRes title: Int, @StringRes subtitle: Int, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(stringResource(title)) },
            supportingContent = { Text(stringResource(subtitle)) },
            leadingContent = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
internal fun ExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = Blocking,
        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
        title = { Text(stringResource(R.string.dialog_exit_title)) },
        text = { Text(stringResource(R.string.dialog_exit_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.yes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) } },
    )
}

@Composable
internal fun PermissionDialog(@StringRes message: Int, onSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = Blocking,
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text(stringResource(R.string.dialog_permission_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.dialog_settings))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun SensorPickerDialog(sensors: List<SensorOption>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bluetooth_select_sensor)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(sensors, key = { it.address }) { sensor ->
                    ListItem(
                        headlineContent = { Text(sensor.name) },
                        supportingContent = { Text(sensor.address) },
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(sensor.address) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Alarm")
@Composable
private fun AlarmDialogPreview() {
    DriveGuardTheme(dynamicColor = false) { AlarmDialog("졸음 경고", "졸음이 감지되었습니다.\n휴식을 취하세요!") {} }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Guide")
@Composable
private fun GuideDialogPreview() {
    DriveGuardTheme(dynamicColor = false) { GuideDialog({}, {}) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Exit")
@Composable
private fun ExitDialogPreview() {
    DriveGuardTheme(dynamicColor = false) { ExitDialog({}, {}) }
}
