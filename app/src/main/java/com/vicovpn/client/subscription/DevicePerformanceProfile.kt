package com.vicovpn.client.subscription

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager

data class DevicePerformanceProfile(
    val label: String,
    val downloadThreads: Int,
    val reachabilityThreads: Int,
    val reachabilityInputLimit: Int,
    val candidateLimit: Int,
    val resultLimit: Int,
    val tcpTimeoutMs: Int,
    val perServerTimeoutSeconds: Int
) {
    companion object {
        fun detect(
            context: Context,
            settings: FreeServerSettings =
                FreeServerSettings(context)
        ): DevicePerformanceProfile {
            val activityManager =
                context.getSystemService(
                    ActivityManager::class.java
                )

            val memoryInfo =
                ActivityManager.MemoryInfo().also {
                    activityManager.getMemoryInfo(it)
                }

            val totalRamGb =
                memoryInfo.totalMem.toDouble() /
                        (1024.0 * 1024.0 * 1024.0)

            val cores =
                Runtime.getRuntime()
                    .availableProcessors()
                    .coerceAtLeast(1)

            val powerManager =
                context.getSystemService(
                    PowerManager::class.java
                )

            val constrained =
                activityManager.isLowRamDevice ||
                        powerManager.isPowerSaveMode ||
                        totalRamGb < 3.5 ||
                        cores <= 4

            val strong =
                !constrained &&
                        totalRamGb >= 7.0 &&
                        cores >= 8

            val base =
                when {
                    constrained -> {
                        DevicePerformanceProfile(
                            label = "light",
                            downloadThreads = 2,
                            reachabilityThreads = 4,
                            reachabilityInputLimit = 100,
                            candidateLimit = 8,
                            resultLimit = 5,
                            tcpTimeoutMs = 900,
                            perServerTimeoutSeconds = 8
                        )
                    }

                    strong -> {
                        DevicePerformanceProfile(
                            label = "strong",
                            downloadThreads = 5,
                            reachabilityThreads = 12,
                            reachabilityInputLimit = 250,
                            candidateLimit = 16,
                            resultLimit = 10,
                            tcpTimeoutMs = 750,
                            perServerTimeoutSeconds = 7
                        )
                    }

                    else -> {
                        DevicePerformanceProfile(
                            label = "balanced",
                            downloadThreads = 3,
                            reachabilityThreads = 8,
                            reachabilityInputLimit = 160,
                            candidateLimit = 12,
                            resultLimit = 8,
                            tcpTimeoutMs = 850,
                            perServerTimeoutSeconds = 8
                        )
                    }
                }

            val modeAdjusted =
                when (settings.getTestMode()) {
                    FreeTestMode.FAST -> {
                        base.copy(
                            label = "${base.label}-fast",
                            reachabilityInputLimit =
                                minOf(
                                    base.reachabilityInputLimit,
                                    100
                                ),
                            candidateLimit =
                                minOf(
                                    base.candidateLimit,
                                    8
                                ),
                            resultLimit =
                                minOf(
                                    base.resultLimit,
                                    5
                                ),
                            tcpTimeoutMs =
                                minOf(
                                    base.tcpTimeoutMs,
                                    700
                                ),
                            perServerTimeoutSeconds = 6
                        )
                    }

                    FreeTestMode.THOROUGH -> {
                        base.copy(
                            label =
                                "${base.label}-thorough",
                            reachabilityInputLimit =
                                minOf(
                                    base.reachabilityInputLimit * 2,
                                    350
                                ),
                            candidateLimit =
                                minOf(
                                    base.candidateLimit + 8,
                                    24
                                ),
                            resultLimit =
                                minOf(
                                    base.resultLimit + 4,
                                    15
                                ),
                            tcpTimeoutMs =
                                maxOf(
                                    base.tcpTimeoutMs,
                                    1100
                                ),
                            perServerTimeoutSeconds = 10
                        )
                    }

                    FreeTestMode.DEVICE_OPTIMIZED -> base
                }

            return modeAdjusted.copy(
                resultLimit =
                    minOf(
                        modeAdjusted.resultLimit,
                        settings.getMaxSaved()
                    )
            )
        }
    }
}