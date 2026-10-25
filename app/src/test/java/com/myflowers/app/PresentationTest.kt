package com.myflowers.app

import com.myflowers.app.data.model.Command
import com.myflowers.app.data.model.DataPoint
import com.myflowers.app.util.HistoryChartPoint
import com.myflowers.app.util.HistoryChartSeries
import com.myflowers.app.util.chartFraction
import com.myflowers.app.util.chartNumber
import com.myflowers.app.util.commandStatus
import com.myflowers.app.util.commandTimedOut
import com.myflowers.app.util.displayTime
import com.myflowers.app.util.historyChartSeries
import com.myflowers.app.util.hideQuickCommand
import com.myflowers.app.util.parseCommandPayload
import com.myflowers.app.util.payloadSummary
import com.myflowers.app.util.quickCommands
import com.myflowers.app.util.QuickCommand
import com.myflowers.app.util.recentSensorKeys
import com.myflowers.app.util.userMessage
import java.io.IOException
import java.time.Duration
import java.time.Instant
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
    fun hideQuickCommand_recordsLatestIdOfType_includingFailures() {
        val commands = listOf(
            command(1, "water", "executed"),
            command(4, "water", "failed"),
            command(2, "reboot", "executed")
        )
        assertEquals(mapOf("water" to 4), hideQuickCommand(emptyMap(), commands, "water"))
        assertEquals(mapOf("water" to 4, "reboot" to 2), hideQuickCommand(mapOf("water" to 4), commands, "reboot"))
        assertEquals(mapOf("water" to 4), hideQuickCommand(mapOf("water" to 4), commands, "unknown"))
    }

    @Test
    fun quickCommands_hiddenTypesStayHidden_untilNewerCommandArrives() {
        val commands = listOf(
            command(1, "water", "executed"),
            command(2, "reboot", "executed")
        )
        val hidden = hideQuickCommand(emptyMap(), commands, "water")
        assertEquals(listOf(QuickCommand("reboot", emptyMap())), quickCommands(commands, hidden))
        val resent = listOf(command(3, "water", "pending").copy(payload = mapOf("amount" to 80))) + commands
        assertEquals(
            listOf(QuickCommand("water", mapOf("amount" to 80)), QuickCommand("reboot", emptyMap())),
            quickCommands(resent, hidden)
        )
        // 被移除后只新增了失败记录：仍不恢复。
        val failedRetry = listOf(command(5, "water", "failed")) + commands
        assertEquals(listOf(QuickCommand("reboot", emptyMap())), quickCommands(failedRetry, hidden))
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
    fun commandTimedOut_onlyUnansweredCommandsExpireAfterTenMinutes() {
        val now = Instant.parse("2026-10-24T10:00:00Z")
        assertTrue(commandTimedOut("pending", "2026-10-24T09:49:59Z", now))
        assertTrue(commandTimedOut("pending", "2026-10-24T09:50:00Z", now))
        assertTrue(commandTimedOut("sent", "2026-10-24T09:00:00Z", now))
        assertFalse(commandTimedOut("pending", "2026-10-24T09:50:01Z", now))
        assertFalse(commandTimedOut("sent", "2026-10-24T10:00:05Z", now))
        assertFalse(commandTimedOut("executed", "2026-10-24T08:00:00Z", now))
        assertFalse(commandTimedOut("failed", "2026-10-24T08:00:00Z", now))
        assertFalse(commandTimedOut("pending", "not-a-timestamp", now))
    }

    @Test
    fun commandTimedOut_parsesOffsetAndLocalTimes() {
        val now = Instant.parse("2026-10-24T10:00:00Z")
        assertTrue(commandTimedOut("pending", "2026-10-24T17:50:00+08:00", now))
        assertFalse(commandTimedOut("pending", "2026-10-24T09:55:00", now))
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

    @Test
    fun historyChartSeries_groupsArbitraryKeys_inFirstAppearanceOrder() {
        val first = dataPoint(1, "1", key = "custom/value")
        val second = dataPoint(2, "2", key = "")
        val third = dataPoint(3, "3", key = "custom/value")
        val fourth = dataPoint(4, "4", key = " custom/value ")
        val series = historyChartSeries(listOf(first, second, third, fourth))

        assertEquals(listOf("custom/value", "", " custom/value "), series.map { it.dataKey })
        assertEquals(listOf(first, third), series[0].points.map { it.record })
        assertEquals(listOf(second), series[1].points.map { it.record })
        assertEquals(listOf(fourth), series[2].points.map { it.record })
        assertSame(first, series[0].points[0].record)
    }

    @Test
    fun historyChartSeries_trimsAndParsesFiniteNumericValues() {
        val values = listOf(" 12.5 \n", "-7", "+2", "1.25e3", "0", "-0.0", "1.7976931348623157E308", "4.9E-324")
        val records = values.mapIndexed { index, value -> dataPoint(index, value) }
        val series = historyChartSeries(records).single()

        assertEquals(
            listOf(12.5, -7.0, 2.0, 1250.0, 0.0, -0.0, Double.MAX_VALUE, Double.MIN_VALUE),
            series.points.map { it.value }
        )
        assertEquals(records, series.points.map { it.record })
    }

    @Test
    fun historyChartSeries_sortsByInstantThenId_acrossEquivalentOffsets() {
        val records = listOf(
            dataPoint(8, "1", "1970-01-01T01:00:01+01:00"),
            dataPoint(2, "2", "1970-01-01T00:00:02Z"),
            dataPoint(6, "3", "1970-01-01T00:00:01Z"),
            dataPoint(4, "4", "1969-12-31T19:00:01-05:00"),
            dataPoint(9, "5", "1970-01-01T00:00:00Z")
        )
        val points = historyChartSeries(records).single().points

        assertEquals(listOf(9, 4, 6, 8, 2), points.map { it.record.id })
        assertEquals(listOf(0L, 1000L, 1000L, 1000L, 2000L), points.map { it.time.toEpochMilli() })
    }

    @Test
    fun historyChartSeries_localTimesUseUtc_andPreserveEpochMilliseconds() {
        val records = listOf(
            dataPoint(1, "1", "1970-01-01T00:00:00.123456789"),
            dataPoint(2, "2", "1970-01-01T08:00:00.123456789+08:00"),
            dataPoint(3, "3", "1970-01-01T00:00:00"),
            dataPoint(4, "4", "1969-12-31T23:59:59.999")
        )
        val points = historyChartSeries(records).single().points

        assertEquals(listOf(4, 3, 1, 2), points.map { it.record.id })
        assertEquals(listOf(-1L, 0L, 123L, 123L), points.map { it.time.toEpochMilli() })
    }

    @Test
    fun historyChartSeries_emptyInputReturnsEmpty() {
        assertTrue(historyChartSeries(emptyList()).isEmpty())
    }

    @Test
    fun historyChartSeries_singleValueKeepsOriginalRecord() {
        val record = dataPoint(42, " 3.5 ")
        assertEquals(
            listOf(HistoryChartSeries("custom", listOf(HistoryChartPoint(record, 3.5, Instant.EPOCH)))),
            historyChartSeries(listOf(record))
        )
    }

    @Test
    fun historyChartSeries_excludesNonNumericValuesAndInvalidTimes() {
        val text = dataPoint(9, "ready")
        val valid = dataPoint(8, "2.5")
        val json = dataPoint(7, """{"value": 3}""")
        val badTime = dataPoint(6, "7", "not-a-timestamp")
        val array = dataPoint(5, "[1, 2]")
        val blank = dataPoint(4, " \t")
        val badDate = dataPoint(3, "9", "2026-02-30T00:00:00Z")
        val emptyTime = dataPoint(2, "10", "")
        val dateOnly = dataPoint(1, "11", "2026-10-21")
        val records = listOf(text, valid, json, badTime, array, blank, badDate, emptyTime, dateOnly)
        val series = historyChartSeries(records).single()

        assertEquals(listOf(HistoryChartPoint(valid, 2.5, Instant.EPOCH)), series.points)
    }

    @Test
    fun historyChartSeries_hidesFieldsWithoutNumericValues() {
        val first = dataPoint(3, "offline", key = "status")
        val second = dataPoint(2, "5", key = "reading")
        val third = dataPoint(1, "{}", key = "status")

        assertEquals(
            listOf(HistoryChartSeries("reading", listOf(HistoryChartPoint(second, 5.0, Instant.EPOCH)))),
            historyChartSeries(listOf(first, second, third))
        )
    }

    @Test
    fun historyChartSeries_nonFiniteAndOverflowingNumbersAreExcluded() {
        val values = listOf("NaN", " Infinity ", "+Infinity", "-Infinity", "1e309", "-1e309", "true", "12kg", "null")
        val records = values.mapIndexed { index, value -> dataPoint(index, value) }
        assertTrue(historyChartSeries(records).isEmpty())
    }

    @Test
    fun historyChartSeries_preservesLargeInstants_withoutEpochMillisecondConversion() {
        val records = listOf(
            dataPoint(1, "1", "+999999999-12-31T23:59:59Z"),
            dataPoint(2, "2", "-999999999-01-01T00:00:00Z")
        )
        val series = historyChartSeries(records).single()
        assertEquals(listOf(2, 1), series.points.map { it.record.id })
    }

    @Test
    fun historyChartSeries_preservesSubMillisecondOrder() {
        val series = historyChartSeries(listOf(
            dataPoint(1, "9", "2026-10-21T09:30:00.123900Z"),
            dataPoint(2, "1", "2026-10-21T09:30:00.123100Z")
        )).single()
        assertEquals(listOf(2, 1), series.points.map { it.record.id })
        assertEquals(800_000, series.points.last().time.nano - series.points.first().time.nano)
    }

    @Test
    fun chartFraction_normalizesFiniteRange() {
        assertEquals(0f, chartFraction(-10.0, -10.0, 30.0), 0f)
        assertEquals(0.25f, chartFraction(0.0, -10.0, 30.0), 0f)
        assertEquals(0.5f, chartFraction(10.0, -10.0, 30.0), 0f)
        assertEquals(1f, chartFraction(30.0, -10.0, 30.0), 0f)
    }

    @Test
    fun chartFraction_handlesHugeFiniteRanges_withoutOverflow() {
        val maximum = Double.MAX_VALUE
        assertEquals(0f, chartFraction(-maximum, -maximum, maximum), 0f)
        assertEquals(0.25f, chartFraction(-maximum / 2, -maximum, maximum), 0f)
        assertEquals(0.5f, chartFraction(0.0, -maximum, maximum), 0f)
        assertEquals(0.75f, chartFraction(maximum / 2, -maximum, maximum), 0f)
        assertEquals(1f, chartFraction(maximum, -maximum, maximum), 0f)
        assertEquals(0.5f, chartFraction(maximum * 0.75, maximum / 2, maximum), 0f)
    }

    @Test
    fun chartFraction_handlesTinyFiniteAndSubnormalRanges() {
        val minimum = Double.MIN_VALUE
        assertEquals(0f, chartFraction(-minimum, -minimum, minimum), 0f)
        assertEquals(0.5f, chartFraction(0.0, -minimum, minimum), 0f)
        assertEquals(1f, chartFraction(minimum, -minimum, minimum), 0f)
        assertEquals(0.5f, chartFraction(minimum, 0.0, minimum * 2), 0f)
        assertEquals(0.5f, chartFraction(2e-300, 1e-300, 3e-300), 0.000001f)
    }

    @Test
    fun chartFraction_constantRangesUseMidpoint() {
        listOf(0.0, -0.0, 7.0, -7.0, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE, -Double.MIN_VALUE)
            .forEach { value -> assertEquals(0.5f, chartFraction(value, value, value), 0f) }
    }

    @Test
    fun chartNumber_zeroAndTrailingZerosAreClean() {
        assertEquals("0", chartNumber(0.0))
        assertEquals("0", chartNumber(-0.0))
        assertEquals("1", chartNumber(1.0))
        assertEquals("12.5", chartNumber(12.50))
        assertEquals("-12.5", chartNumber(-12.50))
    }

    @Test
    fun chartNumber_roundsToFourSignificantDigits() {
        assertEquals("1.235", chartNumber(1.23456))
        assertEquals("-1.235", chartNumber(-1.23456))
        assertEquals("12.35", chartNumber(12.345))
        assertEquals("0.001235", chartNumber(0.00123456))
        assertEquals("1E+4", chartNumber(9999.5))
    }

    @Test
    fun chartNumber_extremeMagnitudesUseCompactScientificNotation() {
        assertEquals("1E+3", chartNumber(1000.0))
        assertEquals("1.235E+20", chartNumber(1.23456e20))
        assertEquals("1.798E+308", chartNumber(Double.MAX_VALUE))
        assertEquals("-1.798E+308", chartNumber(-Double.MAX_VALUE))
        assertEquals("1.235E-20", chartNumber(1.23456e-20))
        assertEquals("4.9E-324", chartNumber(Double.MIN_VALUE))
    }

    @Test
    fun recentSensorKeys_keepsRecentAndFuture_hidesStaleMissingAndInvalid() {
        val now = Instant.parse("2026-10-24T10:00:00Z")
        val latest = mapOf(
            "recent" to dataPoint(1, "42", "2026-10-24T09:45:00Z"),
            "boundary" to dataPoint(2, "43", "2026-10-24T09:30:00Z"),
            "stale" to dataPoint(3, "44", "2026-10-24T09:29:59Z"),
            "offset" to dataPoint(4, "45", "2026-10-24T17:30:00+08:00"),
            "future" to dataPoint(5, "46", "2026-10-24T10:00:05Z"),
            "invalid" to dataPoint(6, "47", "not-a-timestamp"),
            "missing" to null
        )
        assertEquals(
            listOf("recent", "boundary", "offset", "future"),
            recentSensorKeys(
                listOf("recent", "boundary", "stale", "offset", "future", "invalid", "missing"),
                latest,
                now
            )
        )
    }

    @Test
    fun recentSensorKeys_respectsCustomWindow_andPreservesKeyOrder() {
        val now = Instant.parse("2026-10-24T10:00:00Z")
        val latest = mapOf(
            "a" to dataPoint(1, "1", "2026-10-24T09:58:00Z"),
            "b" to dataPoint(2, "2", "2026-10-24T09:55:00Z"),
            "c" to dataPoint(3, "3", "2026-10-24T09:59:30Z")
        )
        assertEquals(
            listOf("c", "a"),
            recentSensorKeys(listOf("b", "c", "a"), latest, now, Duration.ofMinutes(2))
        )
        assertTrue(recentSensorKeys(emptyList(), latest, now).isEmpty())
        assertTrue(recentSensorKeys(listOf("missing"), emptyMap(), now).isEmpty())
    }

    private fun dataPoint(
        id: Int,
        value: String,
        timestamp: String = "1970-01-01T00:00:00Z",
        key: String = "custom"
    ) = DataPoint(
        id = id,
        deviceId = 1,
        dataKey = key,
        dataValue = value,
        timestamp = timestamp
    )

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
