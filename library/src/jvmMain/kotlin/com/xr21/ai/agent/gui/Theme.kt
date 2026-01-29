package com.xr21.ai.agent.gui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 主题模式枚举
 */
enum class ThemeMode {
    LIGHT,  // 亮色模式
    DARK,   // 暗色模式
    SYSTEM  // 跟随系统
}

/**
 * 亮色主题颜色 - macOS/iOS 风格
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF8B5CF6),
    tertiary = Color(0xFF06B6D4),
    background = Color(0xFFF5F5F7),  // macOS 风格浅灰背景
    surface = Color(0xFFFFFFFF).copy(alpha = 0.8f),  // 磨砂玻璃基础色
    surfaceVariant = Color(0xF0F1F5FF),  // 带透明度的表面
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1D1D1F),
    onSurface = Color(0xFF1D1D1F),
    onSurfaceVariant = Color(0xFF86868B),
    error = Color(0xFFEF4444)
)

/**
 * 暗色主题颜色 - macOS/iOS 风格
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF8B5CF6),
    tertiary = Color(0xFF06B6D4),
    background = Color(0xFF1C1C1E),  // macOS 风格深灰背景
    surface = Color(0xFF2C2C2E).copy(alpha = 0.85f),  // 磨砂玻璃基础色
    surfaceVariant = Color(0x3A3A3CFF),  // 带透明度的表面
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFFAEAEB2),
    error = Color(0xFFEF4444)
)

/**
 * 聊天主题颜色（自定义）- 磨砂玻璃风格
 */
data class ChatColors(
    val primary: Color,
    val secondary: Color,
    val userMessageColor: Color,
    val assistantMessageColor: Color,
    val systemMessageColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val borderColor: Color,
    val toolCallBackgroundColor: Color,
    // 磨砂玻璃渐变效果
    val glassGradientStart: Color,
    val glassGradientEnd: Color,
    val userBubbleGradientStart: Color,
    val userBubbleGradientEnd: Color,
    val assistantBubbleGradientStart: Color,
    val assistantBubbleGradientEnd: Color
)

/**
 * 亮色聊天主题 - 磨砂玻璃风格
 */
val LightChatColors = ChatColors(
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF8B5CF6),
    userMessageColor = Color(0xFFE0E0E0),
    assistantMessageColor = Color(0xFFFFFFFF).copy(alpha = 0.7f),
    systemMessageColor = Color(0xFFF59E0B),
    textPrimary = Color(0xFF1D1D1F),
    textSecondary = Color(0xFF86868B),
    borderColor = Color(0xFFD2D2D7),
    toolCallBackgroundColor = Color(0xFF6366F1),
    // 磨砂玻璃渐变
    glassGradientStart = Color(0xFFFFFFFF).copy(alpha = 0.9f),
    glassGradientEnd = Color(0xFFF5F5F7).copy(alpha = 0.7f),
    // 用户消息气泡渐变 (中性灰色系)
    userBubbleGradientStart = Color(0xFFE8E8E8),
    userBubbleGradientEnd = Color(0xFFDCDCDC),
    // AI消息气泡渐变 (白色系磨砂)
    assistantBubbleGradientStart = Color(0xFFFFFFFF).copy(alpha = 0.9f),
    assistantBubbleGradientEnd = Color(0xFFF5F5F7).copy(alpha = 0.8f)
)

/**
 * 暗色聊天主题 - 磨砂玻璃风格
 */
val DarkChatColors = ChatColors(
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF8B5CF6),
    userMessageColor = Color(0xFFE0E0E0),
    assistantMessageColor = Color(0xFF3A3A3C).copy(alpha = 0.8f),
    systemMessageColor = Color(0xFFF59E0B),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFAEAEB2),
    borderColor = Color(0xFF48484A),
    toolCallBackgroundColor = Color(0xFF8B5CF6),
    // 磨砂玻璃渐变
    glassGradientStart = Color(0xFF2C2C2E).copy(alpha = 0.95f),
    glassGradientEnd = Color(0xFF1C1C1E).copy(alpha = 0.9f),
    // 用户消息气泡渐变 (中性灰色系)
    userBubbleGradientStart = Color(0xFFE8E8E8),
    userBubbleGradientEnd = Color(0xFFDCDCDC),
    // AI消息气泡渐变 (深色系磨砂)
    assistantBubbleGradientStart = Color(0xFF3A3A3C).copy(alpha = 0.9f),
    assistantBubbleGradientEnd = Color(0xFF2C2C2E).copy(alpha = 0.85f)
)

/**
 * 获取磨砂玻璃背景渐变
 */
@Composable
fun getGlassGradientBrush(): Brush {
    val chatColors = getCurrentChatColors()
    return Brush.verticalGradient(
        colors = listOf(
            chatColors.glassGradientStart,
            chatColors.glassGradientEnd
        )
    )
}

/**
 * 获取用户气泡渐变
 */
@Composable
fun getUserBubbleGradientBrush(): Brush {
    val chatColors = getCurrentChatColors()
    return Brush.horizontalGradient(
        colors = listOf(
            chatColors.userBubbleGradientStart,
            chatColors.userBubbleGradientEnd
        )
    )
}

/**
 * 获取AI气泡渐变
 */
@Composable
fun getAssistantBubbleGradientBrush(): Brush {
    val chatColors = getCurrentChatColors()
    return Brush.verticalGradient(
        colors = listOf(
            chatColors.assistantBubbleGradientStart,
            chatColors.assistantBubbleGradientEnd
        )
    )
}

/**
 * CompositionLocal 用于共享主题状态
 */
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }
val LocalChatColors = staticCompositionLocalOf { DarkChatColors }

/**
 * 应用主题提供者 - macOS/iOS 磨砂玻璃风格
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColors else LightColors
    val chatColors = if (isDark) DarkChatColors else LightChatColors

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalChatColors provides chatColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

/**
 * 获取当前主题模式
 */
@Composable
fun getCurrentThemeMode(): ThemeMode {
    return LocalThemeMode.current
}

/**
 * 获取当前聊天主题颜色
 */
@Composable
fun getCurrentChatColors(): ChatColors {
    return LocalChatColors.current
}
