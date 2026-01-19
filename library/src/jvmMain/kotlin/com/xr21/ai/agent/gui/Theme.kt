package com.xr21.ai.agent.gui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
 * 亮色主题颜色
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF8B5CF6),
    tertiary = Color(0xFF06B6D4),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFEF4444)
)

/**
 * 暗色主题颜色
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF8B5CF6),
    tertiary = Color(0xFF06B6D4),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF334155),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFEF4444)
)

/**
 * 聊天主题颜色（自定义）
 */
data class ChatColors(
    val userMessageColor: Color,
    val assistantMessageColor: Color,
    val systemMessageColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val borderColor: Color
)

/**
 * 亮色聊天主题
 */
val LightChatColors = ChatColors(
    userMessageColor = Color(0xFF6366F1),
    assistantMessageColor = Color(0xFFF1F5F9),
    systemMessageColor = Color(0xFFF59E0B),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    borderColor = Color(0xFFE2E8F0)
)

/**
 * 暗色聊天主题
 */
val DarkChatColors = ChatColors(
    userMessageColor = Color(0xFF6366F1),
    assistantMessageColor = Color(0xFF1E293B),
    systemMessageColor = Color(0xFFF59E0B),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    borderColor = Color(0xFF475569)
)

/**
 * CompositionLocal 用于共享主题状态
 */
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }
val LocalChatColors = staticCompositionLocalOf { DarkChatColors }

/**
 * 应用主题提供者
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
