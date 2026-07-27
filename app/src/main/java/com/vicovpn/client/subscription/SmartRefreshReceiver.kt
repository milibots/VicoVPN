package com.vicovpn.client.subscription

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Compatibility receiver for alarms created by earlier builds.
 * It only hands work to WorkManager and never starts the native VPN core.
 */
class SmartRefreshReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {
        if (
            intent?.action ==
                ACTION_SMART_REFRESH
        ) {
            SmartRefreshScheduler.sync(
                context,
                triggerNow = true
            )
        }
    }

    companion object {
        const val ACTION_SMART_REFRESH =
            "com.vicovpn.client.SMART_REFRESH_ALARM"
    }
}
