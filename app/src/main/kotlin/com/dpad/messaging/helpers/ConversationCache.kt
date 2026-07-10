package com.dpad.messaging.helpers

import com.dpad.messaging.models.Conversation

/**
 * In-memory cache for conversation list data.
 *
 * Keeps the latest list so the UI can render instantly while a background
 * Telephony refresh runs.
 */
object ConversationCache {

    @Volatile
    private var cached: List<Conversation>? = null

    @Synchronized
    fun get(): List<Conversation>? = cached

    @Synchronized
    fun put(conversations: List<Conversation>) {
        cached = conversations
    }

    @Synchronized
    fun clear() {
        cached = null
    }
}
