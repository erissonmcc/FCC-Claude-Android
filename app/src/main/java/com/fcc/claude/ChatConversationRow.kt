package com.fcc.claude

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import com.composables.icons.lucide.R as LucideR

@Composable
internal fun ChatConversationRow(
    row: ChatRow,
    activeRun: ChatRun?,
    onRespondPrompt: (PermissionCard, Boolean, JSONObject?) -> Unit,
) {
    val item = row.message
    val prompt = row.prompt
    if (item != null) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (item.kind == "user") Arrangement.End else Arrangement.Start,
        ) {
            MessageCard(item)
        }
    } else if (row.work.isNotEmpty()) {
        val workRun = row.run!!
        val active = workRun.status in setOf("preparing", "running", "stopping")
        var expanded by remember(row.id) { mutableStateOf(active) }
        LaunchedEffect(active) { if (!active) expanded = false }
        val duration = workRun.finishedAt?.let { (it - workRun.createdAt).takeIf { ms -> workRun.createdAt > 0 && ms >= 0 } }
        val seconds = duration?.div(1000)
        val formattedDuration = seconds?.let {
            when {
                it >= 3600 -> "${it / 3600} h ${(it % 3600) / 60} min ${it % 60} s"
                it >= 60 -> "${it / 60} min ${it % 60} s"
                else -> "$it s"
            }
        }
        val label = if (active) "Trabalhando…" else if (formattedDuration != null) "Trabalhou por $formattedDuration" else "Trabalhou"
        val arrowRotation by animateFloatAsState(if (expanded) 90f else 0f, label = "workArrow")
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            if (!active) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = if (expanded) "Expandido" else "Recolhido" }
                .clickable(role = Role.Button) { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = MutedText, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Image(painterResource(LucideR.drawable.lucide_ic_chevron_right), null, Modifier.size(16.dp).rotate(arrowRotation), colorFilter = ColorFilter.tint(MutedText))
            }
            if (!active) Box(Modifier.fillMaxWidth().height(1.dp).background(MutedText.copy(alpha = 0.45f)))
            AnimatedVisibility(visible = active || expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = if (!active && row.work.firstOrNull()?.kind != "tool_call") 8.dp else 0.dp)) {
                    var workIndex = 0
                    while (workIndex < row.work.size) {
                        val workItem = row.work[workIndex]
                        if (workItem.kind == "tool_call") {
                            val start = workIndex
                            while (workIndex < row.work.size && row.work[workIndex].kind == "tool_call") workIndex++
                            val group = row.work.subList(start, workIndex)
                            androidx.compose.runtime.key(workItem.id) {
                                var toolsExpanded by remember { mutableStateOf(false) }
                                val latestTool = group.last()
                                val latestInput = remember(latestTool.text) { runCatching { JSONObject(latestTool.text) }.getOrDefault(JSONObject()) }
                                val isLatest = active && row.work.lastOrNull { it.kind == "tool_call" }?.id == latestTool.id
                                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp)
                                    .semantics { stateDescription = if (toolsExpanded) "Expandido" else "Recolhido" }
                                    .clickable(role = Role.Button) { toolsExpanded = !toolsExpanded }, verticalAlignment = Alignment.CenterVertically) {
                                    Image(painterResource(toolGroupIcon(if (isLatest) listOf(latestTool.title) else group.map { it.title })), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(MutedText))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isLatest) activeToolSummary(latestTool.title, latestInput) else toolGroupSummary(group.map { it.title }), color = MutedText,
                                        fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                    Image(painterResource(if (toolsExpanded) LucideR.drawable.lucide_ic_chevron_down else LucideR.drawable.lucide_ic_chevron_right), null,
                                        Modifier.size(16.dp), colorFilter = ColorFilter.tint(MutedText))
                                }
                                AnimatedVisibility(visible = toolsExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    Column { group.forEach { tool -> androidx.compose.runtime.key(tool.id) { MessageCard(tool) } } }
                                }
                            }
                        } else {
                            androidx.compose.runtime.key(workItem.id) { MessageCard(workItem) }
                            workIndex++
                        }
                    }
                }
            }
        }
    } else if (row.tools.isNotEmpty()) {
        var expanded by remember(row.id) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = if (expanded) "Expandido" else "Recolhido" }
                .clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painterResource(toolGroupIcon(row.tools.map { it.title })), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(MutedText))
                Text(toolGroupSummary(row.tools.map { it.title }), color = MutedText, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Image(painterResource(if (expanded) LucideR.drawable.lucide_ic_chevron_down else LucideR.drawable.lucide_ic_chevron_right), null,
                    Modifier.size(16.dp), colorFilter = ColorFilter.tint(MutedText))
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column { row.tools.forEach { tool -> androidx.compose.runtime.key(tool.id) { MessageCard(tool) } } }
            }
        }
    } else if (prompt != null) {
        if (prompt.tool == "AskUserQuestion") {
            QuestionPrompt(prompt, onAnswer = { onRespondPrompt(prompt, true, it) }, onCancel = { onRespondPrompt(prompt, false, null) })
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xFF29251F)).padding(14.dp)) {
                Text("Claude quer executar ${prompt.tool}", color = Accent, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                ToolActivity(prompt.tool, prompt.input, MainText, MutedText, Accent, SurfaceRaised, expandedByDefault = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip("Permitir", Accent) { onRespondPrompt(prompt, true, null) }
                    ActionChip("Negar", MutedText) { onRespondPrompt(prompt, false, null) }
                }
            }
        }
    } else WorkingIndicator(activeRun)
}
