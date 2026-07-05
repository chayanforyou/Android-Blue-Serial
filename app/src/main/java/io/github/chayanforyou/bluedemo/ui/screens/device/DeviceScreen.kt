package io.github.chayanforyou.bluedemo.ui.screens.device

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.location.LocationManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.github.chayanforyou.bluedemo.utils.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    onDeviceSelected: (device: Device) -> Unit,
) {
    val context = LocalContext.current

    // --- State & Remembered Variables ---
    var hasPermissions by remember { mutableStateOf(PermissionHelper.hasConnectPermission(context)) }
    var isBluetoothEnabled by remember { mutableStateOf(isBluetoothEnabled(context)) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isClassicScanning by remember { mutableStateOf(false) }
    var classicScanTrigger by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isLeScanning by remember { mutableStateOf(false) }
    var leScanTrigger by remember { mutableStateOf<(() -> Unit)?>(null) }

    val isScanning = if (selectedTabIndex == 0) isClassicScanning else isLeScanning
    val scanTrigger = if (selectedTabIndex == 0) classicScanTrigger else leScanTrigger

    val titleText = when {
        isClassicScanning || isLeScanning -> "Scanning..."
        else -> "Devices"
    }

    // --- Activity Result Launchers ---
    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isBluetoothEnabled = true
        } else {
            isBluetoothEnabled = false
            Toast.makeText(context, "Bluetooth is required to scan", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermissions = isGranted
        if (isGranted) {
            if (!isBluetoothEnabled) {
                requestBluetoothEnable(bluetoothEnableLauncher)
            }
        } else {
            Toast.makeText(context, "Bluetooth permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    val resolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scanTrigger?.invoke()
        }
    }

    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val scanPermissionsGranted = PermissionHelper.hasScanPermissions(context)
        if (scanPermissionsGranted) {
            checkGpsAndScan(context, resolutionLauncher) {
                scanTrigger?.invoke()
            }
        } else {
            Toast.makeText(
                context,
                "Scanning requires location and scan permissions",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else if (!isBluetoothEnabled) {
            requestBluetoothEnable(bluetoothEnableLauncher)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = titleText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                if (!PermissionHelper.hasScanPermissions(context)) {
                                    scanPermissionLauncher.launch(PermissionHelper.getScanPermissions())
                                } else {
                                    checkGpsAndScan(context, resolutionLauncher) {
                                        scanTrigger?.invoke()
                                    }
                                }
                            },
                            enabled = !isScanning,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(
                                    alpha = 0.38f
                                )
                            )
                        ) {
                            Text(
                                text = "SCAN",
                                fontSize = 14.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Bluetooth Classic") }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Bluetooth LE") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!hasPermissions) {
                PlaceholderContent(
                    text = "Bluetooth permission is missing",
                    buttonText = "Allow Permission",
                    onButtonClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }
                )
            } else if (!isBluetoothEnabled) {
                PlaceholderContent(
                    text = "Bluetooth is disabled",
                    buttonText = "Enable Bluetooth",
                    onButtonClick = { requestBluetoothEnable(bluetoothEnableLauncher) }
                )
            } else {
                when (selectedTabIndex) {
                    0 -> {
                        BluetoothClassicContent(
                            onScanningStateChanged = { isClassicScanning = it },
                            onRegisterScanAction = { classicScanTrigger = it },
                            onDeviceSelected = onDeviceSelected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    1 -> {
                        BluetoothLEContent(
                            onScanningStateChanged = { isLeScanning = it },
                            onRegisterScanAction = { leScanTrigger = it },
                            onDeviceSelected = onDeviceSelected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderContent(
    text: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onButtonClick) {
            Text(text = buttonText)
        }
    }
}

@Composable
fun DeviceRow(
    device: Device,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = device.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = device.address,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

private fun isBluetoothEnabled(context: Context): Boolean {
    try {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter?.isEnabled ?: true
    } catch (_: Exception) {
        return false
    }
}

private fun requestBluetoothEnable(launcher: ActivityResultLauncher<Intent>) {
    try {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        launcher.launch(enableBtIntent)
    } catch (_: Exception) {
        // Safe catch
    }
}

private fun checkGpsAndScan(
    context: Context,
    resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>,
    onGpsEnabled: () -> Unit
) {
    val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        turnOnGPS(context, resolutionLauncher) {
            onGpsEnabled()
        }
    } else {
        onGpsEnabled()
    }
}

private fun turnOnGPS(
    context: Context,
    resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>,
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