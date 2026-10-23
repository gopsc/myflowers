package com.myflowers.app.data.model

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

/** 创建设备 = 建设备记录 + 开一个专属 iot 账号；账号密码由用户填写，设备侧接入要用。 */
data class ProvisionDeviceRequest(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("iot_username") val iotUsername: String,
    @SerializedName("iot_password") val iotPassword: String
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
