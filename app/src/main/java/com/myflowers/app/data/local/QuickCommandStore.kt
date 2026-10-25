package com.myflowers.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 本机移除的快捷指令：不随退出登录清空，按账号 + 设备区分。 */
object QuickCommandStore {
    private const val PREFS_NAME = "myflowers_quick_commands"
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Int>>() {}.type

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hidden(deviceId: Int): Map<String, Int> =
        prefs.getString(key(deviceId), null)?.let { gson.fromJson<Map<String, Int>>(it, mapType) } ?: emptyMap()

    fun setHidden(deviceId: Int, hidden: Map<String, Int>) {
        val editor = prefs.edit()
        if (hidden.isEmpty()) editor.remove(key(deviceId)) else editor.putString(key(deviceId), gson.toJson(hidden))
        editor.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(deviceId: Int) = "hidden:${TokenManager.username.orEmpty()}:$deviceId"
}
