package com.fcc.claude

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun ActionChip(label: String, color: Color, icon: Int? = null, modifier: Modifier = Modifier, action: () -> Unit) {
    Row(
        modifier = modifier.clip(CircleShape).clickable(onClick = action).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Image(painterResource(icon), null, colorFilter = ColorFilter.tint(color), modifier = Modifier.size(17.dp))
        if (label.isNotBlank()) Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun SettingsPanel(
    bootstrap: JSONObject,
    currentModel: String,
    currentMode: String,
    errorMessage: String?,
    onModel: (String) -> Unit,
    onMode: (String) -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    val models = remember(bootstrap) {
        val entries = bootstrap.optJSONArray("models") ?: JSONArray()
        (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(models, query) {
        models.filter { model ->
            query.isBlank() || model.optString("display_name").contains(query.trim(), ignoreCase = true) ||
                model.optString("id").contains(query.trim(), ignoreCase = true)
        }
    }
    Column(Modifier.fillMaxSize().padding(14.dp).clip(RoundedCornerShape(20.dp)).background(Surface).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ajustes da conversa", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            ActionChip("Fechar", Accent, action = onClose)
        }
        Spacer(Modifier.heightIn(min = 16.dp))
        Text("Modelo", color = MutedText, fontSize = 12.sp)
        BasicTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceRaised).padding(12.dp),
            textStyle = TextStyle(color = MainText, fontSize = 14.sp),
            cursorBrush = SolidColor(Accent),
            decorationBox = { inner -> Box {
                if (query.isEmpty()) Text("Pesquisar modelos", color = MutedText, fontSize = 14.sp)
                inner()
            } },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
            items(filtered) { model ->
                val id = model.optString("id")
                val name = model.optString("display_name", id)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onModel(id) }.padding(horizontal = 10.dp, vertical = 10.dp)) {
                    Text("${if (id == currentModel) "●" else "○"}  $name", color = if (id == currentModel) Accent else MainText, fontSize = 14.sp)
                    if (id != name) Text(id, color = MutedText, fontSize = 11.sp, modifier = Modifier.padding(start = 17.dp, top = 3.dp))
                }
            }
            if (filtered.isEmpty()) item { Text("Nenhum modelo encontrado.", color = MutedText, modifier = Modifier.padding(vertical = 16.dp), fontSize = 14.sp) }
        }
        Text("Permissões", color = MutedText, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf("config" to "Padrão", "ask" to "Perguntar", "full_access" to "Acesso amplo").forEach { (id, label) ->
                ActionChip(if (currentMode == id) "✓ $label" else label, if (currentMode == id) Accent else MainText) { onMode(id) }
            }
        }
        if (!errorMessage.isNullOrBlank()) Text(errorMessage, color = Color(0xFFF2C6B8), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        ActionChip("Salvar ajustes", Accent, modifier = Modifier.padding(top = 8.dp), action = onApply)
    }
}
