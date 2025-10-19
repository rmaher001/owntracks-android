package org.owntracks.android.support.receiver

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.owntracks.android.R
import org.owntracks.android.preferences.Preferences
import org.owntracks.android.services.BackgroundService
import org.owntracks.android.support.worker.BootStartWorker
import timber.log.Timber

@AndroidEntryPoint
class StartBackgroundServiceReceiver : BroadcastReceiver() {
  companion object {
    const val BOOT_START_CHANNEL_ID = "owntracks_boot_start"
    const val BOOT_START_NOTIFICATION_ID = 9999

    fun enable(context: Context) {
      val receiver = ComponentName(context, StartBackgroundServiceReceiver::class.java)
      context.packageManager.setComponentEnabledSetting(
          receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }
  }

  @Inject lateinit var preferences: Preferences

  override fun onReceive(context: Context, intent: Intent) {
    val validActions = listOf(
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_LOCKED_BOOT_COMPLETED
    )

    if (intent.action in validActions && preferences.autostartOnBoot) {
      Timber.i("Boot receiver triggered: action=${intent.action}, attempting to start service")
      val startIntent = Intent(context, BackgroundService::class.java).apply {
        action = intent.action
      }

      var startAttempted = false
      var startSucceeded = false
      var errorMessage: String? = null

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          try {
            context.startForegroundService(startIntent)
            startSucceeded = true
            Timber.i("Successfully started foreground service on boot")
          } catch (e: ForegroundServiceStartNotAllowedException) {
            errorMessage = "ForegroundServiceStartNotAllowedException: ${e.message}"
            Timber.e(e, "Unable to start foreground service on boot. This is common for ADB-installed apps.")
            // Schedule WorkManager as fallback
            scheduleDelayedStart(context, intent.action ?: "unknown")
          } catch (e: Exception) {
            errorMessage = "Exception: ${e.message}"
            Timber.e(e, "Unexpected error starting service on boot")
          }
          startAttempted = true
        } else {
          try {
            context.startForegroundService(startIntent)
            startSucceeded = true
            Timber.i("Successfully started foreground service on boot (pre-Android 12)")
          } catch (e: Exception) {
            errorMessage = "Exception: ${e.message}"
            Timber.e(e, "Failed to start foreground service")
          }
          startAttempted = true
        }
      } else {
        try {
          context.startService(startIntent)
          startSucceeded = true
          Timber.i("Successfully started service on boot (legacy)")
        } catch (e: Exception) {
          errorMessage = "Exception: ${e.message}"
          Timber.e(e, "Failed to start service")
        }
        startAttempted = true
      }

      // Notify user if start failed
      if (startAttempted && !startSucceeded) {
        showBootFailureNotification(context, errorMessage)
      }
    }
  }

  private fun scheduleDelayedStart(context: Context, triggerAction: String) {
    Timber.i("Scheduling delayed start via WorkManager due to boot restrictions")
    try {
      val workRequest = OneTimeWorkRequestBuilder<BootStartWorker>()
          .setInitialDelay(30, TimeUnit.SECONDS)
          .setInputData(workDataOf("trigger_action" to triggerAction))
          .addTag("boot_start")
          .build()

      WorkManager.getInstance(context).enqueue(workRequest)
      Timber.i("WorkManager delayed start scheduled for 30 seconds")
    } catch (e: Exception) {
      Timber.e(e, "Failed to schedule WorkManager delayed start")
    }
  }

  private fun showBootFailureNotification(context: Context, errorMessage: String?) {
    try {
      // Create notification channel if needed
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            BOOT_START_CHANNEL_ID,
            "Boot Start Issues",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Notifications about OwnTracks boot start problems"
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
      }

      val notification = NotificationCompat.Builder(context, BOOT_START_CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_owntracks_80)
          .setContentTitle("OwnTracks Auto-Start Failed")
          .setContentText("Manual start required. Tap to open app.")
          .setStyle(NotificationCompat.BigTextStyle()
              .bigText("OwnTracks could not start automatically after boot. " +
                      "This commonly happens with ADB-installed apps. " +
                      "Please open the app manually.\n\n" +
                      "Error: ${errorMessage ?: "Unknown"}"))
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setAutoCancel(true)
          .build()

      NotificationManagerCompat.from(context)
          .notify(BOOT_START_NOTIFICATION_ID, notification)
    } catch (e: Exception) {
      Timber.e(e, "Failed to show boot failure notification")
    }
  }
}
