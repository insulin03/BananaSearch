package com.banana.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banana.search.BuildConfig

private val POINTS = listOf(
    "仅供个人学习，禁止用于考试作弊",
    "题库由你自己导入，答案是否准确需要自行判断",
    "识别结果可能出错，使用后果由使用者自行承担"
)

@Composable
internal fun FirstRunPage(onAccept: () -> Unit) {
    val pal = LocalPal.current
    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(pal.accentSoft, pal.bg, pal.bg)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(64.dp))

        Text(
            "🍌 香蕉做题",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = pal.title
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "一个纯本地优先的屏幕做题助手",
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = pal.muted
        )
        Text(
            "题库自己导入，识别在你的手机上完成",
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = pal.muted
        )

        Spacer(Modifier.height(34.dp))
        POINTS.forEachIndexed { index, text ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(pal.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = pal.accentInk
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                    color = pal.text,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton("我已了解，开始使用", modifier = Modifier.fillMaxWidth()) { onAccept() }
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "继续即表示你同意上述说明",
                fontSize = 11.5.sp,
                color = pal.muted
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "项目以 Apache-2.0 开源 · v${BuildConfig.VERSION_NAME}",
                fontSize = 11.5.sp,
                color = pal.muted
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

internal const val DISCLAIMER_REVISION = "1"
