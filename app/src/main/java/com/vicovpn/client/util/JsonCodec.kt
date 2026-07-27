package com.vicovpn.client.util

object JsonCodec {
    fun stringify(value: Any?): String = buildString { writeValue(value) }

    @Suppress("UNCHECKED_CAST")
    private fun StringBuilder.writeValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> writeString(value)
            is Number, is Boolean -> append(value.toString())
            is Map<*, *> -> {
                append('{')
                value.entries.forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    writeString(entry.key.toString())
                    append(':')
                    writeValue(entry.value)
                }
                append('}')
            }
            is Iterable<*> -> {
                append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    writeValue(item)
                }
                append(']')
            }
            is Array<*> -> writeValue(value.asIterable())
            else -> writeString(value.toString())
        }
    }

    private fun StringBuilder.writeString(value: String) {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    fun parse(text: String): Any? = Parser(text).parse()

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: error("JSON root is not an object")

    private class Parser(private val text: String) {
        private var index = 0

        fun parse(): Any? {
            skipWhitespace()
            val value = readValue()
            skipWhitespace()
            require(index == text.length) { "Unexpected trailing JSON at $index" }
            return value
        }

        private fun readValue(): Any? {
            skipWhitespace()
            require(index < text.length) { "Unexpected end of JSON" }
            return when (text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any?> {
            index++
            val map = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (peek('}')) { index++; return map }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace(); expect(':')
                map[key] = readValue()
                skipWhitespace()
                when {
                    peek(',') -> index++
                    peek('}') -> { index++; return map }
                    else -> error("Expected ',' or '}' at $index")
                }
            }
        }

        private fun readArray(): List<Any?> {
            index++
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (peek(']')) { index++; return list }
            while (true) {
                list += readValue()
                skipWhitespace()
                when {
                    peek(',') -> index++
                    peek(']') -> { index++; return list }
                    else -> error("Expected ',' or ']' at $index")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (index < text.length) {
                val c = text[index++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(index < text.length) { "Bad escape" }
                        when (val e = text[index++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(index + 4 <= text.length) { "Bad unicode escape" }
                                out.append(text.substring(index, index + 4).toInt(16).toChar())
                                index += 4
                            }
                            else -> error("Unknown escape \\$e")
                        }
                    }
                    else -> out.append(c)
                }
            }
            error("Unterminated string")
        }

        private fun readNumber(): Number {
            val start = index
            if (peek('-')) index++
            while (index < text.length && text[index].isDigit()) index++
            if (peek('.')) { index++; while (index < text.length && text[index].isDigit()) index++ }
            if (peek('e') || peek('E')) {
                index++
                if (peek('+') || peek('-')) index++
                while (index < text.length && text[index].isDigit()) index++
            }
            val raw = text.substring(start, index)
            require(raw.isNotEmpty()) { "Expected number at $start" }
            return if (raw.contains('.') || raw.contains('e', true)) raw.toDouble() else raw.toLong()
        }

        private fun <T> readLiteral(literal: String, value: T): T {
            require(text.startsWith(literal, index)) { "Expected $literal at $index" }
            index += literal.length
            return value
        }

        private fun expect(c: Char) {
            require(index < text.length && text[index] == c) { "Expected '$c' at $index" }
            index++
        }
        private fun peek(c: Char) = index < text.length && text[index] == c
        private fun skipWhitespace() { while (index < text.length && text[index].isWhitespace()) index++ }
    }
}
