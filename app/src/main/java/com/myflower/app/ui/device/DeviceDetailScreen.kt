package com.myflower.app.ui.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myflower.app.data.DataRepository
import com.myflower.app.data.model.Command
import com.myflower.app.data.model.DataPoint
import com.myflower.app.data.model.Device
import com.myflower.app.ui.theme.*
import com.myflower.app.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun DeviceDetailScreen(device: Device, repository: DataRepository, onBack: () -> Unit) {
    var currentDevice by remember(device.id) { mutableStateOf(device) }
    var latest by remember(device.id) { mutableStateOf<Map<String, DataPoint?>>(emptyMap()) }
    var history by remember(device.id) { mutableStateOf<List<DataPoint>>(emptyList()) }
    var commands by remember(device.id) { mutableStateOf<List<Command>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var commandError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable(device.id) { mutableIntStateOf(0) }
    var busyCommand by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lightRequested = lastLightRequest(commands)

    LaunchedEffect(device.id, refresh) {
        loading = true
        error = null
        try {
            currentDevice = repository.getDevice(device.id)
            coroutineScope {
                val latestJob = async {
                    sensorSpecs.map { spec -> async { spec.key to repository.getLatestData(device.id, spec.key) } }.awaitAll().toMap()
                }
                val historyJob = async { repository.getDeviceData(device.id, limit = 40) }
                val commandsJob = async { repository.getCommandHistory(device.id, limit = 30) }
                latest = latestJob.await()
                history = historyJob.await()
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

    fun send(type: String) {
        if (busyCommand != null) return
        busyCommand = type
        commandError = null
        scope.launch {
            try {
                val command = repository.sendCommand(device.id, type, if (type == "water") mapOf("amount" to 100) else emptyMap())
                commands = listOf(command) + commands.filterNot { it.id == command.id }
                scope.launch { snackbar.showSnackbar("${commandTitle(type)}指令已提交，${commandStatus(command.status)}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                commandError = userMessage(e)
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
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回花园") }
                        Text("我的花盆", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { refresh++ }, enabled = !loading && busyCommand == null) { Icon(Icons.Outlined.Refresh, "刷新数据") }
                    }
                }
                item {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.padding(22.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("GROWING TOGETHER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                StatusPill(currentDevice.isOnline)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(currentDevice.deviceName, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text("一点关心，一点新绿。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("生长概览") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("养护记录") })
                    }
                }
                if (tab == 0) {
                    item { SectionTitle("感知它的小世界", "最近一次上报 · 点击右上角刷新") }
                    items(sensorSpecs.chunked(2)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { spec -> SensorCard(spec, latest[spec.key], loading, Modifier.weight(1f)) }
                        }
                    }
                    item {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.Eco, null, tint = MaterialTheme.colorScheme.primary)
                                Text("每株植物都有自己的节奏。请结合植物品种、土壤和实际环境决定是否浇水。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("轻松照料", "指令提交不代表设备已执行，请以养护记录为准。")
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CareButton(
                                    title = "浇水", subtitle = "每次 100 ml", icon = Icons.Outlined.WaterDrop,
                                    loading = busyCommand == "water", enabled = busyCommand == null && !loading,
                                    modifier = Modifier.weight(1f), onClick = { send("water") }
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
                            if (!currentDevice.isOnline) Text("设备当前离线，指令可能需要等待设备重新连接。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            commandError?.let { ErrorNotice(it) }
                        }
                    }
                } else {
                    item { SectionTitle("最近的照料", "等待执行 → 已下发 → 已执行 / 执行失败") }
                    if (commands.isEmpty()) item { Text(if (loading) "正在读取指令…" else if (error != null) "指令记录未能加载" else "还没有养护指令。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(commands, key = { "command-${it.id}" }) { CommandCard(it) }
                    item { SectionTitle("数据足迹", "最近 40 条设备上报") }
                    if (history.isEmpty()) item { Text(if (loading) "正在读取数据…" else if (error != null) "设备数据未能加载" else "暂未收到数据，等待花盆第一次上报。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(history, key = { "data-${it.id}" }) { DataPointCard(it) }
                }
            }
        }
    }
}

private data class SensorSpec(val key: String, val label: String, val unit: String, val icon: ImageVector)
private val sensorSpecs = listOf(
    SensorSpec("soil_moisture", "土壤湿度", "%", Icons.Outlined.Grass),
    SensorSpec("temperature", "环境温度", "°C", Icons.Outlined.Thermostat),
    SensorSpec("humidity", "空气湿度", "%", Icons.Outlined.WaterDrop),
    SensorSpec("light", "光照强度", "lux", Icons.Outlined.LightMode)
)

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
private fun SensorCard(spec: SensorSpec, point: DataPoint?, loading: Boolean, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(spec.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Text(spec.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(point?.dataValue ?: "—", style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(spec.unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(if (point != null) displayTime(point.timestamp) else if (loading) "读取中" else "暂无读数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CommandCard(command: Command) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (command.commandType == "water") Icons.Outlined.WaterDrop else Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
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
    val spec = sensorSpecs.find { it.key == dataPoint.dataKey }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(spec?.label ?: dataPoint.dataKey, style = MaterialTheme.typography.bodyMedium)
                Text(displayTime(dataPoint.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${dataPoint.dataValue} ${spec?.unit.orEmpty()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
