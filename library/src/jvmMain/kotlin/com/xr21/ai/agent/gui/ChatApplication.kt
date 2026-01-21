package com.xr21.ai.agent.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.xr21.ai.agent.gui.components.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.*

// ==================== 应用入口 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApplication() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var currentTheme by remember { mutableStateOf(ThemeMode.SYSTEM) }

    val sessionManager = remember { SessionManager.getInstance() }

    // 更新会话ID的回调
    val updateSessionId: (String) -> Unit = { newSessionId ->
        currentSessionId = newSessionId
    }

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center), width = 1200.dp, height = 800.dp
    )

    Window(
        onCloseRequest = { /* 退出 */ }, title = "AI Agents - 智能助手", state = windowState
    ) {
        AppTheme(themeMode = currentTheme) {
            when (currentScreen) {
                is Screen.Chat -> ChatScreen(
                    sessionId = currentSessionId,
                    sessionManager = sessionManager,
                    onNewSession = {
                        currentSessionId = sessionManager.createSession()
                    },
                    onSessionSelected = { sessionId ->
                        currentSessionId = sessionId
                    },
                    onOpenSessionManager = {
                        currentScreen = Screen.SessionManager
                    },
                    onOpenSettings = {
                        currentScreen = Screen.Settings
                    },
                    onSessionIdUpdate = updateSessionId
                )

                is Screen.SessionManager -> SessionManagerScreen(
                    sessionManager = sessionManager,
                    onSessionSelected = { sessionId ->
                        currentSessionId = sessionId
                        currentScreen = Screen.Chat
                    },
                    onBack = {
                        currentScreen = Screen.Chat
                    },
                    onNewSession = {
                        currentSessionId = sessionManager.createSession()
                        currentScreen = Screen.Chat
                    })

                is Screen.Settings -> SettingsScreen(
                    onBack = {
                        currentScreen = Screen.Chat
                    }, onThemeChange = { theme ->
                        currentTheme = theme
                    }, onClearHistory = {
                        clearAllSessions()
                    }, currentTheme = currentTheme
                )
            }
        }
    }
}

sealed class Screen {
    data object Chat : Screen()
    data object SessionManager : Screen()
    data object Settings : Screen()
}

// ==================== 聊天界面 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String?,
    sessionManager: SessionManager,
    onNewSession: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onOpenSessionManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onSessionIdUpdate: (String) -> Unit = {}
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var messages by remember { mutableStateOf(mutableStateListOf<UiChatMessage>()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf(listOf<UiSessionInfo>()) }
    var currentJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isProcessingMessage by remember { mutableStateOf(false) }
    var lastSessionId by remember { mutableStateOf<String?>(null) }
    var streamingContent by remember { mutableStateOf("") }
    var streamingMessageId by remember { mutableStateOf<String?>(null) }
    var isStreaming by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val chatService = remember { ChatService.getInstance() }

    val onCancelRequest = {
        currentJob?.cancel()
        isLoading = false
        isSending = false
        isProcessingMessage = false
        // 清理流式状态
        if (isStreaming && streamingMessageId != null) {
            val currentIndex = messages.indexOfFirst { it.id == streamingMessageId }
            if (currentIndex >= 0) {
                messages.removeAt(currentIndex)
            }
        }
        isStreaming = false
        streamingContent = ""
        streamingMessageId = null
    }

    // 通用发送消息函数
    fun sendMessage(userInput: String) {
        currentJob = scope.launch {
            isSending = true
            isProcessingMessage = true
            isLoading = true
            streamingContent = ""
            streamingMessageId = null
            isStreaming = false

            val userMsg = UiChatMessage(
                id = UUID.randomUUID().toString(),
                content = userInput,
                type = UiMessageType.USER,
                timestamp = LocalDateTime.now()
            )
            messages.add(userMsg)
            inputText = TextFieldValue("")

            try {
                listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
            } catch (_: Throwable) {}

            chatService.sendMessage(userInput, sessionId).collect { response ->
                response.sessionId?.let { newSessionId ->
                    onSessionIdUpdate(newSessionId)
                    lastSessionId = newSessionId
                }

                when (response.type) {
                    ResponseType.THINKING -> {}
                    ResponseType.STREAMING -> {
                        if (streamingMessageId == null) {
                            streamingMessageId = response.messageId
                            isStreaming = true
                            val placeholderMsg = UiChatMessage(
                                id = response.messageId,
                                content = "",
                                type = UiMessageType.ASSISTANT,
                                timestamp = LocalDateTime.now()
                            )
                            messages.add(placeholderMsg)
                        }
                        streamingContent += response.content
                        val currentIndex = messages.indexOfFirst { it.id == streamingMessageId }
                        if (currentIndex >= 0) {
                            val updatedMsg = messages[currentIndex].copy(content = streamingContent)
                            messages[currentIndex] = updatedMsg
                            try {
                                listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                            } catch (_: Throwable) {}
                        }
                    }

                    ResponseType.ASSISTANT -> {
                        if (isStreaming && streamingMessageId != null) {
                            val currentIndex = messages.indexOfFirst { it.id == streamingMessageId }
                            if (currentIndex >= 0) {
                                val finalMsg = messages[currentIndex].copy(
                                    content = streamingContent.ifEmpty { response.content }
                                )
                                messages[currentIndex] = finalMsg
                            }
                        } else {
                            val aiMsg = UiChatMessage(
                                id = response.messageId,
                                content = response.content,
                                type = UiMessageType.ASSISTANT,
                                timestamp = LocalDateTime.now()
                            )
                            messages.add(aiMsg)
                        }
                        isStreaming = false
                        streamingContent = ""
                        streamingMessageId = null
                    }

                    ResponseType.ERROR -> {
                        if (isStreaming && streamingMessageId != null) {
                            val currentIndex = messages.indexOfFirst { it.id == streamingMessageId }
                            if (currentIndex >= 0) {
                                messages.removeAt(currentIndex)
                            }
                        }
                        val errorMsg = UiChatMessage(
                            id = response.messageId,
                            content = response.content,
                            type = UiMessageType.ERROR,
                            timestamp = LocalDateTime.now()
                        )
                        messages.add(errorMsg)
                        isStreaming = false
                        streamingContent = ""
                        streamingMessageId = null
                    }
                }
            }

            // 确保流式传输完成后的最终状态
            if (isStreaming && streamingContent.isNotEmpty() && streamingMessageId != null) {
                val currentIndex = messages.indexOfFirst { it.id == streamingMessageId }
                if (currentIndex >= 0) {
                    val finalMsg = messages[currentIndex].copy(content = streamingContent)
                    messages[currentIndex] = finalMsg
                }
            }
            isStreaming = false
            streamingContent = ""
            streamingMessageId = null

            isLoading = false
            isSending = false
            isProcessingMessage = false
            // 聊天结束，自动保存会话消息
            lastSessionId?.let { sid ->
                if (messages.isNotEmpty()) {
                    sessionManager.saveMessages(sid, messages)
                }
            }
            sessions = sessionManager.loadSessions()

            if (messages.isNotEmpty()) {
                try {
                    listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                } catch (_: Throwable) {}
            }
        }
    }

    LaunchedEffect(Unit) {
        sessions = sessionManager.loadSessions()
    }

    LaunchedEffect(sessionId) {
        if (sessionId != null) {
            if (sessionId != lastSessionId) {
                messages.clear()
                messages.addAll(sessionManager.loadMessages(sessionId))
                lastSessionId = sessionId
            }
        } else {
            messages.clear()
            lastSessionId = null
        }
    }

    val handleSendMessage = {
        val userInput = inputText.text
        if (userInput.isNotBlank() && !isLoading && !isSending) {
            sendMessage(userInput)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "AI Agents", style = TextStyle(
                            fontWeight = FontWeight.Bold, fontSize = 20.sp
                        )
                    )
                    if (sessionId != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "• $sessionId", style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                            )
                        )
                    }
                }
            }, actions = {
                IconButton(onClick = onNewSession) {
                    Icon(
                        imageVector = Icons.Default.Add, contentDescription = "新建会话"
                    )
                }
                IconButton(onClick = onOpenSessionManager) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "会话管理"
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings, contentDescription = "设置"
                    )
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }, bottomBar = {
        ChatInput(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSendMessage = handleSendMessage,
            isLoading = isLoading,
            isSending = isSending
        )
    }) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)
        ) {
            if (messages.isEmpty()) {
                EmptyState(
                    onNewSession = onNewSession, onOpenSessionManager = onOpenSessionManager
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { msg -> msg.id }) { message ->
                        val messageIndex = messages.indexOf(message)

                        MessageBubble(
                            message = message,
                            onDelete = {
                                // 删除当前消息及之后的所有消息
                                val messagesToKeep = messages.subList(0, messageIndex).toMutableList()
                                messages.clear()
                                messages.addAll(messagesToKeep)
                                sessionId?.let { sessionManager.saveMessages(it, messages) }
                            },
                            onCopy = {
                                // 复制功能由 MessageBubble 内部处理
                            },
                            onRetry = {
                                if (message.type == UiMessageType.USER) {
                                    // 用户消息：移除当前消息之后的所有消息，重新发送当前用户消息
                                    val messagesToKeep = messages.subList(0, messageIndex).toMutableList()
                                    messages.clear()
                                    messages.addAll(messagesToKeep)
                                    sendMessage(message.content)
                                } else {
                                    // 回复消息：移除当前消息及之后的所有消息，然后移除最后一条用户消息，重新发送
                                    val messagesToKeep = messages.subList(0, messageIndex).toMutableList()
                                    messages.clear()
                                    messages.addAll(messagesToKeep)

                                    // 找到并移除最后一条用户消息
                                    val lastUserIndex = messages.indexOfLast { it.type == UiMessageType.USER }
                                    if (lastUserIndex >= 0) {
                                        val userInput = messages[lastUserIndex].content
                                        messages.removeAt(lastUserIndex)
                                        sendMessage(userInput)
                                    }
                                }
                            },
                            isStreaming = isStreaming && message.id == streamingMessageId
                        )
                    }
                }

                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.BottomCenter), onStop = onCancelRequest
                    )
                }
            }

            LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                }
            }
        }
    }
}

// ==================== 会话管理界面 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionManagerScreen(
    sessionManager: SessionManager,
    onSessionSelected: (String) -> Unit,
    onBack: () -> Unit,
    onNewSession: () -> Unit
) {
    var sessions by remember { mutableStateOf(listOf<UiSessionInfo>()) }

    LaunchedEffect(Unit) {
        sessions = sessionManager.loadSessions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "会话管理", style = TextStyle(
                            fontWeight = FontWeight.Bold, fontSize = 20.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNewSession) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建会话"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        SessionList(
            sessions = sessions,
            sessionManager = sessionManager,
            onSessionSelected = onSessionSelected,
            onNewSession = onNewSession,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

// ==================== 主函数 ====================

fun main() {
    application {
        ChatApplication()
    }
}
