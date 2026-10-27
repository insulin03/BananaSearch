package com.banana.search.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banana.search.core.SoundFx
import kotlinx.coroutines.delay

@Composable
internal fun ThemeToggle(dark: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val knobX by animateDpAsState(
        targetValue = if (dark) 24.dp else 0.dp,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "knob"
    )
    val rot by animateFloatAsState(
        targetValue = if (dark) 180f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "rot"
    )
    val sunA by animateFloatAsState(if (dark) 0f else 1f, tween(220), label = "sun")
    val moonA by animateFloatAsState(if (dark) 1f else 0f, tween(220), label = "moon")

    Box(
        modifier = Modifier
            .size(56.dp, 32.dp)

            .clip(RoundedCornerShape(16.dp))
            .background(pal.track)
            .clickable {
                SoundFx.play()
                onChange(!dark)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset((4.dp + knobX).roundToPx(), 0) }
                .size(26.dp)
                .clip(CircleShape)
                .background(pal.accent),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.graphicsLayer { rotationZ = rot }) {
                SunGlyph(
                    modifier = Modifier
                        .size(15.dp)
                        .alpha(sunA),
                    color = pal.accentInk
                )
                MoonGlyph(
                    modifier = Modifier
                        .size(15.dp)
                        .alpha(moonA),
                    color = pal.accentInk,
                    cut = pal.accent
                )
            }
        }
    }
}

@Composable
internal fun SoundToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val tint = if (on) pal.title else pal.muted
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(pal.track)
            .clickable {
                SoundFx.play()
                onChange(!on)
            },
        contentAlignment = Alignment.Center
    ) {
        SpeakerGlyph(Modifier.size(16.dp), tint, on)
    }
}

@Composable
internal fun SpeakerGlyph(modifier: Modifier, color: Color, on: Boolean) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cone = Path().apply {
            moveTo(w * 0.10f, h * 0.38f)
            lineTo(w * 0.26f, h * 0.38f)
            lineTo(w * 0.46f, h * 0.16f)
            lineTo(w * 0.46f, h * 0.84f)
            lineTo(w * 0.26f, h * 0.62f)
            lineTo(w * 0.10f, h * 0.62f)
            close()
        }
        drawPath(cone, color)
        if (on) {
            drawArc(
                color = color,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(w * 0.40f, h * 0.30f),
                size = Size(w * 0.24f, h * 0.40f),
                style = Stroke(width = h * 0.09f, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.44f, h * 0.16f),
                size = Size(w * 0.40f, h * 0.68f),
                style = Stroke(width = h * 0.09f, cap = StrokeCap.Round)
            )
        } else {
            drawLine(
                color = color,
                start = Offset(w * 0.64f, h * 0.34f),
                end = Offset(w * 0.92f, h * 0.66f),
                strokeWidth = h * 0.10f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun SunGlyph(modifier: Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, radius = r * 0.42f, center = c)
        val inner = r * 0.66f
        val outer = r * 0.98f
        repeat(8) { i ->
            val a = (i * 45f) * (Math.PI / 180f).toFloat()
            drawLine(
                color = color,
                start = Offset(c.x + kotlin.math.cos(a) * inner, c.y + kotlin.math.sin(a) * inner),
                end = Offset(c.x + kotlin.math.cos(a) * outer, c.y + kotlin.math.sin(a) * outer),
                strokeWidth = r * 0.16f
            )
        }
    }
}

@Composable
internal fun MoonGlyph(modifier: Modifier, color: Color, cut: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val r = size.minDimension * 0.46f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, radius = r, center = c)
        drawCircle(cut, radius = r * 0.92f, center = Offset(c.x + r * 0.72f, c.y - r * 0.34f))
    }
}

@Composable
internal fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val pal = LocalPal.current
    val titles = listOf("启动", "识别", "关于", "题库")

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.5.dp))
    ) {
        val gap = 6.dp
        val pillW = (maxWidth - gap * (titles.size - 1)) / titles.size

        val x by animateDpAsState(
            targetValue = (pillW + gap) * selected,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "tabX"
        )

        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(pillW)
                .height(38.dp)
                .clip(RoundedCornerShape(10.5.dp))
                .background(pal.accentSoft)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            titles.forEachIndexed { index, title ->
                val on = selected == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.5.dp))

                        .pressable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = 13.5.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) pal.title else pal.muted
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScrollPane(content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    val state = rememberScrollState()
    var barVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.value) {
        if (state.maxValue > 0) {
            barVisible = true
            delay(900)
            barVisible = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(state)
                .padding(start = 14.dp, end = 16.dp, top = 6.dp, bottom = 18.dp),
            content = content
        )

        val max = state.maxValue
        if (max > 0) {

            val density = LocalDensity.current
            val viewportPx = with(density) { maxHeight.toPx() }
            val frac = (viewportPx / (viewportPx + max)).coerceIn(0.06f, 1f)
            val thumbPx = (viewportPx * frac).coerceAtLeast(with(density) { 44.dp.toPx() })
            val travelPx = (viewportPx - thumbPx).coerceAtLeast(1f)
            val top = with(density) {
                ((state.value.toFloat() / max) * travelPx).toDp()
            }
            val thumb = with(density) { thumbPx.toDp() }
            val a by animateFloatAsState(if (barVisible) 1f else 0f, tween(320), label = "barA")
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 3.dp, top = 8.dp)
                    .offset(y = top)
                    .width(3.dp)
                    .height(thumb)
                    .clip(RoundedCornerShape(4.dp))
                    .alpha(a)
                    .background(
                        if (pal.isDark) pal.accent.copy(alpha = 0.55f)
                        else pal.accent.copy(alpha = 0.75f)
                    )
            )
        }
    }
}

@Composable
internal fun SectionCard(
    index: Int,
    title: String,
    help: String? = null,

    locked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val pal = LocalPal.current
    var showHelp by remember { mutableStateOf(false) }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 25L)
        shown = true
    }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(160), label = "cardA")
    val lift by animateDpAsState(if (shown) 0.dp else 14.dp, tween(160), label = "cardY")

    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .offset { IntOffset(0, lift.roundToPx()) }
            .alpha(appear)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(

                    if (pal.isDark) Modifier
                    else Modifier
                )
                .clip(shape)
                .background(pal.surface)
                .alpha(if (locked) 0.55f else 1f)
        ) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = pal.muted
                    )
                    if (locked) {
                        LockBadge()
                        Spacer(Modifier.width(6.dp))
                    }
                    if (help != null) {
                        Box(
                            modifier = Modifier
                                .size(21.dp)
                                .clip(CircleShape)
                                .background(pal.helpBg)
                                .clickable { showHelp = true },
                            contentAlignment = Alignment.Center
                        ) {
                            QuestionGlyph(Modifier.size(12.dp), pal.helpInk)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }

    if (showHelp && help != null) {
        InfoDialog(title = "说明", onDismiss = { showHelp = false }) {
            Text(help, fontSize = 13.sp, color = pal.text, lineHeight = 20.sp)
        }
    }
}

@Composable
internal fun LockBanner() {
    val pal = LocalPal.current
    Text(
        "服务运行中，本页参数已锁定；先停止服务再修改。",
        fontSize = 11.5.sp,
        color = pal.title,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(pal.accentSoft)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
internal fun LockBadge() {
    val pal = LocalPal.current
    Text(
        "服务运行中",
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Medium,
        color = pal.accentInk,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(pal.accentSoft)
            .padding(horizontal = 5.dp, vertical = 1.5.dp)
    )
}

@Composable
internal fun SegRow(
    options: List<String>,
    selected: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    val pal = LocalPal.current
    val gap = 3.dp
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {

        val innerW = maxWidth - gap * 2
        val slotW = (innerW - gap * (options.size - 1)) / options.size
        val slotPx = with(density) { slotW.toPx() }
        val stepPx = with(density) { (slotW + gap).toPx() }

        val fill = pal.accent
        val progress = animateFloatAsState(
            targetValue = selected.toFloat(),
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "segX"
        )

        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(7.dp))
                .background(pal.track)
        )

        Box(Modifier.fillMaxWidth().padding(gap).clip(RoundedCornerShape(5.5.dp))) {
            Box(
                Modifier.matchParentSize().drawBehind {
                    val x = progress.value * stepPx
                    drawRoundRect(
                        color = fill,
                        topLeft = Offset(x, 0f),
                        size = Size(slotPx, size.height),
                        cornerRadius = CornerRadius(5.5.dp.toPx())
                    )
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                options.forEachIndexed { index, label ->
                    val on = index == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(5.5.dp))
                            .selectable(
                        selected = on,
                        enabled = enabled,

                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        SoundFx.play()
                        onSelect(index)
                    },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,

                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 2.dp),
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            color = if (on) pal.accentInk else pal.text
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun InfoDialog(
    title: String,
    onDismiss: () -> Unit,
    showConfirm: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val pal = LocalPal.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {

            if (showConfirm) {
                TextButton(onClick = onDismiss) {
                    Text("知道了", color = pal.orange, fontWeight = FontWeight.Bold)
                }
            }
        },
        title = {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = pal.title)
        },
        text = {

            SecureDialogWindow()
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                content = content
            )
        },
        containerColor = pal.surface,
        textContentColor = pal.text,
        titleContentColor = pal.title,
        shape = RoundedCornerShape(11.dp)
    )
}

@Composable
internal fun Hint(text: String, color: Color? = null) {
    val pal = LocalPal.current
    Text(
        text,
        fontSize = 12.sp,
        color = color ?: pal.muted,
        lineHeight = 18.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
internal fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = tween(90),
        label = "press"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = {
                SoundFx.play()
                onClick()
            }
        )
}

@Composable
internal fun QuestionGlyph(modifier: Modifier = Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.15f
        val paint = androidx.compose.ui.graphics.drawscope.Stroke(
            width = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )

        val hook = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.27f, h * 0.31f)
            cubicTo(w * 0.27f, h * 0.09f, w * 0.73f, h * 0.09f, w * 0.73f, h * 0.32f)
            cubicTo(w * 0.73f, h * 0.46f, w * 0.50f, h * 0.47f, w * 0.50f, h * 0.64f)
        }
        drawPath(hook, color, style = paint)
        drawCircle(
            color = color,
            radius = stroke * 0.55f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.85f)
        )
    }
}

@Composable
internal fun PalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    minHeight: Dp = 42.dp,
    fontSize: TextUnit = 13.sp,
    keyboardType: KeyboardType = KeyboardType.Text,
    masked: Boolean = false
) {
    val pal = LocalPal.current
    var focused by remember { mutableStateOf(false) }
    val border = when {
        !enabled -> pal.line
        focused -> pal.accent
        else -> pal.line
    }
    val radius = RoundedCornerShape(8.dp)
    Column(modifier) {
        if (label != null) {
            Text(label, fontSize = 11.sp, color = pal.muted, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = if (enabled) pal.text else pal.muted,
                fontSize = fontSize,
                lineHeight = fontSize * 1.5f
            ),
            cursorBrush = SolidColor(pal.orange),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .clip(radius)
                        .background(pal.surfaceSoft)
                        .border(1.dp, border, radius)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, fontSize = fontSize, color = pal.muted)
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
internal fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,

    fill: Color? = null,

    ink: Color? = null,
    onClick: () -> Unit
) {
    val pal = LocalPal.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, tween(90), label = "pressMain")
    Button(
        onClick = {
            SoundFx.play()
            onClick()
        },
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        colors = ButtonDefaults.buttonColors(
            containerColor = fill ?: pal.accent,
            contentColor = ink ?: pal.accentInk,
            disabledContainerColor = pal.surfaceSoft,
            disabledContentColor = pal.muted
        ),
        shape = RoundedCornerShape(7.dp)
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun GhostButton(
    text: String,
    enabled: Boolean = true,
    color: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = LocalPal.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, tween(90), label = "pressGhost")
    OutlinedButton(
        onClick = {
            SoundFx.play()
            onClick()
        },
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        border = BorderStroke(1.dp, pal.line),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color ?: pal.text,

            disabledContentColor = pal.muted.copy(alpha = 0.75f)
        ),
        shape = RoundedCornerShape(7.dp)
    ) { Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium) }
}

@Composable
internal fun KeyButton(
    text: String,
    enabled: Boolean = true,
    fill: Color? = null,
    ink: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = LocalPal.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, tween(90), label = "pressKey")
    val shape = RoundedCornerShape(7.dp)
    val pad = PaddingValues(horizontal = 2.dp)
    val box = modifier.height(36.dp).graphicsLayer { scaleX = scale; scaleY = scale }
    if (fill != null) {
        Button(
            onClick = {
                SoundFx.play()
                onClick()
            },
            enabled = enabled,
            interactionSource = interaction,
            modifier = box,
            contentPadding = pad,
            colors = ButtonDefaults.buttonColors(
                containerColor = fill,
                contentColor = ink,
                disabledContainerColor = pal.track,
                disabledContentColor = pal.muted
            ),
            shape = shape
        ) {
            Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    } else {
        OutlinedButton(
            onClick = {
                SoundFx.play()
                onClick()
            },
            enabled = enabled,
            interactionSource = interaction,
            modifier = box,
            contentPadding = pad,
            border = BorderStroke(1.dp, pal.line),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ink,
                disabledContentColor = pal.muted.copy(alpha = 0.75f)
            ),
            shape = shape
        ) {
            Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
internal fun PermRow(
    title: String,
    desc: String,
    granted: Boolean,
    required: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val pal = LocalPal.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable { onAction() }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = pal.text
                )
                if (required) {
                    Text(" *", fontSize = 12.sp, color = pal.orange)
                }
            }
            Text(desc, fontSize = 11.sp, color = pal.muted, lineHeight = 16.sp)
        }
        Text(
            if (granted) "已授权" else actionLabel,
            color = if (granted) pal.green else pal.orange,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text("›", color = pal.muted, fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp))
    }
    HorizontalDivider(color = pal.line, thickness = 1.dp)
}

@Composable
internal fun AboutRow(
    title: String,
    sub: String,
    showDivider: Boolean = false,
    onClick: () -> Unit
) {
    val pal = LocalPal.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = pal.title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.sp, color = pal.muted)
        }
        Text("›", fontSize = 18.sp, color = pal.muted)
    }

    if (showDivider) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(pal.line)
        )
    }
}

@Composable
internal fun FramePreviewOverlay(widthDp: Int, heightDp: Int, onClose: () -> Unit) {
    val pal = LocalPal.current
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(widthDp.dp, heightDp.dp)
                .drawBehind {
                    drawRoundRect(
                        color = pal.accent,
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash)
                    )
                }
        ) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "框体预览 · ${widthDp}×${heightDp}dp",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = pal.accent
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "实际识别时内容超出会在框内滚动",
                    fontSize = 10.5.sp,
                    color = pal.muted
                )
            }
            PrimaryButton(
                text = "关闭预览",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
            ) { onClose() }
        }
    }
}
