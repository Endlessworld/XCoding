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

import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.widgets.Text
import com.xr21.ai.agent.tui.acp.ConnectionState
import com.xr21.ai.agent.tui.state.AppState
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 底部状态栏
 *
 * TODO: 1.12 阶段实现完整的状态栏
 */
class StatusBar(private val appState: AppState) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun render(): Text {
        val connSymbol = when (appState.connectionState) {
            ConnectionState.CONNECTED -> "● 已连接"
            ConnectionState.CONNECTING -> "◌ 连接中"
            ConnectionState.DISCONNECTED -> "○ 断开"
            ConnectionState.RECONNECTING -> "◌ 重连中"
            ConnectionState.DISCONNECTED_ERROR -> "✕ 错误"
        }

        val time = LocalTime.now().format(timeFormatter)

        val text = buildString {
            append(" ${appState.agentName} ${appState.agentVersion}")
            append(" │ ${connSymbol}")
            append(" │ 模型: ${appState.modelName.ifEmpty { "—" }}")
            append(" │ 会话: ${appState.sessionCount}/${appState.totalSessions}")
            append(" │ ${time}")
        }

        return Text(TextStyles.dim(text))
    }
}

