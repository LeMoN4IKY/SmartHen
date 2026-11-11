package com.smarthen.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only encrypted bearer tokens reach preferences; the encryption key never leaves Android Keystore. */
class AndroidSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("secure_sessions", Context.MODE_PRIVATE)
    private val alias = "smarthen.session.v1"
    private fun scope(server: String) = MessageDigest.getInstance("SHA-256")
        .digest(server.trimEnd('/').toByteArray()).joinToString("") { "%02x".format(it) }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun read(server: String): String? {
        val encoded = prefs.getString(scope(server), null) ?: return null
        return try {
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            require(data.size > 12)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
                updateAAD(scope(server).toByteArray())
                doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            // Restores/reinstalls can invalidate the hardware-backed key. Require login again.
            clear(server)
            null
        }
    }
    override fun write(server: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
            updateAAD(scope(server).toByteArray())
        }
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray())
        check(prefs.edit().putString(scope(server), Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    override fun clear(server: String) { check(prefs.edit().remove(scope(server)).commit()) }
}
