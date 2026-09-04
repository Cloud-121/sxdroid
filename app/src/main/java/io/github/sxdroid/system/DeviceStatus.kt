package io.github.sxdroid.system

data class DeviceStatus(
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val network: String = "network ?",
)
