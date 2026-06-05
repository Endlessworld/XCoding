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

/**
 * 底部输入面板
 *
 * TODO: 1.9 阶段实现完整的多行输入框
 */
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.widgets.Text
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.theme.GradientPanelWidget
import com.xr21.ai.agent.tui.theme.TuiTheme

class InputPanel(
    private val appState: AppState,
    private val theme: TuiTheme
) {
    fun render(isFocused: Boolean = false, availableLines: Int = 3): Widget {
        val borderType = if (isFocused) BorderType.DOUBLE else BorderType.ROUNDED
        val borderStyle = if (isFocused) theme.borderFocused else theme.borderNormal
        val titleStyle = if (isFocused) theme.panelTitleFocused else theme.panelTitle

        val allLines = if (appState.inputBuffer.isEmpty()) {
            listOf(theme.inputPrompt("> 输入指令...  [Enter 发送, Alt+Enter 换行]"))
        } else {
            appState.inputBuffer.lines().map { theme.inputText("> $it") }
        }

        val maxOffset = (allLines.size - availableLines).coerceAtLeast(0)
        val offset = if (appState.inputScrollOffset == Int.MAX_VALUE || appState.inputScrollOffset > maxOffset) {
            maxOffset
        } else {
            appState.inputScrollOffset.coerceIn(0, maxOffset)
        }
        val visibleLines = allLines.drop(offset).take(availableLines)

        val scrollHint = when {
            offset > 0 && maxOffset > 0 && offset < maxOffset -> theme.scrollHint("↑ $offset/$maxOffset ↓")
            offset > 0 -> theme.scrollHint("↑ $offset/$maxOffset")
            maxOffset > 0 -> theme.scrollHint("↓")
            else -> null
        }

        val content = buildString {
            scrollHint?.let { appendLine(it) }
            visibleLines.forEach { appendLine(it) }
        }.trimEnd().ifEmpty { theme.inputText("> ") }

        return GradientPanelWidget(
            content = Text(content),
            title = Text(titleStyle("Input")),
            titleAlign = TextAlign.LEFT,
            borderType = borderType,
            colors = theme.gradientInput,
            mode = theme.gradientMode,
            fallbackStyle = borderStyle
        )
    }
}