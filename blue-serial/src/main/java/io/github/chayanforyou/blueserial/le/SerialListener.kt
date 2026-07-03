package io.github.chayanforyou.blueserial.le

interface SerialListener {
    fun onSerialConnect()
    fun onSerialConnectError(e: Exception)
    fun onSerialRead(data: ByteArray) // socket -> service
    fun onSerialIoError(e: Exception)
}
