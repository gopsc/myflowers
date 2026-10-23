package com.myflowers.app

import com.myflowers.app.data.model.Command
import com.myflowers.app.util.commandStatus
import com.myflowers.app.util.displayTime
import com.myflowers.app.util.hasPlantCareQuickActions
import com.myflowers.app.util.lastLightRequest
import com.myflowers.app.util.parseCommandPayload
import com.myflowers.app.util.sensorLabel
import com.myflowers.app.util.sensorUnit
import com.myflowers.app.util.userMessage
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    @Test
    fun lastLightRequest_withoutLightCommands_isUnknown() {
        assertNull(lastLightRequest(emptyList()))
        assertNull(lastLightRequest(listOf(command(1, "water", "executed"))))
    }

    @Test
    fun lastLightRequest_ignoresFailedAndUnknownStatusesAndTypes() {
        val ignored = listOf(
            command(90, "turn_off_light", "failed"),
            command(91, "turn_off_light", "unknown"),
            command(92, "turn_on_light", "queued"),
            command(93, "toggle_light", "executed"),
            command(94, "water", "pending")
        )
        assertNull(lastLightRequest(ignored))
        assertEquals(true, lastLightRequest(ignored + command(3, "turn_on_light", "sent")))
    }

    @Test
    fun lastLightRequest_usesLargestId_notListOrderOrTimestamp() {
        val newestId = command(20, "turn_off_light", "pending")
            .copy(createdAt = "2026-01-01T00:00:00Z")
        val lowerId = command(19, "turn_on_light", "executed")
            .copy(createdAt = "2026-10-21T09:30:00Z")
        listOf(
            listOf(newestId, lowerId, command(1, "turn_on_light", "sent")),
            listOf(lowerId, command(1, "turn_on_light", "sent"), newestId)
        ).forEach { assertEquals(false, lastLightRequest(it)) }
        assertEquals(true, lastLightRequest(listOf(newestId, command(21, "turn_on_light", "pending"), lowerId)))
    }

    @Test
    fun lastLightRequest_acceptsPendingSentAndExecuted_asRequests() {
        listOf("pending", "sent", "executed").forEach { status ->
            assertEquals(true, lastLightRequest(listOf(command(1, "turn_on_light", status))))
            assertEquals(false, lastLightRequest(listOf(command(1, "turn_off_light", status))))
        }
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
    fun sensorDisplay_knownKeysHaveLabelsAndUnits() {
        assertEquals("土壤湿度", sensorLabel("soil_moisture"))
        assertEquals("%", sensorUnit("soil_moisture"))
        assertEquals("环境温度", sensorLabel("temperature"))
        assertEquals("°C", sensorUnit("temperature"))
        assertEquals("光照强度", sensorLabel("light"))
        assertEquals("lux", sensorUnit("light"))
    }

    @Test
    fun sensorDisplay_unknownKeyFallsBackToRawKeyAndNoUnit() {
        assertEquals("pm25", sensorLabel("pm25"))
        assertEquals("", sensorUnit("pm25"))
    }

    @Test
    fun plantCareQuickActions_freshDeviceShowsNothingByDefault() {
        assertFalse(hasPlantCareQuickActions(emptySet(), emptyList()))
    }

    @Test
    fun plantCareQuickActions_followsDeviceEvidence() {
        assertTrue(hasPlantCareQuickActions(setOf("soil_moisture", "temperature"), emptyList()))
        assertTrue(hasPlantCareQuickActions(setOf("light"), emptyList()))
        assertTrue(hasPlantCareQuickActions(emptySet(), listOf("water")))
        assertTrue(hasPlantCareQuickActions(setOf("pm25"), listOf("turn_off_light")))
        assertFalse(hasPlantCareQuickActions(setOf("pm25", "co2"), emptyList()))
        assertFalse(hasPlantCareQuickActions(setOf("pm25"), listOf("set_threshold", "reboot")))
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
