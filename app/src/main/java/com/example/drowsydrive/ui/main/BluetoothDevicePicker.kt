package com.example.drowsydrive.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.drowsydrive.R

/**
 * 페어링된 센서의 선택과 선택한 주소의 저장을 담당한다. 연결은 수행하지 않는다.
 * 호출 전 화면에서 BLUETOOTH_CONNECT 권한을 확인해야 한다.
 */
class BluetoothDevicePicker(private val activity: Activity) {
    private val adapter = activity.getSystemService(BluetoothManager::class.java)?.adapter
    private val preferences = activity.getSharedPreferences("co2_sensor", Context.MODE_PRIVATE)
    private var dialog: AlertDialog? = null

    /** 저장된 센서가 현재 페어링 목록에 있을 때만 반환한다. 임의의 장치를 대신 선택하지 않는다. */
    val selectedDevice: BluetoothDevice?
        @SuppressLint("MissingPermission")
        get() {
            val address = preferences.getString(SELECTED_ADDRESS, null) ?: return null
            val bluetoothAdapter = adapter ?: return null
            return try {
                if (!bluetoothAdapter.isEnabled) null
                else bluetoothAdapter.bondedDevices.firstOrNull { it.address == address }
            } catch (_: SecurityException) {
                // 권한이 회수되면 자동 재연결하지 않는다.
                null
            }
        }

    @SuppressLint("MissingPermission")
    fun show(onSelected: (BluetoothDevice) -> Unit) {
        if (dialog?.isShowing == true) return
        try {
            val bluetoothAdapter = adapter
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Toast.makeText(activity, R.string.bluetooth_unavailable, Toast.LENGTH_LONG).show()
                return
            }
            val devices = bluetoothAdapter.bondedDevices.sortedBy { it.address }
            if (devices.isEmpty()) {
                Toast.makeText(activity, R.string.bluetooth_pair_sensor_first, Toast.LENGTH_LONG).show()
                return
            }
            val labels = devices.map {
                val name = it.name ?: activity.getString(R.string.bluetooth_unnamed_device)
                "$name\n${it.address}"
            }.toTypedArray()
            dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.bluetooth_select_sensor)
                .setItems(labels) { _, index ->
                    val selected = devices[index]
                    preferences.edit().putString(SELECTED_ADDRESS, selected.address).apply()
                    onSelected(selected)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .also { it.show() }
        } catch (_: SecurityException) {
            Toast.makeText(activity, R.string.bluetooth_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private companion object {
        const val SELECTED_ADDRESS = "selected_address"
    }
}
