package com.myflowers.app

import com.myflowers.app.data.model.Command
import com.myflowers.app.util.commandStatus
import com.myflowers.app.util.displayTime
import com.myflowers.app.util.parseCommandPayload
import com.myflowers.app.util.payloadSummary
import com.myflowers.app.util.quickCommands
import com.myflowers.app.util.QuickCommand
import com.myflowers.app.util.userMessage
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    @Test
    fun quickCommands_groupByType_takeLatestPayload_sortedByRecentUse() {
        val commands = listOf(
            command(1, "water", "executed").copy(payload = mapOf("amount" to 50)),
            command(2, "set_threshold", "executed").copy(payload = mapOf("level" to 2)),
            command(3, "water", "sent").copy(payload = mapOf("amount" to 100))
        )
        assertEquals(
            listOf(
                QuickCommand("water", mapOf("amount" to 100)),
                QuickCommand("set_threshold", mapOf("level" to 2))
            ),
            quickCommands(commands)
        )
    }

    @Test
    fun quickCommands_allFailedTypesAreNotKeptAsDefaults() {
        val commands = listOf(
            command(1, "reboot", "failed").copy(payload = mapOf("mode" to 1)),
            command(2, "water", "executed"),
            command(3, "water", "failed")
        )
        // water 的最新记录失败 → 回退到成功那条的参数；reboot 全部失败 → 不沉淀。
        assertEquals(listOf(QuickCommand("water", emptyMap())), quickCommands(commands))
    }

    @Test
    fun payloadSummary_emptyAndFormattedValues() {
        assertEquals("无参数", payloadSummary(emptyMap()))
        assertEquals("amount=100", payloadSummary(mapOf("amount" to 100.0)))
        assertEquals("level=2.5, on=true", payloadSummary(mapOf("level" to 2.5, "on" to true)))
    }

    @Test
    fun displayTime_missingValues_showNoRecord() {
        listOf(null, "", "   ").forEach { assertEquals("暂无记录", displayTime(it)) }
    }

    @Test
    fun displayTime_formatsIsoOffsetAndLocalValues_preservesUnparseableText() {
        assertEquals("10-21 09:30", displayTime("2026-10-21T09:30:45Z"))
        assertEquals("10-21 09:30", displayTime("2026-10-21T09:30:45+08:00"))
        assertEquals("10-21 09:30", displayTime("2026-10-21T09:30:45.123"))
        assertEquals("not-a-timestamp", displayTime("not-a-timestamp"))
    }

    @Test
    fun userMessage_ioFailure_mapsToFriendlyNetworkMessage() {
        assertEquals("连接失败，请检查网络后重试。", userMessage(IOException("internal server address")))
    }

    @Test
    fun commandStatus_pendingAndSent_areNotExecutionAcknowledgements() {
        assertEquals("等待执行", commandStatus("pending"))
        assertEquals("已下发", commandStatus("sent"))
        assertEquals("已执行", commandStatus("executed"))
        assertEquals("执行失败", commandStatus("failed"))
    }

    @Test
    fun parseCommandPayload_blankIsEmptyObjectsParseArraysAndScalarsRejected() {
        assertEquals(emptyMap<String, Any>(), parseCommandPayload(""))
        assertEquals(emptyMap<String, Any>(), parseCommandPayload("   "))
        assertEquals(mapOf("level" to 2.0, "on" to true), parseCommandPayload("""{"level": 2, "on": true}"""))
        assertEquals(emptyMap<String, Any>(), parseCommandPayload("{}"))
        assertNull(parseCommandPayload("[1, 2]"))
        assertNull(parseCommandPayload("not-json"))
        assertNull(parseCommandPayload("\"text\""))
    }

    private fun command(id: Int, type: String, status: String) = Command(
        id = id,
        deviceId = 1,
        userId = 7,
        commandType = type,
        payload = emptyMap(),
        status = status,
        createdAt = "2026-10-21T09:30:00Z"
    )
}
