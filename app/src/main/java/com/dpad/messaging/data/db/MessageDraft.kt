package com.dpad.messaging.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_drafts")
data class MessageDraft(
    @PrimaryKey val threadId: Long,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis()
)
