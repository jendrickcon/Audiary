package com.example.audiary.spotify

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class CallbackUri(
    val scheme: String?,
    val host: String?,
    val path: String?,
    val params: Map<String, List<String>>
) {
    val paramNames: List<String> get() = params.keys.toList()
    fun queryParam(name: String): String? = params[name]?.firstOrNull()
    fun queryParams(name: String): List<String> = params[name].orEmpty()

    companion object {
        fun parse(raw: String): CallbackUri {
            val actualBase = raw.substringBefore('?').substringBefore('#')
            val uri = runCatching { URI(actualBase) }.getOrNull()
            val scheme = uri?.scheme ?: run {
                val colon = raw.indexOf(':')
                if (colon != -1) raw.substring(0, colon).lowercase() else null
            }
            val host = uri?.host ?: run {
                val schemeEnd = raw.indexOf("://")
                if (schemeEnd != -1) {
                    val afterScheme = raw.substring(schemeEnd + 3)
                    val slash = afterScheme.indexOfAny(charArrayOf('/', '?', '#'))
                    val hostPart = if (slash != -1) afterScheme.substring(0, slash) else afterScheme
                    hostPart.ifBlank { null }
                } else null
            }
            val path = uri?.path ?: run {
                val qIndex = raw.indexOfAny(charArrayOf('?', '#'))
                val preQuery = if (qIndex != -1) raw.substring(0, qIndex) else raw
                val schemeEnd = raw.indexOf("://")
                if (schemeEnd != -1) {
                    val afterScheme = preQuery.substring(schemeEnd + 3)
                    val slash = afterScheme.indexOf('/')
                    if (slash != -1) afterScheme.substring(slash) else ""
                } else ""
            }

            val map = mutableMapOf<String, MutableList<String>>()
            val qIndex = raw.indexOf('?')
            if (qIndex != -1) {
                val hashIndex = raw.indexOf('#', qIndex)
                val queryString = if (hashIndex != -1) raw.substring(qIndex + 1, hashIndex) else raw.substring(qIndex + 1)
                for (pair in queryString.split('&')) {
                    if (pair.isEmpty()) continue
                    val eqIndex = pair.indexOf('=')
                    val rawKey = if (eqIndex != -1) pair.substring(0, eqIndex) else pair
                    val rawVal = if (eqIndex != -1) pair.substring(eqIndex + 1) else ""
                    val key = runCatching { URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name()) }.getOrDefault(rawKey)
                    val value = runCatching { URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name()) }.getOrDefault(rawVal)
                    map.getOrPut(key) { mutableListOf() }.add(value)
                }
            }
            return CallbackUri(scheme, host, path, map)
        }
    }
}

