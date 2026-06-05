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

/**
 * 渐变边框样式
 *
 * 由于 mordant Panel 的 borderStyle 仅支持单色 TextStyle，
 * 此工具类通过直接生成带 ANSI TrueColor 转义码的字符串来实现渐变边框效果。
 *
 * 使用方式：在 buildString 中对每行内容调用 gradientLine()，
 * 然后最外层用 Panel 包裹即可展示彩色渐变边框。
 */

/** 渐变模式 */
enum class GradientMode { GRADIENT, RAINBOW }

/** 渐变颜色配置 */
data class GradientColors(
    val startR: Int, val startG: Int, val startB: Int,
    val endR: Int, val endG: Int, val endB: Int
) {
    companion object {
        val CYAN_TO_PURPLE = GradientColors(0, 200, 255, 160, 0, 255)
        val GREEN_TO_CYAN = GradientColors(0, 255, 128, 0, 200, 255)
        val ORANGE_TO_PINK = GradientColors(255, 180, 0, 255, 80, 180)
        val BLUE_TO_PURPLE = GradientColors(64, 128, 255, 200, 64, 255)
    }
}

/**
 * 构建带渐变颜色的 ANSI 转义字符串
 *
 * @param text 要着色的文本
 * @param startColor 起始颜色
 * @param endColor 结束颜色
 * @param mode 渐变模式
 * @return 完整的 ANSI 转义序列字符串
 */
fun colorizeGradient(
    text: String,
    startColor: GradientColors,
    endColor: GradientColors = startColor,
    mode: GradientMode = GradientMode.GRADIENT
): String {
    if (text.isEmpty()) return ""
    val rst = "\u001B[0m"
    return text.mapIndexed { i, char ->
        val ratio = if (text.length <= 1) 1.0 else i.toDouble() / (text.length - 1)
        val (r, g, b) = when (mode) {
            GradientMode.GRADIENT -> {
                Triple(
                    (startColor.startR + (endColor.endR - startColor.startR) * ratio).toInt().coerceIn(0, 255),
                    (startColor.startG + (endColor.endG - startColor.startG) * ratio).toInt().coerceIn(0, 255),
                    (startColor.startB + (endColor.endB - startColor.startB) * ratio).toInt().coerceIn(0, 255)
                )
            }
            GradientMode.RAINBOW -> rainbowColor(ratio)
        }
        "\u001B[38;2;${r};${g};${b}m$char$rst"
    }.joinToString("")
}

/**
 * 构建水平渐变边框线
 *
 * @param lineStr 边框字符串（如 "╭────────────────────╮"）
 * @param colors 渐变颜色
 * @param mode 渐变模式
 * @return 带 ANSI 转义码的渐变字符串
 */
fun gradientBorderLine(
    lineStr: String,
    colors: GradientColors,
    mode: GradientMode = GradientMode.GRADIENT
): String {
    if (lineStr.isEmpty()) return ""
    val rst = "\u001B[0m"
    return lineStr.mapIndexed { i, char ->
        val ratio = if (lineStr.length <= 1) 1.0 else i.toDouble() / (lineStr.length - 1)
        val (r, g, b) = when (mode) {
            GradientMode.GRADIENT -> {
                Triple(
                    (colors.startR + (colors.endR - colors.startR) * ratio).toInt().coerceIn(0, 255),
                    (colors.startG + (colors.endG - colors.startG) * ratio).toInt().coerceIn(0, 255),
                    (colors.startB + (colors.endB - colors.startB) * ratio).toInt().coerceIn(0, 255)
                )
            }
            GradientMode.RAINBOW -> rainbowColor(ratio)
        }
        "\u001B[38;2;${r};${g};${b}m$char$rst"
    }.joinToString("")
}

private fun rainbowColor(ratio: Double): Triple<Int, Int, Int> {
    val hue = ratio * 360.0
    return when {
        hue < 60 -> Triple(255, (hue / 60 * 255).toInt(), 0)
        hue < 120 -> Triple(((120 - hue) / 60 * 255).toInt(), 255, 0)
        hue < 180 -> Triple(0, 255, ((hue - 120) / 60 * 255).toInt())
        hue < 240 -> Triple(0, ((240 - hue) / 60 * 255).toInt(), 255)
        hue < 300 -> Triple(((hue - 240) / 60 * 255).toInt(), 0, 255)
        else -> Triple(255, 0, ((360 - hue) / 60 * 255).toInt())
    }.let { (r, g, b) -> Triple(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)) }
}

/**
 * 构建完整带渐变边框的面板字符串
 *
 * 直接生成完整的 ANSI 转义字符串，不依赖 mordant Panel Widget。
 * 在 [render] 方法中调用，替代传统的 terminal.render(Panel(...))。
 *
 * @param content 面板内容行列表
 * @param borderType 边框字符风格："rounded" / "double" / "square"
 * @param title 面板标题（可选）
 * @param colors 渐变颜色
 * @param mode 渐变模式
 * @return 完整的面板字符串（含 ANSI 转义码）
 */
fun buildGradientPanel(
    content: List<String>,
    borderType: String = "rounded",
    title: String? = null,
    colors: GradientColors = GradientColors.CYAN_TO_PURPLE,
    mode: GradientMode = GradientMode.GRADIENT
): String {
    val chars = when (borderType) {
        "double" -> mapOf("tl" to "╔", "tr" to "╗", "bl" to "╚", "br" to "╝", "h" to "═", "v" to "║")
        "square" -> mapOf("tl" to "┌", "tr" to "┐", "bl" to "└", "br" to "┘", "h" to "─", "v" to "│")
        else -> mapOf("tl" to "╭", "tr" to "╮", "bl" to "╰", "br" to "╯", "h" to "─", "v" to "│")
    }

    val contentWidth = content.maxOfOrNull { it.length } ?: 0
    val innerWidth = contentWidth.coerceAtLeast(title?.length?.plus(2) ?: 2)
    val sb = StringBuilder()
    val rst = "\u001B[0m"

    // 构建标题区域
    val titleStr = if (title != null) {
        val paddedTitle = " $title "
        val leftPad = (innerWidth - paddedTitle.length) / 2
        val rightPad = innerWidth - paddedTitle.length - leftPad
        chars["h"]!!.repeat(leftPad) + paddedTitle + chars["h"]!!.repeat(rightPad)
    } else {
        chars["h"]!!.repeat(innerWidth)
    }

    // 顶边框 + 标题
    val topLine = chars["tl"]!! + titleStr + chars["tr"]!!
    sb.appendLine(gradientBorderLine(topLine, colors, mode))

    // 内容行
    for (line in content) {
        val padded = line.padEnd(innerWidth)
        val leftV = gradientSingleChar(chars["v"]!!, colors, mode, innerWidth, 0)
        val rightV = gradientSingleChar(chars["v"]!!, colors, mode, innerWidth, innerWidth + 1)
        sb.appendLine(leftV + padded + rightV)
    }

    // 底边框
    val bottomLine = chars["bl"]!! + chars["h"]!!.repeat(innerWidth) + chars["br"]!!
    sb.appendLine(gradientBorderLine(bottomLine, colors, mode))

    return sb.toString()
}

/**
 * 给单个字符着色（用于垂直边框）
 */
private fun gradientSingleChar(
    char: String,
    colors: GradientColors,
    mode: GradientMode,
    totalWidth: Int,
    position: Int
): String {
    val ratio = if (totalWidth <= 1) 0.5 else position.toDouble() / (totalWidth + 2)
    val (r, g, b) = when (mode) {
        GradientMode.GRADIENT -> {
            Triple(
                (colors.startR + (colors.endR - colors.startR) * ratio).toInt().coerceIn(0, 255),
                (colors.startG + (colors.endG - colors.startG) * ratio).toInt().coerceIn(0, 255),
                (colors.startB + (colors.endB - colors.startB) * ratio).toInt().coerceIn(0, 255)
            )
        }
        GradientMode.RAINBOW -> rainbowColor(ratio)
    }
    val rst = "\u001B[0m"
    return "\u001B[38;2;${r};${g};${b}m$char$rst"
}
