package com.xr21.ai.agent.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

// ==================== 应用入口 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApplication() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var currentTheme by remember { mutableStateOf(ThemeMode.SYSTEM) }

    val sessionManager = remember { SessionManager.getInstance() }
    val chatColors = getCurrentChatColors()

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center), width = 1200.dp, height = 800.dp
    )

    Window(
        onCloseRequest = { /* 退出 */ }, title = "AI Agents - 智能助手", state = windowState
    ) {
        AppTheme(themeMode = currentTheme) {
            val currentChatColors = getCurrentChatColors()

            when (val screen = currentScreen) {
                is Screen.Chat -> ChatScreen(
                    sessionId = currentSessionId, sessionManager = sessionManager,
                    onNewSession = {
                        currentSessionId = sessionManager.createSession()
                    },
                    onSessionSelected = { sessionId ->
                        currentSessionId = sessionId
                    },
                    onOpenSessionManager = {
                        currentScreen = Screen.SessionManager
                    }, onOpenSettings = {
                        currentScreen = Screen.Settings
                    })

                is Screen.SessionManager -> SessionManagerScreen(
                    sessionManager = sessionManager,
                    onSessionSelected = { sessionId ->
                        currentSessionId = sessionId
                        currentScreen = Screen.Chat
                    },
                    onBack = {
                        currentScreen = Screen.Chat
                    }, onNewSession = {
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
    sessionId: String?, sessionManager: SessionManager,
    onNewSession: () -> Unit,
    onSessionSelected: (String) -> Unit, onOpenSessionManager: () -> Unit, onOpenSettings: () -> Unit
) {

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var messages by remember { mutableStateOf(listOf<UiChatMessage>()) }
    var isLoading by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf(listOf<UiSessionInfo>()) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val chatColors = getCurrentChatColors()

    // 加载会话列表
    LaunchedEffect(Unit) {
        sessions = sessionManager.loadSessions()
    }

    // 加载会话消息
    LaunchedEffect(sessionId) {
        if (sessionId != null) {
            messages = sessionManager.loadMessages(sessionId)
        } else {
            messages = emptyList()
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
                    imageVector = Icons.Default.List, contentDescription = "会话管理"
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
        Surface(
            color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(
                    text = "提示: Ctrl+Enter 发送 | Ctrl+N 新建会话 | Ctrl+S 会话管理", style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                    ), modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { /* 附件 */ }, enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "添加附件",
                            tint = if (isLoading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp, max = 150.dp).onKeyEvent { event ->
                            if (event.key == Key.Enter && event.isCtrlPressed) {
                                if (inputText.text.isNotBlank() && !isLoading) {
                                    // 使用 LocalChatService 发送消息
                                    val chatService = ChatService.getInstance()
                                    val userInput = inputText.text

                                    scope.launch {
                                        val userMsg = UiChatMessage(
                                            id = UUID.randomUUID().toString(),
                                            content = userInput,
                                            type = UiMessageType.USER,
                                            timestamp = LocalDateTime.now()
                                        )
                                        messages = messages + userMsg
                                        inputText = TextFieldValue("")

                                        if (sessionId != null) {
                                            sessionManager.saveMessages(sessionId, listOf(userMsg))
                                        }

                                        isLoading = true

                                        // 调用 LocalChatService
                                        chatService.sendMessage(userInput).collect { response ->
                                            when (response.type) {
                                                ResponseType.THINKING -> {
                                                    // 显示加载状态
                                                }

                                                ResponseType.ASSISTANT -> {
                                                    val aiMsg = UiChatMessage(
                                                        id = response.messageId,
                                                        content = response.content,
                                                        type = UiMessageType.ASSISTANT,
                                                        timestamp = LocalDateTime.now()
                                                    )
                                                    messages = messages + aiMsg

                                                    if (sessionId != null) {
                                                        sessionManager.saveMessages(sessionId, listOf(aiMsg))
                                                    }
                                                }

                                                ResponseType.ERROR -> {
                                                    val errorMsg = UiChatMessage(
                                                        id = response.messageId,
                                                        content = response.content,
                                                        type = UiMessageType.ERROR,
                                                        timestamp = LocalDateTime.now()
                                                    )
                                                    messages = messages + errorMsg
                                                }

                                                else -> {}
                                            }
                                        }

                                        isLoading = false
                                        sessions = sessionManager.loadSessions()
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        },
                        placeholder = {
                            Text(
                                text = "输入消息... (Ctrl+Enter 发送)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(28.dp),
                        enabled = !isLoading,
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            if (inputText.text.isNotBlank() && !isLoading) {
                                val chatService = ChatService.getInstance()
                                val userInput = inputText.text

                                scope.launch {
                                    val userMsg = UiChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        content = userInput,
                                        type = UiMessageType.USER,
                                        timestamp = LocalDateTime.now()
                                    )
                                    messages = messages + userMsg
                                    inputText = TextFieldValue("")

                                    if (sessionId != null) {
                                        sessionManager.saveMessages(sessionId, listOf(userMsg))
                                    }

                                    isLoading = true

                                    chatService.sendMessage(userInput).collect { response ->
                                        when (response.type) {
                                            ResponseType.ASSISTANT -> {
                                                val aiMsg = UiChatMessage(
                                                    id = response.messageId,
                                                    content = response.content,
                                                    type = UiMessageType.ASSISTANT,
                                                    timestamp = LocalDateTime.now()
                                                )
                                                messages = messages + aiMsg

                                                if (sessionId != null) {
                                                    sessionManager.saveMessages(sessionId, listOf(aiMsg))
                                                }
                                            }

                                            ResponseType.ERROR -> {
                                                val errorMsg = UiChatMessage(
                                                    id = response.messageId,
                                                    content = response.content,
                                                    type = UiMessageType.ERROR,
                                                    timestamp = LocalDateTime.now()
                                                )
                                                messages = messages + errorMsg
                                            }

                                            else -> {}
                                        }
                                    }

                                    isLoading = false
                                    sessions = sessionManager.loadSessions()
                                }
                            }
                        },
                        enabled = inputText.text.isNotBlank() && !isLoading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.size(56.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
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
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message, onResend = { /* 重新发送 */ })
                    }
                }

                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }
}

// ==================== 消息气泡 ====================

@Composable
fun MessageBubble(
    message: UiChatMessage, onResend: () -> Unit
) {
    val isUser = message.type == UiMessageType.USER
    val chatColors = getCurrentChatColors()

    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            Text(
                text = when (message.type) {
                    UiMessageType.USER -> "你"
                    UiMessageType.ASSISTANT -> "AI"
                    UiMessageType.SYSTEM -> "系统"
                    UiMessageType.TOOL_CALL -> "工具调用"
                    UiMessageType.TOOL_RESPONSE -> "工具响应"
                    UiMessageType.ERROR -> "错误"
                }, style = TextStyle(
                    color = when (message.type) {
                        UiMessageType.USER -> MaterialTheme.colorScheme.primary
                        UiMessageType.ASSISTANT -> MaterialTheme.colorScheme.secondary
                        UiMessageType.SYSTEM -> chatColors.systemMessageColor
                        UiMessageType.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }, fontSize = 11.sp, fontWeight = FontWeight.Medium
                ), modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
            )

            Surface(
                color = when (message.type) {
                    UiMessageType.USER -> chatColors.userMessageColor
                    UiMessageType.ASSISTANT -> chatColors.assistantMessageColor
                    UiMessageType.SYSTEM -> chatColors.systemMessageColor.copy(alpha = 0.2f)
                    UiMessageType.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }, shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ), modifier = Modifier.widthIn(max = 600.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {

                    Text(
                        text = parseMarkdown(message.content), style = TextStyle(
                            color = chatColors.textPrimary, fontSize = 14.sp, lineHeight = 20.sp
                        )
                    )
                }
            }

            Text(
                text = message.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")), style = TextStyle(
                    color = chatColors.textSecondary, fontSize = 11.sp
                ), modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp)
            )
        }
    }
}

// ==================== Markdown 解析 ====================

@Composable
fun parseMarkdown(text: String): AnnotatedString {
    val builder = remember { AnnotatedString.Builder() }

    val content = buildString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        append(text.substring(i + 2, end))
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text.startsWith("*", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1) {
                        append(text.substring(i + 1, end))
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text.startsWith("```", i) -> {
                    val end = text.indexOf("```", i + 3)
                    if (end != -1) {
                        append(text.substring(i + 3, end).trimIndent())
                        i = end + 3
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text.startsWith("- ", i) -> {
                    append("• ")
                    i += 2
                }

                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
    builder.append(content)
    return builder.toAnnotatedString()
}

// ==================== 空状态 ====================

@Composable
fun EmptyState(
    onNewSession: () -> Unit, onOpenSessionManager: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "AI Agents", style = TextStyle(
                color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "您的智能助手已就绪", style = TextStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp
            )
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onNewSession, colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建会话")
            }
            OutlinedButton(
                onClick = onOpenSessionManager, colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("会话管理")
            }
        }
    }
}

// ==================== 加载指示器 ====================

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
        modifier = modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "AI 正在思考...", style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp
                )
            )
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
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

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
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无会话", style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(onClick = onNewSession) {
                        Icon(
                            imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("新建会话")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                items(sessions, key = { it.sessionId }) { session ->
                    SessionCard(
                        session = session,
                        onClick = { onSessionSelected(session.sessionId) },
                        onDelete = { showDeleteDialog = session.sessionId })
                }
            }
        }
    }

    // 删除确认对话框
    showDeleteDialog?.let { sessionId ->
        AlertDialog(onDismissRequest = { showDeleteDialog = null }, icon = {
            Icon(
                imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error
            )
        }, title = { Text("确认删除") }, text = { Text("确定要删除这个会话吗？此操作无法撤销。") }, confirmButton = {
            TextButton(
                onClick = {
                    sessionManager.deleteSession(sessionId)
                    sessions = sessionManager.loadSessions()
                    showDeleteDialog = null
                }, colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除")
            }
        }, dismissButton = {
            TextButton(onClick = { showDeleteDialog = null }) {
                Text("取消")
            }
        })
    }
}

@Composable
fun SessionCard(
    session: UiSessionInfo, onClick: () -> Unit, onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ), shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.briefDescription.ifEmpty { "新会话" }, style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ), modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${session.messageCount} 条消息", style = TextStyle(
                                color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp
                            ), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = session.briefDescription.ifEmpty { "新会话" }, style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
                    ), maxLines = 2, overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formatTimestamp(session.lastUpdated), style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp
                    )
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        val dateTime = LocalDateTime.parse(timestamp)
        val now = LocalDateTime.now()
        val diffMinutes = java.time.Duration.between(dateTime, now).toMinutes()

        when {
            diffMinutes < 1 -> "刚刚"
            diffMinutes < 60 -> "${diffMinutes} 分钟前"
            diffMinutes < 1440 -> "${diffMinutes / 60} 小时前"
            else -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }
    } catch (e: Exception) {
        timestamp
    }
}

// ==================== 主函数 ====================

fun main() {
    application {
        ChatApplication()
    }
}
