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

import com.github.ajalt.mordant.terminal.Terminal
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.theme.TuiTheme

/**
 * 主布局
 *
 * 封装 AppLayout，提供与 TuiApp 的接口。
 */
class MainLayout(
    appState: AppState,
    terminal: Terminal,
    theme: TuiTheme
) {
    private val appLayout = AppLayout(terminal, appState, theme)

    fun render(): String {
        return appLayout.render()
    }
}