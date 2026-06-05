/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.tui.theme

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles

/**
 * TUI 主题系统
 *
 * 提供完整的颜色、边框、文字样式配置。
 * 默认使用 Modern Dark 主题，灵感来自 VS Code Dark+ 与 GitHub Dark。
 */
data class TuiTheme(
    // 面板边框
    val borderNormal: TextStyle,
    val borderFocused: TextStyle,

    // 面板标题
    val panelTitle: TextStyle,
    val panelTitleFocused: TextStyle,

    // 基础文字
    val textPrimary: TextStyle,
    val textSecondary: TextStyle,
    val textMuted: TextStyle,

    // 语义色
    val accent: TextStyle,
    val success: TextStyle,
    val warning: TextStyle,
    val error: TextStyle,
    val info: TextStyle,

    // 消息角色色
    val userMessage: TextStyle,
    val assistantMessage: TextStyle,
    val systemMessage: TextStyle,
    val toolMessage: TextStyle,
    val errorMessage: TextStyle,

    // 状态栏
    val statusBarText: TextStyle,
    val statusConnected: TextStyle,
    val statusConnecting: TextStyle,
    val statusDisconnected: TextStyle,
    val statusError: TextStyle,

    // 输入框
    val inputPrompt: TextStyle,
    val inputText: TextStyle,

    // 列表交互
    val selectedText: TextStyle,
    val currentIndicator: TextStyle,

    // 滚动提示
    val scrollHint: TextStyle,

    // 快捷键提示
    val keyHint: TextStyle
)

/**
 * Modern Dark 主题预设
 *
 * 采用高对比度暗色配色，适合长时间编码/对话场景：
 * - 边框: 柔和灰 / 明亮青（焦点）
 * - 消息: 用户(蓝) AI(绿) 系统(琥珀) 工具(紫) 错误(红)
 * - 状态: 连接(绿) 连接中(琥珀) 断开(灰) 错误(红)
 */
fun modernDarkTheme(): TuiTheme = TuiTheme(
    borderNormal = TextColors.gray,
    borderFocused = TextColors.brightCyan,

    panelTitle = TextColors.brightWhite + TextStyles.bold,
    panelTitleFocused = TextColors.brightCyan + TextStyles.bold,

    textPrimary = TextColors.brightWhite,
    textSecondary = TextColors.white,
    textMuted = TextColors.gray + TextStyles.dim,

    accent = TextColors.brightCyan + TextStyles.bold,
    success = TextColors.brightGreen,
    warning = TextColors.brightYellow,
    error = TextColors.brightRed,
    info = TextColors.brightBlue,

    userMessage = TextColors.brightBlue,
    assistantMessage = TextColors.brightGreen,
    systemMessage = TextColors.brightYellow,
    toolMessage = TextColors.brightMagenta,
    errorMessage = TextColors.brightRed + TextStyles.bold,

    statusBarText = TextColors.gray,
    statusConnected = TextColors.brightGreen,
    statusConnecting = TextColors.brightYellow,
    statusDisconnected = TextColors.gray,
    statusError = TextColors.brightRed,

    inputPrompt = TextColors.gray + TextStyles.dim,
    inputText = TextColors.brightWhite,

    selectedText = TextColors.brightCyan + TextStyles.bold,
    currentIndicator = TextColors.brightGreen,

    scrollHint = TextColors.brightCyan + TextStyles.dim,

    keyHint = TextColors.gray + TextStyles.dim
)
