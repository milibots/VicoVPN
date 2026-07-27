package com.vicovpn.client.xray

import android.content.Context
import com.vicovpn.client.util.DiagnosticsLog
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Semaphore

class XrayNativeBridge(
    private val context: Context
) {
    companion object {
        private val environmentInitialized =
            AtomicBoolean(false)

        private val environmentLock = Any()

        /*
         * The native Go bridge can expose measureOutboundDelay, but not every
         * build is safe under unlimited parallel JNI calls. Four simultaneous
         * calls is still much faster than serial testing and avoids the null
         * InvocationTargetException failures seen with 14 calls at once.
         */
        private val nativeDelaySlots =
            Semaphore(6, true)
    }

    private var controller: Any? = null

    fun initialize(): Result<String> =
        runCatching {
            val appContext =
                context.applicationContext

            runCatching {
                val seq =
                    Class.forName("go.Seq")

                val method =
                    seq.methods.firstOrNull {
                        it.name == "setContext" &&
                            it.parameterCount == 1
                    }

                method?.invoke(
                    null,
                    appContext
                )
            }.onFailure {
                DiagnosticsLog.add(
                    "XRAY",
                    "go.Seq context warning: ${it.message}"
                )
            }

            initializeEnvironmentOnce()

            val lib =
                Class.forName(
                    "libv2ray.Libv2ray"
                )

            val callbackType =
                Class.forName(
                    "libv2ray.CoreCallbackHandler"
                )

            val callback =
                Proxy.newProxyInstance(
                    callbackType.classLoader,
                    arrayOf(callbackType),
                    CallbackHandler()
                )

            controller =
                lib.methods.first {
                    it.name ==
                        "newCoreController" &&
                        it.parameterCount == 1
                }.invoke(
                    null,
                    callback
                )

            val version =
                runCatching {
                    lib.methods.first {
                        it.name ==
                            "checkVersionX" &&
                            it.parameterCount == 0
                    }.invoke(null)
                        .toString()
                }.getOrDefault("unknown")

            DiagnosticsLog.add(
                "XRAY",
                "Initialized native core $version"
            )

            version
        }

    /**
     * Uses the native core's standalone outbound-delay API.
     *
     * This does not create an Android VPN interface and does not start the
     * long-running controller. It is the same class of API used by modern
     * V2Ray clients for fast batch real-delay testing.
     */
    fun measureOutboundDelay(
        config: String,
        testUrl: String
    ): Result<Long> =
        runCatching {
            initializeEnvironmentOnce()

            nativeDelaySlots.acquire()

            try {
                val lib =
                    Class.forName(
                        "libv2ray.Libv2ray"
                    )

                val method =
                    lib.methods.firstOrNull {
                        it.name ==
                            "measureOutboundDelay" &&
                            it.parameterCount == 2
                    } ?: error(
                        "Native core does not expose measureOutboundDelay"
                    )

                val result =
                    try {
                        method.invoke(
                            null,
                            config,
                            testUrl
                        )
                    } catch (
                        exception:
                            InvocationTargetException
                    ) {
                        throw unwrapInvocationException(
                            exception
                        )
                    }

                val delay =
                    (result as? Number)
                        ?.toLong()
                        ?: error(
                            "Unexpected native delay result: ${result?.javaClass?.name ?: "null"}"
                        )

                require(delay >= 0L) {
                    "Native delay returned $delay"
                }

                delay
            } finally {
                nativeDelaySlots.release()
            }
        }

    private fun unwrapInvocationException(
        exception: InvocationTargetException
    ): Throwable {
        var root: Throwable =
            exception.targetException
                ?: exception.cause
                ?: exception

        while (
            root.cause != null &&
            root.cause !== root
        ) {
            root = root.cause!!
        }

        val message =
            root.message
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: root.toString()
                    .takeIf {
                        it.isNotBlank()
                    }
                ?: root.javaClass.name

        return IllegalStateException(
            "${root.javaClass.name}: $message",
            root
        )
    }

    fun start(
        config: String,
        tunFd: Int
    ): Result<Unit> =
        runCatching {
            val currentController =
                controller
                    ?: error(
                        "Xray controller is not initialized"
                    )

            val method =
                currentController
                    .javaClass
                    .methods
                    .first {
                        it.name == "startLoop" &&
                            it.parameterCount == 2
                    }

            method.invoke(
                currentController,
                config,
                coerceNumber(
                    tunFd,
                    method.parameterTypes[1]
                )
            )

            DiagnosticsLog.add(
                "XRAY",
                "Core startLoop returned"
            )
        }

    fun stop() {
        val currentController =
            controller ?: return

        runCatching {
            currentController
                .javaClass
                .methods
                .first {
                    it.name == "stopLoop" &&
                        it.parameterCount == 0
                }.invoke(currentController)
        }.onFailure {
            DiagnosticsLog.add(
                "XRAY",
                "Stop failed: ${it.message}"
            )
        }

        controller = null
    }

    fun isRunning(): Boolean {
        val currentController =
            controller ?: return false

        return runCatching {
            val getter =
                currentController
                    .javaClass
                    .methods
                    .firstOrNull {
                        it.name ==
                            "getIsRunning" &&
                            it.parameterCount == 0
                    }

            (
                getter?.invoke(
                    currentController
                ) as? Boolean
                ) ?: currentController
                .javaClass
                .fields
                .firstOrNull {
                    it.name == "IsRunning"
                }
                ?.getBoolean(
                    currentController
                )
                ?: true
        }.getOrDefault(false)
    }

    fun queryStats(
        direction: String
    ): Long {
        val currentController =
            controller ?: return 0

        return runCatching {
            val method =
                currentController
                    .javaClass
                    .methods
                    .first {
                        it.name == "queryStats" &&
                            it.parameterCount == 2
                    }

            (
                method.invoke(
                    currentController,
                    "proxy",
                    direction
                ) as Number
                ).toLong()
        }.getOrDefault(0)
    }

    private fun initializeEnvironmentOnce() {
        if (environmentInitialized.get()) {
            return
        }

        synchronized(environmentLock) {
            if (environmentInitialized.get()) {
                return
            }

            val lib =
                Class.forName(
                    "libv2ray.Libv2ray"
                )

            val assetPath =
                context.filesDir
                    .resolve("xray-assets")
                    .apply {
                        mkdirs()
                    }
                    .absolutePath

            lib.methods.first {
                it.name == "initCoreEnv" &&
                    it.parameterCount == 2
            }.invoke(
                null,
                assetPath,
                stableInstallKey()
            )

            environmentInitialized.set(true)
        }
    }

    private fun stableInstallKey(): String {
        val preferences =
            context.getSharedPreferences(
                "secure_install",
                Context.MODE_PRIVATE
            )

        return preferences.getString(
            "xudp_key",
            null
        ) ?: UUID.randomUUID()
            .toString()
            .also {
                preferences.edit()
                    .putString(
                        "xudp_key",
                        it
                    )
                    .apply()
            }
    }

    private fun coerceNumber(
        value: Int,
        type: Class<*>
    ): Any =
        when (type) {
            java.lang.Long.TYPE,
            java.lang.Long::class.java ->
                value.toLong()

            java.lang.Short.TYPE,
            java.lang.Short::class.java ->
                value.toShort()

            else -> value
        }

    private class CallbackHandler :
        InvocationHandler {

        override fun invoke(
            proxy: Any,
            method: Method,
            args: Array<out Any?>?
        ): Any? {
            DiagnosticsLog.add(
                "XRAY_CALLBACK",
                method.name +
                    (
                        args?.joinToString(
                            prefix = "(",
                            postfix = ")"
                        ) ?: ""
                        )
            )

            return when (
                method.returnType
            ) {
                java.lang.Long.TYPE,
                java.lang.Long::class.java ->
                    0L

                java.lang.Integer.TYPE,
                java.lang.Integer::class.java ->
                    0

                java.lang.Boolean.TYPE,
                java.lang.Boolean::class.java ->
                    true

                else -> null
            }
        }
    }
}
