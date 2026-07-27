package com.vicovpn.client.split

import android.content.Context

enum class SplitTunnelMode {
    ALL_APPS,
    EXCLUDE_SELECTED,
    INCLUDE_SELECTED
}

class SplitTunnelSettings(
    context: Context
) {
    companion object {
        private const val PREFERENCES =
            "split_tunnel_settings"

        private const val KEY_MODE =
            "mode"

        private const val KEY_PACKAGES =
            "packages"
    }

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
            )

    fun getMode(): SplitTunnelMode {
        return runCatching {
            SplitTunnelMode.valueOf(
                preferences.getString(
                    KEY_MODE,
                    SplitTunnelMode
                        .ALL_APPS.name
                ) ?: SplitTunnelMode
                    .ALL_APPS.name
            )
        }.getOrDefault(
            SplitTunnelMode.ALL_APPS
        )
    }

    fun setMode(
        mode: SplitTunnelMode
    ) {
        preferences.edit()
            .putString(
                KEY_MODE,
                mode.name
            )
            .apply()
    }

    fun getSelectedPackages(): Set<String> {
        return preferences.getStringSet(
            KEY_PACKAGES,
            emptySet()
        )?.toSet() ?: emptySet()
    }

    fun setSelectedPackages(
        packages: Set<String>
    ) {
        preferences.edit()
            .putStringSet(
                KEY_PACKAGES,
                packages.toSet()
            )
            .apply()
    }
}
