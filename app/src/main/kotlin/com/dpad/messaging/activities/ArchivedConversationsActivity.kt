package com.dpad.messaging.activities

import android.content.res.ColorStateList
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dpad.messaging.App
import com.dpad.messaging.adapters.ConversationsAdapter
import com.dpad.messaging.databinding.ActivityArchivedBinding
import com.dpad.messaging.extensions.getConversationsFromTelephony
import com.dpad.messaging.helpers.Prefs
import com.dpad.messaging.helpers.ThemeManager
import com.dpad.messaging.models.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchivedConversationsActivity : BaseActivity() {

    private lateinit var binding: ActivityArchivedBinding
    private lateinit var adapter: ConversationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyAccentColor(this)
        binding = ActivityArchivedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = ConversationsAdapter(
            onConversationClick = { openThread(it) },
            onConversationLongClick = { showUnarchiveMenu(binding.root, it) },
            onConversationMenuClick = { view, conversation -> showUnarchiveMenu(view, conversation) }
        )
        binding.rvConversations.apply {
            this.adapter = this@ArchivedConversationsActivity.adapter
            layoutManager = LinearLayoutManager(this@ArchivedConversationsActivity)
            onTopEdgeReached = { binding.btnBack.requestFocus() }
        }

        loadArchivedConversations()
    }

    override fun onResume() {
        super.onResume()
        applyAccent()
    }

    private fun applyAccent() {
        val tint = ColorStateList.valueOf(ThemeManager.accentColor(this))
        binding.btnBack.imageTintList = tint
        binding.btnBack.backgroundTintList = tint
    }

    private fun openThread(conversation: Conversation) {
        val intent = Intent(this, ThreadActivity::class.java).apply {
            putExtra(ThreadActivity.EXTRA_THREAD_ID, conversation.threadId)
            putExtra(ThreadActivity.EXTRA_THREAD_TITLE, conversation.title)
            putExtra(ThreadActivity.EXTRA_PHONE_NUMBER, conversation.phoneNumber)
            if (conversation.participants.isNotBlank()) {
                putExtra(ThreadActivity.EXTRA_PARTICIPANTS, conversation.participants)
            }
        }
        startActivity(intent)
    }

    private fun loadArchivedConversations() {
        lifecycleScope.launch {
            val archivedIds = Prefs.get().getArchivedThreadIds()
            if (archivedIds.isEmpty()) {
                adapter.submitList(emptyList())
                binding.tvEmpty.visibility = View.VISIBLE
                return@launch
            }
            val archived = withContext(Dispatchers.IO) {
                // Pass all archived IDs as "pinned" = none; pass archived IDs as archivedThreadIds=none
                // so excluded filter is empty, but set includeOnly to the archived IDs.
                getConversationsFromTelephony(
                    App.get().contactHelper,
                    pinnedThreadIds = Prefs.get().getPinnedThreadIds(),
                    archivedThreadIds = emptySet()   // don't exclude — we WANT archived ones
                ).filter { it.threadId in archivedIds }
            }
            adapter.submitList(archived)
            binding.tvEmpty.visibility = if (archived.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showUnarchiveMenu(anchor: View, conversation: Conversation) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, getString(com.dpad.messaging.R.string.unarchive))
        popup.setOnMenuItemClickListener {
            Prefs.get().setThreadArchived(conversation.threadId, false)
            loadArchivedConversations()
            true
        }
        popup.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_STAR) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}

