package org.owntracks.android.support.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import org.owntracks.android.services.BackgroundService
import timber.log.Timber

class BootStartWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val triggerAction = inputData.getString("trigger_action") ?: "unknown"
        Timber.i("BootStartWorker attempting delayed start. Original trigger: $triggerAction")

        return try {
            // Add a small delay to ensure system is ready
            delay(1000)

            val startIntent = Intent(applicationContext, BackgroundService::class.java).apply {
                action = "DELAYED_BOOT_START"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(startIntent)
            } else {
                applicationContext.startService(startIntent)
            }

            Timber.i("BootStartWorker successfully started BackgroundService")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "BootStartWorker failed to start service. Will retry.")

            if (runAttemptCount < 3) {
                // Retry up to 3 times
                Result.retry()
            } else {
                Timber.e("BootStartWorker exceeded max retries. Giving up.")
                Result.failure()
            }
        }
    }
}