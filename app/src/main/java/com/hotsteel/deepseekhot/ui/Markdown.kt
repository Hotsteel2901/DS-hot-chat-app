// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.hotsteel.deepseekhot.ui.liquid.LocalAmbientBackdrop
import com.hotsteel.deepseekhot.ui.liquid.liquidGlass
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Sys
import kotlinx.coroutines.delay

data class TaskItem(val level: Int, val checked: Boolean, val text: String)

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Callout(val kind: String, val text: String) : MdBlock
    data class Bullets(val items: List<Pair<Int, String>>) : MdBlock
    data class TaskList(val items: List<TaskItem>) : MdBlock
    data class Ordered(val items: List<Pair<Int, String>>, val start: Int) : MdBlock
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val alignments: List<TextAlign> = emptyList(),
    ) : MdBlock
    data class Math(val text: String) : MdBlock
    data class Image(val alt: String, val url: String) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {

    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val taskItem = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val ordered = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val rule = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val imageOnly = Regex("^!\\[([^\\]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)$")
    private val calloutHeader = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val latexBegin = Regex("""^\\begin\{(equation\*?|align\*?|aligned|gather\*?|cases|matrix|pmatrix|bmatrix|vmatrix|array)\}""")

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

            // Fenced code blocks (``` or ~~~)
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flush()
                val fenceChar = trimmed.take(3)
                val lang = trimmed.removePrefix(fenceChar).trim().substringBefore(' ')
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fenceChar)) {
                    code.appendLine(lines[i])
                    i++
                }
                i++ // skip closing fence
                val codeStr = code.toString().trimEnd('\n')
                val lowerLang = lang.lowercase()
                if (lowerLang == "latex" || lowerLang == "tex" || lowerLang == "math") {
                    out.add(MdBlock.Math(codeStr.trim()))
                } else {
                    out.add(MdBlock.Code(lang.ifBlank { "text" }, codeStr))
                }
                continue
            }

            // Standalone raw <svg ...>...</svg> block outside code fences
            if (trimmed.startsWith("<svg", ignoreCase = true)) {
                flush()
                val svg = StringBuilder()
                while (i < lines.size) {
                    svg.appendLine(lines[i])
                    val done = lines[i].contains("</svg>", ignoreCase = true)
                    i++
                    if (done) break
                }
                out.add(MdBlock.Code("svg", svg.toString().trim()))
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

            // Standalone Markdown Image
            val img = imageOnly.find(trimmed)
            if (img != null) {
                flush()
                out.add(MdBlock.Image(img.groupValues[1].trim(), img.groupValues[2].trim()))
                i++
                continue
            }

            // Block Math: $$ ... $$
            if (trimmed.startsWith("$$")) {
                flush()
                val body = StringBuilder()
                val first = trimmed.removePrefix("$$")
                if (first.endsWith("$$") && first.length >= 2) {
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
                        val tail = lines[i].trim().removeSuffix("$$")
                        if (tail.isNotBlank()) body.appendLine(tail)
                        i++
                    }
                }
                out.add(MdBlock.Math(body.toString().trim()))
                continue
            }

            // Block Math: \[ ... \] (DeepSeek R1's default display math format!)
            if (trimmed.startsWith("\\[")) {
                flush()
                val body = StringBuilder()
                val first = trimmed.removePrefix("\\[")
                if (first.endsWith("\\]") && first.length >= 2) {
                    body.append(first.removeSuffix("\\]"))
                    i++
                } else {
                    if (first.isNotBlank()) body.appendLine(first)
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("\\]")) {
                        body.appendLine(lines[i])
                        i++
                    }
                    if (i < lines.size) {
                        val tail = lines[i].trim().removeSuffix("\\]")
                        if (tail.isNotBlank()) body.appendLine(tail)
                        i++
                    }
                }
                out.add(MdBlock.Math(body.toString().trim()))
                continue
            }

            // Block Math: \begin{align|equation|matrix|cases} ... \end{...}
            val envMatch = latexBegin.find(trimmed)
            if (envMatch != null) {
                flush()
                val envName = envMatch.groupValues[1]
                val endMarker = "\\end{$envName}"
                val body = StringBuilder()
                while (i < lines.size) {
                    body.appendLine(lines[i])
                    val done = lines[i].contains(endMarker)
                    i++
                    if (done) break
                }
                out.add(MdBlock.Math(body.toString().trim()))
                continue
            }

            // Blockquote & GitHub Callouts (> [!NOTE], > [!TIP], > [!WARNING], > [!IMPORTANT], > [!CAUTION])
            if (trimmed.startsWith(">")) {
                flush()
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                    i++
                }
                val firstLine = quoteLines.firstOrNull().orEmpty()
                val callout = calloutHeader.find(firstLine)
                if (callout != null) {
                    val kind = callout.groupValues[1].uppercase()
                    val rest = buildList {
                        if (callout.groupValues[2].isNotBlank()) add(callout.groupValues[2].trim())
                        addAll(quoteLines.drop(1))
                    }.joinToString("\n").trim()
                    out.add(MdBlock.Callout(kind, rest))
                } else {
                    out.add(MdBlock.Quote(quoteLines.joinToString("\n").trim()))
                }
                continue
            }

            // GFM Table with column alignment parsing
            if (trimmed.startsWith("|") && i + 1 < lines.size) {
                val next = lines[i + 1].trim()
                if (next.startsWith("|") && next.replace("|", "").replace(":", "").replace("-", "").isBlank()) {
                    flush()
                    val header = splitRow(trimmed)
                    val alignments = splitRow(next).map { cell ->
                        when {
                            cell.startsWith(":") && cell.endsWith(":") -> TextAlign.Center
                            cell.endsWith(":") -> TextAlign.End
                            else -> TextAlign.Start
                        }
                    }
                    val rows = mutableListOf<List<String>>()
                    i += 2
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        rows.add(splitRow(lines[i].trim()))
                        i++
                    }
                    out.add(MdBlock.Table(header, rows, alignments))
                    continue
                }
            }

            // Ordered List
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

            // GFM Task List (- [x] / - [ ])
            val tl = taskItem.find(line)
            if (tl != null) {
                flush()
                val items = mutableListOf<TaskItem>()
                while (i < lines.size) {
                    val m = taskItem.find(lines[i]) ?: break
                    items.add(
                        TaskItem(
                            level = indentLevel(m.groupValues[1]),
                            checked = m.groupValues[2].equals("x", ignoreCase = true),
                            text = m.groupValues[3],
                        )
                    )
                    i++
                }
                out.add(MdBlock.TaskList(items))
                continue
            }

            // Unordered Bullet List
            val bl = bullet.find(line)
            if (bl != null) {
                flush()
                val items = mutableListOf<Pair<Int, String>>()
                while (i < lines.size) {
                    if (taskItem.find(lines[i]) != null) break
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

            // Inline LaTeX: \( ... \)
            text.startsWith("\\(", i) -> {
                val end = text.indexOf("\\)", i + 2)
                if (end > i + 2) {
                    val math = MathFallbackFormatter.toUnicode(text.substring(i + 2, end))
                    builder.withStyle(
                        style.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = linkColor,
                        )
                    ) { append(math) }
                    i = end + 2
                } else {
                    builder.append("\\(")
                    i += 2
                }
            }

            // Inline LaTeX: $ ... $
            text[i] == '$' && i + 1 < text.length && text[i + 1] != '$' -> {
                val end = text.indexOf('$', i + 1)
                if (end > i + 1 && !text.substring(i + 1, end).contains('\n')) {
                    val math = MathFallbackFormatter.toUnicode(text.substring(i + 1, end))
                    builder.withStyle(
                        style.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = linkColor,
                        )
                    ) { append(math) }
                    i = end + 1
                } else {
                    builder.append('$')
                    i++
                }
            }

            // Inline link [label](url)
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
                // Fixed: include '$' and '\\' so inline LaTeX anywhere in a paragraph is never skipped!
                val next = text.indexOfFirst(i) {
                    it == '*' || it == '_' || it == '`' || it == '[' || it == '~' || it == '$' || it == '\\'
                }
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

private fun hasComplexInlineMath(text: String): Boolean {
    val hasInlineDelimiter = (text.contains("\\(") && text.contains("\\)")) ||
        (text.count { it == '$' } >= 2)
    return hasInlineDelimiter && MathFallbackFormatter.isComplexLatex(text)
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
    streaming: Boolean = false,
    isSecondary: Boolean = false,
    onLink: (String) -> Unit = {},
) {
    val p = LocalPalette.current
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val body = if (isSecondary) {
        TextStyle(color = p.dim, fontSize = 13.5.sp, lineHeight = 22.sp)
    } else {
        TextStyle(color = p.text, fontSize = 15.5.sp, lineHeight = 25.sp)
    }
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { idx, block ->
            when (block) {
                is MdBlock.Paragraph -> {
                    if (!streaming && !isSecondary && hasComplexInlineMath(block.text)) {
                        RichWebCanvas(
                            kind = RichVisualKind.KATEX_RICH_TEXT,
                            payload = block.text,
                            minHeightDp = 32,
                            maxHeightDp = 420,
                            allowFullscreen = false,
                            onLink = onLink,
                        )
                    } else {
                        MarkdownText(block.text, style = body, onLink = onLink)
                    }
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
                            color = if (isSecondary) p.dim else p.text,
                            fontSize = size,
                            fontWeight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold,
                            lineHeight = (size.value * 1.38f).sp,
                            letterSpacing = (-0.2).sp,
                        ),
                        onLink = onLink,
                    )
                }

                is MdBlock.Code -> RichCodeOrDiagramBlock(
                    lang = block.lang,
                    code = block.code,
                    streaming = streaming,
                    context = context,
                    onLink = onLink,
                )

                is MdBlock.Callout -> CalloutBlock(block.kind, block.text, body, onLink)

                is MdBlock.Quote -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .liquidGlass(
                                backdrop = LocalAmbientBackdrop.current,
                                cornerRadius = 12.dp,
                                blurRadius = 8.dp,
                                refractionHeight = 8.dp,
                                refractionAmount = 14.dp,
                                showShadow = false,
                            ),
                    ) {
                        Box(
                            Modifier
                                .width(3.5.dp)
                                .fillMaxHeight()
                                .background(p.primary.copy(alpha = 0.75f))
                        )
                        MarkdownText(
                            block.text,
                            style = body.copy(color = p.dim, fontSize = 14.5.sp, lineHeight = 23.sp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            onLink = onLink,
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
                                    .background(if (level == 0) p.primary else p.faint)
                            )
                            MarkdownText(item, style = body, modifier = Modifier.weight(1f), onLink = onLink)
                        }
                    }
                }

                is MdBlock.TaskList -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    block.items.forEach { item ->
                        Row(
                            Modifier.padding(start = (item.level * 16).dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                Modifier
                                    .padding(top = 4.dp, end = 9.dp)
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (item.checked) p.primary else Color.Transparent)
                                    .border(
                                        1.5.dp,
                                        if (item.checked) p.primary else p.faint,
                                        RoundedCornerShape(5.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (item.checked) {
                                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                }
                            }
                            MarkdownText(
                                item.text,
                                style = if (item.checked) {
                                    body.copy(color = p.dim, textDecoration = TextDecoration.LineThrough)
                                } else body,
                                modifier = Modifier.weight(1f),
                                onLink = onLink,
                            )
                        }
                    }
                }

                is MdBlock.Ordered -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.items.forEachIndexed { index, (level, item) ->
                        Row(Modifier.padding(start = (level * 16).dp)) {
                            Text(
                                text = "${block.start + index}.",
                                color = p.primary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(end = 7.dp, top = 1.dp),
                            )
                            MarkdownText(item, style = body, modifier = Modifier.weight(1f), onLink = onLink)
                        }
                    }
                }

                is MdBlock.Table -> TableBlock(block.header, block.rows, block.alignments, onLink)

                is MdBlock.Math -> MathFormulaCard(
                    latex = block.text,
                    streaming = streaming,
                    context = context,
                    onLink = onLink,
                )

                is MdBlock.Image -> MarkdownImageCard(block.alt, block.url)

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
private fun MathFormulaCard(
    latex: String,
    streaming: Boolean,
    context: android.content.Context,
    onLink: (String) -> Unit,
) {
    val p = LocalPalette.current
    var copied by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = LocalAmbientBackdrop.current,
                cornerRadius = 16.dp,
                blurRadius = 10.dp,
                refractionHeight = 10.dp,
                refractionAmount = 16.dp,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(p.surfaceAlt.copy(alpha = 0.55f))
                .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LaTeX 公式",
                color = p.primary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Tappable(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                onClick = { showRaw = !showRaw },
            ) {
                Text(
                    if (showRaw) "渲染" else "源码",
                    color = p.dim,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Tappable(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                onClick = {
                    Sys.copy(context, latex, "公式 LaTeX 已复制")
                    copied = true
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "复制公式",
                        tint = if (copied) p.success else p.dim,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (copied) "已复制" else "复制", color = if (copied) p.success else p.dim, fontSize = 11.5.sp)
                }
            }
        }

        if (streaming || showRaw) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (showRaw) latex else MathFallbackFormatter.toUnicode(latex),
                    color = p.text,
                    fontFamily = if (showRaw) FontFamily.Monospace else FontFamily.Serif,
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            RichWebCanvas(
                kind = RichVisualKind.KATEX_BLOCK,
                payload = latex,
                minHeightDp = 52,
                maxHeightDp = 360,
                allowFullscreen = true,
                onLink = onLink,
            )
        }
    }
}

private fun detectVisualKind(lang: String, code: String): RichVisualKind? {
    val l = lang.lowercase().trim()
    return when {
        l == "mermaid" || l == "mmd" -> RichVisualKind.MERMAID
        l == "svg" || (l in setOf("xml", "html", "text") && code.trim().startsWith("<svg", ignoreCase = true)) -> RichVisualKind.SVG
        l == "html" || l == "htm" -> RichVisualKind.HTML
        l in setOf("chart", "chartjs", "json-chart", "echarts") -> RichVisualKind.CHART
        l in setOf("dot", "graphviz") -> RichVisualKind.GRAPHVIZ
        else -> null
    }
}

private fun isAsciiDiagram(lang: String, code: String): Boolean {
    val l = lang.lowercase().trim()
    if (l in setOf("ascii", "tree", "diagram", "art")) return true
    return code.contains("├──") || code.contains("└──") || code.contains("┌──") || code.contains("╔══")
}

@Composable
private fun RichCodeOrDiagramBlock(
    lang: String,
    code: String,
    streaming: Boolean,
    context: android.content.Context,
    onLink: (String) -> Unit,
) {
    val p = LocalPalette.current
    val visualKind = remember(lang, code) { detectVisualKind(lang, code) }
    val asciiDiagram = remember(lang, code) { visualKind == null && isAsciiDiagram(lang, code) }

    // Mermaid / SVG / Chart / Graphviz default to rendered preview once streaming finishes; HTML defaults to code with 1-tap preview
    var showPreview by remember(lang, visualKind) {
        mutableStateOf(visualKind != null && visualKind != RichVisualKind.HTML)
    }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = LocalAmbientBackdrop.current,
                cornerRadius = 16.dp,
                blurRadius = 10.dp,
                refractionHeight = 10.dp,
                refractionAmount = 16.dp,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(p.surfaceAlt.copy(alpha = 0.65f))
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    visualKind != null -> "${lang.lowercase()} · ${visualKind.label}"
                    asciiDiagram -> "${lang.lowercase()} · 结构图"
                    else -> lang.lowercase()
                },
                color = if (visualKind != null) p.primary else p.dim,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (visualKind != null && !streaming) {
                Tappable(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (showPreview) p.primarySoft else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    onClick = { showPreview = !showPreview },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (showPreview) Icons.Rounded.Code
                            else if (visualKind == RichVisualKind.HTML) Icons.Rounded.PlayArrow
                            else Icons.Rounded.Visibility,
                            contentDescription = "切换预览",
                            tint = p.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (showPreview) "源码" else if (visualKind == RichVisualKind.HTML) "运行预览" else "渲染图",
                            color = p.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
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

        if (visualKind != null && showPreview && !streaming) {
            RichWebCanvas(
                kind = visualKind,
                payload = code,
                minHeightDp = if (visualKind == RichVisualKind.CHART) 260 else 120,
                maxHeightDp = 520,
                allowFullscreen = true,
                onLink = onLink,
            )
        } else {
            val highlighted = remember(code, lang, p.isDark) {
                if (asciiDiagram) AnnotatedString(code)
                else SyntaxHighlighter.highlight(code, lang, p.isDark)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(p.codeBg.copy(alpha = 0.72f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 13.dp, vertical = 11.dp),
            ) {
                Text(
                    text = highlighted,
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (asciiDiagram) 12.5.sp else 13.sp,
                    lineHeight = if (asciiDiagram) 18.sp else 20.sp,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun CalloutBlock(
    kind: String,
    text: String,
    body: TextStyle,
    onLink: (String) -> Unit,
) {
    val p = LocalPalette.current
    val (label, accent, icon) = when (kind) {
        "TIP" -> Triple("提示", p.success, Icons.Rounded.Lightbulb)
        "IMPORTANT" -> Triple("重要", Color(0xFF9333EA), Icons.Rounded.PriorityHigh)
        "WARNING" -> Triple("注意", Color(0xFFF59E0B), Icons.Rounded.WarningAmber)
        "CAUTION" -> Triple("警告", p.danger, Icons.Rounded.ErrorOutline)
        else -> Triple("说明", p.primary, Icons.Rounded.Info)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .liquidGlass(
                backdrop = LocalAmbientBackdrop.current,
                cornerRadius = 14.dp,
                tint = accent,
                blurRadius = 10.dp,
                refractionHeight = 8.dp,
                refractionAmount = 14.dp,
                showShadow = false,
            ),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (text.isNotBlank()) {
                MarkdownText(
                    text,
                    style = body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                    onLink = onLink,
                )
            }
        }
    }
}

@Composable
private fun MarkdownImageCard(alt: String, url: String) {
    val p = LocalPalette.current
    var zoom by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = LocalAmbientBackdrop.current,
                cornerRadius = 16.dp,
                blurRadius = 8.dp,
                refractionHeight = 8.dp,
                refractionAmount = 14.dp,
            )
            .clickable { zoom = true }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = url,
            contentDescription = alt.ifBlank { "图片" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 320.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        if (alt.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(alt, color = p.dim, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }

    if (zoom) {
        Dialog(
            onDismissRequest = { zoom = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { zoom = false },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = alt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
                IconTap(
                    Icons.Rounded.Close,
                    "关闭图片",
                    { zoom = false },
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun TableBlock(
    header: List<String>,
    rows: List<List<String>>,
    alignments: List<TextAlign>,
    onLink: (String) -> Unit,
) {
    val p = LocalPalette.current
    val cellStyle = TextStyle(color = p.text, fontSize = 13.5.sp, lineHeight = 20.sp)

    Box(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = LocalAmbientBackdrop.current,
                cornerRadius = 14.dp,
                blurRadius = 8.dp,
                refractionHeight = 8.dp,
                refractionAmount = 14.dp,
            )
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            Row(Modifier.fillMaxWidth().background(p.surfaceAlt.copy(alpha = 0.7f))) {
                header.forEachIndexed { idx, cell ->
                    val align = alignments.getOrNull(idx) ?: TextAlign.Start
                    MarkdownText(
                        cell,
                        style = cellStyle.copy(fontWeight = FontWeight.SemiBold, textAlign = align),
                        modifier = Modifier
                            .widthIn(min = 90.dp, max = 220.dp)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        onLink = onLink,
                    )
                }
            }
            rows.forEachIndexed { rIdx, row ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.border.copy(alpha = 0.7f)))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (rIdx % 2 == 1) p.surfaceAlt.copy(alpha = 0.25f) else Color.Transparent),
                ) {
                    header.indices.forEach { cIdx ->
                        val cell = row.getOrNull(cIdx).orEmpty()
                        val align = alignments.getOrNull(cIdx) ?: TextAlign.Start
                        MarkdownText(
                            cell,
                            style = cellStyle.copy(textAlign = align),
                            modifier = Modifier
                                .widthIn(min = 90.dp, max = 220.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            onLink = onLink,
                        )
                    }
                }
            }
        }
    }
}
