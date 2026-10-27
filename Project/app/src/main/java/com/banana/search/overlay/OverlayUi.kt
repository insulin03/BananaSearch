package com.banana.search.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.banana.search.R
import com.banana.search.core.RecognizeOutcome

object OverlayUi {

    const val TAG_DRAG_HANDLE = "qsearch_drag_handle"

    class Pal(
        val glass: Int,
        val stroke: Int,
        val text: Int,
        val sub: Int,
        val line: Int,
        val accent: Int,
        val accentInk: Int,
        val green: Int,
        val orange: Int,
        val red: Int,
        val soft: Int
    )

    private val light = Pal(
        glass = 0xF7FFFFFF.toInt(),
        stroke = 0x1F000000,
        text = 0xFF2E2A1F.toInt(),
        sub = 0xFF8B8471.toInt(),
        line = 0xFFEFE8D7.toInt(),
        accent = 0xFFFFC93C.toInt(),
        accentInk = 0xFF1E1A08.toInt(),
        green = 0xFF2E9E63.toInt(),
        orange = 0xFFC48111.toInt(),
        red = 0xFFD2453B.toInt(),
        soft = 0xFFF7F2E4.toInt()
    )

    private val darkPal = Pal(
        glass = 0xF21D1A11.toInt(),
        stroke = 0x33FFFFFF,
        text = 0xFFF2EAD3.toInt(),
        sub = 0xFF9A9280.toInt(),
        line = 0xFF302A1A.toInt(),
        accent = 0xFFFFD35C.toInt(),
        accentInk = 0xFF1A1503.toInt(),
        green = 0xFF6BD69A.toInt(),
        orange = 0xFFE8A63C.toInt(),
        red = 0xFFF0705F.toInt(),
        soft = 0xFF262115.toInt()
    )

    fun palette(dark: Boolean): Pal = if (dark) darkPal else light

    fun dp(ctx: Context, v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics
    ).toInt()

    fun roundedBox(fill: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
        rounded(fill, radiusPx, strokeColor, strokePx)

    private fun rounded(fill: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = radiusPx
        d.shape = GradientDrawable.RECTANGLE
        d.setColor(fill)
        if (strokePx > 0) d.setStroke(strokePx, strokeColor)
        return d
    }

    private fun oval(fill: Int, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(fill)
        if (strokePx > 0) d.setStroke(strokePx, strokeColor)
        return d
    }

    private fun tv(
        ctx: Context,
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        maxLines: Int = Int.MAX_VALUE
    ): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        this.maxLines = maxLines
        ellipsize = if (maxLines != Int.MAX_VALUE) android.text.TextUtils.TruncateAt.END else null
        setLineSpacing(dp(ctx, 2f).toFloat(), 1f)
    }

    enum class Glyph { SELECT, FULL, RECOGNIZE, SHOW, MANUAL, STOP }

    @SuppressLint("ViewConstructor")
    class GlyphView(
        ctx: Context,
        private val kind: Glyph,
        var color: Int
    ) : View(ctx) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(ctx, 1.7f).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        private val dash = android.graphics.DashPathEffect(
            floatArrayOf(dp(context, 3f).toFloat(), dp(context, 2.4f).toFloat()), 0f
        )
        private val rectF = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = color
            fillPaint.color = color
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val r = kotlin.math.min(w, h) / 2f - paint.strokeWidth
            when (kind) {

                Glyph.SELECT -> {
                    paint.pathEffect = dash
                    rectF.set(cx - r, cy - r, cx + r, cy + r)
                    canvas.drawRoundRect(rectF, r * 0.35f, r * 0.35f, paint)
                    paint.pathEffect = null
                }

                Glyph.FULL -> {
                    val l = cx - r
                    val t = cy - r
                    val rr = cx + r
                    val b = cy + r
                    val arm = r * 0.55f
                    canvas.drawLine(l, t + arm, l, t, paint)
                    canvas.drawLine(l, t, l + arm, t, paint)
                    canvas.drawLine(rr - arm, t, rr, t, paint)
                    canvas.drawLine(rr, t, rr, t + arm, paint)
                    canvas.drawLine(rr, b - arm, rr, b, paint)
                    canvas.drawLine(rr, b, rr - arm, b, paint)
                    canvas.drawLine(l + arm, b, l, b, paint)
                    canvas.drawLine(l, b, l, b - arm, paint)
                }

                Glyph.RECOGNIZE -> {
                    canvas.drawCircle(cx, cy, r * 0.92f, paint)
                    canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, paint)
                    canvas.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.45f, paint)
                }

                Glyph.SHOW -> {
                    canvas.drawCircle(cx, cy, r * 0.40f, fillPaint)
                    canvas.drawCircle(cx, cy, r * 0.92f, paint)
                }

                Glyph.STOP -> {
                    rectF.set(cx - r, cy - r, cx + r, cy + r)
                    canvas.drawArc(rectF, -70f, 320f, false, paint)
                    canvas.drawLine(cx, cy - r * 1.12f, cx, cy - r * 0.12f, paint)
                }

                Glyph.MANUAL -> {

                    val len = r * 0.72f
                    canvas.drawLine(
                        cx - len, cy + len,
                        cx + len, cy - len,
                        paint
                    )
                }
            }
        }
    }

    fun createConfirmCard(
        ctx: Context,
        dark: Boolean,
        title: String,
        message: String,
        confirmText: String,
        cancelText: String = "取消",
        onCancel: () -> Unit,
        onConfirm: () -> Unit
    ): LinearLayout {
        val p = palette(dark)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(p.glass, dp(ctx, 16f).toFloat(), p.stroke, dp(ctx, 1f))
            setPadding(dp(ctx, 16f), dp(ctx, 14f), dp(ctx, 16f), dp(ctx, 12f))
            elevation = dp(ctx, 10f).toFloat()

            addView(tv(ctx, title, 15f, p.text, bold = true))
            addView(
                tv(ctx, message, 11.5f, p.sub),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 6f) }
            )

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val cancel = tv(ctx, cancelText, 12.5f, p.sub, bold = true).apply {
                gravity = Gravity.CENTER
                background = rounded(p.soft, dp(ctx, 9f).toFloat(), p.line, dp(ctx, 1f))
                setPadding(dp(ctx, 14f), dp(ctx, 9f), dp(ctx, 14f), dp(ctx, 9f))
                isClickable = true
                setOnClickListener { onCancel() }
            }
            val confirm = tv(ctx, confirmText, 12.5f, p.accentInk, bold = true).apply {
                gravity = Gravity.CENTER
                background = rounded(p.red, dp(ctx, 9f).toFloat())
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(dp(ctx, 14f), dp(ctx, 9f), dp(ctx, 14f), dp(ctx, 9f))
                isClickable = true
                setOnClickListener { onConfirm() }
            }
            row.addView(
                cancel,
                LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(ctx, 8f) }
            )
            row.addView(confirm, LinearLayout.LayoutParams(0, -2, 1f))
            addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 12f) })
        }
    }

    fun createActionItem(
        ctx: Context,
        dark: Boolean,
        label: String,
        glyph: Glyph,
        tint: Int
    ): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(ctx, 7f), dp(ctx, 4f), dp(ctx, 7f), dp(ctx, 4f))
        isClickable = true

        setBackgroundResource(android.R.color.transparent)

        val ink = 0xFF2B2415.toInt()

        val glyphSize = dp(ctx, 23f)
        addView(GlyphView(ctx, glyph, ink), LinearLayout.LayoutParams(glyphSize, glyphSize))
        val text = tv(ctx, label, 9.5f, ink, bold = true)
        text.setShadowLayer(dp(ctx, 2f).toFloat(), 0f, 0f, 0xE6FFFFFF.toInt())
        text.gravity = Gravity.CENTER
        text.setPadding(0, dp(ctx, 2f), 0, 0)
        addView(
            text,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        tag = ink
    }

    fun setItemLabel(item: View, label: String) {
        ((item as? LinearLayout)?.getChildAt(1) as? TextView)?.text = label
    }

    fun setItemActive(ctx: Context, item: View, active: Boolean, dark: Boolean, animate: Boolean = true) {
        val p = palette(dark)
        val normal = (item.tag as? Int) ?: p.text
        val ll = item as? LinearLayout
        val glyph = ll?.getChildAt(0) as? GlyphView
        val label = ll?.getChildAt(1) as? TextView
        val fromInk = label?.currentTextColor ?: normal
        val toInk = if (active) p.accentInk else normal
        val fill = rounded(p.accent, dp(ctx, 14f).toFloat())

        if (!animate) {
            glyph?.let { it.color = toInk; it.invalidate() }
            label?.setTextColor(toInk)
            item.background = if (active) fill else null
            return
        }

        if (!active && item.background == null && fromInk == toInk) return

        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).setDuration(150L)
        anim.addUpdateListener { a ->
            val f = a.animatedValue as Float
            val ink = android.animation.ArgbEvaluator().evaluate(f, fromInk, toInk) as Int
            glyph?.let { it.color = ink; it.invalidate() }
            label?.setTextColor(ink)
            if (active) {
                fill.alpha = (255 * f).toInt()
                item.background = fill
            } else {
                fill.alpha = (255 * (1f - f)).toInt()
                item.background = if (f >= 1f) null else fill
            }
        }
        anim.start()
    }

    fun createCircleButton(ctx: Context, dark: Boolean): FrameLayout = FrameLayout(ctx).apply {

        val ring = if (dark) 0xA6FFD35C.toInt() else 0x8CFFC93C.toInt()
        background = oval(android.graphics.Color.TRANSPARENT, ring, dp(ctx, 1.6f))
    }

    fun createPanelRoot(ctx: Context, dark: Boolean): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = palette(dark)
        background = rounded(p.glass, dp(ctx, 18f).toFloat(), p.stroke, dp(ctx, 1f))
        setPadding(dp(ctx, 12f), dp(ctx, 10f), dp(ctx, 12f), dp(ctx, 10f))
        elevation = dp(ctx, 10f).toFloat()
    }

    fun renderPanel(
        ctx: Context,
        panel: LinearLayout,
        outcome: RecognizeOutcome?,
        dark: Boolean,
        onClose: () -> Unit
    ) {
        panel.removeAllViews()
        val p = palette(dark)

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = TAG_DRAG_HANDLE
            setPadding(0, 0, 0, dp(ctx, 4f))
        }
        val grab = View(ctx).apply {
            background = rounded(p.sub, dp(ctx, 2f).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 14f), dp(ctx, 3f)).apply {
                rightMargin = dp(ctx, 7f)
            }
        }
        header.addView(grab)
        header.addView(
            tv(ctx, ctx.getString(R.string.app_title), 11.5f, p.sub, bold = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (outcome != null && outcome.ok) {
            val badge = tv(ctx, outcome.tier.label, 9f, p.accentInk, bold = true).apply {
                background = rounded(badgeColor(outcome, dark), dp(ctx, 9f).toFloat())
                setPadding(dp(ctx, 7f), dp(ctx, 2f), dp(ctx, 7f), dp(ctx, 2f))
            }
            header.addView(badge, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(ctx, 4f) })
        }
        val close = tv(ctx, "✕", 13f, p.sub, bold = true).apply {
            setPadding(dp(ctx, 8f), dp(ctx, 2f), dp(ctx, 2f), dp(ctx, 2f))
            setOnClickListener { onClose() }
        }
        header.addView(close)
        panel.addView(header)

        if (outcome == null) {

            panel.addView(tv(ctx, "正在识别…", 12f, p.sub).apply {
                setPadding(0, dp(ctx, 8f), 0, dp(ctx, 6f))
            })
            repeat(2) {
                val bar = View(ctx).apply {
                    background = rounded(p.soft, dp(ctx, 4f).toFloat())
                }
                panel.addView(bar, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 10f)
                ).apply { bottomMargin = dp(ctx, 6f) })
            }
            return
        }

        val statusColor = when {
            !outcome.ok -> p.red
            outcome.tier == RecognizeOutcome.Tier.LOCAL -> p.green
            else -> p.orange
        }
        panel.addView(tv(ctx, outcome.message, 10.5f, statusColor).apply {
            setPadding(0, dp(ctx, 2f), 0, dp(ctx, 4f))
        })

        if (outcome.ambiguous) {
            val warn = if (outcome.cloudAnswer.isNotBlank()) {
                "本地候选区分度低：前两名分数接近，题库里有措辞相近但含义不同的题。请以模型答案为准，或框选更精确的区域后重试。"
            } else {
                "本地候选区分度低：前两名分数接近，题库里有措辞相近但含义不同的题。请框选更精确的区域，或用「手动」输入题干确认。"
            }
            panel.addView(tv(ctx, warn, 9.5f, p.orange).apply {
                setPadding(dp(ctx, 7f), dp(ctx, 5f), dp(ctx, 7f), dp(ctx, 5f))
                background = rounded(0x1FC48111, dp(ctx, 8f).toFloat())
            })
        }

        val scroll = MaxHeightScrollView(ctx, dp(ctx, 210f)).apply {
            isVerticalScrollBarEnabled = false
        }
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val shown = outcome.matchedQuery.ifBlank { outcome.ocrText }
        if (shown.isNotBlank()) {
            body.addView(tv(ctx, shown, 12.5f, p.text, maxLines = 2).apply {
                setPadding(0, dp(ctx, 2f), 0, dp(ctx, 6f))
            })
        }

        if (outcome.top.isNotEmpty()) {
            body.addView(sectionTitle(ctx, "候选 ${outcome.top.size} 道", dark))
            outcome.top.forEachIndexed { index, scored ->
                body.addView(
                    candidateCard(
                        ctx, index + 1, scored.percent, scored.item.question, scored.item.answers,
                        com.banana.search.core.QuestionTypes.infer(
                            scored.item.question, scored.item.type, scored.item.answers
                        ),
                        dark
                    )
                )
            }
        } else {
            body.addView(sectionTitle(ctx, "候选", dark))
            body.addView(tv(ctx, "没有找到可用的候选题目", 10.5f, p.sub))
        }

        if (outcome.cloudAnswer.isNotBlank()) {
            body.addView(sectionTitle(ctx, "模型答案", dark))
            if (outcome.cloudQuestion.isNotBlank()) {
                body.addView(tv(ctx, outcome.cloudQuestion, 10.5f, p.sub, maxLines = 2))
            }
            body.addView(tv(ctx, outcome.cloudAnswer, 12.5f, p.text, bold = true).apply {
                setPadding(0, dp(ctx, 2f), 0, dp(ctx, 2f))
            })
            if (outcome.cloudExplanation.isNotBlank()) {
                body.addView(tv(ctx, outcome.cloudExplanation, 10.5f, p.sub))
            }
        }

        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 6f), 0, 0)
        }
        val meta = buildString {
            append("耗时 ${outcome.elapsedMs}ms")

            outcome.usage?.let {
                val st = com.banana.search.core.Settings(ctx)
                append(" · ").append(
                    com.banana.search.core.UsageFormat.summary(it, st.priceInputPerM, st.priceOutputPerM)
                )
            }
        }
        footer.addView(
            tv(ctx, meta, 9.5f, p.sub),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        footer.addView(tv(ctx, "复制", 11f, p.orange, bold = true).apply {
            setPadding(dp(ctx, 10f), dp(ctx, 3f), dp(ctx, 3f), dp(ctx, 3f))
            setOnClickListener { copyToClipboard(ctx, buildCopyText(ctx, outcome)) }
        })
        panel.addView(footer)
    }

    private fun sectionTitle(ctx: Context, text: String, dark: Boolean): TextView =
        tv(ctx, text, 9.5f, palette(dark).sub, bold = true).apply {
            setPadding(0, dp(ctx, 7f), 0, dp(ctx, 4f))
        }

    private fun candidateCard(
        ctx: Context,
        index: Int,
        percent: Int,
        question: String,
        answers: List<String>,
        type: com.banana.search.core.QuestionType,
        dark: Boolean
    ): LinearLayout {
        val p = palette(dark)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(p.soft, dp(ctx, 12f).toFloat())
            setPadding(dp(ctx, 9f), dp(ctx, 7f), dp(ctx, 9f), dp(ctx, 7f))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(ctx, 6f)
            layoutParams = lp

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                tv(ctx, "候选 $index · ${type.label}", 9.5f, p.sub, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(
                tv(ctx, "接近率 $percent%", 10f, rateColor(percent, dark), bold = true)
            )
            addView(row)

            addView(tv(ctx, question, 11f, p.text, maxLines = 3).apply {
                setPadding(0, dp(ctx, 3f), 0, 0)
            })
            if (answers.isNotEmpty()) {
                addView(tv(ctx, com.banana.search.core.QuestionTypes.answerText(type, answers), 11f, p.green, bold = true).apply {
                    setPadding(0, dp(ctx, 2f), 0, 0)
                })
            }
        }
    }

    private fun badgeColor(outcome: RecognizeOutcome, dark: Boolean): Int {
        val p = palette(dark)
        return when (outcome.tier) {
            RecognizeOutcome.Tier.LOCAL -> p.accent
            RecognizeOutcome.Tier.TEXT -> p.orange
            else -> p.orange
        }
    }

    private fun rateColor(percent: Int, dark: Boolean): Int {
        val p = palette(dark)
        return when {
            percent >= 85 -> p.green
            percent >= 60 -> p.orange
            else -> p.red
        }
    }

    fun buildCopyText(ctx: Context, outcome: RecognizeOutcome): String = buildString {
        append("题目：").append(outcome.matchedQuery.ifBlank { outcome.ocrText }).append('\n')
        if (outcome.cloudAnswer.isNotBlank()) {
            append("答案：").append(outcome.cloudAnswer).append('\n')
            if (outcome.cloudExplanation.isNotBlank()) {
                append("说明：").append(outcome.cloudExplanation).append('\n')
            }
        }
        outcome.usage?.let {
            val st = com.banana.search.core.Settings(ctx)
            append("用量：").append(
                com.banana.search.core.UsageFormat.summary(it, st.priceInputPerM, st.priceOutputPerM)
            ).append('\n')
        }
        outcome.top.forEachIndexed { i, s ->
            val type = com.banana.search.core.QuestionTypes.infer(s.item.question, s.item.type, s.item.answers)
            append("候选 ${i + 1}（${s.percent}%·${type.label}）：").append(s.item.question).append('\n')
            if (s.item.answers.isNotEmpty()) {
                append("  ").append(com.banana.search.core.QuestionTypes.answerText(type, s.item.answers)).append('\n')
            }
        }
        append("—— ").append(ctx.getString(R.string.app_title))
    }

    private fun copyToClipboard(ctx: Context, text: String) {
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(ctx.getString(R.string.app_title), text))
        }
    }

    @SuppressLint("ViewConstructor")
    class MaxHeightScrollView(ctx: Context, private val maxHeightPx: Int) : android.widget.ScrollView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val spec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, spec)
        }
    }
}
