package com.example.drowsydrive.ui.map

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.drowsydrive.DriveGuardApplication
import com.example.drowsydrive.R
import com.example.drowsydrive.location.LocationProvider
import com.example.drowsydrive.permission.PermissionManager
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapView

class MapActivity : AppCompatActivity(), LocationProvider.Listener, MapManager.Listener {
    private var mapManager: MapManager? = null
    private var locationProvider: LocationProvider? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var permissionManager: PermissionManager
    private var currentPosition: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!(application as DriveGuardApplication).isKakaoMapAvailable) {
            Toast.makeText(this, R.string.map_native_unavailable, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.activity_map)
        val mapView = findViewById<MapView>(R.id.map_view)
        progressBar = findViewById(R.id.progressBar)
        mapManager = MapManager(mapView, this)
        locationProvider = LocationProvider(this, this)
        permissionManager = PermissionManager(this)

        // MainActivity가 전달한 위치로 바로 지도를 띄우고, 없으면 현재 위치를 요청한다.
        if (intent.hasExtra(EXTRA_LATITUDE) && intent.hasExtra(EXTRA_LONGITUDE)) {
            onLocationChanged(
                LatLng.from(
                    intent.getDoubleExtra(EXTRA_LATITUDE, 0.0),
                    intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0),
                ),
            )
        } else if (permissionManager.hasLocation()) {
            locationProvider?.requestCurrentLocation()
        } else {
            onLocationError("위치 권한이 필요합니다.")
        }

        findViewById<Button>(R.id.btn_return_to_my_location).setOnClickListener {
            val position = currentPosition
            if (position != null) {
                mapManager?.moveCameraToCurrentLocation(position)
            } else {
                Toast.makeText(this, "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady() {
        progressBar.visibility = View.GONE
    }

    override fun onMapError(error: Exception) {
        Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
    }

    override fun onMapDestroyed() {
        Toast.makeText(this, "Map Destroyed", Toast.LENGTH_SHORT).show()
    }

    override fun onLocationChanged(position: LatLng) {
        currentPosition = position
        val manager = mapManager ?: return
        if (manager.isStarted) {
            manager.updateLabelPosition(position)
        } else {
            manager.initializeMap(position, START_ZOOM_LEVEL)
        }
    }

    override fun onLocationError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        val manager = mapManager ?: return
        manager.onResume()
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
