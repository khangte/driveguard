package com.example.drowsydrive.ui.main;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.drowsydrive.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 페어링된 센서의 선택과 선택한 주소의 저장을 담당한다. 연결은 수행하지 않는다.
 * 호출 전 화면에서 BLUETOOTH_CONNECT 권한을 확인해야 한다.
 */
public final class BluetoothDevicePicker {
    private static final String SELECTED_ADDRESS = "selected_address";

    private final Activity activity;
    private final BluetoothAdapter adapter;
    private final SharedPreferences preferences;
    private AlertDialog dialog;

    public BluetoothDevicePicker(Activity activity) {
        this.activity = activity;
        BluetoothManager manager = activity.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        preferences = activity.getSharedPreferences("co2_sensor", Context.MODE_PRIVATE);
    }

    /** 저장된 센서가 현재 페어링 목록에 있을 때만 반환한다. 임의의 장치를 대신 선택하지 않는다. */
    @Nullable
    @SuppressLint("MissingPermission")
    public BluetoothDevice getSelectedDevice() {
        String address = preferences.getString(SELECTED_ADDRESS, null);
        if (address == null || adapter == null) return null;
        try {
            if (!adapter.isEnabled()) return null;
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                if (address.equals(device.getAddress())) return device;
            }
        } catch (SecurityException ignored) {
            // 권한이 회수되면 자동 재연결하지 않는다.
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    public void show(Consumer<BluetoothDevice> onSelected) {
        if (dialog != null && dialog.isShowing()) return;
        try {
            if (adapter == null || !adapter.isEnabled()) {
                Toast.makeText(activity, R.string.bluetooth_unavailable, Toast.LENGTH_LONG).show();
                return;
            }
            List<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
            if (devices.isEmpty()) {
                Toast.makeText(activity, R.string.bluetooth_pair_sensor_first, Toast.LENGTH_LONG).show();
                return;
            }
            devices.sort((first, second) -> first.getAddress().compareTo(second.getAddress()));
            String[] labels = new String[devices.size()];
            for (int i = 0; i < devices.size(); i++) {
                BluetoothDevice device = devices.get(i);
                String name = device.getName();
                labels[i] = (name == null ? activity.getString(R.string.bluetooth_unnamed_device) : name)
                        + "\n" + device.getAddress();
            }
            dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.bluetooth_select_sensor)
                    .setItems(labels, (view, index) -> {
                        BluetoothDevice selected = devices.get(index);
                        preferences.edit().putString(SELECTED_ADDRESS, selected.getAddress()).apply();
                        onSelected.accept(selected);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            dialog.show();
        } catch (SecurityException ignored) {
            Toast.makeText(activity, R.string.bluetooth_permission_required, Toast.LENGTH_LONG).show();
        }
    }

    public void dismiss() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }
}
