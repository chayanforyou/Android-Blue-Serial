package io.github.chayanforyou.blueserial

import java.io.IOException
import java.util.UUID

interface BluetoothConnection {
    fun isConnected(): Boolean

    /// Connects to given device by hardware address
    @Throws(IOException::class)
    suspend fun connect(address: String, uuid: UUID)

    /// Connects to given device by hardware address (default UUID used)
    @Throws(IOException::class)
    suspend fun connect(address: String)

    /// Disconnects current session (ignore if not connected)
    fun disconnect()

    /// Writes to connected remote device
    @Throws(IOException::class)
    fun write(data: ByteArray)
}
