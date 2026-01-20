package com.xr21.ai.agent.gui.components.markdown.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xr21.ai.agent.gui.components.markdown.highlight.CodeHighlighter
import com.xr21.ai.agent.gui.components.markdown.model.NodeUtils
import com.xr21.ai.agent.gui.components.markdown.parser.MarkdownParser
import com.xr21.ai.agent.gui.components.markdown.theme.LocalMarkdownTheme
import com.xr21.ai.agent.gui.components.markdown.theme.MarkdownTheme
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Document
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.ThematicBreak

/**
 * Markdown 渲染器主组件
 *
 * 提供了多种渲染 Markdown 内容的方式：
 * - MarkdownText: 基本的文本渲染
 * - MarkdownContent: 使用 LazyColumn 渲染长文档
 */
object MarkdownRenderer {

    /**
     * 创建 Markdown 解析器实例
     */
    fun createParser(): MarkdownParser {
        return MarkdownParser.getInstance()
    }
}

/**
 * 基本的 Markdown 文本组件
 *
 * 使用 Column 渲染，适合中等长度的 Markdown 内容。
 *
 * @param markdown Markdown 原始文本
 * @param modifier 修饰符
 * @param onLinkClick 链接点击回调，接收 URL 作为参数
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        MarkdownContent(
            markdown = markdown,
            onLinkClick = onLinkClick
        )
    }
}

/**
 * 使用 LazyColumn 渲染长文档
 *
 * 适合渲染长篇 Markdown 文档，提供良好的滚动性能。
 *
 * @param markdown Markdown 原始文本
 * @param modifier 修饰符
 * @param onLinkClick 链接点击回调，接收 URL 作为参数
 */
@Composable
fun MarkdownLazyColumn(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {}
) {
    val parser = remember { MarkdownParser.getInstance() }
    val document = remember(markdown) { parser.parse(markdown) }
    val theme = LocalMarkdownTheme.current

    // 使用 LazyColumn 渲染
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp)
    ) {
        // 处理 Document 节点
        item {
            RenderNode(document, onLinkClick, theme)
        }
    }
}

/**
 * 渲染节点的核心函数
 */
@Composable
fun RenderNode(
    node: Node,
    onLinkClick: (String) -> Unit,
    theme: MarkdownTheme = LocalMarkdownTheme.current
) {
    when (node) {
        is Document -> {
            var child = node.firstChild
            while (child != null) {
                RenderNode(child, onLinkClick, theme)
                child = child.next
            }
        }
        is Heading -> {
            val text = NodeUtils.buildStyledText(node.firstChild)
            val style = when (node.level) {
                1 -> theme.h1TextStyle
                2 -> theme.h2TextStyle
                3 -> theme.h3TextStyle
                4 -> theme.h4TextStyle
                5 -> theme.h5TextStyle
                else -> theme.h6TextStyle
            }
            Text(
                text = text,
                style = style,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        is Paragraph -> {
            val text = NodeUtils.buildStyledText(node.firstChild)
            Text(
                text = text,
                style = theme.textStyle,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        is FencedCodeBlock -> {
            val code = CodeHighlighter.highlight(node.literal, node.info)
            Surface(
                modifier = Modifier
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
        is IndentedCodeBlock -> {
            val code = CodeHighlighter.highlightSimple(node.literal)
            Surface(
                modifier = Modifier
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
        is BulletList -> {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                var item = node.firstChild
                while (item is ListItem) {
                    val text = NodeUtils.buildStyledText(item.firstChild)
                    MarkdownListItem(
                        index = null,
                        text = text,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    item = item.next
                }
            }
        }
        is OrderedList -> {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                var item = node.firstChild
                var index = node.startNumber
                while (item is ListItem) {
                    val text = NodeUtils.buildStyledText(item.firstChild)
                    MarkdownOrderedListItem(
                        number = index,
                        text = text,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    item = item.next
                    index++
                }
            }
        }
        is ThematicBreak -> {
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                color = theme.horizontalRuleColor,
                thickness = 1.dp
            )
        }
        is BlockQuote -> {
            val text = NodeUtils.extractText(node.firstChild)
            MarkdownBlockQuote(
                text = AnnotatedString(text),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        is Image -> {
            // 图片占位符
            ImagePlaceholder(
                altText = node.title ?: node.destination,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

/**
 * Markdown 内容渲染组件
 *
 * 内部使用 Column 渲染所有内容。
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {}
) {
    val parser = remember { MarkdownParser.getInstance() }
    val document = remember(markdown) { parser.parse(markdown) }
    val theme = LocalMarkdownTheme.current

    Column(modifier = modifier) {
        var child: Node? = document.firstChild
        while (child != null) {
            RenderNode(child, onLinkClick, theme)
            child = child.next
        }
    }
}

/**
 * 列表项组件
 */
@Composable
private fun MarkdownListItem(
    index: Int?,
    text: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownTheme.current

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(theme.listItemBulletColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            style = theme.textStyle,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 有序列表项组件
 */
@Composable
private fun MarkdownOrderedListItem(
    number: Int,
    text: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownTheme.current

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            style = theme.textStyle,
            color = theme.listItemBulletColor,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            style = theme.textStyle,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 引用块组件
 */
@Composable
private fun MarkdownBlockQuote(
    text: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownTheme.current

    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(androidx.compose.foundation.layout.IntrinsicSize.Max)
                .clip(RoundedCornerShape(2.dp))
                .background(theme.quoteBorderColor)
        )

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
 * 图片占位符组件
 */
@Composable
private fun ImagePlaceholder(
    altText: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                theme.codeBackgroundColor,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "图片",
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = altText.take(50),
                style = theme.textStyle.copy(fontStyle = FontStyle.Italic),
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
