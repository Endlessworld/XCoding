package com.xr21.ai.agent.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.gui.UiChatMessage
import com.xr21.ai.agent.gui.UiMessageType
import com.xr21.ai.agent.gui.components.markdown.renderer.MarkdownContent
import com.xr21.ai.agent.gui.getCurrentChatColors
import java.time.format.DateTimeFormatter

@Composable
fun MessageBubble(
    message: UiChatMessage, onResend: () -> Unit = {}, isStreaming: Boolean = false
) {
    val isUser = message.type == UiMessageType.USER
    val chatColors = getCurrentChatColors()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(6.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            Text(
                text = when (message.type) {
                    UiMessageType.USER -> "你"
                    UiMessageType.ASSISTANT -> "AI"
                    UiMessageType.SYSTEM -> "系统"
                    UiMessageType.TOOL_CALL -> "工具调用"
                    UiMessageType.TOOL_RESPONSE -> "工具响应"
                    UiMessageType.ERROR -> "错误"
                },
                style = TextStyle(
                    color = when (message.type) {
                        UiMessageType.USER -> MaterialTheme.colorScheme.primary
                        UiMessageType.ASSISTANT -> MaterialTheme.colorScheme.secondary
                        UiMessageType.SYSTEM -> chatColors.systemMessageColor
                        UiMessageType.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
            )

            Surface(
                color = when (message.type) {
                    UiMessageType.USER -> chatColors.userMessageColor
                    UiMessageType.ASSISTANT -> chatColors.assistantMessageColor
                    UiMessageType.SYSTEM -> chatColors.systemMessageColor.copy(alpha = 0.2f)
                    UiMessageType.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                modifier = Modifier.widthIn(max = 600.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    MarkdownContent(
                        markdown = message.content,
                        modifier = Modifier.fillMaxWidth(),
                        onLinkClick = { url -> /* 处理链接点击 */
                            println("点击链接：$url")
                        }
                    )
                }
            }

            Text(
                text = message.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = TextStyle(
                    color = chatColors.textSecondary,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(6.dp)
            )
        }
    }
}
