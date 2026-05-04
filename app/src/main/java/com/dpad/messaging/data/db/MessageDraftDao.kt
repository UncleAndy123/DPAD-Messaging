package com.dpad.messaging.data.db

import androidx.room.*

@Dao
interface MessageDraftDao {

    @Query("SELECT * FROM message_drafts WHERE threadId = :threadId LIMIT 1")
    suspend fun getDraft(threadId: Long): MessageDraft?

    @Query("SELECT * FROM message_drafts")
    suspend fun getAllDrafts(): List<MessageDraft>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: MessageDraft)

    @Query("DELETE FROM message_drafts WHERE threadId = :threadId")
    suspend fun deleteDraft(threadId: Long)
}
