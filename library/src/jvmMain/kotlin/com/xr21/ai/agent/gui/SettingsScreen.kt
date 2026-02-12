package com.xr21.ai.agent.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 首页发送消息后的行为配置
 */
enum class HomeSendMessageBehavior {
    REFRESH_LIST,      // 刷新卡片列表
    NAVIGATE_TO_SESSION  // 进入会话详情页
}

/**
 * 设置界面 - macOS/iOS 磨砂玻璃风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onClearHistory: () -> Unit,
    currentTheme: ThemeMode,
    homeSendMessageBehavior: HomeSendMessageBehavior = HomeSendMessageBehavior.REFRESH_LIST,
    onHomeSendMessageBehaviorChange: ((HomeSendMessageBehavior) -> Unit)? = null,
    onModelSettingsChange: ((ModelSettings) -> Unit)? = null,
    currentModelSettings: ModelSettings = ModelSettings()
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showBehaviorDialog by remember { mutableStateOf(false) }
    var showModelSettingsDialog by remember { mutableStateOf(false) }
    var autoSaveEnabled by remember { mutableStateOf(true) }
    val currentBehavior = homeSendMessageBehavior

    val chatColors = getCurrentChatColors()

    Scaffold(
        topBar = {
            // 磨砂玻璃顶部栏
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "设置",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 外观设置
            item {
                SettingsSection(title = "外观")
            }

            item {
                // 磨砂玻璃主题设置项
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.1f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.glassGradientStart,
                                    chatColors.glassGradientEnd.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.borderColor.copy(alpha = 0.3f),
                                    chatColors.borderColor.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { showThemeDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "主题",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = when (currentTheme) {
                                    ThemeMode.LIGHT -> "亮色"
                                    ThemeMode.DARK -> "暗色"
                                    ThemeMode.SYSTEM -> "跟随系统"
                                },
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = chatColors.textSecondary
                        )
                    }
                }
            }

            // 聊天设置
            item {
                SettingsSection(title = "聊天")
            }

            item {
                // 磨砂玻璃开关设置项
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.1f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.glassGradientStart,
                                    chatColors.glassGradientEnd.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.borderColor.copy(alpha = 0.3f),
                                    chatColors.borderColor.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自动保存",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "聊天结束后自动保存会话记录",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Switch(
                            checked = autoSaveEnabled,
                            onCheckedChange = { autoSaveEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            // 模型设置
            item {
                SettingsSection(title = "模型")
            }

            item {
                // 磨砂玻璃模型设置项
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.1f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.glassGradientStart,
                                    chatColors.glassGradientEnd.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.borderColor.copy(alpha = 0.3f),
                                    chatColors.borderColor.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { showModelSettingsDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "模型设置",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = if (currentModelSettings.modelName.isNotBlank()) currentModelSettings.modelName else "使用默认配置",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = chatColors.textSecondary
                        )
                    }
                }
            }

            // 首页发送消息行为设置
            item {
                SettingsSection(title = "首页")
            }

            item {
                // 磨砂玻璃行为选择设置项
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.1f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.glassGradientStart,
                                    chatColors.glassGradientEnd.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.borderColor.copy(alpha = 0.3f),
                                    chatColors.borderColor.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { showBehaviorDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "发送消息后行为",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = when (currentBehavior) {
                                    HomeSendMessageBehavior.REFRESH_LIST -> "刷新卡片列表"
                                    HomeSendMessageBehavior.NAVIGATE_TO_SESSION -> "进入会话详情页"
                                },
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = chatColors.textSecondary
                        )
                    }
                }
            }

            // 数据管理
            item {
                SettingsSection(title = "数据")
            }

            item {
                // 磨砂玻璃危险操作设置项
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            spotColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { showClearDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "清除历史记录",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "删除所有会话历史记录",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            item {
                // 导出设置项
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.1f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.glassGradientStart,
                                    chatColors.glassGradientEnd.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.borderColor.copy(alpha = 0.3f),
                                    chatColors.borderColor.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { /* TODO: 导出功能 */ }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "导出所有会话",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "将所有会话导出为 JSON 文件",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = chatColors.textSecondary
                        )
                    }
                }
            }

            // 关于
            item {
                SettingsSection(title = "关于")
            }

            item {
                // 版本信息
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.1f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.glassGradientStart,
                                    chatColors.glassGradientEnd.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.borderColor.copy(alpha = 0.3f),
                                    chatColors.borderColor.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "版本",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "1.0.0",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }

            item {
                // 开源许可
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.1f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.glassGradientStart,
                                    chatColors.glassGradientEnd.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    chatColors.borderColor.copy(alpha = 0.3f),
                                    chatColors.borderColor.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { /* TODO: 开源许可页面 */ }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "开源许可",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "查看开源协议",
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = chatColors.textSecondary
                        )
                    }
                }
            }
        }
    }

    // 主题选择对话框
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { theme ->
                onThemeChange(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // 清除确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("确认清除")
            },
            text = {
                Text("此操作将删除所有会话历史记录，且无法撤销。是否继续？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 首页发送消息行为选择对话框
    if (showBehaviorDialog) {
        AlertDialog(
            onDismissRequest = { showBehaviorDialog = false },
            title = {
                Text("选择发送消息后行为")
            },
            text = {
                Column {
                    BehaviorOption(
                        title = "刷新卡片列表",
                        description = "发送消息后停留在首页，刷新会话卡片列表",
                        selected = currentBehavior == HomeSendMessageBehavior.REFRESH_LIST,
                        onClick = {
                            onHomeSendMessageBehaviorChange?.invoke(HomeSendMessageBehavior.REFRESH_LIST)
                            showBehaviorDialog = false
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BehaviorOption(
                        title = "进入会话详情页",
                        description = "发送消息后自动跳转到该会话的详情页",
                        selected = currentBehavior == HomeSendMessageBehavior.NAVIGATE_TO_SESSION,
                        onClick = {
                            onHomeSendMessageBehaviorChange?.invoke(HomeSendMessageBehavior.NAVIGATE_TO_SESSION)
                            showBehaviorDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBehaviorDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 模型设置对话框
    if (showModelSettingsDialog) {
        ModelSettingsDialog(
            currentSettings = currentModelSettings,
            onSettingsSaved = { settings ->
                onModelSettingsChange?.invoke(settings)
                showModelSettingsDialog = false
            },
            onDismiss = { showModelSettingsDialog = false }
        )
    }
}

/**
 * 设置分组标题
 */
@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        style = TextStyle(
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        ),
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

/**
 * 主题选择对话框
 */
@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择主题")
        },
        text = {
            Column {
                ThemeOption(
                    title = "亮色",
                    description = "使用浅色背景",
                    selected = currentTheme == ThemeMode.LIGHT,
                    onClick = { onThemeSelected(ThemeMode.LIGHT) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ThemeOption(
                    title = "暗色",
                    description = "使用深色背景",
                    selected = currentTheme == ThemeMode.DARK,
                    onClick = { onThemeSelected(ThemeMode.DARK) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ThemeOption(
                    title = "跟随系统",
                    description = "根据系统设置自动切换",
                    selected = currentTheme == ThemeMode.SYSTEM,
                    onClick = { onThemeSelected(ThemeMode.SYSTEM) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 主题选项
 */
@Composable
fun ThemeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = description,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            )
        }
    }
}

/**
 * 行为选项
 */
@Composable
fun BehaviorOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = description,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            )
        }
    }
}

/**
 * 模型设置对话框
 */
@Composable
fun ModelSettingsDialog(
    currentSettings: ModelSettings,
    onSettingsSaved: (ModelSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var modelName by remember { mutableStateOf(currentSettings.modelName) }
    var baseUrl by remember { mutableStateOf(currentSettings.baseUrl) }
    var apiKey by remember { mutableStateOf(currentSettings.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("模型设置")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "配置 AI 模型的连接参数。留空则使用默认环境变量配置。",
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                )

                // 模型名称输入
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称") },
                    placeholder = { Text("例如: gpt-4, deepseek-chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Base URL 输入
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("例如: https://api.openai.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // API Key 输入
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("输入您的 API Key") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "隐藏" else "显示"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSettingsSaved(ModelSettings(
                        modelName = modelName.trim(),
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim()
                    ))
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (modelName.isNotBlank() || baseUrl.isNotBlank() || apiKey.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onSettingsSaved(ModelSettings())
                        }
                    ) {
                        Text("重置为默认", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

/**
 * 清除所有会话历史
 */
