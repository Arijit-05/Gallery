package com.arijit.gallery.vault.crypto

import android.content.Context
import com.arijit.gallery.core.common.IoDispatcher
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultCryptoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val keysetPrefName = "gallery_vault_keyset_prefs"
    private val prefKeyName = "vault_master_keyset"
    private val masterKeyUri = "android-keystore://gallery_vault_master_key"

    private val aead: Aead by lazy {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, prefKeyName, keysetPrefName)
            .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
            .withMasterKeyUri(masterKeyUri)
            .build()
            .keysetHandle
        keysetHandle.getPrimitive(Aead::class.java)
    }

    val vaultDir: File by lazy {
        File(context.filesDir, "vault_encrypted").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Encrypts data from [sourceStream] directly into an encrypted file in the private vault directory.
     * Returns the name of the created encrypted file.
     */
    suspend fun encryptStreamToFile(
        sourceStream: InputStream,
        associatedData: String
    ): String = withContext(ioDispatcher) {
        val fileName = "${UUID.randomUUID()}.enc"
        val targetFile = File(vaultDir, fileName)
        val plainBytes = sourceStream.use { it.readBytes() }
        val encryptedBytes = aead.encrypt(plainBytes, associatedData.toByteArray(Charsets.UTF_8))

        FileOutputStream(targetFile).use { out ->
            out.write(encryptedBytes)
            out.flush()
        }
        fileName
    }

    /**
     * Decrypts [fileName] directly into memory.
     * Never writes plain bytes to disk.
     */
    suspend fun decryptToBytes(
        fileName: String,
        associatedData: String
    ): ByteArray? = withContext(ioDispatcher) {
        val file = File(vaultDir, fileName)
        if (!file.exists()) return@withContext null
        runCatching {
            val encryptedBytes = FileInputStream(file).use { it.readBytes() }
            aead.decrypt(encryptedBytes, associatedData.toByteArray(Charsets.UTF_8))
        }.getOrNull()
    }

    /**
     * Decrypts [fileName] to an in-memory [InputStream].
     */
    suspend fun decryptToStream(
        fileName: String,
        associatedData: String
    ): InputStream? = withContext(ioDispatcher) {
        val bytes = decryptToBytes(fileName, associatedData) ?: return@withContext null
        ByteArrayInputStream(bytes)
    }

    /**
     * Deletes the encrypted file from disk.
     */
    suspend fun deleteEncryptedFile(fileName: String): Boolean = withContext(ioDispatcher) {
        val file = File(vaultDir, fileName)
        if (file.exists()) file.delete() else true
    }
}

