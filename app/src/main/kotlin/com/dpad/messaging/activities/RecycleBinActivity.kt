package com.dpad.messaging.activities

import android.os.Bundle
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.Telephony
import android.view.KeyEvent
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.PopupMenu
import com.dpad.messaging.App
import com.dpad.messaging.R
import com.dpad.messaging.adapters.RecycleBinAdapter
import com.dpad.messaging.adapters.RecycledItem
import com.dpad.messaging.databinding.ActivityRecycleBinBinding
import com.dpad.messaging.helpers.ThemeManager
import com.dpad.messaging.helpers.ConversationCache
import com.dpad.messaging.helpers.MessageCache
import com.dpad.messaging.helpers.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecycleBinActivity : BaseActivity() {

    private lateinit var binding: ActivityRecycleBinBinding
    private lateinit var adapter: RecycleBinAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyAccentColor(this)
        binding = ActivityRecycleBinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = RecycleBinAdapter(
            onItemClick     = { showItemMenu(binding.root, it) },
            onItemLongClick = { showItemMenu(binding.root, it) },
            onItemMenuClick = { view, item -> showItemMenu(view, item) }
        )
        binding.rvMessages.apply {
            this.adapter = this@RecycleBinActivity.adapter
            layoutManager = LinearLayoutManager(this@RecycleBinActivity)
            onTopEdgeReached = { binding.btnBack.requestFocus() }
        }

        loadRecycleBin()
    }

    override fun onResume() {
        super.onResume()
        applyAccent()
    }

    private fun applyAccent() {
        binding.btnBack.imageTintList = ThemeManager.focusAwareTint(this)
    }

    // ─── Data ─────────────────────────────────────────────────────────────────

    private fun loadRecycleBin() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                App.get().database.messagesDao().getRecycleBinMessages()
                    .sortedByDescending { it.deletedTs }
                    .map { rbMsg ->
                        val providerId = rbMsg.providerId.takeIf { it > 0L }
                            ?: if (rbMsg.isMms) -rbMsg.id else rbMsg.id
                        var address = rbMsg.address
                        var body = rbMsg.body
                        var date = rbMsg.date
                        var messageType = rbMsg.messageType
                        var threadId = rbMsg.threadId
                        if (!rbMsg.isConversation && !rbMsg.isMms && providerId > 0L &&
                            (address.isBlank() || body.isBlank() || date == 0L)
                        ) {
                            runCatching {
                                contentResolver.query(
                                    ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, providerId),
                                    arrayOf(
                                        Telephony.Sms.ADDRESS,
                                        Telephony.Sms.BODY,
                                        Telephony.Sms.DATE,
                                        Telephony.Sms.TYPE,
                                        Telephony.Sms.THREAD_ID
                                    ),
                                    null,
                                    null,
                                    null
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        if (address.isBlank()) address = cursor.getString(0) ?: ""
                                        if (body.isBlank()) body = cursor.getString(1) ?: ""
                                        if (date == 0L) date = cursor.getLong(2)
                                        messageType = cursor.getInt(3)
                                        if (threadId <= 0L) threadId = cursor.getLong(4)
                                    }
                                }
                            }
                        }
                        RecycledItem(
                            id          = rbMsg.id,
                            senderName  = rbMsg.senderName.ifBlank { address },
                            phoneNumber = address,
                            body        = body,
                            date        = date,
                            providerId  = providerId,
                            threadId    = threadId,
                            isMms       = rbMsg.isMms,
                            isConversation = rbMsg.isConversation,
                            messageType = messageType,
                            wasRead     = rbMsg.wasRead,
                            participantsJson = rbMsg.participantsJson
                        )
                    }
            }
            adapter.submitList(items)
            binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ─── Context menu ─────────────────────────────────────────────────────────

    private fun showItemMenu(anchor: View, item: RecycledItem) {
        val popup = PopupMenu(ThemeManager.popupMenuContext(this), anchor)
        popup.menu.add(0, 0, 0, getString(R.string.restore))
        popup.menu.add(0, 1, 1, getString(R.string.delete))
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> restoreItem(item)
                1 -> permanentlyDeleteItem(item)
            }
            true
        }
        popup.show()
    }

    /** Restore the retained provider row or reinsert a removed SMS snapshot. */
    private fun restoreItem(item: RecycledItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            val restored = if (item.isConversation) restoreConversation(item) else restoreMessage(item)
            if (restored) {
                App.get().database.messagesDao().removeFromRecycleBin(item.id)
                ConversationCache.invalidate()
                if (item.threadId > 0L) MessageCache.invalidate(item.threadId)
            }
            withContext(Dispatchers.Main) {
                if (!restored) {
                    android.widget.Toast.makeText(this@RecycleBinActivity, R.string.restore_failed, android.widget.Toast.LENGTH_LONG).show()
                }
                loadRecycleBin()
            }
        }
    }

    private fun restoreMessage(item: RecycledItem): Boolean {
        val providerId = item.providerId.takeIf { it > 0L }
            ?: if (item.isMms) -item.id else item.id
        val uri = if (item.isMms) Uri.parse("content://mms/$providerId")
            else ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, providerId)
        val wasAlreadyInProvider = try {
            val cursor = contentResolver.query(uri, arrayOf("_id"), null, null, null) ?: return false
            cursor.use { it.moveToFirst() }
        } catch (_: Exception) {
            // Don't risk creating a duplicate if the provider couldn't confirm
            // whether the original row is still present.
            return false
        }

        if (wasAlreadyInProvider) {
            val readValues = ContentValues().apply {
                put("read", if (item.wasRead) 1 else 0)
                put("seen", if (item.wasRead) 1 else 0)
            }
            val restoredReadState = runCatching {
                contentResolver.update(uri, readValues, null, null) >= 0
            }.getOrDefault(false)
            return restoredReadState
        }

        // Recycled MMS rows are retained in the provider so their attachment
        // parts remain intact. If an older version deleted one, keep the recycle
        // entry rather than pretending a text-only SMS is a full MMS restore.
        if (item.isMms) return false

        val originalThreadExists = item.threadId > 0L && runCatching {
            contentResolver.query(
                Uri.parse("content://mms-sms/conversations/${item.threadId}"),
                arrayOf("_id"),
                null,
                null,
                null
            )?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
        val participants = runCatching {
            val json = org.json.JSONArray(item.participantsJson)
            buildList {
                for (index in 0 until json.length()) {
                    json.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
        val threadId = when {
            originalThreadExists -> item.threadId
            participants.size > 1 -> runCatching {
                Telephony.Threads.getOrCreateThreadId(this, participants.toSet())
            }.getOrDefault(-1L)
            else -> runCatching {
                Telephony.Threads.getOrCreateThreadId(this, item.phoneNumber)
            }.getOrDefault(-1L)
        }
        if (threadId <= 0L) return false
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, item.phoneNumber)
            put(Telephony.Sms.BODY, item.body)
            put(Telephony.Sms.DATE, item.date)
            put(Telephony.Sms.DATE_SENT, item.date)
            put(Telephony.Sms.READ, if (item.wasRead) 1 else 0)
            put(Telephony.Sms.SEEN, if (item.wasRead) 1 else 0)
            put(Telephony.Sms.TYPE, item.messageType)
            put(Telephony.Sms.THREAD_ID, threadId)
        }
        return runCatching {
            contentResolver.insert(Telephony.Sms.CONTENT_URI, values) != null
        }.getOrDefault(false)
    }

    private fun restoreConversation(item: RecycledItem): Boolean {
        if (item.threadId <= 0L) return false
        Prefs.get().setThreadArchived(item.threadId, false)
        return true
    }

    /** Permanently delete the retained provider data, then discard its recycle entry. */
    private fun permanentlyDeleteItem(item: RecycledItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            val deleted = runCatching {
                if (item.isConversation) {
                    val uri = Uri.parse("content://mms-sms/conversations/${item.threadId}")
                    val hadProviderRows = sequenceOf(
                        Uri.parse("content://sms") to Telephony.Sms.THREAD_ID,
                        Uri.parse("content://mms") to "thread_id"
                    ).any { (rowsUri, threadColumn) ->
                        contentResolver.query(
                            rowsUri,
                            arrayOf("_id"),
                            "$threadColumn = ?",
                            arrayOf(item.threadId.toString()),
                            null
                        )?.use { it.moveToFirst() } == true
                    }
                    val deletedCount = contentResolver.delete(uri, null, null)
                    if (hadProviderRows && deletedCount == 0) {
                        error("Telephony did not delete the recycled conversation")
                    }
                    Prefs.get().setThreadArchived(item.threadId, false)
                    Prefs.get().setThreadPinned(item.threadId, false)
                    Prefs.get().setThreadMuted(item.threadId, false)
                    App.get().database.messagesDao()
                        .removeRecycleBinMessagesForThread(item.threadId)
                } else {
                    val providerId = item.providerId.takeIf { it > 0L }
                        ?: if (item.isMms) -item.id else item.id
                    val uri = if (item.isMms) Uri.parse("content://mms/$providerId")
                        else ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, providerId)
                    val existed = contentResolver.query(uri, arrayOf("_id"), null, null, null)
                        ?.use { it.moveToFirst() } ?: false
                    val removed = contentResolver.delete(uri, null, null)
                    if (existed && removed <= 0) {
                        error("Telephony did not delete the recycled message")
                    }
                }
                if (!item.isConversation) {
                    App.get().database.messagesDao().removeFromRecycleBin(item.id)
                }
                ConversationCache.invalidate()
                if (item.threadId > 0L) MessageCache.invalidate(item.threadId)
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (!deleted) {
                    android.widget.Toast.makeText(this@RecycleBinActivity, R.string.recycle_bin_delete_failed, android.widget.Toast.LENGTH_LONG).show()
                }
                loadRecycleBin()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_STAR) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
