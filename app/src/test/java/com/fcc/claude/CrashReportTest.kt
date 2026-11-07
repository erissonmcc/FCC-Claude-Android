package com.fcc.claude

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    @Test
    fun crashHandlerIsInstalledOnlyInTheMainProcess() {
        assertTrue(shouldInstallCrashHandler("com.fcc.claude", "com.fcc.claude"))
        org.junit.Assert.assertFalse(shouldInstallCrashHandler("com.fcc.claude:crash", "com.fcc.claude"))
        org.junit.Assert.assertFalse(shouldInstallCrashHandler(null, "com.fcc.claude"))
    }

    @Test
    fun reportContainsThreadExceptionCauseAndCompleteStackTrace() {
        val cause = IllegalArgumentException("inner failure")
        val error = IllegalStateException("outer failure", cause)

        val report = formatCrashReport("main", error)

        assertTrue(report.contains("Thread: main"))
        assertTrue(report.contains("java.lang.IllegalStateException: outer failure"))
        assertTrue(report.contains("Caused by: java.lang.IllegalArgumentException: inner failure"))
        assertTrue(report.contains("CrashReportTest.reportContainsThreadExceptionCauseAndCompleteStackTrace"))
    }
}
