package com.vicovpn.client.subscription

import com.vicovpn.client.parser.ShareLinkParser
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ReachableCandidate(
    val rawLink: String,
    val tcpLatencyMs: Long
)

class FastReachabilityTester(
    private val workerCount: Int,
    private val timeoutMs: Int,
    private val outputLimit: Int
) {
    private val cancelled =
        AtomicBoolean(false)

    private val running =
        mutableListOf<Future<*>>()

    fun cancel() {
        cancelled.set(true)

        synchronized(running) {
            running.forEach {
                it.cancel(true)
            }
        }
    }

    fun filter(
        candidates: List<String>,
        onProgress: (
            completed: Int,
            total: Int,
            reachable: Int
        ) -> Unit
    ): Result<List<String>> {
        return runCatching {
            val unique =
                candidates.distinct()

            require(unique.isNotEmpty()) {
                "No candidates to scan"
            }

            val completed =
                AtomicInteger(0)

            val reachable =
                mutableListOf<ReachableCandidate>()

            val executor =
                Executors.newFixedThreadPool(
                    workerCount.coerceIn(2, 12)
                )

            try {
                val tasks =
                    unique.map { rawLink ->
                        executor.submit(
                            Callable {
                                if (
                                    cancelled.get() ||
                                    Thread.currentThread()
                                        .isInterrupted
                                ) {
                                    return@Callable
                                }

                                val profile =
                                    runCatching {
                                        ShareLinkParser.parse(
                                            rawLink
                                        )
                                    }.getOrNull()

                                if (profile != null) {
                                    val start =
                                        System.nanoTime()

                                    val success =
                                        runCatching {
                                            Socket().use { socket ->
                                                socket.connect(
                                                    InetSocketAddress(
                                                        profile.address,
                                                        profile.port
                                                    ),
                                                    timeoutMs
                                                )
                                            }
                                        }.isSuccess

                                    if (success) {
                                        val elapsed =
                                            (
                                                    System.nanoTime() -
                                                            start
                                                    ) / 1_000_000L

                                        synchronized(reachable) {
                                            reachable +=
                                                ReachableCandidate(
                                                    rawLink =
                                                        rawLink,
                                                    tcpLatencyMs =
                                                        elapsed
                                                )
                                        }
                                    }
                                }

                                val done =
                                    completed.incrementAndGet()

                                val found =
                                    synchronized(reachable) {
                                        reachable.size
                                    }

                                onProgress(
                                    done,
                                    unique.size,
                                    found
                                )
                            }
                        )
                    }

                synchronized(running) {
                    running += tasks
                }

                tasks.forEach {
                    if (cancelled.get()) {
                        error(
                            "Reachability scan cancelled"
                        )
                    }

                    it.get()
                }
            } finally {
                executor.shutdownNow()

                synchronized(running) {
                    running.clear()
                }
            }

            synchronized(reachable) {
                reachable
                    .sortedBy {
                        it.tcpLatencyMs
                    }
                    .take(outputLimit)
                    .map {
                        it.rawLink
                    }
            }
        }
    }
}