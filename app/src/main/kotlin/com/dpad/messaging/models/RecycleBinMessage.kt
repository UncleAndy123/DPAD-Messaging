package com.dpad.messaging.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "recycle_bin_messages")
data class RecycleBinMessage(
    /** Stable recycle-entry key; MMS and conversation keys use separate negative ranges. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    /** Original SMS or MMS provider row ID; zero for whole-conversation entries. */
    @ColumnInfo(name = "provider_id")
    val providerId: Long = 0L,

    @ColumnInfo(name = "thread_id")
    val threadId: Long = -1L,

    @ColumnInfo(name = "is_mms")
    val isMms: Boolean = false,

    @ColumnInfo(name = "is_conversation")
    val isConversation: Boolean = false,

    /** Original Telephony SMS type, used when restoring a removed SMS snapshot. */
    @ColumnInfo(name = "message_type")
    val messageType: Int = 1,

    @ColumnInfo(name = "was_read")
    val wasRead: Boolean = true,

    @ColumnInfo(name = "participants_json")
    val participantsJson: String = "[]",

    @ColumnInfo(name = "address")
    val address: String = "",

    @ColumnInfo(name = "sender_name")
    val senderName: String = "",

    @ColumnInfo(name = "body")
    val body: String = "",

    @ColumnInfo(name = "date")
    val date: Long = 0L,

    @ColumnInfo(name = "deleted_ts")
    val deletedTs: Long = System.currentTimeMillis()
) {
    companion object {
        fun smsEntryId(providerId: Long): Long = providerId
        fun mmsEntryId(providerId: Long): Long = -providerId
        fun conversationEntryId(threadId: Long): Long = Long.MIN_VALUE + threadId
    }
}
