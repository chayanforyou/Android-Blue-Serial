package io.github.chayanforyou.blueserial

abstract class BluetoothConnectionBase(
    protected val onReadCallback: OnReadCallback,
    protected val onDisconnectedCallback: OnDisconnectedCallback
) : BluetoothConnection {

    /// Callback for reading data.
    fun interface OnReadCallback {
        fun onRead(data: ByteArray)
    }

    /// Callback for disconnection.
    fun interface OnDisconnectedCallback {
        fun onDisconnected(byRemote: Boolean)
    }

    open fun onRead(data: ByteArray) {
        onReadCallback.onRead(data)
    }

    open fun onDisconnected(byRemote: Boolean) {
        onDisconnectedCallback.onDisconnected(byRemote)
    }
}
