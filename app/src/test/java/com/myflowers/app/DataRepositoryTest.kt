package com.myflowers.app

import com.google.gson.Gson
import com.myflowers.app.data.DataRepository
import com.myflowers.app.data.model.BindDeviceRequest
import com.myflowers.app.data.model.BindDeviceResponse
import com.myflowers.app.data.model.DataPoint
import com.myflowers.app.data.model.Device
import com.myflowers.app.data.model.ProvisionDeviceRequest
import com.myflowers.app.data.remote.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.lang.reflect.Proxy

class DataRepositoryTest {
    private fun repository(result: () -> Any?): DataRepository {
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, _, _ -> result() } as ApiService
        return DataRepository(api)
    }

    @Test
    fun provisionDeviceSendsIotCredentialsAndReturnsDeviceCode() = runTest {
        val device = Device(2, 7, "窗边的小绿", "device-code-2", false, null, "2026-10-22T00:00:00")
        var seen: ProvisionDeviceRequest? = null
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, method, args ->
            check(method.name == "provisionDevice") { "Unexpected API call: ${method.name}" }
            seen = args!![0] as ProvisionDeviceRequest
            device
        } as ApiService

        val actual = DataRepository(api).provisionDevice("窗边的小绿", "pot_1", "grow123")

        assertSame(device, actual)
        assertEquals(ProvisionDeviceRequest("窗边的小绿", "pot_1", "grow123"), seen)
        assertEquals("device-code-2", actual.deviceKey)
    }

    @Test
    fun provisionConflictPropagatesForUiMapping() = runTest {
        val error = HttpException(Response.error<Any>(409, "{}".toResponseBody()))
        try {
            repository { throw error }.provisionDevice("窗边的小绿", "pot_1", "grow123")
            fail("Expected HTTP 409")
        } catch (actual: HttpException) {
            assertSame(error, actual)
        }
    }

    @Test
    fun bindDevicePassesKeyToApiAndReturnsServerResponse() = runTest {
        val response = BindDeviceResponse("Bound successfully", 42, "朋友的绿萝")
        val calls = mutableListOf<String>()
        val requests = mutableListOf<BindDeviceRequest>()
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, method, args ->
            calls += method.name
            check(method.name == "bindDevice") { "Unexpected API call: ${method.name}" }
            requests += args!![0] as BindDeviceRequest
            response
        } as ApiService

        val actual = DataRepository(api).bindDevice("non-uuid-device-key")

        assertEquals(listOf("bindDevice"), calls)
        assertEquals(listOf(BindDeviceRequest("non-uuid-device-key")), requests)
        assertSame(response, actual)
    }

    @Test
    fun unbindDevicePassesIdToApiWithoutDeletingDevice() = runTest {
        val calls = mutableListOf<String>()
        val ids = mutableListOf<Int>()
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, method, args ->
            calls += method.name
            check(method.name == "unbindDevice") { "Unexpected API call: ${method.name}" }
            ids += args!![0] as Int
            Unit
        } as ApiService

        val actual = DataRepository(api).unbindDevice(42)

        assertEquals(Unit, actual)
        assertEquals(listOf("unbindDevice"), calls)
        assertEquals(listOf(42), ids)
    }

    @Test
    fun bindingApiUsesExpectedRetrofitMethodsPathsAndParameters() {
        val bind = ApiService::class.java.methods.single { it.name == "bindDevice" }
        assertEquals("api/v1/devices/bind", bind.getAnnotation(POST::class.java)?.value)
        assertEquals(BindDeviceRequest::class.java, bind.parameterTypes[0])
        assertTrue(bind.parameterAnnotations[0].any { it is Body })

        val unbind = ApiService::class.java.methods.single { it.name == "unbindDevice" }
        assertEquals("api/v1/devices/{device_id}/unbind", unbind.getAnnotation(DELETE::class.java)?.value)
        assertEquals(Int::class.javaPrimitiveType, unbind.parameterTypes[0])
        assertEquals("device_id", unbind.parameterAnnotations[0].filterIsInstance<Path>().single().value)
    }

    @Test
    fun bindingModelsUseSnakeCaseWireFields() {
        val gson = Gson()
        val request = gson.toJsonTree(BindDeviceRequest("existing-key")).asJsonObject
        assertEquals(setOf("device_key"), request.entrySet().map { it.key }.toSet())
        assertEquals("existing-key", request.get("device_key").asString)

        val response = gson.fromJson(
            """{"message":"Bound successfully","device_id":42,"device_name":"朋友的绿萝"}""",
            BindDeviceResponse::class.java
        )
        assertEquals(BindDeviceResponse("Bound successfully", 42, "朋友的绿萝"), response)
        val encoded = gson.toJsonTree(response).asJsonObject
        assertEquals(setOf("message", "device_id", "device_name"), encoded.entrySet().map { it.key }.toSet())
    }

    @Test
    fun bindHttpErrorPropagatesForUiStatusMapping() = runTest {
        listOf(400, 403, 404, 409, 500).forEach { status ->
            val error = HttpException(Response.error<Any>(status, "{}".toResponseBody()))
            try {
                repository { throw error }.bindDevice("existing-key")
                fail("Expected HTTP $status")
            } catch (actual: HttpException) {
                assertSame(error, actual)
            }
        }
    }

    @Test
    fun unbindErrorIsNotReportedAsSuccess() = runTest {
        val error = HttpException(Response.error<Any>(403, "{}".toResponseBody()))
        try {
            repository { throw error }.unbindDevice(42)
            fail("Expected unbind failure")
        } catch (actual: HttpException) {
            assertSame(error, actual)
        }
    }

    @Test
    fun chartDataPassesWindowIntervalAndLimitToApi() = runTest {
        val calls = mutableListOf<List<Any?>>()
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, method, args ->
            check(method.name == "getDeviceData") { "Unexpected API call: ${method.name}" }
            // suspend 方法的最后一个参数是 Continuation，只比较业务参数。
            calls += args!!.take(6)
            emptyList<DataPoint>()
        } as ApiService
        val start = "2026-10-25T20:57:00Z"

        DataRepository(api).getDeviceData(
            deviceId = 42,
            start = start,
            intervalSeconds = 900,
            limit = 1000
        )

        assertEquals(
            listOf(listOf(42, null, start, null, 1000, 900)),
            calls
        )
    }

    @Test
    fun latestDataReturnsServerValue() = runTest {
        val point = DataPoint(1, 1, "temperature", "23.5", "2026-10-22T12:00:00")
        assertEquals(point, repository { point }.getLatestData(1, "temperature"))
    }

    @Test
    fun missingLatestDataIsEmpty() = runTest {
        val error = HttpException(Response.error<Any>(404, "{}".toResponseBody()))
        assertNull(repository { throw error }.getLatestData(1))
    }

    @Test
    fun expiredSessionIsNotDisguisedAsMissingData() = runTest {
        val error = HttpException(Response.error<Any>(401, "{}".toResponseBody()))
        try {
            repository { throw error }.getLatestData(1)
            fail("Expected authentication error")
        } catch (actual: HttpException) {
            assertEquals(401, actual.code())
        }
    }

    @Test
    fun networkFailureIsNotDisguisedAsMissingData() = runTest {
        val error = IOException("offline")
        try {
            repository { throw error }.getLatestData(1)
            fail("Expected network error")
        } catch (actual: java.lang.reflect.UndeclaredThrowableException) {
            assertSame(error, actual.cause)
        }
    }

    @Test
    fun cancellationPropagates() = runTest {
        val error = CancellationException("screen left")
        try {
            repository { throw error }.getLatestData(1)
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(error, actual)
        }
    }
}
