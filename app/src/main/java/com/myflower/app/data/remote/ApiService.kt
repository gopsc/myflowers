package com.myflower.app.data.remote

import com.myflower.app.data.model.*
import retrofit2.http.*

interface ApiService {

    @POST("api/v1/users/register")
    suspend fun register(@Body request: RegisterRequest): User

    @POST("api/v1/users/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/v1/users/me")
    suspend fun getCurrentUser(): User

    @PUT("api/v1/users/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Map<String, String>

    @DELETE("api/v1/users/me")
    suspend fun deleteAccount(): Map<String, String>

    @POST("api/v1/devices")
    suspend fun createDevice(@Body request: CreateDeviceRequest): Device

    @GET("api/v1/devices")
    suspend fun getDevices(): List<Device>

    @GET("api/v1/devices/{device_id}")
    suspend fun getDevice(@Path("device_id") deviceId: Int): Device

    @PUT("api/v1/devices/{device_id}/status")
    suspend fun updateDeviceStatus(
        @Path("device_id") deviceId: Int,
        @Body request: UpdateDeviceStatusRequest
    ): Map<String, String>

    @DELETE("api/v1/devices/{device_id}")
    suspend fun deleteDevice(@Path("device_id") deviceId: Int)

    @POST("api/v1/devices/bind")
    suspend fun bindDevice(@Body request: BindDeviceRequest): BindDeviceResponse

    @DELETE("api/v1/devices/{device_id}/unbind")
    suspend fun unbindDevice(@Path("device_id") deviceId: Int)

    @POST("api/v1/commands")
    suspend fun sendCommand(@Body request: CreateCommandRequest): Command

    @GET("api/v1/commands/history")
    suspend fun getCommandHistory(
        @Query("device_id") deviceId: Int? = null,
        @Query("limit") limit: Int = 50
    ): List<Command>

    @GET("api/v1/data/{device_id}")
    suspend fun getDeviceData(
        @Path("device_id") deviceId: Int,
        @Query("data_key") dataKey: String? = null,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("limit") limit: Int = 100
    ): List<DataPoint>

    @GET("api/v1/data/{device_id}/latest")
    suspend fun getLatestData(
        @Path("device_id") deviceId: Int,
        @Query("data_key") dataKey: String? = null
    ): DataPoint
}
