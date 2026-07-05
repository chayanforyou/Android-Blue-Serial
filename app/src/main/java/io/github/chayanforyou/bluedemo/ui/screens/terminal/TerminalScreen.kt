package io.github.chayanforyou.bluedemo.ui.screens.terminal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chayanforyou.bluedemo.data.Device
import io.github.chayanforyou.bluedemo.data.DeviceType
import io.github.chayanforyou.bluedemo.ui.theme.ConsoleButtonBg
import io.github.chayanforyou.bluedemo.ui.theme.ConsoleIncoming
import io.github.chayanforyou.bluedemo.ui.theme.ConsoleOutgoing
import io.github.chayanforyou.bluedemo.ui.theme.ConsoleStatus
import io.github.chayanforyou.bluedemo.ui.theme.ConsoleTimestamp
import io.github.chayanforyou.blueserial.BluetoothConnectionBase
import io.github.chayanforyou.blueserial.BluetoothConnectionClassic
import io.github.chayanforyou.blueserial.le.BluetoothConnectionLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogType {
    Status,
    Incoming,
    Outgoing
}

data class LogEntry(
    val text: String,
    val timestamp: String = getCurrentTime(),
    val type: LogType = LogType.Status
)

private fun getCurrentTime(): String {
    val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return formatter.format(Date())
}

// Extension to draw bottom border on input text field
fun Modifier.drawBehindBottomBorder(color: Color): Modifier = this.drawBehind {
    val strokeWidth = 1.dp.toPx()
    val y = size.height - strokeWidth / 2
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = strokeWidth
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    device: Device,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logs = remember { mutableStateListOf<LogEntry>() }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    val lazyListState = rememberLazyListState()

    // Read and disconnected callbacks
    val readCallback = remember {
        BluetoothConnectionBase.OnReadCallback { data: ByteArray ->
            val text = String(data)
            logs.add(LogEntry(text, type = LogType.Incoming))
        }
    }

    val disconnectedCallback = remember {
        BluetoothConnectionBase.OnDisconnectedCallback { _ ->
            scope.launch(Dispatchers.Main) {
                logs.add(LogEntry("Disconnected from device"))
                isConnected = false
                isConnecting = false
            }
        }
    }

    // Bluetooth connection instance
    val connection = remember {
        if (device.type == DeviceType.LE) {
            BluetoothConnectionLE(context, readCallback, disconnectedCallback)
        } else {
            BluetoothConnectionClassic(context, readCallback, disconnectedCallback)
        }
    }

    // Connect function
    val connectDevice = {
        if (!isConnecting && !isConnected) {
            isConnecting = true
            logs.add(LogEntry("Connecting to ${device.name}..."))
            scope.launch(Dispatchers.IO) {
                try {
                    connection.connect(device.address)
                    withContext(Dispatchers.Main) {
                        logs.add(LogEntry("Connected"))
                        isConnected = true
                        isConnecting = false
                    }
                } catch (e: Exception) {
                    val message = e.cause?.message ?: e.message ?: ""
                    withContext(Dispatchers.Main) {
                        logs.add(LogEntry("Connection failed: $message"))
                        isConnected = false
                        isConnecting = false
                    }
                }
            }
        }
    }

    // Disconnect function
    val disconnectDevice = {
        if (isConnected || isConnecting) {
            connection.disconnect()
            isConnected = false
            isConnecting = false
            logs.add(LogEntry("Disconnected"))
        }
    }

    // Send function
    val sendData = { text: String ->
        if (isConnected) {
            scope.launch(Dispatchers.IO) {
                try {
                    connection.write(text.toByteArray())
                    withContext(Dispatchers.Main) {
                        logs.add(LogEntry(text, type = LogType.Outgoing))
                    }
                } catch (e: Exception) {
                    val message = e.cause?.message ?: e.message ?: ""
                    withContext(Dispatchers.Main) {
                        logs.add(LogEntry("Send error: $message"))
                    }
                }
            }
        } else {
            Toast.makeText(context, "Serial device not connected", Toast.LENGTH_SHORT).show()
        }
    }

    // Connect automatically on screen entry
    LaunchedEffect(device.address) {
        connectDevice()
    }

    // Clean up connection on dispose
    DisposableEffect(Unit) {
        onDispose {
            connection.disconnect()
        }
    }

    // Auto-scroll lazy list to bottom when new logs are added
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            lazyListState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Terminal",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Connection Status toggle button (Plug icon replacement)
                    IconButton(
                        onClick = {
                            if (isConnected) disconnectDevice() else connectDevice()
                        }
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Rounded.Link else Icons.Rounded.LinkOff,
                            contentDescription = "Connection Toggle",
                        )
                    }
                    // Clear log console button (Trash bin)
                    IconButton(onClick = { logs.clear() }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Clear Logs"
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Logs View Console
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                LazyColumn(
                    state = lazyListState,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { log ->
                        val textColor = when (log.type) {
                            LogType.Status -> ConsoleStatus
                            LogType.Outgoing -> ConsoleOutgoing
                            LogType.Incoming -> ConsoleIncoming
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${log.timestamp} ",
                                color = ConsoleTimestamp,
                                fontSize = 14.sp,
                                lineHeight = 14.sp,
                            )
                            Text(
                                text = log.text,
                                color = textColor,
                                fontSize = 14.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }


            // Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Input TextField with bottom border as in standard terminal UI
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { sendData(inputText) }
                    ),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .drawBehindBottomBorder(Color.Gray),
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Type a command...",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button inside a box matching screen UI
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(50.dp, 40.dp)
                        .background(ConsoleButtonBg, shape = RoundedCornerShape(4.dp))
                        .clickable { sendData(inputText) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
