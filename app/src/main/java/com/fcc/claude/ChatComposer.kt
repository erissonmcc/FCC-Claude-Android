package com.fcc.claude

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DropdownMenu
import org.json.JSONObject
import com.composables.icons.lucide.R as LucideR
import kotlin.math.roundToInt

@Composable
internal fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    busy: Boolean,
    sending: Boolean,
    session: ChatSession?,
    bootstrap: JSONObject,
    selectedModel: String,
    selectedEffort: String,
    attachments: List<PendingAttachment>,
    effortMenuExpanded: Boolean,
    onEffortMenuExpanded: (Boolean) -> Unit,
    onRemoveAttachment: (PendingAttachment) -> Unit,
    onAttach: () -> Unit,
    onEffortSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSend: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val visibleAttachments = attachments.filter { it.sessionId == session?.id }
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(28.dp)).background(SurfaceRaised)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
            .padding(start = 10.dp, end = 9.dp, top = 10.dp, bottom = 9.dp),
    ) {
        if (visibleAttachments.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                visibleAttachments.forEach { attachment ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Surface).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AttachmentThumbnail(attachment)
                        Text(attachment.name, color = MainText, fontSize = 12.sp, maxLines = 1,
                            modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Box(Modifier.size(40.dp).clickable(enabled = !sending) { onRemoveAttachment(attachment) }, contentAlignment = Alignment.Center) {
                            Image(painterResource(LucideR.drawable.lucide_ic_x), "Remover ${attachment.name}",
                                colorFilter = ColorFilter.tint(MutedText), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f).heightIn(min = 34.dp, max = 145.dp).padding(start = 8.dp, top = 7.dp, bottom = 5.dp),
                textStyle = TextStyle(color = MainText, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) Text("Pergunte qualquer coisa", color = MutedText, fontSize = 15.sp)
                        inner()
                    }
                },
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
            )
        }
        val modelId = selectedModel.ifEmpty { session?.model ?: bootstrap.optString("default_model") }
        val models = bootstrap.optJSONArray("models")
        val model = (0 until (models?.length() ?: 0)).mapNotNull { models?.optJSONObject(it) }
            .firstOrNull { it.optString("id") == modelId }
        val effortValues = model?.optJSONArray("reasoning_efforts")
        val efforts = effortValues?.let { values -> (0 until values.length()).map { values.optString(it) } }.orEmpty()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).clickable(enabled = !sending, onClick = onAttach), contentAlignment = Alignment.Center) {
                Image(painterResource(LucideR.drawable.lucide_ic_plus), "Anexar arquivos",
                    colorFilter = ColorFilter.tint(MutedText), modifier = Modifier.size(24.dp))
            }
            if (efforts.isNotEmpty()) {
                val savedEffort = selectedEffort.ifEmpty { session?.reasoningEffort.orEmpty() }
                val effort = savedEffort.takeIf { it in efforts }
                    ?: model?.optString("default_reasoning_effort")?.takeIf { it in efforts }
                    ?: efforts.first()
                val modelName = modelLabel(model, modelId)
                val selectedIndex = efforts.indexOf(effort).coerceAtLeast(0)
                var previewIndex by remember(modelId, effort) { mutableIntStateOf(selectedIndex) }
                val previewEffortLabel = effortLabel(efforts[previewIndex.coerceIn(0, efforts.lastIndex)])
                Spacer(Modifier.weight(1f))
                Box {
                    Row(Modifier.clip(CircleShape).clickable { previewIndex = selectedIndex; onEffortMenuExpanded(true) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(modelName, color = MainText, fontSize = 13.sp, maxLines = 1)
                        Text(previewEffortLabel, color = MutedText, fontSize = 13.sp)
                        Image(painterResource(LucideR.drawable.lucide_ic_chevron_down), null,
                            colorFilter = ColorFilter.tint(MutedText), modifier = Modifier.size(14.dp))
                    }
                    DropdownMenu(
                        expanded = effortMenuExpanded,
                        onDismissRequest = { onEffortMenuExpanded(false) },
                        modifier = Modifier.widthIn(min = 270.dp),
                        shape = RoundedCornerShape(26.dp), containerColor = Surface, tonalElevation = 0.dp,
                        shadowElevation = 12.dp, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                onEffortMenuExpanded(false); onOpenSettings()
                            }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(LucideR.drawable.lucide_ic_zap), null,
                                    colorFilter = ColorFilter.tint(Accent), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(modelName, color = MainText, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(previewEffortLabel, color = MutedText, fontSize = 13.sp)
                                Spacer(Modifier.width(5.dp))
                                Image(painterResource(LucideR.drawable.lucide_ic_chevron_right), null,
                                    colorFilter = ColorFilter.tint(MutedText), modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.heightIn(min = 12.dp))
                            Canvas(Modifier.fillMaxWidth().heightIn(min = 52.dp).pointerInput(efforts) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val inset = 16.dp.toPx()
                                    fun indexAt(x: Float): Int {
                                        val range = (size.width - inset * 2).coerceAtLeast(1f)
                                        return ((x - inset) / range * efforts.lastIndex).roundToInt().coerceIn(0, efforts.lastIndex)
                                    }
                                    previewIndex = indexAt(down.position.x)
                                    drag(down.id) { change -> previewIndex = indexAt(change.position.x); change.consume() }
                                    onEffortMenuExpanded(false)
                                    onEffortSelected(efforts[previewIndex])
                                }
                            }) {
                                val thumbRadius = 16.dp.toPx()
                                val centerY = size.height / 2f
                                val endX = (size.width - thumbRadius).coerceAtLeast(thumbRadius)
                                val step = if (efforts.size <= 1) 0f else (endX - thumbRadius) / (efforts.size - 1)
                                val thumbX = thumbRadius + step * previewIndex
                                drawLine(Color(0xFF414348), Offset(thumbRadius, centerY), Offset(endX, centerY), strokeWidth = 22.dp.toPx(), cap = StrokeCap.Round)
                                if (previewIndex > 0) drawLine(Accent.copy(alpha = 0.85f), Offset(thumbRadius, centerY), Offset(thumbX, centerY), strokeWidth = 22.dp.toPx(), cap = StrokeCap.Round)
                                efforts.indices.filter { it != previewIndex }.forEach { index ->
                                    drawCircle(Color(0xFF85878B), radius = 3.5.dp.toPx(), center = Offset(thumbRadius + step * index, centerY))
                                }
                                drawCircle(MainText, radius = thumbRadius, center = Offset(thumbX, centerY))
                            }
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
            } else Spacer(Modifier.weight(1f))
            Box(Modifier.size(38.dp).clip(CircleShape).background(if (draft.isBlank()) Color(0xFF4A4B4E) else Accent)
                .clickable(enabled = draft.isNotBlank()) { onSend(); focus.clearFocus() }, contentAlignment = Alignment.Center) {
                Image(painterResource(LucideR.drawable.lucide_ic_arrow_up),
                    contentDescription = if (busy && session?.harness != "claude") "Adicionar mensagem à fila" else "Enviar mensagem",
                    colorFilter = ColorFilter.tint(if (draft.isBlank()) MutedText else Backdrop), modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun effortLabel(effort: String) = when (effort.lowercase()) {
    "minimal" -> "Mínimo"
    "low" -> "Leve"
    "medium" -> "Médio"
    "high" -> "Alto"
    "xhigh" -> "Muito alto"
    "max" -> "Máximo"
    else -> effort
}

private fun modelLabel(model: JSONObject?, modelId: String): String {
    val displayName = model?.optString("display_name")?.takeIf { it.isNotBlank() && it != "null" && it != modelId }
    if (displayName != null) return displayName
    val parts = modelId.substringAfterLast('/').split(Regex("[-_/]+"))
    if (parts.firstOrNull().equals("gpt", ignoreCase = true) && parts.size > 1)
        return "GPT-${parts[1]}" + parts.drop(2).joinToString(separator = "") { " ${it.replaceFirstChar(Char::uppercase)}" }
    return parts.joinToString(" ") { if (it.equals("gpt", ignoreCase = true)) "GPT" else it.replaceFirstChar(Char::uppercase) }
}
