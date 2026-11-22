// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Sys
import kotlinx.coroutines.delay

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Bullets(val items: List<Pair<Int, String>>) : MdBlock
    data class Ordered(val items: List<Pair<Int, String>>, val start: Int) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data class Math(val text: String) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {

    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val ordered = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val rule = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

    fun parse(src: String): List<MdBlock> {
        val lines = src.replace("\r\n", "\n").split('\n')
        val out = mutableListOf<MdBlock>()
        var i = 0
        val paragraph = StringBuilder()

        fun flush() {
            if (paragraph.isNotEmpty()) {
                out.add(MdBlock.Paragraph(paragraph.toString().trim()))
                paragraph.clear()
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                flush()
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i])
                    i++
                }
                i++ // 跳过结束围栏
                out.add(MdBlock.Code(lang.ifBlank { "text" }, code.toString().trimEnd('\n')))
                continue
            }

            if (trimmed.isEmpty()) {
                flush()
                i++
                continue
            }

            val h = heading.find(trimmed)
            if (h != null) {
                flush()
                out.add(MdBlock.Heading(h.groupValues[1].length, h.groupValues[2].trim()))
                i++
                continue
            }

            if (rule.matches(trimmed)) {
                flush()
                out.add(MdBlock.Rule)
                i++
                continue
            }

            if (trimmed.startsWith("$$")) {
                flush()
                val body = StringBuilder()
                var first = trimmed.removePrefix("$$")
                if (first.endsWith("$$") && first.length > 2) {
                    body.append(first.removeSuffix("$$"))
                    i++
                } else {
                    if (first.isNotBlank()) body.appendLine(first)
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                        body.appendLine(lines[i])
                        i++
                    }
                    if (i < lines.size) {
                        body.appendLine(lines[i].trim().removeSuffix("$$"))
                        i++
                    }
                }
                out.add(MdBlock.Math(body.toString().trim()))
                continue
            }

            if (trimmed.startsWith(">")) {
                flush()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote.appendLine(lines[i].trim().removePrefix(">").trimStart())
                    i++
                }
                out.add(MdBlock.Quote(quote.toString().trim()))
                continue
            }

            if (trimmed.startsWith("|") && i + 1 < lines.size) {
                val next = lines[i + 1].trim()
                if (next.startsWith("|") && next.replace("|", "").replace(":", "").replace("-", "").isBlank()) {
                    flush()
                    val header = splitRow(trimmed)
                    val rows = mutableListOf<List<String>>()
                    i += 2
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        rows.add(splitRow(lines[i].trim()))
                        i++
                    }
                    out.add(MdBlock.Table(header, rows))
                    continue
                }
            }

            val ol = ordered.find(line)
            if (ol != null) {
                flush()
                val start = ol.groupValues[2].toIntOrNull() ?: 1
                val items = mutableListOf<Pair<Int, String>>()
                while (i < lines.size) {
                    val m = ordered.find(lines[i]) ?: break
                    items.add(indentLevel(m.groupValues[1]) to m.groupValues[3])
                    i++
                }
                out.add(MdBlock.Ordered(items, start))
                continue
            }

            val bl = bullet.find(line)
            if (bl != null) {
                flush()
                val items = mutableListOf<Pair<Int, String>>()
                while (i < lines.size) {
                    val m = bullet.find(lines[i]) ?: break
                    items.add(indentLevel(m.groupValues[1]) to m.groupValues[2])
                    i++
                }
                out.add(MdBlock.Bullets(items))
                continue
            }

            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(trimmed)
            i++
        }
        flush()
        return out
    }

    private fun indentLevel(spaces: String): Int = (spaces.replace("\t", "  ").length / 2).coerceIn(0, 4)

    private fun splitRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
}

const val LINK_TAG = "md-link"

fun inlineAnnotated(
    text: String,
    base: SpanStyle,
    linkColor: Color,
    codeColor: Color,
    codeBg: Color,
): AnnotatedString = buildAnnotatedString {
    appendInline(this, text, base, linkColor, codeColor, codeBg)
}

private fun appendInline(
    builder: AnnotatedString.Builder,
    text: String,
    style: SpanStyle,
    linkColor: Color,
    codeColor: Color,
    codeBg: Color,
) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("***", i) || text.startsWith("___", i) -> {
                val marker = text.substring(i, i + 3)
                val end = text.indexOf(marker, i + 3)
                if (end > i) {
                    builder.pushStyle(style.copy(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    appendInline(builder, text.substring(i + 3, end), style, linkColor, codeColor, codeBg)
                    builder.pop()
                    i = end + 3
                } else {
                    builder.append(marker)
                    i += 3
                }
            }

            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end > i) {
                    builder.pushStyle(style.copy(fontWeight = FontWeight.Bold))
                    appendInline(builder, text.substring(i + 2, end), style, linkColor, codeColor, codeBg)
                    builder.pop()
                    i = end + 2
                } else {
                    builder.append(marker)
                    i += 2
                }
            }

            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i) {
                    builder.pushStyle(style.copy(textDecoration = TextDecoration.LineThrough))
                    appendInline(builder, text.substring(i + 2, end), style, linkColor, codeColor, codeBg)
                    builder.pop()
                    i = end + 2
                } else {
                    builder.append("~~")
                    i += 2
                }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    builder.withStyle(
                        style.copy(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            background = codeBg,
                            fontSize = 13.5.sp,
                        )
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    builder.append('`')
                    i++
                }
            }

            text[i] == '$' && i + 1 < text.length && text[i + 1] != '$' -> {
                val end = text.indexOf('$', i + 1)
                if (end > i + 1) {
                    builder.withStyle(
                        style.copy(
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic,
                            color = linkColor,
                        )
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    builder.append('$')
                    i++
                }
            }

            text[i] == '[' -> {
                val close = text.indexOf(']', i + 1)
                val open = if (close > 0) text.indexOf('(', close) else -1
                val end = if (open > 0) text.indexOf(')', open) else -1
                if (close > 0 && open == close + 1 && end > open) {
                    val label = text.substring(i + 1, close)
                    val url = text.substring(open + 1, end).trim()
                    val start = builder.length
                    builder.pushStyle(style.copy(color = linkColor, textDecoration = TextDecoration.Underline))
                    appendInline(builder, label, style, linkColor, codeColor, codeBg)
                    builder.pop()
                    if (url.isNotBlank()) builder.addStringAnnotation(LINK_TAG, url, start, builder.length)
                    i = end + 1
                } else {
                    builder.append('[')
                    i++
                }
            }

            text[i] == '*' || text[i] == '_' -> {
                val marker = text[i].toString()
                val end = text.indexOf(marker, i + 1)
                if (end > i + 1) {
                    builder.pushStyle(style.copy(fontStyle = FontStyle.Italic))
                    appendInline(builder, text.substring(i + 1, end), style, linkColor, codeColor, codeBg)
                    builder.pop()
                    i = end + 1
                } else {
                    builder.append(marker)
                    i++
                }
            }

            else -> {
                val next = text.indexOfFirst(i) { it == '*' || it == '_' || it == '`' || it == '[' || it == '~' }
                val until = if (next < 0) text.length else next
                if (until <= i) {
                    builder.append(text[i])
                    i++
                } else {
                    builder.append(text.substring(i, until))
                    i = until
                }
            }
        }
    }
}

private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
    var i = from
    while (i < length) {
        if (predicate(this[i])) return i
        i++
    }
    return -1
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(color = LocalPalette.current.text, fontSize = 15.5.sp, lineHeight = 25.sp),
    onLink: (String) -> Unit,
) {
    val p = LocalPalette.current
    val annotated = remember(text, p.isDark, p.primary, p.inlineCode, p.codeBg) {
        inlineAnnotated(
            text = text,
            base = SpanStyle(color = style.color),
            linkColor = p.primary,
            codeColor = p.inlineCode,
            codeBg = p.codeBg,
        )
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val hasLinks = remember(annotated) {
        annotated.getStringAnnotations(LINK_TAG, 0, annotated.length).isNotEmpty()
    }
    Text(
        text = annotated,
        modifier = if (hasLinks) {
            modifier.pointerInput(annotated, onLink) {
                detectTapGestures { pos ->
                    val result = layout ?: return@detectTapGestures
                    val offset = result.getOffsetForPosition(pos)
                    annotated.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let {
                        onLink(it.item)
                    }
                }
            }
        } else {
            modifier
        },
        style = style,
        onTextLayout = { layout = it },
    )
}

@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    onLink: (String) -> Unit = {},
) {
    val p = LocalPalette.current
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val body = TextStyle(color = p.text, fontSize = 15.5.sp, lineHeight = 25.sp)
    val context = LocalContext.current
    val openLink: (String) -> Unit = remember(context, onLink) {
        { url ->
            onLink(url)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { idx, block ->
            when (block) {
                is MdBlock.Paragraph -> {
                    MarkdownText(block.text, style = body, onLink = openLink)
                }

                is MdBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 21.sp
                        2 -> 18.5.sp
                        3 -> 16.5.sp
                        else -> 15.5.sp
                    }
                    MarkdownText(
                        block.text,
                        modifier = Modifier.padding(top = if (idx == 0) 0.dp else 6.dp),
                        style = TextStyle(
                            color = p.text,
                            fontSize = size,
                            fontWeight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold,
                            lineHeight = (size.value * 1.38f).sp,
                            letterSpacing = (-0.2).sp,
                        ),
                        onLink = openLink,
                    )
                }

                is MdBlock.Code -> CodeBlock(block.lang, block.code, context)

                is MdBlock.Quote -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(8.dp))
                            .background(p.surfaceAlt.copy(alpha = 0.55f)),
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(p.primary.copy(alpha = 0.65f))
                        )
                        MarkdownText(
                            block.text,
                            style = body.copy(color = p.dim, fontSize = 14.5.sp, lineHeight = 23.sp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            onLink = openLink,
                        )
                    }
                }

                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.items.forEach { (level, item) ->
                        Row(Modifier.padding(start = (level * 16).dp)) {
                            Box(
                                Modifier
                                    .padding(top = 10.dp, end = 9.dp)
                                    .size(if (level == 0) 5.dp else 4.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (level == 0) p.dim else p.faint)
                            )
                            MarkdownText(item, style = body, modifier = Modifier.weight(1f), onLink = openLink)
                        }
                    }
                }

                is MdBlock.Ordered -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.items.forEachIndexed { index, (level, item) ->
                        Row(Modifier.padding(start = (level * 16).dp)) {
                            Text(
                                text = "${block.start + index}.",
                                color = p.dim,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 7.dp, top = 1.dp),
                            )
                            MarkdownText(item, style = body, modifier = Modifier.weight(1f), onLink = openLink)
                        }
                    }
                }

                is MdBlock.Table -> TableBlock(block.header, block.rows, openLink)

                is MdBlock.Math -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.codeBg)
                            .border(1.dp, p.border, RoundedCornerShape(12.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 12.dp, horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = block.text,
                            color = p.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(p.border)
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String, context: android.content.Context) {
    val p = LocalPalette.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.codeBg)
            .border(1.dp, p.border, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(p.surfaceAlt)
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lang.lowercase(),
                color = p.dim,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Tappable(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                onClick = {
                    Sys.copy(context, code, "代码已复制")
                    copied = true
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "复制代码",
                        tint = if (copied) p.success else p.dim,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (copied) "已复制" else "复制",
                        color = if (copied) p.success else p.dim,
                        fontSize = 11.5.sp,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = code,
                color = if (p.isDark) Color(0xFFD8DEE9) else Color(0xFF1F2329),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun TableBlock(
    header: List<String>,
    rows: List<List<String>>,
    onLink: (String) -> Unit,
) {
    val p = LocalPalette.current
    val scrollable = header.size > 3
    val cellStyle = TextStyle(color = p.dim, fontSize = 13.sp, lineHeight = 19.sp)
    val headerStyle = TextStyle(color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
    val shape = RoundedCornerShape(12.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, p.border, shape)
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
    ) {
        Column(if (scrollable) Modifier else Modifier.fillMaxWidth()) {
            Row(
                Modifier.background(p.surfaceAlt).padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                header.forEach { cell ->
                    MarkdownText(
                        text = cell,
                        modifier = (if (scrollable) Modifier.width(120.dp) else Modifier.weight(1f))
                            .padding(horizontal = 10.dp),
                        style = headerStyle,
                        onLink = onLink,
                    )
                }
            }
            rows.forEachIndexed { index, row ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
                Row(
                    Modifier
                        .background(if (index % 2 == 1) p.surfaceAlt.copy(alpha = 0.35f) else p.surface)
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(header.size) { i ->
                        MarkdownText(
                            text = row.getOrElse(i) { "" },
                            modifier = (if (scrollable) Modifier.width(120.dp) else Modifier.weight(1f))
                                .padding(horizontal = 10.dp),
                            style = cellStyle,
                            onLink = onLink,
                        )
                    }
                }
            }
        }
    }
}
