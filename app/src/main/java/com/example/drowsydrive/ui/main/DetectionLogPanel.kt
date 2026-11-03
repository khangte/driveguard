package com.example.drowsydrive.ui.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drowsydrive.R
import com.example.drowsydrive.detection.DetectionLogRecorder

/** 디버그 빌드의 측정 로그 수집 상태. 릴리스에서는 만들지 않는다. */
data class DetectionLogUiState(
    val state: DetectionLogRecorder.State = DetectionLogRecorder.State.STOPPED,
    val scenario: String = "",
    val sessionId: String? = null,
    val fileName: String? = null,
    val recordsWritten: Long = 0,
    val dropped: Long = 0,
    val message: String? = null,
    /** 진행 중인 행동 마커 이름. 없으면 null. */
    val openMarker: String? = null,
)

/** 측정 로그 제어 동작. */
class DetectionLogActions(
    val onStart: (scenario: String) -> Unit,
    val onStop: () -> Unit,
    val onMarkerStart: (label: String) -> Unit,
    val onMarkerEnd: () -> Unit,
)

private val SCENARIO_PRESETS = listOf(
    "front_open_blink", "eyes_closed_0.5s", "eyes_closed_1s", "eyes_closed_2s",
    "look_left_right", "head_down", "talking", "smiling", "yawning", "face_covered", "multiple_faces",
)

@Composable
internal fun DetectionLogPanel(state: DetectionLogUiState, actions: DetectionLogActions) {
    val recording = state.state == DetectionLogRecorder.State.RECORDING
    var scenario by rememberSaveable { mutableStateOf(state.scenario) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.debug_log_title), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        when (state.state) {
                            DetectionLogRecorder.State.RECORDING -> R.string.debug_log_state_recording
                            DetectionLogRecorder.State.ERROR -> R.string.debug_log_state_error
                            DetectionLogRecorder.State.STOPPED -> R.string.debug_log_state_stopped
                        },
                    ),
                    color = if (state.state == DetectionLogRecorder.State.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            // 시나리오는 수집 중에 바꾸지 않는다.
            OutlinedTextField(
                value = if (recording) state.scenario else scenario,
                onValueChange = { scenario = it },
                enabled = !recording,
                singleLine = true,
                label = { Text(stringResource(R.string.debug_log_scenario)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!recording) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SCENARIO_PRESETS.forEach { preset ->
                        AssistChip(onClick = { scenario = preset }, label = { Text(preset) })
                    }
                }
            }
            state.sessionId?.let {
                Text(stringResource(R.string.debug_log_file, state.fileName ?: it),
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.debug_log_counts, state.recordsWritten, state.dropped),
                    style = MaterialTheme.typography.bodySmall)
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (recording) {
                OutlinedButton(onClick = actions.onStop) { Text(stringResource(R.string.debug_log_stop)) }
                DetectionMarkerControls(state, actions)
            } else {
                Button(onClick = { actions.onStart(scenario.trim()) }) {
                    Text(stringResource(R.string.debug_log_start))
                }
            }
            Text(stringResource(R.string.debug_log_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 메인 화면과 모달 경고·안내창에서 같은 마커 동작을 제공한다. */
@Composable
internal fun DetectionMarkerControls(state: DetectionLogUiState, actions: DetectionLogActions) {
    var markerLabel by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = markerLabel,
            onValueChange = { markerLabel = it },
            enabled = state.openMarker == null,
            singleLine = true,
            label = { Text(stringResource(R.string.debug_log_marker_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { actions.onMarkerStart(markerLabel) }, enabled = state.openMarker == null) {
                Text(stringResource(R.string.debug_log_marker_start))
            }
            OutlinedButton(onClick = actions.onMarkerEnd, enabled = state.openMarker != null) {
                Text(stringResource(R.string.debug_log_marker_end))
            }
        }
        state.openMarker?.let {
            Text(stringResource(R.string.debug_log_marker_open, it), style = MaterialTheme.typography.bodySmall)
        }
    }
}
