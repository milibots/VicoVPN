package com.vicovpn.client.profile

data class VipDashboard(
    val title: String,
    val subtitle: String,
    val progress: Int,
    val progressColor: String,
    val status: String,
    val expireText: String
)

data class VipTraffic(
    val usedGb: Double,
    val totalGb: Double,
    val remainingGb: Double,
    val usagePercent: Double
)

data class VipExpiry(
    val date: String?,
    val daysRemaining: Int?,
    val expired: Boolean
)

data class VipSubscription(
    val status: String,
    val plan: String,
    val traffic: VipTraffic,
    val expiry: VipExpiry
)

data class VipBanner(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val buttonText: String?,
    val buttonUrl: String?,
    val dismissible: Boolean,
    val imageUrl: String? = null,
    val priority: Int = 0
)

data class VipConfig(
    val name: String,
    val config: String
)

data class VipSubscriptionResponse(
    val timestamp: String,
    val dashboard: VipDashboard,
    val subscription: VipSubscription,
    val banners: List<VipBanner>,
    val configs: List<VipConfig>
)
