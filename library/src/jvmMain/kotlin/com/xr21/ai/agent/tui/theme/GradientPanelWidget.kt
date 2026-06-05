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

import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.terminal.Terminal

/**
 * 渐变面板 Widget
 *
 * 继承自 mordant [Widget] 接口，在 [render] 阶段直接生成带 TextStyle 渐变色
 * 的边框 Span。当终端不支持 TrueColor 时，自动回退到 [fallbackStyle]。
 */
class GradientPanelWidget(
    private val content: Widget,
    private val title: Widget? = null,
    private val titleAlign: TextAlign = TextAlign.CENTER,
    private val borderType: BorderType = BorderType.ROUNDED,
    private val colors: GradientColors = GradientColors.CYAN_TO_PURPLE,
    private val mode: GradientMode = GradientMode.GRADIENT,
    private val fallbackStyle: TextStyle? = null
) : Widget {

    override fun measure(t: Terminal, width: Int): WidthRange {
        val contentRange = content.measure(t, width - 4)
        val titleRange = title?.measure(t, width - 4) ?: WidthRange(0, 0)
        val minW = maxOf(contentRange.min, titleRange.min) + 4
        val maxW = maxOf(contentRange.max, titleRange.max) + 4
        return WidthRange(minW.coerceAtMost(width), maxW.coerceAtMost(width))
    }

    override fun render(t: Terminal, width: Int): Lines {
        val innerWidth = (width - 4).coerceAtLeast(1)
        val contentLines = content.render(t, innerWidth)
        val titleLines = title?.render(t, innerWidth) ?: Lines(emptyList())
        val titleStr = titleLines.lines.joinToString(" ") { line ->
            line.joinToString("") { it.text }
        }
        val textLines = contentLines.lines.map { line ->
            line.joinToString("") { it.text }
        }

        // 当终端不支持 TrueColor 且提供了 fallback 样式时，使用普通 Panel
        if (fallbackStyle != null && t.info.ansiLevel != AnsiLevel.TRUECOLOR) {
            val panel = com.github.ajalt.mordant.widgets.Panel(
                content = content,
                title = title,
                titleAlign = titleAlign,
                borderType = borderType,
                borderStyle = fallbackStyle
            )
            return panel.render(t, width)
        }

        val panelLines = buildGradientPanelLines(
            content = textLines,
            title = titleStr.ifEmpty { null },
            width = width,
            borderType = borderChars(borderType),
            colors = colors,
            mode = mode
        )

        return Lines(panelLines)
    }
}

/**
 * 获取边框字符映射
 */
private fun borderChars(type: BorderType): Map<String, String> {
    return when (type) {
        BorderType.DOUBLE -> mapOf(
            "tl" to "╔", "tr" to "╗", "bl" to "╚", "br" to "╝",
            "h" to "═", "v" to "║"
        )
        BorderType.SQUARE -> mapOf(
            "tl" to "┌", "tr" to "┐", "bl" to "└", "br" to "┘",
            "h" to "─", "v" to "│"
        )
        BorderType.ASCII -> mapOf(
            "tl" to "+", "tr" to "+", "bl" to "+", "br" to "+",
            "h" to "-", "v" to "|"
        )
        else -> mapOf(
            "tl" to "╭", "tr" to "╮", "bl" to "╰", "br" to "╯",
            "h" to "─", "v" to "│"
        )
    }
}

/**
 * 构建渐变面板的每一行
 */
private fun buildGradientPanelLines(
    content: List<String>,
    title: String?,
    width: Int,
    borderType: Map<String, String>,
    colors: GradientColors,
    mode: GradientMode
): List<Line> {
    val innerWidth = (width - 4).coerceAtLeast(1)
    val lines = mutableListOf<Line>()
    val defaultStyle = TextStyles.reset.style

    // 构建标题区域
    val titleStr = if (title != null && title.isNotEmpty()) {
        val paddedTitle = " $title "
        val leftPad = (innerWidth - paddedTitle.length) / 2
        val rightPad = innerWidth - paddedTitle.length - leftPad
        borderType["h"]!!.repeat(leftPad.coerceAtLeast(0)) +
            paddedTitle +
            borderType["h"]!!.repeat(rightPad.coerceAtLeast(0))
    } else {
        borderType["h"]!!.repeat(innerWidth)
    }

    // 顶边框 + 标题
    val topLineText = borderType["tl"]!! + titleStr + borderType["tr"]!!
    lines.add(
        Line(
            gradientLineSpans(topLineText, colors, mode, defaultStyle),
            defaultStyle
        )
    )

    // 内容行
    for (line in content) {
        val padded = line.padEnd(innerWidth)
        val leftV = singleGradientCharSpan(borderType["v"]!!, colors, mode, innerWidth, 0)
        val rightV = singleGradientCharSpan(
            borderType["v"]!!, colors, mode, innerWidth, innerWidth + 1
        )
        val contentSpans = mutableListOf<Span>()
        contentSpans.add(leftV)
        // 将内容文本按空白/非空白分段，避免混合空白和非空白
        contentSpans.addAll(splitIntoSpans(padded, defaultStyle))
        contentSpans.add(rightV)
        lines.add(Line(contentSpans, defaultStyle))
    }

    // 底边框
    val bottomLineText = borderType["bl"]!! +
        borderType["h"]!!.repeat(innerWidth) + borderType["br"]!!
    lines.add(
        Line(
            gradientLineSpans(bottomLineText, colors, mode, defaultStyle),
            defaultStyle
        )
    )

    return lines
}

/**
 * 给单个字符创建渐变颜色 Span（用于垂直边框）
 */
private fun singleGradientCharSpan(
    char: String,
    colors: GradientColors,
    mode: GradientMode,
    totalWidth: Int,
    position: Int,
    baseStyle: TextStyle = TextStyles.reset.style
): Span {
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
    val colorStyle = TextColors.rgb(r / 255.0, g / 255.0, b / 255.0)
    return Span.word(char, baseStyle + colorStyle)
}

/**
 * 构建水平渐变边框线的 Span 列表
 */
private fun gradientLineSpans(
    lineStr: String,
    colors: GradientColors,
    mode: GradientMode = GradientMode.GRADIENT,
    baseStyle: TextStyle = TextStyles.reset.style
): List<Span> {
    if (lineStr.isEmpty()) return emptyList()
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
        val colorStyle = TextColors.rgb(r / 255.0, g / 255.0, b / 255.0)
        Span.word(char.toString(), baseStyle + colorStyle)
    }
}

/**
 * 彩虹色计算
 */
private fun rainbowColor(ratio: Double): Triple<Int, Int, Int> {
    val hue = ratio * 360.0
    return when {
        hue < 60 -> Triple(255, (hue / 60 * 255).toInt(), 0)
        hue < 120 -> Triple(((120 - hue) / 60 * 255).toInt(), 255, 0)
        hue < 180 -> Triple(0, 255, ((hue - 120) / 60 * 255).toInt())
        hue < 240 -> Triple(0, ((240 - hue) / 60 * 255).toInt(), 255)
        hue < 300 -> Triple(((hue - 240) / 60 * 255).toInt(), 0, 255)
        else -> Triple(255, 0, ((360 - hue) / 60 * 255).toInt())
    }.let { (r, g, b) ->
        Triple(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}


/**
 * 将文本按空白/非空白分段，每段分别创建 Span，避免混合空白和非空白违反 Span.word 约束
 */
private fun splitIntoSpans(text: String, style: TextStyle): List<Span> {
    if (text.isEmpty()) return emptyList()
    val spans = mutableListOf<Span>()
    val sb = StringBuilder()
    var isWhitespace: Boolean? = null
    for (ch in text) {
        val chWhitespace = ch.isWhitespace()
        if (isWhitespace == null) {
            isWhitespace = chWhitespace
            sb.append(ch)
        } else if (chWhitespace == isWhitespace) {
            sb.append(ch)
        } else {
            spans.add(Span.word(sb.toString(), style))
            sb.clear()
            sb.append(ch)
            isWhitespace = chWhitespace
        }
    }
    if (sb.isNotEmpty()) {
        spans.add(Span.word(sb.toString(), style))
    }
    return spans
}
