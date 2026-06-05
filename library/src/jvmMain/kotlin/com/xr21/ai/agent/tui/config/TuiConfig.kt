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
package com.xr21.ai.agent.tui.config

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.Theme

/** TUI 配置 */
data class TuiConfig(
    /** 布局比例 */
    val sidebarRatio: Float = 0.28f,
    val chatRatio: Float = 0.48f,
    val infoRatio: Float = 0.24f,

    /** 输入框高度 */
    val inputHeight: Int = 3,
    /** 状态栏高度 */
    val statusBarHeight: Int = 1,

    /** 最大消息数量 */
    val maxMessages: Int = 500,
    /** 最大会话数量 */
    val maxSessions: Int = 50,

    /** 输入历史大小 */
    val inputHistorySize: Int = 100,

    /** 主题 */
    val theme: Theme = Theme.Default,

    /** 颜色配置 */
    val colors: TuiColors = TuiColors(),

    /** Agent 启动命令 */
    val agentCommand: List<String> = emptyList(),

    /** 自动重连 */
    val autoReconnect: Boolean = true,

    /** 重连间隔（毫秒） */
    val reconnectIntervalMs: Long = 3000
)

/** TUI 颜色配置 */
data class TuiColors(
    val primary: TextColors = TextColors.blue,
    val success: TextColors = TextColors.green,
    val warning: TextColors = TextColors.yellow,
    val error: TextColors = TextColors.red,
    val info: TextColors = TextColors.cyan,
    val muted: TextColors = TextColors.gray,
    val userMessage: TextColors = TextColors.brightBlue,
    val assistantMessage: TextColors = TextColors.brightGreen,
    val systemMessage: TextColors = TextColors.brightYellow,
    val toolMessage: TextColors = TextColors.brightMagenta,
    val errorMessage: TextColors = TextColors.brightRed,
    val border: TextColors = TextColors.gray,
    val activeBorder: TextColors = TextColors.brightCyan,
    val statusBar: TextColors = TextColors.gray,
    val inputPrompt: TextColors = TextColors.green
)