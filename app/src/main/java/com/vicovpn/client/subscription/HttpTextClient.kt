package com.vicovpn.client.subscription

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

object HttpTextClient {

    const val REGISTRY_USER_AGENT =
        "VicoVPN/0.1"

    /*
     * Many subscription panels use the User-Agent to decide whether to return
     * a browser HTML page or raw share links. A widely supported subscription
     * client identifier makes these endpoints return their client format.
     */
    const val SUBSCRIPTION_USER_AGENT =
        "v2rayNG/2.2.6"

    fun get(
        url: String,
        accept: String =
            "text/plain, application/octet-stream, application/base64, */*;q=0.1",
        maxBytes: Int = 8_000_000,
        userAgent: String =
            SUBSCRIPTION_USER_AGENT,
        noCache: Boolean = true
    ): String {
        require(maxBytes in 1..16_000_000) {
            "Invalid maximum response size"
        }

        val connection =
            URL(url).openConnection()
                as HttpURLConnection

        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
        connection.instanceFollowRedirects = true
        connection.useCaches = !noCache

        connection.setRequestProperty(
            "Accept",
            accept
        )
        connection.setRequestProperty(
            "User-Agent",
            userAgent
        )
        connection.setRequestProperty(
            "Accept-Charset",
            "utf-8"
        )

        if (noCache) {
            connection.setRequestProperty(
                "Cache-Control",
                "no-cache, no-store, max-age=0"
            )
            connection.setRequestProperty(
                "Pragma",
                "no-cache"
            )
        }

        try {
            val responseCode =
                connection.responseCode

            require(responseCode in 200..299) {
                "HTTP $responseCode"
            }

            val bytes =
                connection.inputStream
                    .buffered()
                    .use { input ->
                        val output =
                            ByteArrayOutputStream()

                        val buffer =
                            ByteArray(16 * 1024)

                        var total = 0

                        while (true) {
                            val read =
                                input.read(buffer)

                            if (read < 0) {
                                break
                            }

                            total += read

                            require(total <= maxBytes) {
                                "Subscription response is larger than $maxBytes bytes"
                            }

                            output.write(
                                buffer,
                                0,
                                read
                            )
                        }

                        output.toByteArray()
                    }

            val charset =
                detectCharset(
                    connection.contentType
                )

            return String(
                bytes,
                charset
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun detectCharset(
        contentType: String?
    ): Charset {
        val declared =
            contentType
                ?.substringAfter(
                    "charset=",
                    ""
                )
                ?.substringBefore(';')
                ?.trim()
                ?.trim('"', '\'')
                .orEmpty()

        return runCatching {
            if (declared.isBlank()) {
                Charsets.UTF_8
            } else {
                Charset.forName(declared)
            }
        }.getOrDefault(
            Charsets.UTF_8
        )
    }
}
