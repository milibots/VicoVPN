package com.vicovpn.client.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    private const val PREFERENCES =
        "battery_optimization_prompt"

    private const val KEY_LAST_PROMPT =
        "last_prompt"

    private const val PROMPT_INTERVAL_MS =
        7L *
            24L *
            60L *
            60L *
            1_000L

    fun isExempt(
        context: Context
    ): Boolean {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.M
        ) {
            return true
        }

        val manager =
            context.getSystemService(
                PowerManager::class.java
            )

        return manager
            .isIgnoringBatteryOptimizations(
                context.packageName
            )
    }

    fun shouldPrompt(
        context: Context
    ): Boolean {
        if (isExempt(context)) {
            return false
        }

        val lastPrompt =
            context.getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
            ).getLong(
                KEY_LAST_PROMPT,
                0L
            )

        return System.currentTimeMillis() -
            lastPrompt >=
            PROMPT_INTERVAL_MS
    }

    fun markPrompted(
        context: Context
    ) {
        context.getSharedPreferences(
            PREFERENCES,
            Context.MODE_PRIVATE
        ).edit()
            .putLong(
                KEY_LAST_PROMPT,
                System.currentTimeMillis()
            )
            .apply()
    }

    fun openOptimizationSettings(
        context: Context
    ) {
        val intent =
            Intent(
                Settings
                    .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

        runCatching {
            context.startActivity(intent)
        }.onFailure {
            context.startActivity(
                Intent(
                    Settings
                        .ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse(
                        "package:${context.packageName}"
                    )
                ).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            )
        }
    }
}
