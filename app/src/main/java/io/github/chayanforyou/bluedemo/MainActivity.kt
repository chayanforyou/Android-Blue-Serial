package io.github.chayanforyou.bluedemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.chayanforyou.bluedemo.data.Device
import io.github.chayanforyou.bluedemo.ui.screens.device.DeviceScreen
import io.github.chayanforyou.bluedemo.ui.screens.terminal.TerminalScreen
import io.github.chayanforyou.bluedemo.ui.theme.AppTheme

enum class AppScreen {
    DeviceList,
    Terminal
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.DeviceList) }
                var selectedDevice by remember { mutableStateOf<Device?>(null) }

                when (currentScreen) {
                    AppScreen.DeviceList -> {
                        DeviceScreen(
                            onDeviceSelected = { device ->
                                selectedDevice = device
                                currentScreen = AppScreen.Terminal
                            }
                        )
                    }
                    AppScreen.Terminal -> {
                        val device = selectedDevice
                        if (device != null) {
                            TerminalScreen(
                                device = device,
                                onBackClick = {
                                    currentScreen = AppScreen.DeviceList
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}