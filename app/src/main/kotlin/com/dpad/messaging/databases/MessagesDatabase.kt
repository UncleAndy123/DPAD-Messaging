package com.dpad.messaging.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dpad.messaging.databases.daos.*
import com.dpad.messaging.models.*

@Database(
    entities = [
        Conversation::class,
        Message::class,
        Attachment::class,
        Draft::class,
        RecycleBinMessage::class,
        BlockedKeyword::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MessagesDatabase : RoomDatabase() {

    abstract fun conversationsDao(): ConversationsDao
    abstract fun messagesDao(): MessagesDao
    abstract fun attachmentsDao(): AttachmentsDao
    abstract fun draftsDao(): DraftsDao
    abstract fun blockedKeywordsDao(): BlockedKeywordsDao

    companion object {
        private const val DB_NAME = "dpad_messages.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recycle_bin_messages` (
                        `id` INTEGER NOT NULL,
                        `address` TEXT NOT NULL,
                        `sender_name` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `deleted_ts` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocked_keywords` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `keyword` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN scheduled_date INTEGER")
            }
        }

        @Volatile
        private var instance: MessagesDatabase? = null

        fun getInstance(context: Context): MessagesDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): MessagesDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MessagesDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .build()
        }
    }
}
