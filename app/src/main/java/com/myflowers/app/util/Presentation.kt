package com.myflowers.app.util

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.myflowers.app.data.model.Command
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

/** 常见传感量的展示名与单位；设备是通用的，未知 data_key 原样展示、单位留空。 */
data class SensorDisplay(val label: String, val unit: String)

val knownSensors: Map<String, SensorDisplay> = mapOf(
    "soil_moisture" to SensorDisplay("土壤湿度", "%"),
    "temperature" to SensorDisplay("环境温度", "°C"),
    "humidity" to SensorDisplay("空气湿度", "%"),
    "light" to SensorDisplay("光照强度", "lux"),
    "co2" to SensorDisplay("二氧化碳", "ppm"),
    "water_level" to SensorDisplay("水位", "%"),
    "pressure" to SensorDisplay("气压", "hPa"),
    "battery" to SensorDisplay("电量", "%"),
    "weight" to SensorDisplay("重量", "kg")
)

fun sensorLabel(dataKey: String): String = knownSensors[dataKey]?.label ?: dataKey

fun sensorUnit(dataKey: String): String = knownSensors[dataKey]?.unit ?: ""

private val plantCareTypes = setOf("water", "turn_on_light", "turn_off_light")

/**
 * 是否显示浇水/灯光快捷指令：设备还没有任何上报与指令记录时默认显示（与花盆一致）；
 * 一旦有记录则以内容为准——上报过土壤湿度/光照、或用过浇水/灯光指令的设备才显示，
 * 其他机器只保留自定义指令入口。
 */
fun hasPlantCareQuickActions(sensorKeys: Collection<String>, commandTypes: Collection<String>): Boolean {
    if (sensorKeys.isEmpty() && commandTypes.isEmpty()) return true
    return sensorKeys.any { it in setOf("soil_moisture", "light") } ||
        commandTypes.any { it in plantCareTypes }
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
