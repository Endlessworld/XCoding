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

/** 字符串工具类 */
object StringUtils {

    /**
     * 截断字符串到指定宽度，保留尾部
     */
    fun truncateTail(text: String, maxWidth: Int): String {
        if (text.length <= maxWidth) return text
        return text.take(maxWidth - 1) + "…"
    }

    /**
     * 截断字符串到指定宽度，保留首尾
     */
    fun truncateMiddle(text: String, maxWidth: Int): String {
        if (text.length <= maxWidth) return text
        val half = (maxWidth - 2) / 2
        return text.take(half) + "…" + text.takeLast(half)
    }

    /**
     * 将文本包装为指定宽度的行列表
     */
    fun wrapText(text: String, width: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        if (width <= 0) return listOf(text)

        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (char in text) {
            if (char == '\n') {
                lines.add(current.toString())
                current = StringBuilder()
                continue
            }
            if (current.length >= width) {
                lines.add(current.toString())
                current = StringBuilder()
            }
            current.append(char)
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines
    }

    /**
     * 格式化时间戳
     */
    fun formatTimestamp(hours: Int, minutes: Int): String {
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
    }

    /**
     * 安全地获取字符串的前 n 行
     */
    fun firstNLines(text: String, n: Int): String {
        return text.lines().take(n).joinToString("\n")
    }

    /**
     * 移除 ANSI 转义序列
     */
    fun stripAnsi(text: String): String {
        return text.replace(Regex("\u001b\\[[\\d;]*[A-Za-z]"), "")
    }

    /**
     * 格式化 Token 数量
     */
    fun formatTokenCount(count: Long): String {
        return when {
            count < 1000 -> count.toString()
            count < 1_000_000 -> "${count / 1000}K"
            else -> "${count / 1_000_000}M"
        }
    }
}