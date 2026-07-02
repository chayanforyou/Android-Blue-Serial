package io.github.chayanforyou.bluedemo.ui.screens.device

import io.github.chayanforyou.bluedemo.data.Device
import io.github.chayanforyou.bluedemo.data.DeviceType
import io.github.chayanforyou.bluedemo.utils.PermissionHelper
import androidx.compose.ui.graphics.Color

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import io.github.chayanforyou.bluedemo.utils.parcelable

@SuppressLint("MissingPermission")
@Composable
fun BluetoothClassicContent(
    onScanningStateChanged: (Boolean) -> Unit,
    onRegisterScanAction: (() -> Unit) -> Unit,
    onDeviceSelected: (device: Device) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val bluetoothAdapter = remember { bluetoothManager?.adapter }

    val classicDevices = remember { mutableStateListOf<BluetoothDevice>() }
    var isScanning by remember { mutableStateOf(false) }

    // Notify scanning state change
    LaunchedEffect(isScanning) {
        onScanningStateChanged(isScanning)
    }

    val triggerScan = {
        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Bluetooth not supported on this device", Toast.LENGTH_SHORT)
                .show()
        } else {
            startScan(context, bluetoothAdapter)
        }
    }

    LaunchedEffect(triggerScan) {
        onRegisterScanAction(triggerScan)
    }

    // Load bonded devices initially
    LaunchedEffect(bluetoothAdapter) {
        loadBondedDevices(context, bluetoothAdapter, classicDevices)
    }

    // Dynamic broadcast receiver to update scans
    DisposableEffect(context, bluetoothAdapter) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        isScanning = true
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isScanning = false
                    }

                    BluetoothDevice.ACTION_FOUND -> {
                        val device =
                            intent.parcelable<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && device.type != BluetoothDevice.DEVICE_TYPE_LE) {
                            val isDuplicate = classicDevices.any { it.address == device.address }
                            if (!isDuplicate) {
                                classicDevices.add(device)
                            }
                        }
                    }
                }
            }
        }

        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
            if (bluetoothAdapter != null) {
                if (PermissionHelper.hasScanPermission(context)) {
                    bluetoothAdapter.cancelDiscovery()
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        items(classicDevices.map { it.toUiDevice(context) }) { device ->
            DeviceRow(
                device = device,
                onClick = {
                    if (bluetoothAdapter != null) {
                        if (PermissionHelper.hasScanPermission(context)) {
                            bluetoothAdapter.cancelDiscovery()
                        }
                    }
                    onDeviceSelected(device)
                }
            )
            HorizontalDivider(
                color = Color.DarkGray,
                thickness = 1.dp
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun loadBondedDevices(
    context: Context,
    adapter: BluetoothAdapter?,
    list: MutableList<BluetoothDevice>
) {
    if (adapter == null) return
    if (!PermissionHelper.hasConnectPermission(context)) {
        return
    }
    adapter.bondedDevices.forEach { device ->
        if (device.type != BluetoothDevice.DEVICE_TYPE_LE) {
            val isDuplicate = list.any { it.address == device.address }
            if (!isDuplicate) {
                list.add(device)
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun startScan(context: Context, adapter: BluetoothAdapter) {
    if (!PermissionHelper.hasScanPermission(context)) {
        return
    }
    if (adapter.isDiscovering) {
        adapter.cancelDiscovery()
    }
    adapter.startDiscovery()
}

@SuppressLint("MissingPermission")
private fun BluetoothDevice.toUiDevice(context: Context): Device {
    val name = if (PermissionHelper.hasConnectPermission(context)) {
        this.name
    } else {
        null
    }
    val deviceName = if (name.isNullOrEmpty()) "<unnamed>" else name
    return Device(
        name = deviceName,
        address = this.address,
        type = DeviceType.CLASSIC
    )
}
