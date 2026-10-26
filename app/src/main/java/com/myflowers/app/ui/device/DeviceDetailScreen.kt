package com.myflowers.app.ui.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
import java.time.Duration
import java.time.Instant

private const val HISTORY_LIMIT = 100
private const val VISIBLE_COMMAND_LIMIT = 5
private const val CHART_WINDOW_HOURS = 24L
private const val CHART_INTERVAL_SECONDS = 15 * 60
private const val CHART_POINT_LIMIT = 1000

@Composable
fun DeviceDetailScreen(device: Device, repository: DataRepository, onBack: () -> Unit) {
    var currentDevice by remember(device.id) { mutableStateOf(device) }
    var sensorKeys by remember(device.id) { mutableStateOf<List<String>>(emptyList()) }
    var latest by remember(device.id) { mutableStateOf<Map<String, DataPoint?>>(emptyMap()) }
    var history by remember(device.id) { mutableStateOf<List<DataPoint>>(emptyList()) }
    var chartHistory by remember(device.id) { mutableStateOf<List<DataPoint>>(emptyList()) }
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
    var hiddenQuick by remember(device.id) { mutableStateOf(repository.hiddenQuickCommands(device.id)) }
    var editingQuick by rememberSaveable(device.id) { mutableStateOf(false) }
    val quick = quickCommands(commands, hiddenQuick)
    LaunchedEffect(quick.isEmpty()) { if (quick.isEmpty()) editingQuick = false }
    val recentSeries = remember(history) { historyChartSeries(history) }
    val longTermSeries = remember(chartHistory) { historyChartSeries(chartHistory) }
    val chartKeys = remember(recentSeries, longTermSeries) {
        (recentSeries.map { it.dataKey } + longTermSeries.map { it.dataKey }).distinct()
    }
    var historyKey by rememberSaveable(device.id) { mutableStateOf<String?>(null) }
    val selectedKey = historyKey?.takeIf { it in chartKeys } ?: chartKeys.firstOrNull()
    val selectedRecent = recentSeries.find { it.dataKey == selectedKey }
    val selectedLongTerm = longTermSeries.find { it.dataKey == selectedKey }
    val recentKeys = remember(sensorKeys, latest) { recentSensorKeys(sensorKeys, latest, Instant.now()) }

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
                // 历史图单独取 24 小时、15 分钟聚合的数据；失败时退回最近的原始记录。
                val chartJob = async {
                    try {
                        repository.getDeviceData(
                            device.id,
                            start = Instant.now().minus(Duration.ofHours(CHART_WINDOW_HOURS)).toString(),
                            intervalSeconds = CHART_INTERVAL_SECONDS,
                            limit = CHART_POINT_LIMIT
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                latest = latestJob.await()
                commands = commandsJob.await()
                val aggregated = chartJob.await()
                chartHistory = if (aggregated.isEmpty()) fetchedHistory else chartHistoryWithLatest(aggregated, latest)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = userMessage(e)
        } finally {
            loading = false
        }
    }

    fun updateHidden(hidden: Map<String, Int>) {
        hiddenQuick = hidden
        repository.setHiddenQuickCommands(device.id, hidden)
    }

    fun removeQuick(type: String) {
        val previous = hiddenQuick[type]
        updateHidden(hideQuickCommand(hiddenQuick, commands, type))
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("已移除「${commandTitle(type)}」快捷指令", actionLabel = "撤销", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                updateHidden(if (previous == null) hiddenQuick - type else hiddenQuick + (type to previous))
            }
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
                if (type in hiddenQuick) updateHidden(hiddenQuick - type)
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
                    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(heroBrush())) {
                        Column(Modifier.padding(22.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("STAY CONNECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                StatusPill(currentDevice.isOnline)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(currentDevice.deviceName, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text("设备 #${device.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                PlantIllustration(Modifier.size(132.dp))
                            }
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
                    item { SectionTitle("感知它的一举一动") }
                    if (!loading && error == null && sensorKeys.isEmpty()) {
                        item {
                            Text("暂未收到数据，等待设备第一次上报。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!loading && error == null && sensorKeys.isNotEmpty() && recentKeys.isEmpty()) {
                        item {
                            Text(
                                "超过 ${SENSOR_STALE_AFTER.toMinutes()} 分钟没有新的上报，实时数据暂不显示。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(recentKeys.chunked(2)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { key -> SensorCard(key, latest[key], loading, Modifier.weight(1f)) }
                        }
                    }
                    if (!loading && recentKeys.isNotEmpty() && recentKeys.size < sensorKeys.size) {
                        item {
                            Text(
                                "已隐藏 ${sensorKeys.size - recentKeys.size} 个超过 ${SENSOR_STALE_AFTER.toMinutes()} 分钟未更新的项目。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                            SectionTitle(
                                "轻松照看",
                                if (editingQuick) "移除只影响本机的快捷方式，运行记录不受影响；再次发送同类指令会重新出现。"
                                else "指令提交不代表设备已执行，请以运行记录为准。",
                                action = if (quick.isEmpty()) null else {
                                    {
                                        TextButton(onClick = { editingQuick = !editingQuick }) {
                                            Icon(if (editingQuick) Icons.Outlined.Check else Icons.Outlined.Edit, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (editingQuick) "完成" else "管理")
                                        }
                                    }
                                }
                            )
                            quick.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { command ->
                                        QuickCommandTile(
                                            command = command,
                                            editing = editingQuick,
                                            loading = busyCommand == command.type,
                                            enabled = busyCommand == null && !loading,
                                            modifier = Modifier.weight(1f),
                                            onSend = { send(command.type, command.payload) },
                                            onRemove = { removeQuick(command.type) }
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            OutlinedButton(
                                onClick = { commandError = null; showCustom = true },
                                enabled = busyCommand == null && !loading,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "自定义指令" },
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    IconBadge(Icons.Outlined.Tune, size = 36.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text("自定义指令", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("任意类型 + JSON 参数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                                }
                            }
                            if (!currentDevice.isOnline) Text("设备当前离线，指令可能需要等待设备重新连接。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            commandError?.let { ErrorNotice(it) }
                        }
                    }
                } else {
                    item { SectionTitle("数据足迹", "最近记录与 24 小时趋势") }
                    if (chartKeys.isEmpty()) {
                        item {
                            Text(
                                when {
                                    loading -> "正在读取数据…"
                                    error != null -> "设备数据未能加载"
                                    history.isEmpty() -> "暂未收到数据，等待设备第一次上报。"
                                    else -> "暂无可绘制的数值数据。"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        item {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                chartKeys.forEach { key ->
                                    FilterChip(
                                        selected = key == selectedKey,
                                        onClick = { historyKey = key },
                                        label = { Text(key) },
                                        modifier = Modifier.semantics { contentDescription = "查看 $key 趋势" }
                                    )
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ChartSectionCaption("近期状态", "最近 100 条原始记录")
                                if (selectedRecent != null) {
                                    HistoryChart(selectedRecent, HistoryChartMode.Recent)
                                } else {
                                    EmptyChartNotice("最近记录暂无可绘制的数值")
                                }
                                Spacer(Modifier.height(10.dp))
                                ChartSectionCaption("长期状态", "近 24 小时趋势")
                                if (selectedLongTerm != null) {
                                    HistoryChart(selectedLongTerm, HistoryChartMode.LongTerm)
                                } else {
                                    EmptyChartNotice("近 24 小时暂无可绘制的数值")
                                }
                            }
                        }
                    }
                    item { SectionTitle("最近的指令") }
                    if (commands.isEmpty()) item { Text(if (loading) "正在读取指令…" else if (error != null) "指令记录未能加载" else "还没有任何指令。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(commands.take(VISIBLE_COMMAND_LIMIT), key = { "command-${it.id}" }) { CommandCard(it) }
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
private fun QuickCommandTile(
    command: QuickCommand,
    editing: Boolean,
    loading: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onSend: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val title = commandTitle(command.type)
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(40.dp), shape = CircleShape, color = colors.surface, contentColor = colors.primary) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
                    else Icon(commandIcon(command.type), null, Modifier.size(20.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (loading) "提交中…" else title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(payloadSummary(command.payload), style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Box(modifier) {
        if (editing) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = colors.surface,
                contentColor = colors.onSurface,
                border = BorderStroke(1.dp, colors.outlineVariant),
                content = content
            )
            IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Outlined.RemoveCircleOutline, "移除快捷指令 $title", tint = colors.error)
            }
        } else {
            Surface(
                onClick = onSend,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().alpha(if (enabled || loading) 1f else 0.55f),
                shape = MaterialTheme.shapes.large,
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                content = content
            )
        }
    }
}

@Composable
private fun SensorCard(dataKey: String, point: DataPoint?, loading: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = MaterialTheme.shapes.large, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBadge(Icons.Outlined.Sensors, size = 30.dp)
                Text(dataKey, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(point?.dataValue ?: "—", style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(12.dp), tint = colors.onSurfaceVariant)
                Text(if (point != null) displayTime(point.timestamp) else if (loading) "读取中" else "暂无读数", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

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
    val timedOut = commandTimedOut(command.status, command.createdAt, Instant.now())
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        command.status == "failed" || timedOut -> colors.errorContainer to colors.onErrorContainer
        command.status == "executed" -> colors.primaryContainer to colors.onPrimaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(commandIcon(command.commandType), size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(commandTitle(command.commandType), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(displayTime(command.createdAt), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = container, contentColor = content) {
                Text(
                    if (timedOut) "超时失效" else commandStatus(command.status),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private enum class HistoryChartMode { Recent, LongTerm }

@Composable
private fun HistoryChart(series: HistoryChartSeries, mode: HistoryChartMode) {
    val points = series.points
    val minimum = points.minOf { it.value }
    val maximum = points.maxOf { it.value }
    val positions = remember(points) {
        val elapsed = points.map { Duration.between(points.first().time, it.time).let { duration -> duration.seconds + duration.nano / 1e9 } }
        points.mapIndexed { index, point ->
            Offset(chartFraction(elapsed[index], 0.0, elapsed.last()), chartFraction(point.value, minimum, maximum))
        }
    }
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val ticks = if (minimum == maximum) listOf(minimum) else listOf(maximum, minimum / 2 + maximum / 2, minimum)
    val labels = ticks.map { textMeasurer.measure(chartNumber(it), labelStyle) }
    val inset = with(LocalDensity.current) { 12.dp.toPx() }
    val left = labels.maxOf { it.size.width }.toFloat() + inset

    Surface(shape = MaterialTheme.shapes.large, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mode == HistoryChartMode.LongTerm) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            series.dataKey,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "15 分钟均值 · 末点最新",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            chartNumber(points.last().value),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "最新",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    series.dataKey,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Canvas(
                Modifier.fillMaxWidth().height(200.dp)
                    .semantics {
                        contentDescription = if (mode == HistoryChartMode.LongTerm) {
                            "${series.dataKey} 历史折线图"
                        } else {
                            "${series.dataKey} 近期状态折线图"
                        }
                    }
            ) {
                val plotWidth = (size.width - left - inset).coerceAtLeast(1f)
                val plotHeight = (size.height - inset * 2).coerceAtLeast(1f)
                val bottom = inset + plotHeight
                val coordinates = positions.map { Offset(left + it.x * plotWidth, inset + (1 - it.y) * plotHeight) }
                ticks.forEachIndexed { index, tick ->
                    val y = inset + (1 - chartFraction(tick, minimum, maximum)) * plotHeight
                    drawLine(colors.outlineVariant, Offset(left, y), Offset(left + plotWidth, y), 1.dp.toPx())
                    drawText(labels[index], topLeft = Offset(0f, y - labels[index].size.height / 2f))
                }
                if (coordinates.size > 1) {
                    val area = Path().apply {
                        moveTo(coordinates.first().x, bottom)
                        coordinates.forEach { lineTo(it.x, it.y) }
                        lineTo(coordinates.last().x, bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.16f), colors.primary.copy(alpha = 0.01f))))
                    val line = Path().apply {
                        moveTo(coordinates.first().x, coordinates.first().y)
                        coordinates.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(line, colors.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    if (mode == HistoryChartMode.Recent) {
                        coordinates.forEach { drawCircle(colors.primary, 3.dp.toPx(), it) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(displayTime(points.first().record.timestamp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (mode == HistoryChartMode.LongTerm && points.size > 2) {
                    Text(
                        displayTime(points[points.size / 2].record.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
                if (points.first().time != points.last().time) {
                    Text(displayTime(points.last().record.timestamp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
            if (points.size == 1) {
                Text(
                    if (mode == HistoryChartMode.LongTerm) {
                        "近 24 小时只有一个数据点，收到更多上报后将显示趋势。"
                    } else {
                        "仅有一个数据点，收到更多上报后将显示趋势。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChartSectionCaption(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyChartNotice(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
