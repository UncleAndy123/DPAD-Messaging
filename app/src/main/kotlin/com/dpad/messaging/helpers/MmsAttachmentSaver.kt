package com.dpad.messaging.helpers

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.dpad.messaging.models.MmsAttachment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MmsAttachmentSaver {
    fun save(context: Context, attachment: MmsAttachment): Boolean {
        val mimeType = resolveMimeType(context, attachment)
        val displayName = buildDisplayName(attachment.fileName, mimeType)
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { input ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(context, input, displayName, mimeType)
                } else {
                    saveBeforeQ(context, input, displayName, mimeType)
                }
            } ?: false
        }.getOrDefault(false)
    }

    private fun resolveMimeType(context: Context, attachment: MmsAttachment): String {
        if (attachment.mimeType.isNotBlank() && attachment.mimeType != "*/*") {
            return attachment.mimeType.lowercase()
        }
        return context.contentResolver.getType(Uri.parse(attachment.uri))
            ?.lowercase()
            ?: "application/octet-stream"
    }

    private fun buildDisplayName(originalName: String, mimeType: String): String {
        val cleaned = originalName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "DPAD_SMS_${timestamp()}" }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        return if (extension != null && !cleaned.contains('.')) "$cleaned.$extension" else cleaned
    }

    private fun saveWithMediaStore(
        context: Context,
        input: java.io.InputStream,
        displayName: String,
        mimeType: String
    ): Boolean {
        val collection = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val relativePath = when {
            mimeType.startsWith("image/") -> Environment.DIRECTORY_PICTURES + "/DPAD Messaging"
            mimeType.startsWith("audio/") -> Environment.DIRECTORY_MUSIC + "/DPAD Messaging"
            mimeType.startsWith("video/") -> Environment.DIRECTORY_MOVIES + "/DPAD Messaging"
            else -> Environment.DIRECTORY_DOWNLOADS + "/DPAD Messaging"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val outputUri = context.contentResolver.insert(collection, values) ?: return false
        return try {
            context.contentResolver.openOutputStream(outputUri)?.use { output -> input.copyTo(output) }
                ?: error("Unable to open MediaStore output")
            val published = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(outputUri, published, null, null) > 0
        } catch (error: Exception) {
            context.contentResolver.delete(outputUri, null, null)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun saveBeforeQ(
        context: Context,
        input: java.io.InputStream,
        displayName: String,
        mimeType: String
    ): Boolean {
        val directoryType = when {
            mimeType.startsWith("image/") -> Environment.DIRECTORY_PICTURES
            mimeType.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
            mimeType.startsWith("video/") -> Environment.DIRECTORY_MOVIES
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        val directory = File(
            Environment.getExternalStoragePublicDirectory(directoryType),
            "DPAD Messaging"
        )
        if (!directory.exists() && !directory.mkdirs()) return false
        val outputFile = createUniqueFile(directory, displayName) ?: return false
        return try {
            FileOutputStream(outputFile).use { output -> input.copyTo(output) }
            MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf(mimeType), null)
            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        }
    }

    private fun createUniqueFile(directory: File, displayName: String): File? {
        val requested = File(directory, displayName)
        val extension = requested.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val baseName = requested.name.removeSuffix(extension).ifBlank { "attachment" }
        for (suffix in 0..999) {
            val candidateName = if (suffix == 0) "$baseName$extension" else "$baseName ($suffix)$extension"
            val candidate = File(directory, candidateName)
            if (candidate.createNewFile()) return candidate
        }
        return null
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
