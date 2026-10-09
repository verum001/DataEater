package com.dataeater.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class OpenRouterSettings(val hasKey: Boolean = false, val modelId: String = "openrouter/free", val modelName: String = "Free models", val allowDatabase: Boolean = false)

/** API secrets stay encrypted in private preferences; encryption key is non-exportable. */
class OpenRouterSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("openrouter-private", Context.MODE_PRIVATE)
    private val alias = "dataeater_openrouter_secret"
    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read() = OpenRouterSettings(preferences.contains("secret"), preferences.getString("model", "openrouter/free")!!,
        preferences.getString("name", "Free models")!!, preferences.getBoolean("database", false))
    fun key(): String {
        val bytes = Base64.decode(preferences.getString("secret", null) ?: error("Add an OpenRouter API key in Settings."), Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    fun save(newKey: String, model: OpenRouterProtocol.Model, allowDatabase: Boolean) {
        val edit = preferences.edit()
        if (newKey.isNotBlank()) {
            val value = newKey.trim()
            require(value.length in 10..4096 && value.none { it.isWhitespace() || it.isISOControl() }) { "Check your API key." }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
            edit.putString("secret", Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
        }
        check(newKey.isNotBlank() || preferences.contains("secret")) { "Add an OpenRouter API key." }
        require(model.id.matches(Regex("[A-Za-z0-9_./:@+-]{1,200}")))
        check(edit.putString("model", model.id).putString("name", model.name).putBoolean("database", allowDatabase).commit()) { "Could not save OpenRouter settings." }
    }
    fun remove() { preferences.edit().clear().commit(); KeyStore.getInstance("AndroidKeyStore").apply { load(null); if (containsAlias(alias)) deleteEntry(alias) } }
}
