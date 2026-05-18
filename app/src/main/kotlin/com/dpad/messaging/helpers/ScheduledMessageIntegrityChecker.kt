package com.dpad.messaging.helpers

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.dpad.messaging.App
import com.dpad.messaging.R
import com.dpad.messaging.events.RefreshConversations
import com.dpad.messaging.events.RefreshMessages
import com.dpad.messaging.models.Message
import org.greenrobot.eventbus.EventBus

object ScheduledMessageIntegrityChecker {

    suspend fun run(context: Context) {
        val dao = App.get().database.messagesDao()
        val now = System.currentTimeMillis()

        val future = dao.getScheduledMessagesAfter(now)
        future.forEach { message ->
            val triggerAt = message.scheduledDate ?: return@forEach
            val result = ScheduledMessageScheduler.scheduleMessage(context, message.id, triggerAt)
            if (result is ScheduledMessageScheduler.ScheduleResult.Failed) {
                failMessage(context, dao, message)
            }
        }

        val graceWindowMillis = 5 * 60 * 1000L
        val staleCutoff = now - graceWindowMillis
        val pastDue = dao.getPastDueScheduledMessages(staleCutoff)
        pastDue.forEach { message ->
            failMessage(context, dao, message)
        }
    }

    private suspend fun failMessage(
        context: Context,
        dao: com.dpad.messaging.databases.daos.MessagesDao,
        message: Message
    ) {
        dao.updateMessage(
            message.copy(
                type = Message.TYPE_FAILED,
                status = Message.STATUS_FAILED,
                isScheduled = false,
                scheduledDate = null
            )
        )
        showScheduledFailureNotification(context, message.threadId)
        EventBus.getDefault().post(RefreshConversations())
        if (message.threadId > 0L) {
            EventBus.getDefault().post(RefreshMessages(message.threadId))
        }
    }

    private fun showScheduledFailureNotification(context: Context, threadId: Long) {
        val accent = ThemeManager.accentColor(context)
        val notification = NotificationCompat.Builder(context, App.CHANNEL_SEND_FAILURE)
            .setSmallIcon(R.drawable.ic_new_message)
            .setColor(accent)
            .setColorized(true)
            .setContentTitle(context.getString(R.string.scheduled_send_failed_title))
            .setContentText(context.getString(R.string.scheduled_send_failed_exact_alarm_body))
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify((threadId and 0x7FFFFFFF).toInt() + 50_000, notification)
    }
}
