package com.vicovpn.client.model

data class TransportSettings(
    val network: String = "tcp",
    val security: String = "none",
    val serverName: String = "",
    val host: String = "",
    val path: String = "",
    val serviceName: String = "",
    val headerType: String = "none",
    val fingerprint: String = "",
    val alpn: List<String> = emptyList(),
    val flow: String = "",
    val xhttpMode: String = "",
    val grpcAuthority: String = "",
    val grpcMultiMode: Boolean = false,
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = ""
)

sealed interface ProxyProfile {
    val name: String
    val address: String
    val port: Int
    val transport: TransportSettings

    data class Vmess(
        override val name: String,
        override val address: String,
        override val port: Int,
        val id: String,
        val alterId: Int = 0,
        val cipher: String = "auto",
        override val transport: TransportSettings = TransportSettings()
    ) : ProxyProfile

    data class Vless(
        override val name: String,
        override val address: String,
        override val port: Int,
        val id: String,
        val encryption: String = "none",
        override val transport: TransportSettings = TransportSettings()
    ) : ProxyProfile

    data class Trojan(
        override val name: String,
        override val address: String,
        override val port: Int,
        val password: String,
        override val transport: TransportSettings =
            TransportSettings(security = "tls")
    ) : ProxyProfile

    data class Shadowsocks(
        override val name: String,
        override val address: String,
        override val port: Int,
        val method: String,
        val password: String,
        override val transport: TransportSettings = TransportSettings()
    ) : ProxyProfile
}
