package com.banana.search.ui

import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.banana.search.core.QuestionNormalizer
import androidx.compose.runtime.mutableFloatStateOf
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.banana.search.R
import com.banana.search.core.BankBatch
import com.banana.search.core.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class Pal(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val line: Color,
    val title: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val track: Color,
    val green: Color,
    val orange: Color,
    val red: Color,
    val helpBg: Color,
    val helpInk: Color
)

internal val LightPal = Pal(
    isDark = false,
    bg = Color(0xFFFDFAF1),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF7F2E4),
    line = Color(0xFFEFE8D7),
    title = Color(0xFF2E2A1F),
    text = Color(0xFF4A4534),
    muted = Color(0xFF8B8471),
    accent = Color(0xFFFFC93C),
    accentInk = Color(0xFF1E1A08),
    accentSoft = Color(0xFFFFF3C4),
    track = Color(0xFFF1EBDA),
    green = Color(0xFF2E9E63),
    orange = Color(0xFFC48111),
    red = Color(0xFFD2453B),
    helpBg = Color(0xFFFFF1BF),
    helpInk = Color(0xFF6E5C12)
)

internal val DarkPal = Pal(
    isDark = true,
    bg = Color(0xFF14110A),
    surface = Color(0xFF1D1A11),
    surfaceSoft = Color(0xFF262115),
    line = Color(0xFF302A1A),
    title = Color(0xFFF2EAD3),
    text = Color(0xFFD8D0BA),
    muted = Color(0xFF9A9280),
    accent = Color(0xFFFFD35C),
    accentInk = Color(0xFF1A1503),
    accentSoft = Color(0xFF4A3E14),
    track = Color(0xFF272216),
    green = Color(0xFF6BD69A),
    orange = Color(0xFFE8A63C),
    red = Color(0xFFF0705F),
    helpBg = Color(0x33FFD35C),
    helpInk = Color(0xFFFFD35C)
)

internal val LocalPal = staticCompositionLocalOf { LightPal }

@Composable
internal fun animatedPal(dark: Boolean): Pal {
    val t = if (dark) DarkPal else LightPal
    @Composable
    fun a(c: Color): Color = animateColorAsState(c, tween(300), label = "pal").value
    return Pal(
        isDark = dark,
        bg = a(t.bg),
        surface = a(t.surface),
        surfaceSoft = a(t.surfaceSoft),
        line = a(t.line),
        title = a(t.title),
        text = a(t.text),
        muted = a(t.muted),
        accent = a(t.accent),
        accentInk = a(t.accentInk),
        accentSoft = a(t.accentSoft),
        track = a(t.track),
        green = a(t.green),
        orange = a(t.orange),
        red = a(t.red),
        helpBg = a(t.helpBg),
        helpInk = a(t.helpInk)
    )
}

@Composable
fun AppUi(
    host: AppHost,
    resumeTick: Int,
    showFirstRun: Boolean = false,
    onAcceptFirstRun: () -> Unit = {}
) {

    if (showFirstRun) {
        CompositionLocalProvider(LocalPal provides animatedPal(host.darkTheme())) {
            MaterialTheme { Surface(Modifier.fillMaxSize(), color = LocalPal.current.bg) {
                FirstRunPage(onAccept = onAcceptFirstRun)
            } }
        }
        return
    }

    var dark by remember { mutableStateOf(host.darkTheme()) }
    var sound by remember { mutableStateOf(host.soundEnabled()) }
    var tab by remember { mutableIntStateOf(host.lastTab()) }

    val pal = animatedPal(dark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            val window = activity?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark

            @Suppress("DEPRECATION")
            window.statusBarColor = (if (dark) DarkPal else LightPal).bg.toArgb()
        }
    }

    CompositionLocalProvider(LocalPal provides pal) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = pal.bg) {
              Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.app_title),
                            modifier = Modifier.weight(1f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = pal.title
                        )
                        SoundToggle(on = sound) {
                            sound = it
                            host.setSoundEnabled(it)
                        }
                        Spacer(Modifier.width(8.dp))
                        ThemeToggle(dark = dark) {
                            dark = it
                            host.setDarkTheme(it)
                        }
                    }

                    TabBar(tab) { index ->
                        tab = index
                        host.setLastTab(index)
                    }

                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> LaunchTab(host, resumeTick)
                            1 -> ParamsTab(host)
                            2 -> AboutTab(host, resumeTick)
                            else -> BankTab(host, resumeTick)
                        }
                    }
                }

                if (host.previewOpen()) {
                    FramePreviewOverlay(
                        widthDp = host.panelWidthDp(),
                        heightDp = host.panelHeightDp()
                    ) { host.setPreviewOpen(false) }
                }
              }
            }
        }

    }
}
