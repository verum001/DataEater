package com.dataeater.app.builder

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Exportable publisher keys stored privately behind an AndroidKeyStore wrapping key. */
class PublisherVault(context: Context) {
    private val folder = File(context.filesDir, "builder/keys").apply { mkdirs() }
    data class Item(val id: String, val name: String, val kind: String)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("dataeater-builder-publisher-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("dataeater-builder-publisher-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized fun save(name: String, kind: String, record: JSONObject): Item {
        require(kind in setOf("signing", "content")); if (kind == "signing") BuilderCrypto.validateKey(record) else BuilderCrypto.secretKey(record)
        val id = UUID.randomUUID().toString(); val value = JSONObject().put("name", name).put("kind", kind).put("record", record).toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value)
        File(folder, "$id.vault").writeBytes(cipher.iv + encrypted)
        return Item(id, name, kind)
    }
    @Synchronized fun read(item: Item): JSONObject {
        require(Regex("[a-f0-9-]{36}").matches(item.id))
        val bytes = File(folder, "${item.id}.vault").readBytes(); require(bytes.size in 29..65536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        return JSONObject(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8))
    }
    @Synchronized fun list(): List<Item> = folder.listFiles().orEmpty().filter { it.name.endsWith(".vault") }.map { file ->
        val item = Item(file.nameWithoutExtension, "", "")
        val json = read(item); Item(item.id, json.getString("name"), json.getString("kind"))
    }.sortedBy { it.name }
    fun record(item: Item): JSONObject = read(item).getJSONObject("record")
}
