package com.xr21.ai.agent.gui

import androidx.compose.desktop.ui.tooling.preview.Preview
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
import com.xr21.ai.agent.entity.AgentOutput
import com.xr21.ai.agent.gui.components.*
import com.xr21.ai.agent.gui.model.ConversationMessage
import com.xr21.ai.agent.gui.model.MessageAggregator
import com.xr21.ai.agent.gui.model.StreamingMessageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.messages.UserMessage
import java.util.*

// ==================== 应用入口 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun ChatApplication() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var currentTheme by remember { mutableStateOf(ThemeMode.SYSTEM) }

    val sessionManager = remember { FileSessionManager.getInstance() }

    // 更新会话ID的回调
    val updateSessionId: (String) -> Unit = { newSessionId ->
        currentSessionId = newSessionId
    }

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center), width = 1200.dp, height = 800.dp
    )

    val coroutineScope = rememberCoroutineScope()

    Window(
        onCloseRequest = { /* 退出 */ }, title = "AI Agents - 智能助手", state = windowState
    ) {
        AppTheme(themeMode = currentTheme) {
            when (currentScreen) {
                is Screen.Chat -> ChatScreen(
                    sessionId = currentSessionId,
                    sessionManager = sessionManager,
                    onNewSession = {
                        coroutineScope.launch(Dispatchers.IO) {
                            currentSessionId = sessionManager.createSession()
                        }
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
                        coroutineScope.launch(Dispatchers.IO) {
                            currentSessionId = sessionManager.createSession()
                        }
                        currentScreen = Screen.Chat
                    })

                is Screen.Settings -> SettingsScreen(
                    onBack = {
                        currentScreen = Screen.Chat
                    }, onThemeChange = { theme ->
                        currentTheme = theme
                    }, onClearHistory = {
                        coroutineScope.launch(Dispatchers.IO) {
                            sessionManager.clearAllSessions()
                        }
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
    sessionManager: FileSessionManager,
    onNewSession: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onOpenSessionManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onSessionIdUpdate: (String) -> Unit = {}
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var messages by remember { mutableStateOf(mutableStateListOf<ConversationMessage>()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf(listOf<UiSessionInfo>()) }
    var currentJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastSessionId by remember { mutableStateOf<String?>(null) }
    var isStreaming by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val chatService = remember { ChatService.getInstance() }
    rememberCoroutineScope()

    // 流式消息处理器
    val streamingProcessor = remember { StreamingMessageProcessor.getInstance() }
    // 消息聚合器（用于加载会话时）
    val messageAggregator = remember { MessageAggregator.getInstance() }

    val onCancelRequest = {
        currentJob?.cancel()
        isLoading = false
        isSending = false
        isStreaming = false
        streamingProcessor.reset()
    }

    // 通用发送消息函数 - 使用新版 ConversationMessage 模型
    fun sendMessage(userInput: String) {
        currentJob = scope.launch {
            isSending = true
            isLoading = true
            isStreaming = false
            streamingProcessor.reset()

            // 添加用户消息
            val userMessage = ConversationMessage.User(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                content = userInput,
                rawMessages = listOf(UserMessage(userInput))
            )
            messages.add(userMessage)
            inputText = TextFieldValue("")

            try {
                listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
            } catch (_: Throwable) {}

            // 使用流式消息处理器处理输出
            chatService.sendMessage(userInput, sessionId).collect { output: AgentOutput<*> ->
                val updates = streamingProcessor.processAgentOutput(output, messages)
                updates.forEach { updatedMsg ->
                    val existingIndex = messages.indexOfFirst { it.id == updatedMsg.id }
                    if (existingIndex >= 0) {
                        messages[existingIndex] = updatedMsg
                    } else {
                        messages.add(updatedMsg)
                    }
                }

                // 检查是否正在流式输出
                isStreaming = streamingProcessor.getCurrentStreamingMessage() != null

                try {
                    listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                } catch (_: Throwable) {
                }
            }

            // 流式结束，重置状态
            isStreaming = false
            isLoading = false
            isSending = false

            // 重新加载会话列表
            sessions = withContext(Dispatchers.IO) {
                sessionManager.loadSessions()
            }

            if (messages.isNotEmpty()) {
                try {
                    listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                } catch (_: Throwable) {}
            }
        }
    }

    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) {
            sessionManager.loadSessions()
        }
    }

    LaunchedEffect(sessionId) {
        if (sessionId != null) {
            if (sessionId != lastSessionId) {
                messages.clear()
                val rawMessages = withContext(Dispatchers.IO) {
                    sessionManager.loadMessages(sessionId)
                }
                // 使用聚合器将原始消息转换为 ConversationMessage
                messages.addAll(messageAggregator.aggregate(rawMessages))
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

                        ConversationMessageBubble(
                            message = message,
                            onDelete = {
                                // 删除当前消息及之后的所有消息
                                val messagesToKeep = messages.subList(0, messageIndex).toMutableList()
                                messages.clear()
                                messages.addAll(messagesToKeep)
                                // 注意：保存逻辑需要调整，因为现在保存的是 ConversationMessage
                            },
                            onCopy = {
                                // 复制功能由 ConversationMessageBubble 内部处理
                            },
                            onRetry = {
                                when (message) {
                                    is ConversationMessage.User -> {
                                        // 用户消息：移除当前消息之后的所有消息，重新发送当前用户消息
                                        val messagesToKeep = messages.subList(0, messageIndex).toMutableList()
                                        messages.clear()
                                        messages.addAll(messagesToKeep)
                                        sendMessage(message.content)
                                    }

                                    is ConversationMessage.Assistant -> {
                                        // 回复消息：移除当前消息及之后的所有消息，然后移除最后一条用户消息，重新发送
                                        val messagesToKeep = messages.subList(0, messageIndex).toMutableList()
                                        messages.clear()
                                        messages.addAll(messagesToKeep)

                                        // 找到并移除最后一条用户消息
                                        val lastUserIndex = messages.indexOfLast { it is ConversationMessage.User }
                                        if (lastUserIndex >= 0) {
                                            val userInput =
                                                (messages[lastUserIndex] as? ConversationMessage.User)?.content ?: ""
                                            messages.removeAt(lastUserIndex)
                                            sendMessage(userInput)
                                        }
                                    }
                                }
                            },
                            isStreaming = isStreaming && message.id == streamingProcessor.getCurrentStreamingMessage()?.id
                        )
                    }
                }

                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.BottomCenter), onStop = onCancelRequest
                    )
                }
            }

            LaunchedEffect(messages.size, messages.lastOrNull()?.id) {
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
    sessionManager: FileSessionManager,
    onSessionSelected: (String) -> Unit,
    onBack: () -> Unit,
    onNewSession: () -> Unit
) {
    var sessions by remember { mutableStateOf(listOf<UiSessionInfo>()) }
    rememberCoroutineScope()

    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) {
            sessionManager.loadSessions()
        }
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
