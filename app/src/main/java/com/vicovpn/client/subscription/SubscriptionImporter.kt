package com.vicovpn.client.subscription

import com.vicovpn.client.util.DiagnosticsLog
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class SubscriptionImportProgress(
    val message: String,
    val completed: Int = 0,
    val total: Int = 0,
    val configCount: Int = 0
)

data class SubscriptionImportResult(
    val sourceCount: Int,
    val downloadedSourceCount: Int,
    val configs: List<String>
)

class SubscriptionImporter(
    private val registryUrl: String,
    private val maxSourceCount: Int,
    private val maxConfigs: Int,
    private val downloadThreads: Int
) {
    private val cancelled =
        AtomicBoolean(false)

    private val runningTasks =
        mutableListOf<Future<*>>()

    fun cancel() {
        cancelled.set(true)

        synchronized(runningTasks) {
            runningTasks.forEach {
                it.cancel(true)
            }
        }
    }

    fun run(
        onProgress: (
            SubscriptionImportProgress
        ) -> Unit
    ): Result<SubscriptionImportResult> {
        return runCatching {
            checkNotCancelled()

            onProgress(
                SubscriptionImportProgress(
                    message =
                        "Loading registry…"
                )
            )

            val registry =
                SubscriptionRegistryClient.fetch(
                    registryUrl = registryUrl,
                    maxSourceCount =
                        maxSourceCount
                )

            checkNotCancelled()

            val sourceCount =
                registry.urls.size

            val completed =
                AtomicInteger(0)

            val successful =
                AtomicInteger(0)

            val configSet =
                linkedSetOf<String>()

            val sourceErrors =
                mutableListOf<String>()

            onProgress(
                SubscriptionImportProgress(
                    message =
                        "Downloading $sourceCount sources…",
                    total = sourceCount
                )
            )

            val executor =
                Executors.newFixedThreadPool(
                    downloadThreads.coerceIn(
                        1,
                        6
                    )
                )

            try {
                val tasks =
                    registry.urls.map { sourceUrl ->
                        executor.submit(
                            Callable {
                                checkNotCancelled()

                                val links =
                                    runCatching {
                                        val body =
                                            HttpTextClient.get(
                                                url =
                                                    sourceUrl,
                                                accept =
                                                    "text/plain, application/octet-stream, application/base64, text/html;q=0.8, */*;q=0.1",
                                                maxBytes =
                                                    8_000_000,
                                                userAgent =
                                                    HttpTextClient
                                                        .SUBSCRIPTION_USER_AGENT,
                                                noCache = true
                                            )

                                        SubscriptionContentParser
                                            .extract(body)
                                    }.onFailure { error ->
                                        val message =
                                            "$sourceUrl -> ${error.message ?: error.javaClass.simpleName}"

                                        synchronized(
                                            sourceErrors
                                        ) {
                                            sourceErrors +=
                                                message
                                        }

                                        DiagnosticsLog.add(
                                            "SUBSCRIPTION_SOURCE_FAIL",
                                            message
                                        )
                                    }.getOrDefault(
                                        emptyList()
                                    )

                                if (links.isNotEmpty()) {
                                    successful
                                        .incrementAndGet()

                                    DiagnosticsLog.add(
                                        "SUBSCRIPTION_SOURCE_OK",
                                        "$sourceUrl -> ${links.size} links"
                                    )
                                } else {
                                    DiagnosticsLog.add(
                                        "SUBSCRIPTION_SOURCE_EMPTY",
                                        sourceUrl
                                    )
                                }

                                synchronized(configSet) {
                                    links.forEach { link ->
                                        if (
                                            configSet.size <
                                                maxConfigs
                                        ) {
                                            configSet +=
                                                link
                                        }
                                    }
                                }

                                val done =
                                    completed
                                        .incrementAndGet()

                                val count =
                                    synchronized(configSet) {
                                        configSet.size
                                    }

                                onProgress(
                                    SubscriptionImportProgress(
                                        message =
                                            "Downloading subscriptions…",
                                        completed = done,
                                        total =
                                            sourceCount,
                                        configCount =
                                            count
                                    )
                                )
                            }
                        )
                    }

                synchronized(runningTasks) {
                    runningTasks += tasks
                }

                tasks.forEach { task ->
                    checkNotCancelled()
                    task.get()
                }
            } finally {
                executor.shutdownNow()

                synchronized(runningTasks) {
                    runningTasks.clear()
                }
            }

            checkNotCancelled()

            val configs =
                synchronized(configSet) {
                    configSet
                        .take(maxConfigs)
                }

            require(configs.isNotEmpty()) {
                val details =
                    synchronized(sourceErrors) {
                        sourceErrors
                            .take(2)
                            .joinToString(" | ")
                    }

                if (details.isBlank()) {
                    "No supported VPN configurations were found"
                } else {
                    "No supported VPN configurations were found: $details"
                }
            }

            DiagnosticsLog.add(
                "SUBSCRIPTION_IMPORT",
                "sources=$sourceCount successful=${successful.get()} configs=${configs.size}"
            )

            SubscriptionImportResult(
                sourceCount = sourceCount,
                downloadedSourceCount =
                    successful.get(),
                configs = configs
            )
        }
    }

    private fun checkNotCancelled() {
        check(!cancelled.get()) {
            "Subscription import cancelled"
        }
    }
}
