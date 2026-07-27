package com.vicovpn.client.subscription

import android.content.Context
import java.net.URI

class SubscriptionSettings(
    context: Context
) {
    companion object {
        private const val PREFERENCES_NAME =
            "subscription_settings"

        private const val KEY_REGISTRY_URL =
            "registry_url"

        const val DEFAULT_REGISTRY_URL =
            "https://subfreevico.milibotss.workers.dev/"
    }

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

    fun getRegistryUrl(): String {
        return preferences.getString(
            KEY_REGISTRY_URL,
            DEFAULT_REGISTRY_URL
        ) ?: DEFAULT_REGISTRY_URL
    }

    fun setRegistryUrl(
        value: String
    ): Result<Unit> {
        return runCatching {
            val normalized = validateHttpsUrl(value)
            preferences.edit()
                .putString(
                    KEY_REGISTRY_URL,
                    normalized
                )
                .apply()
        }
    }

    fun resetRegistryUrl() {
        preferences.edit()
            .remove(KEY_REGISTRY_URL)
            .apply()
    }

    private fun validateHttpsUrl(
        value: String
    ): String {
        val trimmed = value.trim()
        val uri = URI(trimmed)

        require(
            uri.scheme.equals(
                "https",
                ignoreCase = true
            )
        ) {
            "Registry URL must use HTTPS"
        }

        require(!uri.host.isNullOrBlank()) {
            "Registry URL has no host"
        }

        return trimmed
    }
}