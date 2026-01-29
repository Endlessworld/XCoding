package com.xr21.ai.agent.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.entity.AgentOutput
import com.xr21.ai.agent.gui.components.ChatInput
import com.xr21.ai.agent.gui.components.SessionCard
import com.xr21.ai.agent.gui.model.ConversationMessage
import com.xr21.ai.agent.gui.model.StreamingMessageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.messages.UserMessage
import java.util.*

/**
 * 首页组件 - 展示会话卡片列表和输入框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sessionManager: FileSessionManager,
    sessionStateTracker: SessionStateTracker,
    homeSendMessageBehavior: HomeSendMessageBehavior = HomeSendMessageBehavior.REFRESH_LIST,
    onNavigateToSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sessions by remember { mutableStateOf(listOf<UiSessionInfo>()) }
    var inputText by remember { mutableStateOf(TextFieldValue("")) }

    val coroutineScope = rememberCoroutineScope()
    val chatService = remember { ChatService.getInstance() }
    val streamingProcessor = remember { StreamingMessageProcessor.getInstance() }

    // 收集运行中的会话
    val runningSessions by sessionStateTracker.runningSessions.collectAsState()

    // 加载会话列表
    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) {
            sessionManager.loadSessions()
        }
        // 注册所有会话到状态追踪器
        sessionStateTracker.registerSessions(sessions.map { it.sessionId })
    }

    // 定期刷新运行中的会话状态（使用 snapshotFlow 避免触发重组时中断）
    LaunchedEffect(runningSessions) {
        snapshotFlow { runningSessions }.collect { running ->
            repeat(5) {
                if (running.isNotEmpty()) {
                    val latestSessions = withContext(Dispatchers.IO) {
                        sessionManager.loadSessions()
                    }
                    sessions = latestSessions
                    delay(500)
                }
            }
        }
    }

    // 流式处理状态
    var currentStreamingSessionId by remember { mutableStateOf<String?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var pendingUserInput by remember { mutableStateOf("") }
    // 标记当前流是否正在完成中，防止重组期间重复触发
    var isCompleting by remember { mutableStateOf(false) }

    // 流式处理协程
    LaunchedEffect(currentStreamingSessionId, isStreaming) {
        val sessionId = currentStreamingSessionId ?: return@LaunchedEffect
        if (!isStreaming || isCompleting) return@LaunchedEffect
        val userInput = pendingUserInput

        // 如果是导航模式，启动流处理后直接导航，让 SessionDetailScreen 处理渲染
        if (homeSendMessageBehavior == HomeSendMessageBehavior.NAVIGATE_TO_SESSION) {
            // 调用 sendMessage 启动流处理（在服务作用域中进行）
            chatService.sendMessage(userInput, sessionId)

            // 清除 UI 状态并导航
            isCompleting = true
            launch {
                delay(50) // 短暂延迟让流开始
                isStreaming = false
                currentStreamingSessionId = null
                pendingUserInput = ""
                isCompleting = false
            }
            onNavigateToSession(sessionId)
            return@LaunchedEffect
        }

        // REFRESH_LIST 模式：收集 SharedFlow 更新 UI
        val userMessage = ConversationMessage.User(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            content = userInput,
            rawMessages = listOf(UserMessage(userInput))
        )

        // 刷新任务引用，用于取消
        var refreshJob: kotlinx.coroutines.Job? = null
        var hasStartedRefresh = false
        var isFinalReceived = false

        try {
            // 调用 sendMessage 启动处理（服务作用域），获取 SharedFlow
            val sessionFlow = chatService.sendMessage(userInput, sessionId)

            sessionFlow.onCompletion { cause: Throwable? ->
                if (!isFinalReceived) {
                    isFinalReceived = true
                    isCompleting = true
                }
            }.collect { output: AgentOutput<*> ->
                streamingProcessor.processAgentOutput(output, mutableListOf(userMessage))

                val metadata = output.metadata
                val isThinking = metadata?.get("reasoningContent")?.toString()?.isNotEmpty() == true
                val isFinal = metadata?.get("finishReason") == "STOP" ||
                              metadata?.get("finishReason") == "COMPLETED" ||
                              output.metadata?.get("finished") == true ||
                              output.metadata?.containsKey("finished") == true

                if (isThinking && !hasStartedRefresh) {
                    hasStartedRefresh = true
                    refreshJob = launch {
                        repeat(5) {
                            val latestSessions = withContext(Dispatchers.IO) {
                                sessionManager.loadSessions()
                            }
                            sessions = latestSessions
                            delay(500)
                        }
                    }
                }

                if (isFinal && !isFinalReceived) {
                    isFinalReceived = true
                    isCompleting = true

                    launch {
                        refreshJob?.cancel()
                        refreshJob = null
                        isStreaming = false

                        sessions = withContext(Dispatchers.IO) {
                            sessionManager.loadSessions()
                        }

                        sessionStateTracker.updateSessionStatus(sessionId, SessionStatus.COMPLETED)
                        currentStreamingSessionId = null
                        pendingUserInput = ""
                        isCompleting = false
                    }
                }
            }
        } finally {
            if (isCompleting) {
                delay(100)
            }
            if (sessionStateTracker.isSessionRunning(sessionId)) {
                sessionStateTracker.updateSessionStatus(sessionId, SessionStatus.COMPLETED)
            }
            if (isStreaming) {
                isStreaming = false
            }
            if (isCompleting) {
                isCompleting = false
            }
        }
    }

    val handleSendMessage = {
        val userInput = inputText.text
        if (userInput.isNotBlank()) {
            coroutineScope.launch {
                pendingUserInput = userInput
                inputText = TextFieldValue("")

                // 1. 创建新会话
                val newSessionId = sessionManager.createSession()

                // 2. 立即保存用户消息到文件（以便详情页能加载）
                val userMessage = org.springframework.ai.chat.messages.UserMessage(userInput)
                sessionManager.saveMessages(newSessionId, listOf(userMessage))

                // 3. 更新会话状态为运行中
                sessionStateTracker.updateSessionStatus(newSessionId, SessionStatus.RUNNING)

                // 4. 启动流式处理
                currentStreamingSessionId = newSessionId
                isStreaming = true
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AI Agents", style = TextStyle(
                            fontWeight = FontWeight.Bold, fontSize = 20.sp
                        )
                    )
                }
            }, actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings, contentDescription = "设置"
                    )
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            )
        )
    }, bottomBar = {
        // 固定的输入框区域 - 首页不阻塞输入框
        ChatInput(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSendMessage = handleSendMessage, isLoading = false, isSending = false
        )
    }) { paddingValues ->
        Box(
            modifier = modifier.fillMaxSize().padding(paddingValues)
        ) {
            if (sessions.isEmpty()) {
                // 空状态
                EmptyHomeState()
            } else {
                // 会话卡片网格布局 - 自适应展示
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 280.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 根据流式输出状态确定卡片状态
                    val runningSessionsList = sessions.filter { session ->
                        runningSessions.contains(session.sessionId) || session.sessionId == currentStreamingSessionId
                    }
                    val completedSessionsList = sessions.filter { session ->
                        !runningSessions.contains(session.sessionId) && session.sessionId != currentStreamingSessionId
                    }

                    // 分别按时间倒序排序
                    val sortedRunningSessions = runningSessionsList.sortedByDescending { it.timestamp }
                    val sortedCompletedSessions = completedSessionsList.sortedByDescending { it.timestamp }

                    if (sortedRunningSessions.isNotEmpty()) {
                        items(sortedRunningSessions, key = { it.sessionId }) { session ->
                            SessionCard(
                                session = session,
                                sessionStatus = SessionStatus.RUNNING,
                                onClick = { onNavigateToSession(session.sessionId) },
                                onDelete = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        sessionManager.deleteSession(session.sessionId)
                                        sessionStateTracker.clearSessionState(session.sessionId)
                                        sessions = withContext(Dispatchers.IO) {
                                            sessionManager.loadSessions()
                                        }
                                    }
                                })
                        }
                    }

                    if (sortedCompletedSessions.isNotEmpty()) {

                        items(sortedCompletedSessions, key = { it.sessionId }) { session ->
                            SessionCard(
                                session = session,
                                sessionStatus = SessionStatus.IDLE,
                                onClick = { onNavigateToSession(session.sessionId) },
                                onDelete = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        sessionManager.deleteSession(session.sessionId)
                                        sessionStateTracker.clearSessionState(session.sessionId)
                                        sessions = withContext(Dispatchers.IO) {
                                            sessionManager.loadSessions()
                                        }
                                    }
                                })
                        }
                    }

                    // 查看更多（如果会话较多，可以分页加载）
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * 首页空状态
 */
@Composable
private fun EmptyHomeState() {
    val chatColors = getCurrentChatColors()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标
        Box(
            modifier = Modifier.size(100.dp).shadow(
                elevation = 15.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            ).clip(RoundedCornerShape(28.dp)).background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                    )
                )
            ), contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "开始您的对话", style = TextStyle(
                color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "在下方输入框输入消息开始会话", style = TextStyle(
                color = chatColors.textSecondary, fontSize = 14.sp
            )
        )
    }
}
