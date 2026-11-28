// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 轻量高性能多语言代码语法高亮器 + LaTeX 数学公式 Unicode 降级美化器。
 * 覆盖 Kotlin / Java / Python / JS / TS / C / C++ / Rust / Go / Swift / Dart / SQL / Bash / JSON / YAML / HTML / XML / CSS / Diff。
 */
object SyntaxHighlighter {

    private data class Colors(
        val plain: Color,
        val keyword: Color,
        val type: Color,
        val string: Color,
        val number: Color,
        val comment: Color,
        val annotation: Color,
        val diffAdd: Color,
        val diffAddBg: Color,
        val diffDel: Color,
        val diffDelBg: Color,
    )

    private val darkColors = Colors(
        plain = Color(0xFFE6EAF2),
        keyword = Color(0xFFC792EA),
        type = Color(0xFF82AAFF),
        string = Color(0xFFC3E88D),
        number = Color(0xFFF78C6C),
        comment = Color(0xFF69768A),
        annotation = Color(0xFFFFCB6B),
        diffAdd = Color(0xFF80E2A7),
        diffAddBg = Color(0x2818A058),
        diffDel = Color(0xFFFF8F94),
        diffDelBg = Color(0x28E5484D),
    )

    private val lightColors = Colors(
        plain = Color(0xFF1F2329),
        keyword = Color(0xFF7C3AED),
        type = Color(0xFF1D4ED8),
        string = Color(0xFF15803D),
        number = Color(0xFFC2410C),
        comment = Color(0xFF7B8494),
        annotation = Color(0xFFB45309),
        diffAdd = Color(0xFF15803D),
        diffAddBg = Color(0x2018A058),
        diffDel = Color(0xFFDC2626),
        diffDelBg = Color(0x20E5484D),
    )

    private val commonKeywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "sealed", "data", "override",
        "private", "public", "protected", "internal", "companion", "suspend", "inline", "lateinit",
        "return", "if", "else", "when", "for", "while", "do", "break", "continue", "try", "catch",
        "finally", "throw", "import", "package", "null", "true", "false", "this", "super", "is", "in", "as",
        "def", "lambda", "yield", "async", "await", "from", "with", "pass", "elif", "None", "True", "False",
        "and", "or", "not", "global", "nonlocal", "raise", "assert",
        "function", "const", "let", "export", "default", "new", "typeof", "instanceof", "switch", "case",
        "void", "static", "final", "abstract", "extends", "implements", "synchronized", "volatile",
        "struct", "impl", "trait", "fn", "mut", "pub", "mod", "use", "match", "ref", "move", "crate",
        "func", "go", "defer", "chan", "select", "type", "map", "range",
        "SELECT", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "ON", "GROUP", "ORDER", "BY",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "INDEX", "AS", "LIMIT",
        "select", "from", "where", "join", "group", "order", "by", "insert", "update", "delete", "create", "table",
        "echo", "export", "fi", "then", "elif", "esac", "done", "local", "shift",
    )

    fun highlight(code: String, lang: String, isDark: Boolean): AnnotatedString {
        val c = if (isDark) darkColors else lightColors
        val lower = lang.lowercase().trim()

        if (lower == "diff" || lower == "patch") {
            return buildAnnotatedString {
                code.split('\n').forEachIndexed { idx, line ->
                    if (idx > 0) append('\n')
                    when {
                        line.startsWith("+") && !line.startsWith("+++") ->
                            withStyle(SpanStyle(color = c.diffAdd, background = c.diffAddBg)) { append(line) }
                        line.startsWith("-") && !line.startsWith("---") ->
                            withStyle(SpanStyle(color = c.diffDel, background = c.diffDelBg)) { append(line) }
                        line.startsWith("@@") ->
                            withStyle(SpanStyle(color = c.type, fontWeight = FontWeight.SemiBold)) { append(line) }
                        else -> withStyle(SpanStyle(color = c.plain)) { append(line) }
                    }
                }
            }
        }

        val hashComment = lower in setOf("py", "python", "sh", "bash", "zsh", "shell", "yaml", "yml", "toml", "rb", "ruby", "r")
        val sqlComment = lower in setOf("sql", "lua", "haskell")

        return buildAnnotatedString {
            var i = 0
            val n = code.length
            while (i < n) {
                val ch = code[i]
                val next = if (i + 1 < n) code[i + 1] else '\u0000'
                when {
                    // Single-line // comment
                    ch == '/' && next == '/' -> {
                        val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                        withStyle(SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // Block /* ... */ comment
                    ch == '/' && next == '*' -> {
                        val end = code.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                        withStyle(SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // # comment for Python/Shell/YAML
                    hashComment && ch == '#' -> {
                        val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                        withStyle(SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // -- comment for SQL
                    sqlComment && ch == '-' && next == '-' -> {
                        val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                        withStyle(SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // Strings "..." or '...' or `...`
                    ch == '"' || ch == '\'' || ch == '`' -> {
                        val quote = ch
                        var j = i + 1
                        while (j < n) {
                            if (code[j] == '\\' && j + 1 < n) {
                                j += 2
                            } else if (code[j] == quote) {
                                j++
                                break
                            } else if (code[j] == '\n' && quote != '`') {
                                break
                            } else {
                                j++
                            }
                        }
                        withStyle(SpanStyle(color = c.string)) { append(code.substring(i, j)) }
                        i = j
                    }
                    // Annotations @Foo
                    ch == '@' && next.isLetter() -> {
                        var j = i + 1
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                        withStyle(SpanStyle(color = c.annotation)) { append(code.substring(i, j)) }
                        i = j
                    }
                    // Numbers
                    ch.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit()) -> {
                        var j = i + 1
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                        withStyle(SpanStyle(color = c.number)) { append(code.substring(i, j)) }
                        i = j
                    }
                    // Identifiers / Keywords / Types
                    ch.isLetter() || ch == '_' -> {
                        var j = i + 1
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                        val word = code.substring(i, j)
                        when {
                            word in commonKeywords ->
                                withStyle(SpanStyle(color = c.keyword, fontWeight = FontWeight.Medium)) { append(word) }
                            word[0].isUpperCase() && word.any { it.isLowerCase() } ->
                                withStyle(SpanStyle(color = c.type)) { append(word) }
                            else ->
                                withStyle(SpanStyle(color = c.plain)) { append(word) }
                        }
                        i = j
                    }
                    else -> {
                        withStyle(SpanStyle(color = c.plain)) { append(ch) }
                        i++
                    }
                }
            }
        }
    }
}

/**
 * 将常见 LaTeX 符号转换为美观可读的 Unicode 数学字符，用于流式输出实时展示与离线兜底。
 */
object MathFallbackFormatter {

    private val symbols = listOf(
        "\\infty" to "∞",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\int" to "∫",
        "\\oint" to "∮",
        "\\sqrt" to "√",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\times" to "×",
        "\\div" to "÷",
        "\\cdot" to "·",
        "\\leq" to "≤",
        "\\le" to "≤",
        "\\geq" to "≥",
        "\\ge" to "≥",
        "\\neq" to "≠",
        "\\ne" to "≠",
        "\\approx" to "≈",
        "\\equiv" to "≡",
        "\\sim" to "∼",
        "\\propto" to "∝",
        "\\rightarrow" to "→",
        "\\to" to "→",
        "\\leftarrow" to "←",
        "\\leftrightarrow" to "↔",
        "\\Rightarrow" to "⇒",
        "\\Leftarrow" to "⇐",
        "\\Leftrightarrow" to "⇔",
        "\\iff" to "⇔",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\subset" to "⊂",
        "\\subseteq" to "⊆",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\emptyset" to "∅",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\epsilon" to "ε",
        "\\varepsilon" to "ε",
        "\\zeta" to "ζ",
        "\\eta" to "η",
        "\\theta" to "θ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\nu" to "ν",
        "\\xi" to "ξ",
        "\\pi" to "π",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\tau" to "τ",
        "\\phi" to "φ",
        "\\varphi" to "φ",
        "\\chi" to "χ",
        "\\psi" to "ψ",
        "\\omega" to "ω",
        "\\Gamma" to "Γ",
        "\\Delta" to "Δ",
        "\\Theta" to "Θ",
        "\\Lambda" to "Λ",
        "\\Pi" to "Π",
        "\\Sigma" to "Σ",
        "\\Phi" to "Φ",
        "\\Psi" to "Ψ",
        "\\Omega" to "Ω",
        "\\ldots" to "…",
        "\\cdots" to "⋯",
        "\\dots" to "…",
        "\\degree" to "°",
        "\\circ" to "°",
    )

    private val complexLatexRegex = Regex(
        """\\[A-Za-z]+|[_\^]\{[^}]+\}|[_\^][A-Za-z0-9]"""
    )

    fun isComplexLatex(text: String): Boolean = complexLatexRegex.containsMatchIn(text)

    fun toUnicode(raw: String): String {
        var s = raw.trim()
        // Convert simple \frac{a}{b} -> (a)/(b)
        val fracRe = Regex("""\\d?frac\{([^{}]+)\}\{([^{}]+)\}""")
        repeat(3) {
            s = fracRe.replace(s) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
        }
        // Convert \boxed{...}, \text{...}, \mathrm{...}, \mathbf{...}
        repeat(2) {
            s = Regex("""\\(?:boxed|text|mathrm|mathbf|mathit|mathbb|mathcal|operatorname|boldsymbol|bm)\{([^{}]+)\}""")
                .replace(s) { it.groupValues[1] }
        }
        // Strip leading backslash from standard math operator names (\tan, \sin, \cos, \log, etc.)
        s = Regex("""\\(arcsin|arccos|arctan|sinh|cosh|tanh|sin|cos|tan|cot|sec|csc|log|ln|lg|exp|lim|max|min|sup|inf|det|gcd|deg|arg|bmod|pmod|mod)(?![A-Za-z])""")
            .replace(s) { "${it.groupValues[1]} " }
        for ((cmd, sym) in symbols) {
            s = s.replace(Regex("${Regex.escape(cmd)}(?![A-Za-z])"), sym)
        }
        s = s.replace("\\left", "").replace("\\right", "")
            .replace("\\,", " ").replace("\\;", " ").replace("\\quad", "  ")
            .replace("\\!", "")
            .replace("^2", "²").replace("^3", "³").replace("^0", "⁰").replace("^1", "¹")
            .replace("^{-1}", "⁻¹").replace("^{2}", "²").replace("^{3}", "³")
            .replace("_0", "₀").replace("_1", "₁").replace("_2", "₂").replace("_n", "ₙ").replace("_i", "ᵢ")
        return s.trim()
    }
}
