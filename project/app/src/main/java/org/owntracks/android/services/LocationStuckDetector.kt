package org.owntracks.android.services

import android.content.Context
import android.location.Location
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.owntracks.android.model.messages.MessageLocation
import org.owntracks.android.preferences.Preferences
import org.owntracks.android.preferences.types.MonitoringMode
import timber.log.Timber
import java.util.concurrent.TimeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationStuckDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences
) {
    companion object {
        // Thresholds for detecting stuck location
        const val STUCK_THRESHOLD_MOVE_MODE_SECONDS = 30L
        const val STUCK_THRESHOLD_SIGNIFICANT_MODE_SECONDS = 120L
        const val STUCK_THRESHOLD_QUIET_MODE_SECONDS = 300L

        // Force refresh intervals
        const val FORCE_REFRESH_INTERVAL_SECONDS = 60L
        const val MAX_LOCATION_AGE_SECONDS = 180L
    }

    interface LocationUpdateCallback {
        fun requestLocationUpdate(reportType: MessageLocation.ReportType)
        fun reInitializeLocationRequests()
    }

    private var monitoringJob: Job? = null
    private var lastLocationTime = System.currentTimeMillis()
    private var lastWifiSsid: String? = null
    private var consecutiveStuckDetections = 0
    private var callback: LocationUpdateCallback? = null

    private val _isLocationStuck = MutableStateFlow(false)
    val isLocationStuck: StateFlow<Boolean> = _isLocationStuck

    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    fun setCallback(callback: LocationUpdateCallback) {
        this.callback = callback
    }

    fun startMonitoring(scope: CoroutineScope) {
        Timber.i("Starting location stuck detection monitoring")
        stopMonitoring()

        monitoringJob = scope.launch {
            while (true) {
                checkLocationStatus()
                delay(10_000) // Check every 10 seconds
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        consecutiveStuckDetections = 0
        _isLocationStuck.value = false
    }

    fun onLocationReceived(location: Location) {
        val now = System.currentTimeMillis()
        val locationAge = now - location.time

        Timber.d("Location received. Age: ${locationAge}ms, Accuracy: ${location.accuracy}m")

        // Only accept recent locations
        if (locationAge < TimeUnit.SECONDS.toMillis(MAX_LOCATION_AGE_SECONDS)) {
            lastLocationTime = now
            consecutiveStuckDetections = 0
            _isLocationStuck.value = false

            // Check for WiFi SSID change
            checkWifiChange()
        } else {
            Timber.w("Received stale location. Age: ${locationAge}ms. Ignoring.")
        }
    }

    private suspend fun checkLocationStatus() {
        val now = System.currentTimeMillis()
        val timeSinceLastLocation = (now - lastLocationTime) / 1000 // Convert to seconds

        val threshold = when (preferences.monitoring) {
            MonitoringMode.Move -> STUCK_THRESHOLD_MOVE_MODE_SECONDS
            MonitoringMode.Significant -> STUCK_THRESHOLD_SIGNIFICANT_MODE_SECONDS
            MonitoringMode.Quiet, MonitoringMode.Manual -> STUCK_THRESHOLD_QUIET_MODE_SECONDS
        }

        if (timeSinceLastLocation > threshold) {
            consecutiveStuckDetections++
            _isLocationStuck.value = true

            Timber.w(
                "Location appears stuck. Time since last: ${timeSinceLastLocation}s, " +
                "Threshold: ${threshold}s, Consecutive detections: $consecutiveStuckDetections"
            )

            // Force location update based on severity
            when {
                consecutiveStuckDetections >= 3 -> {
                    Timber.e("Location severely stuck. Forcing high-accuracy update.")
                    forceHighAccuracyLocationUpdate()
                    consecutiveStuckDetections = 0 // Reset after force update
                }
                consecutiveStuckDetections >= 2 -> {
                    Timber.w("Location moderately stuck. Requesting standard update.")
                    requestLocationUpdate()
                }
                else -> {
                    Timber.i("Location possibly stuck. Monitoring...")
                }
            }
        }
    }

    private fun checkWifiChange() {
        try {
            val currentSsid = if (wifiManager?.isWifiEnabled == true) {
                wifiManager?.connectionInfo?.ssid?.removeSurrounding("\"")
            } else {
                null
            }

            if (currentSsid != lastWifiSsid && lastWifiSsid != null) {
                Timber.i("WiFi SSID changed from $lastWifiSsid to $currentSsid. Triggering location update.")
                requestLocationUpdate()
            }

            lastWifiSsid = currentSsid
        } catch (e: Exception) {
            Timber.e(e, "Failed to check WiFi SSID")
        }
    }

    private fun requestLocationUpdate() {
        try {
            Timber.i("Requesting standard location update due to stuck detection")
            callback?.requestLocationUpdate(MessageLocation.ReportType.USER)
                ?: Timber.w("No callback set for location update request")
        } catch (e: Exception) {
            Timber.e(e, "Failed to request location update")
        }
    }

    private fun forceHighAccuracyLocationUpdate() {
        try {
            Timber.i("Forcing high-accuracy location update due to severe stuck detection")

            // First, try to reinitialize location requests completely
            callback?.reInitializeLocationRequests()
                ?: Timber.w("No callback set for reinitializing location requests")

            // Then request an immediate update
            callback?.requestLocationUpdate(MessageLocation.ReportType.USER)
                ?: Timber.w("No callback set for location update request")

            // If in Move mode and Bluetooth is connected, ensure we're in the right mode
            if (preferences.bluetoothModeSwitch && preferences.monitoring != MonitoringMode.Move) {
                val bluetoothDevice = preferences.bluetoothModeSwitchDevice
                if (bluetoothDevice.isNotEmpty()) {
                    Timber.i("Bluetooth device configured but not in Move mode. Checking connection...")
                    // The BluetoothModeReceiver should handle this, but we can force a check
                    callback?.reInitializeLocationRequests()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to force high-accuracy location update")
        }
    }

    fun forceManualRefresh() {
        Timber.i("Manual location refresh requested")
        lastLocationTime = 0 // Force stuck detection on next check
        consecutiveStuckDetections = 2 // Skip to moderate stuck level
        requestLocationUpdate()
    }

    fun isLocationStale(location: Location): Boolean {
        val now = System.currentTimeMillis()
        val locationAge = now - location.time
        val maxAge = TimeUnit.SECONDS.toMillis(MAX_LOCATION_AGE_SECONDS)

        if (locationAge > maxAge) {
            Timber.w("Location is stale. Age: ${locationAge}ms, Max: ${maxAge}ms")
            return true
        }
        return false
    }
}