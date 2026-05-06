package com.dpad.messaging.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dpad.messaging.R
import com.dpad.messaging.databinding.ActivityNewConversationBinding
import com.dpad.messaging.App
import com.dpad.messaging.helpers.MmsSender
import com.dpad.messaging.helpers.Prefs
import com.dpad.messaging.helpers.SmsSender
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "New Conversation" screen.
 *
 * Phase 1: enter a phone number directly → opens ThreadActivity.
 * Phase 2: add contact-lookup suggestions as the user types.
 *
 * D-Pad flow: Back → et_recipient → et_message → btn_send
 */
class NewConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewConversationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupInputs()
        handleIncomingIntent(intent)
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }

        // D-Pad DOWN from back button → recipient field
        binding.btnBack.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                binding.etRecipient.requestFocus()
                true
            } else false
        }
    }

    private fun setupInputs() {
        // Enable send button only when both fields have content
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateSendButton() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        binding.etRecipient.addTextChangedListener(watcher)
        binding.etMessage.addTextChangedListener(watcher)

        binding.btnSend.setOnClickListener { sendAndOpen() }

        // ENTER on recipient field → jump to message body
        binding.etRecipient.setOnEditorActionListener { _, _, event ->
            if (event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN) {
                binding.etMessage.requestFocus()
                true
            } else false
        }

        // SEND key on message field → send
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendAndOpen(); true
            } else false
        }

        binding.etRecipient.requestFocus()
    }

    private fun updateSendButton() {
        val ready = parseRecipients(binding.etRecipient.text?.toString().orEmpty()).isNotEmpty() &&
                    binding.etMessage.text?.isNotBlank() == true
        binding.btnSend.isEnabled = ready
        binding.btnSend.setColorFilter(
            if (ready) getColor(R.color.sendButtonEnabled)
            else getColor(R.color.sendButtonDisabled)
        )
    }

    private fun sendAndOpen() {
        val recipients = parseRecipients(binding.etRecipient.text?.toString().orEmpty())
        val body = binding.etMessage.text?.toString()?.trim() ?: return
        if (recipients.isEmpty() || body.isBlank()) return

        // Resolve thread ID first, then send and open the thread.
        lifecycleScope.launch {
            val threadId = withContext(Dispatchers.IO) {
                resolveOrCreateThreadId(recipients)
            }
            if (threadId != null) {
                // Send in background; ThreadActivity will reload and show the outbox row.
                withContext(Dispatchers.IO) {
                    val isGroup = recipients.size > 1
                    if (isGroup && Prefs.get().sendGroupMessageMms) {
                        MmsSender.send(
                            context = this@NewConversationActivity,
                            recipients = recipients,
                            body = body,
                            imageUri = null,
                            threadId = threadId
                        )
                    } else if (isGroup) {
                        recipients.forEach { recipient ->
                            SmsSender.send(
                                context = this@NewConversationActivity,
                                phoneNumber = recipient,
                                body = body,
                                threadId = threadId
                            )
                        }
                    } else {
                        SmsSender.send(
                            context = this@NewConversationActivity,
                            phoneNumber = recipients.first(),
                            body = body,
                            threadId = threadId
                        )
                    }
                }
                val intent = Intent(this@NewConversationActivity, ThreadActivity::class.java).apply {
                    putExtra(ThreadActivity.EXTRA_THREAD_ID, threadId)
                    val title = recipients.joinToString(", ") { App.get().contactHelper.getDisplayName(it) }
                    putExtra(ThreadActivity.EXTRA_THREAD_TITLE, title)
                    putExtra(ThreadActivity.EXTRA_PHONE_NUMBER, recipients.first())
                    if (recipients.size > 1) {
                        putExtra(ThreadActivity.EXTRA_PARTICIPANTS, recipients.joinToString(","))
                    }
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(
                    this@NewConversationActivity,
                    R.string.error_sending_message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Returns the existing or created thread ID for one-to-one or group recipients.
     *
     * Phase 2: call SmsManager.sendTextMessage() here before returning the thread ID.
     */
    private fun resolveOrCreateThreadId(recipients: List<String>): Long? {
        return try {
            if (recipients.size == 1) {
                Telephony.Threads.getOrCreateThreadId(this, recipients.first())
            } else {
                Telephony.Threads.getOrCreateThreadId(this, recipients.toSet())
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseRecipients(raw: String): List<String> {
        return raw.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    // Handles text/plain and image-type ACTION_SEND shares, or forward-message pre-fills.
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    binding.etMessage.setText(text)
                    binding.etMessage.setSelection(text.length)
                }
            }
            else -> {
                // Pre-fill body from forward/compose shortcuts
                val prefill = intent.getStringExtra(EXTRA_PREFILL_BODY)
                if (!prefill.isNullOrBlank()) {
                    binding.etMessage.setText(prefill)
                    binding.etMessage.setSelection(prefill.length)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_PREFILL_BODY = "extra_prefill_body"
    }
}
