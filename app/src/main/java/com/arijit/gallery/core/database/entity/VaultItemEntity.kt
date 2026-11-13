package com.arijit.gallery.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val originalMediaId: Long,
    val encryptedFileName: String,
    val originalDisplayName: String,
    val mimeType: String,
    val originalRelativePath: String,
    val originalDateTakenMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val hiddenAtMs: Long
)

