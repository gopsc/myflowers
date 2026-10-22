package com.myflower.app.data

import com.myflower.app.data.local.TokenManager
import com.myflower.app.data.model.*
import com.myflower.app.data.remote.RetrofitClient
import com.myflower.app.data.remote.ApiService
import retrofit2.HttpException

class DataRepository(private val api: ApiService = RetrofitClient.apiService) {

    suspend fun register(username: String, password: String): User {
        return api.register(RegisterRequest(username, password, "mobile"))
    }

    suspend fun login(username: String, password: String): LoginResponse {
        val response = api.login(LoginRequest(username, password))
        TokenManager.token = response.accessToken
        TokenManager.username = username
        return response
    }

    fun logout() {
        TokenManager.clear()
    }

    fun isLoggedIn(): Boolean = TokenManager.isLoggedIn()

    suspend fun getCurrentUser(): User = api.getCurrentUser()

    suspend fun getDevices(): List<Device> = api.getDevices()

    suspend fun getDevice(deviceId: Int): Device = api.getDevice(deviceId)

    suspend fun createDevice(name: String): Device = api.createDevice(CreateDeviceRequest(name))

    suspend fun deleteDevice(deviceId: Int) = api.deleteDevice(deviceId)

    suspend fun bindDevice(deviceKey: String): BindDeviceResponse =
        api.bindDevice(BindDeviceRequest(deviceKey))

    suspend fun unbindDevice(deviceId: Int) = api.unbindDevice(deviceId)

    suspend fun getDeviceData(
        deviceId: Int,
        dataKey: String? = null,
        limit: Int = 100
    ): List<DataPoint> = api.getDeviceData(deviceId, dataKey, limit = limit)

    suspend fun getLatestData(deviceId: Int, dataKey: String? = null): DataPoint? {
        return try {
            api.getLatestData(deviceId, dataKey)
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
        }
    }

    suspend fun sendCommand(
        deviceId: Int,
        commandType: String,
        payload: Map<String, Any>
    ): Command = api.sendCommand(CreateCommandRequest(deviceId, commandType, payload))

    suspend fun getCommandHistory(deviceId: Int? = null, limit: Int = 50): List<Command> =
        api.getCommandHistory(deviceId, limit)
}
