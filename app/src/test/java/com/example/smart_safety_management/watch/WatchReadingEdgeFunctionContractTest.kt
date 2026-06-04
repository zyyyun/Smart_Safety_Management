package com.example.smart_safety_management.watch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WatchReadingEdgeFunctionContractTest {
    private val edgeFunction = File("../supabase/functions/notifications/index.ts")

    @Test
    fun watchReadingResolvesStaleRemovedAndCommsLostAlerts() {
        val src = edgeFunction.readText()
        val watchReadingBlock = src.substringAfter("case \"watch-reading\":")
            .substringBefore("default:")

        assertTrue(watchReadingBlock.contains(".from(\"safety_alerts\")"))
        assertTrue(watchReadingBlock.contains(".update({ resolved_at: nowIso })"))
        assertTrue(watchReadingBlock.contains(".in(\"alert_type\", [\"REMOVED\", \"COMMS_LOST\"])"))
        assertTrue(watchReadingBlock.contains(".is(\"resolved_at\", null)"))
    }
}
