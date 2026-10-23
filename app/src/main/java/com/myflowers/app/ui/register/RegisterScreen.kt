package com.myflowers.app.ui.register

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myflowers.app.data.DataRepository
import com.myflowers.app.ui.theme.ErrorNotice
import com.myflowers.app.ui.theme.PlantIllustration
import com.myflowers.app.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    repository: DataRepository,
    onRegisterSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var accountCreated by rememberSaveable { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val canSubmit = !isLoading && username.isNotBlank() && password.isNotBlank() &&
        (accountCreated || confirmPassword.isNotBlank())

    fun submit() {
        if (isLoading) return
        val account = username.trim()
        val secret = password
        errorMessage = when {
            !accountCreated && account.length !in 3..50 -> "用户名需要 3–50 个字符，请调整后再试。"
            !accountCreated && secret.length < 6 -> "请设置至少 6 位密码，让账户更安心。"
            !accountCreated && secret != confirmPassword -> "两次输入的密码不一致，请再确认一下。"
            secret.isBlank() -> "请输入密码后继续。"
            else -> null
        }
        if (errorMessage != null) return
        focusManager.clearFocus()
        isLoading = true
        scope.launch {
            try {
                if (!accountCreated) {
                    repository.register(account, secret)
                    accountCreated = true
                    username = account
                    confirmPassword = ""
                }
                repository.login(account, secret)
                onRegisterSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = userMessage(error)
            } finally {
                isLoading = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(
                    onClick = onNavigateBack,
                    enabled = !isLoading,
                    modifier = Modifier.align(Alignment.Start).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("返回登录")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "MyFlowers",
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "GROW WITH CARE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                PlantIllustration(modifier = Modifier.size(150.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    if (accountCreated) "你的账户，已经准备好了" else "从一个账户，开始",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (accountCreated) "账户已创建，登录后即可开始管理设备。" else "创建账户，把你的设备收进同一个清单。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            if (accountCreated) "登录新账户" else "创建我的账户",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (accountCreated) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text(
                                        "注册已完成，无需重复注册。若登录未成功，可重试或返回登录页。",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it; errorMessage = null },
                            label = { Text("用户名") },
                            supportingText = if (!accountCreated) ({ Text("3–50 个字符，首尾空格会自动去除") }) else null,
                            leadingIcon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            enabled = !isLoading && !accountCreated,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() })
                        )
                        RegistrationPasswordField(
                            value = password,
                            onValueChange = { password = it; errorMessage = null },
                            label = "密码",
                            hint = if (accountCreated) null else "至少 6 位，请使用不易猜到的密码",
                            enabled = !isLoading,
                            modifier = Modifier.focusRequester(passwordFocus),
                            imeAction = if (accountCreated) ImeAction.Done else ImeAction.Next,
                            keyboardActions = KeyboardActions(
                                onNext = { confirmFocus.requestFocus() },
                                onDone = { submit() }
                            )
                        )
                        if (!accountCreated) {
                            RegistrationPasswordField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; errorMessage = null },
                                label = "确认密码",
                                hint = null,
                                enabled = !isLoading,
                                modifier = Modifier.focusRequester(confirmFocus),
                                imeAction = ImeAction.Done,
                                keyboardActions = KeyboardActions(onDone = { submit() })
                            )
                        }
                        errorMessage?.let { ErrorNotice(message = it, modifier = Modifier.fillMaxWidth()) }
                        Button(
                            onClick = { submit() },
                            enabled = canSubmit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                when {
                                    isLoading && accountCreated -> "正在登录…"
                                    isLoading -> "正在创建…"
                                    accountCreated -> "重试登录"
                                    else -> "创建账户，开始使用"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onNavigateBack, enabled = !isLoading, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (accountCreated) "稍后再来，返回登录" else "已有账户？ 直接登录", textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RegistrationPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String?,
    enabled: Boolean,
    modifier: Modifier,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = hint?.let { text -> { Text(text) } },
        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(
                onClick = { visible = !visible },
                enabled = enabled,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "隐藏${label}" else "显示${label}"
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = keyboardActions
    )
}
