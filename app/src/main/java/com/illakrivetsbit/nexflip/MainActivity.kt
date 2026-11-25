package com.illakrivetsbit.nexflip

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import okhttp3.WebSocket
import org.json.JSONObject
import java.util.UUID

private val Black = Color(0xFF050505)
private val Panel = Color(0xFF111111)
private val PanelRaised = Color(0xFF1B1B1B)
private val Stroke = Color(0xFF303030)
private val White = Color(0xFFF7F7F7)
private val Muted = Color(0xFF969696)

private data class SavedDevice(val id: String, val name: String, val ownerToken: String, val addedAt: Long)
private data class InviteState(
    val id: String, val ownerToken: String, val qr: Bitmap?,
    val pendingId: String? = null, val pendingName: String? = null
)
private data class CompanionState(
    val id: String, val requestToken: String, val status: String = "Ожидание подтверждения",
    val accessToken: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(5, 5, 5)
        window.navigationBarColor = AndroidColor.rgb(5, 5, 5)
        setContent { AppTheme { OnlineFlipperApp(this) } }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = White, onPrimary = Black, background = Black,
            surface = Panel, onSurface = White, onBackground = White,
            secondary = White, outline = Stroke
        ), content = content
    )
}

@Composable
private fun OnlineFlipperApp(activity: MainActivity) {
    val context = activity
    val devices = remember { mutableStateListOf<SavedDevice>().apply { addAll(loadDevices(context)) } }
    var invite by remember { mutableStateOf<InviteState?>(null) }
    var companion by remember { mutableStateOf<CompanionState?>(null) }
    var selected by remember { mutableStateOf<SavedDevice?>(null) }
    var socket by remember { mutableStateOf<WebSocket?>(null) }
    var remoteState by remember { mutableStateOf("Нет данных") }
    var message by remember { mutableStateOf("") }
    var scanBusy by remember { mutableStateOf(false) }

    fun show(text: String) {
        activity.runOnUiThread { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    BackHandler(enabled = invite != null || companion != null || selected != null) {
        when {
            invite != null -> invite = null
            companion != null -> { companion = null; socket?.close(1000, "User closed"); socket = null }
            selected != null -> { selected = null; socket?.close(1000, "Control closed"); socket = null }
        }
    }

    // Owner polls for a device asking to pair. The owner must explicitly approve it.
    LaunchedEffect(invite?.id) {
        while (invite != null) {
            val current = invite ?: break
            RemoteConnection.ownerStatus(current.id, current.ownerToken) { result ->
                result.onSuccess { response ->
                    val pending = response.optJSONObject("pendingRequest")
                    if (pending != null) {
                        invite = invite?.copy(
                            pendingId = pending.optString("requestId"),
                            pendingName = pending.optString("deviceName", "Android device")
                        )
                    }
                }
            }
            kotlinx.coroutines.delay(2500)
        }
    }

    // The phone scanned into the invitation waits until the owner approves it.
    LaunchedEffect(companion?.id, companion?.accessToken) {
        val current = companion ?: return@LaunchedEffect
        if (current.accessToken == null) {
            while (companion?.accessToken == null && companion?.status != "Приглашение истекло") {
                RemoteConnection.joinStatus(current.id, current.requestToken) { result ->
                    result.onSuccess { response ->
                        if (response.optString("status") == "approved") {
                            companion = companion?.copy(
                                status = "Подключено",
                                accessToken = response.optString("accessToken")
                            )
                        }
                    }.onFailure { error ->
                        if (error.message?.contains("expired", true) == true) {
                            companion = companion?.copy(status = "Приглашение истекло")
                        }
                    }
                }
                kotlinx.coroutines.delay(2200)
            }
        }
    }

    // The companion keeps the connection alive while its app screen is open.
    LaunchedEffect(companion?.accessToken) {
        val current = companion
        if (current?.accessToken != null) {
            socket?.close(1000, "Reconnect")
            socket = RemoteConnection.connect(current.id, current.accessToken, { activeSocket, packet ->
                if (packet.optString("type") == "command") {
                    val id = packet.optString("id")
                    val action = packet.optString("action")
                    val result = executeRemoteAction(context, action)
                    RemoteConnection.sendResult(activeSocket, id, result)
                }
            }, { state ->
                activity.runOnUiThread {
                    companion = companion?.copy(status = if (state == "connected") "Подключено" else "Связь: $state")
                }
            })
        }
    }

    // Connect the owner to the selected paired phone.
    LaunchedEffect(selected?.id) {
        val current = selected ?: return@LaunchedEffect
        socket?.close(1000, "Switch device")
        socket = RemoteConnection.connect(current.id, current.ownerToken, { _, packet ->
            if (packet.optString("type") == "result" || packet.optString("type") == "device_status") {
                val payload = packet.optJSONObject("payload") ?: JSONObject()
                val line = buildString {
                    if (payload.has("batteryPercent")) append("Батарея: ${payload.optInt("batteryPercent")}%  ")
                    if (payload.has("charging")) append(if (payload.optBoolean("charging")) "Заряжается  " else "Не заряжается  ")
                    if (payload.has("manufacturer")) append("${payload.optString("manufacturer")} ${payload.optString("model")}  ")
                    if (payload.has("androidVersion")) append("Android ${payload.optString("androidVersion")}  ")
                    if (payload.has("freeStorageMb")) append("Свободно: ${payload.optLong("freeStorageMb")} МБ  ")
                    if (payload.has("message")) append(payload.optString("message"))
                }.ifBlank { "Команда выполнена" }
                activity.runOnUiThread { remoteState = line }
            }
        }, { state ->
            activity.runOnUiThread { message = if (state == "connected") "Устройство на связи" else "Связь: $state" }
        })
    }

    DisposableEffect(Unit) {
        onDispose { socket?.close(1000, "App closed") }
    }

    Box(Modifier.fillMaxSize().background(Black)) {
        when {
            invite != null -> InviteScreen(
                invite = invite!!,
                onApprove = {
                    val current = invite ?: return@InviteScreen
                    val requestId = current.pendingId ?: return@InviteScreen
                    RemoteConnection.approve(current.id, current.ownerToken, requestId) { result ->
                        result.onSuccess {
                            val device = SavedDevice(current.id, current.pendingName ?: "Android device", current.ownerToken, System.currentTimeMillis())
                            if (devices.none { it.id == device.id }) devices.add(device)
                            saveDevices(context, devices)
                            invite = null
                            selected = device
                        }.onFailure { show("Не удалось подтвердить: ${it.message}") }
                    }
                },
                onClose = { invite = null }
            )
            companion != null -> CompanionScreen(companion!!, onClose = {
                companion = null
                socket?.close(1000, "Companion closed")
                socket = null
            })
            selected != null -> DeviceControlScreen(
                device = selected!!,
                connectionMessage = message,
                remoteState = remoteState,
                onCommand = { action ->
                    if (!RemoteConnection.sendCommand(socket, action)) show("Нет соединения. Открой приложение на втором телефоне.")
                    else if (action == "get_status") remoteState = "Запрашиваем состояние…"
                },
                onRemove = {
                    val current = selected ?: return@DeviceControlScreen
                    RemoteConnection.revoke(current.id, current.ownerToken) { }
                    devices.removeAll { it.id == current.id }
                    saveDevices(context, devices)
                    selected = null
                    socket?.close(1000, "Device removed")
                    socket = null
                },
                onBack = { selected = null; socket?.close(1000, "Back"); socket = null }
            )
            else -> HomeScreen(
                devices = devices,
                onCreate = {
                    if (!RemoteConnection.isConfigured()) {
                        show("Сначала настрой URL Cloudflare Worker в RemoteConnection.kt")
                    } else {
                        RemoteConnection.createSession { result ->
                            result.onSuccess { response ->
                                val id = response.optString("sessionId")
                                val ownerToken = response.optString("ownerToken")
                                val payload = response.optString("qrPayload")
                                val qr = makeQr(payload)
                                activity.runOnUiThread { invite = InviteState(id, ownerToken, qr) }
                            }.onFailure { show("Ошибка сервера: ${it.message}") }
                        }
                    }
                },
                onScan = {
                    if (!RemoteConnection.isConfigured()) {
                        show("Сначала настрой URL Cloudflare Worker в RemoteConnection.kt")
                    } else if (!scanBusy) {
                        scanBusy = true
                        GmsBarcodeScanning.getClient(activity).startScan()
                            .addOnSuccessListener { barcode ->
                                val parsed = parseServerInvite(barcode.rawValue.orEmpty())
                                if (parsed == null) {
                                    show("Неверный QR-код Online Flipper : Zero")
                                } else {
                                    RemoteConnection.join(parsed.first, parsed.second, "${Build.MANUFACTURER} ${Build.MODEL}") { result ->
                                        result.onSuccess { response ->
                                            activity.runOnUiThread {
                                                companion = CompanionState(parsed.first, response.optString("requestToken"))
                                            }
                                        }.onFailure { show("Не удалось подключиться: ${it.message}") }
                                    }
                                }
                            }
                            .addOnFailureListener { show("Не удалось открыть сканер QR") }
                            .addOnCompleteListener { scanBusy = false }
                    }
                },
                onOpen = { selected = it }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    devices: List<SavedDevice>,
    onCreate: () -> Unit,
    onScan: () -> Unit,
    onOpen: (SavedDevice) -> Unit
) {
    Column(Modifier.fillMaxSize().background(Black).statusBarsPadding().padding(22.dp)) {
        Text("Online Flipper : Zero", color = White, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("DEVICE CONTROL", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(38.dp))
        Text("ТВОИ УСТРОЙСТВА", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Связь без\nлишнего.", color = White, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(26.dp))
        ActionCard("СОЗДАТЬ ПОДКЛЮЧЕНИЕ", "Создать QR-код", "Показать приглашение для второго телефона", onCreate)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onScan, modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Stroke),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
        ) { Text("СКАНИРОВАТЬ QR", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("СОХРАНЁННЫЕ УСТРОЙСТВА", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(devices.size.toString().padStart(2, '0'), color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (devices.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Panel, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Text("Здесь появятся устройства\nпосле подтверждения подключения", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(devices, key = { it.id }) { device ->
                    Row(
                        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp))
                            .clickable { onOpen(device) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(42.dp).background(PanelRaised, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Text("▯", color = White, fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(device.name, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Сохранено • открыть управление", color = Muted, fontSize = 10.sp)
                        }
                        Text("›", color = Muted, fontSize = 24.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("ЗАЩИЩЁННОЕ СОЕДИНЕНИЕ • 24 ЧАСА", color = Color(0xFF626262), fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun ActionCard(kicker: String, title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(White, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(19.dp)) {
        Text(kicker, color = Color(0xFF555555), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFF666666), fontSize = 11.sp)
        Spacer(Modifier.height(18.dp))
        Text("СОЗДАТЬ  ↗", color = Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
    }
}

@Composable
private fun InviteScreen(invite: InviteState, onApprove: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Black).statusBarsPadding().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ПРИГЛАШЕНИЕ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Text("ЗАКРЫТЬ", color = Muted, fontSize = 9.sp, modifier = Modifier.clickable(onClick = onClose))
        }
        Spacer(Modifier.height(20.dp))
        Text("Отсканируй код\nна втором телефоне", color = White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        invite.qr?.let {
            Image(it.asImageBitmap(), "QR pairing code", Modifier.fillMaxWidth().aspectRatio(1f).background(White, RoundedCornerShape(14.dp)).padding(8.dp))
        }
        Spacer(Modifier.height(18.dp))
        if (invite.pendingId == null) {
            Text("Ожидаем запрос от второго телефона…", color = Muted, fontSize = 12.sp)
        } else {
            Text("ЗАПРОС НА ПОДКЛЮЧЕНИЕ", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(7.dp))
            Text(invite.pendingName ?: "Android device", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onApprove, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black)
            ) { Text("ПОДТВЕРДИТЬ ПОДКЛЮЧЕНИЕ", fontWeight = FontWeight.Bold, fontSize = 10.sp) }
        }
        Spacer(Modifier.weight(1f))
        Text("Код одноразовый. Сессия завершится через 24 часа.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun CompanionScreen(state: CompanionState, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Black).statusBarsPadding().padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Online Flipper : Zero", color = White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(76.dp).background(White, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (state.accessToken == null) "…" else "✓", color = Black, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text(state.status, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.accessToken == null) "Подтверди запрос на телефоне, который создал QR-код. Не закрывай это приложение."
            else "Этот телефон подключён. Пока приложение открыто, он принимает только разрешённые команды владельца.",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp
        )
        Spacer(Modifier.height(30.dp))
        OutlinedButton(onClick = onClose, border = androidx.compose.foundation.BorderStroke(1.dp, Stroke), colors = ButtonDefaults.outlinedButtonColors(contentColor = White)) {
            Text("ОТКЛЮЧИТЬСЯ")
        }
    }
}

@Composable
private fun DeviceControlScreen(
    device: SavedDevice,
    connectionMessage: String,
    remoteState: String,
    onCommand: (String) -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Black).statusBarsPadding().padding(22.dp)) {
        Text("‹  НАЗАД", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(24.dp))
        Text("УПРАВЛЕНИЕ", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(device.name, color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(connectionMessage.ifBlank { "Подключение…" }, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(17.dp)).padding(16.dp)) {
            Text("СОСТОЯНИЕ УСТРОЙСТВА", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(remoteState, color = White, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onCommand("get_status") }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black),
                shape = RoundedCornerShape(12.dp)
            ) { Text("ЗАПРОСИТЬ СОСТОЯНИЕ", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(18.dp))
        Text("РАЗРЕШЁННЫЕ ДЕЙСТВИЯ", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        CommandButton("Вибрация", "vibrate", onCommand)
        CommandButton("Звуковой сигнал", "ring", onCommand)
        CommandButton("Остановить сигнал", "stop_ring", onCommand)
        Spacer(Modifier.weight(1f))
        Text("Удалённый экран, навигация и управление другими приложениями не поддерживаются Android без специальных системных разрешений и отдельного явного включения.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRemove, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(13.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Stroke),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
        ) { Text("ОТВЯЗАТЬ УСТРОЙСТВО", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun CommandButton(label: String, action: String, onCommand: (String) -> Unit) {
    OutlinedButton(
        onClick = { onCommand(action) }, modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp).height(48.dp),
        shape = RoundedCornerShape(13.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Stroke),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

private fun executeRemoteAction(context: Context, action: String): JSONObject {
    return try {
        when (action) {
            "get_status" -> {
                val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
                val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val storage = StatFs(Environment.getDataDirectory().path).availableBytes / (1024L * 1024L)
                JSONObject().put("ok", true).put("batteryPercent", percent).put("charging", charging)
                    .put("androidVersion", Build.VERSION.RELEASE).put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL).put("freeStorageMb", storage)
            }
            "vibrate" -> {
                val vibrator = context.getSystemService(Vibrator::class.java)
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(450, VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") vibrator.vibrate(450)
                    JSONObject().put("ok", true).put("message", "Вибрация включена")
                } else JSONObject().put("ok", false).put("message", "Вибратор недоступен")
            }
            "ring" -> {
                RemoteTone.start()
                JSONObject().put("ok", true).put("message", "Звуковой сигнал включён")
            }
            "stop_ring" -> {
                RemoteTone.stop()
                JSONObject().put("ok", true).put("message", "Сигнал остановлен")
            }
            else -> JSONObject().put("ok", false).put("message", "Команда не разрешена")
        }
    } catch (e: Exception) {
        JSONObject().put("ok", false).put("message", e.message ?: "Ошибка выполнения")
    }
}

private object RemoteTone {
    private var tone: ToneGenerator? = null
    fun start() {
        stop()
        tone = ToneGenerator(AudioManager.STREAM_ALARM, 90).also {
            it.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2200)
        }
    }
    fun stop() {
        try { tone?.stopTone(); tone?.release() } catch (_: Exception) { }
        tone = null
    }
}

private fun parseServerInvite(raw: String): Pair<String, String>? = try {
    val json = JSONObject(raw)
    if (json.optInt("v") != 1 || json.optString("service") != "online-flipper-zero") null
    else {
        val id = json.optString("sessionId")
        val code = json.optString("joinCode")
        if (id.matches(Regex("^[a-f0-9]{24}$")) && code.matches(Regex("^[a-f0-9]{32}$"))) id to code else null
    }
} catch (_: Exception) { null }

private fun makeQr(value: String): Bitmap? = try {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 720, 720)
    Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until 720) for (y in 0 until 720) {
            bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
} catch (_: Exception) { null }

private fun loadDevices(context: Context): List<SavedDevice> = try {
    val raw = context.getSharedPreferences("ofz_devices", Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
    val array = org.json.JSONArray(raw)
    buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val encrypted = item.optString("ownerToken")
            val token = TokenVault.decrypt(encrypted) ?: continue
            add(SavedDevice(item.getString("id"), item.optString("name", "Android"), token, item.optLong("addedAt")))
        }
    }
} catch (_: Exception) { emptyList() }

private fun saveDevices(context: Context, devices: List<SavedDevice>) {
    val array = org.json.JSONArray()
    devices.forEach { device ->
        val encrypted = TokenVault.encrypt(device.ownerToken) ?: return@forEach
        array.put(JSONObject().put("id", device.id).put("name", device.name)
            .put("ownerToken", encrypted).put("addedAt", device.addedAt))
    }
    context.getSharedPreferences("ofz_devices", Context.MODE_PRIVATE).edit().putString("items", array.toString()).apply()
}
