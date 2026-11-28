// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hotsteel.deepseekhot.ui.theme.LocalPalette
import com.hotsteel.deepseekhot.util.Sys

enum class RichVisualKind(val label: String) {
    KATEX_BLOCK("LaTeX 公式"),
    KATEX_RICH_TEXT("公式混排"),
    MERMAID("Mermaid 图表"),
    SVG("SVG 矢量图"),
    HTML("HTML 互动预览"),
    CHART("Chart.js 数据图表"),
    GRAPHVIZ("Graphviz 关系图"),
}

private fun Color.toCssHex(): String {
    val argb = this.toArgb()
    return String.format("#%06X", argb and 0xFFFFFF)
}

private fun escapeJsTemplate(raw: String): String = raw
    .replace("\\", "\\\\")
    .replace("`", "\\`")
    .replace("\${", "\\\${")
    .replace("</script>", "<\\/script>", ignoreCase = true)

private fun escapeHtml(raw: String): String = raw
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

/**
 * 构建支持自动高度回传（AndroidBridge.onHeight）的完整渲染 HTML 文档。
 * 支持：KaTeX + mhchem 数学与化学公式、Mermaid v11 全图表、SVG 矢量图、Chart.js 数据图表、Graphviz DOT 与 HTML 沙盒。
 */
private fun buildRichHtml(
    kind: RichVisualKind,
    payload: String,
    isDark: Boolean,
    textHex: String,
    accentHex: String,
    surfaceHex: String,
    borderHex: String,
): String {
    val baseCss = """
        * { box-sizing: border-box; }
        html, body {
            margin: 0; padding: 0; background: transparent;
            color: $textHex;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans SC", sans-serif;
            overflow-x: auto; overflow-y: hidden;
            -webkit-font-smoothing: antialiased;
        }
        #root { width: 100%; padding: 6px 8px; }
        .err { color: #f87171; font-family: monospace; font-size: 12.5px; white-space: pre-wrap; padding: 6px; }
        .katex { font-size: 1.08em; color: $textHex; }
        .katex-display { margin: 0.35em 0 !important; overflow-x: auto; overflow-y: hidden; padding: 4px 2px; }
        svg { max-width: 100%; height: auto; display: block; margin: 0 auto; }
        a { color: $accentHex; }
    """.trimIndent()

    val heightReporterJs = """
        function reportHeight() {
            try {
                var el = document.getElementById('root') || document.body;
                var h = Math.ceil(Math.max(el.scrollHeight, el.getBoundingClientRect().height));
                if (h > 10 && window.AndroidBridge && window.AndroidBridge.onHeight) {
                    window.AndroidBridge.onHeight(h);
                }
            } catch (e) {}
        }
        window.addEventListener('load', function() {
            reportHeight();
            setTimeout(reportHeight, 120);
            setTimeout(reportHeight, 450);
            setTimeout(reportHeight, 1100);
        });
        if (window.ResizeObserver) {
            new ResizeObserver(reportHeight).observe(document.documentElement);
        }
    """.trimIndent()

    return when (kind) {
        RichVisualKind.KATEX_BLOCK -> """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/mhchem.min.js"></script>
            <style>$baseCss
                #root { display: flex; justify-content: center; align-items: center; padding: 10px 8px; }
                .fallback { font-family: monospace; font-size: 14px; white-space: pre-wrap; text-align: center; }
            </style></head><body><div id="root"></div><script>
            const tex = `${escapeJsTemplate(payload)}`;
            const fallback = `${escapeJsTemplate(MathFallbackFormatter.toUnicode(payload))}`;
            function render() {
                const root = document.getElementById('root');
                if (window.katex) {
                    try {
                        katex.render(tex, root, { displayMode: true, throwOnError: false, strict: false, trust: true });
                    } catch (e) {
                        root.innerHTML = '<div class="fallback"></div>';
                        root.firstChild.textContent = fallback;
                    }
                } else {
                    root.innerHTML = '<div class="fallback"></div>';
                    root.firstChild.textContent = fallback;
                }
                reportHeight();
            }
            $heightReporterJs
            window.addEventListener('load', render);
            setTimeout(render, 80);
            </script></body></html>
        """.trimIndent()

        RichVisualKind.KATEX_RICH_TEXT -> """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/mhchem.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>
            <style>$baseCss
                #root { font-size: 15.5px; line-height: 1.65; padding: 2px 0; word-break: break-word; }
                code { font-family: monospace; font-size: 0.9em; padding: 1px 5px; border-radius: 5px; background: $surfaceHex; color: $accentHex; }
                strong { font-weight: 700; }
            </style></head><body><div id="root">${markdownInlineToHtml(payload)}</div><script>
            function render() {
                const root = document.getElementById('root');
                if (window.renderMathInElement) {
                    try {
                        renderMathInElement(root, {
                            delimiters: [
                                { left: '$$', right: '$$', display: true },
                                { left: '\\[', right: '\\]', display: true },
                                { left: '$', right: '$', display: false },
                                { left: '\\(', right: '\\)', display: false }
                            ],
                            throwOnError: false,
                            strict: false,
                            trust: true
                        });
                    } catch (e) {}
                }
                reportHeight();
            }
            $heightReporterJs
            window.addEventListener('load', render);
            setTimeout(render, 80);
            </script></body></html>
        """.trimIndent()

        RichVisualKind.MERMAID -> """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
            <style>$baseCss
                #root { padding: 10px 6px; text-align: center; }
                .mermaid svg { max-width: 100% !important; height: auto !important; }
            </style></head><body><div id="root"><div id="diagram"></div></div><script>
            const code = `${escapeJsTemplate(payload)}`;
            async function render() {
                const target = document.getElementById('diagram');
                if (!window.mermaid) {
                    target.innerHTML = '<div class="err">离线状态下无法加载 Mermaid 引擎，请切换「源码」查看。</div>';
                    reportHeight();
                    return;
                }
                try {
                    mermaid.initialize({
                        startOnLoad: false,
                        securityLevel: 'loose',
                        theme: '${if (isDark) "dark" else "default"}',
                        fontFamily: '-apple-system, Roboto, "Noto Sans SC", sans-serif'
                    });
                    const id = 'mmd_' + Math.floor(Math.random() * 100000);
                    const { svg } = await mermaid.render(id, code);
                    target.innerHTML = svg;
                } catch (e) {
                    target.innerHTML = '<div class="err">Mermaid 渲染失败：' + (e.message || e) + '</div>';
                }
                reportHeight();
            }
            $heightReporterJs
            window.addEventListener('load', render);
            </script></body></html>
        """.trimIndent()

        RichVisualKind.SVG -> """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>$baseCss
                #root { padding: 10px; display: flex; justify-content: center; align-items: center; }
                svg { max-width: 100%; height: auto; }
            </style></head><body><div id="root">$payload</div><script>
            $heightReporterJs
            </script></body></html>
        """.trimIndent()

        RichVisualKind.CHART -> """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js"></script>
            <style>$baseCss
                #root { padding: 10px; height: 260px; position: relative; }
                canvas { width: 100% !important; height: 240px !important; }
            </style></head><body><div id="root"><canvas id="c"></canvas></div><script>
            const raw = `${escapeJsTemplate(payload)}`;
            function render() {
                const root = document.getElementById('root');
                if (!window.Chart) {
                    root.innerHTML = '<div class="err">离线状态下无法加载 Chart.js，请切换「源码」查看 JSON 数据。</div>';
                    reportHeight();
                    return;
                }
                try {
                    const cfg = JSON.parse(raw);
                    Chart.defaults.color = '$textHex';
                    Chart.defaults.borderColor = '$borderHex';
                    cfg.options = cfg.options || {};
                    cfg.options.responsive = true;
                    cfg.options.maintainAspectRatio = false;
                    new Chart(document.getElementById('c'), cfg);
                } catch (e) {
                    root.innerHTML = '<div class="err">图表配置解析失败：' + (e.message || e) + '</div>';
                }
                reportHeight();
            }
            $heightReporterJs
            window.addEventListener('load', render);
            </script></body></html>
        """.trimIndent()

        RichVisualKind.GRAPHVIZ -> """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://cdn.jsdelivr.net/npm/@viz-js/viz@3.8.0/lib/viz-standalone.js"></script>
            <style>$baseCss
                #root { padding: 10px; text-align: center; }
            </style></head><body><div id="root"><div id="gv"></div></div><script>
            const dot = `${escapeJsTemplate(payload)}`;
            async function render() {
                const el = document.getElementById('gv');
                if (!window.Viz) {
                    el.innerHTML = '<div class="err">离线状态下无法加载 Graphviz 引擎，请切换「源码」查看。</div>';
                    reportHeight();
                    return;
                }
                try {
                    const viz = await Viz.instance();
                    const svg = viz.renderSVGElement(dot);
                    el.innerHTML = '';
                    el.appendChild(svg);
                } catch (e) {
                    el.innerHTML = '<div class="err">Graphviz 渲染失败：' + (e.message || e) + '</div>';
                }
                reportHeight();
            }
            $heightReporterJs
            window.addEventListener('load', render);
            </script></body></html>
        """.trimIndent()

        RichVisualKind.HTML -> {
            val trimmed = payload.trim()
            if (trimmed.contains("<html", ignoreCase = true) || trimmed.contains("<!doctype", ignoreCase = true)) {
                "$trimmed<script>$heightReporterJs</script>"
            } else {
                """
                    <!doctype html><html><head><meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>$baseCss</style></head><body><div id="root">$trimmed</div>
                    <script>$heightReporterJs</script></body></html>
                """.trimIndent()
            }
        }
    }
}

private fun markdownInlineToHtml(src: String): String {
    var html = escapeHtml(src)
    html = Regex("""\*\*(.+?)\*\*""").replace(html, "<strong>$1</strong>")
    html = Regex("""__(.+?)__""").replace(html, "<strong>$1</strong>")
    html = Regex("""~~(.+?)~~""").replace(html, "<del>$1</del>")
    html = Regex("""`([^`]+)`""").replace(html, "<code>$1</code>")
    html = Regex("""\[([^\]]+)]\(([^)]+)\)""").replace(html) { m ->
        val label = m.groupValues[1]
        val url = m.groupValues[2].trim()
        if (url.startsWith("http://") || url.startsWith("https://")) {
            """<a href="$url">$label</a>"""
        } else label
    }
    return html.replace("\n", "<br/>")
}

/**
 * 自适应高度的透明 WebView 富渲染器（支持全屏放大查看与自动释放防内存泄漏）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichWebCanvas(
    kind: RichVisualKind,
    payload: String,
    modifier: Modifier = Modifier,
    minHeightDp: Int = 44,
    maxHeightDp: Int = 520,
    allowFullscreen: Boolean = false,
    onLink: (String) -> Unit = {},
) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    var contentHeightPx by remember(kind, payload, p.isDark) {
        mutableIntStateOf(with(density) { minHeightDp.dp.roundToPx() })
    }
    var fullscreen by remember { mutableStateOf(false) }

    val html = remember(kind, payload, p.isDark, p.text, p.primary, p.surfaceAlt, p.border) {
        buildRichHtml(
            kind = kind,
            payload = payload,
            isDark = p.isDark,
            textHex = p.text.toCssHex(),
            accentHex = p.primary.toCssHex(),
            surfaceHex = p.surfaceAlt.toCssHex(),
            borderHex = p.border.toCssHex(),
        )
    }

    val heightDp = with(density) {
        contentHeightPx.toDp().coerceIn(minHeightDp.dp, maxHeightDp.dp)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = true
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = false
                        loadWithOverviewMode = false
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onHeight(cssHeight: Int) {
                                post {
                                    val px = (cssHeight * resources.displayMetrics.density).toInt()
                                    if (px > 0 && kotlin.math.abs(px - contentHeightPx) > 4) {
                                        contentHeightPx = px
                                    }
                                }
                            }
                        },
                        "AndroidBridge",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val u = request?.url?.toString() ?: return false
                            if (u.startsWith("http://") || u.startsWith("https://")) {
                                onLink(u)
                                return true
                            }
                            return false
                        }
                    }
                    tag = html
                    loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                if (webView.tag != html) {
                    webView.tag = html
                    webView.loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
                }
            },
            onRelease = { webView ->
                runCatching {
                    webView.stopLoading()
                    webView.removeJavascriptInterface("AndroidBridge")
                    webView.loadUrl("about:blank")
                    webView.destroy()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp),
        )

        if (allowFullscreen) {
            Tappable(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.surface.copy(alpha = 0.78f))
                    .border(1.dp, p.border, RoundedCornerShape(8.dp)),
                onClick = { fullscreen = true },
            ) {
                Icon(
                    Icons.Rounded.Fullscreen,
                    contentDescription = "全屏查看",
                    tint = p.dim,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(18.dp),
                )
            }
        }
    }

    if (fullscreen) {
        FullscreenRichDialog(
            title = kind.label,
            html = html,
            onDismiss = { fullscreen = false },
            onLink = onLink,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FullscreenRichDialog(
    title: String,
    html: String,
    onDismiss: () -> Unit,
    onLink: (String) -> Unit,
) {
    val p = LocalPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(p.bg)
                .padding(top = 12.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                IconTap(Icons.Rounded.Close, "关闭全屏", onDismiss, tint = p.text)
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val u = request?.url?.toString() ?: return false
                                if (u.startsWith("http://") || u.startsWith("https://")) {
                                    onLink(u)
                                    return true
                                }
                                return false
                            }
                        }
                        loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
                    }
                },
                onRelease = { webView ->
                    runCatching {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.destroy()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}
