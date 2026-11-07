package com.fcc.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModelsTest {
    @Test
    fun concurrentJournalWritesRemainReadable() {
        val dir = java.nio.file.Files.createTempDirectory("run-messages-concurrent").toFile()
        val executor = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val store = RunMessageStore(java.io.File(dir, "operations"))
            val start = java.util.concurrent.CountDownLatch(1)
            val jobs = (1..4).map { worker -> executor.submit {
                start.await()
                repeat(30) {
                    store.save(listOf(RunMessage("op-$worker", "session", "run", "epoch", "text".repeat(1000))))
                    assertEquals(1, store.load().size)
                }
            } }
            start.countDown()
            jobs.forEach { it.get() }
            assertEquals(1, store.load().size)
        } finally { executor.shutdownNow(); dir.deleteRecursively() }
    }

    @Test
    fun pendingRunMessageSurvivesReopeningAndRestartsAsUncertain() {
        val dir = java.nio.file.Files.createTempDirectory("run-messages").toFile()
        try {
            val file = java.io.File(dir, "operations")
            val message = RunMessage("op-1", "session-1", "run-1", "epoch-1", "private text")
            RunMessageStore(file).save(listOf(message))
            assertEquals(listOf(message.copy(status = "uncertain")), RunMessageStore(file).load())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun onlyMatchingReceiptConfirmsRunMessage() {
        val message = RunMessage("op", "session", "run", "epoch", "hello", "uncertain")
        assertEquals("forwarded", reconcileRunMessage(message, "op", "run", "hello", true, "forwarded").status)
        assertEquals("uncertain", reconcileRunMessage(message, "op", "other", "hello", true, "forwarded").status)
        assertEquals("uncertain", reconcileRunMessage(message, "op", "run", "other", true, "forwarded").status)
        assertEquals("uncertain", reconcileRunMessage(message, "op", "run", "hello", false, "forwarded").status)
    }

    @Test
    fun completedRunIsNotAnActiveTargetWhileTurnIsBeingSent() {
        assertEquals("", activeRunTarget("old", "completed"))
        assertEquals("new", activeRunTarget("new", "running"))
        assertFalse(canApplyRunSnapshot(1, 2, false))
        assertFalse(canApplyRunSnapshot(2, 2, true))
        assertTrue(canApplyRunSnapshot(2, 2, false))
    }
    @Test
    fun activeClaudeTextUsesRunMessagesEvenWithPendingApproval() {
        assertTrue(useRunMessage("claude", true, "run-1", false))
        assertFalse(useRunMessage("codex", true, "run-1", false))
        assertFalse(useRunMessage("claude", false, "run-1", false))
        assertFalse(useRunMessage("claude", true, "", false))
        assertFalse(useRunMessage("claude", true, "run-1", true))
    }

    @Test
    fun runMessageRetryKeepsOriginalIdentityAndNeverBecomesTurn() {
        val message = RunMessage("op-1", "session-1", "run-1", "epoch-1", "hello")
        val uncertain = message.copy(status = "uncertain")
        assertEquals(message.id, uncertain.id)
        assertEquals("run-1", uncertain.runId)
        assertEquals("epoch-1", uncertain.epoch)
        assertTrue(canRetryRunMessage(uncertain, "session-1", "run-1", "epoch-1", true))
        assertFalse(canRetryRunMessage(uncertain, "session-1", "run-2", "epoch-1", true))
        assertFalse(canRetryRunMessage(uncertain, "session-1", "run-1", "epoch-2", true))
        assertFalse(canRetryRunMessage(uncertain, "session-1", "run-1", "epoch-1", false))
        assertFalse(canRetryRunMessage(uncertain.copy(status = "forwarded"), "session-1", "run-1", "epoch-1", true))
    }

    @Test
    fun runMessageResponseMustMatchOperationRunAndKnownStatus() {
        assertEquals("forwarded", validatedRunMessageStatus("op", "run", "op", "run", "forwarded"))
        assertEquals("uncertain", validatedRunMessageStatus("op", "run", "op", "run", "uncertain"))
        assertEquals("uncertain", validatedRunMessageStatus("op", "run", "other", "run", "forwarded"))
        assertEquals("uncertain", validatedRunMessageStatus("op", "run", "op", "other", "forwarded"))
        assertEquals("uncertain", validatedRunMessageStatus("op", "run", "op", "run", "unknown"))
    }
    @Test
    fun autoFollowRespectsUserScrollAndResumesAtEnd() {
        assertFalse(com.fcc.claude.vendor.suiss.autoFollowState(true, dragging = true, atEnd = true))
        assertFalse(com.fcc.claude.vendor.suiss.autoFollowState(false, dragging = false, atEnd = false))
        assertTrue(com.fcc.claude.vendor.suiss.autoFollowState(false, dragging = false, atEnd = true))
        assertTrue(com.fcc.claude.vendor.suiss.autoFollowState(true, dragging = false, atEnd = false))
    }

    @Test
    fun conversationLayoutWaitsForVisibleMeasurementsAndStableFrames() {
        assertFalse(com.fcc.claude.vendor.suiss.isInitialLayoutReady(5, 6, true, 2))
        assertFalse(com.fcc.claude.vendor.suiss.isInitialLayoutReady(6, 6, false, 2))
        assertFalse(com.fcc.claude.vendor.suiss.isInitialLayoutReady(6, 6, true, 1))
        assertTrue(com.fcc.claude.vendor.suiss.isInitialLayoutReady(6, 6, true, 2))
    }

    @Test
    fun reopeningConversationDoesNotReplayAnOldTurnAnchor() {
        assertEquals(0, currentTurnAnchorRequest(false, "session-a", "session-a", 4, 4))
        assertEquals(0, currentTurnAnchorRequest(true, "session-a", "session-a", 4, 4))
        assertEquals(5, currentTurnAnchorRequest(true, "session-a", "session-a", 5, 4))
        assertEquals(0, currentTurnAnchorRequest(true, "session-a", "session-b", 5, 4))
    }

    @Test
    fun conversationIsRevealedOnlyAfterDetailAndInitialLayoutAreReady() {
        assertFalse(com.fcc.claude.vendor.suiss.canRevealConversation(false, true))
        assertFalse(com.fcc.claude.vendor.suiss.canRevealConversation(true, false))
        assertTrue(com.fcc.claude.vendor.suiss.canRevealConversation(true, true))
    }

    @Test
    fun rubberBandIsEnabledOnlyWhenListCanScroll() {
        assertFalse(com.fcc.claude.vendor.suiss.shouldUseRubberBand(false, false))
        assertTrue(com.fcc.claude.vendor.suiss.shouldUseRubberBand(true, false))
        assertTrue(com.fcc.claude.vendor.suiss.shouldUseRubberBand(false, true))
    }

    @Test
    fun toolCallChatItemMapsToStreamToolCallWithInputAndStatus() {
        val item = ChatItem("call-1", "tool_call", "{\"path\":\"src/Main.kt\"}", title = "Read")

        val part = item.toStreamMessagePart()

        assertEquals("ai_tool_call", part.kind.rawValue)
        assertEquals("call-1", part.toolCall?.id)
        assertEquals("Read", part.toolCall?.displayTitle)
        assertEquals(item.text, part.toolCall?.summary)
        assertEquals("completed", part.toolCall?.status.toString())
    }

    @Test
    fun reasoningChatItemMapsToStreamReasoningWithCompletionState() {
        val item = ChatItem("reasoning-1", "reasoning", "Checking the request", complete = false)

        val part = item.toStreamMessagePart()

        assertEquals("ai_reasoning", part.kind.rawValue)
        assertEquals("Checking the request", part.reasoning?.preview)
        assertTrue(part.reasoning?.isStreaming == true)
    }

    @Test
    fun streamingToolCallChatItemMapsToRunningStreamStatus() {
        val item = ChatItem("call-2", "tool_call", "{}", title = "Bash", complete = false)

        assertEquals("running", item.toStreamMessagePart().toolCall?.status.toString())
    }

    @Test
    fun mergeChatItemsDeduplicatesByIdAndOrdersByRunAndSequence() {
        val retained = listOf(
            ChatItem("later", "assistant", "old", ordinal = 2, sequence = 2),
            ChatItem("same", "assistant", "stale", ordinal = 2, sequence = 1),
        )
        val incoming = listOf(
            ChatItem("same", "assistant", "updated", ordinal = 1, sequence = 3),
            ChatItem("first", "user", "hello", ordinal = 1, sequence = 1),
        )

        val result = mergeChatItems(retained, incoming)

        assertEquals(listOf("first", "same", "later"), result.map { it.id })
        assertEquals("updated", result[1].text)
    }

    @Test
    fun separatedWorkSegmentsFromTheSameRunHaveUniqueRowKeys() {
        val items = listOf(
            ChatItem("tool-1", "tool_call", "{}", runId = "run-1"),
            ChatItem("tool-2", "tool_call", "{}", runId = "run-2"),
            ChatItem("tool-3", "tool_call", "{}", runId = "run-1"),
        )
        val runs = mapOf(
            "run-1" to ChatRun("run-1", "running", 0, null, null),
            "run-2" to ChatRun("run-2", "running", 0, null, null),
        )

        val rows = buildChatRows(items, runs, emptyList(), showWorking = false)

        assertEquals(listOf("work:run-1:tool-1", "work:run-2:tool-2", "work:run-1:tool-3"), rows.map { it.id })
    }
}
