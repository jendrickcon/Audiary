package com.example.audiary.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class SpotifyFailure(val status: Int, override val message: String, val retryAt: Long? = null) : IOException(message)

class SpotifyHttp(val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build()) {
    @Volatile private var blockedUntil = 0L

    suspend fun json(request: Request): JSONObject = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now < blockedUntil) throw SpotifyFailure(429, "Spotify needs a little time. Try again after " +
            java.time.Instant.ofEpochMilli(blockedUntil).atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss")) + ".", blockedUntil)
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    400 -> "Spotify couldn't complete this request. Reconnect your account and try again."
                    401 -> "Your Spotify connection has expired. Please reconnect."
                    403 -> "Spotify denied access. Check the app owner's Premium subscription and the account's access in the developer dashboard."
                    429 -> {
                        blockedUntil = retryAt(response.header("Retry-After"), now)
                        val quota = json.optJSONObject("error")?.optString("reason") == "QUOTA_EXCEEDED"
                        if (quota) "The Spotify development quota has been reached. Try later; your diary is still available."
                        else "Spotify is receiving too many requests. Please wait before retrying."
                    }
                    else -> "Spotify is unavailable right now. Please try again later."
                }
                throw SpotifyFailure(response.code, message, blockedUntil.takeIf { response.code == 429 })
            }
            if (body.isBlank()) throw IOException("Spotify returned an empty response.")
            json
        }
    }
    companion object {
        fun retryAt(header: String?, now: Long): Long {
            header?.toLongOrNull()?.let { return now + it.coerceIn(1, 86400) * 1000 }
            val date = runCatching { ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            return date?.coerceIn(now + 1000, now + 86400000) ?: (now + 60000)
        }
    }
}
