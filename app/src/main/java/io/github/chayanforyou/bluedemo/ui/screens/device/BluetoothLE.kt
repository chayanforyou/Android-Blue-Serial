package io.github.chayanforyou.bluedemo.ui.screens.device

import io.github.chayanforyou.bluedemo.utils.PermissionHelper

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
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
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.Builder.IMPLICIT_MIN_UPDATE_INTERVAL
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import io.github.chayanforyou.bluedemo.data.Device
import io.github.chayanforyou.bluedemo.data.DeviceType

@SuppressLint("MissingPermission")
@Composable
fun BluetoothLEContent(
    onScanningStateChanged: (Boolean) -> Unit,
    onRegisterScanAction: (() -> Unit) -> Unit,
    onDeviceSelected: (device: Device) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bluetoothManager =
        remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val bluetoothAdapter = remember { bluetoothManager?.adapter }
    val bluetoothLeScanner = remember { bluetoothAdapter?.bluetoothLeScanner }

    val leDevices = remember { mutableStateListOf<BluetoothDevice>() }
    var isScanning by remember { mutableStateOf(false) }

    val handler = remember { Handler(Looper.getMainLooper()) }

    // Notify scanning state change
    LaunchedEffect(isScanning) {
        onScanningStateChanged(isScanning)
    }

    val leScanCallback = remember {
        object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                val device = result?.device
                if (device != null) {
                    val isDuplicate = leDevices.any { it.address == device.address }
                    if (!isDuplicate) {
                        leDevices.add(device)
                    }
                }
            }
        }
    }

    val stopScan = {
        if (isScanning) {
            if (bluetoothLeScanner != null) {
                if (PermissionHelper.hasScanPermission(context)) {
                    bluetoothLeScanner.stopScan(leScanCallback)
                }
            }
            isScanning = false
        }
    }

    val startScan = {
        if (bluetoothLeScanner != null) {
            if (PermissionHelper.hasScanPermission(context)) {

                leDevices.clear()
                // Re-load bonded non-classic devices
                loadBondedLeDevices(context, bluetoothAdapter, leDevices)

                isScanning = true
                bluetoothLeScanner.startScan(leScanCallback)

                handler.postDelayed({
                    stopScan()
                }, 10000) // LE_SCAN_PERIOD = 10000
            }
        }
    }

    val resolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startScan()
        }
    }

    val requestLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                turnOnGPS(context, resolutionLauncher) {
                    startScan()
                }
            } else {
                startScan()
            }
        }
    }

    val triggerScan = {
        if (bluetoothAdapter == null || bluetoothLeScanner == null) {
            Toast.makeText(context, "Bluetooth LE not supported", Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val hasLocation = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasLocation) {
                    val locationManager =
                        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        turnOnGPS(context, resolutionLauncher) {
                            startScan()
                        }
                    } else {
                        startScan()
                    }
                } else {
                    requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            } else {
                startScan()
            }
        }
    }

    LaunchedEffect(triggerScan) {
        onRegisterScanAction(triggerScan)
    }

    // Load bonded devices initially
    LaunchedEffect(bluetoothAdapter) {
        loadBondedLeDevices(context, bluetoothAdapter, leDevices)
    }

    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacksAndMessages(null)
            if (isScanning && bluetoothLeScanner != null) {
                if (PermissionHelper.hasScanPermission(context)) {
                    bluetoothLeScanner.stopScan(leScanCallback)
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        items(leDevices.map { it.toUiDevice(context) }) { device ->
            DeviceRow(
                device = device,
                onClick = {
                    stopScan()
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
private fun loadBondedLeDevices(
    context: Context,
    adapter: BluetoothAdapter?,
    list: MutableList<BluetoothDevice>
) {
    if (adapter == null) return
    if (!PermissionHelper.hasConnectPermission(context)) {
        return
    }
    adapter.bondedDevices.forEach { device ->
        if (device.type != BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            val isDuplicate = list.any { it.address == device.address }
            if (!isDuplicate) {
                list.add(device)
            }
        }
    }
}

private fun turnOnGPS(
    context: Context,
    resolutionLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    onGpsEnabled: () -> Unit
) {
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).apply {
        setWaitForAccurateLocation(false)
        setMinUpdateIntervalMillis(IMPLICIT_MIN_UPDATE_INTERVAL)
        setMaxUpdateDelayMillis(100000)
    }.build()

    val builder = LocationSettingsRequest.Builder().addLocationRequest(request)
    val client: SettingsClient = LocationServices.getSettingsClient(context)
    val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
    task.addOnSuccessListener {
        onGpsEnabled()
    }
    task.addOnFailureListener { exception ->
        if (exception is ResolvableApiException) {
            try {
                val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                resolutionLauncher.launch(intentSenderRequest)
            } catch (_: IntentSender.SendIntentException) {
            }
        }
    }
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
        type = DeviceType.LE
    )
}
