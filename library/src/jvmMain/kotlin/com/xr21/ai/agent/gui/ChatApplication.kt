package com.xr21.ai.agent.gui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.MouseInfo
import java.awt.PointerInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color

// ==================== 应用入口 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
@Preview
fun ChatApplication() {
    // 从 SettingsManager 加载设置
    val settingsManager = remember { SettingsManager }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var currentTheme by remember { mutableStateOf(settingsManager.getThemeMode()) }
    var homeSendMessageBehavior by remember { mutableStateOf(settingsManager.getHomeSendMessageBehavior()) }
    var currentModelSettings by remember { mutableStateOf(settingsManager.getModelSettings()) }
    var sessions by remember { mutableStateOf(listOf<UiSessionInfo>()) }

    val sessionManager = remember { FileSessionManager.getInstance() }
    val sessionStateTracker = remember { SessionStateTracker.getInstance() }
    val coroutineScope = rememberCoroutineScope()

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center), width = 1200.dp, height = 800.dp
    )

    // 初始化 AiModels 的默认模型设置
    LaunchedEffect(currentModelSettings) {
        if (!currentModelSettings.modelName.isNullOrEmpty() ||
            !currentModelSettings.baseUrl.isNullOrEmpty() ||
            !currentModelSettings.apiKey.isNullOrEmpty()) {
            com.xr21.ai.agent.config.AiModels.setDefaultModelSettings(
                com.xr21.ai.agent.config.AiModels.ModelSettings(
                    currentModelSettings.modelName,
                    currentModelSettings.baseUrl,
                    currentModelSettings.apiKey
                )
            )
        }
    }

    // 加载会话列表
    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) {
            sessionManager.loadSessions()
        }
    }

    // 刷新会话列表（当会话状态改变时）
    LaunchedEffect(sessionStateTracker.runningSessions.value) {
        sessions = withContext(Dispatchers.IO) {
            sessionManager.loadSessions()
        }
    }

    Window(
        onCloseRequest = { System.exit(0) },
        title = "AI Agents - 智能助手",
        state = windowState,
        undecorated = true,  // 隐藏系统默认标题栏
        resizable = true
    ) {
        // 窗口拖拽支持
        var isDragging by remember { mutableStateOf(false) }
        var dragOffset by remember { mutableStateOf(WindowPosition(0.dp, 0.dp)) }

        AppTheme(themeMode = currentTheme) {
            // 渐变背景 - 精致紫调
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        )
                    )
            ) {
                // 自定义标题栏（可拖拽区域）- 暖色调背景
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xBAFFF3E0).copy(alpha = 0.98f),  // 浅橙黄色
                                    Color(0xFFFBE9D7).copy(alpha = 0.95f),  // 淡橙色
                                    Color(0xFFF5E0D3).copy(alpha = 0.92f),  // 暖米色
                                    Color(0xBAFFF3E0).copy(alpha = 0.98f),  // 浅橙黄色
                                    Color(0xFFFBE9D7).copy(alpha = 0.95f),  // 淡橙色
                                    Color(0xFFF5E0D3).copy(alpha = 0.92f),  // 暖米色
                                    Color(0xFFF0D4C8).copy(alpha = 0.88f)   // 浅棕色
                                )
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    // 双击最大化/还原窗口
                                    val currentState = window.extendedState
                                    val isMaximized = (currentState and java.awt.Frame.MAXIMIZED_BOTH) != 0
                                    window.extendedState = if (isMaximized) {
                                        currentState and java.awt.Frame.MAXIMIZED_BOTH.inv()
                                    } else {
                                        currentState or java.awt.Frame.MAXIMIZED_BOTH
                                    }
                                }
                            )
                        }
                        .onPointerEvent(PointerEventType.Press) {
                            val window = window
                            val pointerInfo: PointerInfo = MouseInfo.getPointerInfo()
                            val mousePos = pointerInfo.location
                            val windowPos = window.x to window.y
                            dragOffset = WindowPosition(
                                (mousePos.x - windowPos.first).dp,
                                (mousePos.y - windowPos.second).dp
                            )
                            isDragging = true
                        }
                        .onPointerEvent(PointerEventType.Release) {
                            isDragging = false
                        }
                        .onPointerEvent(PointerEventType.Move) {
                            if (isDragging) {
                                val window = window
                                val pointerInfo: PointerInfo = MouseInfo.getPointerInfo()
                                val mousePos = pointerInfo.location
                                window.setLocation(
                                    (mousePos.x - dragOffset.x.value).toInt(),
                                    (mousePos.y - dragOffset.y.value).toInt()
                                )
                            }
                        }
                ) {
                    // 左边菜单栏 - 参考IDEA设计
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 设置按钮
                        IconButton(
                            onClick = { currentScreen = Screen.Settings },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "设置",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // 右边窗口控制按钮（最小化、最大化/还原、关闭）
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 最小化按钮 - 悬停效果
                        IconButton(
                            onClick = { window.isMinimized = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Filled.Remove,
                                contentDescription = "最小化",
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // 最大化/还原按钮 - 悬停效果
                        IconButton(
                            onClick = {
                                val currentState = window.extendedState
                                val isMaximized = (currentState and java.awt.Frame.MAXIMIZED_BOTH) != 0
                                window.extendedState = if (isMaximized) {
                                    currentState and java.awt.Frame.MAXIMIZED_BOTH.inv()
                                } else {
                                    currentState or java.awt.Frame.MAXIMIZED_BOTH
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.size(30.dp)
                        ) {
                            val currentState = window.extendedState
                            val isMaximized = (currentState and java.awt.Frame.MAXIMIZED_BOTH) != 0
                            Icon(
                                if (isMaximized) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (isMaximized) "还原" else "最大化",
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // 关闭按钮 - 红色悬停效果
                        IconButton(
                            onClick = { System.exit(0) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.error,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // 应用内容区域（从标题栏下方开始）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 32.dp)
                ) {
                    when (currentScreen) {
                        is Screen.Home -> {
                            HomeScreen(
                                sessionManager = sessionManager,
                                sessionStateTracker = sessionStateTracker,
                                homeSendMessageBehavior = homeSendMessageBehavior,
                                onNavigateToSession = { sessionId ->
                                    currentSessionId = sessionId
                                    currentScreen = Screen.SessionDetail
                                }
                            )
                        }

                        is Screen.SessionDetail -> {
                            SessionDetailScreen(
                                sessionId = currentSessionId!!,
                                sessionManager = sessionManager,
                                sessionStateTracker = sessionStateTracker,
                                sessions = sessions,
                                onNavigateBack = {
                                    currentScreen = Screen.Home
                                    currentSessionId = null
                                },
                                onSessionSwitched = { newSessionId ->
                                    currentSessionId = newSessionId
                                },
                                onNewSession = {
                                    coroutineScope.launch {
                                        val newId = sessionManager.createSession()
                                        sessionStateTracker.registerSession(newId)
                                        sessionStateTracker.updateSessionStatus(newId, SessionStatus.IDLE)
                                        currentSessionId = newId
                                    }
                                },
                                onOpenSettings = {
                                    currentScreen = Screen.Settings
                                }
                            )
                        }

                        is Screen.Settings -> {
                            SettingsScreen(
                                onBack = {
                                    currentScreen = when {
                                        currentSessionId != null -> Screen.SessionDetail
                                        else -> Screen.Home
                                    }
                                },
                                onThemeChange = { theme ->
                                    currentTheme = theme
                                    settingsManager.updateThemeMode(theme)
                                },
                                onClearHistory = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        sessionManager.clearAllSessions()
                                        sessionStateTracker.clearAll()
                                    }
                                },
                                currentTheme = currentTheme,
                                homeSendMessageBehavior = homeSendMessageBehavior,
                                onHomeSendMessageBehaviorChange = { behavior ->
                                    homeSendMessageBehavior = behavior
                                    settingsManager.updateHomeSendMessageBehavior(behavior)
                                },
                                currentModelSettings = currentModelSettings,
                                onModelSettingsChange = { settings ->
                                    currentModelSettings = settings
                                    settingsManager.updateModelSettings(settings)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class Screen {
    data object Home : Screen()
    data object SessionDetail : Screen()
    data object Settings : Screen()
}

// ==================== 主函数 ====================

fun main() {
    application {
        ChatApplication()
    }
}
