// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hotsteel.deepseekhot.data.Usage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Fmt {

    fun tokens(n: Int): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.2fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1_000.0)
        else -> n.toString()
    }

    fun money(v: Double): String = when {
        v <= 0.0 -> "¥0"
        v < 0.01 -> String.format(Locale.US, "¥%.4f", v)
        v < 1.0 -> String.format(Locale.US, "¥%.3f", v)
        else -> String.format(Locale.US, "¥%.2f", v)
    }

    fun duration(ms: Long): String = when {
        ms < 1000 -> "${ms}ms"
        ms < 60_000 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
        else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
    }

    fun speed(tokens: Int, ms: Long): String {
        if (tokens <= 0 || ms <= 0) return "—"
        val tps = tokens / (ms / 1000.0)
        return String.format(Locale.US, "%.1f tok/s", tps)
    }

    fun clock(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))

    fun dayLabel(ts: Long): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
            else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
        }
    }

    fun relative(ts: Long): String {
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000} 天前"
            else -> dayLabel(ts)
        }
    }

    fun bytes(n: Long): String = when {
        n >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", n / 1024.0 / 1024.0)
        n >= 1024 -> String.format(Locale.US, "%d KB", n / 1024)
        else -> "$n B"
    }

    fun greeting(): String {
        val h = Instant.now().atZone(ZoneId.systemDefault()).hour
        return when {
            h < 5 -> "夜深了"
            h < 9 -> "早上好"
            h < 12 -> "上午好"
            h < 14 -> "中午好"
            h < 18 -> "下午好"
            else -> "晚上好"
        }
    }
}

object Pricing {

    private fun isPeak(at: Long = System.currentTimeMillis()): Boolean {
        val z = Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Shanghai"))
        val dow = z.dayOfWeek.value
        if (dow >= 6) return false
        val minutes = z.hour * 60 + z.minute
        return minutes in 540 until 720 || minutes in 840 until 1080
    }

    /** 按官方价目表估算，单位：元 / 百万 token。 */
    fun estimate(model: String, usage: Usage, at: Long = System.currentTimeMillis()): Double {
        val peak = isPeak(at)
        val pro = model.contains("pro")
        val cachePrice = if (pro) (if (peak) 0.30 else 0.15) else (if (peak) 0.04 else 0.02)
        val inputPrice = if (pro) (if (peak) 9.0 else 4.5) else (if (peak) 2.0 else 1.0)
        val outputPrice = if (pro) (if (peak) 27.0 else 13.5) else (if (peak) 8.0 else 4.0)
        val cached = usage.cached.coerceAtMost(usage.input)
        val uncached = (usage.input - cached).coerceAtLeast(0)
        return (cached * cachePrice + uncached * inputPrice + usage.output * outputPrice) / 1_000_000.0
    }

    fun priceLabel(model: String): String = if (model.contains("pro")) {
        "Pro · 输入 ¥4.5/9 · 输出 ¥13.5/27 每百万"
    } else {
        "Flash · 输入 ¥1/2 · 输出 ¥4/8 每百万"
    }
}

object Sys {
    fun copy(context: Context, text: String, label: String = "已复制") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("deepseek-hot", text))
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun share(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
