package org.owntracks.android.e2e

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.adevinta.android.barista.assertion.BaristaVisibilityAssertions.assertContains
import com.adevinta.android.barista.assertion.BaristaVisibilityAssertions.assertDisplayed
import com.adevinta.android.barista.interaction.BaristaClickInteractions.clickOn
import com.adevinta.android.barista.interaction.BaristaDrawerInteractions.openDrawer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.owntracks.android.R
import org.owntracks.android.preferences.Preferences
import org.owntracks.android.preferences.types.MonitoringMode
import org.owntracks.android.services.BackgroundService
import org.owntracks.android.services.BluetoothModeReceiver
import org.owntracks.android.testutils.TestWithAnActivity
import org.owntracks.android.testutils.clickOnDrawerAndWait
import org.owntracks.android.testutils.scrollToPreferenceWithText
import org.owntracks.android.testutils.writeToPreference
import org.owntracks.android.ui.map.MapActivity
import timber.log.Timber

@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BluetoothModeSwitchingTests : TestWithAnActivity<MapActivity>() {

    @get:Rule(order = 0)
    val hiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val grantPermissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GrantPermissionRule.grant(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
    }

    @Inject
    lateinit var preferences: Preferences

    @Inject
    lateinit var bluetoothModeReceiver: BluetoothModeReceiver

    private val mockDevice = mockk<BluetoothDevice>()
    private val testDeviceAddress = "AA:BB:CC:DD:EE:FF"
    private val testDeviceName = "Test Car"

    @Before
    fun setup() {
        hiltAndroidRule.inject()
        every { mockDevice.address } returns testDeviceAddress
        every { mockDevice.name } returns testDeviceName
    }

    @Test
    fun bluetooth_mode_switching_full_flow() {
        // Start in Significant mode
        preferences.monitoring = MonitoringMode.Significant
        
        // Open preferences
        clickOn(R.id.menu_monitoring)
        Thread.sleep(500) // Wait for bottom sheet
        
        // Verify we're in Significant mode
        assertDisplayed(R.string.monitoringModeDialogSignificantTitle)
        clickOn(R.string.cancel) // Close bottom sheet
        
        // Navigate to preferences
        clickOn(R.id.fabMyLocation)
        openDrawer()
        clickOnDrawerAndWait(R.string.title_activity_preferences)
        
        // Go to Advanced settings
        clickOn(R.string.preferencesAdvanced)
        
        // Enable Bluetooth auto mode
        scrollToPreferenceWithText(R.string.preferencesBluetoothAutoMode)
        clickOn(R.string.preferencesBluetoothModeSwitchTitle)
        
        // Set the test device address (simulating device selection)
        preferences.bluetoothModeSwitchDevice = testDeviceAddress
        
        // Go back to map
        clickOn(R.id.fabMyLocation)
        
        // Simulate Bluetooth connection
        val connectIntent = Intent(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, mockDevice as android.os.Parcelable)
        }
        InstrumentationRegistry.getInstrumentation().targetContext.sendBroadcast(connectIntent)
        
        // Wait for mode change
        Thread.sleep(1000)
        
        // Verify mode changed to Move
        clickOn(R.id.menu_monitoring)
        Thread.sleep(500)
        assertDisplayed(R.string.monitoringModeDialogMoveTitle)
        clickOn(R.string.cancel)
        
        // Simulate Bluetooth disconnection
        val disconnectIntent = Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, mockDevice as android.os.Parcelable)
        }
        InstrumentationRegistry.getInstrumentation().targetContext.sendBroadcast(disconnectIntent)
        
        // Wait for mode change
        Thread.sleep(1000)
        
        // Verify mode restored to Significant
        clickOn(R.id.menu_monitoring)
        Thread.sleep(500)
        assertDisplayed(R.string.monitoringModeDialogSignificantTitle)
    }

    @Test
    fun bluetooth_connection_without_feature_enabled_does_not_change_mode() {
        // Ensure feature is disabled
        preferences.bluetoothModeSwitch = false
        preferences.monitoring = MonitoringMode.Manual
        
        // Simulate Bluetooth connection
        val connectIntent = Intent(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, mockDevice as android.os.Parcelable)
        }
        InstrumentationRegistry.getInstrumentation().targetContext.sendBroadcast(connectIntent)
        
        // Wait a bit
        Thread.sleep(1000)
        
        // Verify mode is still Manual
        clickOn(R.id.menu_monitoring)
        Thread.sleep(500)
        assertDisplayed(R.string.monitoringModeDialogManualTitle)
    }

    @Test
    fun multiple_connect_disconnect_cycles_work_correctly() {
        // Setup
        preferences.bluetoothModeSwitch = true
        preferences.bluetoothModeSwitchDevice = testDeviceAddress
        preferences.monitoring = MonitoringMode.Quiet
        
        // First connection
        sendBluetoothIntent(BluetoothDevice.ACTION_ACL_CONNECTED)
        Thread.sleep(500)
        assertCurrentMode(MonitoringMode.Move)
        
        // First disconnection
        sendBluetoothIntent(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        Thread.sleep(500)
        assertCurrentMode(MonitoringMode.Quiet)
        
        // Change mode manually
        preferences.monitoring = MonitoringMode.Manual
        
        // Second connection
        sendBluetoothIntent(BluetoothDevice.ACTION_ACL_CONNECTED)
        Thread.sleep(500)
        assertCurrentMode(MonitoringMode.Move)
        
        // Second disconnection
        sendBluetoothIntent(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        Thread.sleep(500)
        assertCurrentMode(MonitoringMode.Manual)
    }

    private fun sendBluetoothIntent(action: String) {
        val intent = Intent(action).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, mockDevice as android.os.Parcelable)
        }
        bluetoothModeReceiver.onReceive(
            InstrumentationRegistry.getInstrumentation().targetContext,
            intent
        )
    }

    private fun assertCurrentMode(expectedMode: MonitoringMode) {
        assert(preferences.monitoring == expectedMode) {
            "Expected mode $expectedMode but was ${preferences.monitoring}"
        }
    }
}