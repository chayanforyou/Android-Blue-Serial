package io.github.chayanforyou.blueserial.le

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
internal class SerialSocket(
    private val context: Context,
    private var device: BluetoothDevice?,
    private val customServiceUuid: UUID? = null
) : BluetoothGattCallback() {

    companion object {
        private val BLUETOOTH_LE_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val EXCLUDED_SERVICE_PREFIXES = setOf(
            "00001800", // Generic Access
            "00001801", // Generic Attribute
            "0000180a", // Device Information
            "0000180f"  // Battery Service
        )
        private const val MAX_MTU = 512
        private const val DEFAULT_MTU = 23
    }

    private var listener: SerialListener? = null
    private var gatt: BluetoothGatt? = null
    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val writeBuffer = ArrayList<ByteArray>()

    private var writePending = false
    private var canceled = false
    private var connected = false
    private var payloadSize = DEFAULT_MTU - 3

    private val pairingBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onPairingBroadcastReceive(intent)
        }
    }

    @Throws(IOException::class)
    internal fun connect(listener: SerialListener) {
        if (connected || gatt != null) {
            throw IOException("already connected")
        }
        canceled = false
        this.listener = listener
        context.registerReceiver(
            pairingBroadcastReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            }
        )

        val dev = device ?: throw IOException("device is null")
        gatt = if (Build.VERSION.SDK_INT < 23) {    // Android 6.0 (October 2015)
            dev.connectGatt(context, false, this)
        } else {
            dev.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE)
        }
        if (gatt == null) {
            throw IOException("connectGatt failed")
        }
    }

    @Throws(IOException::class)
    internal fun write(data: ByteArray) {
        if (canceled || !connected || writeCharacteristic == null) {
            throw IOException("not connected")
        }

        val writeChar = writeCharacteristic
            ?: throw IOException("write characteristic is null")

        var writeNow = false
        val dataToWrite = if (data.size <= payloadSize) data else data.copyOfRange(0, payloadSize)

        synchronized(writeBuffer) {
            if (!writePending && writeBuffer.isEmpty()) {
                writePending = true
                writeNow = true
            } else {
                writeBuffer.add(dataToWrite)
            }

            if (data.size > payloadSize) {
                for (i in 1 until (data.size + payloadSize - 1) / payloadSize) {
                    val from = i * payloadSize
                    val to = minOf(from + payloadSize, data.size)
                    writeBuffer.add(data.copyOfRange(from, to))
                }
            }
        }

        if (writeNow) {
            writeChar.value = dataToWrite
            val activeGatt = gatt ?: throw IOException("gatt is null")
            if (!activeGatt.writeCharacteristic(writeChar)) {
                onSerialIoError(IOException("write failed"))
            }
        }
    }

    internal fun disconnect() {
        listener = null // ignore remaining data and errors
        device = null
        canceled = true
        synchronized(writeBuffer) {
            writePending = false
            writeBuffer.clear()
        }
        readCharacteristic = null
        writeCharacteristic = null
        gatt?.let {
            it.disconnect()
            runCatching { it.close() }
            gatt = null
            connected = false
        }
        runCatching {
            context.unregisterReceiver(pairingBroadcastReceiver)
        }
    }

    private fun onPairingBroadcastReceive(intent: Intent) {
        val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (dev == null || dev != this.device) return

        if (intent.action == BluetoothDevice.ACTION_PAIRING_REQUEST) {
            onSerialConnectError(IOException("Pairing requested: pair and connect again"))
        }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (!gatt.discoverServices()) {
                onSerialConnectError(IOException("discoverServices failed"))
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            if (connected) {
                onSerialIoError(IOException("gatt status $status"))
            } else {
                onSerialConnectError(IOException("gatt status $status"))
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (canceled) return
        writePending = false
        if (customServiceUuid != null) {
            val gattService = gatt.getService(customServiceUuid)
            if (gattService != null) {
                discoverCharacteristics(gattService)
            }
        } else {
            // Search for any service (excluding common non-serial ones) that can act as a serial connection
            for (gattService in gatt.services) {
                val serviceUuidStr = gattService.uuid.toString().lowercase()
                if (serviceUuidStr.take(8) in EXCLUDED_SERVICE_PREFIXES) continue

                discoverCharacteristics(gattService)
                if (readCharacteristic != null && writeCharacteristic != null) break
                readCharacteristic = null
                writeCharacteristic = null
            }
        }

        if (canceled) return
        if (readCharacteristic == null || writeCharacteristic == null) {
            onSerialConnectError(IOException("no serial profile found"))
            return
        }

        if (!gatt.requestMtu(MAX_MTU)) {
            onSerialConnectError(IOException("request MTU failed"))
        }
    }

    private fun discoverCharacteristics(service: BluetoothGattService) {
        var autoWrite: BluetoothGattCharacteristic? = null
        var autoRead: BluetoothGattCharacteristic? = null

        for (char in service.characteristics) {
            val props = char.properties
            val isWritable = (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
            val isReadable = (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

            if (isWritable && isReadable) {
                // Combined characteristic — use for both directions and stop searching
                writeCharacteristic = char
                readCharacteristic = char
                return
            }
            if (isWritable && autoWrite == null) autoWrite = char
            if (isReadable && autoRead == null) autoRead = char
            if (autoWrite != null && autoRead != null) break
        }

        writeCharacteristic = autoWrite
        readCharacteristic = autoRead
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            payloadSize = mtu - 3
        }

        val writeChar = writeCharacteristic
            ?: return onSerialConnectError(IOException("write characteristic is null"))

        if ((writeChar.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
            return onSerialConnectError(IOException("write characteristic not writable"))
        }

        val readChar = readCharacteristic
            ?: return onSerialConnectError(IOException("read characteristic is null"))

        if (!gatt.setCharacteristicNotification(readChar, true)) {
            return onSerialConnectError(IOException("no notification for read characteristic"))
        }

        val readDescriptor = readChar.getDescriptor(BLUETOOTH_LE_CCCD)
            ?: return onSerialConnectError(IOException("no CCCD descriptor for read characteristic"))
        val readProperties = readChar.properties

        readDescriptor.value = when {
            (readProperties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            (readProperties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> return onSerialConnectError(IOException("no indication/notification for read characteristic ($readProperties)"))
        }

        if (!gatt.writeDescriptor(readDescriptor)) {
            onSerialConnectError(IOException("read characteristic CCCD descriptor not writable"))
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        if (canceled) return
        if (descriptor.characteristic == readCharacteristic) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onSerialConnectError(IOException("write descriptor failed"))
            } else {
                connected = true
                onSerialConnect()
            }
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ) {
        if (canceled) return
        if (characteristic == readCharacteristic) {
            onSerialRead(data)
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (canceled || !connected || writeCharacteristic == null) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            onSerialIoError(IOException("write failed"))
            return
        }
        if (characteristic == writeCharacteristic) {
            writeNext()
        }
    }

    private fun writeNext() {
        val writeChar = writeCharacteristic ?: return
        val data: ByteArray?
        synchronized(writeBuffer) {
            if (writeBuffer.isNotEmpty()) {
                writePending = true
                data = writeBuffer.removeAt(0)
            } else {
                writePending = false
                data = null
            }
        }

        if (data != null) {
            writeChar.value = data
            val activeGatt = gatt
            if (activeGatt == null || !activeGatt.writeCharacteristic(writeChar)) {
                onSerialIoError(IOException("write failed"))
            }
        }
    }

    private fun onSerialConnect() {
        listener?.onSerialConnect()
    }

    private fun onSerialConnectError(e: Exception) {
        canceled = true
        listener?.onSerialConnectError(e)
    }

    private fun onSerialRead(data: ByteArray) {
        listener?.onSerialRead(data)
    }

    private fun onSerialIoError(e: Exception) {
        writePending = false
        canceled = true
        listener?.onSerialIoError(e)
    }
}
