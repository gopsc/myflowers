package com.myflowers.app.util

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.myflowers.app.data.model.Command
import com.myflowers.app.data.model.DataPoint
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.floor
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.net.ssl.SSLException

fun userMessage(error: Throwable): String = when (error) {
    is HttpException -> when (error.code()) {
        400, 409 -> "操作未完成，请检查输入；注册时请尝试其他用户名。"
        401 -> "账号、密码不正确，或登录已过期。请重新登录。"
        403 -> "当前账号没有此操作权限，请使用 mobile 类型账号。"
        404 -> "没有找到对应设备或数据，请刷新后再试。"
        422 -> "输入格式不正确，请检查后再试。"
        429 -> "请求过于频繁，请稍后再试。"
        else -> "服务暂时不可用，请稍后重试。"
    }
    is SSLException -> "无法建立安全连接，请检查服务器证书和设备时间。"
    is IOException -> "连接失败，请检查网络后重试。"
    else -> "操作未完成，请稍后重试。"
}

fun displayTime(value: String?): String {
    if (value.isNullOrBlank()) return "暂无记录"
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    return try {
        OffsetDateTime.parse(value).format(formatter)
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(value).format(formatter)
        } catch (_: DateTimeParseException) {
            value
        }
    }
}

data class HistoryChartPoint(val record: DataPoint, val value: Double, val time: Instant)

data class HistoryChartSeries(val dataKey: String, val points: List<HistoryChartPoint>)

fun historyChartSeries(history: List<DataPoint>): List<HistoryChartSeries> =
    history.groupBy { it.dataKey }.mapNotNull { (dataKey, records) ->
        val points = records.mapNotNull { record ->
            val value = record.dataValue.trim().toDoubleOrNull()
            val time = historyTime(record.timestamp)
            if (value != null && value.isFinite() && time != null) HistoryChartPoint(record, value, time) else null
        }
        if (points.isEmpty()) null else HistoryChartSeries(
            dataKey,
            points.sortedWith(compareBy<HistoryChartPoint> { it.time }.thenBy { it.record.id })
        )
    }

/**
 * 聚合曲线末尾补上最新的原始读数，让末点与实时卡片一致。
 * 只补聚合序列里已有的 data_key；如果最新读数已经在序列里（旧服务端回退原始
 * 数据时），按时间比较不会重复追加。
 */
fun chartHistoryWithLatest(
    aggregated: List<DataPoint>,
    latest: Map<String, DataPoint?>
): List<DataPoint> {
    if (aggregated.isEmpty()) return aggregated
    val lastTimes = aggregated.groupBy { it.dataKey }.mapValues { (_, points) ->
        points.mapNotNull { historyTime(it.timestamp) }.maxOrNull()
    }
    val keys = lastTimes.keys
    val extras = latest.values.filterNotNull().filter { point ->
        if (point.dataKey !in keys) return@filter false
        val time = historyTime(point.timestamp) ?: return@filter false
        val last = lastTimes[point.dataKey] ?: return@filter false
        time > last
    }
    return if (extras.isEmpty()) aggregated else aggregated + extras
}

private fun historyTime(timestamp: String): Instant? = try {
    try {
        OffsetDateTime.parse(timestamp).toInstant()
    } catch (_: DateTimeParseException) {
        LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC)
    }
} catch (_: DateTimeException) {
    null
}

fun chartFraction(value: Double, minimum: Double, maximum: Double): Float {
    if (minimum == maximum) return 0.5f
    val scale = maxOf(abs(minimum), abs(maximum))
    val scaledMinimum = minimum / scale
    return ((value / scale - scaledMinimum) / (maximum / scale - scaledMinimum))
        .toFloat().coerceIn(0f, 1f)
}

fun chartNumber(value: Double): String =
    BigDecimal.valueOf(value).round(MathContext(4)).stripTrailingZeros().toString()

/** 实时概览只展示最近这段时间内上报过的传感量。 */
val SENSOR_STALE_AFTER: Duration = Duration.ofMinutes(30)

/** 保留最新读数够新的字段；缺失、时间无法解析或已过期的字段都不再展示。 */
fun recentSensorKeys(
    keys: List<String>,
    latest: Map<String, DataPoint?>,
    now: Instant,
    staleAfter: Duration = SENSOR_STALE_AFTER
): List<String> = keys.filter { key ->
    val time = latest[key]?.let { historyTime(it.timestamp) }
    time != null && !time.isBefore(now.minus(staleAfter))
}

fun commandTitle(type: String): String = when (type) {
    "water" -> "浇水"
    "turn_on_light" -> "开灯"
    "turn_off_light" -> "关灯"
    "set_threshold" -> "设置阈值"
    else -> type
}

fun commandStatus(status: String): String = when (status) {
    "pending" -> "等待执行"
    "sent" -> "已下发"
    "executed" -> "已执行"
    "failed" -> "执行失败"
    else -> status
}

/** 等待回应超过这段时间的指令视为超时失效。 */
val COMMAND_RESPONSE_TIMEOUT: Duration = Duration.ofMinutes(10)

/** 只有等待执行或已下发的指令会超时；已执行、已失败和时间无法解析的记录保持原状态。 */
fun commandTimedOut(status: String, createdAt: String, now: Instant): Boolean =
    (status == "pending" || status == "sent") &&
        historyTime(createdAt)?.let { !it.isAfter(now.minus(COMMAND_RESPONSE_TIMEOUT)) } == true

/** 快捷指令 = 历史指令沉淀：类型 + 复用最近一次的参数。 */
data class QuickCommand(val type: String, val payload: Map<String, Any>)

/**
 * 把指令历史变成快捷指令：每个类型一条，取该类型最近一次的参数，按最近使用排序。
 * 全部记录都失败的类型不沉淀（设备不认的指令不该一键重发）。
 * hidden 记录用户移除时该类型的最新指令 id；只有出现更新的同类指令才重新显示。
 */
fun quickCommands(commands: List<Command>, hidden: Map<String, Int> = emptyMap()): List<QuickCommand> =
    commands.filter { it.status != "failed" }
        .groupBy { it.commandType }
        .map { group -> group.value.maxBy { it.id } }
        .filter { latest -> hidden[latest.commandType]?.let { latest.id > it } ?: true }
        .sortedByDescending { it.id }
        .map { QuickCommand(it.commandType, it.payload) }

/** 移除某类快捷指令：记下该类型当前最新的指令 id（含失败记录）。 */
fun hideQuickCommand(hidden: Map<String, Int>, commands: List<Command>, type: String): Map<String, Int> {
    val latestId = commands.filter { it.commandType == type }.maxOfOrNull { it.id } ?: return hidden
    return hidden + (type to latestId)
}

/** 快捷按钮的参数摘要：空参数显示「无参数」，整数去掉 Gson 带出的小数点。 */
fun payloadSummary(payload: Map<String, Any>): String {
    if (payload.isEmpty()) return "无参数"
    return payload.entries.joinToString(", ") { "${it.key}=${prettyValue(it.value)}" }
}

private fun prettyValue(value: Any): String = when (value) {
    is Double -> if (value == floor(value) && abs(value) < 1e15) value.toLong().toString() else value.toString()
    else -> value.toString()
}

/** 自定义指令参数：空白视为无参数；必须是 JSON 对象，否则返回 null 交由界面提示。 */
fun parseCommandPayload(text: String): Map<String, Any>? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyMap()
    return try {
        val element = JsonParser.parseString(trimmed)
        if (!element.isJsonObject) null
        else Gson().fromJson(element, object : TypeToken<Map<String, Any>>() {}.type)
    } catch (_: JsonSyntaxException) {
        null
    }
}
