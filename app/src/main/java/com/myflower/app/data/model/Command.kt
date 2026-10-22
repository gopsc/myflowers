package com.myflower.app.data.model

import com.google.gson.annotations.SerializedName

data class Command(
    @SerializedName("id") val id: Int,
    @SerializedName("device_id") val deviceId: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("command_type") val commandType: String,
    @SerializedName("payload") val payload: Map<String, Any>,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String
)

data class CreateCommandRequest(
    @SerializedName("device_id") val deviceId: Int,
    @SerializedName("command_type") val commandType: String,
    @SerializedName("payload") val payload: Map<String, Any>
)

data class UpdateCommandStatusRequest(
    @SerializedName("status") val status: String
)
