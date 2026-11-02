package com.example.drowsydrive.ui.main

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.drowsydrive.BuildConfig
import com.example.drowsydrive.R

/** Activity와 Compose 사이의 진입점. 카메라는 AndroidView에서 한 번 생성한다. */
object MainScreenUi {
    fun createView(
        context: Context,
        state: MainScreenState,
        listener: MainDialogs.Listener,
        onPermissions: () -> Unit,
        onExit: () -> Unit,
        onPreviewReady: (PreviewView) -> Unit,
    ): ComposeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            DriveGuardTheme {
                MainScreen(
                    state = state,
                    onMap = { listener.onMapMenuSelected() },
                    onSearch = listener::onGuideKeywordSelected,
                    onPermissions = onPermissions,
                    onExit = onExit,
                    onBluetooth = listener::onBluetoothMenuSelected,
                    camera = {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                PreviewView(viewContext).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    onPreviewReady(this)
                                }
                            },
                        )
                    },
                )
                MainDialogHost(state, listener)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
    state: MainScreenState,
    onMap: () -> Unit,
    onSearch: (String) -> Unit,
    onPermissions: () -> Unit,
    onExit: () -> Unit,
    onBluetooth: () -> Unit,
    camera: @Composable () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary) {
                            Icon(Icons.Default.Face, null, Modifier.padding(10.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.main_brand), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.main_tagline), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.main_permissions)) },
                                onClick = { menuExpanded = false; onPermissions() })
                            if (BuildConfig.CO2_SENSOR_ENABLED) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.bluetooth_connect_sensor)) },
                                    onClick = { menuExpanded = false; onBluetooth() })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.main_exit)) },
                                onClick = { menuExpanded = false; onExit() })
                        }
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val wide = this@BoxWithConstraints.maxWidth >= 720.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.main_eyebrow), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.main_headline), style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.main_subtitle), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        MonitorCard(state, camera, Modifier.weight(1.1f))
                        RestSection(state, onMap, onSearch, onPermissions, Modifier.weight(1f))
                    }
                } else {
                    MonitorCard(state, camera)
                    RestSection(state, onMap, onSearch, onPermissions)
                }
                if (BuildConfig.CO2_SENSOR_ENABLED) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.main_air_quality))
                            Text(state.co2Ppm?.let { stringResource(R.string.main_co2_value, it) }
                                ?: stringResource(R.string.main_co2_waiting), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.main_safety_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MonitorCard(
    state: MainScreenState,
    camera: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.main_monitor), modifier = Modifier.weight(1f).padding(end = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            StatusPill(
                stringResource(if (state.cameraStreaming) R.string.main_live else R.string.main_waiting),
                state.cameraStreaming,
            )
        }
        Box(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(1.45f)
                .clip(RoundedCornerShape(18.dp)).background(Color(0xFF17251F)),
        ) {
            camera()
            if (!state.cameraStreaming) {
                Column(Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Face, null, Modifier.size(44.dp), tint = Color(0xFFB8D9C6))
                    Text(stringResource(if (state.cameraGranted) R.string.main_camera_starting
                        else R.string.main_camera_permission), color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Box(Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f)))))
                Text(stringResource(R.string.main_camera_hint),
                    Modifier.align(Alignment.BottomStart).padding(16.dp), color = Color.White,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Face, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.main_detection_description),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    Surface(color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RestSection(
    state: MainScreenState,
    onMap: () -> Unit,
    onSearch: (String) -> Unit,
    onPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.main_rest_title), modifier = Modifier.weight(1f).padding(end = 12.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(stringResource(if (state.locationAvailable) R.string.main_location_ready else R.string.main_location_waiting),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(onClick = onMap, shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.main_map_title), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.main_map_description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard(R.string.rest_area, R.string.main_rest_area_description, Icons.Default.Search,
                { onSearch(MainDialogs.KEYWORD_REST_AREA) }, Modifier.weight(1f))
            ShortcutCard(R.string.drowsy_shelter, R.string.main_rest_stop_description, Icons.Default.LocationOn,
                { onSearch(MainDialogs.KEYWORD_REST_STOP) }, Modifier.weight(1f))
        }
        if (!state.cameraGranted || !state.locationGranted) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.main_permission_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.main_permission_description), style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = onPermissions) { Text(stringResource(R.string.main_permissions)) }
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(
    title: Int,
    subtitle: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(name = "Phone", showBackground = true, widthDp = 380, heightDp = 860)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.5f)
@Preview(name = "Tablet", showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun MainScreenPreview() {
    DriveGuardTheme(dynamicColor = false) { PreviewContent() }
}

@Preview(name = "Dark", showBackground = true, widthDp = 380, heightDp = 860,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MainScreenDarkPreview() {
    DriveGuardTheme(dynamicColor = false) { PreviewContent() }
}

@Composable
private fun PreviewContent() {
    val state = remember { MainScreenState().apply { updatePermissions(true, true); updateLocationAvailable(true) } }
    MainScreen(state, {}, {}, {}, {}, {}, camera = { Box(Modifier.fillMaxSize()) })
}
