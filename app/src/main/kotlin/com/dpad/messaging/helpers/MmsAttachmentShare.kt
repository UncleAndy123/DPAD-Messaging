package com.dpad.messaging.helpers

import android.content.Context
import androidx.core.content.FileProvider
import com.dpad.messaging.models.MmsAttachment
import java.io.File
import java.util.UUID

object MmsAttachmentShare {
    fun createUri(context: Context, attachment: MmsAttachment): android.net.Uri? {
        return runCatching {
            val directory = File(context.cacheDir, "mms_share").apply { mkdirs() }
            val name = attachment.fileName
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "attachment" }
            // Keep previously issued FileProvider URIs valid while another share
            // is being prepared; recipient apps may still be reading them.
            val file = File(directory, "${UUID.randomUUID()}_$name")
            context.contentResolver.openInputStream(attachment.contentUri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
}
