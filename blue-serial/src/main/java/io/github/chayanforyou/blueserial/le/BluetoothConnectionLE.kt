package io.github.chayanforyou.blueserial.le

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import io.github.chayanforyou.blueserial.BluetoothConnectionBase
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BluetoothConnectionLE(
    private val context: Context,
    onReadCallback: OnReadCallback,
    onDisconnectedCallback: OnDisconnectedCallback,
) : BluetoothConnectionBase(onReadCallback, onDisconnectedCallback) {

    enum class Connected { False, Pending, True }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private var connected = Connected.False
    private var socket: SerialSocket? = null

    override fun isConnected(): Boolean {
        return connected == Connected.True
    }

    @Throws(IOException::class)
    override suspend fun connect(address: String, uuid: UUID) {
        connect(address) // Ignore the uuid, not used
    }

    @Throws(IOException::class)
    override suspend fun connect(address: String) {
        if (isConnected()) {
            throw IOException("already connected")
        }

        try {
            val adapter = bluetoothManager?.adapter
                ?: throw IOException("Bluetooth adapter not available")

            val device: BluetoothDevice = adapter.getRemoteDevice(address)
                ?: throw IOException("Device not found")

            connected = Connected.Pending
            val localSocket = SerialSocket(context, device)

            suspendCancellableCoroutine { continuation ->
                localSocket.connect(object : SerialListener {
                    override fun onSerialConnect() {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onSerialConnectError(e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                        onDisconnected(true)
                    }

                    override fun onSerialRead(data: ByteArray) {
                        onRead(data)
                    }

                    override fun onSerialIoError(e: Exception) {
                        throw RuntimeException("//DUMMY", e)
                    }
                })
            }

            socket = localSocket
            connected = Connected.True
        } catch (e: Exception) {
            runCatching { disconnect() }
            throw IOException(e)
        }
    }

    override fun disconnect() {
        if (isConnected()) {
            connected = Connected.False
            socket?.let {
                it.disconnect()
                socket = null
            }
        }
    }

    @Throws(IOException::class)
    override fun write(data: ByteArray) {
        if (!isConnected()) {
            throw IOException("not connected")
        }
        socket?.write(data) ?: throw IOException("socket is null")
    }
}
