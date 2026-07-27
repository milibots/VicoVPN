package com.vicovpn.client.net

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class ExitLocation(
    val ip: String,
    val countryCode: String = "",
    val countryName: String = "",
    val region: String = "",
    val city: String = "",
    val isp: String = "",
    val asn: String = "",
    val provider: String = ""
)

object ExitIpChecker {

    private val ipEndpoints = listOf(
        "https://api.ipify.org?format=json",
        "https://ifconfig.co/ip",
        "https://1.1.1.1/cdn-cgi/trace"
    )

    fun throughSocks(port: Int): Result<String> {
        val proxy = createProxy(port)
        val failures = mutableListOf<String>()

        ipEndpoints.forEach { endpoint ->
            runCatching {
                fetch(endpoint, proxy)
            }
                .mapCatching(::extractIp)
                .mapCatching { ip ->
                    require(isPublic(ip)) {
                        "Non-public IP returned"
                    }
                    ip
                }
                .onSuccess {
                    return Result.success(it)
                }
                .onFailure {
                    failures +=
                        "${providerName(endpoint)}: ${it.message}"
                }
        }

        return Result.failure(
            IllegalStateException(
                failures.joinToString(" | ")
            )
        )
    }

    fun locationThroughSocks(
        port: Int
    ): Result<ExitLocation> {
        val proxy = createProxy(port)

        val exitIp = throughSocks(port).getOrElse {
            return Result.failure(it)
        }

        val providers = listOf(
            GeoProvider(
                name = "ipapi.co",
                endpoint = "https://ipapi.co/$exitIp/json/",
                parser = ::parseIpApiCo
            ),
            GeoProvider(
                name = "IPinfo",
                endpoint = "https://ipinfo.io/$exitIp/json",
                parser = ::parseIpInfo
            ),
            GeoProvider(
                name = "FreeIPAPI",
                endpoint =
                    "https://free.freeipapi.com/api/json/$exitIp",
                parser = ::parseFreeIpApi
            ),
            GeoProvider(
                name = "ipwho.is",
                endpoint = "https://ipwho.is/$exitIp",
                parser = ::parseIpWho
            )
        )

        val failures = mutableListOf<String>()

        providers.forEach { provider ->
            runCatching {
                val body = fetch(provider.endpoint, proxy)
                val parsed = provider.parser(
                    JSONObject(body),
                    exitIp,
                    provider.name
                )

                require(parsed.ip == exitIp) {
                    "Provider returned a different IP"
                }

                parsed
            }
                .onSuccess {
                    return Result.success(it)
                }
                .onFailure {
                    failures +=
                        "${provider.name}: ${it.message}"
                }
        }

        /*
         * Geolocation is optional. A tunnel with a verified public exit IP
         * remains valid even when every location provider is unavailable.
         */
        return Result.success(
            ExitLocation(
                ip = exitIp,
                provider = "IP verification only"
            )
        )
    }

    private data class GeoProvider(
        val name: String,
        val endpoint: String,
        val parser: (
            JSONObject,
            String,
            String
        ) -> ExitLocation
    )

    private fun createProxy(port: Int): Proxy {
        require(port in 1..65535) {
            "Invalid SOCKS port"
        }

        return Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress.createUnresolved(
                "127.0.0.1",
                port
            )
        )
    }

    private fun fetch(
        endpoint: String,
        proxy: Proxy
    ): String {
        val connection =
            URL(endpoint).openConnection(proxy)
                    as HttpURLConnection

        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty(
            "Accept",
            "application/json,text/plain"
        )
        connection.setRequestProperty(
            "User-Agent",
            "VicoVPN/1.0"
        )

        if (connection is HttpsURLConnection) {
            connection.hostnameVerifier =
                HttpsURLConnection
                    .getDefaultHostnameVerifier()

            connection.sslSocketFactory =
                HttpsURLConnection
                    .getDefaultSSLSocketFactory()
        }

        try {
            require(
                connection.responseCode in 200..299
            ) {
                "HTTP ${connection.responseCode}"
            }

            return connection.inputStream
                .bufferedReader()
                .use {
                    it.readText().take(MAX_BODY_BYTES)
                }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractIp(body: String): String {
        val trimmed = body.trim()

        if (trimmed.startsWith("{")) {
            val objectValue = JSONObject(trimmed)

            return firstString(
                objectValue,
                "ip",
                "query",
                "address",
                "ipAddress"
            ).ifBlank {
                error("No IP field")
            }
        }

        if (trimmed.contains("ip=")) {
            return trimmed.lineSequence()
                .first {
                    it.startsWith("ip=")
                }
                .substringAfter("ip=")
                .trim()
        }

        return trimmed.lineSequence()
            .first()
            .trim()
    }

    private fun parseIpApiCo(
        json: JSONObject,
        expectedIp: String,
        provider: String
    ): ExitLocation {
        require(!json.optBoolean("error", false)) {
            json.optString("reason", "Provider error")
        }

        return ExitLocation(
            ip = firstString(json, "ip")
                .ifBlank { expectedIp },
            countryCode =
                firstString(json, "country_code", "country"),
            countryName =
                firstString(json, "country_name"),
            region = firstString(json, "region"),
            city = firstString(json, "city"),
            isp = firstString(
                json,
                "org",
                "network"
            ),
            asn = firstString(json, "asn"),
            provider = provider
        )
    }

    private fun parseIpInfo(
        json: JSONObject,
        expectedIp: String,
        provider: String
    ): ExitLocation {
        val organization = firstString(json, "org")
        val asn = organization
            .substringBefore(" ")
            .takeIf {
                it.startsWith("AS", ignoreCase = true)
            }
            .orEmpty()

        val isp = if (asn.isBlank()) {
            organization
        } else {
            organization
                .substringAfter(" ", "")
                .trim()
        }

        return ExitLocation(
            ip = firstString(json, "ip")
                .ifBlank { expectedIp },
            countryCode = firstString(json, "country"),
            countryName = "",
            region = firstString(json, "region"),
            city = firstString(json, "city"),
            isp = isp,
            asn = asn,
            provider = provider
        )
    }

    private fun parseFreeIpApi(
        json: JSONObject,
        expectedIp: String,
        provider: String
    ): ExitLocation {
        return ExitLocation(
            ip = firstString(
                json,
                "ipAddress",
                "ip"
            ).ifBlank { expectedIp },
            countryCode = firstString(
                json,
                "countryCode",
                "country_code"
            ),
            countryName = firstString(
                json,
                "countryName",
                "country_name"
            ),
            region = firstString(
                json,
                "regionName",
                "region"
            ),
            city = firstString(
                json,
                "cityName",
                "city"
            ),
            isp = firstString(
                json,
                "asnOrganization",
                "organizationName",
                "isp"
            ),
            asn = normalizeAsn(
                firstString(
                    json,
                    "asn",
                    "asnNumber"
                )
            ),
            provider = provider
        )
    }

    private fun parseIpWho(
        json: JSONObject,
        expectedIp: String,
        provider: String
    ): ExitLocation {
        require(json.optBoolean("success", true)) {
            json.optString("message", "Provider error")
        }

        val connection =
            json.optJSONObject("connection")
                ?: JSONObject()

        return ExitLocation(
            ip = firstString(json, "ip")
                .ifBlank { expectedIp },
            countryCode = firstString(
                json,
                "country_code"
            ),
            countryName = firstString(
                json,
                "country"
            ),
            region = firstString(json, "region"),
            city = firstString(json, "city"),
            isp = firstString(
                connection,
                "isp",
                "org"
            ),
            asn = normalizeAsn(
                firstString(connection, "asn")
            ),
            provider = provider
        )
    }

    private fun firstString(
        json: JSONObject,
        vararg keys: String
    ): String {
        keys.forEach { key ->
            if (
                json.has(key) &&
                !json.isNull(key)
            ) {
                val value =
                    json.opt(key)
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                if (
                    value.isNotBlank() &&
                    !value.equals(
                        "null",
                        ignoreCase = true
                    )
                ) {
                    return value
                }
            }
        }

        return ""
    }

    private fun normalizeAsn(value: String): String {
        if (value.isBlank()) return ""

        return if (
            value.startsWith(
                "AS",
                ignoreCase = true
            )
        ) {
            value.uppercase()
        } else {
            "AS$value"
        }
    }

    private fun providerName(endpoint: String): String {
        return endpoint
            .substringAfter("//")
            .substringBefore("/")
    }

    private fun isPublic(ip: String): Boolean {
        val address = InetAddress.getByName(ip)

        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }

        if (address is Inet4Address) {
            val bytes = address.address
                .map {
                    it.toInt() and 255
                }

            if (
                bytes[0] == 100 &&
                bytes[1] in 64..127
            ) {
                return false
            }

            if (
                bytes[0] == 169 &&
                bytes[1] == 254
            ) {
                return false
            }

            if (
                bytes[0] == 192 &&
                bytes[1] == 0 &&
                bytes[2] == 0
            ) {
                return false
            }

            if (bytes[0] >= 224) {
                return false
            }
        }

        return true
    }

    private const val MAX_BODY_BYTES = 16_384
}