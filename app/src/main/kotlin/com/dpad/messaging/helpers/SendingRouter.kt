package com.dpad.messaging.helpers

/**
 * Encodes the decision of whether to send a message as SMS or MMS, and if a group,
 * whether to use a single group MMS or fan-out individual SMS messages.
 *
 * This ensures consistent routing logic across all send screens (ThreadActivity,
 * NewConversationActivity, etc.).
 */
enum class SendingMode {
    /**
     * Send a single SMS to one recipient.
     */
    SMS_SINGLE,

    /**
     * Send individual SMS to each recipient in a group (fan-out).
     * Each SMS may appear in separate threads on the recipient's device
     * unless the device/carrier groups them by number.
     */
    SMS_FANOUT_GROUP,

    /**
     * Send a single MMS message to one recipient.
     */
    MMS_SINGLE,

    /**
     * Send a group MMS to multiple recipients in a single message.
     * All recipients appear in the same thread on each device.
     */
    MMS_GROUP
}

/**
 * Centralized routing logic for message sending.
 *
 * Policy:
 *  • Any attachment (image) → always MMS
 *  • Group + attachment → MMS_GROUP
 *  • 1:1 + attachment → MMS_SINGLE
 *  • Group + text + sendGroupMessageMms preference ON → MMS_GROUP
 *  • Group + text + sendGroupMessageMms preference OFF → SMS_FANOUT_GROUP
 *  • 1:1 + text → SMS_SINGLE
 */
object SendingRouter {

    /**
     * Decides the sending mode based on message content and user preferences.
     *
     * @param hasAttachment        Whether the message has an image/media attachment.
     * @param recipientCount       Number of recipients (size of recipients list).
     * @param sendGroupMessageMms  User preference: true = send group text as MMS,
     *                             false = send group text as individual SMS.
     * @return The routing decision (SendingMode enum).
     */
    fun decideSendingMode(
        hasAttachment: Boolean,
        recipientCount: Int,
        sendGroupMessageMms: Boolean
    ): SendingMode {
        val isGroup = recipientCount > 1
        return when {
            // Any attachment → MMS
            hasAttachment && isGroup -> SendingMode.MMS_GROUP
            hasAttachment && !isGroup -> SendingMode.MMS_SINGLE
            // Group text: respect user preference
            !hasAttachment && isGroup && sendGroupMessageMms -> SendingMode.MMS_GROUP
            !hasAttachment && isGroup -> SendingMode.SMS_FANOUT_GROUP
            // 1:1 text → SMS
            else -> SendingMode.SMS_SINGLE
        }
    }
}
