package com.vicovpn.client.subscription

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.vicovpn.client.server.ConnectionPrioritySettings

object SmartRefreshScheduler {

    private const val PERIODIC_NAME =
        "vicovpn_safe_subscription_refresh"

    private const val ONCE_NAME =
        "vicovpn_safe_subscription_refresh_now"

    fun sync(
        context: Context,
        triggerNow: Boolean = false
    ) {
        val appContext =
            context.applicationContext

        cancelLegacyAlarm(
            appContext
        )

        val workManager =
            WorkManager.getInstance(
                appContext
            )

        val priority =
            ConnectionPrioritySettings(
                appContext
            ).getMode()

        if (!priority.allowsFree) {
            workManager.cancelUniqueWork(
                PERIODIC_NAME
            )
            workManager.cancelUniqueWork(
                ONCE_NAME
            )
            return
        }

        val mode =
            FreeServerSettings(
                appContext
            ).getAutoRefreshMode()

        if (mode == AutoRefreshMode.OFF) {
            workManager.cancelUniqueWork(
                PERIODIC_NAME
            )
            return
        }

        val minutes =
            when (mode) {
                AutoRefreshMode.SMART -> 15L
                AutoRefreshMode.DAILY -> 24L * 60L
                AutoRefreshMode.EVERY_THREE_DAYS ->
                    72L * 60L
                AutoRefreshMode.OFF -> 15L
            }

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(
                    true
                )
                .build()

        val periodic =
            PeriodicWorkRequest.Builder(
                SafeSubscriptionRefreshWorker::class.java,
                minutes,
                TimeUnit.MINUTES
            )
                .setConstraints(
                    constraints
                )
                .addTag(
                    PERIODIC_NAME
                )
                .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )

        if (triggerNow) {
            val once =
                OneTimeWorkRequest.Builder(
                    SafeSubscriptionRefreshWorker::class.java
                )
                    .setConstraints(
                        constraints
                    )
                    .addTag(
                        ONCE_NAME
                    )
                    .build()

            workManager.enqueueUniqueWork(
                ONCE_NAME,
                ExistingWorkPolicy.REPLACE,
                once
            )
        }
    }

    private fun cancelLegacyAlarm(
        context: Context
    ) {
        val alarmManager =
            context.getSystemService(
                AlarmManager::class.java
            )

        val pending =
            PendingIntent.getBroadcast(
                context,
                4415,
                Intent(
                    context,
                    SmartRefreshReceiver::class.java
                ).setAction(
                    SmartRefreshReceiver
                        .ACTION_SMART_REFRESH
                ),
                PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_IMMUTABLE
            )

        if (pending != null) {
            alarmManager.cancel(
                pending
            )
            pending.cancel()
        }
    }
}
