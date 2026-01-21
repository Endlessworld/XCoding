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
    var typingEffectContent by remember { mutableStateOf("") }
    var typingEffectVisible by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
        if (inputText.text.isNotBlank() && !isLoading && !isSending) {
            isSending = true
            isProcessingMessage = true
            val chatService = ChatService.getInstance()
            val userInput = inputText.text

            currentJob = scope.launch {
                try {
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
                    } catch (_: Throwable) {

                    }

                    isLoading = true
                    streamingContent = ""
                    streamingMessageId = null
                    isStreaming = false

                    chatService.sendMessage(userInput, sessionId).collect { response ->
                        response.sessionId?.let { newSessionId ->
                            onSessionIdUpdate(newSessionId)
                            lastSessionId = newSessionId
                        }

                        when (response.type) {
                            ResponseType.THINKING -> {}
                            ResponseType.STREAMING -> {
                                // 初始化流式消息
                                if (streamingMessageId == null) {
                                    streamingMessageId = response.messageId
                                    isStreaming = true
                                    // 创建一个空的流式消息占位符
                                    val placeholderMsg = UiChatMessage(
                                        id = response.messageId,
                                        content = "",
                                        type = UiMessageType.ASSISTANT,
                                        timestamp = LocalDateTime.now()
                                    )
                                    messages.add(placeholderMsg)
                                }

                                // 累积流式内容
                                streamingContent += response.content

                                // 实时更新流式消息内容
                                val currentIndex = messages.indexOfFirst { it.id == streamingMessageId }
                                if (currentIndex >= 0) {
                                    // 创建新的消息对象以确保状态更新被检测到
                                    val updatedMsg = messages[currentIndex].copy(content = streamingContent)
                                    messages[currentIndex] = updatedMsg

                                    // 自动滚动到最新内容
                                    try {
                                        listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                                    } catch (_: Throwable) {
                                        // 忽略滚动异常
                                    }
                                }
                            }

                            ResponseType.ASSISTANT -> {
                                // 流式传输完成，最终确认消息
                                if (isStreaming && streamingMessageId != null) {
                                    val currentIndex = messages.indexOfFirst { it.id == streamingMessageId }
                                    if (currentIndex >= 0) {
                                        val finalMsg =
                                            messages[currentIndex].copy(content = streamingContent.ifEmpty { response.content })
                                        messages[currentIndex] = finalMsg
                                    }
                                } else {
                                    // 非流式响应，直接添加消息
                                    val aiMsg = UiChatMessage(
                                        id = response.messageId,
                                        content = response.content,
                                        type = UiMessageType.ASSISTANT,
                                        timestamp = LocalDateTime.now()
                                    )
                                    messages.add(aiMsg)
                                }

                                // 重置流式状态
                                isStreaming = false
                                streamingContent = ""
                                streamingMessageId = null
                            }

                            ResponseType.ERROR -> {
                                // 如果正在流式传输，先清理流式状态
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

                                // 重置流式状态
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

                    // 重置所有流式状态
                    isStreaming = false
                    streamingContent = ""
                    streamingMessageId = null

                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error collecting response: ${e.message}")

                    // 清理流式状态
                    if (isStreaming && streamingMessageId != null) {
                        val currentIndex = messages.indexOfFirst { it.id == streamingMessageId }
                        if (currentIndex >= 0) {
                            messages.removeAt(currentIndex)
                        }
                    }

                    // 重置流式状态
                    isStreaming = false
                    streamingContent = ""
                    streamingMessageId = null

                } finally {
                    isLoading = false
                    isSending = false
                    isProcessingMessage = false
                    sessions = sessionManager.loadSessions()
                    if (messages.isNotEmpty()) {
                        try {
                            listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                        } catch (_: Throwable) {
                            // 忽略滚动异常
                        }
                    }
                }
            }
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
                            message = message, onDelete = {
                                // 删除当前消息及之后的所有消息
                                val deleteIndex = messageIndex
                                val messagesToKeep = messages.subList(0, deleteIndex).toMutableList()
                                messages.clear()
                                messages.addAll(messagesToKeep)
                                // 保存到会话
                                sessionId?.let { sessionManager.saveMessages(it, messages) }
                            }, onCopy = {
                                // 复制功能由 MessageBubble 内部处理
                            }, onRetry = {
                                // 移除当前消息之后的所有消息
                                val retryIndex = messageIndex
                                val messagesToKeep = messages.subList(0, retryIndex).toMutableList()

                                // 如果当前消息是用户消息，重新发送
                                if (message.type == UiMessageType.USER) {
                                    messages.clear()
                                    messages.addAll(messagesToKeep)

                                    // 重新发送消息
                                    val userInput = message.content
                                    inputText = TextFieldValue("")

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

                                        try {
                                            listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                                        } catch (_: Throwable) {
                                        }

                                        val chatService = ChatService.getInstance()

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
                                                    val currentIndex =
                                                        messages.indexOfFirst { it.id == streamingMessageId }
                                                    if (currentIndex >= 0) {
                                                        val updatedMsg =
                                                            messages[currentIndex].copy(content = streamingContent)
                                                        messages[currentIndex] = updatedMsg
                                                        try {
                                                            listState.animateScrollToItem(
                                                                messages.size - 1, Int.MAX_VALUE
                                                            )
                                                        } catch (_: Throwable) {
                                                        }
                                                    }
                                                }

                                                ResponseType.ASSISTANT -> {
                                                    if (isStreaming && streamingMessageId != null) {
                                                        val currentIndex =
                                                            messages.indexOfFirst { it.id == streamingMessageId }
                                                        if (currentIndex >= 0) {
                                                            val finalMsg =
                                                                messages[currentIndex].copy(content = streamingContent.ifEmpty { response.content })
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
                                                        val currentIndex =
                                                            messages.indexOfFirst { it.id == streamingMessageId }
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
                                        sessions = sessionManager.loadSessions()

                                        if (messages.isNotEmpty()) {
                                            try {
                                                listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                                            } catch (_: Throwable) {
                                            }
                                        }
                                    }
                                }
                            }, isStreaming = isStreaming && message.id == streamingMessageId
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
                    // scrollOffset 设置为 Int.MAX_VALUE 确保滚动到底部
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
    sessionManager: SessionManager, onSessionSelected: (String) -> Unit, onBack: () -> Unit, onNewSession: () -> Unit
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
                }, navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回"
                        )
                    }
                }, actions = {
                    IconButton(onClick = onNewSession) {
                        Icon(
                            imageVector = Icons.Default.Add, contentDescription = "新建会话"
                        )
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }) { paddingValues ->
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
