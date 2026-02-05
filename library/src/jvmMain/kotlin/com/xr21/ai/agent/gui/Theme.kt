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
 * 亮色主题颜色 - 现代渐变风格
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B21B6),       // 主色调：深邃紫罗兰
    primaryContainer = Color(0xFFE9D5FF),
    secondary = Color(0xFF7C3AED),     // 辅助色：优雅紫
    secondaryContainer = Color(0xFFDDD6FE),
    tertiary = Color(0xFF0891B2),      // 强调色：海洋青
    tertiaryContainer = Color(0xFFCFFAFE),
    background = Color(0xFFFAF9FF),    // 背景色：极浅紫白
    surface = Color(0xFFFFFFFF).copy(alpha = 0.96f),  // 表面色：半透明白
    surfaceVariant = Color(0xFFF1F0FA), // 表面变体：浅紫灰
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDFDDFF),
    onPrimary = Color.White,
    onPrimaryContainer = Color(0xFF2A0055),
    onSecondary = Color.White,
    onSecondaryContainer = Color(0xFF2A1063),
    onTertiary = Color.White,
    onTertiaryContainer = Color(0xFF001E28),
    onBackground = Color(0xFF1B1B22),  // 深色文字
    onSurface = Color(0xFF1B1B22),
    onSurfaceVariant = Color(0xFF474752),
    outline = Color(0xFF777782),
    outlineVariant = Color(0xFFC7C6D1),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onError = Color.White,
    onErrorContainer = Color(0xFF590B0B),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF303038),
    inverseOnSurface = Color(0xFFF2F0FB)
)

/**
 * 暗色主题颜色 - 现代渐变风格
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFD8B4FE),       // 主色调：淡雅紫
    primaryContainer = Color(0xFF411D76),
    secondary = Color(0xFFC4A6FF),     // 辅助色：柔和紫紫
    secondaryContainer = Color(0xFF582D9C),
    tertiary = Color(0xFF67E8F9),      // 强调色：明亮青
    tertiaryContainer = Color(0xFF004C5B),
    background = Color(0xFF0C0C14),    // 背景色：深邃紫黑
    surface = Color(0xFF1C1C24).copy(alpha = 0.92f),  // 表面色：半透明深紫
    surfaceVariant = Color(0xFF2B2B36), // 表面变体：深紫灰
    surfaceBright = Color(0xFF43434E),
    surfaceDim = Color(0xFF1C1C24),
    onPrimary = Color(0xFF3B006A),
    onPrimaryContainer = Color(0xFFE9D5FF),
    onSecondary = Color(0xFF35156A),
    onSecondaryContainer = Color(0xFFDDD6FE),
    onTertiary = Color(0xFF00363F),
    onTertiaryContainer = Color(0xFFCFFAFE),
    onBackground = Color(0xFFE4E1E9),  // 浅色文字
    onSurface = Color(0xFFE4E1E9),
    onSurfaceVariant = Color(0xFFC7C6D1),
    outline = Color(0xFF91919E),
    outlineVariant = Color(0xFF474752),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFF7F1D1D),
    onError = Color.White,
    onErrorContainer = Color(0xFFFEE2E2),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF2F0FB),
    inverseOnSurface = Color(0xFF303038)
)

/**
 * 聊天主题颜色（自定义）- 磨砂玻璃风格
 */
data class ChatColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
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
    primary = Color(0xFF5B21B6),
    secondary = Color(0xFF7C3AED),
    tertiary = Color(0xFF0891B2),
    userMessageColor = Color(0xFFDDD6FE),
    assistantMessageColor = Color(0xFFFFFFFF).copy(alpha = 0.85f),
    systemMessageColor = Color(0xFFF59E0B),
    textPrimary = Color(0xFF1B1B22),
    textSecondary = Color(0xFF6B6B76),
    borderColor = Color(0xFFE5E4EB),
    toolCallBackgroundColor = Color(0xFFE9D5FF),
    // 磨砂玻璃渐变 - 精致紫调
    glassGradientStart = Color(0xFFFAF9FF).copy(alpha = 0.92f),
    glassGradientEnd = Color(0xFFF1F0FA).copy(alpha = 0.85f),
    // 用户消息气泡渐变 (紫色系)
    userBubbleGradientStart = Color(0xFFDDD6FE),
    userBubbleGradientEnd = Color(0xFFC9B0FA),
    // AI消息气泡渐变 (白色系磨砂)
    assistantBubbleGradientStart = Color(0xFFFFFFFF).copy(alpha = 0.95f),
    assistantBubbleGradientEnd = Color(0xFFF8F7FC).copy(alpha = 0.88f)
)

/**
 * 暗色聊天主题 - 磨砂玻璃风格
 */
val DarkChatColors = ChatColors(
    primary = Color(0xFFD8B4FE),
    secondary = Color(0xFFC4A6FF),
    tertiary = Color(0xFF67E8F9),
    userMessageColor = Color(0xFF411D76),
    assistantMessageColor = Color(0xFF2B2B36).copy(alpha = 0.88f),
    systemMessageColor = Color(0xFFFBBF24),
    textPrimary = Color(0xFFE4E1E9),
    textSecondary = Color(0xFF9B9BA6),
    borderColor = Color(0xFF3A3A44),
    toolCallBackgroundColor = Color(0xFF582D9C),
    // 磨砂玻璃渐变 - 精致深紫调
    glassGradientStart = Color(0xFF1C1C24).copy(alpha = 0.95f),
    glassGradientEnd = Color(0xFF16161D).copy(alpha = 0.9f),
    // 用户消息气泡渐变 (紫色系)
    userBubbleGradientStart = Color(0xFF582D9C),
    userBubbleGradientEnd = Color(0xFF411D76),
    // AI消息气泡渐变 (深色系磨砂)
    assistantBubbleGradientStart = Color(0xFF2B2B36).copy(alpha = 0.92f),
    assistantBubbleGradientEnd = Color(0xFF23232B).copy(alpha = 0.87f)
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
