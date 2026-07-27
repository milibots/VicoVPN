package com.vicovpn.client.subscription

import android.content.Context
import com.vicovpn.client.model.ProxyProfile
import com.vicovpn.client.parser.ShareLinkParser
import com.vicovpn.client.server.SavedServer
import com.vicovpn.client.server.ServerOrigin
import com.vicovpn.client.util.DiagnosticsLog
import com.vicovpn.client.xray.XrayConfigBuilder
import com.vicovpn.client.xray.XrayNativeBridge
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class NativeBatchTestProgress(
    val tested: Int,
    val total: Int,
    val working: Int,
    val batchIndex: Int
)

class NativeBatchDelayTester(
    context: Context,
    private val parallelism: Int = 6,
    private val batchPauseMs: Long = 80L,
    private val testUrl: String =
        "http://www.gstatic.com/generate_204",
    private val serverOrigin:
        ServerOrigin =
        ServerOrigin.FREE_SUBSCRIPTION
) {
    private val appContext =
        context.applicationContext

    private val cancelled =
        AtomicBoolean(false)

    private val stopAfterCurrentBatch =
        AtomicBoolean(false)

    private val tasks =
        mutableListOf<Future<*>>()

    fun cancel() {
        cancelled.set(true)

        synchronized(tasks) {
            tasks.forEach {
                it.cancel(true)
            }
        }
    }

    /**
     * Stops after the currently running batch and returns every working
     * server already found. This is the user-friendly "enough for now"
     * action; unlike cancel(), it does not throw away partial results.
     */
    fun requestStopAndKeepResults() {
        stopAfterCurrentBatch.set(true)

        synchronized(tasks) {
            tasks.forEach {
                if (!it.isDone) {
                    it.cancel(true)
                }
            }
        }
    }

    fun test(
        rawLinks: List<String>,
        resultLimit: Int,
        onProgress: (
            NativeBatchTestProgress
        ) -> Unit,
        onVerified: (
            SavedServer
        ) -> Unit = {}
    ): Result<List<SavedServer>> =
        runCatching {
            val candidates =
                prioritizeCandidates(
                    rawLinks
                        .distinct()
                        .mapNotNull { raw ->
                            runCatching {
                                ParsedCandidate(
                                    raw = raw,
                                    profile =
                                        ShareLinkParser
                                            .parse(raw)
                                )
                            }.getOrNull()
                        }
                )

            require(candidates.isNotEmpty()) {
                "No supported candidates"
            }

            val tested =
                AtomicInteger(0)

            val working =
                mutableListOf<SavedServer>()

            val batches =
                candidates.chunked(
                    parallelism.coerceIn(2, 20)
                )

            batchLoop@
            for (
                (batchIndex, batch) in
                batches.withIndex()
            ) {
                if (
                    stopAfterCurrentBatch.get() &&
                    batchIndex > 0
                ) {
                    break@batchLoop
                }

                check(!cancelled.get()) {
                    "Batch test cancelled"
                }

                val executor =
                    Executors.newFixedThreadPool(
                        minOf(
                            parallelism,
                            batch.size
                        )
                    )

                try {
                    val callables =
                        batch.map { candidate ->
                            Callable<SavedServer?> {
                                if (
                                    cancelled.get() ||
                                    Thread.currentThread()
                                        .isInterrupted
                                ) {
                                    return@Callable null
                                }

                                val testedServer =
                                    testOne(candidate)

                                val completed =
                                    tested.incrementAndGet()

                                val available =
                                    synchronized(working) {
                                        if (
                                            testedServer != null
                                        ) {
                                            working +=
                                                testedServer
                                        }

                                        working.size
                                    }

                                if (
                                    testedServer != null
                                ) {
                                    onVerified(
                                        testedServer
                                    )
                                }

                                onProgress(
                                    NativeBatchTestProgress(
                                        tested = completed,
                                        total =
                                            candidates.size,
                                        working =
                                            available,
                                        batchIndex =
                                            batchIndex
                                    )
                                )

                                testedServer
                            }
                        }

                    /*
                     * Submit first so the Cancel button can interrupt active
                     * native calls immediately. Then apply one deadline to
                     * the entire batch instead of a separate timeout for
                     * every Future.
                     */
                    val batchTasks =
                        callables.map {
                            executor.submit(it)
                        }

                    synchronized(tasks) {
                        tasks += batchTasks
                    }

                    executor.shutdown()

                    val completedInTime =
                        executor.awaitTermination(
                            10,
                            TimeUnit.SECONDS
                        )

                    if (!completedInTime) {
                        batchTasks
                            .filterNot {
                                it.isDone
                            }
                            .forEach {
                                it.cancel(true)
                            }

                        executor.shutdownNow()
                    }

                    batchTasks.forEach { future ->
                        if (
                            !future.isCancelled &&
                            future.isDone
                        ) {
                            runCatching {
                                future.get()
                            }.onFailure { error ->
                                if (
                                    !isInterruption(error)
                                ) {
                                    DiagnosticsLog.add(
                                        "NATIVE_BATCH_TASK",
                                        rootMessage(error)
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    executor.shutdownNow()

                    synchronized(tasks) {
                        tasks.clear()
                    }
                }

                val found =
                    synchronized(working) {
                        working.size
                    }

                if (
                    found >= resultLimit ||
                    stopAfterCurrentBatch.get()
                ) {
                    break@batchLoop
                }

                if (
                    batchIndex <
                        batches.lastIndex &&
                    batchPauseMs > 0L
                ) {
                    var remaining =
                        batchPauseMs

                    while (
                        remaining > 0L &&
                        !cancelled.get() &&
                        !stopAfterCurrentBatch.get()
                    ) {
                        val sleep =
                            minOf(
                                remaining,
                                150L
                            )

                        Thread.sleep(sleep)
                        remaining -= sleep
                    }
                }
            }

            synchronized(working) {
                working
                    .distinctBy {
                        it.rawLink
                    }
                    .sortedBy {
                        it.latencyMs
                    }
                    .take(resultLimit)
            }
        }

    private fun prioritizeCandidates(
        input: List<ParsedCandidate>
    ): List<ParsedCandidate> {
        val groups =
            input
                .distinctBy {
                    it.raw
                }
                .groupBy { candidate ->
                    "${protocolName(candidate.profile)}|" +
                        candidate.profile.transport
                            .network
                            .lowercase() +
                        "|" +
                        candidate.profile.address
                            .lowercase()
                }
                .values
                .map { group ->
                    group.sortedByDescending {
                        candidatePriority(it)
                    }.toMutableList()
                }
                .toMutableList()

        val ordered =
            mutableListOf<ParsedCandidate>()

        while (groups.any { it.isNotEmpty() }) {
            groups.forEach { group ->
                if (group.isNotEmpty()) {
                    ordered += group.removeAt(0)
                }
            }
        }

        return ordered
    }

    private fun candidatePriority(
        candidate: ParsedCandidate
    ): Int {
        val transport =
            candidate.profile.transport

        var score = 0

        if (
            candidate.profile.port in
                setOf(
                    443,
                    8443,
                    2053,
                    2083,
                    2087,
                    2096
                )
        ) {
            score += 5
        }

        score +=
            when (
                transport.security
                    .lowercase()
            ) {
                "reality" -> 5
                "tls" -> 4
                else -> 0
            }

        score +=
            when (
                transport.network
                    .lowercase()
            ) {
                "grpc" -> 4
                "xhttp" -> 4
                "ws" -> 3
                "tcp" -> 2
                else -> 1
            }

        if (
            transport.serverName
                .isNotBlank()
        ) {
            score += 2
        }

        return score
    }

    private fun testOne(
        candidate: ParsedCandidate
    ): SavedServer? {
        if (cancelled.get()) {
            return null
        }

        val config =
            XrayConfigBuilder.buildDelayTestConfig(
                candidate.profile
            )

        val bridge =
            XrayNativeBridge(appContext)

        val attempt =
            bridge.measureOutboundDelay(
                config = config,
                testUrl = testUrl
            )

        val measuredDelay =
            attempt.getOrElse { error ->
                if (
                    cancelled.get() ||
                    Thread.currentThread()
                        .isInterrupted ||
                    isInterruption(error)
                ) {
                    return null
                }

                DiagnosticsLog.add(
                    "NATIVE_DELAY_FAIL",
                    buildString {
                        append(
                            candidate.profile.address
                        )
                        append(":")
                        append(
                            candidate.profile.port
                        )
                        append(" ")
                        append(
                            rootMessage(error)
                        )
                    }
                )

                return null
            }

        val now =
            System.currentTimeMillis()

        DiagnosticsLog.add(
            "NATIVE_DELAY_PASS",
            "${candidate.profile.address}:${candidate.profile.port} ${measuredDelay}ms"
        )

        return SavedServer(
            id = UUID.randomUUID().toString(),
            name =
                candidate.profile.name
                    .ifBlank {
                        "${protocolName(candidate.profile)} free"
                    },
            rawLink = candidate.raw,
            protocol =
                protocolName(
                    candidate.profile
                ),
            address =
                candidate.profile.address,
            port =
                candidate.profile.port,
            transport =
                candidate.profile
                    .transport
                    .network
                    .uppercase(),
            createdAt = now,
            origin =
                serverOrigin,
            latencyMs = measuredDelay,
            lastTestedAt = now
        )
    }

    private fun isInterruption(
        throwable: Throwable
    ): Boolean {
        var current: Throwable? =
            throwable

        while (current != null) {
            if (
                current is InterruptedException ||
                current is java.util.concurrent.CancellationException
            ) {
                return true
            }

            current = current.cause
        }

        return false
    }

    private fun rootMessage(
        throwable: Throwable?
    ): String {
        var root: Throwable =
            throwable
                ?: return "Unknown native failure"

        while (
            root.cause != null &&
            root.cause !== root
        ) {
            root = root.cause!!
        }

        return root.message
            ?.takeIf {
                it.isNotBlank()
            }
            ?: root.toString()
                .takeIf {
                    it.isNotBlank()
                }
            ?: root.javaClass.name
    }

    private fun protocolName(
        profile: ProxyProfile
    ): String =
        when (profile) {
            is ProxyProfile.Vmess ->
                "VMess"

            is ProxyProfile.Vless ->
                "VLESS"

            is ProxyProfile.Trojan ->
                "Trojan"

            is ProxyProfile.Shadowsocks ->
                "Shadowsocks"
        }

    private data class ParsedCandidate(
        val raw: String,
        val profile: ProxyProfile
    )
}
