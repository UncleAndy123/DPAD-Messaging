package com.dpad.messaging.debug

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.dpad.messaging.BuildConfig

/**
 * Debug-only activity that deletes all SMS/MMS conversations from
 * the system provider. Requires an explicit action and secret token.
 */
class DebugDeleterActivity : Activity() {
    companion object {
        private const val TAG = "DPAD_MSG"
        const val ACTION_CLEAR_ALL = "com.dpad.messaging.debug.CLEAR_ALL_MESSAGES"
        const val EXTRA_TOKEN = "extra_debug_token"
        const val SECRET_TOKEN = "dpad-clear-2026"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish(); return
        }
        val action = intent?.action
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        if (action != ACTION_CLEAR_ALL || token != SECRET_TOKEN) {
            Log.w(TAG, "DebugDeleter: refused invalid intent")
            finish(); return
        }

        Thread {
            try {
                contentResolver.delete(Uri.parse("content://sms"), null, null)
                contentResolver.delete(Uri.parse("content://mms"), null, null)
                contentResolver.delete(Uri.parse("content://mms-sms/conversations"), null, null)
                Log.i(TAG, "DebugDeleter: deleted sms/mms/conversations")
            } catch (e: Exception) {
                Log.e(TAG, "DebugDeleter: delete failed", e)
            } finally {
                runOnUiThread { finish() }
            }
        }.start()
    }
}
