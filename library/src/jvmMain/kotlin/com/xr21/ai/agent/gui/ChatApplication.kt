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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

// ==================== 数据模型 ====================

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: MessageType,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val toolCall: String? = null
)

enum class MessageType {
    USER, ASSISTANT, SYSTEM, TOOL_CALL, TOOL_RESPONSE, ERROR
}

data class SessionInfo(
    val sessionId: String,
    val title: String,
    val messageCount: Int,
    val lastUpdated: LocalDateTime,
    val preview: String
)

// ==================== 主题配置 ====================

object ChatTheme {
    val primaryColor = Color(0xFF6366F1)
    val secondaryColor = Color(0xFF8B5CF6)
    val backgroundColor = Color(0xFF0F172A)
    val surfaceColor = Color(0xFF1E293B)
    val surfaceVariantColor = Color(0xFF334155)
    val userMessageColor = Color(0xFF6366F1)
    val assistantMessageColor = Color(0xFF1E293B)
    val systemMessageColor = Color(0xFFF59E0B)
    val errorMessageColor = Color(0xFFEF4444)
    val textPrimary = Color(0xFFF8FAFC)
    val textSecondary = Color(0xFF94A3B8)
    val borderColor = Color(0xFF475569)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApplication() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center),
        width = 1200.dp,
        height = 800.dp
    )

    Window(
        onCloseRequest = { /* 退出 */ },
        title = "AI Agents - 智能助手",
        state = windowState
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = ChatTheme.primaryColor,
                secondary = ChatTheme.secondaryColor,
                background = ChatTheme.backgroundColor,
                surface = ChatTheme.surfaceColor,
                surfaceVariant = ChatTheme.surfaceVariantColor,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = ChatTheme.textPrimary,
                onSurface = ChatTheme.textPrimary,
                error = ChatTheme.errorMessageColor
            )
        ) {
            when (val screen = currentScreen) {
                is Screen.Chat -> ChatScreen(
                    sessionId = currentSessionId,
                    onNewSession = {
                        currentSessionId = "session-${System.nanoTime()}"
                    },
                    onSessionSelected = { sessionId ->
                        currentSessionId = sessionId
                    },
                    onOpenSessionManager = {
                        currentScreen = Screen.SessionManager
                    }
                )
                is Screen.SessionManager -> SessionManagerScreen(
                    onSessionSelected = { sessionId ->
                        currentSessionId = sessionId
                        currentScreen = Screen.Chat
                    },
                    onBack = {
                        currentScreen = Screen.Chat
                    }
                )
            }
        }
    }
}

sealed class Screen {
    data object Chat : Screen()
    data object SessionManager : Screen()
}

// ==================== 聊天界面 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String?,
    onNewSession: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onOpenSessionManager: () -> Unit
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isLoading by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf(listOf<SessionInfo>()) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // 模拟会话列表
    LaunchedEffect(Unit) {
        sessions = listOf(
            SessionInfo(
                sessionId = "session-1",
                title = "代码开发讨论",
                messageCount = 15,
                lastUpdated = LocalDateTime.now().minusMinutes(5),
                preview = "帮我写一个排序算法..."
            ),
            SessionInfo(
                sessionId = "session-2",
                title = "文档编写",
                messageCount = 8,
                lastUpdated = LocalDateTime.now().minusHours(2),
                preview = "如何部署应用到生产环境..."
            ),
            SessionInfo(
                sessionId = "session-3",
                title = "问题排查",
                messageCount = 23,
                lastUpdated = LocalDateTime.now().minusDays(1),
                preview = "程序崩溃了，请帮我分析日志..."
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = ChatTheme.primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "AI Agents",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        )
                        if (sessionId != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "• $sessionId",
                                style = TextStyle(
                                    color = ChatTheme.textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNewSession) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建会话"
                        )
                    }
                    IconButton(onClick = onOpenSessionManager) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "会话管理"
                        )
                    }
                    IconButton(onClick = { /* 设置 */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChatTheme.surfaceColor
                )
            )
        },
        bottomBar = {
            Surface(
                color = ChatTheme.surfaceColor,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // 快捷键提示
                    Text(
                        text = "提示: Ctrl+Enter 发送 | Ctrl+N 新建会话 | Ctrl+S 会话管理",
                        style = TextStyle(
                            color = ChatTheme.textSecondary,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 附件按钮
                        IconButton(
                            onClick = { /* 附件 */ },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "添加附件",
                                tint = if (isLoading) ChatTheme.textSecondary else ChatTheme.textPrimary
                            )
                        }

                        // 输入框
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp, max = 150.dp)
                                .onKeyEvent { event ->
                                    if (event.key == Key.Enter && event.isCtrlPressed) {
                                        if (inputText.text.isNotBlank() && !isLoading) {
                                            scope.launch {
                                                val userMessage = ChatMessage(
                                                    content = inputText.text,
                                                    type = MessageType.USER
                                                )
                                                messages = messages + userMessage
                                                inputText = TextFieldValue("")

                                                // 模拟 AI 响应
                                                isLoading = true
                                                delay(1000)
                                                val aiMessage = ChatMessage(
                                                    content = generateAIResponse(inputText.text),
                                                    type = MessageType.ASSISTANT
                                                )
                                                messages = messages + aiMessage
                                                isLoading = false
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
                                    color = ChatTheme.textSecondary
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ChatTheme.primaryColor,
                                unfocusedBorderColor = ChatTheme.borderColor,
                                focusedContainerColor = ChatTheme.surfaceVariantColor,
                                unfocusedContainerColor = ChatTheme.surfaceVariantColor,
                                cursorColor = ChatTheme.primaryColor
                            ),
                            shape = RoundedCornerShape(28.dp),
                            enabled = !isLoading,
                            maxLines = 5
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // 发送按钮
                        FilledIconButton(
                            onClick = {
                                if (inputText.text.isNotBlank() && !isLoading) {
                                    scope.launch {
                                        val userMessage = ChatMessage(
                                            content = inputText.text,
                                            type = MessageType.USER
                                        )
                                        messages = messages + userMessage
                                        inputText = TextFieldValue("")

                                        // 模拟 AI 响应
                                        isLoading = true
                                        delay(1000)
                                        val aiMessage = ChatMessage(
                                            content = generateAIResponse(inputText.text),
                                            type = MessageType.ASSISTANT
                                        )
                                        messages = messages + aiMessage
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = inputText.text.isNotBlank() && !isLoading,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = ChatTheme.primaryColor,
                                disabledContainerColor = ChatTheme.surfaceVariantColor
                            ),
                            modifier = Modifier.size(56.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ChatTheme.backgroundColor)
        ) {
            if (messages.isEmpty()) {
                // 空状态
                EmptyState()
            } else {
                // 消息列表
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onResend = { /* 重新发送 */ }
                        )
                    }
                }

                // 加载动画
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // 自动滚动到底部
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
    message: ChatMessage,
    onResend: () -> Unit
) {
    val isUser = message.type == MessageType.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // 用户头像
        if (!isUser) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = ChatTheme.secondaryColor,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChatTheme.surfaceVariantColor)
                    .padding(6.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            // 消息类型标签
            Text(
                text = when (message.type) {
                    MessageType.USER -> "你"
                    MessageType.ASSISTANT -> "AI"
                    MessageType.SYSTEM -> "系统"
                    MessageType.TOOL_CALL -> "工具调用"
                    MessageType.TOOL_RESPONSE -> "工具响应"
                    MessageType.ERROR -> "错误"
                },
                style = TextStyle(
                    color = when (message.type) {
                        MessageType.USER -> ChatTheme.primaryColor
                        MessageType.ASSISTANT -> ChatTheme.secondaryColor
                        MessageType.SYSTEM -> ChatTheme.systemMessageColor
                        MessageType.ERROR -> ChatTheme.errorMessageColor
                        else -> ChatTheme.textSecondary
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
            )

            // 消息气泡
            Surface(
                color = when (message.type) {
                    MessageType.USER -> ChatTheme.userMessageColor
                    MessageType.ASSISTANT -> ChatTheme.assistantMessageColor
                    MessageType.SYSTEM -> ChatTheme.systemMessageColor.copy(alpha = 0.2f)
                    MessageType.ERROR -> ChatTheme.errorMessageColor.copy(alpha = 0.2f)
                    else -> ChatTheme.surfaceVariantColor
                },
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                modifier = Modifier.widthIn(max = 600.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // 工具调用信息
                    if (message.type == MessageType.TOOL_CALL && message.toolCall != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = ChatTheme.systemMessageColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = message.toolCall,
                                style = TextStyle(
                                    color = ChatTheme.systemMessageColor,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        HorizontalDivider(
                            color = ChatTheme.borderColor.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // 消息内容 - 支持简单的 Markdown 格式
                    Text(
                        text = parseMarkdown(message.content),
                        style = TextStyle(
                            color = ChatTheme.textPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    )
                }
            }

            // 时间戳
            Text(
                text = message.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = TextStyle(
                    color = ChatTheme.textSecondary,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = ChatTheme.primaryColor,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChatTheme.surfaceVariantColor)
                    .padding(6.dp)
            )
        }
    }
}

// ==================== 简单的 Markdown 解析 ====================

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
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = ChatTheme.primaryColor.copy(alpha = 0.7f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "AI Agents",
            style = TextStyle(
                color = ChatTheme.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "您的智能助手已就绪",
            style = TextStyle(
                color = ChatTheme.textSecondary,
                fontSize = 16.sp
            )
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { /* 新建会话 */ },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ChatTheme.primaryColor
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建会话")
            }
            OutlinedButton(
                onClick = { /* 会话管理 */ },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ChatTheme.secondaryColor
                )
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
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
        color = ChatTheme.surfaceColor.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
        modifier = modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ChatTheme.primaryColor,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "AI 正在思考...",
                style = TextStyle(
                    color = ChatTheme.textPrimary,
                    fontSize = 14.sp
                )
            )
        }
    }
}

// ==================== 会话管理界面 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionManagerScreen(
    onSessionSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    var sessions by remember { mutableStateOf(listOf<SessionInfo>()) }

    // 模拟加载会话
    LaunchedEffect(Unit) {
        sessions = listOf(
            SessionInfo(
                sessionId = "session-1",
                title = "代码开发讨论",
                messageCount = 15,
                lastUpdated = LocalDateTime.now().minusMinutes(5),
                preview = "帮我写一个排序算法..."
            ),
            SessionInfo(
                sessionId = "session-2",
                title = "文档编写",
                messageCount = 8,
                lastUpdated = LocalDateTime.now().minusHours(2),
                preview = "如何部署应用到生产环境..."
            ),
            SessionInfo(
                sessionId = "session-3",
                title = "问题排查",
                messageCount = 23,
                lastUpdated = LocalDateTime.now().minusDays(1),
                preview = "程序崩溃了，请帮我分析日志..."
            ),
            SessionInfo(
                sessionId = "session-4",
                title = "新会话",
                messageCount = 0,
                lastUpdated = LocalDateTime.now(),
                preview = "暂无消息"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "会话管理",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
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
                    IconButton(onClick = { /* 新建会话 */ }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建会话"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChatTheme.surfaceColor
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ChatTheme.backgroundColor)
        ) {
            items(sessions, key = { it.sessionId }) { session ->
                SessionCard(
                    session = session,
                    onClick = { onSessionSelected(session.sessionId) }
                )
            }
        }
    }
}

@Composable
fun SessionCard(
    session: SessionInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = ChatTheme.surfaceColor
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.title,
                    style = TextStyle(
                        color = ChatTheme.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = ChatTheme.primaryColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${session.messageCount} 条消息",
                        style = TextStyle(
                            color = ChatTheme.primaryColor,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = session.preview,
                style = TextStyle(
                    color = ChatTheme.textSecondary,
                    fontSize = 14.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = session.lastUpdated.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                style = TextStyle(
                    color = ChatTheme.textSecondary.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            )
        }
    }
}

// ==================== AI 响应模拟 ====================

fun generateAIResponse(input: String): String {
    return when {
        input.contains("你好") || input.contains("hello") || input.contains("Hi") -> """
            你好！我是 AI Agents，一个智能助手。

            我可以帮助你：
            • 回答问题和解释概念
            • 编写和调试代码
            • 分析和解决问题
            • 提供建议和信息查询

            请告诉我你需要什么帮助！
        """.trimIndent()

        input.contains("代码") || input.contains("code") -> """
            好的，我理解你想讨论编程相关的问题。

            为了更好地帮助你，请告诉我：
            1. 你使用的编程语言是什么？
            2. 你想要实现什么功能？
            3. 是否有特定的代码问题需要解决？

            你可以粘贴代码片段，我会帮你分析和优化。
        """.trimIndent()

        input.contains("帮助") || input.contains("help") -> """
            这是 AI Agents 的主要功能：

            **核心能力**
            • 智能对话 - 自然语言交互
            • 代码分析 - 理解、编写、调试代码
            • 工具调用 - 执行各种任务
            • 会话管理 - 保存和恢复对话历史

            **快捷键**
            • Ctrl+Enter - 发送消息
            • Ctrl+N - 新建会话
            • Ctrl+S - 会话管理

            有什么我可以帮你的吗？
        """.trimIndent()

        else -> """
            收到！我理解你的问题是：

            > ${input.take(100)}${if (input.length > 100) "..." else ""}

            请稍等，让我思考一下...

            作为一个 AI 助手，我会尽力帮助你解决这个问题。
            你能否提供更多细节，比如：
            • 具体的背景信息
            • 相关的代码或错误信息
            • 你希望达到的目标

            这样我可以给出更准确的回答！
        """.trimIndent()
    }
}

// ==================== 应用入口 ====================

fun main() {
    application {
        ChatApplication()
    }
}
