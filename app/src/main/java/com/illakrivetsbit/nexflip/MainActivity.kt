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
import android.net.Uri
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
    Module("Device Scanner", "⌕", "Bluetooth LE discovery", "Find nearby devices that advertise Bluetooth Low Energy services.")
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
                    } else {
                        ModuleScreen(selected!!, onBack = { selected = null })
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onSelect: (Module) -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    var qrStatus by remember { mutableStateOf("Scan a device QR code to open its connection page.") }
    Column(Modifier.fillMaxSize().background(Background).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ONLINE", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(" FLIPPER : ZERO", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("ANDROID", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(14.dp)) {
            Text("DEVICE CONNECTION", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("Connect your devices.", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Bluetooth, local Wi-Fi and QR.", color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            GmsBarcodeScanning.getClient(activity).startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue?.trim().orEmpty()
                    val uri = runCatching { Uri.parse(raw) }.getOrNull()
                    if (uri != null && (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) {
                        qrStatus = "Opening device page…"
                        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            .onFailure { qrStatus = "Could not open this address." }
                    } else qrStatus = "Unsupported QR. Use a device web address (http/https)."
                }
                .addOnCanceledListener { qrStatus = "QR scan cancelled." }
                .addOnFailureListener { qrStatus = "QR scanner unavailable. Try again." }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)) {
            Text("Scan QR to connect")
        }
        Text(qrStatus, color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(14.dp))
        Text("CONNECTIONS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        listOf(
            Module("Bluetooth", "ᛒ", "Find and connect BLE devices", "Bluetooth Low Energy scanner"),
            Module("Wi-Fi", "⌁", "Find devices on this network", "Local network service discovery")
        ).forEach { module ->
            Row(Modifier.fillMaxWidth().padding(bottom = 9.dp).background(Panel, RoundedCornerShape(14.dp)).clickable { onSelect(module) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(module.symbol, color = Accent, fontSize = 23.sp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(module.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(module.description, color = Muted, fontSize = 10.sp)
                }
                Text("›", color = Accent, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Only connect to devices you own or are allowed to manage.", color = Muted, fontSize = 9.sp)
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
