package com.fcc.claude

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun WorkingIndicator(run: ChatRun?) {
    var now by remember(run?.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run?.id) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val elapsed = run?.createdAt?.takeIf { it > 0 && now >= it }?.let { (now - it) / 1000 }
    val duration = elapsed?.let { if (it >= 60) "${it / 60}m ${it % 60}s" else "${it}s" }
    val tokens = run?.consumedTokens?.let { if (it >= 1000) String.format(Locale.US, "%.1fk tokens", it / 1000.0) else "$it tokens" }
    val metrics = listOfNotNull(duration, tokens).joinToString(" • ")
    var textWidth by remember { mutableStateOf(0f) }
    val highlightWidth = with(LocalDensity.current) { 56.dp.toPx() }
    val transition = rememberInfiniteTransition(label = "claude-working-shimmer")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "shimmer-progress")
    val x = -highlightWidth + progress * (textWidth + 2f * highlightWidth)
    Row(Modifier.padding(horizontal = 4.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(width = 28.dp, height = 16.dp)) {
            repeat(3) { index ->
                val phase = (progress - index * 0.16f + 2f) % 1f
                val pulse = ((kotlin.math.cos(phase * 2f * kotlin.math.PI).toFloat() + 1f) / 2f)
                drawCircle(Accent.copy(alpha = 0.3f + 0.7f * pulse), radius = 2.5.dp.toPx(), center = Offset(4.dp.toPx() + index * 9.dp.toPx(), size.height / 2))
            }
        }
        androidx.compose.material3.Text("Claude está trabalhando…", onTextLayout = { textWidth = it.size.width.toFloat() }, style = TextStyle(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(MutedText, MainText, MutedText), Offset(x - highlightWidth, 0f), Offset(x + highlightWidth, 0f)), fontSize = 14.sp))
        if (metrics.isNotEmpty()) Text("($metrics)", color = MutedText, fontSize = 14.sp)
    }
}
