package com.xr21.ai.agent.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.entity.AgentOutput
import com.xr21.ai.agent.gui.components.ChatInput
import com.xr21.ai.agent.gui.components.ConversationMessageBubble
import com.xr21.ai.agent.gui.components.LoadingIndicator
import com.xr21.ai.agent.gui.model.ConversationMessage
import com.xr21.ai.agent.gui.model.MessageAggregator
import com.xr21.ai.agent.gui.model.StreamingMessageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.messages.UserMessage
import java.util.*

/**
 * 会话详情页组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    sessionManager: FileSessionManager,
    sessionStateTracker: SessionStateTracker,
    sessions: List<UiSessionInfo>,
    onNavigateBack: () -> Unit,
    onSessionSwitched: (String) -> Unit,
    onNewSession: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var messages by remember { mutableStateOf(mutableStateListOf<ConversationMessage>()) }
    val isLoading = remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    val isStreaming = remember { mutableStateOf(false) }
    var currentJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastSessionId by remember { mutableStateOf<String?>(null) }
    var showSessionDropdown by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val chatService = remember { ChatService.getInstance() }
    val streamingProcessor = remember { StreamingMessageProcessor.getInstance() }
    val messageAggregator = remember { MessageAggregator.getInstance() }

    // 当前会话状态
    val sessionState = sessionStateTracker.getSessionState(sessionId) ?: SessionStatus.IDLE

    // 收集运行中的会话
    val runningSessions by sessionStateTracker.runningSessions.collectAsState()

    // 检查会话是否正在流式传输，如果是，订阅共享流
    val isSubscribingToExistingStream = remember { mutableStateOf(false) }
    val subscriptionJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val enterCount = remember { mutableStateOf(0L) }
    val messagesReady = remember { mutableStateOf(false) }
    val processedOutputs = remember { mutableSetOf<String>() }

    // 加载会话消息并标记就绪
    LaunchedEffect(sessionId) {
        if (sessionId != lastSessionId) {
            messages.clear()
            processedOutputs.clear() // 清空去重集合，确保新会话的消息不会受影响
            val rawMessages = withContext(Dispatchers.IO) {
                sessionManager.loadMessages(sessionId)
            }
            val aggregated = messageAggregator.aggregate(rawMessages)
            messages.addAll(aggregated)
            lastSessionId = sessionId
            messagesReady.value = true
        }
    }

    // 进入时立即订阅共享流（使用 enterCount 确保每次进入都触发）
    LaunchedEffect(sessionId, messagesReady.value) {
        if (!messagesReady.value) return@LaunchedEffect


        // 注册会话
        sessionStateTracker.registerSession(sessionId)

        // 检查会话是否正在流式传输，如果不是，则不订阅
        val sessionState = chatService.getSessionStreamingState(sessionId)
        if (sessionState != null && !sessionState.isStreaming) {
            // 流已经结束，不需要再订阅
            isLoading.value = false
            isStreaming.value = false
            return@LaunchedEffect
        }

        // 等待流就绪（最多等待 2 秒）
        var mutableFlow: MutableSharedFlow<AgentOutput<*>>? = null
        var waitCount = 0
        while (mutableFlow == null && waitCount < 20) {
            mutableFlow = chatService.getSessionMutableFlow(sessionId)
            if (mutableFlow == null) {
                delay(100)
                waitCount++
            }
        }

        if (mutableFlow != null) {
            // 有活跃的流，订阅它
            isSubscribingToExistingStream.value = true
            isLoading.value = true
            isStreaming.value = true

            // 重置 streamingProcessor，避免从首页带来的残留状态
            streamingProcessor.reset()

            // 获取会话开始时间（用户消息的 timestamp）
            val sessionStartTime = messages
                .filterIsInstance<ConversationMessage.User>()
                .maxOfOrNull { it.timestamp } ?: 0L

            // 订阅 SharedFlow
            subscriptionJob.value?.cancel()
            subscriptionJob.value = scope.launch {
                println("=== Starting to collect from SharedFlow ===")
                mutableFlow.collect { output ->
                    println("=== Received output ===")
                    println("  chunk: ${output.chunk}")
                    println("  message: ${output.message}")
                    println("  metadata: ${output.metadata}")
                    println("  timestamp: ${output.timestamp}")

                    // 检查是否是当前会话的输出
                    val isThinking = output.metadata?.containsKey("reasoningContent") == true
                    val isCurrentSessionThinking = if (isThinking) {
                        val outputSessionId = output.metadata?.get("session_id")?.toString()
                        outputSessionId.isNullOrEmpty() || outputSessionId == sessionId
                    } else {
                        true
                    }

                    // 对于 thinking 输出，只验证 session_id；对于其他输出，验证 timestamp
                    val shouldProcess = if (isThinking) {
                        isCurrentSessionThinking
                    } else {
                        output.timestamp >= sessionStartTime
                    }

                    if (shouldProcess) {
                        println("=== Processing output ===")
                        println("  messages before update: ${messages.size} items")

                        val updates = streamingProcessor.processAgentOutput(output, messages)
                        println("  updates: ${updates.size} items")

                        updates.forEach { updatedMsg ->
                            println("  Update type: ${if (messages.any { it.id == updatedMsg.id }) "Update" else "Add"}")
                            println("  Update id: ${updatedMsg.id}")
                            if (updatedMsg is ConversationMessage.Assistant) {
                                println("  Update text: ${updatedMsg.text}")
                                println("  Update raw messages count: ${updatedMsg.rawMessages.size}")
                            }

                            val existingIndex = messages.indexOfFirst { it.id == updatedMsg.id }
                            if (existingIndex >= 0) {
                                messages[existingIndex] = updatedMsg
                            } else {
                                messages.add(updatedMsg)
                            }
                        }

                        println("  messages after update: ${messages.size} items")
                        messages.forEachIndexed { index, msg ->
                            println("  $index: ${msg.javaClass.simpleName}")
                            if (msg is ConversationMessage.Assistant) {
                                println("    text: ${msg.text}")
                            }
                        }

                        // 检查是否流结束
                        val finishReason = output.metadata?.get("finishReason") as? String
                        val isFinal = output.metadata?.get("final") == true
                        val hasError = output.metadata?.get("error") == true

                        if (finishReason == "STOP" || finishReason == "COMPLETED" || isFinal || hasError) {
                            isStreaming.value = false
                            isLoading.value = false
                            sessionStateTracker.updateSessionStatus(sessionId, SessionStatus.COMPLETED)
                        } else {
                            isStreaming.value = streamingProcessor.getCurrentStreamingMessage() != null
                        }

                        try {
                            listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
        } else {
            // 没有活跃的流，检查状态
            val state = chatService.getSessionStreamingState(sessionId)
            if (state != null && state.hasStarted) {
                // 流曾经开始过但已结束
                isLoading.value = false
                isStreaming.value = false
            }
        }
    }

    // 监听流式状态变化
    val streamingState by chatService.streamingState.collectAsState()
    LaunchedEffect(streamingState[sessionId]?.isStreaming) {
        val state = streamingState[sessionId]
        // 如果流已结束但 UI 还在流式，重置
        if (state != null && !state.isStreaming && isStreaming.value) {
            delay(100)
            isStreaming.value = false
            isLoading.value = false
            if (isSubscribingToExistingStream.value) {
                isSubscribingToExistingStream.value = false
            }
        }
    }

    val onCancelRequest = {
        currentJob?.cancel()
        isLoading.value = false
        isSending = false
        isStreaming.value = false
        streamingProcessor.reset()
        chatService.cancelSessionProcessing(sessionId)
        if (!isSubscribingToExistingStream.value) {
            sessionStateTracker.updateSessionStatus(sessionId, SessionStatus.IDLE)
        } else {
            sessionStateTracker.updateSessionStatus(sessionId, SessionStatus.COMPLETED)
            isSubscribingToExistingStream.value = false
        }
    }

    // 发送消息函数
    fun sendMessage(userInput: String) {
        isSubscribingToExistingStream.value = false
        // 取消可能存在的 SharedFlow 订阅，避免双重消费
        subscriptionJob.value?.cancel()
        subscriptionJob.value = null

        currentJob = scope.launch {
            isSending = true
            isLoading.value = true
            isStreaming.value = false
            streamingProcessor.reset()

            sessionStateTracker.updateSessionStatus(sessionId, SessionStatus.RUNNING)

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
            } catch (_: Throwable) {
            }

            // 发送消息并处理流式响应
            try {
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

                    // 检查流式状态
                    val isFinal = output.metadata?.get("final") == true
                    val finishReason = output.metadata?.get("finishReason") as? String
                    val hasError = output.metadata?.get("error") == true

                    if (isFinal || finishReason == "STOP" || finishReason == "COMPLETED" || hasError) {
                        isStreaming.value = false
                        isLoading.value = false
                        sessionStateTracker.updateSessionStatus(sessionId, SessionStatus.COMPLETED)
                    } else {
                        isStreaming.value = streamingProcessor.getCurrentStreamingMessage() != null
                    }

                    try {
                        listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                    } catch (_: Throwable) {
                    }
                }
            } catch (e: Exception) {
                // 处理异常
                isStreaming.value = false
                isLoading.value = false
                isSending = false
                streamingProcessor.reset()
                sessionStateTracker.updateSessionStatus(sessionId, SessionStatus.COMPLETED)
            } finally {
                // 流式结束，重置状态
                isStreaming.value = false
                isLoading.value = false
                isSending = false
                streamingProcessor.reset()
                sessionStateTracker.updateSessionStatus(sessionId, SessionStatus.COMPLETED)
            }

            if (messages.isNotEmpty()) {
                try {
                    listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                } catch (_: Throwable) {
                }
            }
        }
    }

    val handleSendMessage = {
        val userInput = inputText.text
        if (userInput.isNotBlank() && !isLoading.value && !isSending && sessionState != SessionStatus.RUNNING) {
            sendMessage(userInput)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 会话切换下拉框
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showSessionDropdown = true }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = sessions.find { it.sessionId == sessionId }?.briefDescription?.ifEmpty { "新会话" }
                                    ?: "会话",
                                style = TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 下拉菜单
                        DropdownMenu(
                            expanded = showSessionDropdown,
                            onDismissRequest = { showSessionDropdown = false }
                        ) {
                            // 优先展示运行中的会话
                            val runningSessionList = sessions.filter { runningSessions.contains(it.sessionId) }
                            val otherSessions = sessions.filter { !runningSessions.contains(it.sessionId) }

                            if (runningSessionList.isNotEmpty()) {
                                Text(
                                    text = "运行中",
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                runningSessionList.forEach { session ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (session.sessionId == sessionId) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Text(
                                                    text = session.briefDescription.ifEmpty { "新会话" },
                                                    maxLines = 1
                                                )
                                            }
                                        },
                                        onClick = {
                                            if (session.sessionId != sessionId) {
                                                onSessionSwitched(session.sessionId)
                                            }
                                            showSessionDropdown = false
                                        },
                                        leadingIcon = {
                                            if (runningSessions.contains(session.sessionId)) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            if (otherSessions.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = "历史会话",
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                otherSessions.forEach { session ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (session.sessionId == sessionId) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Text(
                                                    text = session.briefDescription.ifEmpty { "新会话" },
                                                    maxLines = 1
                                                )
                                            }
                                        },
                                        onClick = {
                                            if (session.sessionId != sessionId) {
                                                onSessionSwitched(session.sessionId)
                                            }
                                            showSessionDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回首页"
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            // 输入框（根据会话状态控制）
            ChatInput(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSendMessage = handleSendMessage,
                onStop = onCancelRequest,
                isLoading = isLoading.value || sessionState == SessionStatus.RUNNING,
                isSending = isSending
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                )
        ) {
            if (messages.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无消息",
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { msg -> msg.id }) { message ->
                        val messageIndex = messages.indexOf(message)

                        ConversationMessageBubble(
                            message = message,
                            onDelete = {
                                val messagesToKeep = messages.subList(0, messageIndex).toMutableList()
                                messages.clear()
                                messages.addAll(messagesToKeep)
                            },
                            onCopy = { },
                            onRetry = {
                                when (message) {
                                    is ConversationMessage.User -> {
                                        val messagesToKeep = messages.subList(0, messageIndex).toMutableList()
                                        messages.clear()
                                        messages.addAll(messagesToKeep)
                                        sendMessage(message.content)
                                    }

                                    is ConversationMessage.Assistant -> {
                                        val messagesToKeep = messages.subList(0, messageIndex).toMutableList()
                                        messages.clear()
                                        messages.addAll(messagesToKeep)
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
                            isStreaming = isStreaming.value && message.id == streamingProcessor.getCurrentStreamingMessage()?.id
                        )
                    }
                }

            }

            // 自动滚动到最新消息
            LaunchedEffect(messages.size, messages.lastOrNull()?.id) {
                if (messages.isNotEmpty()) {
                    try {
                        listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
