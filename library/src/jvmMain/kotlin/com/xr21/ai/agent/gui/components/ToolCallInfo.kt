package com.xr21.ai.agent.gui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.gui.ChatColors
import com.xr21.ai.agent.gui.getCurrentChatColors
import org.springframework.ai.chat.messages.AssistantMessage

/**
 * 工具调用信息展示组件
 * 展示格式如：读取文件：xxxx(路径)
 */
@Composable
fun ToolCallInfoDisplay(
    toolCalls: List<AssistantMessage.ToolCall>, modifier: Modifier = Modifier
) {
    if (toolCalls.isEmpty()) return

    val chatColors = getCurrentChatColors()

    Column(
        modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        toolCalls.forEach { toolCall ->
            ToolCallItem(toolCall = toolCall, chatColors = chatColors)
        }
    }
}

/**
 * 单个工具调用项展示
 */
@Composable
fun ToolCallItem(
    toolCall: AssistantMessage.ToolCall, chatColors: ChatColors, modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val toolIcon = when (toolCall.name) {
        "read_file" -> "📖"
        "write_file" -> "✏️"
        "edit_file" -> "🔧"
        "ls" -> "📁"
        "grep" -> "🔍"
        "glob" -> "🎯"
        "execute_terminal_command" -> "💻"
        "web_search" -> "🌐"
        "feed_back_tool" -> "📝"
        else -> "🔧"
    }

    val toolNameDisplay = when (toolCall.name) {
        "read_file" -> "读取文件"
        "write_file" -> "写入文件"
        "edit_file" -> "修改文件"
        "ls" -> "列出目录"
        "grep" -> "搜索内容"
        "glob" -> "查找文件"
        "execute_terminal_command" -> "执行命令"
        "web_search" -> "网络搜索"
        "feed_back_tool" -> "收集反馈"
        else -> toolCall.name
    }

    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .background(chatColors.toolCallBackgroundColor.copy(alpha = 0.3f)).clickable { expanded = !expanded }
        .padding(8.dp)) {
        // 工具调用头部信息
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = toolIcon, style = TextStyle(fontSize = 14.sp)
                )
                Text(
                    text = toolNameDisplay, style = TextStyle(
                        fontWeight = FontWeight.Medium, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = chatColors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        // 工具参数展示
        AnimatedVisibility(
            visible = expanded, enter = expandVertically(), exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                // 显示主要参数
                val parameters = parseToolArguments(toolCall.arguments)
                parameters.forEach { (key, value) ->
                    ToolParameterItem(key = key, value = value, chatColors = chatColors)
                }
            }
        }
    }
}

/**
 * 工具参数项展示
 */
@Composable
fun ToolParameterItem(
    key: String, value: String, chatColors: ChatColors, modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "• $key: ", style = TextStyle(
                fontWeight = FontWeight.Normal, fontSize = 11.sp, color = chatColors.textSecondary
            )
        )
        Text(
            text = value, style = TextStyle(
                fontWeight = FontWeight.Normal, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface
            ), modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 简化的工具调用摘要（用于消息气泡内展示）
 */
@Composable
fun ToolCallSummary(
    toolCalls: List<AssistantMessage.ToolCall>, modifier: Modifier = Modifier
) {
    if (toolCalls.isEmpty()) return

    val chatColors = getCurrentChatColors()

    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Code,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )

        Column {
            toolCalls.take(3).forEach { toolCall ->
                val toolNameDisplay = when (toolCall.name) {
                    "read_file" -> "读取"
                    "write_file" -> "写入"
                    "edit_file" -> "修改"
                    "ls" -> "列出"
                    "grep" -> "搜索"
                    "glob" -> "查找"
                    "execute_terminal_command" -> "执行"
                    "web_search" -> "搜索"
                    else -> toolCall.name
                }

                val parameters = parseToolArguments(toolCall.arguments)
                val displayText = buildString {
                    append(toolNameDisplay)
                    parameters.entries.firstOrNull()?.let { (key, value) ->
                        if (value.isNotBlank()) {
                            append(": ${value.take(50)}${if (value.length > 50) "..." else ""}")
                        }
                    }
                }

                Text(
                    text = displayText, style = TextStyle(
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (toolCalls.size > 3) {
                Text(
                    text = "... 及其他 ${toolCalls.size - 3} 个工具调用", style = TextStyle(
                        fontSize = 10.sp, color = chatColors.textSecondary
                    )
                )
            }
        }
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
