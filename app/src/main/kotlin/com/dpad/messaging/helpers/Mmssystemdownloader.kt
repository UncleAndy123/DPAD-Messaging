package com.dpad.messaging.helpers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dpad.messaging.receivers.MmsDownloadResultReceiver
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import com.dpad.messaging.BuildConfig
import java.io.File

/**
 * Downloads an incoming MMS via the privileged system path:
 * [android.telephony.SmsManager.downloadMultimediaMessage].
 *
 * Why this exists (and why the manual MmsDownloader can't work on this hardware):
 *   The Kyocera E4810/E4610 ROM silently ignores app-level
 *   ConnectivityManager.requestNetwork(NET_CAPABILITY_MMS), so MmsDownloader never
 *   acquires the real MMS network and falls back to the internet APN. The carrier's
 *   MMS proxy (e.g. 172.26.39.1:80) is only routable on the MMS APN, so the manual
 *   HTTP GET times out every time — on Wi-Fi AND on LTE.
 *
 *   downloadMultimediaMessage() runs inside the system MMS service, which brings up
 *   the MMS APN itself — the same privileged path that makes our SEND work
 *   (useSystemSending=true). Sending already succeeds on this device, so the system
 *   clearly can drive the MMS APN when asked through SmsManager.
 *
 * Unlike auto-download, downloadMultimediaMessage does NOT write into content://mms.
 * It writes the raw M-Retrieve-Conf PDU into a content URI we supply. We then read
 * those bytes back in [MmsDownloadResultReceiver] and hand them to the EXISTING
 * MmsPduParser + store path via [MmsDownloader.processPdu].
 *
 * Must be called from a background coroutine/thread.
 */
object MmsSystemDownloader {

    private const val TAG = "DPAD_MSG"

    // Where the system writes the downloaded PDU. Must be exposed through a
    // grantUriPermissions=true FileProvider so the MMS service (a different uid)
    // can write to it after we grant permission below.
    private const val CACHE_SUBDIR = "mms_download"
    private const val FILEPROVIDER_AUTHORITY = "com.dpadsms.fileprovider"

    // Package that hosts the system MMS service on most AOSP/Kyocera builds.
    // If a device routes MMS through a different package this must be adjusted;
    // grant is best-effort and failure is logged rather than fatal.
    private const val MMS_SERVICE_PACKAGE = "com.android.phone"

    /**
     * Kicks off a system MMS download. Returns true if the request was dispatched
     * to the system (NOT that it succeeded — success arrives via the PendingIntent).
     * Returns false if we couldn't even hand it off, so the caller can fall back
     * to the manual MmsDownloader.
     */
    fun download(
        context: Context,
        contentLocation: String,
        subId: Int,
        msgId: Long
    ): Boolean {
        return try {
            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            val pduFile = File(dir, "mms_$msgId.pdu")
            // Ensure a clean, empty target the system can write into.
            if (pduFile.exists()) pduFile.delete()
            pduFile.createNewFile()

            val contentUri = FileProvider.getUriForFile(context, FILEPROVIDER_AUTHORITY, pduFile)

            // The system MMS service runs in a different uid and must be granted
            // write access to our FileProvider URI.
            try {
                context.grantUriPermission(
                    MMS_SERVICE_PACKAGE,
                    contentUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "MmsSystemDownloader: grantUriPermission failed", e)
            }

            val downloadedIntent = Intent(context, MmsDownloadResultReceiver::class.java).apply {
                action = MmsDownloadResultReceiver.ACTION
                putExtra(MmsDownloadResultReceiver.EXTRA_MMS_ID, msgId)
                putExtra(MmsDownloadResultReceiver.EXTRA_PDU_URI, contentUri.toString())
                putExtra(MmsDownloadResultReceiver.EXTRA_CONTENT_LOCATION, contentLocation)
            }

            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            val sentIntent = PendingIntent.getBroadcast(
                context,
                msgId.toInt(),
                downloadedIntent,
                flags
            )

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subId >= 0) {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "MmsSystemDownloader.download() url=$contentLocation msgId=$msgId subId=$subId uri=$contentUri")
            }

            smsManager.downloadMultimediaMessage(
                context,
                contentLocation,
                contentUri,
                null,          // configOverrides — use carrier defaults
                sentIntent
            )
            true
        } catch (e: Exception) {
            // Any failure to dispatch → let the caller fall back to the manual path.
            if (BuildConfig.DEBUG) Log.e(TAG, "MmsSystemDownloader.download() dispatch failed for msgId=$msgId", e)
            false
        }
    }
}