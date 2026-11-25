package com.illakrivetsbit.nexflip

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.nfc.NfcAdapter
import android.nfc.NdefMessage
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private val Ink = Color(0xFF080A0D)
private val SurfaceOne = Color(0xFF11151B)
private val SurfaceTwo = Color(0xFF191F28)
private val Line = Color(0xFF252D38)
private val White = Color(0xFFF5F7FA)
private val Gray = Color(0xFF929CAA)
private val Lime = Color(0xFFB8F36B)
private val Blue = Color(0xFF8AB4FF)

private data class Tool(
    val title: String,
    val symbol: String,
    val subtitle: String,
    val detail: String
)

private val tools = listOf(
    Tool("Bluetooth", "ᛒ", "Nearby BLE devices", "Discover Bluetooth Low Energy devices around you. Scanning only lists nearby devices; it does not connect to them."),
    Tool("Wi-Fi", "⌁", "Local network services", "Find devices that announce supported services on the same Wi-Fi network."),
    Tool("QR Studio", "▦", "Create and scan QR", "Generate QR codes from links or text, copy the content, and save the image."),
    Tool("NFC Reader", "◉", "Read public tag data", "Read compatible NFC tags that contain ordinary NDEF text or links. Access credentials are not copied."),
    Tool("Infrared", "◎", "Hardware check", "Check whether your phone has a built-in infrared transmitter. A compatible device profile is needed before any remote can work."),
    Tool("Smart Home", "⌂", "Local device discovery", "Discover supported local services. Actual controls require the device manufacturer's supported pairing method.")
)

private data class BleItem(val name: String, val address: String, val rssi: Int)
private data class LanItem(val name: String, val host: String, val port: Int, val type: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(8, 10, 13)
        window.navigationBarColor = AndroidColor.rgb(8, 10, 13)
        setContent {
            NexFlipTheme {
                AppShell()
            }
        }
    }
}

@Composable
private fun NexFlipTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Lime,
        onPrimary = Ink,
        secondary = Blue,
        background = Ink,
        surface = SurfaceOne,
        onBackground = White,
        onSurface = White,
        outline = Line
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun AppShell() {
    var selectedTab by remember { mutableStateOf("Home") }
    var selectedTool by remember { mutableStateOf<Tool?>(null) }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceOne,
                contentColor = White,
                tonalElevation = 0.dp
            ) {
                listOf("Home", "Explore", "QR", "About").forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab && selectedTool == null,
                        onClick = { selectedTab = tab; selectedTool = null },
                        icon = { Text(when (tab) { "Home" -> "⌂"; "Explore" -> "◈"; "QR" -> "▦"; else -> "i" }, fontSize = 19.sp) },
                        label = { Text(tab, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Lime,
                            selectedTextColor = Lime,
                            indicatorColor = Color(0xFF293421),
                            unselectedIconColor = Gray,
                            unselectedTextColor = Gray
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                selectedTool != null -> ToolDetailScreen(
                    tool = selectedTool!!,
                    onBack = { selectedTool = null }
                )
                selectedTab == "Home" -> HomeScreen(
                    onExplore = { selectedTab = "Explore" },
                    onQr = { selectedTab = "QR" },
                    onTool = { selectedTool = it }
                )
                selectedTab == "Explore" -> ExploreScreen(onTool = { selectedTool = it })
                selectedTab == "QR" -> QrStudioScreen()
                else -> AboutScreen()
            }
        }
    }
}

@Composable
private fun PageHeader(eyebrow: String, title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(eyebrow.uppercase(), color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Text(title, color = White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
        if (subtitle != null) Text(subtitle, color = Gray, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .background(SurfaceOne, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeScreen(onExplore: () -> Unit, onQr: () -> Unit, onTool: (Tool) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Ink).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ONLINE FLIPPER", color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("ZERO", color = White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            Box(
                Modifier.size(46.dp).background(Color(0xFF202B19), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) { Text("N", color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Black) }
        }
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("DEVICE TOOLKIT", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("Explore what’s around you.", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Useful local tools in one clean place.", color = Gray, fontSize = 12.sp)
                }
                Text("◈", color = Lime, fontSize = 42.sp)
            }
            PrimaryButton("EXPLORE TOOLS  →", onExplore, Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("QR STUDIO", "Create & scan codes", "▦", onQr, Modifier.weight(1f))
            QuickTile("DISCOVERY", "Nearby devices", "⌁", onExplore, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("POPULAR TOOLS", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text("VIEW ALL", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onExplore))
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            tools.take(3).forEach { tool ->
                ToolRow(tool, Modifier.fillMaxWidth().clickable { onTool(tool) })
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Privacy first • Scanning stays under your control", color = Gray, fontSize = 10.sp)
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String, symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.background(SurfaceTwo, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(symbol, color = Lime, fontSize = 25.sp)
        Text(title, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Gray, fontSize = 10.sp)
    }
}

@Composable
private fun ToolRow(tool: Tool, modifier: Modifier = Modifier) {
    Row(
        modifier.background(SurfaceOne, RoundedCornerShape(16.dp)).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(42.dp).background(SurfaceTwo, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Text(tool.symbol, color = Lime, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(tool.title, color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(tool.subtitle, color = Gray, fontSize = 10.sp)
        }
        Text("›", color = Gray, fontSize = 25.sp)
    }
}

@Composable
private fun ExploreScreen(onTool: (Tool) -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader("TOOLS / 06 MODULES", "Explore", "Choose a tool to open its controls and status.")
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            items(tools.size) { index ->
                val tool = tools[index]
                Column(
                    Modifier.fillMaxWidth().height(158.dp)
                        .background(SurfaceOne, RoundedCornerShape(18.dp))
                        .clickable { onTool(tool) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(tool.symbol, color = Lime, fontSize = 28.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tool.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(tool.subtitle, color = Gray, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolDetailScreen(tool: Tool, onBack: () -> Unit) {
    when (tool.title) {
        "Bluetooth" -> BluetoothTool(onBack)
        "Wi-Fi", "Smart Home" -> WifiTool(onBack, tool.title)
        "QR Studio" -> QrStudioScreen(onBack)
        "NFC Reader" -> NfcTool(onBack)
        "Infrared" -> InfraredTool(onBack)
        else -> Column(Modifier.fillMaxSize().background(Ink).padding(20.dp)) {
            BackButton(onBack)
            Spacer(Modifier.height(25.dp))
            PageHeader("MODULE", tool.title, tool.detail)
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Text("‹  BACK", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
}

@Composable
private fun ToolIntro(title: String, subtitle: String, onBack: () -> Unit) {
    BackButton(onBack)
    Spacer(Modifier.height(16.dp))
    PageHeader("DEVICE TOOLS", title, subtitle)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BluetoothTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(BluetoothManager::class.java) }
    val scanner = remember { manager?.adapter?.bluetoothLeScanner }
    val found = remember { mutableStateListOf<BleItem>() }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Start a scan to find nearby Bluetooth LE devices.") }
    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = try { device.name ?: "Unnamed device" } catch (_: SecurityException) { "Bluetooth device" }
                val item = BleItem(name, device.address ?: "Unavailable", result.rssi)
                if (found.none { it.address == item.address }) found.add(item)
            }
            override fun onScanFailed(errorCode: Int) { message = "Scan failed (code $errorCode)." ; scanning = false }
        }
    }
    fun stop() {
        try { scanner?.stopScan(callback) } catch (_: Exception) {}
        scanning = false
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = if (Build.VERSION.SDK_INT >= 31) result[Manifest.permission.BLUETOOTH_SCAN] == true
            else result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            try {
                found.clear()
                scanner?.startScan(callback)
                scanning = scanner != null
                message = if (scanning) "Scanning nearby devices…" else "Bluetooth scanning is unavailable on this phone."
            } catch (_: SecurityException) { message = "Bluetooth permission was not granted." }
            catch (_: Exception) { message = "Could not start Bluetooth scan." }
        } else message = "Bluetooth permission is needed to scan."
    }
    DisposableEffect(Unit) { onDispose { stop() } }
    Column(Modifier.fillMaxSize().background(Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ToolIntro("Bluetooth", "Nearby Bluetooth LE discovery. This scan does not pair or control devices.", onBack)
        Panel(Modifier.fillMaxWidth()) {
            Text(if (scanning) "SCANNING" else "READY", color = if (scanning) Lime else Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(message, color = White, fontSize = 13.sp)
            PrimaryButton(if (scanning) "STOP SCAN" else "START SCAN", {
                if (scanning) { stop(); message = "Scan stopped." }
                else {
                    val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                    if (missing.isEmpty()) {
                        try { found.clear(); scanner?.startScan(callback); scanning = scanner != null; message = "Scanning nearby devices…" }
                        catch (_: Exception) { message = "Could not start scan." }
                    } else permissionLauncher.launch(missing.toTypedArray())
                }
            }, Modifier.fillMaxWidth())
        }
        Text("FOUND DEVICES  •  ${found.size}", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(found, key = { it.address }) { item ->
                Panel(Modifier.fillMaxWidth()) {
                    Text(item.name, color = White, fontWeight = FontWeight.Bold)
                    Text(item.address, color = Gray, fontSize = 10.sp)
                    Text("Signal  ${item.rssi} dBm", color = Lime, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun WifiTool(onBack: () -> Unit, title: String) {
    val context = LocalContext.current
    val nsd = remember { context.getSystemService(NsdManager::class.java) }
    val found = remember { mutableStateListOf<LanItem>() }
    var status by remember { mutableStateOf("Discover services announced on your current Wi-Fi.") }
    var active by remember { mutableStateOf(false) }
    val listener = remember {
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { active = true }
            override fun onServiceFound(service: NsdServiceInfo) {
                try {
                    nsd?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val item = LanItem(info.serviceName ?: "Network service", info.host?.hostAddress ?: "Unknown host", info.port, info.serviceType ?: "")
                            if (found.none { it.name == item.name && it.host == item.host && it.port == item.port }) found.add(item)
                        }
                    })
                } catch (_: Exception) {}
            }
            override fun onServiceLost(service: NsdServiceInfo) { found.removeAll { it.name == service.serviceName } }
            override fun onDiscoveryStopped(serviceType: String) { active = false }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { active = false; status = "Discovery could not start (code $errorCode)." }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { active = false }
        }
    }
    fun stop() { try { nsd?.stopServiceDiscovery(listener) } catch (_: Exception) {}; active = false }
    DisposableEffect(Unit) { onDispose { stop() } }
    Column(Modifier.fillMaxSize().background(Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ToolIntro(title, "Only services that advertise themselves on the local network are shown.", onBack)
        Panel(Modifier.fillMaxWidth()) {
            Text(status, color = White, fontSize = 13.sp)
            PrimaryButton(if (active) "STOP DISCOVERY" else "SCAN WI-FI", {
                if (active) { stop(); status = "Discovery stopped." }
                else {
                    found.clear()
                    try {
                        nsd?.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
                        status = "Searching for HTTP services…"
                    } catch (_: Exception) { status = "Wi-Fi discovery is unavailable." }
                }
            }, Modifier.fillMaxWidth())
        }
        Text("FOUND SERVICES  •  ${found.size}", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(found, key = { "${it.name}-${it.host}-${it.port}" }) { item ->
                Panel(Modifier.fillMaxWidth()) {
                    Text(item.name, color = White, fontWeight = FontWeight.Bold)
                    Text("${item.host}:${item.port}", color = Lime, fontSize = 12.sp)
                    Text(item.type, color = Gray, fontSize = 10.sp)
                    Text("Only open services you recognize and trust.", color = Gray, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun makeQr(text: String): Bitmap? = try {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 640, 640)
    Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until 640) for (y in 0 until 640) {
            bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
} catch (_: Exception) { null }

@Composable
private fun QrStudioScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var value by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Enter a link or text to generate a QR code.") }
    val bitmap = remember(value) { if (value.isBlank()) null else makeQr(value.trim()) }
    Column(Modifier.fillMaxSize().background(Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (onBack != null) ToolIntro("QR Studio", "Create a code from a URL or text. No account is needed.", onBack)
        else PageHeader("CREATE / SCAN", "QR Studio", "Create a code from a URL or text. No account is needed.")
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; message = "QR preview updates as you type." },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Link or text") },
            placeholder = { Text("https://example.com") },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Lime,
                unfocusedBorderColor = Line,
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedLabelColor = Lime,
                unfocusedLabelColor = Gray,
                cursorColor = Lime
            )
        )
        if (bitmap != null) {
            Panel(Modifier.fillMaxWidth()) {
                Image(bitmap.asImageBitmap(), "QR code preview", Modifier.fillMaxWidth().padding(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("QR content", value.trim()))
                        message = "Text copied to clipboard."
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = White)) { Text("COPY TEXT") }
                    PrimaryButton("SAVE PNG", {
                        try {
                            if (Build.VERSION.SDK_INT < 29) { message = "Saving requires Android 10 or newer." }
                            else {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, "NexFlip-QR-${System.currentTimeMillis()}.png")
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NexFlip")
                                    put(MediaStore.Images.Media.IS_PENDING, 1)
                                }
                                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                    ?: throw IllegalStateException()
                                context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                values.clear()
                                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                                context.contentResolver.update(uri, values, null, null)
                                message = "Saved to Pictures/NexFlip."
                            }
                        } catch (_: Exception) { message = "Could not save the image." }
                    }, Modifier.weight(1f))
                }
            }
        } else {
            Panel(Modifier.fillMaxWidth()) {
                Text("QR PREVIEW", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Your QR code will appear here.", color = White, fontSize = 13.sp)
            }
        }
        Text(message, color = Gray, fontSize = 11.sp)
        var scanned by remember { mutableStateOf("") }
        val activity = context as ComponentActivity
        PrimaryButton("SCAN QR WITH CAMERA", {
            GmsBarcodeScanning.getClient(activity).startScan()
                .addOnSuccessListener { scanned = it.rawValue ?: "QR code has no text."; message = "Code scanned." }
                .addOnCanceledListener { message = "Scan cancelled." }
                .addOnFailureListener { message = "Scanner could not start." }
        }, Modifier.fillMaxWidth())
        if (scanned.isNotBlank()) Panel(Modifier.fillMaxWidth()) {
            Text("SCANNED CONTENT", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(scanned, color = White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NfcTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var result by remember { mutableStateOf("Hold a compatible NFC tag near the back of your phone.") }
    var tech by remember { mutableStateOf("") }
    DisposableEffect(adapter) {
        if (adapter != null && adapter.isEnabled) {
            try {
                adapter.enableReaderMode(activity, { tag ->
                    val text = try {
                        val ndef = android.nfc.tech.Ndef.get(tag)
                        val records = ndef?.cachedNdefMessage?.records.orEmpty()
                        records.joinToString("\n") { record ->
                            val payload = record.payload
                            if (record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
                                record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT) && payload.isNotEmpty()
                            ) {
                                val languageLength = payload[0].toInt() and 0x3F
                                String(payload, (1 + languageLength).coerceAtMost(payload.size), (payload.size - 1 - languageLength).coerceAtLeast(0), Charsets.UTF_8)
                            } else String(payload, Charsets.UTF_8).take(300)
                        }
                    } catch (_: Exception) { "Tag detected. No readable public NDEF text was found." }
                    activity.runOnUiThread {
                        result = if (text.isBlank()) "Tag detected. No readable public NDEF text was found." else text
                        tech = tag.techList.joinToString(" • ")
                    }
                }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, null)
            } catch (_: Exception) { result = "Could not start NFC reader." }
        } else result = if (adapter == null) "This phone does not have NFC hardware." else "Turn on NFC in Android settings."
        onDispose { try { adapter?.disableReaderMode(activity) } catch (_: Exception) {} }
    }
    Column(Modifier.fillMaxSize().background(Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ToolIntro("NFC Reader", "Reads ordinary public NDEF tag data only. It does not copy access cards or credentials.", onBack)
        Panel(Modifier.fillMaxWidth()) {
            Text("TAG CONTENT", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(result, color = White, fontSize = 13.sp, lineHeight = 19.sp)
            if (tech.isNotBlank()) Text(tech, color = Gray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun InfraredTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val hasIr = remember { try { context.getSystemService(android.hardware.ConsumerIrManager::class.java)?.hasIrEmitter() == true } catch (_: Exception) { false } }
    Column(Modifier.fillMaxSize().background(Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ToolIntro("Infrared", "Hardware information only. Remote profiles and supported devices are separate requirements.", onBack)
        Panel(Modifier.fillMaxWidth()) {
            Text(if (hasIr) "IR TRANSMITTER DETECTED" else "NO BUILT-IN IR TRANSMITTER", color = if (hasIr) Lime else White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                if (hasIr) "Your phone reports an infrared emitter. A compatible, documented device profile is still required."
                else "This phone does not report a built-in infrared transmitter.",
                color = Gray, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun AboutScreen() {
    Column(Modifier.fillMaxSize().background(Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        PageHeader("APP / INFORMATION", "About", "A compact toolkit for QR codes and discovering nearby devices.")
        Panel(Modifier.fillMaxWidth()) {
            Text("ONLINE FLIPPER ZERO", color = White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Version 0.2.0 • redesigned interface", color = Lime, fontSize = 11.sp)
            Text("The app only performs actions that you start and approve. Discovery tools show nearby devices and advertised services; they do not provide hidden access or bypass device security.", color = Gray, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Panel(Modifier.fillMaxWidth()) {
            Text("WHAT WORKS", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("• QR code creation, scanning and image saving\n• Bluetooth LE discovery\n• Local Wi-Fi service discovery\n• Public NFC NDEF reading\n• Infrared hardware check", color = White, fontSize = 12.sp, lineHeight = 21.sp)
        }
        Text("Remote phone control and 24-hour pairing are not enabled in this build. They need a deployed server and a separate, permission-based app integration.", color = Gray, fontSize = 11.sp, lineHeight = 17.sp)
    }
}
