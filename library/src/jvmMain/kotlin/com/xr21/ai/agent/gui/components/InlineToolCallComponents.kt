package com.xr21.ai.agent.gui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.gui.getCurrentChatColors
import kotlinx.coroutines.delay
import org.springframework.ai.chat.messages.AssistantMessage

/**
 * 内联工具调用组件
 * 【优化】支持在 Markdown 文本流中即时渲染工具调用信息
 * 使用独立组件设计，支持丰富的动画效果和样式定制
 * 【优化】使用更紧凑的布局，自然嵌入消息中间
 * 【优化】根据是否有响应显示不同状态：
 * - 无响应时显示"执行中"
 * - 有响应时显示"执行完成"
 */
@Composable
fun InlineToolCall(
    toolCall: AssistantMessage.ToolCall,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    responseData: String? = null,
    isError: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    val hasResponse = responseData != null
    var expanded by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    // 入场动画
    LaunchedEffect(Unit) {
        delay(50)
        visible = true
    }

    val chatColors = getCurrentChatColors()
    val toolConfig = getToolConfig(toolCall.name)

    // 根据状态显示不同文本
    val statusText = when {
        isStreaming -> "执行中..."
        hasResponse -> if (isError) "❌ 执行失败" else "✅ 执行完成"
        else -> "🚀 发起调用"
    }

    val statusColor = when {
        isStreaming -> toolConfig.color
        hasResponse -> if (isError) Color(0xFFE53935) else Color(0xFF43A047)
        else -> toolConfig.color.copy(alpha = 0.8f)
    }

    AnimatedVisibility(
        visible = visible,
        label = "inline_tool_call_${toolCall.id}",
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        ) + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            expandFrom = Alignment.Top
        ),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.4f),
                            statusColor.copy(alpha = 0.15f),
                            statusColor.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(8.dp)
                ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = statusColor.copy(alpha = if (hasResponse || isError) 0.15f else 0.35f)
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                // 头部信息行（更紧凑的设计）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expanded = !expanded
                            onExpandChange(expanded)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 工具图标带动画
                        ToolIcon(
                            icon = toolConfig.icon,
                            color = statusColor,
                            isStreaming = isStreaming && !hasResponse,
                            compact = true
                        )

                        Column {
                            Text(
                                text = toolConfig.displayName,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "调用ID: ${toolCall.id.take(8)}...",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }

                    // 状态标签
                    Text(
                        text = statusText,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 10.sp,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // 动画展开的参数详情和响应内容
                AnimatedVisibility(
                    visible = expanded,
                    label = "tool_params_${toolCall.id}",
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 参数详情
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 参数标题
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = statusColor
                                )
                                Text(
                                    text = "参数详情",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.sp,
                                        color = statusColor
                                    )
                                )
                            }

                            // 参数列表
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val parameters = parseToolArguments(toolCall.arguments)
                                    parameters.forEach { (key, value) ->
                                        ParameterItem(key = key, value = value)
                                    }

                                    if (parameters.isEmpty()) {
                                        Text(
                                            text = "无参数",
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 响应内容（如果有）
                        if (hasResponse) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isError) Color(0xFFE53935) else Color(0xFF43A047)
                                    )
                                    Text(
                                        text = "执行结果",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 10.sp,
                                            color = if (isError) Color(0xFFE53935) else Color(0xFF43A047)
                                        )
                                    )
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .heightIn(max = 200.dp) // 限制最大高度
                                            .verticalScroll(rememberScrollState()) // 添加垂直滚动
                                    ) {
                                        Text(
                                            text = responseData?.ifBlank { "(无输出)" } ?: "",
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 流式状态指示（仅在执行中时显示）
                if (isStreaming && !hasResponse) {
                    Spacer(modifier = Modifier.height(6.dp))
                    StreamingIndicator(color = statusColor)
                }
            }
        }
    }
}

/**
 * 工具图标组件，带有呼吸动画效果
 * 【优化】支持紧凑模式，更适合内联显示
 */
@Composable
private fun ToolIcon(
    icon: ImageVector,
    color: Color,
    isStreaming: Boolean,
    compact: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "toolIcon")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconAlpha"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )
    
    val size = if (compact) 28.dp else 36.dp
    val iconSize = if (compact) 16.dp else 20.dp
    val backgroundAlpha = if (isStreaming) alpha else 0.15f
    
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = backgroundAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(iconSize)
                .then(
                    if (isStreaming) {
                        Modifier.scale(scale)
                    } else {
                        Modifier
                    }
                )
        )
    }
}

/**
 * 参数项组件
 */
@Composable
private fun ParameterItem(
    key: String,
    value: String
) {
    val chatColors = getCurrentChatColors()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatParameterKey(key),
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = value,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 流式指示器组件
 */
@Composable
private fun StreamingIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = dotAlpha))
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "执行中...",
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 10.sp,
                color = color
            )
        )
    }
}

/**
 * 工具配置信息
 */
private data class ToolConfig(
    val icon: ImageVector,
    val color: Color,
    val displayName: String
)

/**
 * 获取工具配置
 * 【优化】采用现代、协调的Material Design 3配色方案
 * - 使用更鲜明的色彩提升视觉吸引力
 * - 确保良好的对比度和可读性
 * - 语义化的颜色映射增强工具识别度
 */
@Composable
private fun getToolConfig(toolName: String): ToolConfig {
    return when (toolName) {
        // 文件读取操作 - 使用清新的绿色系，强调只读性质
        "read_file" -> ToolConfig(
            icon = Icons.Default.FileOpen,
            color = Color(0xFFF3F2F2),  // 深绿色，更专业的视觉感受
            displayName = "📖 读取文件"
        )

        // 文件写入操作 - 使用活力蓝色，强调创建和修改
        "write_file" -> ToolConfig(
            icon = Icons.Default.Edit,
            color = Color(0xFFF3F2F2),  // 标准蓝色，清晰醒目
            displayName = "✏️ 写入文件"
        )

        // 文件编辑操作 - 使用温暖的琥珀色，突出精细操作
        "edit_file" -> ToolConfig(
            icon = Icons.Default.Build,
            color = Color(0xFFF3F2F2),  // 琥珀色，视觉温暖
            displayName = "🔧 修改文件"
        )

        // 目录浏览操作 - 使用柔和的棕色系，自然感
        "ls" -> ToolConfig(
            icon = Icons.Default.Folder,
            color = Color(0xFFF3F2F2),  // 柔和棕色，文件夹意象
            displayName = "📁 列出目录"
        )

        // 文本搜索操作 - 使用品红色，强调精准定位
        "grep" -> ToolConfig(
            icon = Icons.Default.Search,
            color = Color(0xFFF3F2F2),  // 深品红，精准搜索
            displayName = "🔍 搜索内容"
        )

        // 文件模式匹配 - 使用青色调，独特且易于区分
        "glob" -> ToolConfig(
            icon = Icons.Default.Search,
            color = Color(0xFFF3F2F2),  // 青绿色，独特标识
            displayName = "🎯 查找文件"
        )

        // 终端命令执行 - 使用深灰色，专业且冷静
        "execute_terminal_command" -> ToolConfig(
            icon = Icons.Default.Terminal,
            color = Color(0xFFF3F2F2),  // 蓝灰色，专业终端感
            displayName = "💻 执行命令"
        )

        // 网络搜索操作 - 使用深邃靛蓝，网络意象
        "web_search" -> ToolConfig(
            icon = Icons.Default.Language,
            color = Color(0xFFF3F2F2),  // 深靛蓝，网络连接
            displayName = "🌐 网络搜索"
        )

        // 用户反馈收集 - 使用柔和的紫灰色，温和亲切
        "feed_back_tool" -> ToolConfig(
            icon = Icons.Default.Feedback,
            color = Color(0xFFF3F2F2),  // 柔和棕色，反馈交互
            displayName = "📝 收集反馈"
        )

        // 默认配置 - 使用主题主色，保持一致性
        else -> ToolConfig(
            icon = Icons.Default.Build,
            color = MaterialTheme.colorScheme.primary,
            displayName = "🔧 $toolName"
        )
    }
}

/**
 * 格式化参数键名
 */
private fun formatParameterKey(key: String): String {
    return key.replace("_", " ")
        .split(" ")
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

/**
 * 解析工具调用参数
 */
private fun parseToolArguments(arguments: String?): Map<String, String> {
    if (arguments.isNullOrBlank()) return emptyMap()

    return try {
        val result = mutableMapOf<String, String>()
        // 简单解析JSON参数
        val keyValuePattern = Regex(""""([^"]*)":\s*"([^"]*)"""").findAll(arguments)
        for (match in keyValuePattern) {
            val key = match.groupValues[1]
            val value = match.groupValues[2].replace("\\n", "\n").replace("\\\"", "\"")
            result[key] = value
        }
        result
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * 工具调用动画容器
 * 用于在消息流中管理多个工具调用的动画
 */
@Composable
fun ToolCallAnimationContainer(
    toolCalls: List<AssistantMessage.ToolCall>,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    content: @Composable (toolCall: AssistantMessage.ToolCall, index: Int) -> Unit
) {
    Column(modifier = modifier) {
        toolCalls.forEachIndexed { index, toolCall ->
            content(toolCall, index)
        }
    }
}
