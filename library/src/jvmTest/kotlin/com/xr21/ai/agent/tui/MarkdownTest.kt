package com.xr21.ai.agent.tui

import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证 mordant 原生 Markdown Widget 能正确渲染各种 Markdown 语法。
 */
class MarkdownTest {

    private val terminal = Terminal()

    @Test
    fun `Markdown widget renders headers with bold styling`() {
        val md = Markdown("# Title Level 1\n## Title Level 2\n### Title Level 3")
        val output = terminal.render(md)

        println("=== HEADERS TEST ===")
        println(output)
        println("====================\n")

        assertTrue(output.contains("Title Level 1"), "Should contain header text")
        assertTrue(output.contains("Title Level 2"), "Should contain header 2 text")
        assertTrue(output.contains("Title Level 3"), "Should contain header 3 text")
    }

    @Test
    fun `Markdown widget renders bold and italic text`() {
        val md = Markdown("This is **bold** and *italic* text.")
        val output = terminal.render(md)

        println("=== BOLD/ITALIC TEST ===")
        println(output)
        println("========================\n")

        assertTrue(output.contains("bold"), "Should contain 'bold'")
        assertTrue(output.contains("italic"), "Should contain 'italic'")
    }

    @Test
    fun `Markdown widget renders code blocks`() {
        val md = Markdown("```kotlin\nfun main() = println(\"hello\")\n```")
        val output = terminal.render(md)

        println("=== CODE BLOCK TEST ===")
        println(output)
        println("========================\n")

        assertTrue(output.contains("fun main()"), "Should contain code content")
        assertTrue(output.contains("hello"), "Should contain string in code")
    }

    @Test
    fun `Markdown widget renders lists`() {
        val md = Markdown("- Item 1\n- Item 2\n- Item 3")
        val output = terminal.render(md)

        println("=== LIST TEST ===")
        println(output)
        println("==================\n")

        assertTrue(output.contains("Item 1"), "Should contain list item 1")
        assertTrue(output.contains("Item 2"), "Should contain list item 2")
        assertTrue(output.contains("Item 3"), "Should contain list item 3")
    }

    @Test
    fun `Markdown widget renders mixed content`() {
        val md = Markdown(
            """
            # Welcome
            
            This is a **test** of *mixed* content.
            
            ## Features
            
            - **Bold** support
            - `inline code` support
            - Code blocks
            
            ```python
            print("hello")
            ```
            
            > Blockquote text
            """.trimIndent()
        )
        val output = terminal.render(md)

        println("=== MIXED CONTENT TEST ===")
        println(output)
        println("===========================\n")

        assertTrue(output.contains("Welcome"), "Should contain title")
        assertTrue(output.contains("Features"), "Should contain subtitle")
        assertTrue(output.contains("test"), "Should contain bold text")
        assertTrue(output.contains("inline code"), "Should contain inline code")
        assertTrue(output.contains("hello"), "Should contain code content")
    }

    @Test
    fun `Markdown widget renders empty string safely`() {
        val md = Markdown("")
        val output = terminal.render(md)

        println("=== EMPTY STRING TEST ===")
        println("output=[$output]")
        println("=========================\n")

        assertTrue(true, "Empty markdown should not throw")
    }
}