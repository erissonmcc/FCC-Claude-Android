package com.fcc.claude

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR

@Composable
internal fun ChatNavigationContent(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    onClose: () -> Unit,
    onSelect: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FCC Claude", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("Seu espaço de trabalho", color = MutedText, fontSize = 12.sp)
            }
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = "Fechar navegação", role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center) {
                Image(painterResource(LucideR.drawable.lucide_ic_panel_left), "Fechar navegação",
                    colorFilter = ColorFilter.tint(MutedText), modifier = Modifier.size(20.dp))
            }
        }
        Text("CONVERSAS", Modifier.padding(start = 12.dp, bottom = 10.dp),
            color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(sessions.filter { it.harness == "claude" }, key = { it.id }) { entry ->
                val active = entry.id == activeSessionId
                var menuOpen by remember(entry.id) { mutableStateOf(false) }
                val title = entry.title.ifBlank { "Nova conversa" }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (active) SurfaceRaised else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicText(
                        text = title,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .semantics { selected = active }
                            .clickable(role = Role.Button) { onSelect(entry) }
                            .padding(start = 12.dp, top = 14.dp, bottom = 14.dp),
                        style = androidx.compose.ui.text.TextStyle(color = MainText, fontSize = 15.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Box {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "Opções de $title", role = Role.Button) { menuOpen = true },
                            contentAlignment = Alignment.Center) {
                            Image(painterResource(LucideR.drawable.lucide_ic_ellipsis), "Opções de $title",
                                colorFilter = ColorFilter.tint(MutedText), modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false },
                            shape = RoundedCornerShape(14.dp), containerColor = SurfaceRaised,
                            tonalElevation = 0.dp) {
                            DropdownMenuItem(text = { Text("Excluir conversa") },
                                leadingIcon = {
                                    Image(painterResource(LucideR.drawable.lucide_ic_trash_2), null,
                                        colorFilter = ColorFilter.tint(MutedText), modifier = Modifier.size(18.dp))
                                }, onClick = { menuOpen = false; onDelete(entry) })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(MutedText.copy(alpha = 0.12f)))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onSettings).padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(LucideR.drawable.lucide_ic_settings), null,
                colorFilter = ColorFilter.tint(MutedText), modifier = Modifier.size(20.dp))
            Text("Configurações", fontSize = 14.sp)
        }
    }
}
