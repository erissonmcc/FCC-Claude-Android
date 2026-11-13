package com.fcc.claude

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TranscriptStoreTest {
    @Test fun reopensSavedHistoryAfterStoreRecreation() {
        val root = Files.createTempDirectory("transcript-test").toFile()
        try {
            TranscriptStore(root).save("account", "session", "mensagem\nçã😀")
            assertEquals("mensagem\nçã😀", TranscriptStore(root).load("account", "session"))
        } finally { root.deleteRecursively() }
    }

    @Test fun separatesBackendAccountsAndConversations() {
        val root = Files.createTempDirectory("transcript-test").toFile()
        try {
            val store = TranscriptStore(root)
            store.save("backend/account-a", "session", "a")
            store.save("backend/account-b", "session", "b")
            assertEquals("a", store.load("backend/account-a", "session"))
            assertEquals("b", store.load("backend/account-b", "session"))
            assertNull(store.load("backend/account-a", "other-session"))
        } finally { root.deleteRecursively() }
    }

    @Test fun replacesHistoryAndDeletesOnlySelectedConversation() {
        val root = Files.createTempDirectory("transcript-test").toFile()
        try {
            val store = TranscriptStore(root)
            store.save("account", "one", "old")
            store.save("account", "two", "other")
            store.save("account", "one", "updated")
            assertEquals("updated", store.load("account", "one"))
            store.remove("account", "one")
            assertNull(store.load("account", "one"))
            assertEquals("other", store.load("account", "two"))
            assertEquals(1, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
}
