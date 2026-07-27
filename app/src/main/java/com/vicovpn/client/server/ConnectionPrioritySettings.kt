package com.vicovpn.client.server

import android.content.Context

enum class ConnectionPriorityMode(
    val allowsVip: Boolean,
    val allowsFree: Boolean,
    val automaticSelection: Boolean
) {
    VIP_ONLY(
        allowsVip = true,
        allowsFree = false,
        automaticSelection = true
    ),
    VIP_AND_FREE(
        allowsVip = true,
        allowsFree = true,
        automaticSelection = true
    ),
    FREE_ONLY(
        allowsVip = false,
        allowsFree = true,
        automaticSelection = true
    ),
    NONE(
        allowsVip = false,
        allowsFree = false,
        automaticSelection = false
    )
}

class ConnectionPrioritySettings(
    context: Context
) {
    companion object {
        private const val PREFERENCES =
            "connection_priority"

        private const val KEY_MODE =
            "mode"
    }

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
            )

    fun getMode():
        ConnectionPriorityMode {
        return runCatching {
            ConnectionPriorityMode.valueOf(
                preferences.getString(
                    KEY_MODE,
                    ConnectionPriorityMode
                        .VIP_AND_FREE
                        .name
                ) ?: ConnectionPriorityMode
                    .VIP_AND_FREE
                    .name
            )
        }.getOrDefault(
            ConnectionPriorityMode
                .VIP_AND_FREE
        )
    }

    fun setMode(
        mode: ConnectionPriorityMode
    ) {
        preferences.edit()
            .putString(
                KEY_MODE,
                mode.name
            )
            .apply()
    }
}
