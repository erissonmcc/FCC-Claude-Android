package com.fcc.claude

import android.net.Uri
import io.getstream.chat.android.ai.compose.parts.AIMessagePart
import org.json.JSONObject
import java.text.Normalizer
import java.util.UUID

private const val CHART_GUIDANCE_MARKER = "\n\n<!-- fcc:chart-guidance:v1 -->\n"
private const val CHART_GUIDANCE = """If the user asks for a chart or graph, render it as a Markdown fenced block marked `chartjs` containing only valid Chart.js v4 configuration JSON (with `type`, `data.labels`, and `data.datasets`). Keep prose outside the block and never use ASCII art for a requested chart. For other requests, respond normally."""
private val chartRequestPattern = Regex("\\b(graficos?|charts?|plots?|diagramas? de dados)\\b")

internal fun withChartRenderingGuidance(text: String): String {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
    return if (chartRequestPattern.containsMatchIn(normalized)) text + CHART_GUIDANCE_MARKER + CHART_GUIDANCE else text
}

internal fun visibleChatText(text: String): String {
    val guidanceSuffix = CHART_GUIDANCE_MARKER + CHART_GUIDANCE
    return if (text.endsWith(guidanceSuffix)) text.removeSuffix(guidanceSuffix).trimEnd() else text
}

internal fun containsChartJsFence(text: String): Boolean = text.contains("```chartjs", ignoreCase = true)

internal fun JSONObject.toSession() = ChatSession(
    id = optString("id"),
    cwd = optString("cwd"),
    title = optString("title", "Nova conversa"),
    revision = optInt("revision", 1),
    status = optString("status", "ready"),
    harness = optString("harness", "codex"),
    model = optString("model", ""),
    reasoningEffort = optString("reasoning_effort", "").takeUnless { it.isBlank() || it == "null" } ?: "",
    mode = optString("mode", "config"),
)

internal data class ChatItem(
    val id: String,
    val kind: String,
    val text: String,
    val title: String = "",
    val complete: Boolean = true,
    val runId: String = "",
    val ordinal: Long = Long.MAX_VALUE,
    val sequence: Long = Long.MAX_VALUE,
)

internal fun ChatItem.toStreamMessagePart(): AIMessagePart = requireNotNull(
    when (kind) {
        "reasoning" -> AIMessagePart.from(
            "ai_reasoning",
            mapOf(
                "id" to id,
                "status" to if (complete) "completed" else "streaming",
                "preview" to text,
            ),
        )
        else -> AIMessagePart.from(
            "ai_tool_call",
            mapOf(
                "id" to id,
                "name" to title.ifBlank { "Ferramenta" },
                "display_title" to title.ifBlank { "Ferramenta" },
                "status" to if (complete) "completed" else "running",
                "summary" to text,
            ),
        )
    },
)

internal fun mergeChatItems(retained: List<ChatItem>, incoming: List<ChatItem>): List<ChatItem> =
    (retained + incoming).associateBy { it.id }.values
        .sortedWith(compareBy<ChatItem> { it.ordinal }.thenBy { it.sequence })

internal fun currentTurnAnchorRequest(
    initiallyPositioned: Boolean,
    anchorSessionId: String?,
    sessionId: String?,
    request: Int,
    entryRequest: Int,
): Int = if (initiallyPositioned && sessionId != null && anchorSessionId == sessionId && request != entryRequest) request else 0

internal data class ChatRun(val id: String, val status: String, val createdAt: Long, val finishedAt: Long?, val consumedTokens: Long?)

internal data class ChatRow(
    val id: String,
    val message: ChatItem? = null,
    val prompt: PermissionCard? = null,
    val tools: List<ChatItem> = emptyList(),
    val work: List<ChatItem> = emptyList(),
    val run: ChatRun? = null,
)

internal data class RunMessage(val id: String, val sessionId: String, val runId: String, val epoch: String, val text: String, val status: String = "sending")

internal fun useRunMessage(harness: String, busy: Boolean, runId: String, hasAttachments: Boolean) =
    harness == "claude" && busy && runId.isNotBlank() && !hasAttachments

internal fun canRetryRunMessage(message: RunMessage, sessionId: String?, runId: String, epoch: String, busy: Boolean) =
    message.status == "uncertain" && busy && message.sessionId == sessionId && message.runId == runId && message.epoch == epoch

internal fun validatedRunMessageStatus(expectedId: String, expectedRun: String, id: String, run: String, status: String): String =
    if (id == expectedId && run == expectedRun && status == "forwarded") "forwarded" else "uncertain"

internal fun canApplyRunSnapshot(startGeneration: Int, currentGeneration: Int, startedWhileSending: Boolean): Boolean =
    startGeneration == currentGeneration && !startedWhileSending

internal fun activeRunTarget(id: String, status: String): String =
    if (status in setOf("preparing", "running", "stopping")) id else ""

internal fun reconcileRunMessage(message: RunMessage, id: String, run: String, text: String, receipt: Boolean, status: String): RunMessage =
    if (receipt && id == message.id && run == message.runId && visibleChatText(text) == message.text && status == "forwarded")
        message.copy(status = "forwarded") else message

internal data class PendingMessage(val id: String, val sessionId: String, val text: String)
internal data class PendingOperation(val text: String, val attachmentKeys: List<String>, val id: String)
internal data class PendingAttachment(val sessionId: String, val uri: Uri, val name: String, val contentType: String, val uploadedId: String? = null, val key: String = UUID.randomUUID().toString())

internal data class ChatSession(
    val id: String,
    val cwd: String,
    val title: String,
    val revision: Int,
    val status: String,
    val harness: String,
    val model: String,
    val reasoningEffort: String,
    val mode: String,
)

internal data class PermissionCard(val id: String, val tool: String, val input: JSONObject)

internal fun buildChatRows(
    items: List<ChatItem>,
    runs: Map<String, ChatRun>,
    prompts: List<PermissionCard>,
    showWorking: Boolean,
): List<ChatRow> = buildList {
    var index = 0
    while (index < items.size) {
        val item = items[index]
        val run = runs[item.runId]
        val end = if (run != null && item.kind != "user") {
            var cursor = index
            while (cursor < items.size && items[cursor].runId == item.runId && items[cursor].kind != "user") cursor++
            cursor
        } else index
        val lastTool = if (end > index) (index until end).lastOrNull { items[it].kind == "tool_call" } else null
        if (run != null && lastTool != null) {
            add(ChatRow("work:${run.id}:${item.id}", work = items.subList(index, lastTool + 1).toList(), run = run))
            index = lastTool + 1
        } else if (item.kind == "tool_call") {
            val start = index
            while (index < items.size && items[index].kind == "tool_call") index++
            add(ChatRow("tools:${item.id}", tools = items.subList(start, index).toList()))
        } else {
            add(ChatRow(item.id, message = item))
            index++
        }
    }
    prompts.forEach { add(ChatRow("prompt:${it.id}", prompt = it)) }
    if (showWorking) add(ChatRow("working"))
}

internal data class QuestionOption(val label: String, val description: String)
internal data class ClaudeQuestion(
    val question: String,
    val header: String,
    val multiSelect: Boolean,
    val options: List<QuestionOption>,
)
internal data class LaunchOptions(
    val prompt: String? = null,
    val continueSession: Boolean = false,
    val resumeId: String? = null,
    val model: String? = null,
    val mode: String? = null,
)
