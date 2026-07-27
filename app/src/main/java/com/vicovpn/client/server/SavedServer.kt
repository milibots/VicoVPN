package com.vicovpn.client.server

data class SavedServer(
    val id: String,
    val name: String,
    val rawLink: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val transport: String,
    val createdAt: Long,
    val origin: ServerOrigin = ServerOrigin.MANUAL,
    val latencyMs: Long = 0,
    val exitIp: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val city: String = "",
    val isp: String = "",
    val asn: String = "",
    val lastTestedAt: Long = 0
)