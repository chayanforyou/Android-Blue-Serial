package io.github.chayanforyou.bluedemo.ui.screens.device

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import io.github.chayanforyou.bluedemo.data.Device
import io.github.chayanforyou.bluedemo.utils.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    onDeviceSelected: (device: Device) -> Unit,
) {
    val context = LocalContext.current
    val bluetoothManager =
        remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val bluetoothAdapter = remember { bluetoothManager?.adapter }

    var hasPermissions by remember { mutableStateOf(PermissionHelper.hasBluetoothPermissions(context)) }
    var isBluetoothEnabled by remember {
        mutableStateOf(
            if (bluetoothAdapter == null) {
                true
            } else {
                try {
                    bluetoothAdapter.isEnabled
                } catch (e: SecurityException) {
                    false
                }
            }
        )
    }

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
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false
            val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
            hasPermissions = scanGranted && connectGranted
            if (hasPermissions) {
                if (bluetoothAdapter != null) {
                    isBluetoothEnabled = bluetoothAdapter.isEnabled
                    if (!isBluetoothEnabled) {
                        try {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            bluetoothEnableLauncher.launch(enableBtIntent)
                        } catch (e: SecurityException) {
                            // Safe catch
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Bluetooth permissions are required", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    val launchPermissionRequest = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        }
    }

    val launchBluetoothEnable = {
        try {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        } catch (e: SecurityException) {
            launchPermissionRequest()
        }
    }

    LaunchedEffect(Unit) {
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            launchBluetoothEnable()
        } else if (!hasPermissions) {
            launchPermissionRequest()
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions && bluetoothAdapter != null) {
            isBluetoothEnabled = bluetoothAdapter.isEnabled
        }
    }

    LaunchedEffect(isBluetoothEnabled) {
        if (isBluetoothEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermissions) {
            launchPermissionRequest()
        }
    }

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
                            onClick = { scanTrigger?.invoke() },
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
            if (bluetoothAdapter != null && !isBluetoothEnabled) {
                PlaceholderContent(
                    text = "Bluetooth is disabled",
                    buttonText = "Enable Bluetooth",
                    onButtonClick = launchBluetoothEnable
                )
            } else if (!hasPermissions) {
                PlaceholderContent(
                    text = "Bluetooth permission is missing",
                    buttonText = "Allow Permission",
                    onButtonClick = launchPermissionRequest
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = device.address,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}