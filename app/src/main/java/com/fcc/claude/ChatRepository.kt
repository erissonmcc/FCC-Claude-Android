package com.fcc.claude

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

internal object ChatRepository {
    @Volatile private var baseUrl = "http://127.0.0.1:8082"
    @Volatile private var authToken = ""

    fun configure(context: android.content.Context, url: String? = null, token: String? = null) {
        val prefs = context.getSharedPreferences("fcc_backend", android.content.Context.MODE_PRIVATE)
        if (url != null && token != null) prefs.edit().putString("url", url.trimEnd('/')).putString("token", token.trim()).apply()
        baseUrl = prefs.getString("url", "https://fcc-backend-v2-sdui.onrender.com")!!.trimEnd('/')
        authToken = prefs.getString("token", "").orEmpty()
    }

    fun defaultWorkingDirectory(): String =
        if (baseUrl.startsWith("https://")) "/opt/render/project/src/data/workspace" else "/storage/emulated/0"

    fun historyScope(): String = baseUrl + "\n" + authToken

    fun localWorkspace(context: android.content.Context): String = context.getSharedPreferences("fcc_backend", android.content.Context.MODE_PRIVATE)
        .getString("local_workspace", "/storage/emulated/0") ?: "/storage/emulated/0"

    fun configureLocalWorkspace(context: android.content.Context, path: String) {
        val normalized = java.io.File(path.trim()).canonicalPath
        require(normalized.startsWith("/storage/")) { "A pasta local precisa ficar no armazenamento compartilhado do aparelho." }
        context.getSharedPreferences("fcc_backend", android.content.Context.MODE_PRIVATE).edit().putString("local_workspace", normalized).apply()
    }

    fun config(context: android.content.Context) = context.getSharedPreferences("fcc_backend", android.content.Context.MODE_PRIVATE).let {
        org.json.JSONObject().put("url", it.getString("url", "https://fcc-backend-v2-sdui.onrender.com")).put("token", it.getString("token", ""))
            .put("local_workspace", it.getString("local_workspace", "/storage/emulated/0"))
    }

    private fun authorize(connection: java.net.HttpURLConnection) {
        if (authToken.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $authToken")
    }

    suspend fun get(path: String): JSONObject = request("GET", path, null)
    suspend fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    suspend fun uploadAttachment(sessionId: String, uri: Uri, name: String, contentType: String, contentResolver: android.content.ContentResolver): JSONObject = withContext(Dispatchers.IO) {
        val boundary = "fcc-${UUID.randomUUID()}"
        val connection = (java.net.URL("$baseUrl/admin/api/code/sessions/$sessionId/attachments").openConnection() as java.net.HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 3000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.setRequestProperty("Accept", "application/json")
        authorize(connection)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setChunkedStreamingMode(64 * 1024)
        val safeType = contentType.takeIf { it.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")) } ?: "application/octet-stream"
        val safeName = name.filter { it >= ' ' && it != '"' && it != '\\' && it != '/' && it != '\u007f' }.ifBlank { "attachment" }
        try {
            connection.outputStream.buffered().use { output ->
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\nContent-Type: $safeType\r\n\r\n".toByteArray(Charsets.UTF_8))
                val input = contentResolver.openInputStream(uri) ?: throw IOException("Não foi possível ler o arquivo selecionado.")
                input.use { it.copyTo(output, 64 * 1024) }
                output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("FCC ($status): ${runCatching { JSONObject(raw).optString("detail", raw) }.getOrDefault(raw)}")
            JSONObject(raw)
        } finally { connection.disconnect() }
    }

    suspend fun patch(path: String, body: JSONObject): JSONObject = request("PATCH", path, body)
    suspend fun delete(path: String): JSONObject = request("DELETE", path, null)

    suspend fun observeEvents(onEvent: (String, JSONObject) -> Unit) = withContext(Dispatchers.IO) {
        val connection = (java.net.URL("$baseUrl/admin/api/code/events").openConnection() as java.net.HttpURLConnection)
        connection.connectTimeout = 3000
        connection.readTimeout = 30000
        connection.setRequestProperty("Accept", "text/event-stream")
        authorize(connection)
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (connection.responseCode !in 200..299) throw IOException("FCC event feed unavailable")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var event = "message"
                val data = StringBuilder()
                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine() ?: throw IOException("FCC event feed closed")
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) {
                                val payload = JSONObject(data.toString())
                                val eventType = event
                                withContext(Dispatchers.Main) { onEvent(eventType, payload) }
                            }
                            event = "message"
                            data.setLength(0)
                        }
                        line.startsWith("event:") -> event = line.substringAfter(':').trimStart()
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.substringAfter(':').removePrefix(" "))
                        }
                    }
                }
            }
        } finally { connection.disconnect() }
    }

    suspend fun createSession(cwd: String, bootstrap: JSONObject, model: String? = null, mode: String? = null, effort: String? = null): ChatSession {
        val body = JSONObject().put("session_id", UUID.randomUUID().toString()).put("cwd", cwd).put("harness", "claude")
        val created = post("/admin/api/code/sessions", body).toSession()
        if (model == null && mode == null && effort == null) return created
        val availableModels = bootstrap.optJSONArray("models") ?: org.json.JSONArray()
        val modelId = (0 until availableModels.length()).mapNotNull { availableModels.optJSONObject(it) }
            .firstOrNull { option ->
                option.optString("id").equals(model, ignoreCase = true) ||
                    option.optString("display_name").equals(model, ignoreCase = true) ||
                    (model.equals("sonnet", true) && option.optString("display_name").contains("sonnet", true)) ||
                    (model.equals("opus", true) && option.optString("display_name").contains("opus", true)) ||
                    (model.equals("haiku", true) && option.optString("display_name").contains("haiku", true))
            }?.optString("id")
        return patch("/admin/api/code/sessions/${created.id}", JSONObject()
            .put("expected_revision", created.revision)
            .put("model", modelId ?: created.model)
            .put("reasoning_effort", effort ?: JSONObject.NULL)
            .put("mode", mode ?: created.mode)).toSession()
    }

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val connection = (java.net.URL(baseUrl + path).openConnection() as java.net.HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 3000
        connection.readTimeout = 30000
        connection.setRequestProperty("Accept", "application/json")
        authorize(connection)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("FCC ($status): ${runCatching { JSONObject(raw).optString("detail", raw) }.getOrDefault(raw)}")
            JSONObject(raw)
        } finally { connection.disconnect() }
    }
}
