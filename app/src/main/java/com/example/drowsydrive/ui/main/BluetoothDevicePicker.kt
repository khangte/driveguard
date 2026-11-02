package com.example.drowsydrive.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.Toast
import com.example.drowsydrive.R

/** 센서 선택 다이얼로그에 표시할 페어링 장치 정보. */
data class SensorOption(val name: String, val address: String)

/**
 * 페어링된 센서 목록 조회와 선택한 주소의 저장을 담당한다. 연결과 다이얼로그 표시는 수행하지 않는다.
 * 호출 전 화면에서 BLUETOOTH_CONNECT 권한을 확인해야 한다.
 */
class BluetoothDevicePicker(private val activity: Activity) {
    private val adapter = activity.getSystemService(BluetoothManager::class.java)?.adapter
    private val preferences = activity.getSharedPreferences("co2_sensor", Context.MODE_PRIVATE)

    /** 저장된 센서가 현재 페어링 목록에 있을 때만 반환한다. 임의의 장치를 대신 선택하지 않는다. */
    val selectedDevice: BluetoothDevice?
        get() = preferences.getString(SELECTED_ADDRESS, null)?.let(::bondedDevice)

    /** 선택 가능한 센서 목록. 선택할 수 없는 상황이면 안내 Toast를 띄우고 null을 반환한다. */
    @SuppressLint("MissingPermission")
    fun pairedSensors(): List<SensorOption>? {
        try {
            val bluetoothAdapter = adapter
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                toast(R.string.bluetooth_unavailable)
                return null
            }
            val devices = bluetoothAdapter.bondedDevices.sortedBy { it.address }
            if (devices.isEmpty()) {
                toast(R.string.bluetooth_pair_sensor_first)
                return null
            }
            val unnamed = activity.getString(R.string.bluetooth_unnamed_device)
            return devices.map { SensorOption(it.name ?: unnamed, it.address) }
        } catch (_: SecurityException) {
            toast(R.string.bluetooth_permission_required)
            return null
        }
    }

    /** 선택한 주소를 저장하고 해당 장치를 반환한다. 그사이 페어링이 해제되었으면 null을 반환한다. */
    fun select(address: String): BluetoothDevice? {
        val device = bondedDevice(address) ?: return null
        preferences.edit().putString(SELECTED_ADDRESS, address).apply()
        return device
    }

    @SuppressLint("MissingPermission")
    private fun bondedDevice(address: String): BluetoothDevice? {
        val bluetoothAdapter = adapter ?: return null
        return try {
            if (!bluetoothAdapter.isEnabled) null
            else bluetoothAdapter.bondedDevices.firstOrNull { it.address == address }
        } catch (_: SecurityException) {
            // 권한이 회수되면 자동 재연결하지 않는다.
            null
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val SELECTED_ADDRESS = "selected_address"
    }
}
