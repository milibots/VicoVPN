package com.vicovpn.client.subscription

import android.content.Context

enum class FreeTestMode {
    DEVICE_OPTIMIZED,
    FAST,
    THOROUGH
}

enum class AutoRefreshMode(
    val intervalMinutes: Int
) {
    SMART(15),
    OFF(0),
    DAILY(24 * 60),
    EVERY_THREE_DAYS(72 * 60)
}

class FreeServerSettings(
    context: Context
) {
    companion object {
        private const val PREFERENCES_NAME =
            "free_server_preferences"

        private const val KEY_TEST_MODE =
            "test_mode"

        private const val KEY_MAX_SAVED =
            "max_saved"

        private const val KEY_WIFI_ONLY =
            "wifi_only"

        private const val KEY_AUTO_REFRESH =
            "auto_refresh"

        private const val KEY_LAST_UPDATE =
            "last_update"

        private const val KEY_SMART_FAILOVER =
            "smart_failover"

        private const val KEY_BACKGROUND_DISCOVERY =
            "background_discovery"
    }

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

    fun getTestMode(): FreeTestMode {
        return runCatching {
            FreeTestMode.valueOf(
                preferences.getString(
                    KEY_TEST_MODE,
                    FreeTestMode.DEVICE_OPTIMIZED.name
                ) ?: FreeTestMode.DEVICE_OPTIMIZED.name
            )
        }.getOrDefault(
            FreeTestMode.DEVICE_OPTIMIZED
        )
    }

    fun setTestMode(
        mode: FreeTestMode
    ) {
        preferences.edit()
            .putString(
                KEY_TEST_MODE,
                mode.name
            )
            .apply()
    }

    fun getMaxSaved(): Int {
        return preferences.getInt(
            KEY_MAX_SAVED,
            10
        ).coerceIn(4, 20)
    }

    fun setMaxSaved(
        value: Int
    ) {
        preferences.edit()
            .putInt(
                KEY_MAX_SAVED,
                value.coerceIn(4, 20)
            )
            .apply()
    }

    fun isWifiOnly(): Boolean {
        return preferences.getBoolean(
            KEY_WIFI_ONLY,
            false
        )
    }

    fun setWifiOnly(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_WIFI_ONLY,
                enabled
            )
            .apply()
    }

    fun getAutoRefreshMode(): AutoRefreshMode {
        return runCatching {
            AutoRefreshMode.valueOf(
                preferences.getString(
                    KEY_AUTO_REFRESH,
                    AutoRefreshMode.SMART.name
                ) ?: AutoRefreshMode.SMART.name
            )
        }.getOrDefault(
            AutoRefreshMode.SMART
        )
    }

    fun setAutoRefreshMode(
        mode: AutoRefreshMode
    ) {
        preferences.edit()
            .putString(
                KEY_AUTO_REFRESH,
                mode.name
            )
            .apply()
    }

    fun getLastUpdateAt(): Long {
        return preferences.getLong(
            KEY_LAST_UPDATE,
            0L
        )
    }

    fun isSmartFailoverEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_SMART_FAILOVER,
            true
        )
    }

    fun setSmartFailoverEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_SMART_FAILOVER,
                enabled
            )
            .apply()
    }

    fun isBackgroundDiscoveryEnabled():
        Boolean {
        return preferences.getBoolean(
            KEY_BACKGROUND_DISCOVERY,
            true
        )
    }

    fun setBackgroundDiscoveryEnabled(
        enabled: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                KEY_BACKGROUND_DISCOVERY,
                enabled
            )
            .apply()
    }

    fun markUpdatedNow() {
        preferences.edit()
            .putLong(
                KEY_LAST_UPDATE,
                System.currentTimeMillis()
            )
            .apply()
    }
}
