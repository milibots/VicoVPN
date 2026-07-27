package com.vicovpn.client.subscription

data class SubscriptionRegistry(
    val success: Boolean,
    val urls: List<String>
)