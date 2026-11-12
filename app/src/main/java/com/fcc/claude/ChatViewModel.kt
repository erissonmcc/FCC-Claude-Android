package com.fcc.claude

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val runMessageStore = RunMessageStore(java.io.File(application.filesDir, "run-messages.dat"))
    private var runMessageStoreError: String? = null
    val items = mutableStateListOf<ChatItem>()
    val runs = mutableStateMapOf<String, ChatRun>()
    val sessions = mutableStateListOf<ChatSession>()
    val prompts = mutableStateListOf<PermissionCard>()
    val pendingMessages = mutableStateListOf<PendingMessage>()
    val runMessages = mutableStateListOf<RunMessage>()
    val pendingAttachments = mutableStateListOf<PendingAttachment>()
    val pendingOperations = mutableStateMapOf<String, PendingOperation>()
    val sessionDrafts = mutableStateMapOf<String, String>()
    var draft = mutableStateOf("")
    var failedQueueId = mutableStateOf<String?>(null)
    var scrollRequest = mutableStateOf(0)
    var anchorSessionId = mutableStateOf<String?>(null)
    var session = mutableStateOf<ChatSession?>(null)
    var detailLoadedForSession = mutableStateOf<String?>(null)
    var sendingSessionId = mutableStateOf<String?>(null)
    var busy = mutableStateOf(false)
    var activeRunId = mutableStateOf("")
    var detailGeneration = mutableIntStateOf(0)
    var historyGeneration = mutableIntStateOf(-1)
    val historyIds = mutableSetOf<String>()
    var historyCursor = mutableStateOf<String?>(null)
    var historyLoading = mutableStateOf(false)
    val historyCursors = mutableSetOf<String>()
    var historyError = mutableStateOf<String?>(null)
    var error = mutableStateOf<String?>(null)
    var bootstrap = mutableStateOf(JSONObject())
    var connected = mutableStateOf(false)
    var feedConnected = mutableStateOf(false)

    private val detailLock = Mutex()
    private val deviceToolsInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val deviceToolsCompleted = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val deviceToolResults = java.util.concurrent.ConcurrentHashMap<String, DeviceToolResult>()
    private val detailSignals = Channel<Unit>(Channel.CONFLATED)
    private var turnGeneration = 0
    private var observersStarted = false
    private var currentCwd = "/storage/emulated/0"
    private var currentLaunchOptions = LaunchOptions()

    init {
        try {
            runMessages.addAll(runMessageStore.load())
        } catch (failure: Exception) {
            runMessageStoreError = failure.message ?: "Registro de mensagens indisponível."
        }
    }

    private fun persistRunMessages(messages: List<RunMessage>): Boolean = try {
        check(runMessageStoreError == null) { runMessageStoreError.orEmpty() }
        runMessageStore.save(messages)
        true
    } catch (failure: Exception) {
        error.value = "Não foi possível salvar as mensagens: ${failure.message}"
        false
    }

    suspend fun refresh(cwd: String, options: LaunchOptions, launchPrompt: String?): String? {
        currentCwd = cwd
        currentLaunchOptions = options
        try {
            bootstrap.value = ChatRepository.get("/admin/api/code/bootstrap?harness=claude")
            val harnesses = bootstrap.value.optJSONArray("harnesses") ?: JSONArray()
            val claudeEnabled = (0 until harnesses.length()).any { harnesses.optJSONObject(it)?.optString("id") == "claude" }
            connected.value = bootstrap.value.optBoolean("available", false) && bootstrap.value.optString("harness") == "claude" && claudeEnabled
            if (!connected.value) {
                error.value = bootstrap.value.optString("message").takeIf(String::isNotBlank)
                    ?: if (!claudeEnabled) "Reinicie o fcc-server no Termux para carregar o suporte ao Claude." else "FCC não está disponível."
                return null
            }
            val response = ChatRepository.get("/admin/api/code/sessions?limit=25")
            val sessionsJson = response.optJSONArray("sessions") ?: JSONArray()
            sessions.clear()
            for (index in 0 until sessionsJson.length()) sessions += sessionsJson.getJSONObject(index).toSession()
            var promptToSend: String? = null
            if (session.value == null) {
                val matching = options.resumeId?.let { id -> sessions.firstOrNull { it.id == id && it.harness == "claude" } }
                    ?: sessions.firstOrNull { it.cwd == cwd && it.harness == "claude" }
                if (matching != null && launchPrompt.isNullOrBlank()) session.value = matching
                else if (!launchPrompt.isNullOrBlank() || (!options.continueSession && sessions.none { it.harness == "claude" && it.cwd == cwd })) {
                    session.value = ChatRepository.createSession(cwd, bootstrap.value, options.model, options.mode)
                }
                if (!launchPrompt.isNullOrBlank()) promptToSend = launchPrompt
            }
            session.value?.let { loadDetail(it.id) }
            error.value = null
            return promptToSend
        } catch (failure: Exception) {
            connected.value = false
            error.value = failure.message ?: "Não foi possível conectar ao FCC em 127.0.0.1:8082."
            return null
        }
    }

    suspend fun selectSession(entry: ChatSession) {
        if (session.value?.id != entry.id) {
            session.value?.id?.let { sessionDrafts[it] = draft.value }
            draft.value = sessionDrafts[entry.id].orEmpty()
            detailGeneration.intValue += 1
            detailLoadedForSession.value = null
            busy.value = false
            activeRunId.value = ""
        }
        session.value = entry
        currentCwd = entry.cwd
        runCatching { loadDetail(entry.id) }
            .onFailure { if (session.value?.id == entry.id) error.value = it.message ?: "Não foi possível carregar a conversa." }
    }

    suspend fun deleteSession(entry: ChatSession) {
        runCatching { ChatRepository.delete("/admin/api/code/sessions/${entry.id}?expected_revision=${entry.revision}") }
            .onSuccess {
                sessions.remove(entry)
                if (session.value?.id == entry.id) {
                    sessionDrafts[entry.id] = draft.value
                    draft.value = ""
                    detailGeneration.intValue += 1
                    session.value = null
                    items.clear()
                    prompts.clear()
                }
            }
            .onFailure { error.value = it.message }
    }

    suspend fun startSession(
        cwd: String,
        model: String? = null,
        mode: String? = null,
        effort: String? = null,
        clearDraft: Boolean = true,
    ): ChatSession {
        val created = ChatRepository.createSession(cwd, bootstrap.value, model, mode, effort)
        session.value?.id?.let { sessionDrafts[it] = draft.value }
        if (clearDraft) draft.value = ""
        detailGeneration.intValue += 1
        session.value = created
        detailLoadedForSession.value = null
        sessions.removeAll { it.id == created.id }
        sessions.add(0, created)
        items.clear()
        prompts.clear()
        busy.value = false
        activeRunId.value = ""
        loadDetail(created.id)
        return created
    }

    suspend fun stopActiveRun() {
        val active = session.value ?: return
        runCatching {
            ChatRepository.post("/admin/api/code/sessions/${active.id}/stop", JSONObject().put("operation_id", activeRunId.value))
        }.onFailure { error.value = it.message }
    }

    suspend fun updateReasoningEffort(option: String) {
        val active = session.value ?: return
        runCatching {
            ChatRepository.patch("/admin/api/code/sessions/${active.id}", JSONObject()
                .put("expected_revision", active.revision).put("reasoning_effort", option))
        }.onSuccess { session.value = it.toSession() }
            .onFailure { error.value = it.message }
    }

    suspend fun applySettings(cwd: String, model: String, mode: String, effort: String): Boolean =
        runCatching {
            val active = session.value
            if (active == null) {
                startSession(cwd, model.ifEmpty { null }, mode, effort.ifEmpty { null }, clearDraft = false)
            } else {
                val changes = JSONObject()
                    .put("expected_revision", active.revision)
                    .put("model", model.ifEmpty { active.model })
                    .put("mode", mode)
                if (effort.isNotEmpty()) changes.put("reasoning_effort", effort)
                ChatRepository.patch("/admin/api/code/sessions/${active.id}", changes)
                loadDetail(active.id)
            }
            true
        }.onFailure { error.value = it.message }
            .getOrDefault(false)

    suspend fun chooseProjectFolder(initialPath: String): String? = runCatching {
        ChatRepository.post("/admin/api/code/folder-picker", JSONObject().put("initial_path", initialPath))
            .optString("path").takeIf(String::isNotBlank)
    }.onFailure { error.value = it.message }.getOrNull()

    fun startObservers() {
        if (observersStarted) return
        observersStarted = true
        viewModelScope.launch {
            for (signal in detailSignals) {
                val id = session.value?.id ?: continue
                runCatching { loadDetail(id) }.onFailure { if (session.value?.id == id) error.value = it.message }
            }
        }
        viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                delay(if (feedConnected.value) 15000 else if (busy.value) 1300 else 6000)
                if (!connected.value) refresh(currentCwd, currentLaunchOptions, null)
                else session.value?.id?.let { id ->
                    runCatching { loadDetail(id) }.onFailure { if (session.value?.id == id) error.value = it.message }
                }
            }
        }
        viewModelScope.launch {
            var needsSnapshot = false
            while (currentCoroutineContext().isActive) {
                if (!connected.value) {
                    feedConnected.value = false
                    delay(1500)
                    continue
                }
                try {
                    ChatRepository.observeEvents { type, data ->
                        when (type) {
                            "feed.ready" -> {
                                feedConnected.value = true
                                if (needsSnapshot || data.optString("epoch") != bootstrap.value.optString("epoch")) {
                                    needsSnapshot = false
                                    viewModelScope.launch { refresh(currentCwd, currentLaunchOptions, null) }
                                } else detailSignals.trySend(Unit)
                            }
                            "feed.resync_required" -> throw IOException("FCC event feed needs resynchronization")
                            else -> if (data.optString("epoch") == bootstrap.value.optString("epoch")) {
                                val sessionId = data.optString("session_id")
                                if (type == "session.deleted") {
                                    sessions.removeAll { it.id == sessionId }
                                    pendingAttachments.removeAll { it.sessionId == sessionId }
                                    sessionDrafts.remove(sessionId)
                                    pendingOperations.remove(sessionId)
                                    if (session.value?.id == sessionId) {
                                        detailGeneration.intValue += 1
                                        draft.value = ""
                                        session.value = null
                                        items.clear()
                                        prompts.clear()
                                        busy.value = false
                                        activeRunId.value = ""
                                    }
                                } else {
                                    data.optJSONObject("session")?.toSession()?.let { updated ->
                                        val index = sessions.indexOfFirst { it.id == updated.id }
                                        if (index >= 0) sessions[index] = updated else sessions.add(0, updated)
                                    }
                                    if (sessionId == session.value?.id) {
                                        if (type == "session.notice") error.value = data.optString("message")
                                        detailSignals.trySend(Unit)
                                    }
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    needsSnapshot = true
                } finally {
                    feedConnected.value = false
                }
                delay(1500)
            }
        }
    }

    suspend fun sendMessage(text: String, queued: PendingMessage?, cwd: String, selectedEffort: String, contentResolver: android.content.ContentResolver, clientMessageId: String? = null): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        if (session.value != null && detailLoadedForSession.value != session.value?.id) {
            error.value = "Aguarde o carregamento da conversa."
            return false
        }
        if (sendingSessionId.value != null) {
            error.value = "Aguarde o envio atual terminar."
            return false
        }
        if (busy.value || prompts.isNotEmpty()) {
            val active = session.value ?: return false
            if (pendingAttachments.any { it.sessionId == active.id }) {
                error.value = "Aguarde o turno atual terminar para enviar anexos."
                return false
            }
            if (active.harness == "claude" && queued == null) {
                val epoch = bootstrap.value.optString("epoch").takeUnless { it.isBlank() || it == "null" }
                if (!useRunMessage(active.harness, busy.value, activeRunId.value, false) || epoch == null) {
                    error.value = "Não foi possível identificar o run ativo. Aguarde a atualização da conversa."
                    return false
                }
                val message = RunMessage(java.util.UUID.randomUUID().toString(), active.id, activeRunId.value, epoch, value)
                if (!persistRunMessages(runMessages.toList() + message)) return false
                runMessages.add(message)
                if (draft.value == text) draft.value = ""
                sendRunMessage(message)
                return false
            }
            pendingMessages += PendingMessage(java.util.UUID.randomUUID().toString(), active.id, value)
            draft.value = ""
            error.value = null
            return false
        }
        if (value.equals("/model", ignoreCase = true)) {
            draft.value = ""
            error.value = null
            return true
        }
        val originalSession = session.value
        val originalGeneration = detailGeneration.intValue
        val creatingMarker = "__fcc_creating_session__"
        var active: ChatSession? = originalSession
        val optimisticId = clientMessageId?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
        val clearedDraft = queued == null && draft.value == text
        turnGeneration += 1
        sendingSessionId.value = originalSession?.id ?: creatingMarker
        activeRunId.value = ""
        busy.value = true
        error.value = null
        scrollRequest.value += 1
        anchorSessionId.value = originalSession?.id
        items += ChatItem(optimisticId, "user", value)
        if (clearedDraft) draft.value = ""
        try {
            val target = originalSession ?: ChatRepository.createSession(cwd, bootstrap.value, effort = selectedEffort.ifEmpty { null }).also {
                active = it
                if (session.value == null && detailGeneration.intValue == originalGeneration) {
                    session.value = it
                    anchorSessionId.value = it.id
                }
                sendingSessionId.value = it.id
            }
            val epoch = bootstrap.value.optString("epoch")
            val selected = if (queued == null) pendingAttachments.filter { it.sessionId == target.id } else emptyList()
            val keys = selected.map { it.key }
            val operation = if (queued != null) PendingOperation(value, emptyList(), queued.id) else {
                pendingOperations[target.id]?.takeIf { it.text == value && it.attachmentKeys == keys }
                    ?: PendingOperation(value, keys, java.util.UUID.randomUUID().toString()).also { pendingOperations[target.id] = it }
            }
            val body = JSONObject().put("operation_id", operation.id)
                .put("expected_revision", target.revision).put("expected_epoch", epoch)
                .put("text", withChartRenderingGuidance(value))
            val ids = JSONArray()
            for (attachment in selected) {
                val id = attachment.uploadedId ?: ChatRepository.uploadAttachment(
                    target.id, attachment.uri, attachment.name, attachment.contentType, contentResolver,
                ).getString("attachment_id").also { uploadedId ->
                    val index = pendingAttachments.indexOfFirst { it.key == attachment.key }
                    if (index >= 0) pendingAttachments[index] = pendingAttachments[index].copy(uploadedId = uploadedId)
                }
                ids.put(id)
            }
            if (ids.length() > 0) body.put("attachment_ids", ids)
            ChatRepository.post("/admin/api/code/sessions/${target.id}/turns", body)
            if (queued != null) {
                pendingMessages.remove(queued)
                failedQueueId.value = null
            } else {
                pendingOperations.remove(target.id)
                pendingAttachments.removeAll { attachment -> selected.any { it.key == attachment.key } }
            }
        } catch (failure: Exception) {
            if (queued != null) failedQueueId.value = queued.id
            val targetId = active?.id
            if (session.value?.id == targetId || (targetId == null && session.value == null)) {
                items.removeAll { it.id == optimisticId }
                error.value = failure.message ?: "Falha ao enviar a mensagem."
                busy.value = false
                if (clearedDraft) draft.value = if (draft.value.isBlank()) text else "$text\n${draft.value}"
            } else if (clearedDraft) {
                val restoreId = originalSession?.id ?: targetId
                if (restoreId != null) {
                    val currentDraft = sessionDrafts[restoreId].orEmpty()
                    sessionDrafts[restoreId] = if (currentDraft.isBlank()) text else "$text\n$currentDraft"
                } else {
                    draft.value = if (draft.value.isBlank()) text else "$text\n${draft.value}"
                    error.value = failure.message ?: "Falha ao enviar a mensagem."
                }
            }
            return false
        } finally {
            sendingSessionId.value = null
        }
        val targetId = active?.id ?: return false
        runCatching { loadDetail(targetId) }.onFailure {
            if (session.value?.id == targetId) error.value = "Mensagem enviada, mas não foi possível atualizar a conversa."
        }
        return false
    }

    suspend fun retryRunMessage(message: RunMessage) {
        if (sendingSessionId.value != null || !canRetryRunMessage(message, session.value?.id, activeRunId.value,
                bootstrap.value.optString("epoch"), busy.value)) return
        sendRunMessage(message)
    }

    private suspend fun sendRunMessage(message: RunMessage) {
        val index = runMessages.indexOfFirst { it.id == message.id }
        if (index < 0 || sendingSessionId.value != null) return
        val sending = message.copy(status = "sending")
        val updatedMessages = runMessages.toMutableList().also { it[index] = sending }
        if (message.status != "sending" && !persistRunMessages(updatedMessages)) return
        runMessages[index] = sending
        sendingSessionId.value = message.sessionId
        try {
            val response = ChatRepository.post(
                "/admin/api/code/sessions/${message.sessionId}/runs/${message.runId}/messages",
                JSONObject().put("operation_id", message.id).put("text", withChartRenderingGuidance(message.text))
                    .put("expected_epoch", message.epoch),
            )
            val status = validatedRunMessageStatus(message.id, message.runId, response.optString("operation_id"),
                response.optString("run_id"), response.optString("status"))
            val current = runMessages.indexOfFirst { it.id == message.id }
            if (current >= 0) {
                val updated = runMessages.toMutableList().also { it[current] = message.copy(status = status) }
                if (persistRunMessages(updated)) runMessages[current] = updated[current]
                else runMessages[current] = message.copy(status = "uncertain")
            }
        } catch (failure: Exception) {
            val current = runMessages.indexOfFirst { it.id == message.id }
            if (current >= 0) {
                val updated = runMessages.toMutableList().also { it[current] = message.copy(status = "uncertain") }
                persistRunMessages(updated)
                runMessages[current] = updated[current]
            }
            error.value = "Não foi possível confirmar a entrega. A mensagem foi preservada; não a reenvie como novo turno."
            if (failure is CancellationException) throw failure
        } finally {
            sendingSessionId.value = null
        }
    }

    suspend fun respondPrompt(prompt: PermissionCard, approved: Boolean, answers: JSONObject? = null) {
        val active = session.value ?: return
        try {
            val answer = JSONObject().put("approved", approved)
            if (answers != null) answer.put("answers", answers)
            ChatRepository.post(
                "/admin/api/code/sessions/${active.id}/prompts/${prompt.id}/responses",
                JSONObject().put("response_id", java.util.UUID.randomUUID().toString()).put("answer", answer),
            )
            loadDetail(active.id)
        } catch (failure: Exception) {
            error.value = failure.message ?: "Não foi possível responder à aprovação."
        }
    }

    private fun dispatchDeviceTool(sessionId: String, prompt: PermissionCard) {
        if (prompt.kind != "device_tool" || prompt.id in deviceToolsCompleted || !deviceToolsInFlight.add(prompt.id)) return
        val sessionCwd = session.value?.cwd.orEmpty()
        val root = if (sessionCwd.startsWith("/storage/")) sessionCwd else ChatRepository.localWorkspace(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = deviceToolResults[prompt.id] ?: DeviceToolExecutor(java.io.File(root), {
                    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()
                }).execute(prompt.tool, prompt.input).also { deviceToolResults[prompt.id] = it }
                ChatRepository.post("/admin/api/code/sessions/$sessionId/prompts/${prompt.id}/responses",
                    JSONObject().put("response_id", java.util.UUID.randomUUID().toString())
                        .put("answer", JSONObject().put("device_tool_result", result.text).put("is_error", result.isError)))
                withContext(Dispatchers.Main.immediate) {
                    deviceToolsCompleted.add(prompt.id)
                    deviceToolResults.remove(prompt.id)
                    loadDetail(sessionId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                withContext(Dispatchers.Main.immediate) { error.value = failure.message ?: "Não foi possível executar a ferramenta no celular." }
            } finally { deviceToolsInFlight.remove(prompt.id) }
        }
    }

    suspend fun loadDetail(id: String, older: Boolean = false) {
        val generation = detailGeneration.intValue
        detailLock.withLock {
            if (session.value?.id != id || generation != detailGeneration.intValue) return@withLock
            val snapshotTurnGeneration = turnGeneration
            val snapshotStartedWhileSending = sendingSessionId.value == id
            val requestedCursor = if (older) historyCursor.value ?: return@withLock else null
            var page = ChatRepository.get(if (requestedCursor == null) "/admin/api/code/sessions/$id" else
                "/admin/api/code/sessions/$id/items?before=${java.net.URLEncoder.encode(requestedCursor, "UTF-8")}")
            val response = page
            if (!older) {
                val unresolved = runMessages.filter { it.sessionId == id && it.status == "uncertain" }
                for (batch in unresolved.chunked(40)) {
                    val query = batch.joinToString("&") { "include_item_ids=${java.net.URLEncoder.encode(it.id, "UTF-8")}" }
                    val receipts = try {
                        ChatRepository.get("/admin/api/code/sessions/$id?$query").optJSONArray("items") ?: JSONArray()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        continue
                    }
                    if (session.value?.id != id || generation != detailGeneration.intValue) return@withLock
                    val updated = runMessages.toMutableList()
                    for (i in 0 until receipts.length()) {
                        val item = receipts.optJSONObject(i) ?: continue
                        val index = updated.indexOfFirst { it.sessionId == id && it.id == item.optString("id") && it.status == "uncertain" }
                        if (index < 0) continue
                        updated[index] = reconcileRunMessage(updated[index], item.optString("id"), item.optString("run_id"),
                            visibleChatText(item.optString("text")), item.optString("kind") == "user" && item.optString("session_id") == id,
                            item.optString("detail"))
                    }
                    if (updated != runMessages.toList() && persistRunMessages(updated)) {
                        runMessages.clear()
                        runMessages.addAll(updated)
                    }
                }
            }
            if (session.value?.id != id || generation != detailGeneration.intValue) return@withLock
            val sessionJson = response.optJSONObject("session") ?: return@withLock
            val initialHistory = historyGeneration.intValue != generation
            val pages = mutableListOf(response)
            val seenCursors = mutableSetOf<String>()
            while (!older && !initialHistory && historyIds.isNotEmpty()) {
                val pageItems = page.optJSONArray("items") ?: JSONArray()
                val activeId = response.optJSONObject("run")?.takeIf {
                    it.optString("status") in setOf("preparing", "running", "stopping")
                }?.optString("id")
                val overlaps = (0 until pageItems.length()).any { index ->
                    val item = pageItems.optJSONObject(index)
                    item != null && item.optString("run_id") != activeId && item.optString("id") in historyIds
                }
                val before = page.optString("next_before").takeIf { it.isNotBlank() && it != "null" }
                if (overlaps || before == null) break
                check(seenCursors.add(before)) { "Cursor de histórico repetido" }
                page = ChatRepository.get("/admin/api/code/sessions/$id/items?before=${java.net.URLEncoder.encode(before, "UTF-8")}")
                if (session.value?.id != id || generation != detailGeneration.intValue) return@withLock
                pages.add(page)
            }
            while (initialHistory && pages.all { historyPage ->
                val pageItems = historyPage.optJSONArray("items") ?: JSONArray()
                (0 until pageItems.length()).none { index ->
                    val value = pageItems.optJSONObject(index)
                    value != null && value.optString("kind") !in setOf("tool_result", "prompt") &&
                        !(value.optString("kind") == "tool_call" && value.optString("title") == "AskUserQuestion")
                }
            }) {
                val before = page.optString("next_before").takeIf { it.isNotBlank() && it != "null" } ?: break
                check(seenCursors.add(before)) { "Cursor de histórico repetido" }
                page = ChatRepository.get("/admin/api/code/sessions/$id/items?before=${java.net.URLEncoder.encode(before, "UTF-8")}")
                if (session.value?.id != id || generation != detailGeneration.intValue) return@withLock
                pages.add(page)
            }
            val ordinals = mutableMapOf<String, Long>()
            for (historyPage in pages) {
                val pageRuns = historyPage.optJSONArray("runs") ?: JSONArray()
                for (index in 0 until pageRuns.length()) {
                    val entry = pageRuns.optJSONObject(index) ?: continue
                    ordinals[entry.optString("id")] = entry.optLong("ordinal")
                }
            }
            val parsed = ArrayList<ChatItem>()
            val receivedIds = mutableSetOf<String>()
            for (historyPage in pages.asReversed()) {
                val detailItems = historyPage.optJSONArray("items") ?: JSONArray()
                for (index in 0 until detailItems.length()) {
                    val value = detailItems.optJSONObject(index) ?: continue
                    receivedIds.add(value.optString("id"))
                    if (value.optString("kind") in setOf("tool_result", "prompt") ||
                        (value.optString("kind") == "tool_call" && value.optString("title") == "AskUserQuestion")) continue
                    parsed += ChatItem(
                        id = value.optString("id", index.toString()),
                        kind = value.optString("kind", "text"),
                        text = if (value.optString("kind") == "user") visibleChatText(value.optString("text")) else value.optString("text"),
                        title = value.optString("title"),
                        complete = value.optBoolean("complete", true),
                        runId = value.optString("run_id", ""),
                        ordinal = ordinals[value.optString("run_id")] ?: Long.MAX_VALUE,
                        sequence = value.optLong("sequence"),
                    )
                }
            }
            val parsedPrompts = mutableListOf<PermissionCard>()
            val promptJson = response.optJSONArray("prompts") ?: JSONArray()
            for (index in 0 until promptJson.length()) {
                val prompt = promptJson.optJSONObject(index) ?: continue
                if (prompt.optString("status") != "pending") continue
                val form = prompt.optJSONObject("form") ?: JSONObject()
                val parsedPrompt = PermissionCard(prompt.optString("id"), form.optString("tool", "Ação do Claude"), form.optJSONObject("input") ?: JSONObject(), prompt.optString("kind", "permission"))
                if (parsedPrompt.kind == "device_tool") dispatchDeviceTool(id, parsedPrompt) else parsedPrompts += parsedPrompt
            }
            val parsedRuns = mutableMapOf<String, ChatRun>()
            for (historyPage in pages.asReversed()) {
                val runList = historyPage.optJSONArray("runs") ?: JSONArray()
                for (index in 0 until runList.length()) {
                    val entry = runList.optJSONObject(index) ?: continue
                    val runId = entry.optString("id")
                    parsedRuns[runId] = ChatRun(runId, entry.optString("status"), entry.optLong("created_at"),
                        if (entry.isNull("finished_at")) null else entry.optLong("finished_at"),
                        if (entry.isNull("consumed_tokens")) null else entry.optLong("consumed_tokens"))
                }
            }
            val updated = sessionJson.toSession()
            val run = response.optJSONObject("run")
            val nextCursor = (if (initialHistory) page else response).optString("next_before")
                .takeIf { it.isNotBlank() && it != "null" }
            if (older && nextCursor != null && (nextCursor == requestedCursor || nextCursor in historyCursors))
                error("Cursor de histórico repetido")
            val existing = if (initialHistory) {
                if (sendingSessionId.value == id) items.filter { it.ordinal == Long.MAX_VALUE } else emptyList()
            } else items.toList()
            val confirmed = parsed.filter { it.kind == "user" && it.id !in historyIds }.map { it.text }.toSet()
            val retained = existing.filterNot { it.ordinal == Long.MAX_VALUE && it.kind == "user" && it.text in confirmed }
            val merged = mergeChatItems(retained, parsed)
            Snapshot.withMutableSnapshot {
                items.clear()
                items.addAll(merged)
                if (initialHistory) {
                    runs.clear()
                    historyIds.clear()
                    historyCursors.clear()
                    historyError.value = null
                }
                if (older) parsedRuns.forEach { (key, value) -> if (key !in runs) runs[key] = value }
                else runs.putAll(parsedRuns)
                historyIds.addAll(receivedIds)
                if (older && requestedCursor != null) historyCursors.add(requestedCursor)
                if (initialHistory || older) historyCursor.value = nextCursor
                historyGeneration.intValue = generation
                if (!older) {
                    prompts.clear()
                    prompts.addAll(parsedPrompts)
                    session.value = updated
                    if (canApplyRunSnapshot(snapshotTurnGeneration, turnGeneration, snapshotStartedWhileSending)) {
                        activeRunId.value = activeRunTarget(run?.optString("id").orEmpty(), run?.optString("status").orEmpty())
                        busy.value = sendingSessionId.value == id || run?.optString("status") in setOf("preparing", "running", "stopping")
                    }
                    detailLoadedForSession.value = id
                }
            }
        }
    }
}
