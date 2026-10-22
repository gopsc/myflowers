package com.myflower.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myflower.app.data.DataRepository
import com.myflower.app.data.local.TokenManager
import com.myflower.app.data.model.*
import com.myflower.app.data.remote.ApiService
import com.myflower.app.theme.MyFlowerTheme
import com.myflower.app.ui.device.DeviceDetailScreen
import com.myflower.app.ui.device.DeviceListScreen
import com.myflower.app.ui.login.LoginScreen
import com.myflower.app.ui.register.RegisterScreen
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiRedesignTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeApiService()
    private val repository = DataRepository(api)

    @Before
    fun initializeTokens() {
        TokenManager.init(ApplicationProvider.getApplicationContext())
        TokenManager.clear()
    }

    @After
    fun clearTokens() {
        TokenManager.clear()
    }

    @Test
    fun login_emptyDisabled_thenFakeSuccess_andGolden() {
        val successes = AtomicInteger()
        screen { LoginScreen(repository, { successes.incrementAndGet() }, {}) }
        compose.onNodeWithText("走进我的花园").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("MyFlower").performScrollTo()
        screenshot("login-redesign.png")

        field("用户名").performScrollTo().performTextInput("  gardener  ")
        closeSoftKeyboard()
        compose.onNodeWithText("走进我的花园").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(api.loginRequests.isEmpty()) }
        field("密码").performScrollTo().performTextInput("secret123")
        closeSoftKeyboard()
        compose.onNodeWithText("走进我的花园").performScrollTo().assertIsEnabled().performClick()

        compose.waitUntil(10_000) { successes.get() == 1 }
        compose.runOnIdle {
            assertEquals(listOf(LoginRequest("gardener", "secret123")), api.loginRequests)
            assertEquals("fake-ui-token", TokenManager.token)
            assertEquals("gardener", TokenManager.username)
        }
    }

    @Test
    fun registration_passwordMismatch_neverCallsApi() {
        val successes = AtomicInteger()
        screen { RegisterScreen(repository, { successes.incrementAndGet() }, {}) }
        field("用户名").performScrollTo().performTextInput("gardener")
        field("密码").performScrollTo().performTextInput("secret123")
        field("确认密码").performScrollTo().performTextInput("different123")
        closeSoftKeyboard()
        compose.onNodeWithText("创建账户，开启养护").performScrollTo().performClick()
        awaitNode(hasText("两次输入的密码不一致，请再确认一下。"))
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(api.registerRequests.isEmpty())
            assertTrue(api.loginRequests.isEmpty())
            assertEquals(0, successes.get())
        }
    }

    @Test
    fun garden_errorRetry_recoversPopulatedList_andGolden() {
        api.deviceFailuresRemaining = 1
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasText(NETWORK_ERROR)).assertIsDisplayed()
        lazyNode(hasText("重新加载")).performClick()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasText(PLANT_NAME)).assertIsDisplayed()
        compose.onNodeWithText(NETWORK_ERROR).assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, api.getDevicesCalls) }
        screenshot("garden-redesign.png")
    }

    @Test
    fun garden_offlineFilter_hasEmptyState_andAllRestoresDevice() {
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        // Status badges also say 在线/离线; select only clickable filter chips.
        lazyNode(hasText("离线") and hasClickAction()).performClick()
        lazyNode(hasText("暂无离线花盆")).assertIsDisplayed()
        compose.onNodeWithText(PLANT_NAME).assertDoesNotExist()
        lazyNode(hasText("全部") and hasClickAction()).performClick()
        lazyNode(hasText(PLANT_NAME)).assertIsDisplayed()
    }

    @Test
    fun garden_addNameValidation_andTrimmedCreation() {
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        compose.onNodeWithContentDescription("添加花盆").performClick()
        val confirm = hasText("添加花盆") and hasClickAction() and hasAnyAncestor(isDialog())
        awaitNode(confirm).assertIsNotEnabled()
        field("花盆名称").performTextInput("   ")
        compose.onNode(confirm).assertIsNotEnabled()
        field("花盆名称").performTextReplacement("a".repeat(101))
        compose.onNode(confirm).assertIsNotEnabled()
        compose.runOnIdle { assertTrue(api.createRequests.isEmpty()) }
        field("花盆名称").performTextReplacement("  阳台薄荷  ")
        closeSoftKeyboard()
        compose.onNode(confirm).assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
        }
        lazyNode(hasText("阳台薄荷")).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(CreateDeviceRequest("阳台薄荷")), api.createRequests)
            assertEquals(2, api.devices.size)
        }
    }

    @Test
    fun garden_deleteRequiresConfirmation_cancelPreserves_confirmRemoves() {
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasContentDescription("删除 $PLANT_NAME")).performClick()
        awaitNode(hasText("删除这个花盆？")).assertIsDisplayed()
        compose.onNodeWithText("保留花盆").performClick()
        lazyNode(hasText(PLANT_NAME)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(api.deletedIds.isEmpty()) }
        lazyNode(hasContentDescription("删除 $PLANT_NAME")).performClick()
        awaitNode(hasText("确认删除")).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
        }
        lazyNode(hasText("你的花园，从这里开始")).assertIsDisplayed()
        compose.onNodeWithText(PLANT_NAME).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(1), api.deletedIds)
            assertTrue(api.devices.isEmpty())
        }
    }

    @Test
    fun detail_dataAndExactlyTwoCareActions_water100Pending_andGolden() {
        detail()
        lazyNode(hasText("每次 100 ml")).assertIsDisplayed()
        compose.onNode(hasText("浇水") and hasClickAction()).assertIsEnabled()
        compose.onNode(hasText("开灯") and hasClickAction()).assertIsEnabled()
        // Exclude navigation/refresh and the two tabs: only the two care actions remain.
        compose.onAllNodes(
            hasClickAction() and !hasContentDescription("返回花园") and
                !hasContentDescription("刷新数据") and !hasText("生长概览") and !hasText("养护记录")
        ).assertCountEquals(2)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        screenshot("detail-redesign.png")

        listOf("土壤湿度" to "42", "环境温度" to "23.5", "空气湿度" to "61", "光照强度" to "820")
            .forEach { (label, value) ->
                lazyNode(hasText(label)).assertIsDisplayed()
                lazyNode(hasText(value)).assertIsDisplayed()
            }
        lazyNode(hasText("浇水") and hasClickAction()).performClick()
        awaitNode(hasText("浇水指令已提交，等待执行"))
        compose.runOnIdle {
            assertEquals(listOf(CreateCommandRequest(1, "water", mapOf("amount" to 100))), api.commandRequests)
            assertEquals("pending", api.commands.single().status)
        }
        lazyNode(hasText("养护记录") and hasClickAction()).performClick()
        lazyNode(hasText("等待执行")).assertIsDisplayed()
        compose.onNodeWithText("已执行").assertDoesNotExist()
        compose.onNodeWithText("已下发").assertDoesNotExist()
    }

    @Test
    fun detail_lightTogglesRequestedState_notClaimedHardwareState() {
        detail()
        lazyNode(hasText("灯光状态未知")).assertIsDisplayed()
        lazyNode(hasText("开灯") and hasClickAction()).performClick()
        awaitNode(hasText("关灯") and hasClickAction() and isEnabled())
        lazyNode(hasText("上次目标：开启")).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(CreateCommandRequest(1, "turn_on_light", emptyMap())), api.commandRequests)
            assertEquals("pending", api.commands.single().status)
        }
        lazyNode(hasText("关灯") and hasClickAction()).performClick()
        awaitNode(hasText("开灯") and hasClickAction() and isEnabled())
        lazyNode(hasText("上次目标：关闭")).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("turn_on_light", "turn_off_light"), api.commandRequests.map { it.commandType })
            assertTrue(api.commandRequests.all { it.deviceId == 1 && it.payload.isEmpty() })
            assertTrue(api.commands.all { it.status == "pending" })
        }
    }

    @Test
    fun detail_commandError_showsFeedback_andDoesNotInventExecution() {
        api.commandFailuresRemaining = 1
        detail()
        lazyNode(hasText("开灯") and hasClickAction()).performClick()
        awaitNode(hasText(NETWORK_ERROR)).performScrollTo().assertIsDisplayed()
        lazyNode(hasText("灯光状态未知")).assertIsDisplayed()
        compose.onNode(hasText("开灯") and hasClickAction()).assertIsEnabled()
        compose.runOnIdle {
            assertEquals(listOf(CreateCommandRequest(1, "turn_on_light", emptyMap())), api.commandRequests)
            assertTrue(api.commands.isEmpty())
        }
        lazyNode(hasText("养护记录") and hasClickAction()).performClick()
        lazyNode(hasText("还没有养护指令。")).assertIsDisplayed()
        compose.onNodeWithText("已执行").assertDoesNotExist()
        compose.onNodeWithText("等待执行").assertDoesNotExist()
    }

    @Test
    fun garden_bindBlankDisabled_trimsNonUuidKey_refreshesSharedCard_andGoldens() {
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasText("自有花盆", substring = true)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("user", "devices"), api.loadCalls) }
        openBindingDialog()
        compose.onNode(bindConfirm).assertIsNotEnabled()
        field("设备密钥").performTextInput("   ")
        compose.onNode(bindConfirm).assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(api.bindRequests.isEmpty())
            assertTrue(api.createRequests.isEmpty())
        }
        field("设备密钥").performTextReplacement("  shared-key-not-a-uuid  ")
        closeSoftKeyboard()
        compose.onNode(bindConfirm).assertIsEnabled()
        screenshot("bind-device-dialog.png")
        compose.onNode(bindConfirm).performClick()
        awaitNoDialog()
        lazyNode(hasContentDescription("刷新花盆"))
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasText(SHARED_NAME)).assertIsDisplayed()
        lazyNode(hasText("共享花盆", substring = true)).assertIsDisplayed()
        lazyNode(hasContentDescription("解绑 $SHARED_NAME")).assertIsEnabled()
        compose.onNodeWithContentDescription("删除 $SHARED_NAME").assertDoesNotExist()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("花盆已绑定")).fetchSemanticsNodes().isEmpty() }
        screenshot("garden-shared-device.png")
        lazyNode(hasContentDescription("解绑 $SHARED_NAME")).performClick()
        awaitNode(hasText("解绑这个花盆？")).assertIsDisplayed()
        screenshot("unbind-device-dialog.png")
        compose.onNodeWithText("保留花盆").performClick()
        compose.runOnIdle {
            assertEquals(listOf(BindDeviceRequest("shared-key-not-a-uuid")), api.bindRequests)
            assertTrue(api.createRequests.isEmpty())
            assertEquals(2, api.getDevicesCalls)
            assertEquals(listOf("user", "devices", "user", "devices"), api.loadCalls)
            assertEquals(OTHER_USER_ID, api.devices.single { it.deviceName == SHARED_NAME }.userId)
        }
    }

    @Test
    fun garden_bind404_keepsDialogAndKey_allowsRetry() {
        bindingFailureThenRetry(404, "没有找到设备，请检查设备密钥。")
    }

    @Test
    fun garden_bind400_keepsDialogAndKey_allowsRetry() {
        bindingFailureThenRetry(400, "无法绑定：设备可能已绑定，或属于当前账号。")
    }

    @Test
    fun garden_bind409_keepsDialogAndKey_allowsRetry() {
        bindingFailureThenRetry(409, "无法绑定：设备可能已绑定，或属于当前账号。")
    }

    @Test
    fun garden_bind403_keepsDialogAndKey_allowsRetry() {
        bindingFailureThenRetry(403, "当前账号没有绑定此设备的权限。")
    }

    @Test
    fun garden_bindNetworkFailure_usesUserMessage_allowsRetry() {
        bindingFailureThenRetry(null, NETWORK_ERROR)
    }

    @Test
    fun garden_sharedUnbind_cancelPreserves_confirmUnbinds_neverDeletes() {
        useSharedDevice()
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasText("共享花盆", substring = true)).assertIsDisplayed()
        compose.onNodeWithContentDescription("删除 $PLANT_NAME").assertDoesNotExist()
        lazyNode(hasContentDescription("解绑 $PLANT_NAME")).performClick()
        awaitNode(hasText("解绑这个花盆？")).assertIsDisplayed()
        compose.onNodeWithText("确认删除").assertDoesNotExist()
        compose.onNodeWithText("保留花盆").performClick()
        awaitNoDialog()
        lazyNode(hasText(PLANT_NAME)).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(api.unboundIds.isEmpty())
            assertTrue(api.deletedIds.isEmpty())
            assertEquals(1, api.devices.size)
        }
        lazyNode(hasContentDescription("解绑 $PLANT_NAME")).performClick()
        awaitNode(hasText("确认解绑") and isEnabled()).performClick()
        awaitNoDialog()
        awaitNode(hasText("花盆已解绑")).assertIsDisplayed()
        lazyNode(hasText("你的花园，从这里开始")).assertIsDisplayed()
        compose.onNodeWithText(PLANT_NAME).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(1), api.unboundIds)
            assertTrue(api.deletedIds.isEmpty())
            assertTrue(api.devices.isEmpty())
        }
    }

    @Test
    fun garden_unbindNetworkFailure_keepsDialog_allowsRetry_neverDeletes() {
        useSharedDevice()
        api.unbindFailuresRemaining = 1
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasContentDescription("解绑 $PLANT_NAME")).performClick()
        awaitNode(hasText("确认解绑") and isEnabled()).performClick()
        awaitNode(hasText(NETWORK_ERROR) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("解绑这个花盆？").assertIsDisplayed()
        compose.onNodeWithText("花盆已解绑").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(1), api.unboundIds)
            assertEquals(1, api.devices.size)
            assertTrue(api.deletedIds.isEmpty())
        }
        compose.onNode(hasText("确认解绑") and isEnabled()).performClick()
        awaitNoDialog()
        awaitNode(hasText("花盆已解绑")).assertIsDisplayed()
        lazyNode(hasText("你的花园，从这里开始")).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(1, 1), api.unboundIds)
            assertTrue(api.deletedIds.isEmpty())
            assertTrue(api.devices.isEmpty())
        }
    }

    @Test
    fun garden_userLoadFailure_neverLoadsDevicesOrEnablesRemoval_retryRecovers() {
        api.userFailuresRemaining = 1
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasText(NETWORK_ERROR)).assertIsDisplayed()
        assertNoEnabledRemoval()
        compose.runOnIdle {
            assertEquals(1, api.getCurrentUserCalls)
            assertEquals(0, api.getDevicesCalls)
            assertTrue(api.deletedIds.isEmpty())
            assertTrue(api.unboundIds.isEmpty())
        }
        lazyNode(hasText("重新加载")).performClick()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasContentDescription("删除 $PLANT_NAME")).assertIsEnabled()
        lazyNode(hasText("自有花盆", substring = true)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("user", "user", "devices"), api.loadCalls) }
    }

    @Test
    fun garden_userRefreshFailure_doesNotLeaveStaleRemovalEnabled() {
        useSharedDevice()
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasContentDescription("解绑 $PLANT_NAME")).assertIsEnabled()
        compose.runOnIdle { api.userFailuresRemaining = 1 }
        lazyNode(hasContentDescription("刷新花盆")).performClick()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasText(NETWORK_ERROR)).assertIsDisplayed()
        // Bring any retained stale card into the viewport before checking its actions.
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        assertNoEnabledRemoval()
        compose.runOnIdle {
            assertEquals(2, api.getCurrentUserCalls)
            assertEquals(1, api.getDevicesCalls)
            assertTrue(api.deletedIds.isEmpty())
            assertTrue(api.unboundIds.isEmpty())
        }
    }

    @Test
    fun garden_removalDisabledWhileUserOrListLoading_andAfterListFailure() {
        useSharedDevice()
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasContentDescription("解绑 $PLANT_NAME")).assertIsEnabled()
        val userGate = CompletableDeferred<Unit>()
        val devicesGate = CompletableDeferred<Unit>()
        compose.runOnIdle {
            api.userLoadGate = userGate
            api.devicesLoadGate = devicesGate
            api.deviceFailuresRemaining = 1
        }
        try {
            lazyNode(hasContentDescription("刷新花盆")).performClick()
            compose.waitUntil(10_000) { api.getCurrentUserCalls == 2 }
            compose.runOnIdle { assertEquals(1, api.getDevicesCalls) }
            compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
            assertNoEnabledRemoval()

            compose.runOnIdle { userGate.complete(Unit) }
            compose.waitUntil(10_000) { api.getDevicesCalls == 2 }
            compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
            assertNoEnabledRemoval()

            compose.runOnIdle { devicesGate.complete(Unit) }
            lazyNode(hasContentDescription("刷新花盆"))
            awaitNode(hasContentDescription("刷新花盆") and isEnabled())
            lazyNode(hasText(NETWORK_ERROR)).assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
            assertNoEnabledRemoval()
            compose.runOnIdle {
                assertTrue(api.unboundIds.isEmpty())
                assertTrue(api.deletedIds.isEmpty())
            }
            lazyNode(hasText("重新加载")).performClick()
            awaitNode(hasContentDescription("刷新花盆") and isEnabled())
            lazyNode(hasContentDescription("解绑 $PLANT_NAME")).assertIsEnabled()
        } finally {
            userGate.complete(Unit)
            devicesGate.complete(Unit)
        }
    }

    @Test
    fun garden_bindSuccess_listRefreshFailure_retriesListWithoutBindingAgain() {
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        openBindingDialog()
        field("设备密钥").performTextInput("existing-key")
        closeSoftKeyboard()
        compose.runOnIdle { api.deviceFailuresRemaining = 1 }
        compose.onNode(bindConfirm).performClick()
        awaitNoDialog()
        lazyNode(hasContentDescription("刷新花盆"))
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasText(NETWORK_ERROR)).assertIsDisplayed()
        compose.onNodeWithText("设备密钥").assertDoesNotExist()
        compose.onNode(bindConfirm).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(BindDeviceRequest("existing-key")), api.bindRequests)
            assertEquals(2, api.getDevicesCalls)
            assertEquals(2, api.devices.size)
        }
        lazyNode(hasText("重新加载")).performClick()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasText(SHARED_NAME)).assertIsDisplayed()
        lazyNode(hasText("共享花盆", substring = true)).assertIsDisplayed()
        compose.onNodeWithText(NETWORK_ERROR).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(3, api.getDevicesCalls)
            assertEquals(listOf(BindDeviceRequest("existing-key")), api.bindRequests)
            assertTrue(api.createRequests.isEmpty())
        }
    }

    @Test
    fun detail_sharedDevice_canWaterAndTurnOnLight_usingOnlyFakeCommands() {
        useSharedDevice()
        detail()
        lazyNode(hasText("浇水") and hasClickAction()).assertIsEnabled().performClick()
        awaitNode(hasText("浇水指令已提交，等待执行"))
        lazyNode(hasText("开灯") and hasClickAction()).assertIsEnabled().performClick()
        awaitNode(hasText("关灯") and hasClickAction() and isEnabled())
        compose.runOnIdle {
            assertEquals(OTHER_USER_ID, api.devices.single().userId)
            assertEquals(
                listOf(
                    CreateCommandRequest(1, "water", mapOf("amount" to 100)),
                    CreateCommandRequest(1, "turn_on_light", emptyMap())
                ),
                api.commandRequests
            )
            assertEquals(2, api.commands.size)
            assertTrue(api.commands.all { it.status == "pending" })
            assertTrue(api.deletedIds.isEmpty())
            assertTrue(api.unboundIds.isEmpty())
        }
    }

    private val bindConfirm = hasText("绑定花盆") and hasClickAction() and hasAnyAncestor(isDialog())

    private fun openBindingDialog() {
        compose.onNodeWithContentDescription("添加花盆").performClick()
        awaitNode(hasText("创建花盆") and hasClickAction()).assertIsSelected()
        field("花盆名称").assertExists()
        compose.onNode(hasText("绑定已有") and hasClickAction()).performClick()
        compose.onNode(hasText("绑定已有") and hasClickAction()).assertIsSelected()
        field("设备密钥").assertExists()
        field("花盆名称").assertDoesNotExist()
    }

    private fun bindingFailureThenRetry(status: Int?, message: String) {
        api.bindFailuresRemaining = 1
        api.bindError = status?.let { HttpException(Response.error<Any>(it, "{}".toResponseBody())) }
            ?: IOException("Simulated bind failure")
        garden()
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        openBindingDialog()
        field("设备密钥").performTextInput("retry-key")
        closeSoftKeyboard()
        compose.onNode(bindConfirm).performClick()
        awaitNode(hasText(message) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        field("设备密钥").assertTextContains("retry-key")
        compose.onNode(bindConfirm).assertIsEnabled()
        compose.runOnIdle {
            assertEquals(listOf(BindDeviceRequest("retry-key")), api.bindRequests)
            assertEquals(1, api.getDevicesCalls)
            assertEquals(1, api.devices.size)
            assertTrue(api.createRequests.isEmpty())
        }
        compose.onNode(bindConfirm).performClick()
        awaitNoDialog()
        lazyNode(hasContentDescription("刷新花盆"))
        awaitNode(hasContentDescription("刷新花盆") and isEnabled())
        lazyNode(hasText(SHARED_NAME)).assertIsDisplayed()
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(List(2) { BindDeviceRequest("retry-key") }, api.bindRequests)
            assertEquals(2, api.getDevicesCalls)
            assertTrue(api.createRequests.isEmpty())
        }
    }

    private fun useSharedDevice() {
        api.devices[0] = api.devices.single().copy(userId = OTHER_USER_ID)
    }

    private fun assertNoEnabledRemoval() {
        val removal = hasContentDescription("删除 $PLANT_NAME") or hasContentDescription("解绑 $PLANT_NAME")
        compose.onAllNodes(removal and hasClickAction() and isEnabled()).assertCountEquals(0)
    }

    private fun awaitNoDialog() {
        compose.waitUntil(10_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }

    private fun screen(content: @Composable () -> Unit) {
        compose.setContent {
            MyFlowerTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize(), content = content)
            }
        }
    }

    private fun garden() = screen { DeviceListScreen(repository, {}, {}) }

    private fun detail() {
        screen { DeviceDetailScreen(api.devices.single(), repository, {}) }
        awaitNode(hasContentDescription("刷新数据") and isEnabled())
    }

    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())

    private fun awaitNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.waitUntil(10_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNode(matcher)
    }

    private fun lazyNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        // Lazy children may not exist yet; first ask the container to compose the target.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        return awaitNode(matcher).performScrollTo()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.filesDir, name).outputStream().use {
                assertTrue("Could not save $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}

private const val NETWORK_ERROR = "连接失败，请检查网络后重试。"
private const val PLANT_NAME = "窗边的龟背竹"
private const val SHARED_NAME = "好友的绿萝"
private const val OTHER_USER_ID = 99
private const val SAMPLE_TIME = "2026-10-21T09:30:00Z"

/** In-memory API only: no Retrofit client, network, or device-control hardware. */
private class FakeApiService : ApiService {
    val devices = mutableListOf(Device(1, 7, PLANT_NAME, "fake-device-key", true, SAMPLE_TIME, SAMPLE_TIME))
    val loginRequests = mutableListOf<LoginRequest>()
    val registerRequests = mutableListOf<RegisterRequest>()
    val createRequests = mutableListOf<CreateDeviceRequest>()
    val bindRequests = mutableListOf<BindDeviceRequest>()
    val unboundIds = mutableListOf<Int>()
    val deletedIds = mutableListOf<Int>()
    val commandRequests = mutableListOf<CreateCommandRequest>()
    val commands = mutableListOf<Command>()
    val loadCalls = mutableListOf<String>()
    var getDevicesCalls = 0
    var getCurrentUserCalls = 0
    var userLoadGate: CompletableDeferred<Unit>? = null
    var devicesLoadGate: CompletableDeferred<Unit>? = null
    var userFailuresRemaining = 0
    var deviceFailuresRemaining = 0
    var commandFailuresRemaining = 0
    var bindFailuresRemaining = 0
    var bindError: Exception = IOException("Simulated bind failure")
    var unbindFailuresRemaining = 0
    var unbindError: Exception = IOException("Simulated unbind failure")
    private var nextDeviceId = 2
    private var nextCommandId = 1
    private val user = User(7, "gardener", "mobile", true, SAMPLE_TIME)
    private val points = listOf(
        DataPoint(1, 1, "soil_moisture", "42", SAMPLE_TIME),
        DataPoint(2, 1, "temperature", "23.5", SAMPLE_TIME),
        DataPoint(3, 1, "humidity", "61", SAMPLE_TIME),
        DataPoint(4, 1, "light", "820", SAMPLE_TIME)
    )

    override suspend fun login(request: LoginRequest): LoginResponse {
        loginRequests += request
        delay(100)
        return LoginResponse("fake-ui-token", "bearer")
    }

    override suspend fun register(request: RegisterRequest): User {
        registerRequests += request
        delay(100)
        return user.copy(username = request.username)
    }

    override suspend fun getDevices(): List<Device> {
        getDevicesCalls++
        loadCalls += "devices"
        delay(100)
        devicesLoadGate?.await()
        if (deviceFailuresRemaining > 0) {
            deviceFailuresRemaining--
            throw IOException("Simulated list failure")
        }
        return devices.toList()
    }

    override suspend fun getDevice(deviceId: Int): Device {
        delay(100)
        return devices.single { it.id == deviceId }
    }

    override suspend fun createDevice(request: CreateDeviceRequest): Device {
        createRequests += request
        delay(100)
        return Device(nextDeviceId++, 7, request.deviceName, "fake-created-key", false, null, SAMPLE_TIME)
            .also { devices += it }
    }

    override suspend fun deleteDevice(deviceId: Int) {
        deletedIds += deviceId
        delay(100)
        devices.removeAll { it.id == deviceId }
    }

    override suspend fun bindDevice(request: BindDeviceRequest): BindDeviceResponse {
        bindRequests += request
        delay(100)
        if (bindFailuresRemaining > 0) {
            bindFailuresRemaining--
            throw bindError
        }
        val device = Device(nextDeviceId++, OTHER_USER_ID, SHARED_NAME, request.deviceKey, true, SAMPLE_TIME, SAMPLE_TIME)
        devices += device
        return BindDeviceResponse("Device bound", device.id, device.deviceName)
    }

    override suspend fun unbindDevice(deviceId: Int) {
        unboundIds += deviceId
        delay(100)
        if (unbindFailuresRemaining > 0) {
            unbindFailuresRemaining--
            throw unbindError
        }
        devices.removeAll { it.id == deviceId }
    }

    override suspend fun sendCommand(request: CreateCommandRequest): Command {
        commandRequests += request
        delay(100)
        if (commandFailuresRemaining > 0) {
            commandFailuresRemaining--
            throw IOException("Simulated command failure")
        }
        return Command(nextCommandId++, request.deviceId, 7, request.commandType, request.payload, "pending", SAMPLE_TIME)
            .also { commands.add(0, it) }
    }

    override suspend fun getCommandHistory(deviceId: Int?, limit: Int): List<Command> {
        delay(100)
        return commands.filter { deviceId == null || it.deviceId == deviceId }.take(limit)
    }

    override suspend fun getLatestData(deviceId: Int, dataKey: String?): DataPoint {
        delay(100)
        return points.first { it.deviceId == deviceId && (dataKey == null || it.dataKey == dataKey) }
    }

    override suspend fun getDeviceData(deviceId: Int, dataKey: String?, start: String?, end: String?, limit: Int): List<DataPoint> {
        delay(100)
        return points.filter { it.deviceId == deviceId && (dataKey == null || it.dataKey == dataKey) }.take(limit)
    }

    override suspend fun getCurrentUser(): User {
        getCurrentUserCalls++
        loadCalls += "user"
        delay(100)
        userLoadGate?.await()
        if (userFailuresRemaining > 0) {
            userFailuresRemaining--
            throw IOException("Simulated user failure")
        }
        return user
    }

    override suspend fun changePassword(request: ChangePasswordRequest): Map<String, String> =
        error("Unexpected changePassword call")
    override suspend fun deleteAccount(): Map<String, String> = error("Unexpected deleteAccount call")
    override suspend fun updateDeviceStatus(deviceId: Int, request: UpdateDeviceStatusRequest): Map<String, String> =
        error("Unexpected updateDeviceStatus call")
}
