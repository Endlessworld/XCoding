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
package com.xr21.ai.agent.tui.layout

import com.github.ajalt.mordant.widgets.Text
import com.xr21.ai.agent.tui.acp.ConnectionState
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.theme.TuiTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 底部状态栏
 *
 * 带颜色的状态信息栏，显示连接状态、模型、会话数和时间。
 */
class StatusBar(
    private val appState: AppState,
    private val theme: TuiTheme
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun render(): Text {
        val (connSymbol, connStyle) = when (appState.connectionState) {
            ConnectionState.CONNECTED -> "● 已连接" to theme.statusConnected
            ConnectionState.CONNECTING -> "◌ 连接中" to theme.statusConnecting
            ConnectionState.DISCONNECTED -> "○ 断开" to theme.statusDisconnected
            ConnectionState.RECONNECTING -> "◌ 重连中" to theme.statusConnecting
            ConnectionState.DISCONNECTED_ERROR -> "✕ 错误" to theme.statusError
        }

        val time = LocalTime.now().format(timeFormatter)
        val sep = theme.statusBarText(" │ ")

        val text = buildString {
            append(theme.textSecondary(" ${appState.agentName} ${appState.agentVersion}"))
            append(sep)
            append(connStyle(connSymbol))
            append(sep)
            append(theme.statusBarText("模型: ") + theme.textPrimary(appState.modelName.ifEmpty { "—" }))
            append(sep)
            append(theme.statusBarText("会话: ") + theme.info("${appState.sessionCount}/${appState.totalSessions}"))
            append(sep)
            append(theme.accent(time))
        }

        return Text(text, whitespace = com.github.ajalt.mordant.rendering.Whitespace.PRE)
    }
}

