package com.dpad.messaging.helpers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsMultipartTrackerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sms_multipart_tracker", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun recordSentFinalizesWhenAllPartsSucceed() {
        val first = SmsMultipartTracker.recordSent(context, messageId = 101L, totalParts = 2, partSuccess = true)
        assertFalse(first.shouldFinalize)

        val second = SmsMultipartTracker.recordSent(context, messageId = 101L, totalParts = 2, partSuccess = true)
        assertTrue(second.shouldFinalize)
        assertTrue(second.isSuccess)
    }

    @Test
    fun recordSentFinalizesImmediatelyOnFailure() {
        val result = SmsMultipartTracker.recordSent(context, messageId = 202L, totalParts = 3, partSuccess = false)
        assertTrue(result.shouldFinalize)
        assertFalse(result.isSuccess)
    }

    @Test
    fun recordDeliveredIgnoresLateDuplicatesAfterFinalization() {
        SmsMultipartTracker.recordDelivered(context, messageId = 303L, totalParts = 1, partSuccess = true)
        val duplicate = SmsMultipartTracker.recordDelivered(context, messageId = 303L, totalParts = 1, partSuccess = true)
        assertFalse(duplicate.shouldFinalize)
    }
}
