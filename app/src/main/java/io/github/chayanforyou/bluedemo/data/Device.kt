package io.github.chayanforyou.bluedemo.data

enum class DeviceType {
    CLASSIC,
    LE
}

data class Device(
    val name: String,
    val address: String,
    val type: DeviceType
)
