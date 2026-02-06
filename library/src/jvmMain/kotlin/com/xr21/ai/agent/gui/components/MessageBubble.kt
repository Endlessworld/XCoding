package com.xr21.ai.agent.gui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.gui.components.markdown.renderer.MarkdownContent
import com.xr21.ai.agent.gui.getAssistantBubbleGradientBrush
import com.xr21.ai.agent.gui.getCurrentChatColors
import com.xr21.ai.agent.gui.getUserBubbleGradientBrush
import com.xr21.ai.agent.gui.model.ConversationMessage
import com.xr21.ai.agent.gui.model.isUser

/**
 * 基于 ConversationMessage 的消息气泡组件 - macOS/iOS 磨砂玻璃风格
 * 通过遍历 rawMessages 实现混合排版：文本 + 工具调用 + 工具响应
 * 支持自适应窗口宽度和渐变磨砂玻璃效果
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
    var showActionBar by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // 自适应宽度：使用 fillMaxWidth() 配合 widthIn() 实现响应式布局
    // 最大宽度限制为 700.dp，最小宽度为 280.dp
    // 对于用户消息使用 85% 宽度，对于 AI 消息使用 75% 宽度

    // 获取渐变背景
    val bubbleGradient = if (isUser) getUserBubbleGradientBrush() else getAssistantBubbleGradientBrush()

    // 获取混合内容项列表
    val mixedContentItems = when (message) {
        is ConversationMessage.User -> listOf(ConversationMessage.MixedContentItem.Text(message.content, index = 0))
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
        // 自适应宽度：用户消息使用 85% 宽度，AI 消息使用 75% 宽度，最大 700.dp
        Row(
            modifier = Modifier
                .widthIn(max = 1500.dp)
                .fillMaxWidth(if (isUser) 0.5f else 0.95f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                // AI 头像 - 带磨砂玻璃效果
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                )
                            )
                        )
                        .graphicsLayer {
                            this.renderEffect = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 680.dp)
            ) {
                // 发送者名称
                Text(
                    text = when (message) {
                        is ConversationMessage.User -> "你"
                        is ConversationMessage.Assistant -> "AI 助手"
                    }, style = TextStyle(
                        color = when (message) {
                            is ConversationMessage.User -> Color.Gray
                            is ConversationMessage.Assistant -> MaterialTheme.colorScheme.secondary
                        }, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    ), modifier = Modifier.padding(bottom = 6.dp, start = 4.dp, end = 4.dp)
                )

                // 消息气泡 - 磨砂玻璃效果 - 精致紫调
                Box(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(
                                topStart = if (isUser) 22.dp else 10.dp,
                                topEnd = if (isUser) 10.dp else 22.dp,
                                bottomStart = 22.dp,
                                bottomEnd = 22.dp
                            ),
                            ambientColor = if (isUser) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                Color.Black.copy(alpha = 0.12f)
                            },
                            spotColor = if (isUser) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                Color.Black.copy(alpha = 0.1f)
                            }
                        )
                        .clip(
                            RoundedCornerShape(
                                topStart = if (isUser) 22.dp else 10.dp,
                                topEnd = if (isUser) 10.dp else 22.dp,
                                bottomStart = 22.dp,
                                bottomEnd = 22.dp
                            )
                        )
                        .background(bubbleGradient)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = rememberRipple(bounded = true, color = MaterialTheme.colorScheme.primary),
                            onClick = { showActionBar = !showActionBar }
                        )
                ) {
                    // 内部实际内容
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isUser) 0f else 0.05f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Column(
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
                                        // 直接从 message 获取响应数据，message 变化时会自动重组
                                        val toolResponse = (message as? ConversationMessage.Assistant)?.getToolResponseFor(item.toolCall.id)
                                        val responseData = toolResponse?.responses?.firstOrNull()?.responseData()
//                                        val responseMap = responseData?.let { Json.to(it, Map::class.java) } ?: emptyMap<String, Any>()
                                        val isError = responseData?.contains("error") ?: false
                                        InlineToolCall(
                                            toolCall = item.toolCall,
                                            isStreaming = isStreaming,
                                            responseData = responseData,
                                            isError = isError
                                        )
                                    }

                                    is ConversationMessage.MixedContentItem.ToolResponse -> {
                                        // 不再单独渲染 ToolResponseItem，响应内容已合并到 InlineToolCall 中显示
                                    }
                                }
                            }
                        }
                    }
                }

                // 操作栏 - 磨砂玻璃风格
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
                Spacer(modifier = Modifier.width(10.dp))
                // 用户头像 - 带磨砂玻璃效果
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Gray.copy(alpha = 0.2f),
                                    Color.LightGray.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
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
fun formatTimestamp(timestamp: Long): String {
    return try {
        val date = java.time.Instant.ofEpochMilli(timestamp)
        val localDateTime = date.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        ""
    }
}
