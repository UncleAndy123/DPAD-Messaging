package com.dpad.messaging.helpers

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.dpad.messaging.BuildConfig
import java.io.File

/**
 * Copies an incoming attachment URI (from the gallery picker or camera capture) into
 * app-private cache storage and returns a FileProvider URI that THIS app owns.
 *
 * Why this exists:
 *   On the Kyocera E4810/E4610 (and other OEM ROMs) the gallery picker returns a URI into
 *   the vendor's own, non-exported provider (jp.kyocera.datafolder.provider). The transient
 *   FLAG_GRANT_READ_URI_PERMISSION granted to us is tied to the activity/moment of the pick,
 *   and is GONE by the time MmsSender reads the URI later on a background coroutine — the read
 *   then fails with SecurityException. The camera path fails the same way with ENOENT because
 *   the Kyocera camera ignores EXTRA_OUTPUT and writes to /DCIM/100KYCRA instead of our target.
 *
 *   The fix is to stop holding a foreign URI across time: read the bytes IMMEDIATELY, in the
 *   pick/capture callback while the grant is still live (UI thread), and copy them into a file
 *   we own. MmsSender then reads OUR file — no vendor grant, no ENOENT, no SecurityException.
 *
 * Usage (in the pick/capture result callback, before storing the pending attachment):
 *   val owned = AttachmentStore.copyToOwnedCache(this, pickedUri)
 *   if (owned != null) {
 *       // store `owned` as the pending attachment instead of pickedUri
 *   } else {
 *       // show "couldn't read that image" — copy failed while grant was live
 *   }
 *
 * The FileProvider authority below assumes the existing "com.dpadsms.fileprovider" authority
 * seen in the logcat (content://com.dpadsms.fileprovider/...). Adjust FILEPROVIDER_AUTHORITY
 * if your manifest declares a different one.
 */
object AttachmentStore {

    private const val TAG = "DPAD_MSG"

    // Matches the authority already in use per logcat: content://com.dpadsms.fileprovider/...
    private const val FILEPROVIDER_AUTHORITY = "com.dpadsms.fileprovider"

    // Subdirectory under cacheDir that must be mapped in file_paths.xml (see note below).
    private const val CACHE_SUBDIR = "mms_attachments"

    /**
     * Reads [source] via the ContentResolver (using whatever grant is currently live) and
     * writes the bytes into app-private cache, returning a FileProvider URI we own.
     *
     * MUST be called while the read grant on [source] is still valid — i.e. synchronously in
     * the pick/capture result callback, NOT deferred to a background send.
     *
     * @return an owned content:// URI on success, or null if the source could not be read.
     */
    fun copyToOwnedCache(context: Context, source: Uri): Uri? {
        val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }

        // Preserve a sensible extension so downstream MIME/type resolution behaves.
        val displayName = resolveDisplayName(context, source)
        val ext = displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            ?: source.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
            ?: "jpg"
        val dest = File(dir, "attach_${System.currentTimeMillis()}.$ext")

        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output ->
                    val copied = input.copyTo(output)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "AttachmentStore: copied $copied bytes from $source -> ${dest.name}")
                    }
                }
            } ?: run {
                if (BuildConfig.DEBUG) Log.w(TAG, "AttachmentStore: openInputStream returned null for $source")
                return null
            }

            if (dest.length() == 0L) {
                if (BuildConfig.DEBUG) Log.w(TAG, "AttachmentStore: copied file is empty for $source")
                dest.delete()
                return null
            }

            FileProvider.getUriForFile(context, FILEPROVIDER_AUTHORITY, dest)
        } catch (e: Exception) {
            // SecurityException (dead grant), FileNotFoundException (ENOENT), etc. — all land here.
            if (BuildConfig.DEBUG) Log.w(TAG, "AttachmentStore: copyToOwnedCache failed for $source", e)
            dest.delete()
            null
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        val fromResolver = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.trim().orEmpty()

        if (fromResolver.isNotBlank()) return fromResolver
        return uri.lastPathSegment.orEmpty().substringAfterLast('/').trim()
    }

    /**
     * Optional: clear copied attachments after a send completes, so cache doesn't accumulate.
     * Call from your MMS-sent receiver or after the send coroutine finishes.
     */
    fun clearCache(context: Context) {
        runCatching {
            File(context.cacheDir, CACHE_SUBDIR).listFiles()?.forEach { it.delete() }
        }
    }
}