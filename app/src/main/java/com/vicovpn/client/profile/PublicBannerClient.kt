package com.vicovpn.client.profile

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class PublicBannerClient {

    companion object {
        const val PUBLIC_BANNERS_URL =
            "https://vicovpn.milibook3.workers.dev/banners?placement=android"

        private const val MAX_RESPONSE_BYTES =
            2 * 1024 * 1024
    }

    fun fetch():
        List<VipBanner> {
        val connection =
            (
                URL(
                    PUBLIC_BANNERS_URL
                ).openConnection()
                    as HttpURLConnection
                ).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                useCaches = true

                setRequestProperty(
                    "Accept",
                    "application/json"
                )

                setRequestProperty(
                    "User-Agent",
                    "VicoVPN-Android/1"
                )
            }

        try {
            val status =
                connection.responseCode

            if (
                status !in 200..299
            ) {
                throw IOException(
                    "Public banners are unavailable."
                )
            }

            val body =
                readLimited(
                    connection.inputStream
                )

            val root =
                JSONObject(
                    body
                )

            if (
                !root.optBoolean(
                    "success",
                    false
                )
            ) {
                throw IOException(
                    "Public banners are unavailable."
                )
            }

            return parse(
                root.optJSONArray(
                    "banners"
                ) ?: JSONArray()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(
        array: JSONArray
    ): List<VipBanner> {
        val result =
            mutableListOf<VipBanner>()

        for (
            index in
            0 until array.length()
        ) {
            val item =
                array.optJSONObject(
                    index
                ) ?: continue

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

            result.add(
                VipBanner(
                    id = id,
                    type =
                        item.optString(
                            "type",
                            "info"
                        ),
                    title = title,
                    message =
                        item.optString(
                            "message",
                            ""
                        ),
                    buttonText =
                        item.optString(
                            "buttonText",
                            ""
                        ).takeIf {
                            it.isNotBlank()
                        },
                    buttonUrl =
                        item.optString(
                            "buttonUrl",
                            ""
                        ).takeIf {
                            it.isNotBlank()
                        },
                    dismissible =
                        item.optBoolean(
                            "dismissible",
                            true
                        ),
                    imageUrl =
                        item.optString(
                            "imageUrl",
                            ""
                        ).takeIf {
                            it.isNotBlank()
                        },
                    priority =
                        item.optInt(
                            "priority",
                            0
                        )
                )
            )
        }

        /*
         * Do not reorder. The public API already returns priority/newest order.
         */
        return result
    }

    private fun readLimited(
        input: java.io.InputStream
    ): String {
        BufferedInputStream(
            input
        ).use {
                buffered ->
            val output =
                ByteArrayOutputStream()

            val buffer =
                ByteArray(
                    8 * 1024
                )

            var total = 0

            while (true) {
                val count =
                    buffered.read(
                        buffer
                    )

                if (count < 0) {
                    break
                }

                total += count

                if (
                    total >
                        MAX_RESPONSE_BYTES
                ) {
                    throw IOException(
                        "Public banner response is too large."
                    )
                }

                output.write(
                    buffer,
                    0,
                    count
                )
            }

            return output.toString(
                StandardCharsets.UTF_8
                    .name()
            )
        }
    }
}
