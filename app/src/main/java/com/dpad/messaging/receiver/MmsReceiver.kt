package com.dpad.messaging.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dpad.messaging.MainActivity
import com.dpad.messaging.R
import com.dpad.messaging.data.db.AppDatabase
import com.klinker.android.send_message.MmsReceivedReceiver

/**
 * Receives the post-download MMS callback from Fossify mmslib.
 *
 * Pipeline (Fossify mmslib 1.0.0):
 *   WAP_PUSH_DELIVER → com.android.mms.transaction.PushReceiver (library)
 *     → TransactionService (library) → NotificationTransaction.download()
 *     → Telephony provider write
 *     → BroadcastUtils.sendExplicitBroadcast("com.klinker.android.messaging.MMS_RECEIVED")
 *     → MmsReceivedReceiver.onReceive() → isAddressBlocked() → onMessageReceived() / onError()
 *
 * The manifest declares taskAffinity="com.klinker.android.messaging.MMS_RECEIVED" so
 * BroadcastUtils can route to this receiver by scanning the package's receiver list and
 * matching taskAffinity to the action string.
 */
class MmsReceiver : MmsReceivedReceiver() {

    companion object {
        private const val TAG = "MmsReceiver"
    }

    /**
     * Called by the library before the MMS is persisted.
     * Return true to silently drop the message (deleted from provider + no notification).
     */
    override fun isAddressBlocked(context: Context, address: String): Boolean {
        val db = AppDatabase.getDatabase(context)
        return try {
            val threadId = resolveThreadId(context, address) ?: return false
            db.threadMetadataDao().getMetadataBlocking(threadId)?.isBlocked == true
        } catch (e: Exception) {
            Log.e(TAG, "isAddressBlocked check failed", e)
            false
        }
    }

    /**
     * Called after the MMS has been downloaded and persisted to the Telephony provider.
     * [messageUri] points to the MMS row, e.g. content://mms/42.
     */
    override fun onMessageReceived(context: Context, messageUri: Uri) {
        Log.d(TAG, "onMessageReceived: $messageUri")
        val mmsId = messageUri.lastPathSegment?.toLongOrNull() ?: return
        val threadId = getMmsThreadId(context, mmsId) ?: return

        // Check muted — blocked was already handled by isAddressBlocked()
        val db = AppDatabase.getDatabase(context)
        val meta = try {
            db.threadMetadataDao().getMetadataBlocking(threadId)
        } catch (_: Exception) { null }

        if (meta?.isMuted == true) {
            Log.d(TAG, "Thread $threadId is muted — suppressing notification")
            return
        }

        val address = getMmsAddress(context, mmsId)
        val snippet = getMmsTextSnippet(context, mmsId)
        postNotification(context, mmsId, threadId, address, snippet)
    }

    /**
     * Called if the library encounters an error downloading the MMS.
     */
    override fun onError(context: Context, error: String) {
        Log.e(TAG, "MMS download error: $error")
        // Optionally post a "failed to download" notification here in future.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveThreadId(context: Context, address: String): Long? {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (_: Exception) { null }
    }

    private fun getMmsThreadId(context: Context, mmsId: Long): Long? {
        return try {
            context.contentResolver.query(
                Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, mmsId.toString()),
                arrayOf(Telephony.Mms.THREAD_ID),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        } catch (_: Exception) { null }
    }

    private fun getMmsAddress(context: Context, mmsId: Long): String {
        return try {
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsId/addr"),
                arrayOf("address"),
                "type = 137", // 0x89 = PduHeaders.FROM
                null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) ?: "" else ""
            } ?: ""
        } catch (_: Exception) { "" }
    }

    private fun getMmsTextSnippet(context: Context, mmsId: Long): String {
        return try {
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsId/part"),
                arrayOf("ct", "text"),
                "ct = 'text/plain'",
                null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(1) ?: "" else ""
            } ?: ""
        } catch (_: Exception) { "" }
    }

    private fun postNotification(
        context: Context,
        mmsId: Long,
        threadId: Long,
        address: String,
        snippet: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                SmsDeliverReceiver.CHANNEL_ID,
                SmsDeliverReceiver.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifications for incoming messages" }
        )

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("threadId", threadId)
            putExtra("address", address)
        }
        val pi = PendingIntent.getActivity(
            context, threadId.toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = address.ifBlank { "MMS" }
        val text = snippet.ifBlank { "\uD83D\uDCF7 MMS" }

        nm.notify(
            threadId.toInt(),
            NotificationCompat.Builder(context, SmsDeliverReceiver.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
        )
    }
}
