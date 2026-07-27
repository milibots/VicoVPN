package com.vicovpn.client.vpn

import android.content.Context
import android.content.Intent
import com.vicovpn.client.util.DiagnosticsLog

object VpnStateRepository {
    const val ACTION_STATE = "com.vicovpn.client.STATE"

    const val EXTRA_STAGE = "stage"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_EXIT_IP = "exitIp"
    const val EXTRA_COUNTRY_CODE = "countryCode"
    const val EXTRA_COUNTRY_NAME = "countryName"
    const val EXTRA_REGION = "region"
    const val EXTRA_CITY = "city"
    const val EXTRA_ISP = "isp"
    const val EXTRA_ASN = "asn"
    const val EXTRA_LOCATION_PROVIDER = "locationProvider"
    const val EXTRA_DOWN = "down"
    const val EXTRA_UP = "up"
    const val EXTRA_SERVER = "server"

    @Volatile
    private var current = VpnSnapshot()

    fun get(): VpnSnapshot = current

    fun publish(
        context: Context,
        snapshot: VpnSnapshot
    ) {
        current = snapshot

        DiagnosticsLog.add(
            "STATE",
            "${snapshot.stage}: ${snapshot.message}"
        )

        context.sendBroadcast(
            Intent(ACTION_STATE)
                .setPackage(context.packageName)
                .apply {
                    putExtra(EXTRA_STAGE, snapshot.stage.name)
                    putExtra(EXTRA_MESSAGE, snapshot.message)
                    putExtra(EXTRA_EXIT_IP, snapshot.exitIp)
                    putExtra(EXTRA_COUNTRY_CODE, snapshot.countryCode)
                    putExtra(EXTRA_COUNTRY_NAME, snapshot.countryName)
                    putExtra(EXTRA_REGION, snapshot.region)
                    putExtra(EXTRA_CITY, snapshot.city)
                    putExtra(EXTRA_ISP, snapshot.isp)
                    putExtra(EXTRA_ASN, snapshot.asn)
                    putExtra(
                        EXTRA_LOCATION_PROVIDER,
                        snapshot.locationProvider
                    )
                    putExtra(EXTRA_DOWN, snapshot.downloadBytes)
                    putExtra(EXTRA_UP, snapshot.uploadBytes)
                    putExtra(EXTRA_SERVER, snapshot.serverName)
                }
        )
    }

    fun fromIntent(intent: Intent): VpnSnapshot {
        return VpnSnapshot(
            stage = runCatching {
                VpnStage.valueOf(
                    intent.getStringExtra(EXTRA_STAGE).orEmpty()
                )
            }.getOrDefault(VpnStage.DISCONNECTED),
            message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
            exitIp = intent.getStringExtra(EXTRA_EXIT_IP).orEmpty(),
            countryCode =
                intent.getStringExtra(EXTRA_COUNTRY_CODE).orEmpty(),
            countryName =
                intent.getStringExtra(EXTRA_COUNTRY_NAME).orEmpty(),
            region = intent.getStringExtra(EXTRA_REGION).orEmpty(),
            city = intent.getStringExtra(EXTRA_CITY).orEmpty(),
            isp = intent.getStringExtra(EXTRA_ISP).orEmpty(),
            asn = intent.getStringExtra(EXTRA_ASN).orEmpty(),
            locationProvider =
                intent.getStringExtra(EXTRA_LOCATION_PROVIDER).orEmpty(),
            downloadBytes = intent.getLongExtra(EXTRA_DOWN, 0),
            uploadBytes = intent.getLongExtra(EXTRA_UP, 0),
            serverName = intent.getStringExtra(EXTRA_SERVER).orEmpty()
        )
    }
}