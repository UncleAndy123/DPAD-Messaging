package com.dpad.messaging.helpers

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Telephony
import android.util.Log
import com.klinker.android.send_message.Message as KlinkerMessage
import com.klinker.android.send_message.Settings as KlinkerSettings
import com.klinker.android.send_message.Transaction as KlinkerTransaction
import com.dpad.messaging.extensions.getOwnPhoneNumbers
import com.dpad.messaging.receivers.MmsSentReceiver
import java.io.ByteArrayOutputStream

/**
 * Sends MMS messages via mmslib Transaction for unified, carrier-compatible handling.
 *
 * Phase 2 unified approach:
 *  All MMS sends (text-only group, text-only 1:1, media 1:1, media group) go through
 *  mmslib Transaction which handles:
 *    - PDU composition
 *    - Provider insertion
 *    - System MMS sending via SmsManager.sendMultimediaMessage()
 *    - Sent/delivery callbacks
 *
 * Flow for all sends:
 *  1. Filter own numbers from recipient list
 *  2. Build Message object (text + optional image)
 *  3. Create Transaction with Settings (useSystemSending=true, group=true/false, etc.)
 *  4. Attach explicit broadcast intent for MmsSentReceiver
 *  5. Send via transaction.sendNewMessage()
 *
 * Must be called from a background thread — performs file I/O and network operations.
 */
object MmsSender {

    private const val TAG = "DPAD_MSG"

    const val ACTION_MMS_SENT = "com.dpad.messaging.MMS_SENT"
    const val EXTRA_THREAD_ID = "extra_thread_id"

    private const val MAX_IMAGE_WIDTH = 800
    private const val JPEG_QUALITY    = 85

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends an MMS with the given text body and optional image attachment via mmslib.
     *
     * Both text-only and media messages are unified through the mmslib Transaction path,
     * which ensures carrier compatibility and proper provider bookkeeping.
     *
     * @param context        Application context.
     * @param recipients     Recipient phone number(s). Multiple numbers = group MMS.
     * @param body           Text body (may be blank when image-only).
     * @param attachmentUri  Content URI of media/file to attach, or null for text-only MMS.
     * @param threadId       Telephony thread ID (used for logging; mmslib may override).
     * @param subscriptionId SIM subscription ID (-1 = system default).
     */
    fun send(
        context: Context,
        recipients: List<String>,
        body: String,
        attachmentUri: Uri?,
        attachmentUris: List<Uri> = emptyList(),
        threadId: Long,
        subscriptionId: Int = -1
    ) {
        if (recipients.isEmpty()) return

        // Strip own phone number(s) from the recipient list
        val ownNumbers = context.getOwnPhoneNumbers()
        val filteredRecipients = recipients.filter { num ->
            val digits = num.filter { it.isDigit() }
            digits !in ownNumbers && digits.takeLast(10) !in ownNumbers
        }.ifEmpty { recipients }
        val mergedAttachments = LinkedHashSet<Uri>().apply {
            attachmentUris.forEach { add(it) }
            if (attachmentUri != null) add(attachmentUri)
        }.toList()

        Log.d(
            TAG,
            "MmsSender.send() recipients=$recipients filtered=$filteredRecipients body='${body.take(20)}' attachments=${mergedAttachments.size} threadId=$threadId"
        )

        if (mergedAttachments.isEmpty()) {
            sendSingleMms(
                context = context,
                recipients = filteredRecipients,
                body = body,
                attachmentUri = null,
                subscriptionId = subscriptionId
            )
            return
        }

        mergedAttachments.forEachIndexed { index, uri ->
            val isLast = index == mergedAttachments.lastIndex
            sendSingleMms(
                context = context,
                recipients = filteredRecipients,
                body = if (isLast) body else "",
                attachmentUri = uri,
                subscriptionId = subscriptionId
            )
        }
    }

    private fun sendSingleMms(
        context: Context,
        recipients: List<String>,
        body: String,
        attachmentUri: Uri?,
        subscriptionId: Int
    ) {
        val message = KlinkerMessage(body, recipients.toTypedArray())
        if (recipients.size > 1) {
            message.setSubject("Group message")
        }

        if (attachmentUri != null) {
            val mimeType = context.contentResolver.getType(attachmentUri)?.lowercase() ?: "application/octet-stream"
            val bytes = if (mimeType.startsWith("image/")) {
                compressImage(context, attachmentUri)
            } else {
                readAttachment(context, attachmentUri)
            }

            if (bytes != null) {
                try {
                    val normalizedMime = when {
                        mimeType.startsWith("image/") -> "image/jpeg"
                        mimeType == "text/plain" -> "application/txt"
                        else -> mimeType
                    }
                    val attachmentName = resolveAttachmentName(context, attachmentUri, normalizedMime)
                    message.addMedia(bytes, normalizedMime, attachmentName)
                    Log.d(TAG, "MmsSender: added attachment mime=$normalizedMime name=$attachmentName bytes=${bytes.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "MmsSender: failed to add attachment", e)
                }
            } else {
                Log.w(TAG, "MmsSender: unable to read attachment data from uri=$attachmentUri")
            }
        }

        // Create Settings with desired behavior
        val settings = KlinkerSettings().apply {
            setUseSystemSending(true)  // Use system MMS APIs (Lollipop+)
            setGroup(recipients.size > 1)  // Group mode if multiple recipients
            setDeliveryReports(Prefs.get().deliveryReports)
            if (subscriptionId >= 0) {
                setSubscriptionId(subscriptionId)
            }
        }

        // Create Transaction and attach callback
        val transaction = KlinkerTransaction(context, settings)
        val resolvedThreadId = resolveThreadId(context, recipients)
        val hasImage = attachmentUri?.let {
            (context.contentResolver.getType(it) ?: "").startsWith("image/", ignoreCase = true)
        } == true
        val sentIntent = Intent(ACTION_MMS_SENT, null, context, MmsSentReceiver::class.java).apply {
            putExtra(EXTRA_THREAD_ID, resolvedThreadId)
            putExtra("extra_has_image", hasImage)
            putExtra("extra_library_sender", true)
        }
        transaction.setExplicitBroadcastForSentMms(sentIntent)

        // Send via mmslib
        try {
            Log.d(TAG, "MmsSender: sending via mmslib recipients=$recipients group=${recipients.size > 1} subId=$subscriptionId")
            transaction.sendNewMessage(message)
            Log.d(TAG, "MmsSender: sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "MmsSender: send failed", e)
        }
    }

    private fun resolveThreadId(context: Context, recipients: List<String>): Long {
        return try {
            if (recipients.size == 1) {
                Telephony.Threads.getOrCreateThreadId(context, recipients.first())
            } else {
                Telephony.Threads.getOrCreateThreadId(context, recipients.toSet())
            }
        } catch (e: Exception) {
            Log.w(TAG, "MmsSender: resolveThreadId failed for $recipients", e)
            -1L
        }
    }

    private fun readAttachment(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MmsSender: readAttachment failed for $uri", e)
            null
        }
    }

    private fun resolveAttachmentName(context: Context, uri: Uri, mimeType: String): String {
        val displayName = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty().trim()

        if (displayName.isNotBlank()) return displayName

        val lastSegment = uri.lastPathSegment.orEmpty().substringAfterLast('/').trim()
        if (lastSegment.isNotBlank()) return lastSegment

        return when (mimeType) {
            "image/jpeg" -> "image.jpg"
            "application/txt" -> "attachment.txt"
            else -> "attachment.bin"
        }
    }

    // ── Image compression ─────────────────────────────────────────────────────

    private fun compressImage(context: Context, uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original    = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return null

            val scaled = if (original.width > MAX_IMAGE_WIDTH) {
                val ratio  = MAX_IMAGE_WIDTH.toFloat() / original.width.toFloat()
                val newH   = (original.height * ratio).toInt()
                val result = Bitmap.createScaledBitmap(original, MAX_IMAGE_WIDTH, newH, true)
                original.recycle()
                result
            } else {
                original
            }

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            scaled.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            // If bitmap decode fails (e.g., provider quirk), fall back to raw bytes.
            Log.w(TAG, "MmsSender: compressImage failed, falling back to raw bytes", e)
            readAttachment(context, uri)
        }
    }
}
