package com.fcc.claude

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxCommandSpecTest {
    @Test
    fun wrapsCommandsAsOneQuotedBashArgument() {
        val command = "printf '%s' \"a'b c\""

        assertArrayEquals(
            arrayOf("-c", "exec timeout 60s bash -c 'printf '\\''%s'\\'' \"a'\\''b c\"'"),
            TermuxCommandSpec.arguments(command),
        )
    }

    @Test
    fun formatsTermuxStdoutStderrAndExitCode() {
        val result = TermuxCommandSpec.result("hello", "warning", 7, -1, null)

        assertTrue(result.isError)
        assertEquals("hello\nwarning\n(exit 7)", result.text)
    }

    @Test
    fun reportsTermuxServiceErrorsWithoutMarkingThemSuccessful() {
        val result = TermuxCommandSpec.result("", "", 0, 1, "allow-external-apps is disabled")

        assertTrue(result.isError)
        assertTrue(result.text.contains("allow-external-apps is disabled"))
    }

    @Test
    fun preservesSuccessfulEmptyOutput() {
        val result = TermuxCommandSpec.result("", "", 0, -1, null)

        assertFalse(result.isError)
        assertEquals("(exit 0)", result.text)
    }
}
