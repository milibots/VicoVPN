package com.vicovpn.client.vpn

import android.content.Context

class TrafficStore(
    context: Context
) {
    companion object {
        private const val PREFERENCES_NAME =
            "traffic_totals"

        private const val KEY_DOWN =
            "lifetime_download"

        private const val KEY_UP =
            "lifetime_upload"
    }

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

    @Synchronized
    fun add(
        downloadBytes: Long,
        uploadBytes: Long
    ) {
        if (
            downloadBytes <= 0L &&
            uploadBytes <= 0L
        ) {
            return
        }

        val nextDown =
            preferences.getLong(KEY_DOWN, 0L) +
                    downloadBytes.coerceAtLeast(0L)

        val nextUp =
            preferences.getLong(KEY_UP, 0L) +
                    uploadBytes.coerceAtLeast(0L)

        preferences.edit()
            .putLong(KEY_DOWN, nextDown)
            .putLong(KEY_UP, nextUp)
            .apply()
    }

    fun getDownloadBytes(): Long {
        return preferences.getLong(
            KEY_DOWN,
            0L
        )
    }

    fun getUploadBytes(): Long {
        return preferences.getLong(
            KEY_UP,
            0L
        )
    }
}