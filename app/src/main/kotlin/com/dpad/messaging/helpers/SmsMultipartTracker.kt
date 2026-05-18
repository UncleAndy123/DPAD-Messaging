package com.dpad.messaging.helpers

import android.content.Context

object SmsMultipartTracker {
    private const val PREFS_NAME = "sms_multipart_tracker"

    data class AggregateResult(
        val shouldFinalize: Boolean,
        val isSuccess: Boolean
    )

    @Synchronized
    fun recordSent(
        context: Context,
        messageId: Long,
        totalParts: Int,
        partSuccess: Boolean
    ): AggregateResult {
        return record(context, "sent_", messageId, totalParts, partSuccess)
    }

    @Synchronized
    fun recordDelivered(
        context: Context,
        messageId: Long,
        totalParts: Int,
        partSuccess: Boolean
    ): AggregateResult {
        return record(context, "delivered_", messageId, totalParts, partSuccess)
    }

    private fun record(
        context: Context,
        prefix: String,
        messageId: Long,
        totalParts: Int,
        partSuccess: Boolean
    ): AggregateResult {
        if (messageId <= 0L) return AggregateResult(shouldFinalize = true, isSuccess = partSuccess)
        val safeTotal = totalParts.coerceAtLeast(1)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "$prefix$messageId"
        val doneKey = "${key}_done"
        if (prefs.getBoolean(doneKey, false)) {
            return AggregateResult(shouldFinalize = false, isSuccess = false)
        }
        val raw = prefs.getString(key, null)

        var total = safeTotal
        var reported = 0
        var failures = 0

        if (!raw.isNullOrBlank()) {
            val parts = raw.split('|')
            total = parts.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(1) ?: safeTotal
            reported = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            failures = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            if (safeTotal > total) total = safeTotal
        }

        reported += 1
        if (!partSuccess) failures += 1

        val shouldFinalize = failures > 0 || reported >= total
        return if (shouldFinalize) {
            prefs.edit().remove(key).putBoolean(doneKey, true).apply()
            AggregateResult(shouldFinalize = true, isSuccess = failures == 0)
        } else {
            prefs.edit().putString(key, "$total|$reported|$failures").apply()
            AggregateResult(shouldFinalize = false, isSuccess = false)
        }
    }
}
