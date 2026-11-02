package com.example.drowsydrive.ui.map

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import com.example.drowsydrive.DriveGuardApplication
import com.example.drowsydrive.R
import com.example.drowsydrive.location.LocationProvider
import com.example.drowsydrive.permission.PermissionManager
import com.example.drowsydrive.ui.main.DriveGuardTheme
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapView

class MapActivity : AppCompatActivity(), LocationProvider.Listener, MapManager.Listener {
    private var mapManager: MapManager? = null
    private var locationProvider: LocationProvider? = null
    private lateinit var permissionManager: PermissionManager
    private var currentPosition: LatLng? = null
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var mapReady by mutableStateOf(false)
    private var hasPosition by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        supportActionBar?.hide()
        permissionManager = PermissionManager(this)
        val sdkAvailable = (application as DriveGuardApplication).isKakaoMapAvailable
        if (sdkAvailable) {
            locationProvider = LocationProvider(this, this)
            if (intent.hasExtra(EXTRA_LATITUDE) && intent.hasExtra(EXTRA_LONGITUDE)) {
                onLocationChanged(LatLng.from(
                    intent.getDoubleExtra(EXTRA_LATITUDE, 0.0),
                    intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0),
                ))
            } else if (permissionManager.hasLocation()) {
                locationProvider?.requestCurrentLocation()
            } else {
                showError(getString(R.string.map_location_required))
            }
        } else {
            showError(getString(R.string.map_native_unavailable))
        }
        setContentView(ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DriveGuardTheme {
                    MapScreen(
                        loading = loading,
                        error = error,
                        canLocate = mapReady && hasPosition,
                        onBack = { finish() },
                        onLocate = { currentPosition?.let { mapManager?.moveCameraToCurrentLocation(it) } },
                        mapContent = {
                            if (sdkAvailable) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { context -> MapView(context).also(::onMapViewCreated) },
                                )
                            }
                        },
                    )
                }
            }
        })
    }

    private fun onMapViewCreated(view: MapView) {
        mapManager = MapManager(view, this).also { manager ->
            currentPosition?.let { manager.initializeMap(it, START_ZOOM_LEVEL) }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) manager.onResume()
        }
    }

    override fun onMapReady() {
        mapReady = true
        loading = false
        error = null
    }

    override fun onMapError(error: Exception) {
        showError(getString(R.string.map_load_failed))
    }

    override fun onMapDestroyed() {
        if (!isFinishing && !isDestroyed) showError(getString(R.string.map_load_failed))
    }

    override fun onLocationChanged(position: LatLng) {
        currentPosition = position
        hasPosition = true
        val manager = mapManager ?: return
        if (manager.isStarted) manager.updateLabelPosition(position)
        else manager.initializeMap(position, START_ZOOM_LEVEL)
    }

    override fun onLocationError(message: String) {
        hasPosition = false
        if (!mapReady) showError(message)
        else Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        loading = false
        mapReady = false
        error = message
    }

    override fun onResume() {
        super.onResume()
        mapManager?.onResume()
        if (permissionManager.hasLocation()) locationProvider?.start()
    }

    override fun onPause() {
        locationProvider?.stop()
        mapManager?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapManager?.onDestroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        private const val START_ZOOM_LEVEL = 17
    }
}
