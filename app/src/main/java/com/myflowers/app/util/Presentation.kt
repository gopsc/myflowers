package com.myflowers.app.util

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.myflowers.app.data.model.Command
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.abs
import kotlin.math.floor
import java.time.LocalDateTime
import java.time.OffsetDateTime
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

/** 快捷指令 = 历史指令沉淀：类型 + 复用最近一次的参数。 */
data class QuickCommand(val type: String, val payload: Map<String, Any>)

/**
 * 把指令历史变成快捷指令：每个类型一条，取该类型最近一次的参数，按最近使用排序。
 * 全部记录都失败的类型不沉淀（设备不认的指令不该一键重发）。
 */
fun quickCommands(commands: List<Command>): List<QuickCommand> =
    commands.filter { it.status != "failed" }
        .groupBy { it.commandType }
        .map { group -> group.value.maxBy { it.id } }
        .sortedByDescending { it.id }
        .map { QuickCommand(it.commandType, it.payload) }

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
