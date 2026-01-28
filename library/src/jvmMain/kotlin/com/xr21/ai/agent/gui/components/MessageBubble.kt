package com.xr21.ai.agent.gui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.gui.components.markdown.renderer.MarkdownContent
import com.xr21.ai.agent.gui.getCurrentChatColors
import com.xr21.ai.agent.gui.model.ConversationMessage
import com.xr21.ai.agent.gui.model.isUser
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.messages.ToolResponseMessage

/**
 * 消息操作栏组件
 */
@Composable
fun MessageActionBar(
    message: Message,
    showActionBar: Boolean,
    onToggleActionBar: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit
) {
    val chatColors = getCurrentChatColors()
    val clipboardManager = LocalClipboardManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val messageContent = message.text ?: ""
    // 尝试从metadata获取时间戳
    val timestamp = message.metadata["timestamp"]?.toString() ?: ""

    Column {
        // 操作栏（时间 + 展开按钮）
        Row(
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp).clickable(
                interactionSource = interactionSource, indication = rememberRipple(bounded = true)
            ) { onToggleActionBar() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (timestamp.isNotEmpty()) {
                Text(
                    text = timestamp, style = TextStyle(
                        color = chatColors.textSecondary, fontSize = 11.sp
                    )
                )
            }

            Text(
                text = if (showActionBar) "收起 ▲" else "更多 ▼", style = TextStyle(
                    color = chatColors.textSecondary, fontSize = 11.sp
                )
            )
        }

        // 操作按钮组
        if (showActionBar) {
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 删除按钮
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(
                        interactionSource = interactionSource, indication = rememberRipple(bounded = true)
                    ) {
                        onDelete()
                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "删除", style = TextStyle(
                            color = MaterialTheme.colorScheme.error, fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 复制按钮
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(
                        interactionSource = interactionSource, indication = rememberRipple(bounded = true)
                    ) {
                        clipboardManager.setText(AnnotatedString(messageContent))
                        onCopy()
                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier.size(14.dp),
                        tint = chatColors.textSecondary
                    )
                    Text(
                        text = "复制", style = TextStyle(
                            color = chatColors.textSecondary, fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 重试按钮
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(
                        interactionSource = interactionSource, indication = rememberRipple(bounded = true)
                    ) {
                        onRetry()
                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "重试",
                        modifier = Modifier.size(14.dp),
                        tint = chatColors.textSecondary
                    )
                    Text(
                        text = "重试", style = TextStyle(
                            color = chatColors.textSecondary, fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    onRetry: () -> Unit = {},
    isStreaming: Boolean = false
) {
    val isUser = message.messageType == MessageType.USER
    val isError = message.metadata["is_error"] == true
    val chatColors = getCurrentChatColors()
    var showActionBar by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val messageContent = message.text ?: ""
    val toolCalls: List<AssistantMessage.ToolCall> = when (message) {
        is AssistantMessage -> message.toolCalls
        else -> emptyList()
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.widthIn(max = 600.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
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
                // 发送者名称
                Text(
                    text = when (message.messageType) {
                        MessageType.USER -> "你"
                        MessageType.ASSISTANT -> "AI"
                        MessageType.SYSTEM -> "系统"
                        else -> "其他"
                    }, style = TextStyle(
                        color = when (message.messageType) {
                            MessageType.USER -> MaterialTheme.colorScheme.primary
                            MessageType.ASSISTANT -> MaterialTheme.colorScheme.secondary
                            MessageType.SYSTEM -> chatColors.systemMessageColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }, fontSize = 11.sp, fontWeight = FontWeight.Medium
                    ), modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
                )

                // 消息气泡
                Surface(
                    color = when {
                        isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        message.messageType == MessageType.USER -> chatColors.assistantMessageColor
                        message.messageType == MessageType.ASSISTANT -> chatColors.assistantMessageColor
                        message.messageType == MessageType.SYSTEM -> chatColors.systemMessageColor.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }, shape = RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ), modifier = Modifier.widthIn(max = 600.dp).combinedClickable(
                        interactionSource = interactionSource,
                        indication = rememberRipple(bounded = true),
                        onClick = { showActionBar = !showActionBar })
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        // 【优化】在消息中间展示工具调用信息，文本内容在工具调用前后显示
                        if (toolCalls.isNotEmpty()) {
                            // 工具调用前的文本内容（如果有的话）
                            val preToolContent = extractPreToolContent(messageContent)
                            if (preToolContent.isNotBlank()) {
                                MarkdownContent(
                                    markdown = preToolContent,
                                    modifier = Modifier.fillMaxWidth(),
                                    onLinkClick = { url -> println("点击链接：$url") })
                            }

                            // 工具调用信息（显示在消息中间）
                            InlineToolCallSection(
                                toolCalls = toolCalls, isStreaming = isStreaming
                            )

                            // 工具调用后的文本内容
                            val postToolContent = extractPostToolContent(messageContent)
                            if (postToolContent.isNotBlank()) {
                                MarkdownContent(
                                    markdown = postToolContent,
                                    modifier = Modifier.fillMaxWidth(),
                                    onLinkClick = { url -> println("点击链接：$url") })
                            }
                        } else {
                            // 没有工具调用时，直接显示全部内容
                            if (messageContent.isNotBlank()) {
                                MarkdownContent(
                                    markdown = messageContent,
                                    modifier = Modifier.fillMaxWidth(),
                                    onLinkClick = { url -> println("点击链接：$url") })
                            }
                        }
                    }
                }

                // 操作栏（所有消息都显示）
                MessageActionBar(
                    message = message,
                    showActionBar = showActionBar,
                    onToggleActionBar = { showActionBar = !showActionBar },
                    onDelete = {
                        onDelete()
                        showActionBar = false
                    },
                    onCopy = {
                        onCopy()
                        showActionBar = false
                    },
                    onRetry = {
                        onRetry()
                        showActionBar = false
                    })
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
}

/**
 * 内联工具调用区块组件
 * 【优化】用于在消息内容中混合渲染工具调用，支持动画和交互效果
 * 显示格式：模型回复：让我 查看目录... 发起工具调用
 * 【优化】使用更紧凑的布局，自然嵌入消息中间
 */
@Composable
fun InlineToolCallSection(
    toolCalls: List<AssistantMessage.ToolCall>, isStreaming: Boolean = false, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 工具调用列表（带延迟动画）
        toolCalls.forEachIndexed { index, toolCall ->
            val itemKey = "tool_call_${toolCall.id}"
            androidx.compose.animation.AnimatedVisibility(
                visible = true, label = itemKey, enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = 0.6f, stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(
                    animationSpec = tween(300)
                )
            ) {
                InlineToolCall(
                    toolCall = toolCall, isStreaming = isStreaming
                )
            }
        }
    }
}

/**
 * 提取工具调用标记前的内容
 * 格式："[TOOL_CALLS]" 标记之前的内容
 */
private fun extractPreToolContent(content: String): String {
    val toolCallMarker = "[TOOL_CALLS]"
    val index = content.indexOf(toolCallMarker)
    return if (index >= 0) {
        content.substring(0, index).trim()
    } else {
        // 如果没有找到标记，返回空字符串（工具调用前没有内容）
        ""
    }
}

/**
 * 提取工具调用标记后的内容
 * 格式："[TOOL_CALLS]" 标记之后的内容
 */
private fun extractPostToolContent(content: String): String {
    val toolCallMarker = "[TOOL_CALLS]"
    val index = content.indexOf(toolCallMarker)
    return if (index >= 0) {
        content.substring(index + toolCallMarker.length).trim()
    } else {
        // 如果没有找到标记，返回空字符串
        ""
    }
}

/**
 * 构建工具调用的头部描述文本
 */
private fun buildToolCallHeaderText(toolCalls: List<AssistantMessage.ToolCall>): String {
    if (toolCalls.isEmpty()) return ""

    val firstTool = toolCalls.first()
    val toolName = firstTool.name
    val parameters = parseToolArguments(firstTool.arguments)

    return when (toolName) {
        "ls" -> {
            val directory = parameters["directory"] ?: "当前目录"
            "让我查看 $directory"
        }

        "read_file" -> {
            val filePath = parameters["file_paths"] ?: parameters["file_path"] ?: "文件"
            "让我读取 $filePath"
        }

        "write_file" -> {
            val filePath = parameters["file_path"] ?: "文件"
            "让我写入 $filePath"
        }

        "edit_file" -> {
            val filePath = parameters["file_path"] ?: "文件"
            "让我修改 $filePath"
        }

        "grep" -> {
            val pattern = parameters["pattern"] ?: "内容"
            "让我搜索 $pattern"
        }

        "glob" -> {
            val pattern = parameters["pattern"] ?: "文件"
            "让我查找 $pattern"
        }

        "web_search" -> {
            val query = parameters["query"] ?: parameters["queryList"] ?: "内容"
            "让我搜索网络 $query"
        }

        "execute_terminal_command" -> {
            val command = parameters["command"] ?: "命令"
            "让我执行命令 $command"
        }

        "feed_back_tool" -> {
            "让我收集反馈"
        }

        else -> {
            "让我执行 $toolName"
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

// ==================== 新版 ConversationMessage 组件 ====================

/**
 * 基于 ConversationMessage 的消息气泡组件
 * 通过遍历 rawMessages 实现混合排版：文本 + 工具调用 + 工具响应
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationMessageBubble(
    message: ConversationMessage,
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    onRetry: () -> Unit = {},
    isStreaming: Boolean = false
) {
    val isUser = message.isUser()
    val chatColors = getCurrentChatColors()
    var showActionBar by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // 获取混合内容项列表
    val mixedContentItems = when (message) {
        is ConversationMessage.User -> listOf(ConversationMessage.MixedContentItem.Text(message.content))
        is ConversationMessage.Assistant -> message.buildMixedContentItems()
    }

    // 获取用于复制的完整文本内容
    val fullTextContent = when (message) {
        is ConversationMessage.User -> message.content
        is ConversationMessage.Assistant -> message.text
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.widthIn(max = 600.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
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
                // 发送者名称
                Text(
                    text = when (message) {
                        is ConversationMessage.User -> "你"
                        is ConversationMessage.Assistant -> "AI"
                    }, style = TextStyle(
                        color = when (message) {
                            is ConversationMessage.User -> MaterialTheme.colorScheme.primary
                            is ConversationMessage.Assistant -> MaterialTheme.colorScheme.secondary
                        }, fontSize = 11.sp, fontWeight = FontWeight.Medium
                    ), modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
                )

                // 消息气泡
                Surface(
                    color = chatColors.assistantMessageColor, shape = RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ), modifier = Modifier.widthIn(max = 600.dp).combinedClickable(
                        interactionSource = interactionSource,
                        indication = rememberRipple(bounded = true),
                        onClick = { showActionBar = !showActionBar })
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        // 遍历混合内容项进行渲染
                        mixedContentItems.forEach { item ->
                            when (item) {
                                is ConversationMessage.MixedContentItem.Text -> {
                                    // 渲染文本内容
                                    if (item.content.isNotBlank()) {
                                        MarkdownContent(
                                            markdown = item.content,
                                            modifier = Modifier.fillMaxWidth(),
                                            onLinkClick = { url -> println("点击链接：$url") })
                                    }
                                }

                                is ConversationMessage.MixedContentItem.ToolCall -> {
                                    // 渲染工具调用
                                    InlineToolCall(
                                        toolCall = item.toolCall, isStreaming = isStreaming
                                    )
                                }

                                is ConversationMessage.MixedContentItem.ToolResponse -> {
                                    // 渲染工具响应（渲染到对应的工具调用下面）
                                    ToolResponseItem(
                                        responseMessage = item.responseMessage, toolCallId = item.toolCallId
                                    )
                                }
                            }
                        }
                    }
                }

                // 操作栏
                ConversationMessageActionBar(
                    message = message,
                    fullText = fullTextContent,
                    showActionBar = showActionBar,
                    onToggleActionBar = { showActionBar = !showActionBar },
                    onDelete = {
                        onDelete()
                        showActionBar = false
                    },
                    onCopy = {
                        onCopy()
                        showActionBar = false
                    },
                    onRetry = {
                        onRetry()
                        showActionBar = false
                    })
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
}

/**
 * 工具响应项组件（基于 ToolResponseMessage）
 * 渲染到对应的工具调用下面
 */
@Composable
private fun ToolResponseItem(
    responseMessage: ToolResponseMessage, toolCallId: String
) {
    val chatColors = getCurrentChatColors()

    // 找到对应的响应
    val response = responseMessage.responses.find { it.id() == toolCallId } ?: return

    var expanded by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val itemKey = "tool_response_${response.id()}"

    // 入场动画
    LaunchedEffect(itemKey) {
        visible = true
    }

    val toolConfig = getToolResponseConfig(response.name())
    val isError = response.responseData()?.contains("error", ignoreCase = true) == true

    AnimatedVisibility(
        visible = visible, label = itemKey, enter = fadeIn(
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow
            ), expandFrom = Alignment.Top
        ), modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().border(
                width = 1.dp, brush = Brush.horizontalGradient(
                    colors = listOf(
                        if (isError) Color(0xFFE53935).copy(alpha = 0.3f)
                        else toolConfig.color.copy(alpha = 0.3f), if (isError) Color(0xFFE53935).copy(alpha = 0.1f)
                        else toolConfig.color.copy(alpha = 0.1f), if (isError) Color(0xFFE53935).copy(alpha = 0.3f)
                        else toolConfig.color.copy(alpha = 0.3f)
                    )
                ), shape = RoundedCornerShape(8.dp)
            ), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(
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
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 状态图标
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(
                                if (isError) Color(0xFFE53935).copy(alpha = 0.2f)
                                else Color(0xFF43A047).copy(alpha = 0.2f)
                            ), contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                contentDescription = if (isError) "失败" else "成功",
                                tint = if (isError) Color(0xFFE53935) else Color(0xFF43A047),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (isError) "❌ ${toolConfig.displayName} 失败" else "✅ ${toolConfig.displayName}",
                                style = TextStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = if (isError) Color(0xFFE53935)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "执行ID: ${response.id().take(8)}...",
                                style = TextStyle(
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
                    visible = expanded, enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(), exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                text = "执行结果", style = TextStyle(
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
                                style = TextStyle(
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
 * 获取工具响应配置
 */
@Composable
private fun getToolResponseConfig(toolName: String): ToolResponseConfig {
    return when (toolName) {
        "read_file" -> ToolResponseConfig(
            icon = Icons.Default.FileOpen, color = Color(0xFF43A047), displayName = "读取文件"
        )

        "write_file" -> ToolResponseConfig(
            icon = Icons.Default.Edit, color = Color(0xFF1E88E5), displayName = "写入文件"
        )

        "edit_file" -> ToolResponseConfig(
            icon = Icons.Default.Build, color = Color(0xFFFFA000), displayName = "修改文件"
        )

        "ls" -> ToolResponseConfig(
            icon = Icons.Default.Folder, color = Color(0xFF8D6E63), displayName = "列出目录"
        )

        "grep" -> ToolResponseConfig(
            icon = Icons.Default.Search, color = Color(0xFFD81B60), displayName = "搜索内容"
        )

        "glob" -> ToolResponseConfig(
            icon = Icons.Default.Search, color = Color(0xFF00ACC1), displayName = "查找文件"
        )

        "execute_terminal_command" -> ToolResponseConfig(
            icon = Icons.Default.Terminal, color = Color(0xFF546E7A), displayName = "执行命令"
        )

        "web_search" -> ToolResponseConfig(
            icon = Icons.Default.Language, color = Color(0xFF5E35B1), displayName = "网络搜索"
        )

        "feed_back_tool" -> ToolResponseConfig(
            icon = Icons.Default.Feedback, color = Color(0xFF6D4C41), displayName = "收集反馈"
        )

        else -> ToolResponseConfig(
            icon = Icons.Default.Build, color = MaterialTheme.colorScheme.primary, displayName = toolName
        )
    }
}

/**
 * ConversationMessage 的操作栏组件
 */
@Composable
private fun ConversationMessageActionBar(
    message: ConversationMessage,
    fullText: String,
    showActionBar: Boolean,
    onToggleActionBar: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit
) {
    val chatColors = getCurrentChatColors()
    val clipboardManager = LocalClipboardManager.current
    val interactionSource = remember { MutableInteractionSource() }

    val timestamp = formatTimestamp(message.timestamp)

    Column {
        // 操作栏（时间 + 展开按钮）
        Row(
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp).clickable(
                interactionSource = interactionSource, indication = rememberRipple(bounded = true)
            ) { onToggleActionBar() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (timestamp.isNotEmpty()) {
                Text(
                    text = timestamp, style = TextStyle(
                        color = chatColors.textSecondary, fontSize = 11.sp
                    )
                )
            }

            Text(
                text = if (showActionBar) "收起 ▲" else "更多 ▼", style = TextStyle(
                    color = chatColors.textSecondary, fontSize = 11.sp
                )
            )
        }

        // 操作按钮组
        if (showActionBar) {
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 删除按钮
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(
                        interactionSource = interactionSource, indication = rememberRipple(bounded = true)
                    ) { onDelete() }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "删除", style = TextStyle(
                            color = MaterialTheme.colorScheme.error, fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 复制按钮
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(
                        interactionSource = interactionSource, indication = rememberRipple(bounded = true)
                    ) {
                        clipboardManager.setText(AnnotatedString(fullText))
                        onCopy()
                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier.size(14.dp),
                        tint = chatColors.textSecondary
                    )
                    Text(
                        text = "复制", style = TextStyle(
                            color = chatColors.textSecondary, fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 重试按钮
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(
                        interactionSource = interactionSource, indication = rememberRipple(bounded = true)
                    ) { onRetry() }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "重试",
                        modifier = Modifier.size(14.dp),
                        tint = chatColors.textSecondary
                    )
                    Text(
                        text = "重试", style = TextStyle(
                            color = chatColors.textSecondary, fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    return try {
        val date = java.time.Instant.ofEpochMilli(timestamp)
        val localDateTime = date.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        ""
    }
}
