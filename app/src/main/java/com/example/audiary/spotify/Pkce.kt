package com.example.audiary.spotify

import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Pkce {
    fun random(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun matchesState(expected: String, received: String?): Boolean = received != null && expected.isNotBlank() &&
        MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), received.toByteArray(Charsets.UTF_8))
    fun isRedirect(actual: String, expected: String): Boolean = runCatching {
        if (actual.contains('#')) return false
        val actualBase = actual.substringBefore('?').substringBefore('#')
        val expectedBase = expected.substringBefore('?').substringBefore('#')
        val a = URI(actualBase)
        val e = URI(expectedBase)
        val schemeMatch = a.scheme.equals(e.scheme, ignoreCase = true)
        val hostMatch = (a.host == null && e.host == null) || (a.host != null && a.host.equals(e.host, ignoreCase = true))
        val portMatch = a.port == e.port || (a.port == -1 && (e.port == 80 || e.port == 443)) || (e.port == -1 && (a.port == 80 || a.port == 443))
        val pathMatch = a.rawPath.orEmpty().removeSuffix("/") == e.rawPath.orEmpty().removeSuffix("/")
        schemeMatch && hostMatch && portMatch && pathMatch && a.userInfo == null
    }.getOrDefault(false)
}
