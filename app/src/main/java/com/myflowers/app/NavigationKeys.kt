package com.myflowers.app

import androidx.navigation3.runtime.NavKey
import com.myflowers.app.data.model.Device
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable data object Login : NavKey
@Serializable data object Register : NavKey
@Serializable data object DeviceList : NavKey

@Serializable
data class DeviceDetail(
    val deviceId: Int,
    val deviceName: String,
    val isOnline: Boolean,
    val lastSeen: String?
) : NavKey {
    constructor(device: Device) : this(
        deviceId = device.id,
        deviceName = device.deviceName,
        isOnline = device.isOnline,
        lastSeen = device.lastSeen
    )

    fun toDevice(): Device = Device(
        id = deviceId,
        userId = 0,
        deviceName = deviceName,
        deviceKey = "",
        isOnline = isOnline,
        lastSeen = lastSeen,
        createdAt = ""
    )
}
