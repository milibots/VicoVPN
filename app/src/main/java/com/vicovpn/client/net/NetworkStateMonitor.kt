package com.vicovpn.client.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
    NONE
}

data class NetworkConnectionState(
    val hasInternet: Boolean,
    val isValidated: Boolean,
    val transport: NetworkTransport
)

class NetworkStateMonitor(
    context: Context,
    private val onChanged: (
        NetworkConnectionState
    ) -> Unit
) {
    private val connectivityManager =
        context.applicationContext
            .getSystemService(
                ConnectivityManager::class.java
            )

    private val started =
        AtomicBoolean(false)

    private val callback =
        object :
            ConnectivityManager
                .NetworkCallback() {

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities:
                    NetworkCapabilities
            ) {
                emit(
                    fromCapabilities(
                        capabilities
                    )
                )
            }

            override fun onLost(
                network: Network
            ) {
                emit(
                    NetworkConnectionState(
                        hasInternet = false,
                        isValidated = false,
                        transport =
                            NetworkTransport.NONE
                    )
                )
            }

            override fun onUnavailable() {
                emit(
                    NetworkConnectionState(
                        hasInternet = false,
                        isValidated = false,
                        transport =
                            NetworkTransport.NONE
                    )
                )
            }
        }

    fun start() {
        if (
            !started.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        emit(readCurrentState())

        runCatching {
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.N
            ) {
                connectivityManager
                    .registerDefaultNetworkCallback(
                        callback
                    )
            } else {
                val request =
                    NetworkRequest.Builder()
                        .addCapability(
                            NetworkCapabilities
                                .NET_CAPABILITY_INTERNET
                        )
                        .build()

                connectivityManager
                    .registerNetworkCallback(
                        request,
                        callback
                    )
            }
        }.onFailure {
            started.set(false)
            emit(readCurrentState())
        }
    }

    fun stop() {
        if (
            !started.compareAndSet(
                true,
                false
            )
        ) {
            return
        }

        runCatching {
            connectivityManager
                .unregisterNetworkCallback(
                    callback
                )
        }
    }

    fun readCurrentState():
        NetworkConnectionState {
        val network =
            connectivityManager
                .activeNetwork
                ?: return NetworkConnectionState(
                    hasInternet = false,
                    isValidated = false,
                    transport =
                        NetworkTransport.NONE
                )

        val capabilities =
            connectivityManager
                .getNetworkCapabilities(
                    network
                )
                ?: return NetworkConnectionState(
                    hasInternet = false,
                    isValidated = false,
                    transport =
                        NetworkTransport.NONE
                )

        return fromCapabilities(
            capabilities
        )
    }

    private fun fromCapabilities(
        capabilities:
            NetworkCapabilities
    ): NetworkConnectionState {
        val internet =
            capabilities.hasCapability(
                NetworkCapabilities
                    .NET_CAPABILITY_INTERNET
            )

        val validated =
            capabilities.hasCapability(
                NetworkCapabilities
                    .NET_CAPABILITY_VALIDATED
            )

        val transport =
            when {
                capabilities.hasTransport(
                    NetworkCapabilities
                        .TRANSPORT_WIFI
                ) ->
                    NetworkTransport.WIFI

                capabilities.hasTransport(
                    NetworkCapabilities
                        .TRANSPORT_CELLULAR
                ) ->
                    NetworkTransport.CELLULAR

                capabilities.hasTransport(
                    NetworkCapabilities
                        .TRANSPORT_ETHERNET
                ) ->
                    NetworkTransport.ETHERNET

                internet ->
                    NetworkTransport.OTHER

                else ->
                    NetworkTransport.NONE
            }

        return NetworkConnectionState(
            hasInternet =
                internet &&
                    validated,
            isValidated =
                validated,
            transport = transport
        )
    }

    private fun emit(
        state:
            NetworkConnectionState
    ) {
        onChanged(state)
    }
}
