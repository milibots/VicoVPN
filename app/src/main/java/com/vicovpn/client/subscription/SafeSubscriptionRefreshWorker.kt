package com.vicovpn.client.subscription

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.vicovpn.client.util.DiagnosticsLog
import com.vicovpn.client.server.ConnectionPrioritySettings

class SafeSubscriptionRefreshWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker(context, parameters) {

    override fun doWork(): Result {
        return try {
            if (
                !ConnectionPrioritySettings(
                    applicationContext
                ).getMode()
                    .allowsFree
            ) {
                return Result.success()
            }

            val settings =
                FreeServerSettings(
                    applicationContext
                )

            if (
                settings.getAutoRefreshMode() ==
                    AutoRefreshMode.OFF
            ) {
                return Result.success()
            }

            val imported =
                SubscriptionImporter(
                    registryUrl =
                        SubscriptionSettings(
                            applicationContext
                        ).getRegistryUrl(),
                    maxSourceCount = 24,
                    maxConfigs = 1_500,
                    downloadThreads = 3
                ).run {
                    // Background work stays silent and nontechnical.
                }.getOrThrow()

            val candidates =
                imported.configs
                    .asSequence()
                    .map {
                        it.trim()
                    }
                    .filter {
                        it.isNotBlank()
                    }
                    .distinct()
                    .take(1_500)
                    .toList()

            if (candidates.isNotEmpty()) {
                FreeServerUpdateStore(
                    applicationContext
                ).saveCandidates(
                    candidates
                )

                settings.markUpdatedNow()
            }

            DiagnosticsLog.add(
                "SAFE_REFRESH",
                "Prepared ${candidates.size} candidates without starting the native core"
            )

            Result.success()
        } catch (
            throwable: Throwable
        ) {
            DiagnosticsLog.add(
                "SAFE_REFRESH_ERROR",
                throwable.message
                    ?: throwable.javaClass.name
            )

            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                // Do not create a recurring system crash/error loop.
                Result.success()
            }
        }
    }
}
