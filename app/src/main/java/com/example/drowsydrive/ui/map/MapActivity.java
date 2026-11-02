package com.example.drowsydrive.ui.map;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.drowsydrive.R;
import com.example.drowsydrive.location.LocationProvider;
import com.example.drowsydrive.permission.PermissionManager;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapView;

public class MapActivity extends AppCompatActivity implements LocationProvider.Listener {
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";
    private static final int START_ZOOM_LEVEL = 17;

    private MapManager mapManager;
    private LocationProvider locationProvider;
    private PermissionManager permissionManager;
    private LatLng currentPosition;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        MapView mapView = findViewById(R.id.map_view);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        mapManager = new MapManager(this, mapView, progressBar);
        locationProvider = new LocationProvider(this, this);
        permissionManager = new PermissionManager(this);

        // MainActivity가 전달한 위치로 바로 지도를 띄우고, 없으면 현재 위치를 요청한다.
        if (getIntent().hasExtra(EXTRA_LATITUDE) && getIntent().hasExtra(EXTRA_LONGITUDE)) {
            onLocationChanged(LatLng.from(
                    getIntent().getDoubleExtra(EXTRA_LATITUDE, 0),
                    getIntent().getDoubleExtra(EXTRA_LONGITUDE, 0)));
        } else if (permissionManager.hasLocation()) {
            locationProvider.requestCurrentLocation();
        } else {
            onLocationError("위치 권한이 필요합니다.");
        }

        // "내 위치로 돌아가기" 버튼
        Button returnToMyLocationButton = findViewById(R.id.btn_return_to_my_location);
        returnToMyLocationButton.setOnClickListener(v -> {
            if (currentPosition != null) {
                mapManager.moveCameraToCurrentLocation(currentPosition);
            } else {
                Toast.makeText(this, "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onLocationChanged(@NonNull LatLng position) {
        currentPosition = position;
        if (mapManager.isStarted()) {
            mapManager.updateLabelPosition(position);
        } else {
            mapManager.initializeMap(position, START_ZOOM_LEVEL);
        }
    }

    @Override
    public void onLocationError(@NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapManager.onResume();
        if (permissionManager.hasLocation()) {
            locationProvider.start();
        }
    }

    @Override
    protected void onPause() {
        locationProvider.stop();
        mapManager.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mapManager.onDestroy();
        super.onDestroy();
    }
}
