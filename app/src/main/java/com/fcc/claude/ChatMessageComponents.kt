package com.fcc.claude

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.composables.icons.lucide.R as LucideR
import io.getstream.chat.android.ai.compose.ui.component.AIMessageParts
import io.getstream.chat.android.ai.compose.ui.component.StreamingText

@Composable
internal fun AttachmentThumbnail(attachment: PendingAttachment) {
    val context = LocalContext.current
    val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, attachment.uri) {
        if (attachment.contentType.startsWith("image/") && android.os.Build.VERSION.SDK_INT >= 29) {
            value = withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(attachment.uri, android.util.Size(96, 96), null).asImageBitmap() }.getOrNull() }
        }
    }
    if (thumbnail != null) Image(thumbnail!!, null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)))
    else Image(painterResource(LucideR.drawable.lucide_ic_file), null, colorFilter = ColorFilter.tint(Accent), modifier = Modifier.size(16.dp))
}

@Composable
internal fun ConversationSkeleton() {
    val transition = rememberInfiniteTransition(label = "conversation-skeleton-shimmer")
    val progress by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "conversation-skeleton-progress",
    )
    Row(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        Canvas(Modifier.fillMaxWidth(0.78f).height(96.dp).clip(RoundedCornerShape(20.dp))) {
            val shimmerWidth = size.width * 0.45f
            val x = -shimmerWidth + progress * (size.width + shimmerWidth * 2)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Surface, SurfaceRaised, Surface),
                    start = Offset(x, 0f),
                    end = Offset(x + shimmerWidth, size.height),
                ),
                cornerRadius = CornerRadius(20.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun MessageCard(item: ChatItem) {
    when (item.kind) {
        "tool_call", "reasoning" -> {
            val parts = remember(item) { listOf(item.toStreamMessagePart()) }
            AIMessageParts(parts = parts, modifier = Modifier.fillMaxWidth())
        }
        "user" -> Text(item.text)
        else -> StreamingText(
            text = item.text,
            animate = !item.complete,
        )
    }
}
