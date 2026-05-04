package com.dpad.messaging.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dpad.messaging.data.model.SmsThread
import com.dpad.messaging.util.dpadFocusableItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onOpenChat: (threadId: Long, address: String, name: String) -> Unit,
    onNewMessage: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ConversationsViewModel = viewModel()
) {
    val threads by viewModel.threads.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val drafts by viewModel.drafts.collectAsState()

    // Refresh drafts whenever the conversations screen is visible (e.g. after returning from chat)
    LaunchedEffect(Unit) { viewModel.refreshDrafts() }

    var showArchived by remember { mutableStateOf(false) }
    val fabFocus = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showArchived) "Archived" else "Messages",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    val archiveFocus = remember { FocusRequester() }
                    val settingsFocus = remember { FocusRequester() }
                    IconButton(
                        onClick = {
                            showArchived = !showArchived
                            viewModel.setShowArchived(showArchived)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .focusRequester(archiveFocus)
                            .dpadFocusableItem(
                                onClick = {
                                    showArchived = !showArchived
                                    viewModel.setShowArchived(showArchived)
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                borderWidth = 3.dp
                            )
                    ) {
                        Icon(
                            Icons.Default.Archive,
                            if (showArchived) "Show Inbox" else "Show Archived",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier
                            .size(40.dp)
                            .focusRequester(settingsFocus)
                            .dpadFocusableItem(
                                onClick = onSettings,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                borderWidth = 3.dp
                            )
                    ) {
                        Icon(Icons.Default.Settings, "Settings", modifier = Modifier.size(20.dp))
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        },
        // FAB is placed INSIDE the LazyColumn below as a final item so D-pad down reaches it.
        // Do NOT put it here as a floatingActionButton — it would be unreachable via D-pad.
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        when {
            isLoading && threads.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            threads.isEmpty() -> {
                // Show empty state + FAB so user can still start a conversation
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (showArchived) "No archived conversations." else "No messages yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    FloatingActionButton(
                        onClick = onNewMessage,
                        modifier = Modifier
                            .size(48.dp)
                            .focusRequester(fabFocus)
                            .dpadFocusableItem(
                                onClick = onNewMessage,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                borderWidth = 3.dp
                            )
                    ) {
                        Icon(Icons.Default.Edit, "New message", modifier = Modifier.size(20.dp))
                    }
                }
                LaunchedEffect(Unit) { try { fabFocus.requestFocus() } catch (_: Exception) {} }
            }
            else -> {
                val listState = rememberLazyListState()
                val focusRequesters = remember(threads.size) { List(threads.size) { FocusRequester() } }
                val scope = rememberCoroutineScope()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    itemsIndexed(threads) { index, thread ->
                        ThreadItem(
                            thread = thread,
                            draftText = drafts[thread.threadId],
                            focusRequester = focusRequesters.getOrNull(index),
                            isFirst = index == 0,
                            onClick = { onOpenChat(thread.threadId, thread.address, thread.contactName) },
                            onPin = { viewModel.togglePin(thread.threadId) },
                            onArchive = {
                                viewModel.toggleArchive(thread.threadId)
                                val msg = if (thread.isArchived) "Conversation unarchived" else "Conversation archived"
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = msg,
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.toggleArchive(thread.threadId)
                                    }
                                }
                            },
                            onMute = { viewModel.toggleMute(thread.threadId) },
                            onBlock = { viewModel.toggleBlock(thread.threadId) },
                            onDelete = { viewModel.deleteThread(thread.threadId) }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                    // FAB as the last list item — D-pad down from the last thread reaches it
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FloatingActionButton(
                                onClick = onNewMessage,
                                modifier = Modifier
                                    .size(48.dp)
                                    .focusRequester(fabFocus)
                                    .dpadFocusableItem(
                                        onClick = onNewMessage,
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        borderWidth = 3.dp
                                    )
                            ) {
                                Icon(Icons.Default.Edit, "New message", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadItem(
    thread: SmsThread,
    draftText: String?,
    focusRequester: FocusRequester?,
    isFirst: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onMute: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit
) {
    var showOptionsDialog by remember { mutableStateOf(false) }
    val menuFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("Options", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    DialogOption(if (thread.isPinned) "Unpin" else "Pin to Top") { showOptionsDialog = false; onPin() }
                    DialogOption(if (thread.isArchived) "Unarchive" else "Archive") { showOptionsDialog = false; onArchive() }
                    DialogOption(if (thread.isMuted) "Unmute" else "Mute") { showOptionsDialog = false; onMute() }
                    DialogOption(if (thread.isBlocked) "Unblock" else "Block") { showOptionsDialog = false; onBlock() }
                    DialogOption("Delete") { showOptionsDialog = false; onDelete() }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOptionsDialog = false }) {
                    Text("Close", style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left zone: thread body ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                // D-pad RIGHT from the thread body moves focus to the three-dot button
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.DirectionRight) {
                        focusManager.moveFocus(FocusDirection.Right)
                        true
                    } else false
                }
                .dpadFocusableItem(
                    onClick = onClick,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    borderWidth = 3.dp,
                    padding = 2.dp
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            val initials = thread.contactName
                .split(" ")
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        avatarColor(thread.contactName),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials.ifBlank { "?" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = thread.contactName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (thread.isPinned) Icon(Icons.Default.PushPin, "Pinned", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    if (thread.isMuted) Icon(Icons.Default.NotificationsOff, "Muted", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (thread.isBlocked) Icon(Icons.Default.Block, "Blocked", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatDate(thread.date),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!draftText.isNullOrBlank()) {
                        Text(
                            text = "Draft: ",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = draftText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = thread.snippet,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (thread.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (thread.unreadCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Badge(modifier = Modifier.padding(0.dp)) {
                            Text(thread.unreadCount.toString(), fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // ── Right zone: three-dot menu ─────────────────────────────────────────
        // D-pad LEFT from here moves back to the thread body
        Box(
            modifier = Modifier
                .size(40.dp)
                .focusRequester(menuFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.DirectionLeft) {
                        focusManager.moveFocus(FocusDirection.Left)
                        true
                    } else false
                }
                .dpadFocusableItem(
                    onClick = { showOptionsDialog = true },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    borderWidth = 2.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Options",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    LaunchedEffect(isFirst) {
        if (isFirst) focusRequester?.requestFocus()
    }
}

@Composable
private fun DialogOption(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusableItem(
                onClick = onClick,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                borderWidth = 3.dp,
                padding = 2.dp
            )
            .padding(vertical = 10.dp, horizontal = 12.dp)
    )
}

private fun avatarColor(name: String): androidx.compose.ui.graphics.Color {
    val colors = listOf(
        androidx.compose.ui.graphics.Color(0xFF1976D2),
        androidx.compose.ui.graphics.Color(0xFF388E3C),
        androidx.compose.ui.graphics.Color(0xFFF57C00),
        androidx.compose.ui.graphics.Color(0xFF7B1FA2),
        androidx.compose.ui.graphics.Color(0xFFD32F2F),
        androidx.compose.ui.graphics.Color(0xFF0288D1)
    )
    return colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
}

private fun formatDate(ms: Long): String {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return if (now.get(Calendar.DATE) == cal.get(Calendar.DATE))
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    else
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
}
