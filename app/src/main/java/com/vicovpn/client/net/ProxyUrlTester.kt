package com.vicovpn.client.net

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.min

data class ProxyUrlTestResult(
    val endpoint: String,
    val responseCode: Int,
    val latencyMs: Long
)

enum class ProxyUrlTestFailure {
    SOCKS_UNAVAILABLE,
    TLS_FAILED,
    HTTP_TIMEOUT,
    HTTP_REJECTED,
    NETWORK_FAILED
}

class ProxyUrlTestException(
    val failure: ProxyUrlTestFailure,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(
    "$failure: $message",
    cause
)

object ProxyUrlTester {

    private val endpoints = listOf(
        "https://www.gstatic.com/generate_204",
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204"
    )

    fun testThroughSocks(
        port: Int,
        timeoutMs: Int = 8_000
    ): Result<ProxyUrlTestResult> {
        return runCatching {
            require(port in 1..65535) {
                "Invalid SOCKS port"
            }

            val effectiveTimeout =
                timeoutMs.coerceIn(
                    2_500,
                    12_000
                )

            val proxy =
                Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress.createUnresolved(
                        "127.0.0.1",
                        port
                    )
                )

            val failures =
                mutableListOf<String>()

            endpoints.forEachIndexed {
                    index,
                    endpoint ->

                val remainingEndpoints =
                    endpoints.size - index

                val perEndpointTimeout =
                    min(
                        effectiveTimeout,
                        maxOf(
                            2_500,
                            effectiveTimeout /
                                    remainingEndpoints
                        )
                    )

                runCatching {
                    testEndpoint(
                        endpoint = endpoint,
                        proxy = proxy,
                        timeoutMs =
                            perEndpointTimeout
                    )
                }.onSuccess {
                    return Result.success(it)
                }.onFailure {
                    failures +=
                        "${endpoint.substringAfter("//").substringBefore("/")}: ${it.message}"
                }
            }

            throw ProxyUrlTestException(
                failure =
                    ProxyUrlTestFailure.NETWORK_FAILED,
                message =
                    failures.joinToString(" | ")
            )
        }
    }

    private fun testEndpoint(
        endpoint: String,
        proxy: Proxy,
        timeoutMs: Int
    ): ProxyUrlTestResult {
        val started =
            System.nanoTime()

        val headResult =
            runCatching {
                execute(
                    endpoint = endpoint,
                    proxy = proxy,
                    timeoutMs = timeoutMs,
                    method = "HEAD"
                )
            }

        val response =
            headResult.getOrElse {
                execute(
                    endpoint = endpoint,
                    proxy = proxy,
                    timeoutMs = timeoutMs,
                    method = "GET"
                )
            }

        val latencyMs =
            (System.nanoTime() - started) /
                    1_000_000L

        return ProxyUrlTestResult(
            endpoint = endpoint,
            responseCode = response,
            latencyMs = latencyMs
        )
    }

    private fun execute(
        endpoint: String,
        proxy: Proxy,
        timeoutMs: Int,
        method: String
    ): Int {
        val connection =
            URL(endpoint).openConnection(proxy)
                    as HttpURLConnection

        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.requestMethod = method
        connection.setRequestProperty(
            "Accept",
            "*/*"
        )
        connection.setRequestProperty(
            "Connection",
            "close"
        )
        connection.setRequestProperty(
            "User-Agent",
            "VicoVPN/0.1"
        )

        if (
            connection is HttpsURLConnection
        ) {
            connection.hostnameVerifier =
                HttpsURLConnection
                    .getDefaultHostnameVerifier()

            connection.sslSocketFactory =
                HttpsURLConnection
                    .getDefaultSSLSocketFactory()
        }

        try {
            val code =
                connection.responseCode

            if (code !in 200..399) {
                throw ProxyUrlTestException(
                    failure =
                        ProxyUrlTestFailure.HTTP_REJECTED,
                    message = "HTTP $code"
                )
            }

            return code
        } catch (
            exception:
            java.net.SocketTimeoutException
        ) {
            throw ProxyUrlTestException(
                failure =
                    ProxyUrlTestFailure.HTTP_TIMEOUT,
                message =
                    exception.message
                        ?: "Timed out",
                cause = exception
            )
        } catch (
            exception:
            javax.net.ssl.SSLException
        ) {
            throw ProxyUrlTestException(
                failure =
                    ProxyUrlTestFailure.TLS_FAILED,
                message =
                    exception.message
                        ?: "TLS failed",
                cause = exception
            )
        } catch (
            exception:
            ProxyUrlTestException
        ) {
            throw exception
        } catch (
            exception:
            Throwable
        ) {
            throw ProxyUrlTestException(
                failure =
                    ProxyUrlTestFailure.NETWORK_FAILED,
                message =
                    exception.message
                        ?: exception.javaClass
                            .simpleName,
                cause = exception
            )
        } finally {
            connection.disconnect()
        }
    }
}