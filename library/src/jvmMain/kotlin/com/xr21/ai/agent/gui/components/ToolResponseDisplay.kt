package com.xr21.ai.agent.gui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.gui.getCurrentChatColors
import kotlinx.coroutines.delay
import org.springframework.ai.chat.messages.ToolResponseMessage

/**
 * 单个工具响应项
 */
@Composable
private fun ToolResponseItem(
    response: ToolResponseMessage.ToolResponse,
    index: Int,
    total: Int
) {
    var expanded by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val itemKey = "tool_response_${response.id()}"

    // 入场动画
    LaunchedEffect(itemKey) {
        delay(index * 100L) // 错开动画时间
        visible = true
    }

    val chatColors = getCurrentChatColors()
    val toolConfig = getToolResponseConfig(response.name())
    val isError = response.responseData()?.contains("error", ignoreCase = true) == true

    AnimatedVisibility(
        visible = visible,
        label = itemKey,
        enter = fadeIn(
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            expandFrom = Alignment.Top
        ),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            if (isError) Color(0xFFE53935).copy(alpha = 0.3f)
                            else toolConfig.color.copy(alpha = 0.3f),
                            if (isError) Color(0xFFE53935).copy(alpha = 0.1f)
                            else toolConfig.color.copy(alpha = 0.1f),
                            if (isError) Color(0xFFE53935).copy(alpha = 0.3f)
                            else toolConfig.color.copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(8.dp)
                ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isError) {
                    Color(0xFFE53935).copy(alpha = 0.1f)
                } else {
                    chatColors.toolCallBackgroundColor.copy(alpha = 0.3f)
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                // 头部信息行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 状态图标
                        ToolResponseIcon(
                            isError = isError,
                            isExpanded = expanded
                        )

                        Column {
                            Text(
                                text = if (isError) "❌ ${toolConfig.displayName} 失败" else "✅ ${toolConfig.displayName}",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = if (isError) Color(0xFFE53935)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "执行ID: ${response.id().take(8)}...",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }

                    // 展开/收起指示器
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = chatColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 动画展开的响应内容
                AnimatedVisibility(
                    visible = expanded,
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 响应内容标题
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (isError) Color(0xFFE53935) else toolConfig.color
                            )
                            Text(
                                text = "执行结果",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                    color = if (isError) Color(0xFFE53935) else toolConfig.color
                                )
                            )
                        }

                        // 响应内容卡片
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = response.responseData().ifBlank { "(无输出)" },
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                modifier = Modifier.padding(8.dp),
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 工具响应状态图标
 */
@Composable
private fun ToolResponseIcon(
    isError: Boolean,
    isExpanded: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "responseIcon")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconAlpha"
    )

    val backgroundColor = if (isError) {
        Color(0xFFE53935).copy(alpha = 0.2f)
    } else {
        Color(0xFF43A047).copy(alpha = 0.2f)
    }

    val iconColor = if (isError) {
        Color(0xFFE53935)
    } else {
        Color(0xFF43A047)
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
            contentDescription = if (isError) "失败" else "成功",
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 工具响应配置信息
 */
 data class ToolResponseConfig(
    val icon: ImageVector,
    val color: Color,
    val displayName: String
)

/**
 * 获取工具响应配置
 */
@Composable
private fun getToolResponseConfig(toolName: String): ToolResponseConfig {
    return when (toolName) {
        "read_file" -> ToolResponseConfig(
            icon = Icons.Default.FileOpen,
            color = Color(0xFF43A047),
            displayName = "读取文件"
        )
        "write_file" -> ToolResponseConfig(
            icon = Icons.Default.Edit,
            color = Color(0xFF1E88E5),
            displayName = "写入文件"
        )
        "edit_file" -> ToolResponseConfig(
            icon = Icons.Default.Build,
            color = Color(0xFFFFA000),
            displayName = "修改文件"
        )
        "ls" -> ToolResponseConfig(
            icon = Icons.Default.Folder,
            color = Color(0xFF8D6E63),
            displayName = "列出目录"
        )
        "grep" -> ToolResponseConfig(
            icon = Icons.Default.Search,
            color = Color(0xFFD81B60),
            displayName = "搜索内容"
        )
        "glob" -> ToolResponseConfig(
            icon = Icons.Default.Search,
            color = Color(0xFF00ACC1),
            displayName = "查找文件"
        )
        "execute_terminal_command" -> ToolResponseConfig(
            icon = Icons.Default.Terminal,
            color = Color(0xFF546E7A),
            displayName = "执行命令"
        )
        "web_search" -> ToolResponseConfig(
            icon = Icons.Default.Language,
            color = Color(0xFF5E35B1),
            displayName = "网络搜索"
        )
        "feed_back_tool" -> ToolResponseConfig(
            icon = Icons.Default.Feedback,
            color = Color(0xFF6D4C41),
            displayName = "收集反馈"
        )
        else -> ToolResponseConfig(
            icon = Icons.Default.Build,
            color = MaterialTheme.colorScheme.primary,
            displayName = toolName
        )
    }
}

