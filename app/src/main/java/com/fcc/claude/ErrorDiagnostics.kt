package com.fcc.claude

import java.io.Reader
import java.net.URI
import java.time.Instant

internal const val MAX_ERROR_DIAGNOSTIC_CHARS = 32768
internal const val MAX_ERROR_BODY_CHARS = 8192

internal fun boundedErrorText(text: String): String {
    if (text.length <= MAX_ERROR_DIAGNOSTIC_CHARS) return text
    val suffix = "\n[Diagnóstico truncado]"
    return text.take(MAX_ERROR_DIAGNOSTIC_CHARS - suffix.length) + suffix
}

internal fun readErrorBody(reader: Reader): String {
    val buffer = CharArray(MAX_ERROR_BODY_CHARS + 1)
    var count = 0
    while (count < buffer.size) {
        val read = reader.read(buffer, count, buffer.size - count)
        if (read < 0) break
        count += read
    }
    return String(buffer, 0, minOf(count, MAX_ERROR_BODY_CHARS)) +
        if (count > MAX_ERROR_BODY_CHARS) "\n[Resposta truncada]" else ""
}

internal fun requestErrorEndpoint(baseUrl: String, path: String): String {
    val uri = URI(baseUrl)
    val port = if (uri.port < 0) "" else ":${uri.port}"
    return "${uri.scheme}://${uri.host}$port${uri.path.orEmpty().trimEnd('/')}${path.substringBefore('?')}"
}

internal fun formatDiagnosticError(operation: String, failure: Throwable, context: String? = null): String =
    boundedErrorText(buildString {
        appendLine("Operação: ${operation.take(256)}")
        if (context != null) appendLine(context.take(2048))
        appendLine("Horário: ${Instant.now()}")
        var cause: Throwable? = failure
        repeat(4) {
            val current = cause ?: return@repeat
            appendLine("${current.javaClass.name}: ${current.message.orEmpty().take(MAX_ERROR_BODY_CHARS)}")
            current.stackTrace.take(16).forEach { frame -> appendLine("  at $frame") }
            cause = current.cause
        }
    })
