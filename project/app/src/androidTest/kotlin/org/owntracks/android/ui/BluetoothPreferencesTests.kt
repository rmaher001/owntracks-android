package org.owntracks.android.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.adevinta.android.barista.assertion.BaristaEnabledAssertions.assertDisabled
import com.adevinta.android.barista.assertion.BaristaEnabledAssertions.assertEnabled
import com.adevinta.android.barista.assertion.BaristaVisibilityAssertions.assertContains
import com.adevinta.android.barista.assertion.BaristaVisibilityAssertions.assertDisplayed
import com.adevinta.android.barista.interaction.BaristaClickInteractions.clickBack
import com.adevinta.android.barista.interaction.BaristaClickInteractions.clickOn
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.owntracks.android.R
import org.owntracks.android.testutils.TestWithAnActivity
import org.owntracks.android.testutils.scrollToPreferenceWithText
import org.owntracks.android.ui.preferences.PreferencesActivity

@MediumTest
@HiltAndroidTest
class BluetoothPreferencesTests : TestWithAnActivity<PreferencesActivity>() {

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GrantPermissionRule.grant(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        GrantPermissionRule.grant() // No runtime permission needed before Android 12
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    @Before
    fun checkBluetoothAvailable() {
        // Skip tests if Bluetooth is not available on the device
        Assume.assumeNotNull(bluetoothAdapter)
    }

    @Test
    fun bluetooth_auto_mode_preference_is_displayed_in_advanced_section() {
        // Navigate to Advanced preferences
        clickOn(R.string.preferencesAdvanced)
        
        // Scroll to Bluetooth section
        scrollToPreferenceWithText(R.string.preferencesBluetoothAutoMode)
        
        // Verify Bluetooth preferences are displayed
        assertDisplayed(R.string.preferencesBluetoothModeSwitchTitle)
        assertDisplayed(R.string.preferencesBluetoothModeSwitchSummary)
    }

    @Test
    fun bluetooth_device_dropdown_is_disabled_when_feature_is_off() {
        // Navigate to Advanced preferences
        clickOn(R.string.preferencesAdvanced)
        
        // Scroll to Bluetooth section
        scrollToPreferenceWithText(R.string.preferencesBluetoothAutoMode)
        
        // By default, the feature should be off and device selection disabled
        assertDisabled(R.string.preferencesBluetoothDevice)
    }

    @Test
    fun bluetooth_device_dropdown_is_enabled_when_feature_is_on() {
        // Navigate to Advanced preferences
        clickOn(R.string.preferencesAdvanced)
        
        // Scroll to Bluetooth section
        scrollToPreferenceWithText(R.string.preferencesBluetoothAutoMode)
        
        // Enable the feature
        clickOn(R.string.preferencesBluetoothModeSwitchTitle)
        
        // Device selection should now be enabled
        assertEnabled(R.string.preferencesBluetoothDevice)
    }

    @Test
    fun bluetooth_device_dropdown_shows_no_devices_message_when_bluetooth_is_off() {
        // Assume Bluetooth is off (we can't actually control this in tests)
        Assume.assumeFalse("Bluetooth must be off for this test", bluetoothAdapter?.isEnabled ?: true)
        
        // Navigate to Advanced preferences
        clickOn(R.string.preferencesAdvanced)
        
        // Scroll to Bluetooth section
        scrollToPreferenceWithText(R.string.preferencesBluetoothAutoMode)
        
        // Enable the feature
        clickOn(R.string.preferencesBluetoothModeSwitchTitle)
        
        // Click on device selection
        clickOn(R.string.preferencesBluetoothDevice)
        
        // Should show no paired devices message
        assertContains(R.string.preferencesBluetoothNoPairedDevices)
    }

    @Test
    fun preference_state_persists_after_navigation() {
        // Navigate to Advanced preferences
        clickOn(R.string.preferencesAdvanced)
        
        // Scroll to Bluetooth section
        scrollToPreferenceWithText(R.string.preferencesBluetoothAutoMode)
        
        // Enable the feature
        clickOn(R.string.preferencesBluetoothModeSwitchTitle)
        
        // Navigate back
        clickBack()
        
        // Navigate back to Advanced
        clickOn(R.string.preferencesAdvanced)
        scrollToPreferenceWithText(R.string.preferencesBluetoothAutoMode)
        
        // Device selection should still be enabled
        assertEnabled(R.string.preferencesBluetoothDevice)
    }
}