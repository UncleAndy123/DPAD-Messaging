package com.dpad.messaging.databases.daos

import androidx.room.*
import com.dpad.messaging.models.Message
import com.dpad.messaging.models.RecycleBinMessage

@Dao
interface MessagesDao {

    @Query("SELECT * FROM messages WHERE thread_id = :threadId AND id NOT IN (SELECT id FROM recycle_bin_messages) ORDER BY date ASC")
    suspend fun getMessagesForThread(threadId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: Long): Message?

    @Query("SELECT * FROM messages")
    suspend fun getAllMessages(): List<Message>

    @Query("SELECT * FROM messages WHERE is_scheduled = 1 AND scheduled_date > :timestamp ORDER BY scheduled_date ASC")
    suspend fun getScheduledMessagesAfter(timestamp: Long): List<Message>

    @Query("SELECT * FROM messages WHERE is_scheduled = 1 AND scheduled_date IS NOT NULL AND scheduled_date <= :timestamp ORDER BY scheduled_date ASC")
    suspend fun getPastDueScheduledMessages(timestamp: Long): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)

    @Update
    suspend fun updateMessage(message: Message)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("UPDATE messages SET read = 1 WHERE thread_id = :threadId")
    suspend fun markThreadRead(threadId: Long)

    @Query("UPDATE messages SET read = 0 WHERE thread_id = :threadId")
    suspend fun markThreadUnread(threadId: Long)

    // ─── Recycle Bin ───

    @Query("SELECT * FROM recycle_bin_messages")
    suspend fun getRecycleBinMessages(): List<RecycleBinMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecycleBinMessage(message: RecycleBinMessage)

    @Query("DELETE FROM recycle_bin_messages WHERE id = :id")
    suspend fun removeFromRecycleBin(id: Long)

    @Query("DELETE FROM recycle_bin_messages WHERE thread_id = :threadId")
    suspend fun removeRecycleBinMessagesForThread(threadId: Long)

    @Query("DELETE FROM recycle_bin_messages")
    suspend fun emptyRecycleBin()
}
