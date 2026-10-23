package com.myflowers.app.data.model

import com.google.gson.annotations.SerializedName

data class DataPoint(
    @SerializedName("id") val id: Int,
    @SerializedName("device_id") val deviceId: Int,
    @SerializedName("data_key") val dataKey: String,
    @SerializedName("data_value") val dataValue: String,
    @SerializedName("timestamp") val timestamp: String
)

data class PushDataRequest(
    @SerializedName("device_id") val deviceId: Int,
    @SerializedName("data_key") val dataKey: String,
    @SerializedName("data_value") val dataValue: String
)

data class BatchPushDataRequest(
    @SerializedName("device_id") val deviceId: Int,
    @SerializedName("items") val items: List<PushDataRequest>
)

data class BatchPushResponse(
    @SerializedName("count") val count: Int
)
