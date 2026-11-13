package com.fcc.claude

import java.io.IOException
import java.io.StringReader
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class ErrorDiagnosticsTest {
    @Test fun preservesOperationContextAndOriginalCause() {
        val error = IOException("Falha de transporte", SocketTimeoutException("Read timed out"))
        val diagnostic = formatDiagnosticError("PATCH /admin/api/code/sessions/123", error, "HTTP: 504; duração: 30000 ms")
        assertTrue(diagnostic.contains("PATCH /admin/api/code/sessions/123"))
        assertTrue(diagnostic.contains("HTTP: 504"))
        assertTrue(diagnostic.contains("30000 ms"))
        assertTrue(diagnostic.contains("java.net.SocketTimeoutException: Read timed out"))
        assertTrue(diagnostic.contains("ErrorDiagnosticsTest"))
    }

    @Test fun limitsHugeDiagnosticsBeforeSendingToReactNative() {
        val diagnostic = formatDiagnosticError("Salvar configurações", IllegalStateException("x".repeat(2_000_000)))
        assertTrue(diagnostic.length <= MAX_ERROR_DIAGNOSTIC_CHARS)
        assertTrue(diagnostic.contains("Salvar configurações"))
        assertTrue(diagnostic.contains("IllegalStateException"))
    }

    @Test fun readsOnlyBoundedErrorResponse() {
        var readChars = 0
        val reader = object : StringReader("x".repeat(2_000_000)) {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                if (count > 0) readChars += count
                return count
            }
        }
        val response = readErrorBody(reader)
        assertTrue(readChars <= MAX_ERROR_BODY_CHARS + 1)
        assertTrue(response.length < MAX_ERROR_BODY_CHARS + 100)
        assertTrue(response.contains("truncada"))
    }

    @Test fun keepsShortResponseUnchanged() {
        assertEquals("{\"detail\":\"revision conflict\"}", readErrorBody(StringReader("{\"detail\":\"revision conflict\"}")))
    }

    @Test fun endpointDetailsExcludeCredentialsAndQueryParameters() {
        assertEquals("https://example.test/base/sessions/123", requestErrorEndpoint("https://user:password@example.test/base", "/sessions/123?token=private"))
    }

    @Test fun boundsDiagnosticsComingFromOtherErrorSources() {
        val limited = boundedErrorText("x".repeat(2_000_000))
        assertEquals(32768, limited.length)
        assertTrue(limited.endsWith("[Diagnóstico truncado]"))
    }
}
