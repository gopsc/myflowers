package com.myflower.app.data.model

import com.google.gson.annotations.SerializedName

data class Device(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_key") val deviceKey: String,
    @SerializedName("is_online") val isOnline: Boolean,
    @SerializedName("last_seen") val lastSeen: String?,
    @SerializedName("created_at") val createdAt: String
)

data class CreateDeviceRequest(
    @SerializedName("device_name") val deviceName: String
)

data class UpdateDeviceStatusRequest(
    @SerializedName("is_online") val isOnline: Boolean
)

data class BindDeviceRequest(
    @SerializedName("device_key") val deviceKey: String
)

data class BindDeviceResponse(
    @SerializedName("message") val message: String,
    @SerializedName("device_id") val deviceId: Int,
    @SerializedName("device_name") val deviceName: String
)
