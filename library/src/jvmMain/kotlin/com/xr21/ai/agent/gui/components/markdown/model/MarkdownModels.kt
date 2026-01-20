package com.xr21.ai.agent.gui.components.markdown.model

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xr21.ai.agent.gui.components.markdown.theme.LocalMarkdownTheme
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text

/**
 * Markdown 节点类型枚举
 */
enum class MarkdownNodeType {
    HEADING,
    PARAGRAPH,
    BULLETED_LIST,
    ORDERED_LIST,
    LIST_ITEM,
    CODE_BLOCK,
    INLINE_CODE,
    BLOCK_QUOTE,
    THEMATIC_BREAK,
    IMAGE,
    LINK,
    TEXT,
    BOLD,
    ITALIC,
    STRONG_EMPHASIS
}

/**
 * 基础 Markdown 节点模型
 */
sealed class MarkdownNode {
    abstract val type: MarkdownNodeType
    abstract val children: List<MarkdownNode>

    data class HeadingNode(
        val level: Int,
        val text: AnnotatedString,
        override val children: List<MarkdownNode> = emptyList()
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.HEADING
    }

    data class ParagraphNode(
        val text: AnnotatedString,
        override val children: List<MarkdownNode> = emptyList()
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.PARAGRAPH
    }

    data class ListItemNode(
        val index: Int? = null, // 有序列表的序号，无序列表为 null
        val text: AnnotatedString,
        override val children: List<MarkdownNode> = emptyList()
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.LIST_ITEM
    }

    data class BulletListNode(
        val items: List<MarkdownNode>,
        override val children: List<MarkdownNode> = items
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.BULLETED_LIST
    }

    data class OrderedListNode(
        val startNumber: Int,
        val items: List<MarkdownNode>,
        override val children: List<MarkdownNode> = items
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.ORDERED_LIST
    }

    data class CodeBlockNode(
        val code: AnnotatedString,
        val language: String? = null,
        override val children: List<MarkdownNode> = emptyList()
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.CODE_BLOCK
    }

    data class InlineCodeNode(
        val code: String,
        override val children: List<MarkdownNode> = emptyList()
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.INLINE_CODE
    }

    data class BlockQuoteNode(
        val text: AnnotatedString,
        override val children: List<MarkdownNode> = emptyList()
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.BLOCK_QUOTE
    }

    data class ThematicBreakNode(
        override val children: List<MarkdownNode> = emptyList()
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.THEMATIC_BREAK
    }

    data class ImageNode(
        val url: String,
        val title: String? = null,
        val altText: String? = null,
        override val children: List<MarkdownNode> = emptyList()
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.IMAGE
    }

    data class LinkNode(
        val url: String,
        val text: AnnotatedString,
        val title: String? = null,
        override val children: List<MarkdownNode> = emptyList()
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.LINK
    }

    data class TextNode(
        val content: String,
        val styles: List<SpanStyle> = emptyList(),
        override val children: List<MarkdownNode> = emptyList()
    ) : MarkdownNode() {
        override val type = MarkdownNodeType.TEXT
    }
}

/**
 * 列表项渲染组件
 */
@Composable
fun MarkdownListItem(
    index: Int?,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    indentSize: Dp = 24.dp
) {
    val theme = LocalMarkdownTheme.current

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 列表符号
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(theme.listItemBulletColor),
            contentAlignment = Alignment.Center
        ) {
            if (index != null) {
                // 有序列表数字
                Text(
                    text = index.toString(),
                    style = theme.textStyle,
                    color = theme.listItemBulletColor,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 列表内容
        Text(
            text = text,
            style = theme.textStyle,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 有序列表项渲染组件
 */
@Composable
fun MarkdownOrderedListItem(
    number: Int,
    text: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownTheme.current

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 序号
        Text(
            text = "$number.",
            style = theme.textStyle,
            color = theme.listItemBulletColor,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 列表内容
        Text(
            text = text,
            style = theme.textStyle,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 引用块渲染组件
 */
@Composable
fun MarkdownBlockQuote(
    text: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownTheme.current

    Row(
        modifier = modifier.padding(vertical = 8.dp)
    ) {
        // 左边框
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(2.dp))
                .background(theme.quoteBorderColor)
        )

        // 内容
        Text(
            text = text,
            style = theme.blockQuoteTextStyle,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        )
    }
}

/**
 * 分割线渲染组件
 */
@Composable
fun MarkdownThematicBreak(
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownTheme.current

    Divider(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        color = theme.horizontalRuleColor,
        thickness = 1.dp
    )
}

/**
 * 代码块渲染组件
 */
@Composable
fun MarkdownCodeBlock(
    code: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownTheme.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = theme.codeBackgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = code,
            style = theme.codeBlockTextStyle,
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * 内联代码渲染组件
 */
@Composable
fun InlineCode(
    code: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownTheme.current

    Surface(
        modifier = modifier,
        color = theme.codeBackgroundColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = code,
            style = theme.codeTextStyle,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 工具函数：从 CommonMark 节点提取文本内容
 */
object NodeUtils {

    /**
     * 从节点中提取纯文本内容
     */
    fun extractText(node: Node?): String {
        if (node == null) return ""

        return when (node) {
            is Text -> node.literal
            is Code -> "`${node.literal}`"
            is Emphasis -> "*${extractText(node.firstChild)}*"
            is StrongEmphasis -> "**${extractText(node.firstChild)}**"
            is Link -> "[${extractText(node.firstChild)}](${node.destination})"
            is Image -> "![${node.title ?: ""}](${node.destination})"
            else -> {
                val sb = StringBuilder()
                var child = node.firstChild
                while (child != null) {
                    sb.append(extractText(child))
                    child = child.next
                }
                sb.toString()
            }
        }
    }

    /**
     * 构建带样式的 AnnotatedString
     */
    fun buildStyledText(node: Node?): AnnotatedString {
        // 获取主题用于构建样式
        val theme = LocalMarkdownTheme.current

        return buildAnnotatedString {
            if (node == null) return@buildAnnotatedString

            when (node) {
                is Text -> {
                    append(node.literal)
                }
                is Code -> {
                    pushStyle(theme.codeTextStyle.toSpanStyle())
                    pushStyle(SpanStyle(background = theme.codeBackgroundColor))
                    append(node.literal)
                    pop()
                    pop()
                }
                is Emphasis -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(buildStyledText(node.firstChild))
                    pop()
                }
                is StrongEmphasis -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(buildStyledText(node.firstChild))
                    pop()
                }
                is Link -> {
                    pushStyle(theme.linkTextStyle.toSpanStyle())
                    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    append(buildStyledText(node.firstChild))
                    pop()
                    pop()
                }
                is Image -> {
                    // 图片暂时显示为占位符
                    val altText = node.title ?: node.destination
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    pushStyle(SpanStyle(color = Color.Gray))
                    append("[图片: $altText]")
                    pop()
                    pop()
                }
                else -> {
                    var child = node.firstChild
                    while (child != null) {
                        append(buildStyledText(child))
                        child = child.next
                    }
                }
            }
        }
    }
}
