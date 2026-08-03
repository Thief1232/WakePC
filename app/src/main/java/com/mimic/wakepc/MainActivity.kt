package com.mimic.wakepc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mimic.wakepc.ui.theme.WakePCTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WakePCTheme {
                WakePcApp()
            }
        }
    }
}

@Composable
private fun WakePcApp() {
    val context = LocalContext.current
    val repository = remember(context) { DeviceRepository(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var savedDevices by remember { mutableStateOf(repository.loadDevices()) }
    var discoveredDevices by remember { mutableStateOf(emptyList<DiscoveredDevice>()) }
    var editedDevice by remember { mutableStateOf<SavedDevice?>(null) }
    var originalDevice by remember { mutableStateOf<SavedDevice?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Нажмите «Поиск», чтобы найти устройства") }
    var wakingIp by remember { mutableStateOf<String?>(null) }

    fun openEditor(device: SavedDevice, original: SavedDevice? = null) {
        editedDevice = device
        originalDevice = original
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("WakePC", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Компьютеры и устройства в локальной сети",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        isScanning = true
                        statusMessage = "Поиск устройств в сети..."
                        coroutineScope.launch {
                            NetworkScanner.scan().fold(
                                onSuccess = { devices ->
                                    discoveredDevices = devices
                                    statusMessage = "Найдено устройств: ${devices.size}"
                                },
                                onFailure = { error ->
                                    statusMessage = error.message ?: "Ошибка поиска"
                                }
                            )
                            isScanning = false
                        }
                    },
                    enabled = !isScanning
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Поиск")
                    }
                }
                OutlinedButton(
                    onClick = {
                        openEditor(SavedDevice("Новый компьютер", "", "", 9))
                    }
                ) {
                    Text("Добавить вручную")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (savedDevices.isNotEmpty()) {
                    item {
                        Text("Сохраненные", style = MaterialTheme.typography.titleLarge)
                    }
                    items(savedDevices, key = { "saved-${it.ipAddress}-${it.macAddress}" }) { device ->
                        SavedDeviceCard(
                            device = device,
                            isWaking = wakingIp == device.ipAddress,
                            onEdit = { openEditor(device, device) },
                            onWake = {
                                wakingIp = device.ipAddress
                                statusMessage = "Отправка пакета для ${device.name}..."
                                coroutineScope.launch {
                                    WakeOnLan.sendMagicPacket(
                                        macAddress = device.macAddress,
                                        broadcastIp = broadcastAddressFor(device.ipAddress),
                                        port = device.port
                                    ).fold(
                                        onSuccess = { statusMessage = "Magic packet отправлен: ${device.name}" },
                                        onFailure = { statusMessage = it.message ?: "Ошибка отправки" }
                                    )
                                    wakingIp = null
                                }
                            }
                        )
                    }
                }

                if (discoveredDevices.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Найдены в сети", style = MaterialTheme.typography.titleLarge)
                    }
                    items(discoveredDevices, key = { "found-${it.ipAddress}" }) { device ->
                        DiscoveredDeviceCard(device) {
                            openEditor(
                                SavedDevice(
                                    name = device.name,
                                    ipAddress = device.ipAddress,
                                    macAddress = device.macAddress,
                                    port = 9
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    editedDevice?.let { device ->
        DeviceEditorDialog(
            initialDevice = device,
            canDelete = originalDevice != null,
            onDismiss = { editedDevice = null },
            onSave = { updatedDevice ->
                savedDevices = if (originalDevice == null) {
                    savedDevices + updatedDevice
                } else {
                    savedDevices.map { if (it == originalDevice) updatedDevice else it }
                }
                repository.saveDevices(savedDevices)
                statusMessage = "${updatedDevice.name} сохранен"
                editedDevice = null
            },
            onDelete = {
                savedDevices = savedDevices.filterNot { it == originalDevice }
                repository.saveDevices(savedDevices)
                statusMessage = "Устройство удалено"
                editedDevice = null
            }
        )
    }
}

@Composable
private fun SavedDeviceCard(
    device: SavedDevice,
    isWaking: Boolean,
    onEdit: () -> Unit,
    onWake: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text("IP: ${device.ipAddress}")
            Text("MAC: ${device.macAddress}")
            Text("Порт: ${device.port}")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onWake, enabled = !isWaking) {
                    Text(if (isWaking) "Отправка..." else "Включить")
                }
                TextButton(onClick = onEdit) { Text("Настроить") }
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text("IP: ${device.ipAddress}")
            Text("MAC: ${device.macAddress.ifBlank { "не определен" }}")
            Text("Нажмите, чтобы настроить и сохранить")
        }
    }
}

@Composable
private fun DeviceEditorDialog(
    initialDevice: SavedDevice,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (SavedDevice) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(initialDevice) { mutableStateOf(initialDevice.name) }
    var ipAddress by remember(initialDevice) { mutableStateOf(initialDevice.ipAddress) }
    var macAddress by remember(initialDevice) {
        mutableStateOf(
            TextFieldValue(
                text = initialDevice.macAddress,
                selection = TextRange(initialDevice.macAddress.length)
            )
        )
    }
    var portText by remember(initialDevice) { mutableStateOf(initialDevice.port.toString()) }
    var errorMessage by remember(initialDevice) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Параметры устройства") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(
                    ipAddress,
                    { ipAddress = it },
                    label = { Text("IP-адрес") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    macAddress,
                    { macAddress = formatMacAddress(it) },
                    label = { Text("MAC-адрес") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                OutlinedTextField(
                    portText,
                    { portText = it },
                    label = { Text("Порт") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                if (canDelete) {
                    TextButton(onClick = onDelete) { Text("Удалить устройство") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val device = SavedDevice(
                        name = name.trim(),
                        ipAddress = ipAddress.trim(),
                        macAddress = macAddress.text.trim().uppercase().replace('-', ':'),
                        port = portText.toIntOrNull() ?: 0
                    )
                    errorMessage = validateDevice(device)
                    if (errorMessage == null) onSave(device)
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun validateDevice(device: SavedDevice): String? {
    if (device.name.isBlank()) return "Введите название устройства"
    if (!IPV4_REGEX.matches(device.ipAddress)) return "Введите корректный IPv4-адрес"
    if (!MAC_REGEX.matches(device.macAddress)) return "MAC должен иметь формат AA:BB:CC:DD:EE:FF"
    if (device.port !in 1..65535) return "Порт должен быть числом от 1 до 65535"
    return null
}

private fun broadcastAddressFor(ipAddress: String): String =
    "${ipAddress.substringBeforeLast('.')}.255"

private fun formatMacAddress(input: TextFieldValue): TextFieldValue {
    val hexCharacters = input.text
        .uppercase()
        .filter { it in '0'..'9' || it in 'A'..'F' }
        .take(12)
    val formattedText = hexCharacters.chunked(2).joinToString(":")
    val hexCharactersBeforeCursor = input.text
        .take(input.selection.start)
        .count { it.uppercaseChar() in '0'..'9' || it.uppercaseChar() in 'A'..'F' }
        .coerceAtMost(12)
    val cursorPosition = if (hexCharactersBeforeCursor == 0) {
        0
    } else {
        hexCharactersBeforeCursor + (hexCharactersBeforeCursor - 1) / 2
    }.coerceAtMost(formattedText.length)

    return TextFieldValue(
        text = formattedText,
        selection = TextRange(cursorPosition)
    )
}

private val IPV4_REGEX = Regex(
    "^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$"
)
private val MAC_REGEX = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
