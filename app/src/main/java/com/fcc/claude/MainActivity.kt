package com.fcc.claude

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.composables.icons.lucide.R as LucideR
import com.fcc.claude.vendor.suiss.MessageScroller
import com.fcc.claude.vendor.suiss.canRevealConversation
import com.fcc.claude.vendor.suiss.rememberMessageScrollerState

internal val Backdrop = Color(0xFF101113)
internal val Surface = Color(0xFF1B1C1F)
internal val SurfaceRaised = Color(0xFF25272B)
internal val MainText = Color(0xFFE9E9E7)
internal val MutedText = Color(0xFF999B9F)
internal val Accent = Color(0xFFD5BFA3)
private val ClaudeCommands = listOf(
    "/help" to "Mostrar ajuda e comandos disponíveis",
    "/clear" to "Limpar a conversa atual",
    "/compact" to "Resumir o contexto da conversa",
    "/model" to "Trocar o modelo",
    "/permissions" to "Ver permissões da sessão",
    "/review" to "Revisar alterações do projeto",
    "/init" to "Criar instruções para o projeto",
    "/memory" to "Editar memória do projeto",
    "/doctor" to "Diagnosticar a instalação",
    "/cost" to "Ver uso da sessão",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 17, 19)
        window.navigationBarColor = android.graphics.Color.rgb(16, 17, 19)
        window.decorView.systemUiVisibility = 0
        setContent { FccChat(intent) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

private fun launchOptions(intent: Intent): LaunchOptions {
    val raw = intent.getStringExtra("fcc_args_json") ?: return LaunchOptions()
    val args = runCatching { JSONArray(raw) }.getOrNull() ?: return LaunchOptions()
    var prompt: String? = null
    var resume: String? = null
    var model: String? = null
    var mode: String? = null
    var continueSession = false
    var index = 0
    while (index < args.length()) {
        val arg = args.optString(index++)
        when (arg) {
            "-p", "--print" -> {
                val parts = mutableListOf<String>()
                while (index < args.length() && !args.optString(index).startsWith("-")) {
                    parts += args.optString(index++)
                }
                prompt = parts.joinToString(" ").ifBlank { "" }
            }
            "-c", "--continue" -> continueSession = true
            "--resume", "-r" -> if (index < args.length() && !args.optString(index).startsWith("-")) resume = args.optString(index++)
            "--model", "-m" -> if (index < args.length()) model = args.optString(index++)
            "--permission-mode" -> if (index < args.length()) mode = args.optString(index++)
            "--dangerously-skip-permissions" -> mode = "full_access"
        }
    }
    val normalizedMode = when (mode) {
        "default", "acceptEdits" -> "config"
        "plan", "dontAsk" -> "ask"
        "bypassPermissions" -> "full_access"
        else -> mode
    }
    return LaunchOptions(prompt, continueSession, resume, model, normalizedMode)
}

@Composable
private fun FccChat(launchIntent: Intent) {
    val viewModel: ChatViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val items = viewModel.items
    val runs = viewModel.runs
    val sessions = viewModel.sessions
    val prompts = viewModel.prompts
    val pendingMessages = viewModel.pendingMessages
    var session by viewModel.session
    var detailLoadedForSession by viewModel.detailLoadedForSession
    var draft by viewModel.draft
    val sessionDrafts = viewModel.sessionDrafts
    val pendingOperations = viewModel.pendingOperations
    var failedQueueId by viewModel.failedQueueId
    var detailGeneration by viewModel.detailGeneration
    var sendingSessionId by viewModel.sendingSessionId
    var scrollRequest by viewModel.scrollRequest
    var anchorSessionId by viewModel.anchorSessionId
    var busy by viewModel.busy
    var activeRunId by viewModel.activeRunId
    var connected by viewModel.connected
    var historyGeneration by viewModel.historyGeneration
    val historyIds = viewModel.historyIds
    var historyCursor by viewModel.historyCursor
    var historyLoading by viewModel.historyLoading
    val historyCursors = viewModel.historyCursors
    var historyError by viewModel.historyError
    var error by viewModel.error
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showSettings by remember { mutableStateOf(false) }
    var cwd by remember { mutableStateOf(launchIntent.getStringExtra("fcc_cwd") ?: "/storage/emulated/0") }
    var bootstrap by viewModel.bootstrap
    var selectedModel by remember { mutableStateOf("") }
    var selectedEffort by remember { mutableStateOf("") }
    var showEffortMenu by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("config") }
    val pendingAttachments = viewModel.pendingAttachments
    var pickerSessionId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val targetId = pickerSessionId
            if (targetId != null && targetId == session?.id) {
                val existingCount = pendingAttachments.count { it.sessionId == targetId }
                if (existingCount + uris.size > 5) {
                    error = "É possível anexar até 5 arquivos por mensagem."
                } else {
                    runCatching {
                        uris.map { uri ->
                            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                                ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Anexo"
                            PendingAttachment(targetId, uri, name, context.contentResolver.getType(uri) ?: "application/octet-stream")
                        }
                    }.onSuccess { pendingAttachments.addAll(it) }
                        .onFailure { error = it.message ?: "Não foi possível acessar os arquivos selecionados." }
                }
            }
        }
    }
    var showProjectDialog by remember { mutableStateOf(false) }
    var projectDraft by remember { mutableStateOf(cwd) }
    val launchOptions = remember(launchIntent) { launchOptions(launchIntent) }
    val focus = LocalFocusManager.current


    suspend fun sendMessage(text: String, queued: PendingMessage? = null) {
        val openModelSettings = viewModel.sendMessage(text, queued, cwd, selectedEffort, context.contentResolver)
        if (openModelSettings) {
            selectedModel = session?.model.orEmpty()
            selectedMode = session?.mode ?: "config"
            showSettings = true
        }
    }

    suspend fun refresh() {
        val launchPrompt = launchIntent.getStringExtra("fcc_prompt") ?: launchOptions.prompt
        val promptToSend = viewModel.refresh(cwd, launchOptions, launchPrompt)
        session?.let { selectedEffort = it.reasoningEffort }
        if (!promptToSend.isNullOrBlank()) sendMessage(promptToSend)
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            val activeId = session?.id
            val next = pendingMessages.firstOrNull { it.sessionId == activeId }
            Triple(sendingSessionId != null || busy || prompts.isNotEmpty() || detailLoadedForSession != activeId ||
                (failedQueueId != null && next?.id == failedQueueId), activeId, next)
        }.collect { (blocked, activeId, next) ->
            if (!blocked && activeId != null && next != null) {
                sendMessage(next.text, next)
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        viewModel.startObservers()
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color(0x88000000),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                drawerContainerColor = Surface,
                drawerContentColor = MainText,
                drawerTonalElevation = 0.dp,
            ) {
                ChatNavigationContent(
                    sessions = sessions,
                    activeSessionId = session?.id,
                    onClose = { scope.launch { drawerState.close() } },
                    onSelect = { entry ->
                        scope.launch {
                            viewModel.selectSession(entry)
                            cwd = entry.cwd
                            drawerState.close()
                        }
                    },
                    onDelete = { entry -> scope.launch { viewModel.deleteSession(entry) } },
                    onSettings = {
                        selectedModel = session?.model.orEmpty()
                        selectedMode = session?.mode ?: "config"
                        showSettings = true
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
    Box(
        modifier = Modifier.fillMaxSize().background(Backdrop).statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionChip("", MutedText, icon = LucideR.drawable.lucide_ic_panel_left) {
                scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() }
            }
            Spacer(Modifier.weight(1f))
            ActionChip("FCC", if (connected) Accent else MutedText, icon = LucideR.drawable.lucide_ic_circle) {
                scope.launch { refresh() }
            }
            Spacer(Modifier.width(8.dp))
            if (busy && activeRunId.isNotBlank()) {
                ActionChip("Parar", Color(0xFFE9A28F), icon = LucideR.drawable.lucide_ic_square) {
                    scope.launch { viewModel.stopActiveRun() }
                }
                Spacer(Modifier.width(8.dp))
            }
            ActionChip("", Accent, icon = LucideR.drawable.lucide_ic_plus) {
                scope.launch {
                    runCatching { viewModel.startSession(cwd) }
                        .onSuccess { showSettings = false }
                        .onFailure { error = it.message }
                }
            }
        }

        Text(
            text = session?.cwd ?: cwd,
            color = MutedText,
            fontSize = 12.sp,
            modifier = Modifier.clickable {
                projectDraft = session?.cwd ?: cwd
                showProjectDialog = true
            }.padding(horizontal = 20.dp, vertical = 2.dp),
            maxLines = 1,
        )

        if (showProjectDialog) {
            Dialog(onDismissRequest = { showProjectDialog = false }) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(18.dp)) {
                    Text("Pasta do projeto", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Use um caminho acessível pelo Termux ou procure a pasta.", color = MutedText, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                    BasicTextField(
                        value = projectDraft,
                        onValueChange = { projectDraft = it },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceRaised).padding(12.dp),
                        textStyle = TextStyle(color = MainText, fontSize = 14.sp),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                        ActionChip("Procurar", MainText) {
                            scope.launch {
                                viewModel.chooseProjectFolder(projectDraft)?.let { projectDraft = it }
                            }
                        }
                        ActionChip("Cancelar", MutedText) { showProjectDialog = false }
                        ActionChip("Abrir projeto", Accent) {
                            cwd = projectDraft.trim()
                            showProjectDialog = false
                            scope.launch {
                                runCatching { viewModel.startSession(cwd) }
                                    .onFailure { error = it.message }
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) {
            SettingsPanel(
                bootstrap = bootstrap,
                currentModel = selectedModel.ifEmpty { session?.model ?: bootstrap.optString("default_model") },
                currentMode = selectedMode,
                errorMessage = error,
                onModel = { selectedModel = it },
                onMode = { selectedMode = it },
                onApply = {
                    scope.launch {
                        error = null
                        if (viewModel.applySettings(cwd, selectedModel, selectedMode, selectedEffort)) showSettings = false
                    }
                },
                onClose = { showSettings = false },
            )
        } else {
            val density = LocalDensity.current
            var composerHeight by remember { mutableStateOf(0) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
            if (session != null && detailLoadedForSession != session?.id) {
                ConversationSkeleton()
            } else if (items.isEmpty() && prompts.isEmpty() && !busy && error == null) {
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .padding(start = 30.dp, end = 30.dp, bottom = with(density) { composerHeight.toDp() }),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(72.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF302C29), Color(0xFF211F1E))))
                            .border(1.dp, Accent.copy(alpha = 0.24f), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(LucideR.drawable.lucide_ic_sparkles),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Accent),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.heightIn(min = 24.dp))
                    BasicText(
                        "O que vamos construir?",
                        style = TextStyle(color = MainText, fontSize = 25.sp,
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 32.sp),
                    )
                    Spacer(Modifier.heightIn(min = 10.dp))
                    BasicText(
                        "Peça ajuda para criar, entender ou melhorar seu projeto.",
                        modifier = Modifier.widthIn(max = 320.dp),
                        style = TextStyle(color = MutedText, fontSize = 14.sp,
                            textAlign = TextAlign.Center, lineHeight = 21.sp),
                    )
                }
            } else {
                val scroller = androidx.compose.runtime.key(session?.id) { rememberMessageScrollerState() }
                val entryScrollRequest = remember(session?.id) { scrollRequest }
                LaunchedEffect(session?.id, scroller) {
                    snapshotFlow { Triple(scroller.initiallyPositioned && scroller.isNearStart && scroller.visibleKeys.isNotEmpty(), historyCursor, historyError to detailLoadedForSession) }
                        .collect { (atStart, cursor, status) ->
                            val (failure, loadedId) = status
                            val id = session?.id
                            if (atStart && cursor != null && failure == null && id != null && loadedId == id) {
                                historyLoading = true
                                try { viewModel.loadDetail(id, older = true) }
                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (e: Exception) { if (session?.id == id) historyError = e.message ?: "Falha ao carregar histórico" }
                                finally { historyLoading = false }
                            }
                        }
                }
                val showScrollButton by produceState(false, scroller) {
                    snapshotFlow { scroller.canScrollToEnd to scroller.isScrollInProgress }
                        .collectLatest { (canScroll, scrolling) ->
                            if (scrolling) value = false
                            else {
                                delay(if (canScroll) 240 else 180)
                                value = canScroll
                            }
                        }
                }
                val showWorking = busy && prompts.isEmpty() && items.lastOrNull()?.kind != "text"
                val rows = buildChatRows(items, runs, prompts, showWorking)
                val revealConversation = canRevealConversation(
                    detailLoadedForSession == session?.id,
                    scroller.initiallyPositioned || rows.isEmpty(),
                )
                LaunchedEffect(session?.id, prompts.map { it.id }) {
                    if (prompts.isNotEmpty()) scroller.scrollToMessage("prompt:${prompts.first().id}")
                }
                if (historyLoading) Text("Carregando histórico…", color = MutedText, fontSize = 12.sp)
                if (historyError != null) Text("Não foi possível carregar o histórico. Toque para tentar novamente.",
                    modifier = Modifier.clickable { historyError = null }.padding(12.dp), color = MutedText, fontSize = 12.sp)
                MessageScroller(
                    items = rows,
                    key = { it.id },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    state = scroller,
                    anchorKey = items.lastOrNull { it.kind == "user" }?.id,
                    anchorRequest = currentTurnAnchorRequest(
                        scroller.initiallyPositioned,
                        anchorSessionId,
                        session?.id,
                        scrollRequest,
                        entryScrollRequest,
                    ),
                    contentPadding = PaddingValues(top = 14.dp,
                        bottom = with(density) { composerHeight.toDp() } + 14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                    overlay = {
                        if (!revealConversation) ConversationSkeleton()
                        else androidx.compose.animation.AnimatedVisibility(
                            visible = showScrollButton,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(bottom = with(density) { composerHeight.toDp() } + 8.dp),
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(140)),
                        ) {
                            ActionChip("", Accent,
                                modifier = Modifier.background(SurfaceRaised, CircleShape),
                                icon = LucideR.drawable.lucide_ic_arrow_down) {
                                scope.launch { scroller.scrollToEnd() }
                            }
                        }
                    },
                ) { row ->
                    ChatConversationRow(row, runs[activeRunId]) { prompt, approved, answers ->
                        scope.launch { viewModel.respondPrompt(prompt, approved, answers) }
                    }
                }
            }
            }
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { composerHeight = it.height },
            ) {
            error?.let { message ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0xFF392926)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, color = Color(0xFFF2C6B8), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Tentar", color = Accent, modifier = Modifier.clickable { scope.launch { refresh(); failedQueueId = null } }.padding(6.dp))
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
            viewModel.runMessages.filter { it.sessionId == session?.id }.forEach { message ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(13.dp)).background(Surface).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(when (message.status) {
                            "forwarded" -> "Entregue ao worker (leitura não confirmada)"
                            "sending" -> "Enviando ao run"
                            else -> "Entrega incerta — não envie novamente como novo turno"
                        }, color = Accent, fontSize = 11.sp)
                        Text(message.text, color = MutedText, fontSize = 12.sp, maxLines = 2)
                    }
                    if (sendingSessionId == null && canRetryRunMessage(message, session?.id, activeRunId,
                            bootstrap.optString("epoch"), busy)) {
                        Text("Repetir", color = Accent, modifier = Modifier.clickable {
                            scope.launch { viewModel.retryRunMessage(message) }
                        }.padding(6.dp))
                    }
                }
            }
            }
            val queuedForSession = pendingMessages.filter { it.sessionId == session?.id }
            queuedForSession.takeLast(3).forEach { pending ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(13.dp)).background(Surface).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Na fila", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Text(pending.text, color = MutedText, fontSize = 12.sp, maxLines = 2,
                        modifier = Modifier.weight(1f))
                }
            }
            if (queuedForSession.size > 3) {
                Text("+${queuedForSession.size - 3} mensagens na fila", color = MutedText, fontSize = 11.sp,
                    modifier = Modifier.padding(start = 18.dp, bottom = 3.dp))
            }
            val commandQuery = draft.takeIf { it.startsWith("/") }
                ?.substringBefore(' ')
                ?.lowercase()
            val commandSuggestions = commandQuery?.let { query ->
                ClaudeCommands.filter { it.first.startsWith(query) }
            }.orEmpty()
            if (commandSuggestions.isNotEmpty() && !busy) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Surface).padding(6.dp),
                ) {
                    commandSuggestions.take(4).forEach { (command, description) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                .clickable { draft = "$command " }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(command, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(12.dp))
                            Text(description, color = MutedText, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        }
                    }
                }
            }
            ChatComposer(
                draft = draft,
                onDraftChange = { draft = it },
                busy = busy,
                sending = sendingSessionId != null,
                session = session,
                bootstrap = bootstrap,
                selectedModel = selectedModel,
                selectedEffort = selectedEffort,
                attachments = pendingAttachments,
                effortMenuExpanded = showEffortMenu,
                onEffortMenuExpanded = { showEffortMenu = it },
                onRemoveAttachment = { pendingAttachments.remove(it) },
                onAttach = {
                    scope.launch {
                        runCatching {
                            val target = session ?: viewModel.startSession(cwd, clearDraft = false)
                            pickerSessionId = target.id
                            attachmentPicker.launch(arrayOf("*/*"))
                        }.onFailure { error = it.message ?: "Não foi possível abrir os arquivos." }
                    }
                },
                onEffortSelected = { option ->
                    selectedEffort = option
                    scope.launch { viewModel.updateReasoningEffort(option) }
                },
                onOpenSettings = { showSettings = true },
                onSend = { scope.launch { sendMessage(draft); focus.clearFocus() } },
            )
            }
            }
        }
    }
    }
    }
}

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MainText,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight),
        maxLines = maxLines,
    )
}

