package com.example.drowsydrive.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.drowsydrive.R
import com.example.drowsydrive.ui.main.DriveGuardTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapScreen(
    loading: Boolean,
    error: String?,
    canLocate: Boolean,
    onBack: () -> Unit,
    onLocate: () -> Unit,
    mapContent: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.map_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (canLocate && !loading && error == null) {
                ExtendedFloatingActionButton(
                    onClick = onLocate,
                    icon = { Icon(Icons.Default.LocationOn, null) },
                    text = { Text(stringResource(R.string.map_return_to_location)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            mapContent()
            if (error != null) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.map_error_title), style = MaterialTheme.typography.titleLarge)
                        Text(error, textAlign = TextAlign.Center)
                        Button(onClick = onBack) { Text(stringResource(R.string.map_back)) }
                    }
                }
            } else if (loading) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp,
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.map_loading))
                    }
                }
            }
        }
    }
}

@Preview(name = "Map loading", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun MapLoadingPreview() {
    DriveGuardTheme(dynamicColor = false) { MapScreen(true, null, false, {}, {}, {}) }
}

@Preview(name = "Map error", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun MapErrorPreview() {
    DriveGuardTheme(dynamicColor = false) {
        MapScreen(false, stringResource(R.string.map_native_unavailable), false, {}, {}, {})
    }
}
