package com.myflowers.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myflowers.app.data.DataRepository
import com.myflowers.app.data.local.TokenManager
import com.myflowers.app.data.model.*
import com.myflowers.app.data.remote.ApiService
import com.myflowers.app.theme.MyFlowersTheme
import com.myflowers.app.ui.device.DeviceDetailScreen
import com.myflowers.app.ui.device.DeviceListScreen
import com.myflowers.app.ui.login.LoginScreen
import com.myflowers.app.ui.register.RegisterScreen
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
        compose.onNodeWithText("进入我的设备").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("MyFlowers").performScrollTo()
        screenshot("login-redesign.png")

        field("用户名").performScrollTo().performTextInput("  gardener  ")
        closeSoftKeyboard()
        compose.onNodeWithText("进入我的设备").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(api.loginRequests.isEmpty()) }
        field("密码").performScrollTo().performTextInput("secret123")
        closeSoftKeyboard()
        compose.onNodeWithText("进入我的设备").performScrollTo().assertIsEnabled().performClick()

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
        compose.onNodeWithText("创建账户，开始使用").performScrollTo().performClick()
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
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasText(NETWORK_ERROR)).assertIsDisplayed()
        lazyNode(hasText("重新加载")).performClick()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasText(DEVICE_NAME)).assertIsDisplayed()
        compose.onNodeWithText(NETWORK_ERROR).assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, api.getDevicesCalls) }
        screenshot("garden-redesign.png")
    }

    @Test
    fun garden_offlineFilter_hasEmptyState_andAllRestoresDevice() {
        garden()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        // Status badges also say 在线/离线; select only clickable filter chips.
        lazyNode(hasText("离线") and hasClickAction()).performClick()
        lazyNode(hasText("暂无离线设备")).assertIsDisplayed()
        compose.onNodeWithText(DEVICE_NAME).assertDoesNotExist()
        lazyNode(hasText("全部") and hasClickAction()).performClick()
        lazyNode(hasText(DEVICE_NAME)).assertIsDisplayed()
    }

    @Test
    fun garden_provisionValidatesInput_thenShowsDeviceCode() {
        garden()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        compose.onNodeWithContentDescription("添加设备").performClick()
        val confirm = hasContentDescription("确认创建设备") and hasAnyAncestor(isDialog())
        awaitNode(confirm).assertIsNotEnabled()
        field("设备名称").performTextInput("   ")
        compose.onNode(confirm).assertIsNotEnabled()
        field("设备名称").performTextReplacement("a".repeat(101))
        compose.onNode(confirm).assertIsNotEnabled()
        field("设备名称").performTextReplacement("  阳台薄荷  ")
        field("设备账号").performTextInput("pot_balcony")
        field("设备密码").performTextInput("grow123")
        closeSoftKeyboard()
        compose.onNode(confirm).assertIsEnabled().performClick()

        awaitNode(hasText("设备已创建"))
        compose.onNodeWithText("设备码").assertIsDisplayed()
        compose.onNodeWithText("fake-provisioned-key").assertIsDisplayed()
        compose.onNodeWithText("pot_balcony").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(ProvisionDeviceRequest("阳台薄荷", "pot_balcony", "grow123")),
                api.provisionRequests
            )
            assertTrue(api.createRequests.isEmpty())
        }

        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
        }
        lazyNode(hasText("阳台薄荷")).assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, api.devices.size) }
    }

    @Test
    fun garden_provisionConflict_keepsDialogAndCredentials_forRetry() {
        garden()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        compose.onNodeWithContentDescription("添加设备").performClick()
        field("设备名称").performTextInput("阳台薄荷")
        field("设备账号").performTextInput("pot_taken")
        field("设备密码").performTextInput("grow123")
        closeSoftKeyboard()
        api.provisionFailuresRemaining = 1
        api.provisionError = HttpException(Response.error<Any>(409, "{}".toResponseBody()))
        compose.onNode(hasContentDescription("确认创建设备")).performClick()

        awaitNode(hasText("这个设备账号已被占用：换一个账号，或改用「绑定已有」。"))
        compose.onNodeWithText("设备已创建").assertDoesNotExist()
        field("设备账号").assertTextContains("pot_taken")

        compose.onNodeWithText("取消").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
        }
        compose.runOnIdle { assertTrue(api.devices.size == 1) }
    }

    @Test
    fun garden_deleteRequiresConfirmation_cancelPreserves_confirmRemoves() {
        garden()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasContentDescription("删除 $DEVICE_NAME")).performClick()
        awaitNode(hasText("删除这个设备？")).assertIsDisplayed()
        compose.onNodeWithText("保留设备").performClick()
        lazyNode(hasText(DEVICE_NAME)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(api.deletedIds.isEmpty()) }
        lazyNode(hasContentDescription("删除 $DEVICE_NAME")).performClick()
        awaitNode(hasText("确认删除")).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
        }
        lazyNode(hasText("你的设备，从这里开始")).assertIsDisplayed()
        compose.onNodeWithText(DEVICE_NAME).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(1), api.deletedIds)
            assertTrue(api.devices.isEmpty())
        }
    }

    @Test
    fun detail_noHistory_showsOnlyCustomCommandEntry() {
        detail()
        lazyNode(hasText("自定义指令") and hasClickAction()).assertIsEnabled()
        compose.onNode(hasText("浇水") and hasClickAction()).assertDoesNotExist()
        compose.onNode(hasText("开灯") and hasClickAction()).assertDoesNotExist()
        // Only the custom command entry is actionable besides navigation/refresh/tabs.
        compose.onAllNodes(
            hasClickAction() and !hasContentDescription("返回列表") and
                !hasContentDescription("刷新数据") and !hasText("实时概览") and !hasText("运行记录")
        ).assertCountEquals(1)
    }

    @Test
    fun detail_quickActions_deriveFromHistory_waterResendPending_andGolden() {
        api.commands += Command(30, 1, 7, "turn_on_light", emptyMap(), "executed", SAMPLE_TIME)
        api.commands += Command(31, 1, 7, "water", mapOf("amount" to 100), "executed", SAMPLE_TIME)
        detail()
        // 快捷指令全部来自历史，没有内置指令；参数复用最近一次的值。
        compose.onNode(hasText("每次 100 ml")).assertDoesNotExist()
        lazyNode(hasText("浇水") and hasClickAction()).assertIsEnabled()
        lazyNode(hasText("开灯") and hasClickAction()).assertIsEnabled()
        lazyNode(hasText("amount=100")).assertIsDisplayed()
        // Exclude navigation/refresh and the two tabs: the two history-derived quick actions and the custom entry.
        compose.onAllNodes(
            hasClickAction() and !hasContentDescription("返回列表") and
                !hasContentDescription("刷新数据") and !hasText("实时概览") and !hasText("运行记录")
        ).assertCountEquals(3)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        screenshot("detail-redesign.png")

        // 数据展示完全通用：data_key 原样显示，无预设标签、单位或图标。
        listOf("soil_moisture" to "42", "temperature" to "23.5", "humidity" to "61", "light" to "820")
            .forEach { (label, value) ->
                lazyNode(hasText(label)).assertIsDisplayed()
                lazyNode(hasText(value)).assertIsDisplayed()
            }
        compose.onNodeWithText("土壤湿度").assertDoesNotExist()
        lazyNode(hasText("浇水") and hasClickAction()).performClick()
        awaitNode(hasText("浇水指令已提交，等待执行"))
        compose.runOnIdle {
            assertEquals(listOf(CreateCommandRequest(1, "water", mapOf("amount" to 100))), api.commandRequests)
            assertEquals("pending", api.commands.first().status)
        }
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        lazyNode(hasText("等待执行")).assertIsDisplayed()
        compose.onNodeWithText("已下发").assertDoesNotExist()
    }

    @Test
    fun detail_overview_hidesItemsWithoutRecentUpdates_thenShowsAllStaleNotice() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        api.points.clear()
        api.points += DataPoint(1, 1, "fresh_metric", "7", now.minusMinutes(5).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        api.points += DataPoint(2, 1, "stale_metric", "9", now.minusMinutes(45).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        api.points += DataPoint(3, 1, "broken_metric", "11", "invalid-time")
        detail()
        lazyNode(hasText("fresh_metric")).assertIsDisplayed()
        lazyNode(hasText("7")).assertIsDisplayed()
        compose.onNodeWithText("stale_metric").assertDoesNotExist()
        compose.onNodeWithText("broken_metric").assertDoesNotExist()
        lazyNode(hasText("已隐藏 2 个超过 30 分钟未更新的项目。")).assertIsDisplayed()
        screenshot("overview-stale-filtered.png")

        compose.runOnIdle {
            api.points.clear()
            api.points += DataPoint(4, 1, "old_metric", "1", now.minusHours(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }
        lazyNode(hasContentDescription("刷新数据")).performClick()
        awaitNode(hasContentDescription("刷新数据") and isEnabled())
        lazyNode(hasText("超过 30 分钟没有新的上报，实时数据暂不显示。")).assertIsDisplayed()
        compose.onNodeWithText("old_metric").assertDoesNotExist()
    }

    @Test
    fun detail_customCommand_validatesInput_thenSendsArbitraryTypeAndPayload() {        detail()
        lazyNode(hasText("自定义指令") and hasClickAction()).performClick()
        awaitNode(hasText("发送任意指令")).assertIsDisplayed()
        compose.onNode(hasContentDescription("发送自定义指令")).performClick()
        awaitNode(hasText("请填写指令类型。")).assertIsDisplayed()
        compose.runOnIdle { assertTrue(api.commandRequests.isEmpty()) }
        field("指令类型").performTextInput("set_threshold")
        field("参数（JSON，可选）").performTextInput("[1, 2]")
        closeSoftKeyboard()
        compose.onNode(hasContentDescription("发送自定义指令")).performClick()
        awaitNode(hasText("参数不是合法的 JSON 对象。")).assertIsDisplayed()
        compose.runOnIdle { assertTrue(api.commandRequests.isEmpty()) }
        field("参数（JSON，可选）").performTextReplacement("""{"level": 2}""")
        closeSoftKeyboard()
        compose.onNode(hasContentDescription("发送自定义指令")).performClick()
        awaitNoDialog()
        awaitNode(hasText("设置阈值指令已提交，等待执行"))
        // 发送过的自定义指令会沉淀为快捷指令按钮。
        awaitNode(hasText("设置阈值") and hasClickAction()).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(CreateCommandRequest(1, "set_threshold", mapOf("level" to 2.0))), api.commandRequests)
            assertEquals("pending", api.commands.single().status)
        }
    }

    @Test
    fun detail_commandError_showsFeedback_andDoesNotInventExecution() {
        api.commands += Command(40, 1, 7, "turn_on_light", emptyMap(), "executed", SAMPLE_TIME)
        api.commandFailuresRemaining = 1
        detail()
        lazyNode(hasText("开灯") and hasClickAction()).performClick()
        awaitNode(hasText(NETWORK_ERROR)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("开灯") and hasClickAction()).assertIsEnabled()
        compose.runOnIdle {
            assertEquals(listOf(CreateCommandRequest(1, "turn_on_light", emptyMap())), api.commandRequests)
            assertEquals(1, api.commands.size)
        }
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        compose.onNodeWithText("等待执行").assertDoesNotExist()
        compose.onNodeWithText("已下发").assertDoesNotExist()
    }

    @Test
    fun garden_bindBlankDisabled_trimsNonUuidKey_refreshesSharedCard_andGoldens() {
        garden()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasText("自有设备", substring = true)).assertIsDisplayed()
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
        lazyNode(hasContentDescription("刷新设备"))
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasText(SHARED_NAME)).assertIsDisplayed()
        lazyNode(hasText("共享设备", substring = true)).assertIsDisplayed()
        lazyNode(hasContentDescription("解绑 $SHARED_NAME")).assertIsEnabled()
        compose.onNodeWithContentDescription("删除 $SHARED_NAME").assertDoesNotExist()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("设备已绑定")).fetchSemanticsNodes().isEmpty() }
        screenshot("garden-shared-device.png")
        lazyNode(hasContentDescription("解绑 $SHARED_NAME")).performClick()
        awaitNode(hasText("解绑这个设备？")).assertIsDisplayed()
        screenshot("unbind-device-dialog.png")
        compose.onNodeWithText("保留设备").performClick()
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
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasText("共享设备", substring = true)).assertIsDisplayed()
        compose.onNodeWithContentDescription("删除 $DEVICE_NAME").assertDoesNotExist()
        lazyNode(hasContentDescription("解绑 $DEVICE_NAME")).performClick()
        awaitNode(hasText("解绑这个设备？")).assertIsDisplayed()
        compose.onNodeWithText("确认删除").assertDoesNotExist()
        compose.onNodeWithText("保留设备").performClick()
        awaitNoDialog()
        lazyNode(hasText(DEVICE_NAME)).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(api.unboundIds.isEmpty())
            assertTrue(api.deletedIds.isEmpty())
            assertEquals(1, api.devices.size)
        }
        lazyNode(hasContentDescription("解绑 $DEVICE_NAME")).performClick()
        awaitNode(hasText("确认解绑") and isEnabled()).performClick()
        awaitNoDialog()
        awaitNode(hasText("设备已解绑")).assertIsDisplayed()
        lazyNode(hasText("你的设备，从这里开始")).assertIsDisplayed()
        compose.onNodeWithText(DEVICE_NAME).assertDoesNotExist()
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
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasContentDescription("解绑 $DEVICE_NAME")).performClick()
        awaitNode(hasText("确认解绑") and isEnabled()).performClick()
        awaitNode(hasText(NETWORK_ERROR) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("解绑这个设备？").assertIsDisplayed()
        compose.onNodeWithText("设备已解绑").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(1), api.unboundIds)
            assertEquals(1, api.devices.size)
            assertTrue(api.deletedIds.isEmpty())
        }
        compose.onNode(hasText("确认解绑") and isEnabled()).performClick()
        awaitNoDialog()
        awaitNode(hasText("设备已解绑")).assertIsDisplayed()
        lazyNode(hasText("你的设备，从这里开始")).assertIsDisplayed()
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
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasText(NETWORK_ERROR)).assertIsDisplayed()
        assertNoEnabledRemoval()
        compose.runOnIdle {
            assertEquals(1, api.getCurrentUserCalls)
            assertEquals(0, api.getDevicesCalls)
            assertTrue(api.deletedIds.isEmpty())
            assertTrue(api.unboundIds.isEmpty())
        }
        lazyNode(hasText("重新加载")).performClick()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasContentDescription("删除 $DEVICE_NAME")).assertIsEnabled()
        lazyNode(hasText("自有设备", substring = true)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("user", "user", "devices"), api.loadCalls) }
    }

    @Test
    fun garden_userRefreshFailure_doesNotLeaveStaleRemovalEnabled() {
        useSharedDevice()
        garden()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasContentDescription("解绑 $DEVICE_NAME")).assertIsEnabled()
        compose.runOnIdle { api.userFailuresRemaining = 1 }
        lazyNode(hasContentDescription("刷新设备")).performClick()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
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
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasContentDescription("解绑 $DEVICE_NAME")).assertIsEnabled()
        val userGate = CompletableDeferred<Unit>()
        val devicesGate = CompletableDeferred<Unit>()
        compose.runOnIdle {
            api.userLoadGate = userGate
            api.devicesLoadGate = devicesGate
            api.deviceFailuresRemaining = 1
        }
        try {
            lazyNode(hasContentDescription("刷新设备")).performClick()
            compose.waitUntil(10_000) { api.getCurrentUserCalls == 2 }
            compose.runOnIdle { assertEquals(1, api.getDevicesCalls) }
            compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
            assertNoEnabledRemoval()

            compose.runOnIdle { userGate.complete(Unit) }
            compose.waitUntil(10_000) { api.getDevicesCalls == 2 }
            compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
            assertNoEnabledRemoval()

            compose.runOnIdle { devicesGate.complete(Unit) }
            lazyNode(hasContentDescription("刷新设备"))
            awaitNode(hasContentDescription("刷新设备") and isEnabled())
            lazyNode(hasText(NETWORK_ERROR)).assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
            assertNoEnabledRemoval()
            compose.runOnIdle {
                assertTrue(api.unboundIds.isEmpty())
                assertTrue(api.deletedIds.isEmpty())
            }
            lazyNode(hasText("重新加载")).performClick()
            awaitNode(hasContentDescription("刷新设备") and isEnabled())
            lazyNode(hasContentDescription("解绑 $DEVICE_NAME")).assertIsEnabled()
        } finally {
            userGate.complete(Unit)
            devicesGate.complete(Unit)
        }
    }

    @Test
    fun garden_bindSuccess_listRefreshFailure_retriesListWithoutBindingAgain() {
        garden()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        openBindingDialog()
        field("设备密钥").performTextInput("existing-key")
        closeSoftKeyboard()
        compose.runOnIdle { api.deviceFailuresRemaining = 1 }
        compose.onNode(bindConfirm).performClick()
        awaitNoDialog()
        lazyNode(hasContentDescription("刷新设备"))
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasText(NETWORK_ERROR)).assertIsDisplayed()
        compose.onNodeWithText("设备密钥").assertDoesNotExist()
        compose.onNode(bindConfirm).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(BindDeviceRequest("existing-key")), api.bindRequests)
            assertEquals(2, api.getDevicesCalls)
            assertEquals(2, api.devices.size)
        }
        lazyNode(hasText("重新加载")).performClick()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
        lazyNode(hasText(SHARED_NAME)).assertIsDisplayed()
        lazyNode(hasText("共享设备", substring = true)).assertIsDisplayed()
        compose.onNodeWithText(NETWORK_ERROR).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(3, api.getDevicesCalls)
            assertEquals(listOf(BindDeviceRequest("existing-key")), api.bindRequests)
            assertTrue(api.createRequests.isEmpty())
        }
    }

    @Test
    fun detail_sharedDevice_reusesHistoryCommands_usingOnlyFakeCommands() {
        useSharedDevice()
        api.commands += Command(50, 1, 7, "turn_on_light", emptyMap(), "executed", SAMPLE_TIME)
        api.commands += Command(51, 1, 7, "water", mapOf("amount" to 100), "executed", SAMPLE_TIME)
        detail()
        lazyNode(hasText("浇水") and hasClickAction()).assertIsEnabled().performClick()
        awaitNode(hasText("浇水指令已提交，等待执行"))
        // Snackbar 会覆盖下方按钮，等它消失后再进行真实触摸点击。
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("浇水指令已提交，等待执行")).fetchSemanticsNodes().isEmpty()
        }
        lazyNode(hasText("开灯") and hasClickAction()).assertIsEnabled().performClick()
        awaitNode(hasText("开灯指令已提交，等待执行"))
        compose.runOnIdle {
            assertEquals(OTHER_USER_ID, api.devices.single().userId)
            assertEquals(
                listOf(
                    CreateCommandRequest(1, "water", mapOf("amount" to 100)),
                    CreateCommandRequest(1, "turn_on_light", emptyMap())
                ),
                api.commandRequests
            )
            assertEquals(4, api.commands.size)
            assertTrue(api.commands.take(2).all { it.status == "pending" })
            assertTrue(api.deletedIds.isEmpty())
            assertTrue(api.unboundIds.isEmpty())
        }
    }

    @Test
    fun detail_commandHistory_marksUnansweredCommandsTimedOut() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        api.commands.clear()
        api.commands += listOf(
            Command(1, 1, 7, "water", emptyMap(), "pending", now.minusMinutes(11).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
            Command(2, 1, 7, "turn_on_light", emptyMap(), "sent", now.minusMinutes(30).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
            Command(3, 1, 7, "set_threshold", emptyMap(), "executed", now.minusMinutes(30).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
            Command(4, 1, 7, "reboot", emptyMap(), "pending", now.minusMinutes(5).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        )
        detail()
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        lazyNode(hasText("reboot")).assertIsDisplayed()
        repeat(3) { compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        screenshot("command-timeout.png")
        compose.onAllNodes(hasText("超时失效")).assertCountEquals(2)
        compose.onAllNodes(hasText("等待执行")).assertCountEquals(1)
        compose.onAllNodes(hasText("已执行")).assertCountEquals(1)
        compose.onAllNodes(hasText("已下发")).assertCountEquals(0)
    }

    @Test
    fun detail_commandHistory_showsAtMostTenRecentCommands() {
        api.commands.clear()
        api.commands += (1..12).map {
            Command(it, 1, 7, "cmd_%02d".format(it), emptyMap(), "executed", SAMPLE_TIME)
        }
        detail()
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        lazyNode(hasText("cmd_01")).assertIsDisplayed()
        lazyNode(hasText("cmd_10")).assertIsDisplayed()
        repeat(3) { compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        lazyNode(hasText("cmd_10")).assertIsDisplayed()
        compose.onNodeWithText("cmd_11").assertDoesNotExist()
        compose.onNodeWithText("cmd_12").assertDoesNotExist()
    }

    @Test
    fun detail_historyChart_isStaticAndSwitchesFields_keepsCommandHistory() {
        api.points.clear()
        api.points += listOf(
            DataPoint(3, 1, "temperature", "28.5", "2026-10-21T10:30:00Z"),
            DataPoint(1, 1, "temperature", "18.5", "2026-10-21T08:30:00Z"),
            DataPoint(2, 1, "temperature", "22.5", "2026-10-21T09:30:00Z"),
            DataPoint(4, 1, "humidity", "61", SAMPLE_TIME)
        )
        api.commands += Command(50, 1, 7, "water", emptyMap(), "executed", SAMPLE_TIME)
        detail()
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        val chart = hasContentDescription("temperature 历史折线图")
        lazyNode(chart).assertIsDisplayed()
        compose.onNode(chart).assert(!hasClickAction())
        compose.onNodeWithText("最低").assertDoesNotExist()
        compose.onNodeWithText("最高").assertDoesNotExist()
        compose.onAllNodes(hasText("个数据点", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("22.5").assertDoesNotExist()
        compose.onNodeWithText("2026-10-21T10:30:00Z").assertDoesNotExist()
        screenshot("history-chart-light.png")
        lazyNode(hasContentDescription("查看 humidity 趋势")).performClick()
        lazyNode(hasContentDescription("humidity 历史折线图")).assertIsDisplayed()
        compose.onNode(chart).assertDoesNotExist()
        lazyNode(hasText("仅有一个数据点，收到更多上报后将显示趋势。")).assertIsDisplayed()
        lazyNode(hasText("最近的指令")).assertIsDisplayed()
        lazyNode(hasText("已执行")).assertIsDisplayed()
        compose.runOnIdle { assertTrue(api.commandRequests.isEmpty()) }
    }

    @Test
    fun detail_historyChart_onlyShowsNumericStrings_hidesNonNumericFieldsAndRecords() {
        api.points.clear()
        api.points += listOf(
            DataPoint(1, 1, "reading", " 12.5 ", SAMPLE_TIME),
            DataPoint(2, 1, "reading", "offline", SAMPLE_TIME),
            DataPoint(3, 1, "reading", "14.5", "invalid-time"),
            DataPoint(4, 1, "status", "{\"online\":true}", SAMPLE_TIME),
            DataPoint(5, 1, "reading", "1.25e2", SAMPLE_TIME)
        )
        detail()
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        lazyNode(hasContentDescription("reading 历史折线图")).assertIsDisplayed()
        compose.onNodeWithText(" 12.5 ").assertDoesNotExist()
        compose.onNodeWithContentDescription("查看 status 趋势").assertDoesNotExist()
        listOf("offline", "invalid-time", "{\"online\":true}").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.runOnIdle { api.points.removeAll { it.dataKey == "reading" } }
        lazyNode(hasContentDescription("刷新数据")).performClick()
        awaitNode(hasContentDescription("刷新数据") and isEnabled())
        lazyNode(hasText("暂无可绘制的数值数据。")).assertIsDisplayed()
        compose.onNodeWithContentDescription("reading 历史折线图").assertDoesNotExist()
        compose.onNodeWithContentDescription("查看 status 趋势").assertDoesNotExist()
        compose.onNodeWithText("{\"online\":true}").assertDoesNotExist()
        screenshot("history-chart-nonnumeric-empty.png")
    }

    @Test
    fun detail_historyChart_emptyAndRefresh_removesOldSelection() {
        api.points.clear()
        detail()
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        lazyNode(hasText("暂未收到数据，等待设备第一次上报。")).assertIsDisplayed()
        compose.runOnIdle { api.points += DataPoint(1, 1, "new_metric", "42", SAMPLE_TIME) }
        lazyNode(hasContentDescription("刷新数据")).performClick()
        awaitNode(hasContentDescription("刷新数据") and isEnabled())
        lazyNode(hasContentDescription("new_metric 历史折线图")).assertIsDisplayed()
        compose.runOnIdle {
            api.points.clear()
            api.points += DataPoint(2, 1, "replacement", "7", SAMPLE_TIME)
        }
        lazyNode(hasContentDescription("刷新数据")).performClick()
        awaitNode(hasContentDescription("刷新数据") and isEnabled())
        lazyNode(hasContentDescription("replacement 历史折线图")).assertIsDisplayed()
        compose.onNodeWithContentDescription("查看 new_metric 趋势").assertDoesNotExist()
        compose.runOnIdle { api.points.clear() }
        lazyNode(hasContentDescription("刷新数据")).performClick()
        awaitNode(hasContentDescription("刷新数据") and isEnabled())
        lazyNode(hasText("暂未收到数据，等待设备第一次上报。")).assertIsDisplayed()
        compose.onNodeWithContentDescription("replacement 历史折线图").assertDoesNotExist()
    }

    @Test
    fun detail_historyChart_sameTimeConstantValues_inDarkTheme() {
        api.points.clear()
        api.points += (1..3).map { DataPoint(it, 1, "constant", "-2.5", SAMPLE_TIME) }
        compose.setContent {
            MyFlowersTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize()) { DeviceDetailScreen(api.devices.single(), repository, {}) }
            }
        }
        awaitNode(hasContentDescription("刷新数据") and isEnabled())
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        lazyNode(hasContentDescription("constant 历史折线图")).assertIsDisplayed()
        compose.onNodeWithText("最低").assertDoesNotExist()
        compose.onNodeWithText("最高").assertDoesNotExist()
        screenshot("history-chart-dark.png")
    }

    @Test
    fun detail_historyChart_loadFailureCanRetry_withoutInventedPoints() {
        api.dataFailuresRemaining = 1
        detail()
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        lazyNode(hasText("设备数据未能加载")).assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("历史折线图", substring = true)).assertCountEquals(0)
        lazyNode(hasText("重新加载数据") and hasClickAction()).performClick()
        awaitNode(hasContentDescription("刷新数据") and isEnabled())
        lazyNode(hasContentDescription("soil_moisture 历史折线图")).assertIsDisplayed()
    }

    @Test
    fun detail_historyChart_restoresSafelyAfterHistoryShrinks() {
        api.points.clear()
        api.points += (1..3).map { DataPoint(it, 1, "metric", "$it", "2026-10-21T09:3${it}:00Z") }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MyFlowersTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) { DeviceDetailScreen(api.devices.single(), repository, {}) }
            }
        }
        awaitNode(hasContentDescription("刷新数据") and isEnabled())
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        lazyNode(hasContentDescription("metric 历史折线图")).assertIsDisplayed()
        compose.runOnIdle {
            api.points.clear()
            api.points += DataPoint(10, 1, "metric", "10", SAMPLE_TIME)
        }
        restoration.emulateSavedInstanceStateRestore()
        lazyNode(hasContentDescription("刷新数据"))
        awaitNode(hasContentDescription("刷新数据") and isEnabled())
        lazyNode(hasContentDescription("metric 历史折线图")).assertIsDisplayed()
        lazyNode(hasText("仅有一个数据点，收到更多上报后将显示趋势。")).assertIsDisplayed()
    }

    @Test
    fun detail_historyChart_switchingFieldsWithIdenticalShapes_updatesChart() {
        api.points.clear()
        listOf("first", "second").forEachIndexed { group, key ->
            api.points += (1..3).map { DataPoint(group * 10 + it, 1, key, "$it", "2026-10-21T09:3${it}:00Z") }
        }
        detail()
        lazyNode(hasText("运行记录") and hasClickAction()).performClick()
        lazyNode(hasContentDescription("first 历史折线图")).assertIsDisplayed()
        lazyNode(hasContentDescription("查看 second 趋势")).performClick()
        lazyNode(hasContentDescription("second 历史折线图")).assertIsDisplayed()
        compose.onNodeWithContentDescription("first 历史折线图").assertDoesNotExist()
        lazyNode(hasContentDescription("查看 first 趋势")).performClick()
        lazyNode(hasContentDescription("first 历史折线图")).assertIsDisplayed()
        compose.onNodeWithContentDescription("second 历史折线图").assertDoesNotExist()
    }

    private val bindConfirm = hasText("绑定设备") and hasClickAction() and hasAnyAncestor(isDialog())

    private fun openBindingDialog() {
        compose.onNodeWithContentDescription("添加设备").performClick()
        awaitNode(hasText("创建设备") and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsSelected()
        field("设备名称").assertExists()
        compose.onNode(hasText("绑定已有") and hasClickAction()).performClick()
        compose.onNode(hasText("绑定已有") and hasClickAction()).assertIsSelected()
        field("设备密钥").assertExists()
        field("设备名称").assertDoesNotExist()
    }

    private fun bindingFailureThenRetry(status: Int?, message: String) {
        api.bindFailuresRemaining = 1
        api.bindError = status?.let { HttpException(Response.error<Any>(it, "{}".toResponseBody())) }
            ?: IOException("Simulated bind failure")
        garden()
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
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
        lazyNode(hasContentDescription("刷新设备"))
        awaitNode(hasContentDescription("刷新设备") and isEnabled())
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
        val removal = hasContentDescription("删除 $DEVICE_NAME") or hasContentDescription("解绑 $DEVICE_NAME")
        compose.onAllNodes(removal and hasClickAction() and isEnabled()).assertCountEquals(0)
    }

    private fun awaitNoDialog() {
        compose.waitUntil(10_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }

    private fun screen(content: @Composable () -> Unit) {
        compose.setContent {
            MyFlowersTheme(darkTheme = false) {
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
private const val DEVICE_NAME = "窗边的龟背竹"
private const val SHARED_NAME = "好友的绿萝"
private const val OTHER_USER_ID = 99
// 实时概览只显示近期上报，模拟数据必须跟随当前时间。
private val SAMPLE_TIME: String =
    OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

/** In-memory API only: no Retrofit client, network, or device-control hardware. */
private class FakeApiService : ApiService {
    val devices = mutableListOf(Device(1, 7, DEVICE_NAME, "fake-device-key", true, SAMPLE_TIME, SAMPLE_TIME))
    val loginRequests = mutableListOf<LoginRequest>()
    val registerRequests = mutableListOf<RegisterRequest>()
    val createRequests = mutableListOf<CreateDeviceRequest>()
    val provisionRequests = mutableListOf<ProvisionDeviceRequest>()
    var provisionFailuresRemaining = 0
    var provisionError: Exception = IOException("Simulated provision failure")
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
    var dataFailuresRemaining = 0
    var commandFailuresRemaining = 0
    var bindFailuresRemaining = 0
    var bindError: Exception = IOException("Simulated bind failure")
    var unbindFailuresRemaining = 0
    var unbindError: Exception = IOException("Simulated unbind failure")
    private var nextDeviceId = 2
    private var nextCommandId = 1
    private val user = User(7, "gardener", "mobile", true, SAMPLE_TIME)
    val points = mutableListOf(
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

    override suspend fun provisionDevice(request: ProvisionDeviceRequest): Device {
        provisionRequests += request
        delay(100)
        if (provisionFailuresRemaining > 0) {
            provisionFailuresRemaining--
            throw provisionError
        }
        return Device(nextDeviceId++, 7, request.deviceName, "fake-provisioned-key", false, null, SAMPLE_TIME)
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
        if (dataFailuresRemaining > 0) {
            dataFailuresRemaining--
            throw IOException("Simulated data failure")
        }
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
