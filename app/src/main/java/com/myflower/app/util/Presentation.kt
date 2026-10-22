package com.myflower.app.util

import com.myflower.app.data.model.Command
import retrofit2.HttpException
import java.io.IOException
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

fun lastLightRequest(commands: List<Command>): Boolean? = commands
    .filter { it.commandType in setOf("turn_on_light", "turn_off_light") && it.status in setOf("pending", "sent", "executed") }
    .maxByOrNull { it.id }
    ?.let { it.commandType == "turn_on_light" }
