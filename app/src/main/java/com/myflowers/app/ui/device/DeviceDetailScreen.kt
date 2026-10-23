package com.myflowers.app.ui.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myflowers.app.data.DataRepository
import com.myflowers.app.data.model.Command
import com.myflowers.app.data.model.DataPoint
import com.myflowers.app.data.model.Device
import com.myflowers.app.ui.theme.*
import com.myflowers.app.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val HISTORY_LIMIT = 100

@Composable
fun DeviceDetailScreen(device: Device, repository: DataRepository, onBack: () -> Unit) {
    var currentDevice by remember(device.id) { mutableStateOf(device) }
    var sensorKeys by remember(device.id) { mutableStateOf<List<String>>(emptyList()) }
    var latest by remember(device.id) { mutableStateOf<Map<String, DataPoint?>>(emptyMap()) }
    var history by remember(device.id) { mutableStateOf<List<DataPoint>>(emptyList()) }
    var commands by remember(device.id) { mutableStateOf<List<Command>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var commandError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable(device.id) { mutableIntStateOf(0) }
    var busyCommand by remember { mutableStateOf<String?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lightRequested = lastLightRequest(commands)
    val plantCare = hasPlantCareQuickActions(sensorKeys, commands.map { it.commandType })

    LaunchedEffect(device.id, refresh) {
        loading = true
        error = null
        try {
            currentDevice = repository.getDevice(device.id)
            val fetchedHistory = repository.getDeviceData(device.id, limit = HISTORY_LIMIT)
            history = fetchedHistory
            // 传感量不写死：从上报记录里发现这台设备实际有哪些 data_key。
            val keys = fetchedHistory.map { it.dataKey }.distinct()
            sensorKeys = keys
            coroutineScope {
                val latestJob = async {
                    keys.map { key -> async { key to repository.getLatestData(device.id, key) } }.awaitAll().toMap()
                }
                val commandsJob = async { repository.getCommandHistory(device.id, limit = 30) }
                latest = latestJob.await()
                commands = commandsJob.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = userMessage(e)
        } finally {
            loading = false
        }
    }

    fun send(type: String, payload: Map<String, Any> = emptyMap(), onFinished: (Boolean) -> Unit = {}) {
        if (busyCommand != null) return
        busyCommand = type
        commandError = null
        scope.launch {
            try {
                val command = repository.sendCommand(device.id, type, payload)
                commands = listOf(command) + commands.filterNot { it.id == command.id }
                scope.launch { snackbar.showSnackbar("${commandTitle(type)}指令已提交，${commandStatus(command.status)}") }
                onFinished(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                commandError = userMessage(e)
                onFinished(false)
            } finally {
                busyCommand = null
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回列表") }
                        Text("设备详情", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { refresh++ }, enabled = !loading && busyCommand == null) { Icon(Icons.Outlined.Refresh, "刷新数据") }
                    }
                }
                item {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.padding(22.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("STAY CONNECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                StatusPill(currentDevice.isOnline)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(currentDevice.deviceName, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text("一点关注，一点安心。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("设备 #${device.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                PlantIllustration(Modifier.size(132.dp))
                            }
                            Text("最近连接  ${displayTime(currentDevice.lastSeen)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                error?.let { message -> item {
                    Column {
                        ErrorNotice(message)
                        TextButton(onClick = { refresh++ }, enabled = !loading && busyCommand == null) { Text("重新加载数据") }
                    }
                } }
                item {
                    PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("实时概览") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("运行记录") })
                    }
                }
                if (tab == 0) {
                    item { SectionTitle("感知它的一举一动", "最近一次上报 · 点击右上角刷新") }
                    if (!loading && error == null && sensorKeys.isEmpty()) {
                        item {
                            Text("暂未收到数据，等待设备第一次上报。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(sensorKeys.chunked(2)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { key -> SensorCard(key, latest[key], loading, Modifier.weight(1f)) }
                        }
                    }
                    item {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.Eco, null, tint = MaterialTheme.colorScheme.primary)
                                Text("每台设备都有自己的节奏。请结合设备类型与实际环境决定下一步操作。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("轻松照看", "指令提交不代表设备已执行，请以运行记录为准。")
                            if (plantCare) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CareButton(
                                        title = "浇水", subtitle = "每次 100 ml", icon = Icons.Outlined.WaterDrop,
                                        loading = busyCommand == "water", enabled = busyCommand == null && !loading,
                                        modifier = Modifier.weight(1f), onClick = { send("water", mapOf("amount" to 100)) }
                                    )
                                    CareButton(
                                        title = if (lightRequested == true) "关灯" else "开灯",
                                        subtitle = when (lightRequested) { true -> "上次目标：开启"; false -> "上次目标：关闭"; null -> "灯光状态未知" },
                                        icon = if (lightRequested == true) Icons.Outlined.Lightbulb else Icons.Outlined.LightbulbCircle,
                                        loading = busyCommand in listOf("turn_on_light", "turn_off_light"), enabled = busyCommand == null && !loading,
                                        warm = true, modifier = Modifier.weight(1f),
                                        onClick = { send(if (lightRequested == true) "turn_off_light" else "turn_on_light") }
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { commandError = null; showCustom = true },
                                enabled = busyCommand == null && !loading,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "自定义指令" },
                                shape = MaterialTheme.shapes.large
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Outlined.Tune, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("自定义指令", style = MaterialTheme.typography.titleMedium)
                                        Text("任意类型 + JSON 参数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (!currentDevice.isOnline) Text("设备当前离线，指令可能需要等待设备重新连接。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            commandError?.let { ErrorNotice(it) }
                        }
                    }
                } else {
                    item { SectionTitle("最近的指令", "等待执行 → 已下发 → 已执行 / 执行失败") }
                    if (commands.isEmpty()) item { Text(if (loading) "正在读取指令…" else if (error != null) "指令记录未能加载" else "还没有任何指令。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(commands, key = { "command-${it.id}" }) { CommandCard(it) }
                    item { SectionTitle("数据足迹", "最近 $HISTORY_LIMIT 条设备上报") }
                    if (history.isEmpty()) item { Text(if (loading) "正在读取数据…" else if (error != null) "设备数据未能加载" else "暂未收到数据，等待设备第一次上报。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(history, key = { "data-${it.id}" }) { DataPointCard(it) }
                }
            }
        }
    }

    if (showCustom) CustomCommandDialog(
        busy = busyCommand != null,
        error = commandError,
        onDismiss = { showCustom = false },
        onSubmit = { type, payload -> send(type, payload) { if (it) showCustom = false } }
    )
}

@Composable
private fun CareButton(title: String, subtitle: String, icon: ImageVector, loading: Boolean, enabled: Boolean, modifier: Modifier, warm: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (warm) colors.tertiaryContainer else colors.primary, contentColor = if (warm) colors.onTertiaryContainer else colors.onPrimary)
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(25.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            else Icon(icon, null, Modifier.size(25.dp))
            Text(if (loading) "提交中…" else title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SensorCard(dataKey: String, point: DataPoint?, loading: Boolean, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(sensorIcon(dataKey), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Text(sensorLabel(dataKey), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(point?.dataValue ?: "—", style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val unit = sensorUnit(dataKey)
            if (unit.isNotBlank()) Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(if (point != null) displayTime(point.timestamp) else if (loading) "读取中" else "暂无读数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 通用设备不限定传感量：已知 key 用贴切图标，其余用统一的传感图标。 */
private val sensorIcons = mapOf(
    "soil_moisture" to Icons.Outlined.Grass,
    "temperature" to Icons.Outlined.Thermostat,
    "humidity" to Icons.Outlined.WaterDrop,
    "light" to Icons.Outlined.LightMode,
    "co2" to Icons.Outlined.Air,
    "water_level" to Icons.Outlined.Water,
    "pressure" to Icons.Outlined.Speed,
    "battery" to Icons.Outlined.BatteryFull,
    "weight" to Icons.Outlined.Scale
)

private fun sensorIcon(dataKey: String): ImageVector = sensorIcons[dataKey] ?: Icons.Outlined.Sensors

private fun commandIcon(type: String): ImageVector = when (type) {
    "water" -> Icons.Outlined.WaterDrop
    "turn_on_light", "turn_off_light" -> Icons.Outlined.Lightbulb
    "set_threshold" -> Icons.Outlined.Tune
    else -> Icons.Outlined.Bolt
}

@Composable
private fun CustomCommandDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (type: String, payload: Map<String, Any>) -> Unit
) {
    var type by rememberSaveable { mutableStateOf("") }
    var payloadText by rememberSaveable { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Outlined.Tune, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("发送任意指令") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("向设备提交任意类型的指令，由设备侧按其支持的指令集执行。", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it; validationError = null },
                    label = { Text("指令类型") },
                    placeholder = { Text("例如：set_threshold") },
                    singleLine = true,
                    enabled = !busy,
                    isError = validationError != null,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = payloadText,
                    onValueChange = { payloadText = it; validationError = null },
                    label = { Text("参数（JSON，可选）") },
                    placeholder = { Text("{\"amount\": 200}") },
                    enabled = !busy,
                    isError = validationError != null,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth()
                )
                validationError?.let { ErrorNotice(it) }
                error?.let { ErrorNotice(it) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "发送自定义指令" },
                onClick = {
                    val trimmedType = type.trim()
                    val payload = parseCommandPayload(payloadText)
                    validationError = when {
                        trimmedType.isEmpty() -> "请填写指令类型。"
                        payload == null -> "参数不是合法的 JSON 对象。"
                        else -> null
                    }
                    if (validationError == null) onSubmit(trimmedType, payload!!)
                }
            ) { Text(if (busy) "提交中…" else "发送指令") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } }
    )
}

@Composable
fun CommandCard(command: Command) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(commandIcon(command.commandType), null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(commandTitle(command.commandType), style = MaterialTheme.typography.titleSmall)
                Text(displayTime(command.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(commandStatus(command.status), style = MaterialTheme.typography.labelMedium, color = if (command.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun DataPointCard(dataPoint: DataPoint) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(sensorLabel(dataPoint.dataKey), style = MaterialTheme.typography.bodyMedium)
                Text(displayTime(dataPoint.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val unit = sensorUnit(dataPoint.dataKey)
            Text("${dataPoint.dataValue}${if (unit.isBlank()) "" else " $unit"}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
