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
package com.xr21.ai.agent.tui.util

/**
 * 简易 Markdown 解析器
 *
 * 将 Markdown 文本转换为 mordant 富文本。
 * 支持：粗体、斜体、代码块、行内代码、标题、列表。
 *
 * TODO: 2.2 阶段实现完整的 Markdown 渲染
 */
class MarkdownParser {

    /**
     * 将 Markdown 文本解析为带 ANSI 格式的文本
     */
    fun parse(text: String): String {
        val lines = text.lines()
        val result = StringBuilder()

        var inCodeBlock = false
        var codeBlockLang = ""

        for (line in lines) {
            when {
                // 代码块开始/结束
                line.trimStart().startsWith("```") -> {
                    if (inCodeBlock) {
                        result.appendLine("${ANSI_RESET}${ANSI_DIM}```${ANSI_RESET}")
                        inCodeBlock = false
                    } else {
                        codeBlockLang = line.trimStart().removePrefix("```").trim()
                        result.appendLine("${ANSI_DIM}┌─ ${codeBlockLang.ifEmpty { "code" }}${ANSI_RESET}")
                        inCodeBlock = true
                    }
                }
                // 在代码块内
                inCodeBlock -> {
                    result.appendLine("${ANSI_DIM}│${ANSI_RESET} $line")
                }
                // 标题
                line.startsWith("# ") -> result.appendLine("${ANSI_BOLD}${line.removePrefix("# ")}${ANSI_RESET}")
                line.startsWith("## ") -> result.appendLine("${ANSI_BOLD}${line.removePrefix("## ")}${ANSI_RESET}")
                line.startsWith("### ") -> result.appendLine("${ANSI_BOLD}${ANSI_CYAN}${line.removePrefix("### ")}${ANSI_RESET}")
                // 列表项
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val indent = line.length - line.trimStart().length
                    val content = line.trimStart().removePrefix("- ").removePrefix("* ")
                    result.appendLine("${" ".repeat(indent)}• $content")
                }
                // 数字列表
                line.matches(Regex("^\\s*\\d+\\.\\s.*")) -> {
                    result.appendLine(line)
                }
                // 引用
                line.trimStart().startsWith(">") -> {
                    result.appendLine("${ANSI_DIM}│${ANSI_RESET} ${line.trimStart().removePrefix(">").trim()}")
                }
                // 分隔线
                line.trimStart().startsWith("---") || line.trimStart().startsWith("***") -> {
                    result.appendLine("${ANSI_DIM}${"─".repeat(40)}${ANSI_RESET}")
                }
                // 空行
                line.isBlank() -> result.appendLine()
                // 普通文本（处理行内格式）
                else -> result.appendLine(parseInlineFormatting(line))
            }
        }

        return result.toString()
    }

    private fun parseInlineFormatting(text: String): String {
        var result = text
        // 粗体 **text**
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*")) { "${ANSI_BOLD}${it.groupValues[1]}${ANSI_RESET}" }
        // 行内代码 `code`
        result = result.replace(Regex("`([^`]+)`")) { "${ANSI_YELLOW}${it.groupValues[1]}${ANSI_RESET}" }
        // 斜体 *text*
        result = result.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")) { "${ANSI_ITALIC}${it.groupValues[1]}${ANSI_RESET}" }
        return result
    }

    companion object {
        private const val ANSI_RESET = "\u001b[0m"
        private const val ANSI_BOLD = "\u001b[1m"
        private const val ANSI_DIM = "\u001b[2m"
        private const val ANSI_ITALIC = "\u001b[3m"
        private const val ANSI_CYAN = "\u001b[36m"
        private const val ANSI_YELLOW = "\u001b[33m"
    }
}