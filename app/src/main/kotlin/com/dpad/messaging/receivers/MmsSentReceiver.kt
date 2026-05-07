package com.dpad.messaging.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dpad.messaging.events.RefreshConversations
import com.dpad.messaging.events.RefreshMessages
import com.dpad.messaging.helpers.MmsSender
import org.greenrobot.eventbus.EventBus

/**
 * Receives the result PendingIntent fired by mmslib Transaction via SmsManager.sendMultimediaMessage().
 *
 * With mmslib:
 *  - mmslib handles all provider persistence (inserts, updates to sent/failed)
 *  - We receive this callback to refresh the UI
 *  - resultCode = RESULT_OK (-1) = sent, otherwise = failed
 *  - threadId is provided for UI refresh targeting
 *
 * This receiver logs the result and posts EventBus events for UI refresh.
 */
class MmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(MmsSender.EXTRA_THREAD_ID, -1L)
        val hasImage = intent.getBooleanExtra("extra_has_image", false)
        val isSuccess = resultCode == Activity.RESULT_OK

        val extras = intent.extras?.keySet()?.sorted()?.joinToString() ?: "<none>"
        Log.d(
            "DPAD_MSG",
            "MmsSentReceiver.onReceive() threadId=$threadId hasImage=$hasImage resultCode=$resultCode isSuccess=$isSuccess extras=$extras"
        )

        // mmslib has already updated the provider (inserted and updated msg_box)
        // Our job is to refresh the UI so the message appears with correct status
        EventBus.getDefault().post(RefreshConversations())
        if (threadId > 0) {
            EventBus.getDefault().post(RefreshMessages(threadId))
            Log.d("DPAD_MSG", "MmsSentReceiver: posted refresh events for threadId=$threadId")
        }
    }
}

