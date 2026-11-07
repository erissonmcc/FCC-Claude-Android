package com.fcc.claude

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import org.json.JSONArray
import org.json.JSONObject

private val toolActions = mapOf(
    "Skill" to "Carregar skill", "Bash" to "Executar comando", "BashOutput" to "Acompanhar comando",
    "Read" to "Ler arquivo", "FileRead" to "Ler arquivo", "Write" to "Gravar arquivo", "FileWrite" to "Gravar arquivo",
    "Edit" to "Editar arquivo", "FileEdit" to "Editar arquivo", "MultiEdit" to "Editar arquivos",
    "Glob" to "Buscar arquivos", "Grep" to "Buscar no conteúdo", "LS" to "Listar arquivos",
    "NotebookEdit" to "Editar notebook", "WebSearch" to "Pesquisar na web", "WebFetch" to "Abrir página",
    "Agent" to "Executar agente", "Task" to "Executar agente", "TaskOutput" to "Acompanhar tarefa",
    "TaskCreate" to "Criar tarefa", "TaskUpdate" to "Atualizar tarefa", "TaskGet" to "Consultar tarefa",
    "TaskList" to "Listar tarefas", "TaskStop" to "Parar tarefa", "KillShell" to "Parar comando",
    "TodoWrite" to "Atualizar lista de tarefas", "EnterPlanMode" to "Entrar no modo de planejamento",
    "ExitPlanMode" to "Concluir planejamento", "EnterWorktree" to "Abrir área de trabalho isolada",
    "ExitWorktree" to "Sair da área de trabalho isolada", "ToolSearch" to "Buscar ferramentas",
    "ListMcpResources" to "Listar recursos MCP", "ListMcpResourcesTool" to "Listar recursos MCP",
    "ReadMcpResource" to "Ler recurso MCP", "ReadMcpResourceTool" to "Ler recurso MCP",
    "ReadMcpResourceDir" to "Listar pasta MCP", "RefreshMcpTools" to "Atualizar ferramentas MCP",
    "SendMessage" to "Enviar mensagem ao agente", "TeamCreate" to "Criar equipe", "TeamDelete" to "Encerrar equipe",
    "CronCreate" to "Agendar tarefa", "CronDelete" to "Remover agendamento", "CronList" to "Listar agendamentos",
    "ScheduleWakeup" to "Programar retomada", "RemoteTrigger" to "Gerenciar acionamento remoto",
    "Workflow" to "Executar fluxo de trabalho", "Monitor" to "Monitorar execução",
    "Artifact" to "Gerenciar artefato", "Projects" to "Gerenciar arquivos do projeto",
    "ClaudeDesign" to "Executar ação de design", "ReportFindings" to "Registrar resultados da revisão",
    "ProposeSkills" to "Propor skills", "ProposeGoal" to "Definir objetivo",
    "PushNotification" to "Enviar notificação", "ReadNotifications" to "Ler notificações",
    "SendFeedback" to "Enviar feedback", "OfferChromeSetup" to "Configurar navegador",
    "ShowOnboardingRolePicker" to "Escolher perfil", "AskUserQuestion" to "Fazer uma pergunta",
)

private val fieldLabels = mapOf(
    "skill" to "Skill", "args" to "Argumentos", "arguments" to "Argumentos", "command" to "Comando",
    "file_path" to "Arquivo", "notebook_path" to "Notebook", "path" to "Pasta", "paths" to "Pastas",
    "pattern" to "Padrão", "glob" to "Arquivos", "query" to "Busca", "url" to "Endereço",
    "description" to "Descrição", "prompt" to "Instruções", "content" to "Conteúdo",
    "old_string" to "Antes", "new_string" to "Depois", "new_source" to "Novo conteúdo", "edits" to "Alterações",
    "replace_all" to "Substituir todas as ocorrências", "offset" to "Início", "limit" to "Limite",
    "pages" to "Páginas", "cell_id" to "Célula", "cell_type" to "Tipo de célula", "edit_mode" to "Operação",
    "output_mode" to "Formato da busca", "head_limit" to "Limite de resultados", "multiline" to "Várias linhas",
    "timeout" to "Tempo limite (ms)", "timeout_ms" to "Tempo limite (ms)", "run_in_background" to "Em segundo plano",
    "server" to "Servidor", "uri" to "Recurso", "tool" to "Ferramenta", "name" to "Nome",
    "task_id" to "Tarefa", "taskId" to "Tarefa", "shell_id" to "Comando", "agent_id" to "Agente",
    "subagent_type" to "Tipo de agente", "team_name" to "Equipe", "recipient" to "Destinatário",
    "subject" to "Tarefa", "status" to "Estado", "todos" to "Tarefas", "activeForm" to "Em andamento",
    "owner" to "Responsável", "model" to "Modelo", "effort" to "Esforço", "mode" to "Modo",
    "action" to "Ação", "operation" to "Operação", "allowed_domains" to "Domínios permitidos",
    "blocked_domains" to "Domínios excluídos", "cron" to "Agendamento", "recurring" to "Recorrente",
    "durable" to "Persistente", "delaySeconds" to "Espera (segundos)", "reason" to "Motivo",
    "plan" to "Plano", "allowedPrompts" to "Ações previstas", "metadata" to "Informações adicionais",
    "addBlocks" to "Bloqueia", "addBlockedBy" to "Depende de", "findings" to "Resultados",
)

private val primaryFields = listOf("skill", "args", "command", "file_path", "notebook_path", "path", "pattern",
    "glob", "query", "url", "subject", "name", "task_id", "taskId", "status", "server", "uri", "recipient",
    "subagent_type", "action", "operation", "cron", "reason")
private val codeFields = setOf("command", "content", "old_string", "new_string", "new_source", "script", "diff", "skillMd")

internal fun toolCategory(tool: String): String = when (tool) {
    "Bash", "BashOutput", "KillShell" -> "comandos"
    "Read", "FileRead", "Write", "FileWrite", "Edit", "FileEdit", "MultiEdit", "NotebookEdit" -> "arquivos"
    "Glob", "Grep", "LS" -> "buscas"
    "WebSearch", "WebFetch" -> "web"
    else -> "ferramentas"
}

internal fun toolGroupIcon(tools: List<String>): Int = when {
    tools.isNotEmpty() && tools.all { it.startsWith("mcp__") } -> LucideR.drawable.lucide_ic_plug
    else -> when (tools.map(::toolCategory).distinct().singleOrNull()) {
    "comandos" -> LucideR.drawable.lucide_ic_terminal
    "arquivos" -> LucideR.drawable.lucide_ic_file
    "buscas", "web" -> LucideR.drawable.lucide_ic_search
    else -> LucideR.drawable.lucide_ic_zap
    }
}

internal fun toolGroupSummary(tools: List<String>): String {
    val categories = tools.map(::toolCategory).distinct().map {
        when (it) {
            "comandos" -> "executou comandos"
            "arquivos" -> "acessou arquivos"
            "buscas" -> "fez buscas"
            "web" -> "acessou a web"
            else -> "usou ferramentas"
        }
    }
    return categories.joinToString(" e ").replaceFirstChar { it.titlecase() }
}

internal fun activeToolSummary(tool: String, input: JSONObject): String {
    val path = input.optString("file_path").ifBlank { input.optString("path") }.substringAfterLast('/')
    return when (tool) {
        "Read", "FileRead" -> if (path.isNotBlank()) "Lendo $path" else "Lendo arquivo"
        "Edit", "FileEdit", "MultiEdit", "NotebookEdit" -> if (path.isNotBlank()) "Editando $path" else "Editando arquivos"
        "Write", "FileWrite" -> if (path.isNotBlank()) "Gravando $path" else "Gravando arquivo"
        "Grep" -> "Buscando no conteúdo"
        "Glob", "LS" -> "Buscando arquivos"
        "Bash" -> input.optString("description").ifBlank { "Executando comando" }
        "WebSearch" -> "Pesquisando na web"
        "WebFetch" -> "Abrindo página"
        else -> toolActions[tool] ?: "Usando ferramenta"
    }
}

@Composable
internal fun ToolActivity(tool: String, input: JSONObject, textColor: Color, mutedColor: Color, accent: Color,
    surface: Color, expandedByDefault: Boolean = false, alignedWithGroup: Boolean = false) {
    val keys = remember(input.toString()) { input.keys().asSequence().filter { !input.isNull(it) }.toList() }
    val description = input.optString("description").takeIf { it.isNotBlank() }
    val title = toolActions[tool] ?: tool.substringAfterLast("__").replace('_', ' ').replaceFirstChar { it.titlecase() }
    val summaryKey = primaryFields.firstOrNull { it in keys && input.opt(it) !is JSONObject && input.opt(it) !is JSONArray }
        ?: keys.firstOrNull { it != "description" && input.opt(it) !is JSONObject && input.opt(it) !is JSONArray }
    val summary = description ?: summaryKey?.let { input.opt(it)?.toString() }
    var expanded by remember(tool, expandedByDefault) { mutableStateOf(expandedByDefault) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp)
                .semantics { stateDescription = if (expanded) "Expandido" else "Recolhido" }
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = if (alignedWithGroup) 0.dp else 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val icon = toolGroupIcon(listOf(tool))
            Image(painterResource(icon), null, Modifier.size(14.dp),
                colorFilter = ColorFilter.tint(mutedColor))
            val line = if (!summary.isNullOrBlank() && !expanded) "$title: ${summary.replace('\n', ' ')}" else title
            BasicText(line, modifier = Modifier.weight(1f),
                style = TextStyle(color = mutedColor, fontSize = 13.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Image(painterResource(if (expanded) LucideR.drawable.lucide_ic_chevron_up else LucideR.drawable.lucide_ic_chevron_down),
                null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(mutedColor))
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(tool, style = TextStyle(color = mutedColor, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth())
                if (description != null) BasicText(description, style = TextStyle(color = mutedColor, fontSize = 13.sp, lineHeight = 19.sp))
                keys.filter { it != "description" }.forEach { key -> ToolField(key, input.opt(key), textColor, mutedColor, surface) }
            }
        }
    }
}

@Composable
private fun ToolField(key: String, value: Any?, textColor: Color, mutedColor: Color, surface: Color) {
    val label = fieldLabels[key] ?: key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replaceFirstChar { it.titlecase() }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        when (value) {
            is JSONObject -> {
                BasicText(label, style = TextStyle(color = mutedColor, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    value.keys().asSequence().filter { !value.isNull(it) }.forEach { child ->
                        ToolField(child, value.opt(child), textColor, mutedColor, surface)
                    }
                }
            }
            is JSONArray -> {
                BasicText("$label (${value.length()})", style = TextStyle(color = mutedColor, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (index in 0 until value.length()) {
                        val entry = value.opt(index)
                        if (key == "todos" && entry is JSONObject) {
                            val status = entry.optString("status")
                            val statusText = when (status) { "completed" -> "Concluída"; "in_progress" -> "Em andamento"; else -> "Pendente" }
                            BasicText("${index + 1}. ${entry.optString("content")} · $statusText",
                                style = TextStyle(color = textColor, fontSize = 13.sp, lineHeight = 19.sp))
                        } else ToolField((index + 1).toString(), entry, textColor, mutedColor, surface)
                    }
                }
            }
            else -> {
                val raw = when (value) { null, JSONObject.NULL -> "—"; true -> "Sim"; false -> "Não"; else -> value.toString() }
                val text = when (key) {
                    "status" -> when (raw) { "completed" -> "Concluída"; "in_progress" -> "Em andamento"; "pending" -> "Pendente"; "deleted" -> "Removida"; else -> raw }
                    else -> raw
                }
                if (key in codeFields || text.contains('\n') || text.length > 240) {
                    BasicText(label, style = TextStyle(color = mutedColor, fontSize = 12.sp))
                    var full by remember(text) { mutableStateOf(false) }
                    SelectionContainer {
                        BasicText(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(surface)
                            .horizontalScroll(rememberScrollState()).padding(10.dp),
                            style = TextStyle(color = when (key) { "old_string" -> Color(0xFFE0A4A4); "new_string" -> Color(0xFFA8C9AB); else -> textColor },
                                fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace),
                            maxLines = if (full) Int.MAX_VALUE else 12, softWrap = false)
                    }
                    if (text.count { it == '\n' } >= 12) BasicText(if (full) "Recolher conteúdo" else "Mostrar conteúdo completo",
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { full = !full }.padding(vertical = 12.dp),
                        style = TextStyle(color = mutedColor, fontSize = 12.sp))
                } else BasicText("$label: $text", style = TextStyle(color = textColor, fontSize = 13.sp, lineHeight = 19.sp))
            }
        }
    }
}
