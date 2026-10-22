package com.rider.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

data class EarbudsState(
    val connected: Boolean,
    val name: String,
    val leftBattery: Int, // -1 if unknown/unavailable from OS
    val caseBattery: Int, // -1 if unknown/unavailable from OS
    val rightBattery: Int, // -1 if unknown/unavailable from OS
    val address: String
)

class BluetoothHelper(private val context: Context) {

    private val bluetoothManager: BluetoothManager? by lazy {
        try {
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        } catch (_: Exception) {
            null
        }
    }

    private val adapter: BluetoothAdapter?
        get() = try {
            bluetoothManager?.adapter
        } catch (_: Exception) {
            null
        }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun getEarbudsState(): EarbudsState {
        if (!hasBluetoothPermission()) {
            return EarbudsState(false, "", -1, -1, -1, "")
        }

        return try {
            val adp = adapter
            if (adp == null || !adp.isEnabled) {
                return EarbudsState(false, "", -1, -1, -1, "")
            }

            val headsetConnected = adp.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
            val a2dpConnected = adp.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED

            if (!headsetConnected && !a2dpConnected) {
                return EarbudsState(false, "", -1, -1, -1, "")
            }

            val bonded = adp.bondedDevices.orEmpty()
            for (dev in bonded) {
                val battery = readDeviceBattery(dev)
                val devName = dev.name ?: "Bluetooth Earbuds"

                // If OS doesn't provide battery (-1), show -1 (displayed as N/A in UI) instead of fake numbers
                return EarbudsState(
                    connected = true,
                    name = devName,
                    leftBattery = if (battery in 0..100) battery else -1,
                    caseBattery = -1, // Case battery is rarely exposed by standard Android Bluetooth API
                    rightBattery = if (battery in 0..100) battery else -1,
                    address = dev.address ?: ""
                )
            }

            EarbudsState(false, "", -1, -1, -1, "")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking real bluetooth: ${e.message}")
            EarbudsState(false, "", -1, -1, -1, "")
        }
    }

    private fun readDeviceBattery(device: BluetoothDevice): Int {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val result = method.invoke(device) as? Int
            result ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    companion object {
        private const val TAG = "BluetoothHelper"
    }
}
