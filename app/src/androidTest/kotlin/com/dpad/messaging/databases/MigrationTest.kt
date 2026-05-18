package com.dpad.messaging.databases

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MessagesDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2And3() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages` (
                    `id` INTEGER NOT NULL,
                    `thread_id` INTEGER NOT NULL,
                    `body` TEXT NOT NULL,
                    `type` INTEGER NOT NULL,
                    `date` INTEGER NOT NULL,
                    `date_sent` INTEGER NOT NULL,
                    `read` INTEGER NOT NULL,
                    `address` TEXT NOT NULL,
                    `sender_name` TEXT NOT NULL,
                    `sender_photo_uri` TEXT NOT NULL,
                    `is_mms` INTEGER NOT NULL,
                    `status` INTEGER NOT NULL,
                    `subscription_id` INTEGER NOT NULL,
                    `is_scheduled` INTEGER NOT NULL,
                    `attachments_json` TEXT NOT NULL,
                    `participants_json` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `conversations` (
                    `thread_id` INTEGER NOT NULL,
                    `phone_number` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `photo_uri` TEXT NOT NULL,
                    `snippet` TEXT NOT NULL,
                    `date` INTEGER NOT NULL,
                    `read` INTEGER NOT NULL,
                    `unread_count` INTEGER NOT NULL,
                    `is_group_conversation` INTEGER NOT NULL,
                    `archived` INTEGER NOT NULL,
                    `pinned` INTEGER NOT NULL,
                    `muted` INTEGER NOT NULL,
                    `uses_custom_title` INTEGER NOT NULL,
                    `is_scheduled` INTEGER NOT NULL,
                    `participants` TEXT NOT NULL,
                    PRIMARY KEY(`thread_id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `attachments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `message_id` INTEGER NOT NULL,
                    `uri_string` TEXT NOT NULL,
                    `mimetype` TEXT NOT NULL,
                    `width` INTEGER NOT NULL,
                    `height` INTEGER NOT NULL,
                    `filename` TEXT NOT NULL,
                    `file_size` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `drafts` (
                    `thread_id` INTEGER NOT NULL,
                    `body` TEXT NOT NULL,
                    `date` INTEGER NOT NULL,
                    PRIMARY KEY(`thread_id`)
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            testDb,
            3,
            true,
            MessagesDatabase.MIGRATION_1_2,
            MessagesDatabase.MIGRATION_2_3
        )
    }
}
