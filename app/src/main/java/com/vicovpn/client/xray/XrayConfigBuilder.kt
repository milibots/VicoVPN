package com.vicovpn.client.xray

import com.vicovpn.client.model.ProxyProfile
import com.vicovpn.client.model.TransportSettings
import com.vicovpn.client.util.JsonCodec

object XrayConfigBuilder {
    const val TUN_IPV4 = "10.10.0.2"
    const val TUN_IPV6 = "fd00:1:fd00:1::2"
    const val MTU = 1400

    fun build(
        profile: ProxyProfile,
        socksPort: Int
    ): String {
        return buildInternal(
            profile = profile,
            socksPort = socksPort,
            includeTunInbound = true
        )
    }

    fun buildForTest(
        profile: ProxyProfile,
        socksPort: Int
    ): String {
        return buildInternal(
            profile = profile,
            socksPort = socksPort,
            includeTunInbound = true
        )
    }

    fun buildDelayTestConfig(
        profile: ProxyProfile
    ): String {
        val config =
            linkedMapOf<String, Any?>(
                "log" to mapOf(
                    "loglevel" to "warning"
                ),
                "inbounds" to
                    emptyList<Map<String, Any?>>(),
                "outbounds" to listOf(
                    proxyOutbound(
                        profile = profile,
                        includeMux = false
                    )
                )
            )

        return JsonCodec.stringify(config)
    }

    private fun buildInternal(
        profile: ProxyProfile,
        socksPort: Int,
        includeTunInbound: Boolean
    ): String {
        val inbounds =
            buildList<Map<String, Any?>> {
                if (includeTunInbound) {
                    add(
                        mapOf(
                            "tag" to "tun-in",
                            "protocol" to "tun",
                            "settings" to mapOf(
                                "name" to "xray0",
                                "mtu" to MTU,
                                "gateway" to listOf(
                                    "$TUN_IPV4/30",
                                    "$TUN_IPV6/126"
                                ),
                                "userLevel" to 8
                            ),
                            "sniffing" to mapOf(
                                "enabled" to true,
                                "destOverride" to listOf(
                                    "http",
                                    "tls",
                                    "quic"
                                ),
                                "routeOnly" to false
                            )
                        )
                    )
                }

                add(
                    mapOf(
                        "tag" to "socks-in",
                        "listen" to "127.0.0.1",
                        "port" to socksPort,
                        "protocol" to "socks",
                        "settings" to mapOf(
                            "auth" to "noauth",
                            "udp" to true,
                            "userLevel" to 8
                        ),
                        "sniffing" to mapOf(
                            "enabled" to true,
                            "destOverride" to listOf(
                                "http",
                                "tls",
                                "quic"
                            )
                        )
                    )
                )
            }

        val inboundTags =
            inbounds.mapNotNull {
                it["tag"]?.toString()
            }

        val config = linkedMapOf<String, Any?>(
            "log" to mapOf(
                "loglevel" to "warning"
            ),
            "stats" to emptyMap<String, Any?>(),
            "policy" to mapOf(
                "levels" to mapOf(
                    "8" to mapOf(
                        "handshake" to 8,
                        "connIdle" to 300,
                        "uplinkOnly" to 2,
                        "downlinkOnly" to 5
                    )
                ),
                "system" to mapOf(
                    "statsOutboundUplink" to true,
                    "statsOutboundDownlink" to true
                )
            ),
            "inbounds" to inbounds,
            "outbounds" to listOf(
                proxyOutbound(profile),
                mapOf(
                    "tag" to "direct",
                    "protocol" to "freedom",
                    "settings" to emptyMap<String, Any?>()
                ),
                mapOf(
                    "tag" to "block",
                    "protocol" to "blackhole",
                    "settings" to mapOf(
                        "response" to mapOf(
                            "type" to "http"
                        )
                    )
                )
            ),
            "routing" to mapOf(
                "domainStrategy" to "IPIfNonMatch",
                "rules" to listOf(
                    mapOf(
                        "type" to "field",
                        "inboundTag" to inboundTags,
                        "outboundTag" to "proxy"
                    )
                )
            )
        )

        return JsonCodec.stringify(config)
    }

    private fun proxyOutbound(
        profile: ProxyProfile,
        includeMux: Boolean = true
    ): Map<String, Any?> {
        val settings: Map<String, Any?> =
            when (profile) {
                is ProxyProfile.Vmess -> {
                    mapOf(
                        "vnext" to listOf(
                            mapOf(
                                "address" to profile.address,
                                "port" to profile.port,
                                "users" to listOf(
                                    mapOf(
                                        "id" to profile.id,
                                        "alterId" to profile.alterId,
                                        "security" to profile.cipher,
                                        "level" to 8
                                    )
                                )
                            )
                        )
                    )
                }

                is ProxyProfile.Vless -> {
                    mapOf(
                        "vnext" to listOf(
                            mapOf(
                                "address" to profile.address,
                                "port" to profile.port,
                                "users" to listOf(
                                    buildMap<String, Any?> {
                                        put("id", profile.id)
                                        require(
                                            profile.encryption == "none"
                                        ) {
                                            "Unsupported VLESS encryption: ${profile.encryption}"
                                        }

                                        put(
                                            "encryption",
                                            profile.encryption
                                        )
                                        put("level", 8)

                                        profile.transport.flow
                                            .takeIf {
                                                it.isNotBlank()
                                            }
                                            ?.let {
                                                put("flow", it)
                                            }
                                    }
                                )
                            )
                        )
                    )
                }

                is ProxyProfile.Trojan -> {
                    mapOf(
                        "servers" to listOf(
                            mapOf(
                                "address" to profile.address,
                                "port" to profile.port,
                                "password" to profile.password,
                                "level" to 8
                            )
                        )
                    )
                }

                is ProxyProfile.Shadowsocks -> {
                    mapOf(
                        "servers" to listOf(
                            mapOf(
                                "address" to profile.address,
                                "port" to profile.port,
                                "method" to profile.method,
                                "password" to profile.password,
                                "level" to 8
                            )
                        )
                    )
                }
            }

        val protocol =
            when (profile) {
                is ProxyProfile.Vmess -> "vmess"
                is ProxyProfile.Vless -> "vless"
                is ProxyProfile.Trojan -> "trojan"
                is ProxyProfile.Shadowsocks -> "shadowsocks"
            }

        return linkedMapOf<String, Any?>(
            "tag" to "proxy",
            "protocol" to protocol,
            "settings" to settings,
            "streamSettings" to streamSettings(
                profile.transport
            )
        ).apply {
            if (includeMux) {
                put(
                    "mux",
                    mapOf(
                        "enabled" to false
                    )
                )
            }
        }
    }

    private fun streamSettings(
        transport: TransportSettings
    ): Map<String, Any?> {
        return buildMap {
            put("network", transport.network)
            put("security", transport.security)

            when (transport.network) {
                "ws" -> {
                    put(
                        "wsSettings",
                        buildMap<String, Any?> {
                            put(
                                "path",
                                transport.path.ifBlank { "/" }
                            )

                            if (transport.host.isNotBlank()) {
                                put(
                                    "headers",
                                    mapOf(
                                        "Host" to transport.host
                                    )
                                )
                            }
                        }
                    )
                }

                "grpc" -> {
                    put(
                        "grpcSettings",
                        buildMap<String, Any?> {
                            put(
                                "serviceName",
                                transport.serviceName
                            )
                            put(
                                "multiMode",
                                transport.grpcMultiMode
                            )

                            if (
                                transport.grpcAuthority
                                    .isNotBlank()
                            ) {
                                put(
                                    "authority",
                                    transport.grpcAuthority
                                )
                            }
                        }
                    )
                }

                "httpupgrade" -> {
                    put(
                        "httpupgradeSettings",
                        buildMap<String, Any?> {
                            put(
                                "path",
                                transport.path.ifBlank { "/" }
                            )

                            if (transport.host.isNotBlank()) {
                                put("host", transport.host)
                            }
                        }
                    )
                }

                "xhttp" -> {
                    put(
                        "xhttpSettings",
                        buildMap<String, Any?> {
                            put(
                                "path",
                                transport.path.ifBlank { "/" }
                            )

                            if (transport.host.isNotBlank()) {
                                put("host", transport.host)
                            }

                            if (
                                transport.xhttpMode.isNotBlank()
                            ) {
                                put(
                                    "mode",
                                    transport.xhttpMode
                                )
                            }
                        }
                    )
                }

                "tcp" -> {
                    if (
                        transport.headerType != "none"
                    ) {
                        put(
                            "tcpSettings",
                            mapOf(
                                "header" to mapOf(
                                    "type" to
                                        transport.headerType
                                )
                            )
                        )
                    }
                }
            }

            if (transport.security == "tls") {
                put(
                    "tlsSettings",
                    buildMap<String, Any?> {
                        if (
                            transport.serverName.isNotBlank()
                        ) {
                            put(
                                "serverName",
                                transport.serverName
                            )
                        }

                        if (
                            transport.fingerprint.isNotBlank()
                        ) {
                            put(
                                "fingerprint",
                                transport.fingerprint
                            )
                        }

                        if (transport.alpn.isNotEmpty()) {
                            put("alpn", transport.alpn)
                        }
                    }
                )
            }

            if (transport.security == "reality") {
                put(
                    "realitySettings",
                    buildMap<String, Any?> {
                        if (
                            transport.serverName.isNotBlank()
                        ) {
                            put(
                                "serverName",
                                transport.serverName
                            )
                        }

                        if (
                            transport.fingerprint.isNotBlank()
                        ) {
                            put(
                                "fingerprint",
                                transport.fingerprint
                            )
                        }

                        if (
                            transport.realityPublicKey
                                .isNotBlank()
                        ) {
                            put(
                                "publicKey",
                                transport.realityPublicKey
                            )
                        }

                        if (
                            transport.realityShortId
                                .isNotBlank()
                        ) {
                            put(
                                "shortId",
                                transport.realityShortId
                            )
                        }

                        if (
                            transport.realitySpiderX
                                .isNotBlank()
                        ) {
                            put(
                                "spiderX",
                                transport.realitySpiderX
                            )
                        }
                    }
                )
            }
        }
    }
}
