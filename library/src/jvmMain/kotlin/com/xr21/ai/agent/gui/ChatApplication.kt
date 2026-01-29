package com.xr21.ai.agent.gui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================== 应用入口 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun ChatApplication() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var currentTheme by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var homeSendMessageBehavior by remember { mutableStateOf(HomeSendMessageBehavior.REFRESH_LIST) }
    var sessions by remember { mutableStateOf(listOf<UiSessionInfo>()) }

    val sessionManager = remember { FileSessionManager.getInstance() }
    val sessionStateTracker = remember { SessionStateTracker.getInstance() }
    val coroutineScope = rememberCoroutineScope()

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center), width = 1200.dp, height = 800.dp
    )

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
        onCloseRequest = { /* 退出 */ },
        title = "AI Agents - 智能助手",
        state = windowState
    ) {
        AppTheme(themeMode = currentTheme) {
            // 磨砂玻璃主背景
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    )
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
                            },
                            onOpenSettings = {
                                currentScreen = Screen.Settings
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
                            }
                        )
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
