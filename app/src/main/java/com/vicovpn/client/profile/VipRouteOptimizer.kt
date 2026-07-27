package com.vicovpn.client.profile

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.vicovpn.client.server.ServerOrigin
import com.vicovpn.client.server.ServerStore
import com.vicovpn.client.subscription.NativeBatchDelayTester
import com.vicovpn.client.util.DiagnosticsLog
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object VipRouteOptimizer {

    private const val FRESH_FOR_MS =
        15L * 60L * 1_000L

    private val running =
        AtomicBoolean(false)

    private val executor =
        Executors.newSingleThreadExecutor()

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    fun start(
        context: Context,
        force: Boolean = false,
        onUpdated: (() -> Unit)? = null
    ) {
        if (
            !running.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        val appContext =
            context.applicationContext

        executor.execute {
            try {
                val store =
                    ServerStore(
                        appContext
                    )

                val now =
                    System.currentTimeMillis()

                val routes =
                    store.getServers()
                        .filter {
                            it.origin ==
                                ServerOrigin
                                    .VIP_SUBSCRIPTION
                        }

                if (routes.isEmpty()) {
                    return@execute
                }

                val needsUpdate =
                    force ||
                        routes.any {
                            it.latencyMs <= 0 ||
                                now -
                                    it.lastTestedAt >=
                                    FRESH_FOR_MS
                        }

                if (!needsUpdate) {
                    return@execute
                }

                val tester =
                    NativeBatchDelayTester(
                        context =
                            appContext,
                        parallelism = 3,
                        batchPauseMs = 70L,
                        serverOrigin =
                            ServerOrigin
                                .VIP_SUBSCRIPTION
                    )

                tester.test(
                    rawLinks =
                        routes.map {
                            it.rawLink
                        },
                    resultLimit =
                        routes.size,
                    onProgress = {},
                    onVerified = {
                            measured ->
                        store.updateVipRouteMeasurement(
                            measured
                        )

                        mainHandler.post {
                            onUpdated
                                ?.invoke()
                        }
                    }
                ).onFailure {
                        error ->
                    DiagnosticsLog.add(
                        "VIP_ROUTE_OPTIMIZER",
                        error.message
                            ?: error.javaClass
                                .name
                    )
                }
            } finally {
                running.set(false)

                mainHandler.post {
                    onUpdated
                        ?.invoke()
                }
            }
        }
    }
}
