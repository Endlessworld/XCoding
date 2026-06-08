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

/** TUI 配置 */
data class ACPConnectConfig(

    /** Agent 启动命令（Stdio 模式） */
    val agentCommand: List<String> = emptyList(),

    /** WebSocket 连接地址（为空时启动内部 WebSocket 服务器） */
    val webSocketUrl: String = "",

    /** WebSocket 服务器端口（内部启动时使用） */
    val webSocketServerPort: Int = 9988,

    /** 自动重连 */
    val autoReconnect: Boolean = true,

    /** 重连间隔（毫秒） */
    val reconnectIntervalMs: Long = 3000
)