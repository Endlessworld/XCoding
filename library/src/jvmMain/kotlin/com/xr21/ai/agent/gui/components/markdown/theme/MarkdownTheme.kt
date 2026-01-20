package com.xr21.ai.agent.gui.components.markdown.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Markdown 主题配置数据类
 *
 * @property textStyle 默认文本样式
 * @property h1TextStyle 一级标题样式
 * @property h2TextStyle 二级标题样式
 * @property h3TextStyle 三级标题样式
 * @property h4TextStyle 四级标题样式
 * @property h5TextStyle 五级标题样式
 * @property h6TextStyle 六级标题样式
 * @property codeTextStyle 行内代码样式
 * @property codeBlockTextStyle 代码块样式
 * @property linkTextStyle 链接文本样式
 * @property blockQuoteTextStyle 引用块文本样式
 * @property codeBackgroundColor 代码背景色
 * @property quoteBorderColor 引用块边框色
 * @property listItemBulletColor 列表项符号颜色
 * @property horizontalRuleColor 分割线颜色
 */
data class MarkdownTheme(
    val textStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Color.Unspecified
    ),
    val h1TextStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
        color = Color.Unspecified
    ),
    val h2TextStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 36.sp,
        color = Color.Unspecified
    ),
    val h3TextStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
        color = Color.Unspecified
    ),
    val h4TextStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
        color = Color.Unspecified
    ),
    val h5TextStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
        color = Color.Unspecified
    ),
    val h6TextStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
        color = Color.Unspecified
    ),
    val codeTextStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        color = Color.Unspecified
    ),
    val codeBlockTextStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color.Unspecified
    ),
    val linkTextStyle: TextStyle = TextStyle(
        color = Color(0xFF2196F3),
        textDecoration = TextDecoration.Underline
    ),
    val blockQuoteTextStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = Color.Unspecified
    ),
    val codeBackgroundColor: Color = Color(0xFFF5F5F5),
    val quoteBorderColor: Color = Color(0xFF2196F3),
    val listItemBulletColor: Color = Color(0xFF666666),
    val horizontalRuleColor: Color = Color(0xFFE0E0E0)
)

/**
 * 默认浅色主题
 */
val DefaultMarkdownTheme = MarkdownTheme()

/**
 * 深色主题配置
 */
val DarkMarkdownTheme = MarkdownTheme(
    textStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Color(0xFFE0E0E0)
    ),
    h1TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
        color = Color.White
    ),
    h2TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 36.sp,
        color = Color.White
    ),
    h3TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
        color = Color.White
    ),
    codeBackgroundColor = Color(0xFF2D2D2D),
    quoteBorderColor = Color(0xFF64B5F6),
    listItemBulletColor = Color(0xFFAAAAAA),
    horizontalRuleColor = Color(0xFF424242)
)

/**
 * CompositionLocal 用于在 Compose 树中传递 Markdown 主题
 */
val LocalMarkdownTheme = staticCompositionLocalOf { DefaultMarkdownTheme }

/**
 * Markdown 主题提供者组件
 */
@Composable
fun MarkdownThemeProvider(
    theme: MarkdownTheme = if (isSystemInDarkTheme()) DarkMarkdownTheme else DefaultMarkdownTheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalMarkdownTheme provides theme) {
        content()
    }
}
