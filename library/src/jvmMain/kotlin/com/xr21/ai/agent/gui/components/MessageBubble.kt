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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.gui.UiChatMessage
import com.xr21.ai.agent.gui.UiMessageType
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
                    modifier = Modifier.padding(12.dp)
                ) {
                    MarkdownText(
                        content = message.content,
                        baseTextStyle = TextStyle(
                            color = chatColors.textPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
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

@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    baseTextStyle: TextStyle = TextStyle.Default
) {
    val annotatedString = remember(content) {
        parseMarkdown(content)
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = baseTextStyle
    )
}

private fun parseMarkdown(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    val sb = StringBuilder(text)

    while (i < sb.length) {
        when {
            // Code block
            sb.substring(i).startsWith("```") -> {
                val endIndex = sb.indexOf("```", i + 3)
                if (endIndex != -1) {
                    val codeWithLabel = sb.substring(i + 3, endIndex)
                    val codeLines = codeWithLabel.split("\n")
                    val code = if (codeLines.isNotEmpty() && !codeLines.first().contains(" ") && codeLines.first().isNotBlank()) {
                        // 第一行是语言标签，去掉它
                        codeLines.drop(1).joinToString("\n")
                    } else {
                        codeWithLabel
                    }
                    builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.2f)))
                    builder.append(code)
                    builder.pop()
                    i = endIndex + 3
                    continue
                }
            }
            // Bold **text**
            sb.substring(i).startsWith("**") -> {
                val endIndex = sb.indexOf("**", i + 2)
                if (endIndex != -1) {
                    val boldText = sb.substring(i + 2, endIndex)
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(boldText)
                    builder.pop()
                    i = endIndex + 2
                    continue
                }
            }
            // Bold __text__
            sb.substring(i).startsWith("__") -> {
                val endIndex = sb.indexOf("__", i + 2)
                if (endIndex != -1) {
                    val boldText = sb.substring(i + 2, endIndex)
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(boldText)
                    builder.pop()
                    i = endIndex + 2
                    continue
                }
            }
            // Italic *text*
            sb.substring(i).startsWith("*") -> {
                val nextAsterisk = sb.indexOf("*", i + 1)
                if (nextAsterisk != -1 && !sb.substring(i + 1, nextAsterisk).contains("*")) {
                    val italicText = sb.substring(i + 1, nextAsterisk)
                    builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    builder.append(italicText)
                    builder.pop()
                    i = nextAsterisk + 1
                    continue
                }
            }
            // Italic _text_
            sb.substring(i).startsWith("_") -> {
                val nextUnderscore = sb.indexOf("_", i + 1)
                if (nextUnderscore != -1 && !sb.substring(i + 1, nextUnderscore).contains("_")) {
                    val italicText = sb.substring(i + 1, nextUnderscore)
                    builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    builder.append(italicText)
                    builder.pop()
                    i = nextUnderscore + 1
                    continue
                }
            }
            // Inline code
            sb.substring(i).startsWith("`") -> {
                val endIndex = sb.indexOf("`", i + 1)
                if (endIndex != -1) {
                    val code = sb.substring(i + 1, endIndex)
                    builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.2f)))
                    builder.append(code)
                    builder.pop()
                    i = endIndex + 1
                    continue
                }
            }
            // Strikethrough ~~text~~
            sb.substring(i).startsWith("~~") -> {
                val endIndex = sb.indexOf("~~", i + 2)
                if (endIndex != -1) {
                    val strikethroughText = sb.substring(i + 2, endIndex)
                    builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    builder.append(strikethroughText)
                    builder.pop()
                    i = endIndex + 2
                    continue
                }
            }
            // Link [text](url)
            sb.substring(i).startsWith("[") -> {
                val closeBracket = sb.indexOf("]", i + 1)
                if (closeBracket != -1) {
                    val linkText = sb.substring(i + 1, closeBracket)
                    val openParen = sb.indexOf("(", closeBracket + 1)
                    if (openParen != -1) {
                        val closeParen = sb.indexOf(")", openParen + 1)
                        if (closeParen != -1) {
                            builder.pushStyle(SpanStyle(color = Color.Blue))
                            builder.append(linkText)
                            builder.pop()
                            i = closeParen + 1
                            continue
                        }
                    }
                }
            }
            // Heading #
            sb.substring(i).startsWith("# ") -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp))
                builder.append(sb.substring(i + 2).takeWhile { it != '\n' })
                builder.pop()
                i = sb.length
                continue
            }
            sb.substring(i).startsWith("## ") -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp))
                builder.append(sb.substring(i + 3).takeWhile { it != '\n' })
                builder.pop()
                i = sb.length
                continue
            }
            sb.substring(i).startsWith("### ") -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp))
                builder.append(sb.substring(i + 4).takeWhile { it != '\n' })
                builder.pop()
                i = sb.length
                continue
            }
            // Block quote >
            sb.substring(i).startsWith("> ") -> {
                builder.append("> ")
                val contentStart = i + 2
                val contentEnd = sb.indexOf("\n", contentStart).let { if (it == -1) sb.length else it }
                builder.append(sb.substring(contentStart, contentEnd))
                builder.append("\n")
                i = contentEnd
                continue
            }
            // List items - or *
            sb.substring(i).startsWith("- ") || sb.substring(i).startsWith("* ") -> {
                builder.append("• ")
                val contentStart = i + 2
                val contentEnd = sb.indexOf("\n", contentStart).let { if (it == -1) sb.length else it }
                builder.append(sb.substring(contentStart, contentEnd))
                builder.append("\n")
                i = contentEnd
                continue
            }
            // Ordered list
            sb.substring(i).matches(Regex("^\\d+\\. ")) -> {
                val dotIndex = sb.indexOf(". ", i)
                if (dotIndex != -1) {
                    builder.append("• ")
                    val contentStart = dotIndex + 2
                    val contentEnd = sb.indexOf("\n", contentStart).let { if (it == -1) sb.length else it }
                    builder.append(sb.substring(contentStart, contentEnd))
                    builder.append("\n")
                    i = contentEnd
                    continue
                }
            }
            // Horizontal rule
            sb.substring(i).startsWith("---") && (i == 0 || sb[i - 1] == '\n') -> {
                builder.append("——")
                builder.append("\n")
                i += 3
                continue
            }
        }
        builder.append(sb[i])
        i++
    }

    return builder.toAnnotatedString()
}
