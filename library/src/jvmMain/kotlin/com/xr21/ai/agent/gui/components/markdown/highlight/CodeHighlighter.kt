package com.xr21.ai.agent.gui.components.markdown.highlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * 轻量级代码高亮器
 *
 * 支持基本的语法高亮，包括：
 * - 关键字（fun, val, var, class, etc.）
 * - 字符串（单引号和双引号）
 * - 注释（单行和多行）
 * - 数字
 * - 符号
 */
object CodeHighlighter {

    // Kotlin 关键字列表
    private val kotlinKeywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "sealed",
        "if", "else", "when", "for", "while", "do", "return", "break", "continue",
        "in", "is", "as", "this", "super", "null", "true", "false", "try", "catch",
        "finally", "throw", "import", "package", "private", "public", "protected",
        "internal", "override", "open", "abstract", "final", "data", "inline",
        "suspend", "lateinit", "by", "companion", "init", "constructor"
    )

    // Java 关键字列表
    private val javaKeywords = setOf(
        "public", "private", "protected", "static", "final", "abstract", "class",
        "interface", "enum", "extends", "implements", "new", "return", "void",
        "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
        "try", "catch", "finally", "throw", "throws", "import", "package", "this",
        "super", "null", "true", "false", "instanceof", "synchronized", "volatile",
        "transient", "native", "strictfp", "assert", "default", "boolean", "byte",
        "char", "short", "int", "long", "float", "double"
    )

    // Swift 关键字列表
    private val swiftKeywords = setOf(
        "func", "var", "let", "class", "struct", "enum", "protocol", "extension",
        "if", "else", "guard", "switch", "case", "for", "while", "repeat", "return",
        "break", "continue", "defer", "do", "catch", "throw", "throws", "try",
        "import", "public", "private", "fileprivate", "internal", "open", "static",
        "final", "override", "mutating", "nonmutating", "lazy", "weak", "unowned",
        "convenience", "required", "init", "deinit", "subscript", "associatedtype",
        "typealias", "where", "in", "is", "as", "self", "Self", "nil"
    )

    // 颜色定义
    private val keywordColor = Color(0xFFCC7832)      // 橙色 - 关键字
    private val stringColor = Color(0xFF6A8759)       // 绿色 - 字符串
    private val commentColor = Color(0xFF808080)      // 灰色 - 注释
    private val numberColor = Color(0xFF6897BB)       // 蓝色 - 数字
    private val annotationColor = Color(0xFFBBB529)   // 黄色 - 注解
    private val functionColor = Color(0xFFFFC66D)     // 浅黄 - 函数名

    /**
     * 对代码进行语法高亮处理
     *
     * @param code 原始代码字符串
     * @param language 代码语言（用于选择关键字集）
     * @return 高亮后的 AnnotatedString
     */
    fun highlight(code: String, language: String?): AnnotatedString {
        val keywords = when (language?.lowercase()) {
            "kotlin", "kt" -> kotlinKeywords
            "java" -> javaKeywords
            "swift" -> swiftKeywords
            else -> kotlinKeywords // 默认使用 Kotlin 关键字
        }

        return buildAnnotatedString {
            append(code)

            // 高亮多行注释
            highlightMultiLineComments(code)

            // 高亮单行注释
            highlightSingleLineComments(code)

            // 高亮字符串
            highlightStrings(code)

            // 高亮数字
            highlightNumbers(code)

            // 高亮注解
            highlightAnnotations(code)

            // 高亮关键字
            highlightKeywords(code, keywords)

            // 高亮函数调用
            highlightFunctionCalls(code)
        }
    }

    /**
     * 高亮关键字
     */
    private fun AnnotatedString.Builder.highlightKeywords(code: String, keywords: Set<String>) {
        keywords.forEach { keyword ->
            val regex = "\\b$keyword\\b".toRegex()
            regex.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(
                        color = keywordColor,
                        fontWeight = FontWeight.Bold
                    ),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }

    /**
     * 高亮字符串（单引号和双引号）
     */
    private fun AnnotatedString.Builder.highlightStrings(code: String) {
        // 双引号字符串
        val doubleQuoteRegex = "\"([^\"\\\\]|\\\\.)*\"".toRegex()
        doubleQuoteRegex.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = stringColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // 单引号字符串
        val singleQuoteRegex = "'([^'\\\\]|\\\\.)*'".toRegex()
        singleQuoteRegex.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = stringColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // 多行字符串（Kotlin）
        val tripleQuoteRegex = "\"\"\"[\\s\\S]*?\"\"\"".toRegex()
        tripleQuoteRegex.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = stringColor),
                match.range.first,
                match.range.last + 1
            )
        }
    }

    /**
     * 高亮单行注释
     */
    private fun AnnotatedString.Builder.highlightSingleLineComments(code: String) {
        // // 风格的注释
        val singleLineRegex = "//.*$".toRegex(RegexOption.MULTILINE)
        singleLineRegex.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(
                    color = commentColor,
                    fontStyle = FontStyle.Italic
                ),
                match.range.first,
                match.range.last + 1
            )
        }

        // # 风格的注释（Shell, Python）
        val hashCommentRegex = "#.*$".toRegex(RegexOption.MULTILINE)
        hashCommentRegex.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(
                    color = commentColor,
                    fontStyle = FontStyle.Italic
                ),
                match.range.first,
                match.range.last + 1
            )
        }
    }

    /**
     * 高亮多行注释
     */
    private fun AnnotatedString.Builder.highlightMultiLineComments(code: String) {
        val multiLineRegex = "/\\*[\\s\\S]*?\\*/".toRegex()
        multiLineRegex.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(
                    color = commentColor,
                    fontStyle = FontStyle.Italic
                ),
                match.range.first,
                match.range.last + 1
            )
        }
    }

    /**
     * 高亮数字
     */
    private fun AnnotatedString.Builder.highlightNumbers(code: String) {
        // 整数和浮点数
        val numberRegex = "\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b".toRegex()
        numberRegex.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = numberColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // 十六进制数
        val hexRegex = "\\b0[xX][0-9a-fA-F]+[lL]?\\b".toRegex()
        hexRegex.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = numberColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // 二进制数
        val binaryRegex = "\\b0[bB][01]+[lL]?\\b".toRegex()
        binaryRegex.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = numberColor),
                match.range.first,
                match.range.last + 1
            )
        }
    }

    /**
     * 高亮注解
     */
    private fun AnnotatedString.Builder.highlightAnnotations(code: String) {
        val annotationRegex = "@[A-Za-z_][A-Za-z0-9_]*".toRegex()
        annotationRegex.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = annotationColor),
                match.range.first,
                match.range.last + 1
            )
        }
    }

    /**
     * 高亮函数调用
     */
    private fun AnnotatedString.Builder.highlightFunctionCalls(code: String) {
        // 匹配类似 functionName( 的模式
        val functionRegex = "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(".toRegex()
        functionRegex.findAll(code).forEach { match ->
            val functionName = match.groupValues[1]
            // 排除关键字
            if (!kotlinKeywords.contains(functionName) &&
                !javaKeywords.contains(functionName) &&
                !swiftKeywords.contains(functionName)
            ) {
                addStyle(
                    SpanStyle(color = functionColor),
                    match.range.first,
                    match.range.first + functionName.length
                )
            }
        }
    }

    /**
     * 简单的高亮方法（无语言特定关键字）
     */
    fun highlightSimple(code: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)

            // 高亮字符串
            val doubleQuoteRegex = "\"([^\"\\\\]|\\\\.)*\"".toRegex()
            doubleQuoteRegex.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(color = stringColor),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // 高亮注释
            val singleLineRegex = "//.*$".toRegex(RegexOption.MULTILINE)
            singleLineRegex.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(
                        color = commentColor,
                        fontStyle = FontStyle.Italic
                    ),
                    match.range.first,
                    match.range.last + 1
                )
            }

            val multiLineRegex = "/\\*[\\s\\S]*?\\*/".toRegex()
            multiLineRegex.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(
                        color = commentColor,
                        fontStyle = FontStyle.Italic
                    ),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // 高亮数字
            val numberRegex = "\\b\\d+(\\.\\d+)?\\b".toRegex()
            numberRegex.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(color = numberColor),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }
}
