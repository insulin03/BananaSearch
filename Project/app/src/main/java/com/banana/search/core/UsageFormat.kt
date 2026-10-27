package com.banana.search.core

import java.util.Locale

object UsageFormat {

    fun rmb(usage: DeepSeekUsage, inputPerM: Double, outputPerM: Double): Double =
        usage.promptTokens / 1_000_000.0 * inputPerM +
            usage.completionTokens / 1_000_000.0 * outputPerM

    fun money(yuan: Double): String = when {
        yuan <= 0.0 -> "¥0"
        yuan < 0.01 -> String.format(Locale.US, "¥%.4f", yuan)
        yuan < 1.0 -> String.format(Locale.US, "¥%.3f", yuan)
        else -> String.format(Locale.US, "¥%.2f", yuan)
    }

    fun summary(usage: DeepSeekUsage, inputPerM: Double, outputPerM: Double): String {
        val total = usage.promptTokens + usage.completionTokens
        return "${grouped(total)} token ≈ ${money(rmb(usage, inputPerM, outputPerM))}"
    }

    fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)
}
