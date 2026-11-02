package com.example.drowsydrive.permission;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.drowsydrive.BuildConfig;
import com.example.drowsydrive.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 카메라/위치/블루투스 런타임 권한 확인과 요청을 담당한다.
 * 권한 결과는 화면(Activity)의 onRequestPermissionsResult로 전달되고, 화면이 기능 시작 여부를 결정한다.
 */
public class PermissionManager {
    public static final int REQUEST_CODE = 1001;

    private final Activity activity;

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    public boolean hasCamera() {
        return isGranted(Manifest.permission.CAMERA);
    }

    // Android 12+에서는 대략적인 위치만 허용될 수 있으므로 둘 중 하나면 위치 기능을 시작한다.
    public boolean hasLocation() {
        return isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
                || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    // Android 11 이하의 BLUETOOTH 권한은 설치 시 부여되는 일반 권한이다.
    public boolean hasBluetooth() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || isGranted(Manifest.permission.BLUETOOTH_CONNECT);
    }

    /** 아직 허용되지 않은 권한을 한 번에 요청한다. 요청할 권한이 없으면 false를 반환한다. */
    public boolean requestMissingPermissions() {
        List<String> missing = new ArrayList<>();
        if (!hasCamera()) {
            missing.add(Manifest.permission.CAMERA);
        }
        if (!hasLocation()) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (BuildConfig.CO2_SENSOR_ENABLED && !hasBluetooth()) {
            missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (missing.isEmpty()) {
            return false;
        }
        ActivityCompat.requestPermissions(activity, missing.toArray(new String[0]), REQUEST_CODE);
        return true;
    }

    /** 필수 권한(카메라/위치) 중 거부된 항목의 안내 문구를 반환한다. 모두 허용되면 0을 반환한다. */
    @StringRes
    public int deniedMessageRes() {
        if (!hasCamera() && !hasLocation()) {
            return R.string.dialog_permission_camera_location;
        } else if (!hasCamera()) {
            return R.string.dialog_permission_camera;
        } else if (!hasLocation()) {
            return R.string.dialog_permission_location;
        }
        return 0;
    }

    /** 앱 상세 설정 화면을 연다. */
    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }

    private boolean isGranted(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
