package io.github.chayanforyou.blueserial

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

open class BluetoothConnectionClassic(
    private val context: Context,
    onReadCallback: OnReadCallback,
    onDisconnectedCallback: OnDisconnectedCallback,
) : BluetoothConnectionBase(onReadCallback, onDisconnectedCallback) {

    companion object {
        private val DEFAULT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private var connectionThread: ConnectionThread? = null

    override fun isConnected(): Boolean {
        return connectionThread != null && !connectionThread!!.requestedClosing
    }

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    override suspend fun connect(address: String, uuid: UUID) {
        if (isConnected()) {
            throw IOException("already connected")
        }

        val adapter = bluetoothManager?.adapter
            ?: throw IOException("bluetooth adapter is null")

        val device: BluetoothDevice = adapter.getRemoteDevice(address)
            ?: throw IOException("device not found")

        var socket: BluetoothSocket? = device.createRfcommSocketToServiceRecord(uuid)
            ?: throw IOException("socket connection not established")

        // Cancel discovery, even though we didn't start it
        adapter.cancelDiscovery()

        try {
            socket?.connect()
        } catch (_: IOException) {
            try {
                // Newer versions of android may require voodoo; see https://stackoverflow.com/a/25647197
                val method =
                    device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                socket = method.invoke(device, 1) as? BluetoothSocket
                socket?.connect()
            } catch (e2: Exception) {
                throw IOException("Failed to connect", e2)
            }
        }

        if (socket == null) {
            throw IOException("Failed to connect")
        }

        connectionThread = ConnectionThread(socket).apply {
            start()
        }
    }

    @Throws(IOException::class)
    override suspend fun connect(address: String) {
        connect(address, DEFAULT_UUID)
    }

    override fun disconnect() {
        if (isConnected()) {
            connectionThread?.cancel()
            connectionThread = null
        }
    }

    @Throws(IOException::class)
    override fun write(data: ByteArray) {
        if (!isConnected()) {
            throw IOException("not connected")
        }
        connectionThread?.write(data)
    }

    private inner class ConnectionThread(
        private val socket: BluetoothSocket
    ) : Thread() {

        @Volatile
        var requestedClosing = false

        private val input: InputStream? = runCatching { socket.inputStream }.getOrNull()
        private val output: OutputStream? = runCatching { socket.outputStream }.getOrNull()

        override fun run() {
            val buffer = ByteArray(1024)

            while (!requestedClosing) {
                try {
                    val stream = input ?: break
                    val bytes = stream.read(buffer)
                    onRead(buffer.copyOf(bytes))
                } catch (_: IOException) {
                    break
                }
            }

            // Make sure output stream is closed
            runCatching { output?.close() }

            // Make sure input stream is closed
            runCatching { input?.close() }

            // Callback on disconnected, with information which side is closing
            onDisconnected(!requestedClosing)

            // Just prevent unnecessary `cancel`ing
            requestedClosing = true
        }

        fun write(bytes: ByteArray) {
            runCatching {
                output?.write(bytes)
            }.onFailure { e ->
                e.printStackTrace()
            }
        }

        fun cancel() {
            if (requestedClosing) {
                return
            }
            requestedClosing = true

            // Flush output buffers before closing
            runCatching { output?.flush() }

            // Close the connection socket
            runCatching {
                // Might be useful (see https://stackoverflow.com/a/22769260/4880243)
                sleep(111)
                socket.close()
            }
        }
    }
}
