package com.dpad.messaging.data.model

enum class MsgType { SMS_IN, SMS_OUT, MMS_IN, MMS_OUT }

enum class DeliveryState { UNKNOWN, SENDING, SENT, FAILED }

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: MsgType,
    val mmsPartUris: List<String> = emptyList(),   // image/video URIs for MMS (deprecated; use mmsParts)
    val mmsParts: List<MmsPart> = emptyList(),     // richer MMS part descriptors
    val state: DeliveryState = DeliveryState.UNKNOWN
)

// Simple descriptor for an MMS media part. Keep small and parcelable if needed later.
data class MmsPart(
    val id: Long,
    val uri: String,
    val contentType: String,
    val name: String? = null,
    val hasData: Boolean = true
)
