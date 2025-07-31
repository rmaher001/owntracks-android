package org.owntracks.android.ui.preferences

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.owntracks.android.R
import org.owntracks.android.preferences.Preferences
import org.owntracks.android.preferences.types.ReverseGeocodeProvider
import org.owntracks.android.support.RequirementsChecker
import timber.log.Timber

@AndroidEntryPoint
class AdvancedFragment @Inject constructor() :
    AbstractPreferenceFragment(), Preferences.OnPreferenceChangeListener {
  @Inject lateinit var requirementsChecker: RequirementsChecker

  override fun onAttach(context: Context) {
    super.onAttach(context)
    preferences.registerOnPreferenceChangedListener(this)
  }

  override fun onDetach() {
    super.onDetach()
    preferences.unregisterOnPreferenceChangedListener(this)
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    setPreferencesFromResource(R.xml.preferences_advanced, rootKey)
    val remoteConfigurationPreference =
        findPreference<SwitchPreferenceCompat>(Preferences::remoteConfiguration.name)
    val remoteCommandPreference = findPreference<SwitchPreferenceCompat>(Preferences::cmd.name)
    val remoteCommandAndConfigurationChangeListener =
        Preference.OnPreferenceChangeListener { preference, newValue ->
          if (newValue is Boolean) {
            when (preference.key) {
              Preferences::cmd.name ->
                  if (!newValue) {
                    remoteConfigurationPreference?.isChecked = false
                  }
              Preferences::remoteConfiguration.name ->
                  if (newValue) {
                    remoteCommandPreference?.isChecked = true
                  }
            }
          }
          true
        }
    remoteConfigurationPreference?.onPreferenceChangeListener =
        remoteCommandAndConfigurationChangeListener
    remoteCommandPreference?.onPreferenceChangeListener =
        remoteCommandAndConfigurationChangeListener

    findPreference<Preference>("autostartWarning")?.isVisible =
        !requirementsChecker.hasBackgroundLocationPermission()

    findPreference<ListPreference>(Preferences::reverseGeocodeProvider.name)
        ?.onPreferenceChangeListener =
        Preference.OnPreferenceChangeListener { preference, newValue ->
          if (newValue == ReverseGeocodeProvider.OpenCage.name) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preferencesAdvancedOpencagePrivacyDialogTitle)
                .setMessage(R.string.preferencesAdvancedOpencagePrivacyDialogMessage)
                .setPositiveButton(R.string.preferencesAdvancedOpencagePrivacyDialogAccept) { _, _
                  ->
                  (preference as ListPreference).value = newValue.toString()
                }
                .setNegativeButton(R.string.preferencesAdvancedOpencagePrivacyDialogCancel, null)
                .create()
                .apply { show() }
                .findViewById<TextView>(android.R.id.message)
                ?.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            false
          } else {
            true
          }
        }
    setOpenCageAPIKeyPreferenceVisibility()
    
    // Set up Bluetooth device dropdown
    updateBluetoothDeviceList()
  }

  private fun setOpenCageAPIKeyPreferenceVisibility() {
    setOf(Preferences::opencageApiKey.name, "opencagePrivacy").forEach {
      findPreference<Preference>(it)?.isVisible =
          preferences.reverseGeocodeProvider == ReverseGeocodeProvider.OpenCage
    }
  }

  override fun onPreferenceChanged(properties: Set<String>) {
    if (properties.contains(Preferences::reverseGeocodeProvider.name)) {
      setOpenCageAPIKeyPreferenceVisibility()
    }
  }
  
  private fun updateBluetoothDeviceList() {
    Timber.d("updateBluetoothDeviceList() called")
    val devicePref = findPreference<ListPreference>(Preferences::bluetoothModeSwitchDevice.name) ?: return
    
    // Check Bluetooth permission
    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(
        requireContext(),
        Manifest.permission.BLUETOOTH_CONNECT
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true // No runtime permission needed before Android 12
    }
    
    Timber.d("Bluetooth permission granted: $hasPermission")
    
    if (!hasPermission) {
      Timber.d("No Bluetooth permission, showing empty list")
      devicePref.entries = arrayOf(getString(R.string.preferencesBluetoothNoPairedDevices))
      devicePref.entryValues = arrayOf("")
      devicePref.isEnabled = false
      return
    }
    
    try {
      val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
      val bluetoothAdapter = bluetoothManager?.adapter
      
      Timber.d("BluetoothAdapter null: ${bluetoothAdapter == null}, enabled: ${bluetoothAdapter?.isEnabled}")
      
      if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
        Timber.d("Bluetooth adapter null or disabled")
        devicePref.entries = arrayOf(getString(R.string.preferencesBluetoothNoPairedDevices))
        devicePref.entryValues = arrayOf("")
        devicePref.isEnabled = false
        return
      }
      
      val pairedDevices = bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
      Timber.d("Found ${pairedDevices.size} paired devices")
      
      if (pairedDevices.isEmpty()) {
        Timber.d("No paired devices found")
        devicePref.entries = arrayOf(getString(R.string.preferencesBluetoothNoPairedDevices))
        devicePref.entryValues = arrayOf("")
        devicePref.isEnabled = false
      } else {
        Timber.d("Setting device list with ${pairedDevices.size} devices")
        devicePref.entries = pairedDevices.map { 
          "${it.name ?: "Unknown"} (${it.address})" 
        }.toTypedArray()
        devicePref.entryValues = pairedDevices.map { it.address }.toTypedArray()
        devicePref.isEnabled = true
        Timber.d("Device list set successfully")
      }
    } catch (e: SecurityException) {
      Timber.e(e, "SecurityException accessing Bluetooth devices")
      devicePref.entries = arrayOf(getString(R.string.preferencesBluetoothNoPairedDevices))
      devicePref.entryValues = arrayOf("")
      devicePref.isEnabled = false
    }
  }
}
