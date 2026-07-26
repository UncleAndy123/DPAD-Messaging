package com.dpad.messaging.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.RemoteInput
import com.dpad.messaging.BuildConfig
import com.dpad.messaging.events.RefreshConversations
import com.dpad.messaging.events.RefreshMessages
import com.dpad.messaging.helpers.AppCoroutineScopes
import com.dpad.messaging.helpers.MessageSenders
import com.dpad.messaging.helpers.MmsDownloader
import com.dpad.messaging.helpers.NotificationHelper
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus

/**
 * Handles the "Reply" notification action (inline RemoteInput reply).
 *
 * Extracts the reply text, sends it via SmsSender, cancels the notification,
 * and fires refresh events so the thread updates.
 */
class DirectReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = results.getCharSequence(NotificationHelper.REPLY_KEY)
            ?.toString()?.trim() ?: return
        if (replyText.isBlank()) return

        val threadId    = intent.getLongExtra(MarkAsReadReceiver.EXTRA_THREAD_ID, -1L)
        val notifId     = intent.getIntExtra(MarkAsReadReceiver.EXTRA_NOTIFICATION_ID, -1)
        val phoneNumber = intent.getStringExtra(NotificationHelper.EXTRA_PHONE_NUMBER) ?: return

        if (threadId == -1L) return

        // goAsync() keeps the receiver alive while the coroutine runs.
        val pendingResult = goAsync()
        AppCoroutineScopes.io.launch {
            var sendSucceeded = false
            try {
                MessageSenders.unified.sendSms(
                    context = context,
                    phoneNumber = phoneNumber,
                    body = replyText,
                    threadId = threadId
                )
                sendSucceeded = true
                EventBus.getDefault().post(RefreshMessages(threadId))
                EventBus.getDefault().post(RefreshConversations())
            } finally {
                if (sendSucceeded && notifId != -1) {
                    NotificationHelper.cancelNotification(context, notifId)
                }
                pendingResult.finish()
            }
        }
    }
}

/**
 * Receives the result PendingIntent fired by
 * [android.telephony.SmsManager.downloadMultimediaMessage] (dispatched from
 * MmsSystemDownloader).
 *
 * On RESULT_OK the system has written the raw M-Retrieve-Conf PDU into the content
 * URI we supplied. We read those bytes and hand them to the EXISTING
 * MmsDownloader.processPdu() so parsing, MDM filtering, and content-provider storage
 * all reuse the code path the manual downloader already used.
 *
 * On failure we do NOT delete the placeholder here — MmsReceiver falls back to the
 * manual MmsDownloader, which owns placeholder cleanup on its own failure.
 */
class MmsDownloadResultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION                  = "com.dpad.messaging.MMS_SYSTEM_DOWNLOADED"
        const val EXTRA_MMS_ID            = "extra_mms_id"
        const val EXTRA_PDU_URI           = "extra_pdu_uri"
        const val EXTRA_CONTENT_LOCATION  = "extra_content_location"
        private const val TAG = "DPAD_MSG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val result   = resultCode
        val msgId    = intent.getLongExtra(EXTRA_MMS_ID, -1L)
        val pduUriStr = intent.getStringExtra(EXTRA_PDU_URI)
        val httpStatus = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "MmsDownloadResultReceiver.onReceive() resultCode=$result httpStatus=$httpStatus msgId=$msgId uri=$pduUriStr")
        }

        if (result != Activity.RESULT_OK || pduUriStr.isNullOrBlank() || msgId <= 0L) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "MmsDownloadResultReceiver: system download failed (result=$result httpStatus=$httpStatus) — manual fallback owns cleanup")
            }
            EventBus.getDefault().post(RefreshConversations())
            return
        }

        val pduUri = Uri.parse(pduUriStr)
        val pendingResult = goAsync()
        AppCoroutineScopes.io.launch {
            try {
                val pduBytes = try {
                    context.contentResolver.openInputStream(pduUri)?.use { it.readBytes() }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "MmsDownloadResultReceiver: could not read PDU from $pduUri", e)
                    null
                }

                if (pduBytes == null || pduBytes.isEmpty()) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "MmsDownloadResultReceiver: empty PDU for msgId=$msgId")
                    EventBus.getDefault().post(RefreshConversations())
                    return@launch
                }

                if (BuildConfig.DEBUG) Log.d(TAG, "MmsDownloadResultReceiver: read ${pduBytes.size} PDU bytes for msgId=$msgId — handing to processPdu()")

                // Reuse the existing parse + MDM filter + store path.
                MmsDownloader.processPdu(context, msgId, pduBytes)
            } finally {
                // Best-effort cleanup of the temp PDU file.
                runCatching {
                    context.contentResolver.delete(pduUri, null, null)
                }
                pendingResult.finish()
            }
        }
    }
}