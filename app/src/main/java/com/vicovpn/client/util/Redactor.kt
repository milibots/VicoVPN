package com.vicovpn.client.util

object Redactor {
    private val uuid = Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val links = Regex("(?i)(vmess|vless|trojan|ss)://\\S+")
    private val password = Regex("(?i)(password|id)\\s*[=:]\\s*[^,;\\s]+")
    fun redact(input: String): String = input
        .replace(links, "[REDACTED_PROFILE]")
        .replace(uuid, "[REDACTED_UUID]")
        .replace(password) { "${it.value.substringBeforeAny('=', ':')}=[REDACTED]" }

    private fun String.substringBeforeAny(vararg chars: Char): String {
        val i = indices.firstOrNull { this[it] in chars } ?: length
        return substring(0, i)
    }
}
