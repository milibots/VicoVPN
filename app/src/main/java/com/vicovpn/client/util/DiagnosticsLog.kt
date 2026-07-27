package com.vicovpn.client.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object DiagnosticsLog {
    private const val MAX_LINES = 180
    private val lines = ConcurrentLinkedDeque<String>()
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun add(category: String, message: String) {
        val line = "${formatter.format(Date())} [$category] ${Redactor.redact(message)}"
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.pollFirst()
    }

    fun snapshot(): String = lines.joinToString("\n")
    fun clear() = lines.clear()
}
