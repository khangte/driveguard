package com.example.drowsydrive.ui.map

import android.util.Log
import com.example.drowsydrive.R
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle

class MapManager(private val mapView: MapView, private val listener: Listener) {
    interface Listener {
        fun onMapReady()
        fun onMapError(error: Exception)
        fun onMapDestroyed()
    }

    private var kakaoMap: KakaoMap? = null
    private var centerLabel: Label? = null

    // mapView.start()는 한 번만 호출한다.
    var isStarted = false
        private set

    fun initializeMap(startPosition: LatLng, startZoomLevel: Int) {
        if (isStarted) return
        isStarted = true
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                Log.d(TAG, "onMapDestroy")
                listener.onMapDestroyed()
            }

            override fun onMapError(error: Exception) {
                Log.e(TAG, "onMapError", error)
                listener.onMapError(error)
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                listener.onMapReady()
                addCurrentLocationLabel(startPosition)
            }

            override fun getPosition(): LatLng = startPosition
            override fun getZoomLevel(): Int = startZoomLevel
        })
    }

    private fun addCurrentLocationLabel(position: LatLng) {
        val map = kakaoMap ?: return
        val layer = map.labelManager?.layer ?: return
        val label = layer.addLabel(
            LabelOptions.from("centerLabel", position)
                .setStyles(LabelStyle.from(R.drawable.current_location).setAnchorPoint(0.5f, 0.5f))
                .setRank(1),
        )
        centerLabel = label
        if (label != null) map.trackingManager?.startTracking(label)
        map.setOnCameraMoveStartListener { movingMap, _ ->
            movingMap.trackingManager?.stopTracking()
        }
    }

    fun onResume() {
        mapView.resume()
    }

    fun onPause() {
        mapView.pause()
    }

    fun onDestroy() {
        mapView.destroyDrawingCache()
    }

    fun updateLabelPosition(newPosition: LatLng) {
        centerLabel?.moveTo(newPosition)
    }

    fun moveCameraToCurrentLocation(currentPosition: LatLng) {
        val map = kakaoMap
        if (map != null) {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(currentPosition))
        } else {
            Log.e(TAG, "KakaoMap is null")
        }
    }

    private companion object {
        const val TAG = "MapManager"
    }
}
