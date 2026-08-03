package com.mimic.wakepc

data class SavedDevice(
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val port: Int = 9
)

data class DiscoveredDevice(
    val name: String,
    val ipAddress: String,
    val macAddress: String = ""
)
