package com.vicovpn.client.subscription

import android.util.Base64

object SubscriptionContentParser {

    private val linkRegex =
        Regex(
            pattern =
                """(?i)(?:vmess|vless|trojan|ss)://[^\s<>"'`]+"""
        )

    fun extract(
        body: String
    ): List<String> {
        val normalized =
            normalizeText(body)

        if (normalized.isBlank()) {
            return emptyList()
        }

        /*
         * First try the response exactly as received. This supports raw
         * line-based subscriptions and subscription pages that return HTML.
         */
        val direct =
            extractFromText(normalized)

        if (direct.isNotEmpty()) {
            return direct
        }

        /*
         * Otherwise treat the complete response as a Base64 subscription.
         * Both standard and URL-safe alphabets, whitespace, and missing
         * padding are supported.
         */
        val compact =
            normalized
                .lineSequence()
                .map(String::trim)
                .filter {
                    it.isNotBlank() &&
                        !it.startsWith("#")
                }
                .joinToString("")

        val decodedCandidates =
            linkedSetOf<String>()

        decodeBase64(
            compact,
            Base64.DEFAULT
        )?.let(decodedCandidates::add)

        decodeBase64(
            compact,
            Base64.URL_SAFE or
                Base64.NO_WRAP
        )?.let(decodedCandidates::add)

        return decodedCandidates
            .flatMap(::extractFromText)
            .distinct()
    }

    private fun extractFromText(
        text: String
    ): List<String> {
        val htmlDecoded =
            decodeCommonHtmlEntities(text)
                .replace("\\/", "/")

        return linkRegex
            .findAll(htmlDecoded)
            .map {
                cleanLink(
                    it.value
                )
            }
            .filter {
                it.isNotBlank()
            }
            .filterNot {
                isMetadataOnlyLink(it)
            }
            .distinct()
            .toList()
    }

    private fun cleanLink(
        value: String
    ): String {
        return value
            .trim()
            .trimEnd(
                '.',
                ',',
                ';',
                ')',
                ']',
                '}'
            )
    }

    private fun isMetadataOnlyLink(
        value: String
    ): Boolean {
        val authority =
            value.substringAfter("://")
                .substringAfter('@', "")
                .substringBefore('?')
                .substringBefore('#')

        val host =
            when {
                authority.startsWith("[") ->
                    authority.substringAfter('[')
                        .substringBefore(']')

                authority.contains(':') ->
                    authority.substringBeforeLast(':')

                else ->
                    authority
            }.lowercase()

        return host == "localhost" ||
            host == "0.0.0.0" ||
            host == "::1" ||
            host.startsWith("127.")
    }

    private fun decodeBase64(
        value: String,
        flags: Int
    ): String? {
        if (
            value.isBlank() ||
            value.length < 8
        ) {
            return null
        }

        val padded =
            value +
                "=".repeat(
                    (
                        4 -
                            value.length % 4
                        ) % 4
                )

        return runCatching {
            val bytes =
                Base64.decode(
                    padded,
                    flags
                )

            val decoded =
                String(
                    bytes,
                    Charsets.UTF_8
                )

            if (
                decoded.isBlank() ||
                decoded.contains('\uFFFD')
            ) {
                null
            } else {
                decoded
            }
        }.getOrNull()
    }

    private fun normalizeText(
        value: String
    ): String {
        return value
            .removePrefix("\uFEFF")
            .replace("\u0000", "")
            .trim()
    }

    private fun decodeCommonHtmlEntities(
        value: String
    ): String {
        return value
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&#x26;", "&")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&#x22;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
