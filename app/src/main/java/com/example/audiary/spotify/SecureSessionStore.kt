package com.example.audiary.spotify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Tokens(val access: String, val refresh: String, val expiresAt: Long)
data class PendingAuth(val verifier: String, val state: String, val createdAt: Long)
data class SpotifySession(val tokens: Tokens? = null, val pending: PendingAuth? = null)
interface SessionStore {
    fun read(): SpotifySession
    fun write(session: SpotifySession)
    fun clear()
}

/** Encrypted with a device-bound Android Keystore key and excluded from Android backups. */
class SecureSessionStore(context: Context) : SessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "spotify-session.enc"))
    private val alias = "audiary.spotify.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun read(): SpotifySession {
        if (!file.baseFile.exists()) return SpotifySession()
        val bytes = file.readFully()
        require(bytes.size > 12 && bytes.size < 65536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        val tokens = json.optJSONObject("tokens")?.let { Tokens(it.getString("access"), it.getString("refresh"), it.getLong("expiresAt")) }
        val pending = json.optJSONObject("pending")?.let { PendingAuth(it.getString("verifier"), it.getString("state"), it.getLong("createdAt")) }
        return SpotifySession(tokens, pending)
    }
    override fun write(session: SpotifySession) {
        val json = JSONObject()
        session.tokens?.let { json.put("tokens", JSONObject().put("access", it.access).put("refresh", it.refresh).put("expiresAt", it.expiresAt)) }
        session.pending?.let { json.put("pending", JSONObject().put("verifier", it.verifier).put("state", it.state).put("createdAt", it.createdAt)) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try { out.write(encrypted); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    override fun clear() = file.delete()
}
