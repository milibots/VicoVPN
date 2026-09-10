package com.vicovpn.client.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val version: String,
    val title: String,
    val downloadUrl: String
)

object GitHubUpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/milibots/VicoVPN/releases/latest"

    fun check(currentVersion: String): Result<AppUpdate?> = runCatching {
        val connection =
            URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 4_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "VicoVPN-Android")

            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub update check returned HTTP ${connection.responseCode}"
            }

            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(payload)
            val tag = release.optString("tag_name").trim()
            val releasePage = release.optString("html_url").trim()

            require(tag.isNotBlank() && releasePage.startsWith("https://github.com/")) {
                "GitHub returned an invalid release"
            }

            if (compareVersions(tag, currentVersion) <= 0) {
                return@runCatching null
            }

            val assets = release.optJSONArray("assets")
            var universalApk = ""

            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name").lowercase()
                    val url = asset.optString("browser_download_url").trim()

                    if (
                        name.endsWith(".apk") &&
                        "universal" in name &&
                        url.startsWith("https://github.com/")
                    ) {
                        universalApk = url
                        break
                    }
                }
            }

            AppUpdate(
                version = normalizeVersion(tag),
                title = release.optString("name").ifBlank { tag },
                downloadUrl = universalApk.ifBlank { releasePage }
            )
        } finally {
            connection.disconnect()
        }
    }

    internal fun compareVersions(left: String, right: String): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        val size = maxOf(leftParts.size, rightParts.size)

        for (index in 0 until size) {
            val difference =
                leftParts.getOrElse(index) { 0 }
                    .compareTo(rightParts.getOrElse(index) { 0 })

            if (difference != 0) return difference
        }

        return 0
    }

    private fun normalizeVersion(value: String): String =
        value.trim().removePrefix("v").removePrefix("V")

    private fun versionParts(value: String): List<Int> =
        normalizeVersion(value)
            .substringBefore('-')
            .split('.')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
