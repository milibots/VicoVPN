package com.vicovpn.client

import com.vicovpn.client.subscription.FastReachabilityTester
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastReachabilityTesterTest {
    @Test
    fun keepsOnlyTcpReachableEndpoints() {
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            val accepting = AtomicBoolean(true)
            val acceptor = Thread {
                while (accepting.get()) {
                    runCatching { server.accept().close() }
                }
            }.apply {
                isDaemon = true
                start()
            }

            val closedPort = ServerSocket(0).use { it.localPort }
            val live = "live"
            val closed = "closed"
            var finalCompleted = 0

            val result =
                FastReachabilityTester(2, 500, 4) { candidate ->
                    "127.0.0.1" to if (candidate == live) server.localPort else closedPort
                }
                    .filter(listOf(closed, live)) { completed, _, _ ->
                        finalCompleted = completed
                    }.getOrThrow()

            accepting.set(false)
            server.close()
            acceptor.join(500)

            assertEquals(listOf(live), result.map { it.rawLink })
            assertEquals(2, finalCompleted)
            assertTrue(result.single().tcpLatencyMs >= 1L)
        }
    }

    @Test
    fun stopReturnsWithoutWaitingForEveryProbe() {
        val tester = FastReachabilityTester(4, 1_500, 8)
        val worker = Executors.newSingleThreadExecutor()
        val candidates =
            (1..40).map { index ->
                "vless://11111111-1111-4111-8111-111111111111@192.0.2.1:443#server-$index"
            }

        val future = worker.submit<List<*>> {
            tester.filter(candidates) { _, _, _ -> }.getOrThrow()
        }

        Thread.sleep(50)
        tester.requestStopAndKeepResults()
        future.get(2, TimeUnit.SECONDS)
        worker.shutdownNow()

        assertTrue(tester.wasStopRequested())
    }
}
