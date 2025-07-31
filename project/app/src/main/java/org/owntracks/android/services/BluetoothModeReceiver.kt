package org.owntracks.android.services

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import javax.inject.Inject
import org.owntracks.android.preferences.Preferences
import org.owntracks.android.preferences.types.MonitoringMode
import timber.log.Timber

class BluetoothModeReceiver @Inject constructor(
    private val preferences: Preferences
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!preferences.bluetoothModeSwitch) {
            return
        }

        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (device == null) {
            Timber.w("Bluetooth broadcast received but no device in intent")
            return
        }

        val selectedDevice = preferences.bluetoothModeSwitchDevice
        if (selectedDevice.isEmpty() || device.address != selectedDevice) {
            Timber.d("Bluetooth device ${device.address} not configured for auto mode switching")
            return
        }

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> handleConnect(device)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleDisconnect(device)
        }
    }

    private fun handleConnect(device: BluetoothDevice) {
        Timber.i("Bluetooth device connected: ${device.address}, switching to Move mode")
        
        // Save current mode if not already in Move mode
        if (preferences.monitoring != MonitoringMode.Move) {
            preferences.previousMonitoringMode = preferences.monitoring.name
            preferences.monitoring = MonitoringMode.Move
            Timber.d("Saved previous mode: ${preferences.previousMonitoringMode}")
        } else {
            Timber.d("Already in Move mode, not saving previous mode")
        }
    }

    private fun handleDisconnect(device: BluetoothDevice) {
        Timber.i("Bluetooth device disconnected: ${device.address}, restoring previous mode")
        
        val previousModeString = preferences.previousMonitoringMode
        if (previousModeString.isNotEmpty()) {
            try {
                val previousMode = MonitoringMode.valueOf(previousModeString)
                preferences.monitoring = previousMode
                Timber.d("Restored previous mode: $previousMode")
                // Clear the saved mode
                preferences.previousMonitoringMode = ""
            } catch (e: IllegalArgumentException) {
                Timber.e("Invalid previous monitoring mode: $previousModeString")
                // Fall back to Significant mode
                preferences.monitoring = MonitoringMode.Significant
            }
        } else {
            Timber.w("No previous mode saved, keeping current mode")
        }
    }
}