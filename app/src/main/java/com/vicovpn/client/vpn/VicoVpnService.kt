package com.vicovpn.client.vpn
import android.app.Notification
import androidx.core.content.ContextCompat
import android.Manifest

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vicovpn.client.MainActivity
import com.vicovpn.client.R
import com.vicovpn.client.model.ProxyProfile
import com.vicovpn.client.net.ExitIpChecker
import com.vicovpn.client.net.ProxyUrlTester
import com.vicovpn.client.parser.ShareLinkParser
import com.vicovpn.client.server.SavedServer
import com.vicovpn.client.server.ServerOrigin
import com.vicovpn.client.server.ServerStore
import com.vicovpn.client.split.SplitTunnelMode
import com.vicovpn.client.split.SplitTunnelSettings
import com.vicovpn.client.subscription.DevicePerformanceProfile
import com.vicovpn.client.subscription.FreeServerUpdateStore
import com.vicovpn.client.subscription.FreeServerSettings
import com.vicovpn.client.subscription.NativeBatchDelayTester
import com.vicovpn.client.subscription.SubscriptionImporter
import com.vicovpn.client.subscription.SubscriptionSettings
import com.vicovpn.client.util.DiagnosticsLog
import com.vicovpn.client.xray.XrayConfigBuilder
import com.vicovpn.client.xray.XrayNativeBridge
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.vicovpn.client.server.ConnectionPrioritySettings

@SuppressLint("VpnServicePolicy")
class VicoVpnService : VpnService() {

    private val worker =
        Executors.newSingleThreadExecutor()

    private val monitor =
        Executors.newSingleThreadScheduledExecutor()

    private val discoveryWorker =
        Executors.newSingleThreadExecutor()

    private val transitionLock =
        AtomicBoolean(false)

    private val cancelFreeTest =
        AtomicBoolean(false)

    private val discoveryRunning =
        AtomicBoolean(false)

    private val manualStopRequested =
        AtomicBoolean(false)

    private val failoverQueued =
        AtomicBoolean(false)

    private var currentSocksPort = 0
    private var healthFailureCount = 0
    private var healthTick = 0

    private var discoveryTester:
        NativeBatchDelayTester? = null

    private val failedSessionProfiles =
        ConcurrentHashMap
            .newKeySet<String>()

    private var currentRawProfile =
        ""

    private var monitoringTask:
        ScheduledFuture<*>? = null

    private var tun:
        ParcelFileDescriptor? = null

    private var xray:
        XrayNativeBridge? = null

    private var current =
        VpnSnapshot()

    private var totalDown = 0L
    private var totalUp = 0L

    private var connectedAtElapsed = 0L
    private var lastNotificationUpdateAt = 0L

    private val connectivity by lazy {
        getSystemService(
            ConnectivityManager::class.java
        )
    }

    private var networkCallback:
        ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        DiagnosticsLog.add(
            "SERVICE",
            "Created; package=$packageName"
        )
        registerPhysicalNetworkCallback()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                manualStopRequested.set(true)
                failoverQueued.set(false)
                failedSessionProfiles.clear()

                worker.execute {
                    stopTunnel(
                        publishDisconnected = true
                    )

                    ServerStore(this)
                        .rotateActiveFreeServer()
                }
            }

            ACTION_CONNECT -> {
                startForeground(
                    NOTIFICATION_ID,
                    notification(
                        getString(
                            R.string.notification_connecting
                        )
                    )
                )

                val profile =
                    intent.getStringExtra(
                        EXTRA_PROFILE
                    ).orEmpty()

                manualStopRequested.set(false)
                cancelFreeTest.set(false)
                failoverQueued.set(false)
                failedSessionProfiles.clear()

                worker.execute {
                    connect(profile)
                }
            }

            ACTION_TEST_FREE_SERVERS -> {
                startForeground(
                    NOTIFICATION_ID,
                    notification(
                        getString(
                            R.string.notification_testing_free
                        )
                    )
                )

                cancelFreeTest.set(false)

                if (
                    discoveryRunning.compareAndSet(
                        false,
                        true
                    )
                ) {
                    discoveryWorker.execute {
                        testFreeServersSmart()
                    }
                }
            }

            ACTION_SMART_REFRESH -> {
                if (
                    current.stage ==
                        VpnStage.CONNECTED &&
                    discoveryRunning.compareAndSet(
                        false,
                        true
                    )
                ) {
                    discoveryWorker.execute {
                        runCatching {
                            testFreeServersSmart()
                        }.onFailure {
                            DiagnosticsLog.add(
                                "SAFE_DISCOVERY_ERROR",
                                it.message
                                    ?: it.javaClass.name
                            )
                            discoveryRunning.set(false)
                        }
                    }
                } else if (
                    current.stage !=
                        VpnStage.CONNECTED
                ) {
                    stopForeground(
                        STOP_FOREGROUND_REMOVE
                    )
                    stopSelf(startId)
                }
            }

            ACTION_CANCEL_FREE_TEST -> {
                discoveryTester
                    ?.requestStopAndKeepResults()

                publishFreeProgress(
                    stage = "finishing",
                    completed = 0,
                    total = 0,
                    working =
                        ServerStore(this)
                            .getServers()
                            .count {
                                it.origin ==
                                    ServerOrigin
                                        .FREE_SUBSCRIPTION
                            },
                    failed = 0,
                    message = getString(
                        R.string.subscription_stopped_results_kept
                    ),
                    finished = false,
                    success = false
                )
            }
        }

        return START_NOT_STICKY
    }

    override fun onRevoke() {
        cancelFreeTest.set(true)

        worker.execute {
            stopTunnel(
                publishDisconnected = true
            )
        }

        super.onRevoke()
    }

    override fun onDestroy() {
        cancelFreeTest.set(true)
        discoveryTester?.cancel()

        stopTunnel(
            publishDisconnected = false
        )

        runCatching {
            networkCallback?.let(
                connectivity::unregisterNetworkCallback
            )
        }

        worker.shutdownNow()
        monitor.shutdownNow()
        discoveryWorker.shutdownNow()

        super.onDestroy()
    }

    private fun refreshSubscriptionsAndTestSmart() {
        var handedToTester =
            false

        try {
            val settings =
                FreeServerSettings(this)

            val lastUpdate =
                settings.getLastUpdateAt()

            if (
                System.currentTimeMillis() -
                    lastUpdate <
                12L * 60L * 1_000L
            ) {
                return
            }

            val deviceProfile =
                DevicePerformanceProfile.detect(
                    this
                )

            val imported =
                SubscriptionImporter(
                    registryUrl =
                        SubscriptionSettings(this)
                            .getRegistryUrl(),
                    maxSourceCount = 40,
                    maxConfigs = 2_500,
                    downloadThreads =
                        deviceProfile.downloadThreads
                ).run {
                    // Background refresh is intentionally nontechnical.
                }.getOrThrow()

            val candidates =
                imported.configs
                    .asSequence()
                    .filter { rawLink ->
                        runCatching {
                            ShareLinkParser.parse(
                                rawLink
                            )
                        }.isSuccess
                    }
                    .distinct()
                    .toList()

            require(
                candidates.isNotEmpty()
            ) {
                "No usable smart routes"
            }

            FreeServerUpdateStore(this)
                .saveCandidates(
                    candidates
                )

            handedToTester = true
            testFreeServersSmart()
        } catch (
            throwable: Throwable
        ) {
            DiagnosticsLog.add(
                "SMART_REFRESH_ERROR",
                throwable.rootMessage()
            )

            publishFreeProgress(
                stage = "error",
                message =
                    getString(
                        R.string
                            .subscription_refresh_try_later
                    ),
                finished = true,
                success = false
            )
        } finally {
            if (!handedToTester) {
                discoveryRunning.set(false)

                if (
                    current.stage ==
                        VpnStage.CONNECTED
                ) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification(
                            getString(
                                R.string
                                    .notification_connected
                            )
                        )
                    )
                } else {
                    stopForeground(
                        STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                }
            }
        }
    }

    private fun testFreeServersSmart() {
        if (
            !ConnectionPrioritySettings(
                this
            ).getMode()
                .allowsFree
        ) {
            publishFreeProgress(
                stage = "disabled",
                message =
                    getString(
                        R.string
                            .connection_priority_free_disabled
                    ),
                finished = true,
                success = true
            )
            return
        }

        val updateStore =
            FreeServerUpdateStore(this)

        try {
            val candidates =
                updateStore.loadCandidates()

            require(candidates.isNotEmpty()) {
                "No candidates available"
            }

            val deviceProfile =
                DevicePerformanceProfile.detect(
                    this,
                    FreeServerSettings(this)
                )

            val baseParallelism =
                when {
                    deviceProfile.label
                        .startsWith("strong") ->
                        6

                    deviceProfile.label
                        .startsWith("balanced") ->
                        6

                    else ->
                        4
                }

            val parallelism =
                if (
                    current.stage ==
                        VpnStage.CONNECTED
                ) {
                    minOf(
                        baseParallelism,
                        4
                    )
                } else {
                    baseParallelism
                }

            publishFreeProgress(
                stage = "testing",
                completed = 0,
                total = candidates.size,
                working = 0,
                failed = 0,
                message = getString(
                    R.string.subscription_verifying_routes
                )
            )

            val tester =
                NativeBatchDelayTester(
                    context = this,
                    parallelism = parallelism,
                    batchPauseMs = 80L
                )

            discoveryTester = tester

            val serverStore =
                ServerStore(this)

            val result =
                tester.test(
                    rawLinks = candidates,
                    resultLimit =
                        deviceProfile.resultLimit,
                    onProgress = { progress ->
                        publishFreeProgress(
                            stage = "testing",
                            completed =
                                progress.tested,
                            total =
                                progress.total,
                            working =
                                progress.working,
                            failed =
                                (
                                    progress.tested -
                                        progress.working
                                    ).coerceAtLeast(0),
                            message =
                                if (
                                    progress.working > 0
                                ) {
                                    getString(
                                        R.string.subscription_route_ready_background
                                    )
                                } else {
                                    getString(
                                        R.string.subscription_verifying_routes
                                    )
                                }
                        )
                    },
                    onVerified = { verified ->
                        serverStore.mergeFreeServers(
                            verifiedServers =
                                listOf(verified),
                            activateBestWhenNeeded =
                                true
                        )

                        DiagnosticsLog.add(
                            "SMART_DISCOVERY",
                            "Saved immediately: ${verified.address}:${verified.port} ${verified.latencyMs}ms"
                        )
                    }
                )

            result.fold(
                onSuccess = { verified ->
                    if (verified.isEmpty()) {
                        publishFreeProgress(
                            stage = "complete",
                            completed = candidates.size,
                            total = candidates.size,
                            working = 0,
                            failed = candidates.size,
                            message = getString(
                                R.string.subscription_no_verified_routes
                            ),
                            finished = true,
                            success = false
                        )
                        return@fold
                    }

                    /*
                     * The first server has already been saved. Final replace
                     * removes stale free routes only after the complete smart
                     * scan (or after "enough for now") returns safely.
                     */
                    serverStore.replaceFreeServers(
                        verified
                    )

                    updateStore.clear()

                    FreeServerSettings(this)
                        .markUpdatedNow()

                    publishFreeProgress(
                        stage = "complete",
                        completed = verified.size,
                        total = candidates.size,
                        working = verified.size,
                        failed =
                            (
                                candidates.size -
                                    verified.size
                                ).coerceAtLeast(0),
                        message = getString(
                            R.string
                                .subscription_refresh_ready
                        ),
                        finished = true,
                        success = true
                    )
                },
                onFailure = { error ->
                    DiagnosticsLog.add(
                        "SMART_DISCOVERY_ERROR",
                        error.rootMessage()
                    )

                    val savedCount =
                        serverStore.getServers()
                            .count {
                                it.origin ==
                                    ServerOrigin
                                        .FREE_SUBSCRIPTION
                            }

                    publishFreeProgress(
                        stage = "error",
                        working = savedCount,
                        message =
                            if (savedCount > 0) {
                                getString(
                                    R.string.subscription_route_ready_background
                                )
                            } else {
                                error.rootMessage()
                            },
                        finished = true,
                        success =
                            savedCount > 0
                    )
                }
            )
        } catch (throwable: Throwable) {
            DiagnosticsLog.add(
                "SMART_DISCOVERY_ERROR",
                throwable.rootMessage()
            )

            publishFreeProgress(
                stage = "error",
                message = throwable.rootMessage(),
                finished = true,
                success = false
            )
        } finally {
            discoveryTester = null
            discoveryRunning.set(false)

            if (
                current.stage in
                    setOf(
                        VpnStage.CONNECTED,
                        VpnStage.VERIFYING,
                        VpnStage.STARTING_XRAY,
                        VpnStage.ESTABLISHING_TUN,
                        VpnStage.PREPARING
                    )
            ) {
                val text =
                    if (
                        current.stage ==
                            VpnStage.CONNECTED
                    ) {
                        getString(
                            R.string.notification_connected
                        )
                    } else {
                        getString(
                            R.string.notification_connecting
                        )
                    }

                startForeground(
                    NOTIFICATION_ID,
                    notification(text)
                )
            } else {
                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )
                stopSelf()
            }
        }
    }

    private fun publishFreeProgress(
        stage: String,
        completed: Int = 0,
        total: Int = 0,
        working: Int = 0,
        failed: Int = 0,
        message: String,
        finished: Boolean = false,
        success: Boolean = false
    ) {
        sendBroadcast(
            Intent(ACTION_FREE_TEST_PROGRESS)
                .setPackage(packageName)
                .apply {
                    putExtra(
                        EXTRA_FREE_STAGE,
                        stage
                    )
                    putExtra(
                        EXTRA_FREE_COMPLETED,
                        completed
                    )
                    putExtra(
                        EXTRA_FREE_TOTAL,
                        total
                    )
                    putExtra(
                        EXTRA_FREE_WORKING,
                        working
                    )
                    putExtra(
                        EXTRA_FREE_FAILED,
                        failed
                    )
                    putExtra(
                        EXTRA_FREE_MESSAGE,
                        message
                    )
                    putExtra(
                        EXTRA_FREE_FINISHED,
                        finished
                    )
                    putExtra(
                        EXTRA_FREE_SUCCESS,
                        success
                    )
                }
        )

        if (
            !finished &&
            current.stage !=
                VpnStage.CONNECTED
        ) {
            startForeground(
                NOTIFICATION_ID,
                notification(message)
            )
        }
    }

    private fun connect(
        rawProfile: String
    ) {
        if (
            !transitionLock.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        try {
            currentRawProfile = rawProfile
            cancelFreeTest.set(false)

            stopTunnel(
                publishDisconnected = false
            )

            update(
                VpnStage.PREPARING,
                "در حال اعتبارسنجی کانفیگ"
            )

            if (prepare(this) != null) {
                error(
                    "VPN permission is not granted"
                )
            }

            val profile =
                ShareLinkParser.parse(
                    rawProfile
                )

            current = current.copy(
                serverName = profile.name
            )

            val bridge =
                XrayNativeBridge(this)

            val version =
                bridge.initialize().getOrElse {
                    error(
                        "Xray native library is missing or invalid. " +
                            it.rootMessage()
                    )
                }

            xray = bridge

            DiagnosticsLog.add(
                "XRAY",
                "Version: $version"
            )

            update(
                VpnStage.ESTABLISHING_TUN,
                "در حال ساخت رابط TUN"
            )

            val descriptor =
                buildTun(profile.name)

            tun = descriptor

            update(
                VpnStage.STARTING_XRAY,
                "در حال راه‌اندازی هسته Xray"
            )

            val socksPort =
                allocateLoopbackPort()

            currentSocksPort = socksPort

            val config =
                XrayConfigBuilder.build(
                    profile,
                    socksPort
                )

            bridge.start(
                config,
                descriptor.fd
            ).getOrElse {
                error(
                    "Xray failed to start: " +
                        it.rootMessage()
                )
            }

            waitForSocks(
                port = socksPort,
                timeoutSeconds = 12
            )

            require(bridge.isRunning()) {
                "Xray reported stopped after startup"
            }

            update(
                VpnStage.VERIFYING,
                "در حال بررسی اتصال امن"
            )

            val urlTest =
                ProxyUrlTester.testThroughSocks(
                    port = socksPort,
                    timeoutMs = 8_000
                ).getOrElse {
                    error(
                        "Tunnel verification failed: " +
                            it.rootMessage()
                    )
                }

            val optionalLocation =
                ExitIpChecker
                    .locationThroughSocks(
                        socksPort
                    )
                    .getOrNull()

            optionalLocation?.let {
                    location ->
                ServerStore(this)
                    .updateConnectionMetadata(
                        rawLink = rawProfile,
                        exitIp = location.ip,
                        countryCode =
                            location.countryCode,
                        countryName =
                            location.countryName,
                        city = location.city,
                        isp = location.isp,
                        asn = location.asn
                    )
            }

            current = current.copy(
                stage = VpnStage.CONNECTED,
                message = "اتصال امن تأیید شد",
                exitIp =
                    optionalLocation?.ip.orEmpty(),
                countryCode =
                    optionalLocation
                        ?.countryCode
                        .orEmpty(),
                countryName =
                    optionalLocation
                        ?.countryName
                        .orEmpty(),
                region =
                    optionalLocation
                        ?.region
                        .orEmpty(),
                city =
                    optionalLocation
                        ?.city
                        .orEmpty(),
                isp =
                    optionalLocation
                        ?.isp
                        .orEmpty(),
                asn =
                    optionalLocation
                        ?.asn
                        .orEmpty(),
                locationProvider =
                    optionalLocation
                        ?.provider
                        .orEmpty()
            )

            DiagnosticsLog.add(
                "URL_TEST_PASS",
                "endpoint=${urlTest.endpoint}; code=${urlTest.responseCode}; latency=${urlTest.latencyMs}ms"
            )

            publish()

            val notificationText =
                buildString {
                    append(
                        getString(
                            R.string.notification_connected
                        )
                    )

                    if (
                        !optionalLocation
                            ?.ip
                            .isNullOrBlank()
                    ) {
                        append(" • ")
                        append(optionalLocation?.ip)
                    }
                }

            startForeground(
                NOTIFICATION_ID,
                notification(notificationText)
            )

            connectedAtElapsed =
                SystemClock.elapsedRealtime()
            lastNotificationUpdateAt = 0L

            notifySafely(
                NOTIFICATION_ID,
                connectedNotification(
                    uploadPerSecond = 0L,
                    downloadPerSecond = 0L
                )
            )

            showConnectionSuccessAlert()

            failedSessionProfiles.remove(
                rawProfile
            )
            healthFailureCount = 0
            healthTick = 0
            failoverQueued.set(false)

            startMonitoring()
        } catch (throwable: Throwable) {
            val message =
                throwable.rootMessage()

            DiagnosticsLog.add(
                "ERROR",
                message
            )

            stopTunnel(
                publishDisconnected = false
            )

            failedSessionProfiles +=
                rawProfile

            val next =
                if (
                    !manualStopRequested.get() &&
                    FreeServerSettings(this)
                        .isSmartFailoverEnabled()
                ) {
                    ServerStore(this)
                        .getNextFreeServer(
                            failedSessionProfiles
                                .toSet()
                        )
                } else {
                    null
                }

            if (next != null) {
                ServerStore(this)
                    .setActiveServer(next.id)

                current = VpnSnapshot(
                    stage =
                        VpnStage.PREPARING,
                    message =
                        getString(
                            R.string.smart_switching_route
                        ),
                    serverName = next.name
                )

                publish()

                DiagnosticsLog.add(
                    "SMART_FAILOVER",
                    "Switching to ${next.address}:${next.port}"
                )

                worker.execute {
                    Thread.sleep(350L)

                    if (
                        !manualStopRequested.get()
                    ) {
                        connect(
                            next.rawLink
                        )
                    }
                }
            } else {
                current = VpnSnapshot(
                    stage = VpnStage.ERROR,
                    message = message
                )

                publish()

                if (!discoveryRunning.get()) {
                    stopForeground(
                        STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                }
            }
        } finally {
            transitionLock.set(false)
        }
    }

    private fun buildTun(
        serverName: String
    ): ParcelFileDescriptor {
        val builder = Builder()
            .setSession(
                "VicoVPN • $serverName"
            )
            .setMtu(
                XrayConfigBuilder.MTU
            )
            .addAddress(
                XrayConfigBuilder.TUN_IPV4,
                30
            )
            .addAddress(
                XrayConfigBuilder.TUN_IPV6,
                126
            )
            .addRoute(
                "0.0.0.0",
                0
            )
            .addRoute(
                "::",
                0
            )
            .addDnsServer(
                "1.1.1.1"
            )
            .addDnsServer(
                "2606:4700:4700::1111"
            )
            .setBlocking(false)

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            builder.setMetered(false)
        }

        applySplitTunneling(
            builder
        )

        return builder.establish()
            ?: error(
                "Android refused to establish " +
                    "the VPN interface"
            )
    }

    private fun applySplitTunneling(
        builder: Builder
    ) {
        val split =
            SplitTunnelSettings(this)

        val selected =
            split.getSelectedPackages()
                .filter {
                    it.isNotBlank() &&
                        it != packageName
                }
                .toSet()

        when (split.getMode()) {
            SplitTunnelMode.ALL_APPS -> {
                addDisallowedPackageSafely(
                    builder,
                    packageName
                )
            }

            SplitTunnelMode.EXCLUDE_SELECTED -> {
                addDisallowedPackageSafely(
                    builder,
                    packageName
                )

                selected.forEach {
                    addDisallowedPackageSafely(
                        builder,
                        it
                    )
                }
            }

            SplitTunnelMode.INCLUDE_SELECTED -> {
                if (selected.isEmpty()) {
                    // Safe fallback: do not create a VPN that routes no apps.
                    addDisallowedPackageSafely(
                        builder,
                        packageName
                    )
                } else {
                    selected.forEach {
                        packageName ->
                        try {
                            builder.addAllowedApplication(
                                packageName
                            )
                        } catch (
                            exception:
                                PackageManager
                                    .NameNotFoundException
                        ) {
                            DiagnosticsLog.add(
                                "SPLIT_TUNNEL_SKIP",
                                packageName
                            )
                        }
                    }
                }
            }
        }
    }

    private fun addDisallowedPackageSafely(
        builder: Builder,
        packageName: String
    ) {
        try {
            builder.addDisallowedApplication(
                packageName
            )
        } catch (
            exception:
                PackageManager
                    .NameNotFoundException
        ) {
            DiagnosticsLog.add(
                "SPLIT_TUNNEL_SKIP",
                packageName
            )
        }
    }

    private fun allocateLoopbackPort(): Int {
        return ServerSocket().use { socket ->
            socket.reuseAddress = false

            socket.bind(
                InetSocketAddress(
                    "127.0.0.1",
                    0
                )
            )

            socket.localPort
        }
    }

    private fun waitForSocks(
        port: Int,
        timeoutSeconds: Int
    ) {
        val deadline =
            System.nanoTime() +
                TimeUnit.SECONDS.toNanos(
                    timeoutSeconds.toLong()
                )

        var lastError:
            Throwable? = null

        while (
            System.nanoTime() < deadline
        ) {
            check(!cancelFreeTest.get()) {
                "Free server test cancelled"
            }

            try {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(
                            "127.0.0.1",
                            port
                        ),
                        400
                    )
                }

                return
            } catch (
                throwable: Throwable
            ) {
                lastError = throwable
                Thread.sleep(150)
            }
        }

        error(
            "Xray SOCKS listener did not open: " +
                lastError?.message
        )
    }

    private fun startMonitoring() {
        monitoringTask?.cancel(true)

        monitoringTask =
            monitor.scheduleAtFixedRate(
                {
                    val bridge =
                        xray
                            ?: return@scheduleAtFixedRate

                    if (!bridge.isRunning()) {
                        DiagnosticsLog.add(
                            "HEALTH",
                            "Xray stopped unexpectedly"
                        )

                        queueSmartFailover(
                            getString(
                                R.string.smart_core_stopped
                            )
                        )

                        return@scheduleAtFixedRate
                    }

                    val upDelta =
                        bridge.queryStats("uplink")
                            .coerceAtLeast(0L)

                    val downDelta =
                        bridge.queryStats("downlink")
                            .coerceAtLeast(0L)

                    totalUp += upDelta
                    totalDown += downDelta

                    TrafficStore(this)
                        .add(
                            downloadBytes = downDelta,
                            uploadBytes = upDelta
                        )

                    current = current.copy(
                        uploadBytes = totalUp,
                        downloadBytes = totalDown
                    )

                    publish()

                    maybeUpdateConnectedNotification(
                        uploadPerSecond = upDelta,
                        downloadPerSecond = downDelta
                    )

                    healthTick++

                    if (
                        healthTick % 900 == 0 &&
                        FreeServerSettings(this)
                            .isBackgroundDiscoveryEnabled() &&
                        discoveryRunning.compareAndSet(
                            false,
                            true
                        )
                    ) {
                        discoveryWorker.execute {
                            runCatching {
                                testFreeServersSmart()
                            }.onFailure {
                                DiagnosticsLog.add(
                                    "CONNECTED_DISCOVERY_ERROR",
                                    it.message
                                        ?: it.javaClass.name
                                )
                                discoveryRunning.set(false)
                            }
                        }
                    }

                    if (
                        healthTick % 12 == 0 &&
                        currentSocksPort > 0 &&
                        current.stage ==
                            VpnStage.CONNECTED
                    ) {
                        val healthy =
                            ProxyUrlTester
                                .testThroughSocks(
                                    port =
                                        currentSocksPort,
                                    timeoutMs =
                                        4_500
                                )
                                .isSuccess

                        if (healthy) {
                            healthFailureCount = 0
                        } else {
                            healthFailureCount++

                            DiagnosticsLog.add(
                                "HEALTH",
                                "Active route check failed $healthFailureCount/2"
                            )

                            if (
                                healthFailureCount >= 2
                            ) {
                                queueSmartFailover(
                                    getString(
                                        R.string.smart_route_unhealthy
                                    )
                                )
                            }
                        }
                    }
                },
                1,
                1,
                TimeUnit.SECONDS
            )
    }

    private fun queueSmartFailover(
        reason: String
    ) {
        if (
            manualStopRequested.get() ||
            !FreeServerSettings(this)
                .isSmartFailoverEnabled() ||
            !failoverQueued.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        worker.execute {
            val failedRaw =
                currentRawProfile

            stopTunnel(
                publishDisconnected = false
            )

            if (failedRaw.isNotBlank()) {
                failedSessionProfiles +=
                    failedRaw
            }

            val next =
                ServerStore(this)
                    .getNextFreeServer(
                        failedSessionProfiles
                            .toSet()
                    )

            if (next != null) {
                ServerStore(this)
                    .setActiveServer(
                        next.id
                    )

                current =
                    VpnSnapshot(
                        stage =
                            VpnStage.PREPARING,
                        message =
                            getString(
                                R.string.smart_switching_route
                            ),
                        serverName =
                            next.name
                    )

                publish()

                DiagnosticsLog.add(
                    "SMART_FAILOVER",
                    "$reason -> ${next.address}:${next.port}"
                )

                Thread.sleep(350L)
                failoverQueued.set(false)

                if (
                    !manualStopRequested.get()
                ) {
                    connect(
                        next.rawLink
                    )
                }
            } else {
                current =
                    VpnSnapshot(
                        stage =
                            VpnStage.ERROR,
                        message =
                            getString(
                                R.string.smart_no_backup_route
                            )
                    )

                publish()
                failoverQueued.set(false)

                if (
                    !discoveryRunning.get()
                ) {
                    stopForeground(
                        STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                }
            }
        }
    }

    private fun stopTestResources() {
        xray?.stop()
        xray = null

        runCatching {
            tun?.close()
        }

        tun = null
    }

    private fun stopTunnel(
        publishDisconnected: Boolean
    ) {
        monitoringTask?.cancel(true)
        monitoringTask = null

        if (
            current.stage !=
            VpnStage.DISCONNECTED &&
            current.stage !=
            VpnStage.ERROR
        ) {
            current = current.copy(
                stage = VpnStage.STOPPING,
                message =
                    "در حال آزادسازی منابع"
            )

            publish()
        }

        stopTestResources()

        totalDown = 0
        totalUp = 0
        currentSocksPort = 0
        healthFailureCount = 0
        healthTick = 0

        if (publishDisconnected) {
            current = VpnSnapshot()
            publish()

            if (
                discoveryRunning.get()
            ) {
                startForeground(
                    NOTIFICATION_ID,
                    notification(
                        getString(
                            R.string.notification_testing_free
                        )
                    )
                )
            } else {
                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )

                stopSelf()
            }
        }
    }

    private fun registerPhysicalNetworkCallback() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.P
        ) {
            return
        }

        val callback =
            object :
                ConnectivityManager
                    .NetworkCallback() {

                override fun onAvailable(
                    network: Network
                ) {
                    runCatching {
                        setUnderlyingNetworks(
                            arrayOf(network)
                        )
                    }

                    DiagnosticsLog.add(
                        "NETWORK",
                        "Underlying network available: " +
                            network
                    )
                }

                override fun onLost(
                    network: Network
                ) {
                    DiagnosticsLog.add(
                        "NETWORK",
                        "Underlying network lost: " +
                            network
                    )
                }
            }

        val request =
            NetworkRequest.Builder()
                .addCapability(
                    NetworkCapabilities
                        .NET_CAPABILITY_INTERNET
                )
                .addCapability(
                    NetworkCapabilities
                        .NET_CAPABILITY_NOT_VPN
                )
                .build()

        runCatching {
            connectivity.registerNetworkCallback(
                request,
                callback
            )

            networkCallback = callback
        }
    }

    private fun update(
        stage: VpnStage,
        message: String
    ) {
        current = current.copy(
            stage = stage,
            message = message
        )

        publish()
    }

    private fun publish() {
        VpnStateRepository.publish(
            this,
            current
        )
    }

    private fun maybeUpdateConnectedNotification(
        uploadPerSecond: Long,
        downloadPerSecond: Long
    ) {
        if (
            current.stage !=
                VpnStage.CONNECTED
        ) {
            return
        }

        val now =
            SystemClock.elapsedRealtime()

        if (
            now -
                lastNotificationUpdateAt <
            1_500L
        ) {
            return
        }

        lastNotificationUpdateAt = now

        notifySafely(
            NOTIFICATION_ID,
            connectedNotification(
                uploadPerSecond,
                downloadPerSecond
            )
        )
    }

    private fun canPostNotifications(): Boolean {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return NotificationManagerCompat
            .from(this)
            .areNotificationsEnabled()
    }

    /**
     * Lint cannot infer the permission guarantee through this custom helper,
     * so MissingPermission is suppressed only after an explicit runtime
     * permission check and SecurityException handling.
     */
    @SuppressLint("MissingPermission")
    private fun notifySafely(
        notificationId: Int,
        notification: Notification
    ): Boolean {
        if (!canPostNotifications()) {
            DiagnosticsLog.add(
                "NOTIFICATION_PERMISSION",
                "Skipped notification id=$notificationId; permission disabled"
            )
            return false
        }

        return try {
            NotificationManagerCompat
                .from(this)
                .notify(
                    notificationId,
                    notification
                )
            true
        } catch (
            securityException: SecurityException
        ) {
            DiagnosticsLog.add(
                "NOTIFICATION_PERMISSION",
                securityException.message
                    ?: securityException.javaClass.name
            )
            false
        }
    }

    private fun showConnectionSuccessAlert() {
        val contentIntent =
            PendingIntent.getActivity(
                this,
                31,
                Intent(
                    this,
                    MainActivity::class.java
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val location =
            listOf(
                countryFlag(
                    current.countryCode
                ),
                current.countryName,
                current.city
            )
                .filter {
                    it.isNotBlank()
                }
                .distinct()
                .joinToString(" ")

        val message =
            if (location.isBlank()) {
                getString(
                    R.string
                        .connection_success_notification_body
                )
            } else {
                getString(
                    R.string
                        .connection_success_notification_body_location,
                    location
                )
            }

        val alert =
            NotificationCompat.Builder(
                this,
                CONNECTION_ALERT_CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_vico_shield
                )
                .setContentTitle(
                    getString(
                        R.string
                            .connection_success_notification_title
                    )
                )
                .setContentText(message)
                .setContentIntent(
                    contentIntent
                )
                .setColor(
                    android.graphics.Color.rgb(
                        34,
                        197,
                        94
                    )
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_STATUS
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setTimeoutAfter(5_000L)
                .setDefaults(
                    NotificationCompat
                        .DEFAULT_SOUND or
                        NotificationCompat
                            .DEFAULT_VIBRATE
                )
                .build()

        notifySafely(
            CONNECTION_ALERT_NOTIFICATION_ID,
            alert
        )
    }

    private fun connectedNotification(
        uploadPerSecond: Long,
        downloadPerSecond: Long
    ): android.app.Notification {
        val contentIntent =
            PendingIntent.getActivity(
                this,
                1,
                Intent(
                    this,
                    MainActivity::class.java
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val stopIntent =
            PendingIntent.getService(
                this,
                2,
                Intent(
                    this,
                    VicoVpnService::class.java
                ).setAction(
                    ACTION_STOP
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val activeServer =
            ServerStore(this)
                .getActiveServer()

        val location =
            listOf(
                countryFlag(
                    current.countryCode
                ),
                current.countryName,
                current.city
            )
                .filter {
                    it.isNotBlank()
                }
                .distinct()
                .joinToString(" ")
                .ifBlank {
                    current.serverName
                }

        val latency =
            activeServer?.latencyMs
                ?.takeIf {
                    it > 0
                }
                ?.let {
                    "${it}ms"
                }
                .orEmpty()

        val speedLine =
            "⬆ ${formatNotificationSpeed(uploadPerSecond)}  •  " +
                "⬇ ${formatNotificationSpeed(downloadPerSecond)}"

        val detailLine =
            listOf(
                location,
                latency,
                current.exitIp
            )
                .filter {
                    it.isNotBlank()
                }
                .joinToString(" • ")

        val totalLine =
            "📊 ↑ ${Formatter.formatShortFileSize(this, totalUp)}  •  " +
                "↓ ${Formatter.formatShortFileSize(this, totalDown)}"

        val elapsed =
            if (connectedAtElapsed > 0L) {
                SystemClock.elapsedRealtime() -
                    connectedAtElapsed
            } else {
                0L
            }

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                R.drawable.ic_vico_shield
            )
            .setContentTitle(
                "🛡️ ${getString(R.string.app_name)} • ${getString(R.string.status_connected)}"
            )
            .setContentText(
                speedLine
            )
            .setSubText(
                detailLine
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        listOf(
                            speedLine,
                            detailLine,
                            totalLine
                        )
                            .filter {
                                it.isNotBlank()
                            }
                            .joinToString("\n")
                    )
            )
            .setContentIntent(
                contentIntent
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(
                System.currentTimeMillis() -
                    elapsed
            )
            .setUsesChronometer(
                true
            )
            .addAction(
                R.drawable.ic_power,
                "⏹ ${getString(R.string.notification_stop)}",
                stopIntent
            )
            .build()
    }

    private fun formatNotificationSpeed(
        bytesPerSecond: Long
    ): String {
        return Formatter.formatShortFileSize(
            this,
            bytesPerSecond.coerceAtLeast(0L)
        ) + "/s"
    }

    private fun countryFlag(
        countryCode: String
    ): String {
        val normalized =
            countryCode.trim()
                .uppercase()

        if (
            normalized.length != 2 ||
            normalized.any {
                it !in 'A'..'Z'
            }
        ) {
            return ""
        }

        val first =
            Character.toChars(
                0x1F1E6 +
                    normalized[0].code -
                    'A'.code
            )

        val second =
            Character.toChars(
                0x1F1E6 +
                    normalized[1].code -
                    'A'.code
            )

        return String(first) +
            String(second)
    }

    private fun createChannel() {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(
                        R.string.notification_channel
                    ),
                    NotificationManager
                        .IMPORTANCE_LOW
                )
            )


            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(
                NotificationChannel(
                    CONNECTION_ALERT_CHANNEL_ID,
                    getString(
                        R.string
                            .connection_success_channel_name
                    ),
                    NotificationManager
                        .IMPORTANCE_HIGH
                ).apply {
                    description =
                        getString(
                            R.string
                                .connection_success_channel_description
                        )
                    enableVibration(true)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun notification(
        text: String
    ): android.app.Notification {
        val contentIntent =
            PendingIntent.getActivity(
                this,
                1,
                Intent(
                    this,
                    MainActivity::class.java
                ),
                PendingIntent
                    .FLAG_UPDATE_CURRENT or
                    PendingIntent
                        .FLAG_IMMUTABLE
            )

        val stopIntent =
            PendingIntent.getService(
                this,
                2,
                Intent(
                    this,
                    VicoVpnService::class.java
                ).setAction(ACTION_STOP),
                PendingIntent
                    .FLAG_UPDATE_CURRENT or
                    PendingIntent
                        .FLAG_IMMUTABLE
            )

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setSmallIcon(
                R.drawable.ic_vico_shield
            )
            .setContentTitle(
                getString(R.string.app_name)
            )
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(
                NotificationCompat
                    .CATEGORY_SERVICE
            )
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                getString(
                    R.string.notification_stop
                ),
                stopIntent
            )
            .build()
    }

    private fun Throwable.rootMessage(): String {
        var root:
            Throwable = this

        while (
            root.cause != null &&
            root.cause !== root
        ) {
            root = root.cause!!
        }

        return root.message
            ?: root.javaClass.simpleName
    }

    companion object {
        const val ACTION_CONNECT =
            "com.vicovpn.client.CONNECT"

        const val ACTION_STOP =
            "com.vicovpn.client.STOP"

        const val ACTION_TEST_FREE_SERVERS =
            "com.vicovpn.client.TEST_FREE_SERVERS"

        const val ACTION_CANCEL_FREE_TEST =
            "com.vicovpn.client.CANCEL_FREE_TEST"

        const val ACTION_SMART_REFRESH =
            "com.vicovpn.client.SMART_REFRESH"

        const val ACTION_FREE_TEST_PROGRESS =
            "com.vicovpn.client.FREE_TEST_PROGRESS"

        const val EXTRA_PROFILE =
            "profile"

        const val EXTRA_FREE_STAGE =
            "free_stage"

        const val EXTRA_FREE_COMPLETED =
            "free_completed"

        const val EXTRA_FREE_TOTAL =
            "free_total"

        const val EXTRA_FREE_WORKING =
            "free_working"

        const val EXTRA_FREE_FAILED =
            "free_failed"

        const val EXTRA_FREE_MESSAGE =
            "free_message"

        const val EXTRA_FREE_FINISHED =
            "free_finished"

        const val EXTRA_FREE_SUCCESS =
            "free_success"

        private const val CHANNEL_ID =
            "vicovpn_connection"

        private const val CONNECTION_ALERT_CHANNEL_ID =
            "vicovpn_connection_alerts"

        private const val CONNECTION_ALERT_NOTIFICATION_ID =
            4402

        private const val NOTIFICATION_ID =
            4401
    }
}
