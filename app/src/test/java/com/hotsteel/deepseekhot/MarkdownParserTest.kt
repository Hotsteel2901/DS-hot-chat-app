// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot

import com.hotsteel.deepseekhot.ui.MarkdownParser
import com.hotsteel.deepseekhot.ui.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun headingsAndParagraphs() {
        val blocks = MarkdownParser.parse("# 标题\n\n正文第一段\n第二行\n\n## 小标题")
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertEquals("正文第一段\n第二行", (blocks[1] as MdBlock.Paragraph).text)
    }

    @Test
    fun codeFenceWithLanguage() {
        val blocks = MarkdownParser.parse("说明\n```kotlin\nval a = 1\nprintln(a)\n```\n结束")
        assertEquals(3, blocks.size)
        val code = blocks[1] as MdBlock.Code
        assertEquals("kotlin", code.lang)
        assertEquals("val a = 1\nprintln(a)", code.code)
    }

    @Test
    fun unfinishedFenceDoesNotCrash() {
        val blocks = MarkdownParser.parse("```python\nprint(1)")
        assertEquals(1, blocks.size)
        assertEquals("print(1)", (blocks[0] as MdBlock.Code).code)
    }

    @Test
    fun listsAndTable() {
        val blocks = MarkdownParser.parse("- 一\n  - 嵌套\n- 二\n\n| A | B |\n| --- | --- |\n| 1 | 2 |")
        val bullets = blocks[0] as MdBlock.Bullets
        assertEquals(3, bullets.items.size)
        assertEquals(1, bullets.items[1].first)
        val table = blocks[1] as MdBlock.Table
        assertEquals(listOf("A", "B"), table.header)
        assertEquals(listOf("1", "2"), table.rows[0])
    }

    @Test
    fun quoteAndRule() {
        val blocks = MarkdownParser.parse("> 引用\n> 第二行\n\n---\n")
        assertEquals(2, blocks.size)
        assertEquals("引用\n第二行", (blocks[0] as MdBlock.Quote).text)
        assertTrue(blocks[1] is MdBlock.Rule)
    }

    @Test
    fun pathologicalInlineMarkersDoNotHang() {
        // 这些片段主要验证块解析稳定；行内解析的同类用例见 appendInline 的兜底分支
        val samples = listOf("~~~", "*", "_", "`", "[", "[x](", "***", "a**b", "******")
        samples.forEach { s ->
            val blocks = MarkdownParser.parse(s)
            assertTrue(blocks.isNotEmpty())
        }
    }

    @Test
    fun mathBlockDelimitersAndCalloutAndTasks() {
        val md = """
            \[
            E = mc^2
            \]

            \begin{align}
            a + b &= c
            \end{align}

            > [!TIP]
            > 支持完整公式与图表

            - [x] 已完成
            - [ ] 待完成

            ![示例图](https://example.com/a.png)
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        assertTrue(blocks.any { it is MdBlock.Math && it.text.contains("E = mc^2") })
        assertTrue(blocks.any { it is MdBlock.Math && it.text.contains("\\begin{align}") })
        assertTrue(blocks.any { it is MdBlock.Callout && it.kind == "TIP" })
        assertTrue(blocks.any { it is MdBlock.TaskList && it.items.size == 2 && it.items[0].checked && !it.items[1].checked })
        assertTrue(blocks.any { it is MdBlock.Image && it.alt == "示例图" })
    }
}
