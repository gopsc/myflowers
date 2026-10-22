package com.myflower.app.ui.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myflower.app.data.DataRepository
import com.myflower.app.data.model.Device
import com.myflower.app.ui.theme.*
import com.myflower.app.util.displayTime
import com.myflower.app.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun DeviceListScreen(repository: DataRepository, onDeviceClick: (Device) -> Unit, onLogout: () -> Unit) {
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var currentUserId by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Device?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh) {
        loading = true
        error = null
        currentUserId = null
        try {
            val user = repository.getCurrentUser()
            val fetchedDevices = repository.getDevices()
            devices = fetchedDevices
            currentUserId = user.id
            loaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = userMessage(e)
        } finally {
            loading = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.semantics { contentDescription = "添加花盆" },
                onClick = { showAdd = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("添加花盆") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BrandHeader(Modifier.weight(1f))
                        IconButton(onClick = { refresh++ }, enabled = !loading) { Icon(Icons.Outlined.Refresh, "刷新花盆") }
                        IconButton(onClick = { showLogout = true }) { Icon(Icons.AutoMirrored.Outlined.Logout, "退出登录") }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("YOUR LITTLE GARDEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("我的花园", style = MaterialTheme.typography.headlineLarge)
                        Text("把日常的照料，交给一点点科技。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(start = 22.dp, top = 18.dp, bottom = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("让每一片绿意\n都有回应", style = MaterialTheme.typography.titleLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                                    GardenCount(if (loaded) devices.size.toString() else "—", "全部花盆")
                                    GardenCount(if (loaded) devices.count { it.isOnline }.toString() else "—", "当前在线")
                                }
                            }
                            PlantIllustration(Modifier.width(132.dp).height(150.dp))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("全部", "在线", "离线").forEachIndexed { index, label ->
                            FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) })
                        }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                error?.let { message ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ErrorNotice(message)
                            TextButton(onClick = { refresh++ }, enabled = !loading) { Text("重新加载") }
                        }
                    }
                }
                val visible = devices.filter { filter == 0 || (filter == 1 && it.isOnline) || (filter == 2 && !it.isOnline) }
                if (!loading && error == null && visible.isEmpty()) {
                    item {
                        EmptyGarden(
                            if (devices.isEmpty()) "你的花园，从这里开始" else "暂无${if (filter == 1) "在线" else "离线"}花盆",
                            if (devices.isEmpty()) "添加第一个智能花盆，开始记录它的每一天。" else "切换分类或刷新，查看最新设备状态。"
                        )
                    }
                }
                items(visible, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        isOwner = currentUserId?.let { device.userId == it },
                        removalEnabled = !loading && currentUserId != null,
                        onClick = { onDeviceClick(device) },
                        onRemove = { deleting = device; deleteError = null }
                    )
                }
                if (visible.isNotEmpty()) item {
                    Text("细心照料，慢慢生长。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }

    if (showAdd) AddDeviceDialog(
        repository = repository,
        onDismiss = { showAdd = false },
        onCreated = {
            devices = devices + it
            filter = 0
            showAdd = false
            scope.launch { snackbar.showSnackbar("已添加花盆。设备接入后即可查看数据。") }
        },
        onBound = {
            filter = 0
            showAdd = false
            refresh++
            scope.launch { snackbar.showSnackbar("花盆已绑定") }
        }
    )
    deleting?.let { device ->
        val isOwner = device.userId == currentUserId
        val action = if (isOwner) "删除" else "解绑"
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) deleting = null },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Icon(if (isOwner) Icons.Outlined.DeleteOutline else Icons.Outlined.LinkOff, null) },
            title = { Text("${action}这个花盆？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (isOwner) "将删除「${device.deviceName}」。此操作无法在 App 中撤销。"
                        else "将解除与「${device.deviceName}」的绑定，你将无法再查看或控制它。设备和数据仍由所有者保留。")
                    deleteError?.let { ErrorNotice(it) }
                }
            },
            confirmButton = {
                TextButton(enabled = !deleteBusy && !loading && currentUserId != null, onClick = {
                    deleteBusy = true
                    deleteError = null
                    scope.launch {
                        try {
                            if (isOwner) repository.deleteDevice(device.id) else repository.unbindDevice(device.id)
                            devices = devices.filterNot { it.id == device.id }
                            deleting = null
                            scope.launch { snackbar.showSnackbar("花盆已$action") }
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) {
                            deleteError = if (e is HttpException && e.code() in listOf(400, 403, 404))
                                "无法${action}此花盆，请取消并刷新列表，确认设备归属与绑定关系。"
                            else userMessage(e)
                        } finally { deleteBusy = false }
                    }
                }) { Text(if (deleteBusy) "正在${action}…" else "确认$action", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }, enabled = !deleteBusy) { Text("保留花盆") } }
        )
    }
    if (showLogout) AlertDialog(
        onDismissRequest = { showLogout = false },
        title = { Text("退出当前账号？") },
        text = { Text("花盆和数据会保留在服务器，下次登录即可查看。") },
        confirmButton = { TextButton(onClick = onLogout) { Text("退出登录") } },
        dismissButton = { TextButton(onClick = { showLogout = false }) { Text("取消") } }
    )
}

@Composable
private fun GardenCount(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DeviceCard(device: Device, isOwner: Boolean?, removalEnabled: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Outlined.Spa, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.deviceName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val ownership = when (isOwner) { true -> "自有花盆"; false -> "共享花盆"; null -> "归属待确认" }
                    Text("$ownership · #${device.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(device.isOnline)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("最近连接", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(displayTime(device.lastSeen), style = MaterialTheme.typography.bodySmall)
                }
                if (isOwner != null) {
                    IconButton(onClick = onRemove, enabled = removalEnabled) {
                        Icon(
                            if (isOwner) Icons.Outlined.DeleteOutline else Icons.Outlined.LinkOff,
                            "${if (isOwner) "删除" else "解绑"} ${device.deviceName}"
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AddDeviceDialog(repository: DataRepository, onDismiss: () -> Unit, onCreated: (Device) -> Unit, onBound: () -> Unit) {
    var binding by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var deviceKey by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (binding) "让花盆加入你的花园" else "给绿意一个名字") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !binding, enabled = !busy, onClick = { binding = false; error = null }, label = { Text("创建花盆") })
                    FilterChip(selected = binding, enabled = !busy, onClick = { binding = true; error = null }, label = { Text("绑定已有") })
                }
                if (binding) {
                    Text("输入设备所有者提供的 device_key，绑定后即可查看数据和发送养护指令。", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = deviceKey,
                        onValueChange = { deviceKey = it; error = null },
                        label = { Text("设备密钥") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("设备密钥可授予设备访问权限，请勿公开分享。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("为你的智能花盆创建一个设备记录。", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("花盆名称") }, placeholder = { Text("例如：窗边的小绿") }, singleLine = true, enabled = !busy, isError = name.length > 100, supportingText = { Text("${name.length}/100") }, modifier = Modifier.fillMaxWidth())
                }
                error?.let { ErrorNotice(it) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && (if (binding) deviceKey.isNotBlank() else name.trim().length in 1..100), onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        if (binding) {
                            repository.bindDevice(deviceKey.trim())
                            onBound()
                        } else {
                            onCreated(repository.createDevice(name.trim()))
                        }
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) {
                        error = if (binding && e is HttpException) when (e.code()) {
                            400, 409 -> "无法绑定：设备可能已绑定，或属于当前账号。"
                            403 -> "当前账号没有绑定此设备的权限。"
                            404 -> "没有找到设备，请检查设备密钥。"
                            else -> userMessage(e)
                        } else userMessage(e)
                    } finally { busy = false }
                }
            }) { Text(if (binding) { if (busy) "绑定中…" else "绑定花盆" } else { if (busy) "添加中…" else "添加花盆" }) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } }
    )
}
