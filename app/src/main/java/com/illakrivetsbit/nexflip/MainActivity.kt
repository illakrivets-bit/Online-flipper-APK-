package com.illakrivetsbit.nexflip

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.hardware.ConsumerIrManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.content.Intent
import android.content.ContentValues
import android.content.ClipboardManager
import android.content.ClipData
import android.provider.MediaStore
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.charset.Charset
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private val Background = Color(0xFF080B0A)
private val Panel = Color(0xFF141A17)
private val Accent = Color(0xFFB6F36B)
private val Muted = Color(0xFF8B968E)

private data class Module(
    val name: String,
    val symbol: String,
    val description: String,
    val detail: String
)

private val modules = listOf(
    Module("Bluetooth", "ᛒ", "Nearby device connections", "Scan for nearby Bluetooth Low Energy devices and connect to compatible peripherals."),
    Module("Wi-Fi", "⌁", "Find devices on your Wi-Fi", "Discover services advertised by devices on your local network and open supported web interfaces."),
    Module("Infrared", "◉", "TVs and projectors", "Requires a phone with an IR transmitter or a supported external IR accessory. Device-specific remote profiles will be added."),
    Module("NFC / RFID", "▦", "Read compatible NFC tags", "NFC reading requires an NFC-equipped phone. Access cards and security credentials are not copied."),
    Module("Smart Home", "⌂", "Home device services", "Discover local smart-home services. Direct controls require the device maker’s supported protocol and pairing."),
    Module("Device Scanner", "⌕", "Bluetooth LE discovery", "Find nearby devices that advertise Bluetooth Low Energy services."),
    Module("QR Studio", "▦", "Create and save QR codes", "Generate QR codes from links or text, copy content, and keep a local history.")
)

private data class NearbyDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var selected by remember { mutableStateOf<Module?>(null) }
                Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = Background) {
                    if (selected == null) HomeScreen { selected = it }
                    else if (selected?.name == "Bluetooth" || selected?.name == "Device Scanner") {
                        BluetoothScreen(onBack = { selected = null })
                    } else if (selected?.name == "Wi-Fi") {
                        WifiScreen(onBack = { selected = null })
                    } else if (selected?.name == "Smart Home") {
                        WifiScreen(onBack = { selected = null })
                    } else if (selected?.name == "NFC / RFID") {
                        NfcScreen(onBack = { selected = null })
                    } else if (selected?.name == "Infrared") {
                        InfraredScreen(onBack = { selected = null })
                    } else if (selected?.name == "QR Studio") {
                        QrStudioScreen(onBack = { selected = null })
                    } else {
                        ModuleScreen(selected!!, onBack = { selected = null })
                    }
                }
            }
        }
    }
}

private data class SavedDevice(val name: String, val type: String, val address: String)
private fun deviceQr(d: SavedDevice) = "onlineflipper://device?name=${Uri.encode(d.name)}&type=${Uri.encode(d.type)}&address=${Uri.encode(d.address)}"
private fun parseDeviceQr(raw: String): SavedDevice? {
    return try {
        val u = Uri.parse(raw)
        if (u.scheme != "onlineflipper" || u.host != "device") null
        else {
            val n = u.getQueryParameter("name") ?: return null
            val t = u.getQueryParameter("type") ?: return null
            val a = u.getQueryParameter("address") ?: return null
            if (n.isBlank() || a.isBlank()) null else SavedDevice(n, t, a)
        }
    } catch (_: Exception) { null }
}
private fun qrBitmap(value: String): Bitmap? = try {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 560, 560)
    Bitmap.createBitmap(560, 560, Bitmap.Config.RGB_565).also { bmp ->
        for (x in 0 until 560) for (y in 0 until 560)
            bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
} catch (_: Exception) { null }

private fun saveDevice(context: android.content.Context, item: SavedDevice) {
    val prefs = context.getSharedPreferences("device_hub", android.content.Context.MODE_PRIVATE)
    val all = prefs.getStringSet("saved", emptySet()).orEmpty().toMutableSet()
    all.add(item.name + "|" + item.type + "|" + item.address)
    prefs.edit().putStringSet("saved", all).apply()
}
@Composable
private fun HomeScreen(onSelect: (Module) -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    val prefs = remember { activity.getSharedPreferences("device_hub", android.content.Context.MODE_PRIVATE) }
    val saved = remember {
        mutableStateListOf<SavedDevice>().apply {
            prefs.getStringSet("saved", emptySet()).orEmpty().forEach { line ->
                val p = line.split("|", limit = 3)
                if (p.size == 3) add(SavedDevice(p[0], p[1], p[2]))
            }
        }
    }
    var selectedQr by remember { mutableStateOf<SavedDevice?>(null) }
    var message by remember { mutableStateOf("Scan a device QR to add it to your list.") }
    fun save() {
        prefs.edit().putStringSet("saved", saved.map { "${it.name}|${it.type}|${it.address}" }.toSet()).apply()
    }
    Column(Modifier.fillMaxSize().background(Background).padding(16.dp)) {
        Text("ONLINE FLIPPER : ZERO", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Text("DEVICE HUB", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("QR pairing • device sessions • local tools", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            GmsBarcodeScanning.getClient(activity).startScan()
                .addOnSuccessListener { barcode ->
                    val item = parseDeviceQr(barcode.rawValue.orEmpty())
                    if (item == null) message = "QR code is not a supported device profile."
                    else {
                        if (saved.none { it.type == item.type && it.address == item.address }) saved.add(item)
                        save()
                        message = "${item.name} saved."
                    }
                }
                .addOnCanceledListener { message = "Scan cancelled." }
                .addOnFailureListener { message = "Scanner unavailable." }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) {
            Text("＋  SCAN QR / ADD DEVICE")
        }
        Text(message, color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSelect(Module("QR Studio", "▦", "QR tools", "Create QR codes from links or text.")) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) { Text("QR STUDIO") }
            Button(onClick = { onSelect(Module("Bluetooth", "B", "Bluetooth", "Scan and connect to compatible BLE devices.")) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAEAEA), contentColor = Color.White)) { Text("Bluetooth") }
            Button(onClick = { onSelect(Module("Wi-Fi", "W", "Wi-Fi", "Find devices on your local network.")) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAEAEA), contentColor = Color.White)) { Text("Wi-Fi") }
        }
        Spacer(Modifier.height(14.dp))
        Text("SAVED DEVICES  •  ${saved.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        if (saved.isEmpty()) Text("Your saved devices will appear here.", color = Muted, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(saved, key = { "${it.type}-${it.address}" }) { item ->
                Column(Modifier.fillMaxWidth().background(Panel), RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text(item.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${item.type}  •  ${item.address}", color = Muted, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { selectedQr = item }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) { Text("Show QR") }
                        Button(onClick = { saved.remove(item); save() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0), contentColor = Color.White)) { Text("Remove") }
                    }
                }
            }
        }
    }
    selectedQr?.let { item ->
        Dialog(onDismissRequest = { selectedQr = null }) {
            Column(Modifier.background(Panel, RoundedCornerShape(18.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold)
                qrBitmap(deviceQr(item))?.let { Image(it.asImageBitmap(), "Device QR code", Modifier.fillMaxWidth().padding(12.dp)) }
                Text("Scan in Online Flipper : Zero on another device.", color = Muted, fontSize = 10.sp)
                Button(onClick = { selectedQr = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) { Text("CLOSE") }
            }
        }
    }
}

@Composable
private fun QrStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qr_studio", android.content.Context.MODE_PRIVATE) }
    var value by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Enter a link or any text to create a QR code.") }
    val history = remember {
        mutableStateListOf<String>().apply {
            prefs.getStringSet("history", emptySet()).orEmpty().toList().take(30).forEach { encoded ->
                try { add(String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)) } catch (_: Exception) {}
            }
        }
    }
    val bitmap = remember(value) { if (value.isBlank()) null else qrBitmap(value.trim()) }

    fun rememberValue(text: String) {
        if (text.isBlank()) return
        history.remove(text)
        history.add(0, text)
        while (history.size > 30) history.removeAt(history.lastIndex)
        val encoded = history.map { android.util.Base64.encodeToString(it.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP) }.toSet()
        prefs.edit().putStringSet("history", encoded).apply()
    }

    fun saveImage() {
        val image = bitmap ?: run { status = "Enter text first."; return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            status = "Image saving is supported on Android 10 and newer."
            return
        }
        try {
            val name = "OnlineFlipper-QR-${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OnlineFlipper")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create image")
            context.contentResolver.openOutputStream(uri)?.use { output ->
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IllegalStateException("Could not write image")
            } ?: throw IllegalStateException("Could not open image")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            rememberValue(value.trim())
            status = "QR image saved to Pictures/OnlineFlipper."
        } catch (_: Exception) {
            status = "Could not save image. Check available storage."
        }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(16.dp)) {
        Text("‹  BACK", color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.height(16.dp))
        Text("QR STUDIO", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("Create • copy • save • reuse", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Link or text") },
            placeholder = { Text("https://example.com") },
            minLines = 2,
            maxLines = 4
        )
        Spacer(Modifier.height(10.dp))
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), "Generated QR code", Modifier.fillMaxWidth().padding(horizontal = 28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("QR content", value.trim()))
                    rememberValue(value.trim())
                    status = "Content copied."
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) { Text("COPY TEXT") }
                Button(onClick = { saveImage() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAEAEA), contentColor = Color.White)) { Text("SAVE PNG") }
            }
        } else {
            Text("Your QR preview will appear here.", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(status, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("RECENT QR CONTENT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("CLEAR", color = Muted, fontSize = 10.sp, modifier = Modifier.clickable {
                history.clear()
                prefs.edit().remove("history").apply()
                status = "History cleared."
            })
        }
        if (history.isEmpty()) Text("Generated and saved items will appear here.", color = Muted, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history.toList(), key = { it }) { item ->
                Row(
                    Modifier.fillMaxWidth().background(Panel), RoundedCornerShape(10.dp))
                        .clickable { value = item; status = "Loaded from history." }.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item, color = Color.White, fontSize = 11.sp, maxLines = 2, modifier = Modifier.weight(1f))
                    Text("USE", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun BluetoothScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val manager = remember { context.getSystemService(BluetoothManager::class.java) }
    val adapter = manager?.adapter
    val devices = remember { mutableStateListOf<NearbyDevice>() }
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready to scan") }
    var connectedName by remember { mutableStateOf("") }
    var gattServices by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeGatt by remember { mutableStateOf<BluetoothGatt?>(null) }

    val permissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
    }
    fun hasPermissions(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device
                val name = try { d.name ?: result.scanRecord?.deviceName ?: "Unknown device" }
                    catch (_: SecurityException) { "Bluetooth device" }
                val entry = NearbyDevice(d, name, d.address, result.rssi)
                activity.runOnUiThread {
                    val index = devices.indexOfFirst { it.address == entry.address }
                    if (index >= 0) devices[index] = entry else devices.add(entry)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                activity.runOnUiThread {
                    scanning = false
                    status = "Scan failed ($errorCode)"
                }
            }
        }
    }

    fun startScan() {
        if (!hasPermissions()) {
            status = "Allow Nearby devices permission"
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || scanner == null || !adapter.isEnabled) {
            status = "Turn Bluetooth on first"
            return
        }
        try {
            devices.clear()
            scanner.startScan(scanCallback)
            scanning = true
            status = "Scanning nearby BLE devices…"
        } catch (e: SecurityException) {
            status = "Bluetooth permission required"
        } catch (e: Exception) {
            status = "Could not start scan"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) startScan()
        else status = "Permission denied"
    }

    DisposableEffect(activeGatt) {
        onDispose {
            try { activeGatt?.disconnect(); activeGatt?.close() } catch (_: Exception) {}
        }
    }

    DisposableEffect(scanning, adapter) {
        onDispose {
            if (scanning && hasPermissions()) {
                try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(18.dp)) {
        Text("‹  BACK", color = Accent, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("BLUETOOTH", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(status, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (scanning) {
                        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
                        scanning = false
                        status = "Scan stopped"
                    } else if (hasPermissions()) startScan()
                    else permissionLauncher.launch(permissions)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
            ) { Text(if (scanning) "Stop scan" else "Scan devices") }
            Button(
                onClick = {
                    try {
                        val intent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        activity.startActivity(intent)
                    } catch (_: Exception) { status = "Open Bluetooth settings manually" }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White)
            ) { Text("Bluetooth settings") }
        }
        if (connectedName.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("Connected: $connectedName", color = Accent, fontSize = 13.sp)
            Text("GATT services: ${gattServices.size}", color = Muted, fontSize = 11.sp)
            gattServices.take(6).forEach { uuid -> Text(uuid, color = Muted, fontSize = 9.sp) }
            Button(onClick = { try { activeGatt?.disconnect(); activeGatt?.close() } catch (_: Exception) {}; activeGatt = null; connectedName = ""; gattServices = emptyList(); status = "Disconnected" }, colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White)) { Text("Disconnect") }
        }
        Spacer(Modifier.height(14.dp))
        Text("FOUND DEVICES  •  ${devices.size}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            Text("Tap Scan devices. Keep the device nearby and make it discoverable.", color = Muted, fontSize = 13.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.address }) { item ->
                Column(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp)
                ) {
                    Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${item.address}  •  RSSI ${item.rssi} dBm", color = Muted, fontSize = 10.sp)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                try {
                                    status = "Connecting to ${item.name}…"
                                    activeGatt?.close()
                                    activeGatt = item.device.connectGatt(context, false, object : BluetoothGattCallback() {
                                        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
                                            activity.runOnUiThread {
                                                if (newState == BluetoothProfile.STATE_CONNECTED) {
                                                    connectedName = item.name
                                                    status = "Connected"
                                                    gatt.discoverServices()
                                                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                                    if (connectedName == item.name) connectedName = ""
                                                    status = "Disconnected"
                                                }
                                            }
                                        }
                                        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
                                            activity.runOnUiThread {
                                                gattServices = if (statusCode == BluetoothGatt.GATT_SUCCESS) gatt.services.map { it.uuid.toString() } else emptyList()
                                                status = if (statusCode == BluetoothGatt.GATT_SUCCESS)
                                                    "Connected • ${gatt.services.size} services found"
                                                else "Connected • service discovery unavailable"
                                            }
                                        }
                                    }, BluetoothDevice.TRANSPORT_LE)
                                } catch (_: SecurityException) { status = "Bluetooth permission required" }
                                catch (_: Exception) { status = "Connection not supported by this device" }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                        ) { Text("Connect") }
                        Button(onClick = { saveDevice(context, SavedDevice(item.name, "Bluetooth LE", item.address)); status = "Saved to device hub" }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0), contentColor = Color.Black)) { Text("Save") }
                        Button(
                            onClick = {
                                try {
                                    if (item.device.bondState == BluetoothDevice.BOND_BONDED) status = "Already paired"
                                    else {
                                        status = "Confirm pairing in Android dialog"
                                        item.device.createBond()
                                    }
                                } catch (_: SecurityException) { status = "Bluetooth permission required" }
                                catch (_: Exception) { status = "Pairing not supported" }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29332D), contentColor = Color.White)
                        ) { Text("Pair") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleScreen(module: Module, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Background).padding(22.dp)) {
        Text("‹  BACK", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.height(30.dp))
        Text(module.symbol, color = Accent, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(module.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(module.detail, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(20.dp))
        Text("NOT AVAILABLE YET", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun NfcScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val nfc = remember { NfcAdapter.getDefaultAdapter(context) }
    var result by remember { mutableStateOf("Hold an NFC tag near the back of your phone.") }
    var tagInfo by remember { mutableStateOf("") }

    DisposableEffect(nfc) {
        if (nfc != null && nfc.isEnabled) {
            val callback = NfcAdapter.ReaderCallback { tag ->
                val message = try {
                    Ndef.get(tag)?.cachedNdefMessage?.records?.joinToString("\n") { record ->
                        val payload = record.payload
                        if (record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
                            record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT) && payload.isNotEmpty()
                        ) {
                            val languageLength = payload[0].toInt() and 0x3F
                            val start = (1 + languageLength).coerceAtMost(payload.size)
                            val length = (payload.size - start).coerceAtLeast(0)
                            String(payload, start, length, Charset.forName("UTF-8"))
                        } else {
                            String(payload, Charset.forName("UTF-8")).take(180)
                        }
                    }.orEmpty()
                } catch (_: Exception) { "Tag detected. Content could not be read." }
                activity.runOnUiThread {
                    tagInfo = tag.techList.joinToString(", ")
                    result = if (message.isBlank()) "NFC tag detected. No readable NDEF message." else message
                }
            }
            try {
                nfc.enableReaderMode(
                    activity,
                    callback,
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                    null
                )
            } catch (_: Exception) { result = "Could not start NFC reader." }
        } else {
            result = if (nfc == null) "This phone has no NFC hardware." else "Turn NFC on in Android settings."
        }
        onDispose {
            try { nfc?.disableReaderMode(activity) } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(20.dp)) {
        Text("‹  BACK", color = Accent, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.height(24.dp))
        Text("NFC READER", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(14.dp))
        Text(result, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(12.dp))
        if (tagInfo.isNotBlank()) Text("TECH: $tagInfo", color = Muted, fontSize = 10.sp)
        Text("Reads compatible NFC tags only. It does not copy access credentials.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun InfraredScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ir = remember { context.getSystemService(ConsumerIrManager::class.java) }
    val hasEmitter = remember { try { ir?.hasIrEmitter() == true } catch (_: Exception) { false } }
    val ranges = remember {
        try {
            ir?.carrierFrequencies?.joinToString(", ") { "${it.minFrequency / 1000}-${it.maxFrequency / 1000} kHz" } ?: ""
        } catch (_: Exception) { "" }
    }
    Column(Modifier.fillMaxSize().background(Background).padding(20.dp)) {
        Text("‹  BACK", color = Accent, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.height(24.dp))
        Text("INFRARED", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        Text(if (hasEmitter) "IR transmitter detected" else "No built-in IR transmitter",
            color = if (hasEmitter) Accent else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (ranges.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Supported frequencies: $ranges", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (hasEmitter) "Hardware is available. A matching remote profile is still needed for each TV or projector."
            else "Use a compatible external IR accessory. The phone alone cannot send infrared without IR hardware.",
            color = Muted, fontSize = 13.sp, lineHeight = 20.sp
        )
    }
}

private data class NetworkDevice(
    val name: String,
    val type: String,
    val host: String,
    val port: Int
)

@Composable
private fun WifiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val nsd = remember { context.getSystemService(NsdManager::class.java) }
    val wifi = remember { context.applicationContext.getSystemService(WifiManager::class.java) }
    val found = remember { mutableStateListOf<NetworkDevice>() }
    var status by remember { mutableStateOf("Scan your current Wi-Fi network") }
    var running by remember { mutableStateOf(false) }
    val listeners = remember { mutableStateListOf<NsdManager.DiscoveryListener>() }
    var multicastLock by remember { mutableStateOf<WifiManager.MulticastLock?>(null) }

    fun stopDiscovery() {
        listeners.toList().forEach { listener ->
            try { nsd?.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
        listeners.clear()
        try { multicastLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        multicastLock = null
        running = false
    }

    fun startDiscovery() {
        if (nsd == null) {
            status = "Network discovery unavailable"
            return
        }
        stopDiscovery()
        found.clear()
        try {
            val lock = wifi?.createMulticastLock("online_flipper_zero_nsd")
            lock?.setReferenceCounted(false)
            lock?.acquire()
            multicastLock = lock
        } catch (_: Exception) {}
        val types = listOf("_http._tcp.", "_ipp._tcp.", "_googlecast._tcp.", "_airplay._tcp.", "_hap._tcp.")
        types.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    try {
                        nsd.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val host = info.host?.hostAddress ?: return
                                val item = NetworkDevice(info.serviceName, info.serviceType, host, info.port)
                                activity.runOnUiThread {
                                    if (found.none { it.name == item.name && it.host == item.host && it.port == item.port }) found.add(item)
                                }
                            }
                        })
                    } catch (_: Exception) {}
                }
                override fun onServiceLost(service: NsdServiceInfo) {
                    activity.runOnUiThread { found.removeAll { it.name == service.serviceName } }
                }
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try { nsd.stopServiceDiscovery(this) } catch (_: Exception) {}
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try { nsd.stopServiceDiscovery(this) } catch (_: Exception) {}
                }
            }
            try {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                listeners.add(listener)
            } catch (_: Exception) {}
        }
        running = listeners.isNotEmpty()
        status = if (running) "Searching local network…" else "Could not start discovery"
    }

    DisposableEffect(Unit) {
        onDispose { stopDiscovery() }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(18.dp)) {
        Text("‹  BACK", color = Accent, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("WI-FI DEVICES", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(status, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (running) { stopDiscovery(); status = "Scan stopped" } else startDiscovery() },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
            ) { Text(if (running) "Stop" else "Scan network") }
            Button(
                onClick = {
                    try { activity.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }
                    catch (_: Exception) { status = "Open Wi-Fi settings manually" }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White)
            ) { Text("Wi-Fi settings") }
        }
        Spacer(Modifier.height(12.dp))
        Text("FOUND SERVICES • ${found.size}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (found.isEmpty()) {
            Text("Only devices advertising a supported local service appear here.", color = Muted, fontSize = 12.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(found, key = { "${it.name}-${it.host}-${it.port}" }) { item ->
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${item.host}:${item.port} • ${item.type}", color = Muted, fontSize = 10.sp)
                    Button(onClick = { saveDevice(context, SavedDevice(item.name, "Wi-Fi", item.host + ":" + item.port)); status = "Saved to device hub" }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0), contentColor = Color.Black)) { Text("Save device") }
                    if (item.type.contains("_http._tcp") || item.type.contains("_ipp._tcp")) {
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                try {
                                    val uri = Uri.parse("http://${item.host}:${item.port}")
                                    activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (_: Exception) { status = "Could not open device page" }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                        ) { Text("Open device page") }
                    }
                }
            }
        }
    }
}
