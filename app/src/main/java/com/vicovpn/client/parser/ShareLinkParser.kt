package com.vicovpn.client.parser

import com.vicovpn.client.model.ProxyProfile
import com.vicovpn.client.model.TransportSettings
import com.vicovpn.client.util.JsonCodec
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

object ShareLinkParser {

    private val supportedShadowsocksMethods =
        setOf(
            "aes-128-gcm",
            "aes-192-gcm",
            "aes-256-gcm",
            "chacha20-ietf-poly1305",
            "xchacha20-ietf-poly1305",
            "2022-blake3-aes-128-gcm",
            "2022-blake3-aes-256-gcm",
            "2022-blake3-chacha20-poly1305",
            "none",
            "plain"
        )

    fun parse(rawInput: String): ProxyProfile {
        val raw = rawInput
            .trim()
            .removePrefix("\uFEFF")

        require(raw.isNotEmpty()) {
            "Profile is empty"
        }

        return when {
            raw.startsWith("vmess://", true) ->
                parseVmess(raw)

            raw.startsWith("vless://", true) ->
                parseVless(raw)

            raw.startsWith("trojan://", true) ->
                parseTrojan(raw)

            raw.startsWith("ss://", true) ->
                parseShadowsocks(raw)

            else ->
                error("Unsupported profile scheme")
        }
    }

    private fun parseVmess(
        raw: String
    ): ProxyProfile.Vmess {
        val payload =
            raw.substringAfter("vmess://")
                .substringBefore('#')
                .trim()

        val json =
            decodeBase64Text(payload)

        val obj =
            JsonCodec.parseObject(json)

        val address =
            obj.string("add").trim()

        val port =
            obj.int("port")

        val id =
            obj.string("id").trim()

        validateEndpoint(address, port)
        validateUuid(id)

        val network =
            obj.stringOr(
                "net",
                "tcp"
            ).normalizeNetwork()

        val security =
            obj.stringOr(
                "tls",
                "none"
            ).ifBlank {
                "none"
            }.lowercase()

        val path =
            obj.stringOr(
                "path",
                ""
            )

        val serviceName =
            when {
                network != "grpc" -> ""
                obj.stringOr(
                    "serviceName",
                    ""
                ).isNotBlank() ->
                    obj.stringOr(
                        "serviceName",
                        ""
                    )

                else ->
                    path.removePrefix("/")
            }

        val grpcMode =
            obj.stringOr(
                "mode",
                ""
            ).lowercase()

        val transport =
            TransportSettings(
                network = network,
                security = security,
                serverName =
                    obj.stringOr("sni", ""),
                host =
                    obj.stringOr("host", ""),
                path = path,
                serviceName = serviceName,
                headerType =
                    obj.stringOr(
                        "type",
                        "none"
                    ).ifBlank {
                        "none"
                    },
                fingerprint =
                    obj.stringOr("fp", ""),
                alpn =
                    obj.stringOr(
                        "alpn",
                        ""
                    ).split(',')
                        .map(String::trim)
                        .filter(String::isNotBlank),
                xhttpMode = grpcMode,
                grpcAuthority =
                    obj.stringOr(
                        "authority",
                        obj.stringOr("host", "")
                    ),
                grpcMultiMode =
                    grpcMode == "multi",
                realityPublicKey =
                    obj.stringOr("pbk", ""),
                realityShortId =
                    obj.stringOr("sid", ""),
                realitySpiderX =
                    obj.stringOr("spx", "")
            )

        validateTransport(transport)

        return ProxyProfile.Vmess(
            name =
                obj.stringOr(
                    "ps",
                    address
                ).ifBlank {
                    address
                },
            address = address,
            port = port,
            id = id,
            alterId =
                obj.intOr(
                    "aid",
                    0
                ),
            cipher =
                obj.stringOr(
                    "scy",
                    "auto"
                ).ifBlank {
                    "auto"
                },
            transport = transport
        )
    }

    private fun parseVless(
        raw: String
    ): ProxyProfile.Vless {
        val uri = URI(raw)

        val id =
            decodeComponent(
                uri.rawUserInfo
                    ?: error(
                        "Missing VLESS UUID"
                    )
            ).trim()

        validateUuid(id)

        val address =
            uri.host
                ?: parseHostFallback(raw)

        val port = uri.port

        validateEndpoint(address, port)

        val query = query(uri)

        val encryption =
            query["encryption"]
                ?.ifBlank {
                    "none"
                }
                ?: "none"

        /*
         * Xray currently supports the normal VLESS encryption mode here.
         * Experimental post-quantum encryption strings from subscription
         * metadata are deliberately skipped instead of producing broken
         * outbound JSON.
         */
        require(
            encryption.equals(
                "none",
                ignoreCase = true
            )
        ) {
            "Unsupported VLESS encryption: $encryption"
        }

        return ProxyProfile.Vless(
            name =
                fragmentName(
                    uri,
                    address
                ),
            address = address,
            port = port,
            id = id,
            encryption =
                encryption.lowercase(),
            transport =
                transportFromQuery(query)
        )
    }

    private fun parseTrojan(
        raw: String
    ): ProxyProfile.Trojan {
        val uri = URI(raw)

        val password =
            decodeComponent(
                uri.rawUserInfo
                    ?: error(
                        "Missing Trojan password"
                    )
            )

        require(password.isNotBlank()) {
            "Trojan password is empty"
        }

        val address =
            uri.host
                ?: parseHostFallback(raw)

        val port = uri.port

        validateEndpoint(address, port)

        val query = query(uri)

        return ProxyProfile.Trojan(
            name =
                fragmentName(
                    uri,
                    address
                ),
            address = address,
            port = port,
            password = password,
            transport =
                transportFromQuery(query)
                    .let {
                        if (
                            it.security == "none"
                        ) {
                            it.copy(
                                security = "tls"
                            )
                        } else {
                            it
                        }
                    }
        )
    }

    private fun parseShadowsocks(
        raw: String
    ): ProxyProfile.Shadowsocks {
        val payload =
            raw.substringAfter("ss://")
                .trim()

        val name =
            payload.substringAfter(
                '#',
                "Shadowsocks"
            ).let(
                ::decodeComponent
            )

        val beforeFragment =
            payload.substringBefore('#')

        val rawQuery =
            beforeFragment.substringAfter(
                '?',
                ""
            )

        if (rawQuery.isNotBlank()) {
            val parsedQuery =
                parseRawQuery(rawQuery)

            require(
                parsedQuery["plugin"]
                    .isNullOrBlank()
            ) {
                "Shadowsocks plugin links are not supported"
            }
        }

        val body =
            beforeFragment.substringBefore('?')
                .trim()

        val parsed =
            parseShadowsocksBody(body)

        validateEndpoint(
            parsed.address,
            parsed.port
        )

        val normalizedMethod =
            parsed.method
                .trim()
                .lowercase()

        require(
            normalizedMethod in
                supportedShadowsocksMethods
        ) {
            "Unsupported Shadowsocks cipher: $normalizedMethod"
        }

        require(
            parsed.password.isNotBlank()
        ) {
            "Shadowsocks password is empty"
        }

        return ProxyProfile.Shadowsocks(
            name =
                name.ifBlank {
                    parsed.address
                },
            address = parsed.address,
            port = parsed.port,
            method = normalizedMethod,
            password = parsed.password
        )
    }

    private fun parseShadowsocksBody(
        body: String
    ): ParsedShadowsocks {
        /*
         * SIP002 form:
         * ss://BASE64(method:password)@host:port
         */
        if (body.contains('@')) {
            val credentialsPart =
                body.substringBeforeLast('@')

            val endpointPart =
                body.substringAfterLast('@')

            val decodedCredentials =
                if (
                    credentialsPart
                        .contains(':')
                ) {
                    decodeComponent(
                        credentialsPart
                    )
                } else {
                    decodeBase64Text(
                        credentialsPart
                    )
                }

            val credentials =
                splitCredentials(
                    decodedCredentials
                )

            val endpoint =
                splitHostPort(endpointPart)

            return ParsedShadowsocks(
                method = credentials.first,
                password = credentials.second,
                address = endpoint.first,
                port = endpoint.second
            )
        }

        /*
         * Legacy form:
         * ss://BASE64(method:password@host:port)
         */
        val decodedBody =
            decodeBase64Text(body)

        val at =
            decodedBody.lastIndexOf('@')

        require(at > 0) {
            "Invalid Shadowsocks URI"
        }

        val credentials =
            splitCredentials(
                decodedBody.substring(
                    0,
                    at
                )
            )

        val endpoint =
            splitHostPort(
                decodedBody.substring(
                    at + 1
                )
            )

        return ParsedShadowsocks(
            method = credentials.first,
            password = credentials.second,
            address = endpoint.first,
            port = endpoint.second
        )
    }

    private fun splitCredentials(
        value: String
    ): Pair<String, String> {
        val separator =
            value.indexOf(':')

        require(separator > 0) {
            "Invalid Shadowsocks credentials"
        }

        val method =
            decodeComponent(
                value.substring(
                    0,
                    separator
                )
            ).trim()

        val password =
            decodeComponent(
                value.substring(
                    separator + 1
                )
            )

        require(
            method.isNotBlank() &&
                password.isNotBlank()
        ) {
            "Invalid Shadowsocks credentials"
        }

        return method to password
    }

    private fun transportFromQuery(
        query: Map<String, String>
    ): TransportSettings {
        val network =
            (
                query["type"]
                    ?: query["network"]
                    ?: "tcp"
                ).normalizeNetwork()

        val security =
            (
                query["security"]
                    ?: "none"
                ).lowercase()

        val path =
            query["path"]
                ?: ""

        val grpcMode =
            (
                query["mode"]
                    ?: ""
                ).lowercase()

        val serviceName =
            query["serviceName"]
                ?: query["service_name"]
                ?: if (
                    network == "grpc"
                ) {
                    path.removePrefix("/")
                } else {
                    ""
                }

        val authority =
            query["authority"]
                ?: if (
                    network == "grpc"
                ) {
                    query["host"]
                        ?: ""
                } else {
                    ""
                }

        val transport =
            TransportSettings(
                network = network,
                security = security,
                serverName =
                    query["sni"]
                        ?: query["peer"]
                        ?: "",
                host =
                    query["host"]
                        ?: "",
                path = path,
                serviceName = serviceName,
                headerType =
                    query["headerType"]
                        ?: query["header"]
                        ?: "none",
                fingerprint =
                    query["fp"]
                        ?: "",
                alpn =
                    (
                        query["alpn"]
                            ?: ""
                        ).split(',')
                        .map(String::trim)
                        .filter(String::isNotBlank),
                flow =
                    query["flow"]
                        ?: "",
                xhttpMode = grpcMode,
                grpcAuthority = authority,
                grpcMultiMode =
                    grpcMode == "multi",
                realityPublicKey =
                    query["pbk"]
                        ?: "",
                realityShortId =
                    query["sid"]
                        ?: "",
                realitySpiderX =
                    query["spx"]
                        ?: ""
            )

        validateTransport(transport)

        return transport
    }

    private fun validateTransport(
        transport: TransportSettings
    ) {
        require(
            transport.network in
                setOf(
                    "tcp",
                    "ws",
                    "grpc",
                    "httpupgrade",
                    "xhttp"
                )
        ) {
            "Unsupported transport: ${transport.network}"
        }

        require(
            transport.security in
                setOf(
                    "none",
                    "tls",
                    "reality"
                )
        ) {
            "Unsupported transport security: ${transport.security}"
        }

        if (
            transport.security == "reality"
        ) {
            require(
                transport.serverName
                    .isNotBlank()
            ) {
                "REALITY server name is missing"
            }

            require(
                transport.realityPublicKey
                    .isNotBlank()
            ) {
                "REALITY public key is missing"
            }
        }

        if (
            transport.network == "grpc"
        ) {
            require(
                transport.serviceName
                    .isNotBlank()
            ) {
                "gRPC serviceName is missing"
            }
        }
    }

    private fun query(
        uri: URI
    ): Map<String, String> =
        parseRawQuery(
            uri.rawQuery.orEmpty()
        )

    private fun parseRawQuery(
        rawQuery: String
    ): Map<String, String> =
        rawQuery.split('&')
            .filter {
                it.isNotBlank()
            }
            .associate { item ->
                val key =
                    decodeComponent(
                        item.substringBefore('=')
                    )

                val value =
                    decodeComponent(
                        item.substringAfter(
                            '=',
                            ""
                        )
                    )

                key to value
            }

    private fun fragmentName(
        uri: URI,
        fallback: String
    ): String =
        decodeComponent(
            uri.rawFragment
                ?: fallback
        ).ifBlank {
            fallback
        }

    private fun parseHostFallback(
        raw: String
    ): String {
        val authority =
            raw.substringAfter("://")
                .substringAfter('@')
                .substringBefore('?')
                .substringBefore('#')

        return if (
            authority.startsWith('[')
        ) {
            authority.substringAfter('[')
                .substringBefore(']')
        } else {
            authority.substringBeforeLast(':')
        }
    }

    private fun splitHostPort(
        endpointValue: String
    ): Pair<String, Int> {
        val endpoint =
            decodeComponent(
                endpointValue.trim()
            )

        if (endpoint.startsWith('[')) {
            val host =
                endpoint.substringAfter('[')
                    .substringBefore(']')

            val port =
                endpoint.substringAfter("]:")
                    .toInt()

            return host to port
        }

        val cut =
            endpoint.lastIndexOf(':')

        require(cut > 0) {
            "Missing port"
        }

        return endpoint.substring(
            0,
            cut
        ) to endpoint.substring(
            cut + 1
        ).toInt()
    }

    private fun decodeBase64Text(
        value: String
    ): String {
        val cleaned =
            value.trim()
                .replace("\n", "")
                .replace("\r", "")
                .replace('-', '+')
                .replace('_', '/')

        val padded =
            cleaned +
                "=".repeat(
                    (
                        4 -
                            cleaned.length % 4
                        ) % 4
                )

        val bytes =
            Base64.getDecoder()
                .decode(padded)

        val decoded =
            String(
                bytes,
                StandardCharsets.UTF_8
            )

        require(
            !decoded.contains('\uFFFD')
        ) {
            "Invalid UTF-8 Base64 payload"
        }

        return decoded
    }

    private fun decodeComponent(
        value: String
    ): String =
        URLDecoder.decode(
            value,
            StandardCharsets.UTF_8.name()
        )

    private fun validateUuid(
        value: String
    ) {
        UUID.fromString(value)
    }

    private fun validateEndpoint(
        addressValue: String,
        port: Int
    ) {
        val address =
            addressValue.trim()

        require(address.isNotBlank()) {
            "Server address is empty"
        }

        require(port in 1..65535) {
            "Server port is invalid"
        }

        require(
            !isLocalOrMetadataAddress(
                address
            )
        ) {
            "Local or metadata-only endpoint is not connectable"
        }
    }

    private fun isLocalOrMetadataAddress(
        address: String
    ): Boolean {
        val normalized =
            address.lowercase()

        if (
            normalized == "localhost" ||
            normalized == "0.0.0.0" ||
            normalized == "::" ||
            normalized == "::1" ||
            normalized.startsWith("127.")
        ) {
            return true
        }

        return runCatching {
            val inet =
                InetAddress.getByName(address)

            inet.isAnyLocalAddress ||
                inet.isLoopbackAddress
        }.getOrDefault(false)
    }

    private fun String.normalizeNetwork():
        String =
        when (lowercase()) {
            "websocket" -> "ws"
            "http-upgrade",
            "http_upgrade" ->
                "httpupgrade"

            "splithttp" ->
                "xhttp"

            else ->
                lowercase()
        }

    private fun Map<String, Any?>.string(
        key: String
    ): String =
        this[key]?.toString()
            ?: error("Missing $key")

    private fun Map<String, Any?>.stringOr(
        key: String,
        default: String
    ): String =
        this[key]?.toString()
            ?: default

    private fun Map<String, Any?>.int(
        key: String
    ): Int =
        string(key)
            .toDouble()
            .toInt()

    private fun Map<String, Any?>.intOr(
        key: String,
        default: Int
    ): Int =
        this[key]
            ?.toString()
            ?.toDoubleOrNull()
            ?.toInt()
            ?: default

    private data class ParsedShadowsocks(
        val method: String,
        val password: String,
        val address: String,
        val port: Int
    )
}
