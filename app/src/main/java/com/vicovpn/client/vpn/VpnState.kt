package com.vicovpn.client.vpn

enum class VpnStage(val fa: String) {
    DISCONNECTED("قطع"),
    PREPARING("در حال آماده‌سازی"),
    ESTABLISHING_TUN("در حال ساخت تونل"),
    STARTING_XRAY("در حال راه‌اندازی Xray"),
    VERIFYING("در حال بررسی اتصال"),
    CONNECTED("متصل"),
    STOPPING("در حال قطع اتصال"),
    ERROR("خطا")
}

data class VpnSnapshot(
    val stage: VpnStage = VpnStage.DISCONNECTED,
    val message: String = "",
    val exitIp: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val region: String = "",
    val city: String = "",
    val isp: String = "",
    val asn: String = "",
    val locationProvider: String = "",
    val downloadBytes: Long = 0,
    val uploadBytes: Long = 0,
    val serverName: String = ""
)