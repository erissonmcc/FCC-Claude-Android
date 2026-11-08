package com.fcc.claude

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.provider.OpenableColumns
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.content.res.AppCompatResources
import com.facebook.react.uimanager.UIManagerHelper
import androidx.lifecycle.ViewModelProvider
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import org.json.JSONArray
import org.json.JSONObject

class FccChatModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context), ActivityEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var model: ChatViewModel? = null
    private var eventCollectionStarted = false
    private var pickerSessionId: String? = null

    init {
        context.addActivityEventListener(this)
    }

    override fun getName() = "FccChat"

    @ReactMethod
    fun addListener(eventName: String) = Unit

    @ReactMethod
    fun removeListeners(count: Int) = Unit

    private fun currentModel(): ChatViewModel {
        model?.let { return it }
        val activity = context.currentActivity as? MainActivity
            ?: error("A tela principal do FCC não está disponível.")
        return ViewModelProvider(activity)[ChatViewModel::class.java].also { model = it }
    }

    private fun emitState(value: String) {
        if (!context.hasActiveCatalystInstance()) return
        context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("fccStateChanged", value)
    }

    @ReactMethod
    fun initialize(promise: Promise) {
        val activity = context.currentActivity as? MainActivity
        val viewModel = runCatching { currentModel() }.getOrElse { promise.reject("FCC_INIT", it); return }
        scope.launch {
            try {
                val cwd = activity?.launchCwd ?: "/storage/emulated/0"
                val options = activity?.launchOptions ?: LaunchOptions()
                val launchPrompt = activity?.launchPrompt
                val prompt = viewModel.refresh(cwd, options, launchPrompt)
                viewModel.startObservers()
                if (!prompt.isNullOrBlank()) {
                    viewModel.sendMessage(prompt, null, cwd, viewModel.session.value?.reasoningEffort.orEmpty(), context.contentResolver)
                }
                if (!eventCollectionStarted) {
                    eventCollectionStarted = true
                    scope.launch {
                        snapshotFlow { stateJson(viewModel).toString() }.distinctUntilChanged().collect(::emitState)
                    }
                }
                val snapshot = stateJson(viewModel).toString()
                promise.resolve(snapshot)
                emitState(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                promise.reject("FCC_INIT", failure)
            }
        }
    }

    @ReactMethod
    fun refresh(promise: Promise) = runWithModel(promise) { vm ->
        val activity = context.currentActivity as? MainActivity
        vm.refresh(activity?.launchCwd ?: vm.session.value?.cwd ?: "/storage/emulated/0", activity?.launchOptions ?: LaunchOptions(), null)
        null
    }

    @ReactMethod
    fun setDraft(text: String) {
        runCatching { currentModel().draft.value = text }
    }

    @ReactMethod
    fun sendMessage(text: String, promise: Promise) = runWithModel(promise) { vm ->
        val cwd = vm.session.value?.cwd ?: (context.currentActivity as? MainActivity)?.launchCwd ?: "/storage/emulated/0"
        vm.sendMessage(text, null, cwd, vm.session.value?.reasoningEffort.orEmpty(), context.contentResolver)
    }

    @ReactMethod
    fun stop(promise: Promise) = runWithModel(promise) { it.stopActiveRun(); null }

    @ReactMethod
    fun newSession(cwd: String, modelName: String, mode: String, effort: String, promise: Promise) =
        runWithModel(promise) { vm -> vm.startSession(cwd, modelName.ifBlank { null }, mode, effort.ifBlank { null }); null }

    @ReactMethod
    fun selectSession(id: String, promise: Promise) = runWithModel(promise) { vm ->
        vm.sessions.firstOrNull { it.id == id }?.let { vm.selectSession(it) }
        null
    }

    @ReactMethod
    fun deleteSession(id: String, promise: Promise) = runWithModel(promise) { vm ->
        vm.sessions.firstOrNull { it.id == id }?.let { vm.deleteSession(it) }
        null
    }

    @ReactMethod
    fun showSessionMenu(id: String, viewTag: Double, promise: Promise) {
        scope.launch {
            try {
                val activity = context.currentActivity ?: error("A tela principal não está disponível.")
                val tag = viewTag.toInt()
                val anchor = UIManagerHelper.getUIManagerForReactTag(context, tag)?.resolveView(tag)
                    ?: error("O botão da conversa não está disponível.")
                val vm = currentModel()
                val session = vm.sessions.firstOrNull { it.id == id } ?: run { promise.resolve(null); return@launch }
                val red = Color.rgb(207, 153, 153)
                val label = SpannableString("Excluir conversa").apply {
                    setSpan(ForegroundColorSpan(red), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                PopupMenu(activity, anchor).apply {
                    menu.add(label).icon = AppCompatResources.getDrawable(activity, R.drawable.ic_delete)?.mutate()?.apply { setTint(red) }
                    setForceShowIcon(true)
                    setOnMenuItemClickListener {
                        AlertDialog.Builder(activity)
                            .setTitle("Excluir conversa?")
                            .setMessage("A conversa “${session.title.ifBlank { "Nova conversa" }}” será excluída. Esta ação não pode ser desfeita.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Excluir") { _, _ -> scope.launch { vm.deleteSession(session) } }
                            .create().apply {
                                setOnShowListener { getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(red) }
                                show()
                            }
                        true
                    }
                    show()
                }
                promise.resolve(null)
            } catch (failure: Exception) {
                promise.reject("FCC_SESSION_MENU", failure)
            }
        }
    }

    @ReactMethod
    fun applySettings(cwd: String, modelName: String, mode: String, effort: String, promise: Promise) =
        runWithModel(promise) { vm -> vm.applySettings(cwd, modelName, mode, effort); null }

    @ReactMethod
    fun chooseProjectFolder(path: String, promise: Promise) = runWithModel(promise) { it.chooseProjectFolder(path) }

    @ReactMethod
    fun respondPrompt(promptId: String, approved: Boolean, answersJson: String?, promise: Promise) =
        runWithModel(promise) { vm ->
            vm.prompts.firstOrNull { it.id == promptId }?.let { prompt ->
                val answers = answersJson?.takeIf(String::isNotBlank)?.let(::JSONObject)
                vm.respondPrompt(prompt, approved, answers)
            }
            null
        }

    @ReactMethod
    fun loadOlder(promise: Promise) = runWithModel(promise) { vm ->
        vm.session.value?.id?.let { vm.loadDetail(it, older = true) }
        null
    }

    @ReactMethod
    fun removeAttachment(key: String, promise: Promise) = runWithModel(promise) { vm ->
        vm.pendingAttachments.removeAll { it.key == key }
        null
    }

    @ReactMethod
    fun pickAttachments(promise: Promise) {
        val activity = context.currentActivity ?: run { promise.reject("FCC_PICKER", "A tela principal do FCC não está disponível."); return }
        val vm = runCatching { currentModel() }.getOrElse { promise.reject("FCC_PICKER", it); return }
        pickerSessionId = vm.session.value?.id
        if (pickerSessionId == null) {
            promise.reject("FCC_PICKER", "Crie ou selecione uma conversa antes de anexar arquivos.")
            return
        }
        pendingPickerPromise = promise
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        activity.startActivityForResult(intent, PICKER_REQUEST)
    }

    private var pendingPickerPromise: Promise? = null

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != PICKER_REQUEST) return
        val promise = pendingPickerPromise.also { pendingPickerPromise = null }
        if (resultCode != Activity.RESULT_OK || data == null) {
            promise?.resolve(null)
            return
        }
        runCatching {
            val vm = currentModel()
            val sessionId = pickerSessionId ?: return@runCatching
            val uris = buildList {
                data.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) add(clip.getItemAt(index).uri)
                } ?: data.data?.let(::add)
            }.distinct()
            require(vm.pendingAttachments.count { it.sessionId == sessionId } + uris.size <= 5) {
                "É possível anexar até 5 arquivos por mensagem."
            }
            for (uri in uris) {
                runCatching { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val name = activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { it.getFirstColumnString() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Anexo"
                vm.pendingAttachments.add(PendingAttachment(sessionId, uri, name,
                    activity.contentResolver.getType(uri) ?: "application/octet-stream"))
            }
            promise?.resolve(null)
        }.onFailure { promise?.reject("FCC_PICKER", it) }
    }

    override fun onNewIntent(intent: Intent) = Unit

    override fun onCatalystInstanceDestroy() {
        context.removeActivityEventListener(this)
        scope.cancel()
        super.onCatalystInstanceDestroy()
    }

    private fun Cursor.getFirstColumnString(): String? = if (moveToFirst()) getString(0) else null

    private fun runWithModel(promise: Promise, block: suspend (ChatViewModel) -> Any?) {
        val vm = runCatching { currentModel() }.getOrElse { promise.reject("FCC_ACTION", it); return }
        scope.launch {
            try {
                val result = block(vm)
                promise.resolve(result)
                emitState(stateJson(vm).toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                promise.reject("FCC_ACTION", failure)
            }
        }
    }

    private fun stateJson(vm: ChatViewModel): JSONObject = JSONObject()
        .put("items", JSONArray().apply { vm.items.forEach { item ->
            put(JSONObject().put("id", item.id).put("kind", item.kind).put("text", item.text)
                .put("title", item.title).put("complete", item.complete).put("runId", item.runId)
                .put("ordinal", item.ordinal).put("sequence", item.sequence))
        } })
        .put("runs", JSONObject().apply { vm.runs.forEach { (id, run) ->
            put(id, JSONObject().put("id", run.id).put("status", run.status).put("createdAt", run.createdAt)
                .put("finishedAt", run.finishedAt ?: JSONObject.NULL).put("consumedTokens", run.consumedTokens ?: JSONObject.NULL))
        } })
        .put("sessions", JSONArray().apply { vm.sessions.forEach { entry -> put(sessionJson(entry)) } })
        .put("session", vm.session.value?.let(::sessionJson) ?: JSONObject.NULL)
        .put("prompts", JSONArray().apply { vm.prompts.forEach { prompt ->
            put(JSONObject().put("id", prompt.id).put("tool", prompt.tool).put("input", prompt.input))
        } })
        .put("pendingMessages", JSONArray().apply { vm.pendingMessages.forEach { msg ->
            put(JSONObject().put("id", msg.id).put("sessionId", msg.sessionId).put("text", msg.text))
        } })
        .put("runMessages", JSONArray().apply { vm.runMessages.forEach { msg ->
            put(JSONObject().put("id", msg.id).put("sessionId", msg.sessionId).put("runId", msg.runId)
                .put("text", msg.text).put("status", msg.status))
        } })
        .put("attachments", JSONArray().apply { vm.pendingAttachments.forEach { attachment ->
            put(JSONObject().put("key", attachment.key).put("sessionId", attachment.sessionId)
                .put("name", attachment.name).put("contentType", attachment.contentType).put("uploaded", attachment.uploadedId != null))
        } })
        .put("busy", vm.busy.value).put("sending", vm.sendingSessionId.value != null)
        .put("activeRunId", vm.activeRunId.value).put("connected", vm.connected.value)
        .put("feedConnected", vm.feedConnected.value).put("draft", vm.draft.value)
        .put("error", vm.error.value ?: JSONObject.NULL).put("historyError", vm.historyError.value ?: JSONObject.NULL)
        .put("historyCursor", vm.historyCursor.value ?: JSONObject.NULL)
        .put("historyLoading", vm.historyLoading.value).put("detailLoaded", vm.detailLoadedForSession.value == vm.session.value?.id)
        .put("bootstrap", vm.bootstrap.value)

    private fun sessionJson(session: ChatSession) = JSONObject()
        .put("id", session.id).put("cwd", session.cwd).put("title", session.title)
        .put("revision", session.revision).put("status", session.status).put("harness", session.harness)
        .put("model", session.model).put("reasoningEffort", session.reasoningEffort).put("mode", session.mode)

    companion object {
        private const val PICKER_REQUEST = 49031
    }
}
