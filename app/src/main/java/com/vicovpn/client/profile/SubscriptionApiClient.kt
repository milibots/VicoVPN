package com.vicovpn.client.profile

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SubscriptionApiClient {

    companion object {
        const val BASE_URL =
            "https://vicovpn.milibook3.workers.dev"

        private val KEY_PATTERN =
            Regex(
                "^[A-Za-z0-9._~-]{1,256}$"
            )

        private const val MAX_RESPONSE_BYTES =
            4 * 1024 * 1024
    }

    fun validateKey(
        value: String
    ): String {
        val key =
            value.trim()

        require(
            KEY_PATTERN.matches(key)
        ) {
            "Invalid subscription key"
        }

        return key
    }

    fun fetch(
        rawKey: String
    ): VipSubscriptionResponse {
        val key =
            validateKey(
                rawKey
            )

        val encodedKey =
            URLEncoder.encode(
                key,
                StandardCharsets.UTF_8.name()
            )

        val connection =
            (
                URL(
                    "$BASE_URL/api/subscription/$encodedKey"
                ).openConnection()
                    as HttpURLConnection
                ).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty(
                    "Accept",
                    "application/json"
                )
                setRequestProperty(
                    "Cache-Control",
                    "no-store"
                )
                setRequestProperty(
                    "User-Agent",
                    "VicoVPN-Android/1"
                )
            }

        try {
            val status =
                connection.responseCode

            val stream =
                if (
                    status in 200..299
                ) {
                    connection.inputStream
                } else {
                    connection.errorStream
                        ?: connection.inputStream
                }

            val body =
                readLimited(
                    stream
                )

            val root =
                runCatching {
                    JSONObject(body)
                }.getOrElse {
                    throw IOException(
                        "The subscription service returned invalid data."
                    )
                }

            val success =
                root.optBoolean(
                    "success",
                    false
                )

            if (
                status !in 200..299 ||
                !success
            ) {
                val error =
                    root.optJSONObject(
                        "error"
                    )

                val message =
                    error
                        ?.optString(
                            "message"
                        )
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "Subscription could not be loaded."

                throw IOException(
                    message
                )
            }

            return parseSuccess(
                root
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSuccess(
        root: JSONObject
    ): VipSubscriptionResponse {
        val dashboardJson =
            root.requireObject(
                "dashboard"
            )

        val subscriptionJson =
            root.requireObject(
                "subscription"
            )

        val trafficJson =
            subscriptionJson.requireObject(
                "traffic"
            )

        val expiryJson =
            subscriptionJson.requireObject(
                "expire"
            )

        val dashboard =
            VipDashboard(
                title =
                    dashboardJson.optString(
                        "title",
                        ""
                    ),
                subtitle =
                    dashboardJson.optString(
                        "subtitle",
                        ""
                    ),
                progress =
                    dashboardJson.optInt(
                        "progress",
                        0
                    ).coerceIn(
                        0,
                        100
                    ),
                progressColor =
                    dashboardJson.optString(
                        "progressColor",
                        "#F59E0B"
                    ),
                status =
                    dashboardJson.optString(
                        "status",
                        ""
                    ),
                expireText =
                    dashboardJson.optString(
                        "expireText",
                        ""
                    )
            )

        val subscription =
            VipSubscription(
                status =
                    subscriptionJson.optString(
                        "status",
                        ""
                    ),
                plan =
                    subscriptionJson.optString(
                        "plan",
                        ""
                    ),
                traffic =
                    VipTraffic(
                        usedGb =
                            trafficJson.optDouble(
                                "usedGb",
                                0.0
                            ),
                        totalGb =
                            trafficJson.optDouble(
                                "totalGb",
                                0.0
                            ),
                        remainingGb =
                            trafficJson.optDouble(
                                "remainingGb",
                                0.0
                            ),
                        usagePercent =
                            trafficJson.optDouble(
                                "usagePercent",
                                0.0
                            )
                    ),
                expiry =
                    VipExpiry(
                        date =
                            expiryJson
                                .optString(
                                    "date",
                                    ""
                                )
                                .takeIf {
                                    it.isNotBlank() &&
                                        it != "null"
                                },
                        daysRemaining =
                            if (
                                expiryJson.isNull(
                                    "daysRemaining"
                                )
                            ) {
                                null
                            } else {
                                expiryJson.optInt(
                                    "daysRemaining",
                                    0
                                )
                            },
                        expired =
                            expiryJson.optBoolean(
                                "expired",
                                false
                            )
                    )
            )

        return VipSubscriptionResponse(
            timestamp =
                root.optString(
                    "timestamp",
                    ""
                ),
            dashboard = dashboard,
            subscription = subscription,
            banners =
                parseBanners(
                    root.optJSONArray(
                        "banners"
                    ) ?: JSONArray()
                ),
            configs =
                parseConfigs(
                    root.optJSONArray(
                        "configs"
                    ) ?: JSONArray()
                )
        )
    }

    private fun parseBanners(
        array: JSONArray
    ): List<VipBanner> {
        val result =
            mutableListOf<VipBanner>()

        for (
            index in
            0 until array.length()
        ) {
            val item =
                array.optJSONObject(index)
                    ?: continue

            val id =
                item.optString(
                    "id",
                    ""
                )

            val title =
                item.optString(
                    "title",
                    ""
                )

            if (
                id.isBlank() ||
                title.isBlank()
            ) {
                continue
            }

            val knownType =
                item.optString(
                    "type",
                    "info"
                ).lowercase()
                    .takeIf {
                        it in setOf(
                            "info",
                            "warning",
                            "success",
                            "error",
                            "maintenance",
                            "promotion"
                        )
                    }
                    ?: "info"

            result.add(
                VipBanner(
                    id = id,
                    type = knownType,
                    title = title,
                    message =
                        item.optString(
                            "message",
                            ""
                        ),
                    buttonText =
                        item
                            .optString(
                                "buttonText",
                                ""
                            )
                            .takeIf {
                                it.isNotBlank()
                            },
                    buttonUrl =
                        item
                            .optString(
                                "buttonUrl",
                                ""
                            )
                            .takeIf {
                                it.isNotBlank()
                            },
                    dismissible =
                        item.optBoolean(
                            "dismissible",
                            true
                        )
                )
            )
        }

        /*
         * Preserve server order. The API already sorts by priority and date.
         */
        return result
    }

    private fun parseConfigs(
        array: JSONArray
    ): List<VipConfig> {
        val result =
            mutableListOf<VipConfig>()

        for (
            index in
            0 until array.length()
        ) {
            val item =
                array.optJSONObject(index)
                    ?: continue

            val config =
                item.optString(
                    "config",
                    ""
                ).trim()

            if (config.isBlank()) {
                continue
            }

            result.add(
                VipConfig(
                    name =
                        item.optString(
                            "name",
                            ""
                        ).ifBlank {
                            "Premium server ${
                                index + 1
                            }"
                        },
                    config = config
                )
            )
        }

        return result
    }

    private fun JSONObject.requireObject(
        name: String
    ): JSONObject {
        return optJSONObject(name)
            ?: throw IOException(
                "The subscription response is incomplete."
            )
    }

    private fun readLimited(
        input: java.io.InputStream
    ): String {
        BufferedInputStream(input).use {
                buffered ->
            val output =
                ByteArrayOutputStream()

            val buffer =
                ByteArray(8 * 1024)

            var total = 0

            while (true) {
                val count =
                    buffered.read(buffer)

                if (count < 0) {
                    break
                }

                total += count

                if (
                    total >
                        MAX_RESPONSE_BYTES
                ) {
                    throw IOException(
                        "The subscription response is too large."
                    )
                }

                output.write(
                    buffer,
                    0,
                    count
                )
            }

            return output.toString(
                StandardCharsets.UTF_8.name()
            )
        }
    }
}
