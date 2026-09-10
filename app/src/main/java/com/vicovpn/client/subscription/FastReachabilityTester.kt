package com.vicovpn.client.subscription

import com.vicovpn.client.parser.ShareLinkParser
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

data class ReachableCandidate(
    val rawLink: String,
    val tcpLatencyMs: Long
)

/**
 * Fast first-pass endpoint probe. A successful result only means that the
 * remote TCP listener is reachable; NativeBatchDelayTester performs the real
 * end-to-end proxy test on the short list returned by this class.
 */
class FastReachabilityTester(
    private val workerCount: Int,
    private val timeoutMs: Int,
    private val outputLimit: Int,
    private val endpointResolver: (String) -> Pair<String, Int> = { rawLink ->
        ShareLinkParser.parse(rawLink).let { profile ->
            profile.address to profile.port
        }
    }
) {
    private val cancelled = AtomicBoolean(false)
    private val stopAndKeepResults = AtomicBoolean(false)
    private val running = mutableListOf<Future<*>>()

    fun cancel() {
        cancelled.set(true)
        cancelRunningTasks()
    }

    fun requestStopAndKeepResults() {
        stopAndKeepResults.set(true)
        cancelRunningTasks()
    }

    fun wasStopRequested(): Boolean = stopAndKeepResults.get()

    fun filter(
        candidates: List<String>,
        onProgress: (completed: Int, total: Int, reachable: Int) -> Unit
    ): Result<List<ReachableCandidate>> = runCatching {
        val unique = candidates.distinct()
        require(unique.isNotEmpty()) { "No candidates to scan" }

        val executor =
            Executors.newFixedThreadPool(workerCount.coerceIn(2, 16))
        val completions = ExecutorCompletionService<ReachableCandidate?>(executor)
        val reachable = mutableListOf<ReachableCandidate>()

        try {
            val tasks = unique.map { rawLink ->
                completions.submit(Callable { probe(rawLink) })
            }
            synchronized(running) { running += tasks }

            var completed = 0
            while (completed < unique.size && !stopAndKeepResults.get()) {
                check(!cancelled.get()) { "Reachability scan cancelled" }
                val candidate =
                    runCatching { completions.take().get() }.getOrNull()
                completed++

                if (candidate != null) reachable += candidate
                onProgress(completed, unique.size, reachable.size)
            }
        } finally {
            executor.shutdownNow()
            synchronized(running) { running.clear() }
        }

        reachable
            .distinctBy { it.rawLink }
            .sortedBy { it.tcpLatencyMs }
            .take(outputLimit.coerceAtLeast(1))
    }

    private fun probe(rawLink: String): ReachableCandidate? {
        if (
            cancelled.get() ||
            stopAndKeepResults.get() ||
            Thread.currentThread().isInterrupted
        ) {
            return null
        }

        val endpoint = runCatching { endpointResolver(rawLink) }.getOrNull()
            ?: return null
        val startedAt = System.nanoTime()

        val connected = runCatching {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(
                    InetSocketAddress(endpoint.first, endpoint.second),
                    timeoutMs.coerceIn(250, 3_000)
                )
            }
        }.isSuccess

        if (!connected) return null

        return ReachableCandidate(
            rawLink = rawLink,
            tcpLatencyMs =
                ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
        )
    }

    private fun cancelRunningTasks() {
        synchronized(running) {
            running.filterNot { it.isDone }.forEach { it.cancel(true) }
        }
    }
}
