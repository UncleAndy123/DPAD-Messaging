package com.dpad.messaging.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.dpad.messaging.receivers.ScheduledMessageReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ScheduledMessageSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun canScheduleExactIsTruePreS() {
        assertTrue(ScheduledMessageScheduler.canScheduleExact(context))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun scheduleMessageCreatesAlarmPendingIntent() {
        val triggerAt = System.currentTimeMillis() + 60_000L
        val result = ScheduledMessageScheduler.scheduleMessage(context, messageId = 42L, triggerAtMillis = triggerAt)

        assertEquals(ScheduledMessageScheduler.ScheduleResult.ScheduledExact, result)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val shadowAlarmManager = shadowOf(alarmManager)
        val nextAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull(nextAlarm)

        val pi: PendingIntent = requireNotNull(requireNotNull(nextAlarm).operation)
        val savedIntent: Intent = shadowOf(pi).savedIntent
        assertEquals(ScheduledMessageReceiver::class.java.name, savedIntent.component?.className)
        assertEquals(42L, savedIntent.getLongExtra(ScheduledMessageReceiver.EXTRA_SCHEDULED_MESSAGE_ID, -1L))
    }

    @Test
    fun scheduleMessageFailsForInvalidId() {
        val result = ScheduledMessageScheduler.scheduleMessage(
            context,
            messageId = 0L,
            triggerAtMillis = System.currentTimeMillis() + 60_000L
        )
        assertTrue(result is ScheduledMessageScheduler.ScheduleResult.Failed)
    }
}
