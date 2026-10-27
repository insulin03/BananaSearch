package com.banana.search.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiKeyStore(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("qsearch_secret", Context.MODE_PRIVATE)

    fun masked(): String {
        val k = load()
        if (k.isNullOrEmpty()) return ""
        return if (k.length <= 10) "****" else k.substring(0, 6) + "****" + k.substring(k.length - 4)
    }

    fun save(raw: String) {
        val key = raw.trim()
        if (key.isEmpty()) {
            clear()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        sp.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? {
        val ivB64 = sp.getString(KEY_IV, null) ?: return null
        val ctB64 = sp.getString(KEY_CT, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (t: Throwable) {

            clear()
            null
        }
    }

    fun clear() {
        sp.edit().remove(KEY_IV).remove(KEY_CT).apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "qsearch_api_key_v1"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val KEY_IV = "k_iv"
        const val KEY_CT = "k_ct"
    }
}
