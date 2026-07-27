package com.vicovpn.client.subscription

import org.json.JSONObject
import java.net.URI

object SubscriptionRegistryClient {

    fun fetch(
        registryUrl: String,
        maxSourceCount: Int
    ): SubscriptionRegistry {
        val body =
            HttpTextClient.get(
                url = registryUrl,
                accept =
                    "application/json, text/json;q=0.9, */*;q=0.1",
                maxBytes = 256_000,
                userAgent =
                    HttpTextClient
                        .REGISTRY_USER_AGENT,
                noCache = true
            )

        val json =
            JSONObject(body)

        require(
            json.optBoolean(
                "success",
                false
            )
        ) {
            "Registry reported failure"
        }

        val array =
            json.optJSONArray("urls")
                ?: error(
                    "Registry response has no urls array"
                )

        val urls =
            buildList {
                for (
                    index in
                    0 until array.length()
                ) {
                    val value =
                        array.optString(index)
                            .trim()

                    if (
                        isValidHttpsUrl(
                            value
                        )
                    ) {
                        add(value)
                    }
                }
            }
                .distinct()
                .take(maxSourceCount)

        require(urls.isNotEmpty()) {
            "Registry returned no valid HTTPS sources"
        }

        return SubscriptionRegistry(
            success = true,
            urls = urls
        )
    }

    private fun isValidHttpsUrl(
        value: String
    ): Boolean {
        return runCatching {
            val uri = URI(value)

            uri.scheme.equals(
                "https",
                ignoreCase = true
            ) &&
                !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}
