package com.xr21.ai.agent.gui.components.markdown.parser

import org.commonmark.node.Document
import org.commonmark.parser.Parser

/**
 * Markdown 解析器
 *
 * 使用 CommonMark 库将 Markdown 文本解析为内部节点模型。
 */
class MarkdownParser {

    private val parser: Parser = Parser.builder().build()

    /**
     * 解析 Markdown 文本为文档节点
     *
     * @param markdown Markdown 原始文本
     * @return 解析后的 Document 节点
     */
    fun parse(markdown: String): Document {
        return parser.parse(markdown) as Document
    }

    companion object {
        @Volatile
        private var instance: MarkdownParser? = null

        /**
         * 获取单例实例
         */
        fun getInstance(): MarkdownParser {
            return instance ?: synchronized(this) {
                instance ?: MarkdownParser().also { instance = it }
            }
        }
    }
}
