package com.mimic.wakepc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DeviceRepository(context: Context) {
    private val preferences = context.getSharedPreferences("wake_pc_devices", Context.MODE_PRIVATE)

    fun loadDevices(): List<SavedDevice> {
        val json = preferences.getString(DEVICES_KEY, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        SavedDevice(
                            name = item.getString("name"),
                            ipAddress = item.getString("ipAddress"),
                            macAddress = item.getString("macAddress"),
                            port = item.optInt("port", 9)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveDevices(devices: List<SavedDevice>) {
        val array = JSONArray()
        devices.forEach { device ->
            array.put(
                JSONObject()
                    .put("name", device.name)
                    .put("ipAddress", device.ipAddress)
                    .put("macAddress", device.macAddress)
                    .put("port", device.port)
            )
        }
        preferences.edit().putString(DEVICES_KEY, array.toString()).apply()
    }

    private companion object {
        const val DEVICES_KEY = "devices"
    }
}
