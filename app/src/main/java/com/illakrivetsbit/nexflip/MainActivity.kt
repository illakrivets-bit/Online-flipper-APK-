package com.illakrivetsbit.nexflip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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
import org.json.JSONObject
import java.util.UUID

private val Black = Color(0xFF050505)
private val Panel = Color(0xFF111111)
private val PanelRaised = Color(0xFF191919)
private val Stroke = Color(0xFF292929)
private val White = Color(0xFFF7F7F7)
private val Muted = Color(0xFF929292)
private val SoftWhite = Color(0xFFE5E5E5)

private data class SavedDevice(
    val id: String,
    val name: String,
    val state: String,
    val addedAt: Long
)

private data class PairInvite(
    val id: String,
    val key: String,
    val createdAt: Long,
    val expiresAt: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(5, 5, 5)
        window.navigationBarColor = AndroidColor.rgb(5, 5, 5)
        window.decorView.systemUiVisibility = 0
        setContent { NexFlipTheme { NexFlipApp() } }
    }
}

@Composable
private fun NexFlipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = White,
            onPrimary = Black,
            background = Black,
            surface = Panel,
            onSurface = White,
            onBackground = White,
            secondary = SoftWhite,
            outline = Stroke
        ),
        content = content
    )
}

@Composable
private fun NexFlipApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as ComponentActivity
    val devices = remember {
        mutableStateListOf<SavedDevice>().apply { addAll(loadSavedDevices(context)) }
    }
    var selected by remember { mutableStateOf<SavedDevice?>(null) }
    var invite by remember { mutableStateOf<PairInvite?>(null) }
    var inviteQr by remember { mutableStateOf<Bitmap?>(null) }
    var showInvite by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var scanBusy by remember { mutableStateOf(false) }

    BackHandler(enabled = selected != null || showAbout || showInvite) {
        when {
            showInvite -> showInvite = false
            showAbout -> showAbout = false
            else -> selected = null
        }
    }

    Box(Modifier.fillMaxSize().background(Black)) {
        when {
            selected != null -> DeviceControlScreen(
                device = selected!!,
                onBack = { selected = null },
                onRemove = {
                    devices.removeAll { it.id == selected!!.id }
                    saveDevices(context, devices)
                    selected = null
                }
            )
            showAbout -> AboutScreen(onBack = { showAbout = false })
            else -> HomeScreen(
                devices = devices,
                onCreateQr = {
                    val now = System.currentTimeMillis()
                    val newInvite = PairInvite(
                        id = UUID.randomUUID().toString(),
                        key = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                        createdAt = now,
                        expiresAt = now + 24L * 60L * 60L * 1000L
                    )
                    val payload = JSONObject().apply {
                        put("app", "nexflip")
                        put("version", 1)
                        put("session", newInvite.id)
                        put("key", newInvite.key)
                        put("createdAt", newInvite.createdAt)
                        put("expiresAt", newInvite.expiresAt)
                    }.toString()
                    invite = newInvite
                    inviteQr = makeQr(payload)
                    showInvite = true
                },
                onScan = {
                    if (!scanBusy) {
                        scanBusy = true
                        GmsBarcodeScanning.getClient(activity).startScan()
                            .addOnSuccessListener { barcode ->
                                val raw = barcode.rawValue.orEmpty()
                                val parsed = parseInvite(raw)
                                if (parsed == null) {
                                    Toast.makeText(context, "Это не QR-приглашение Online Flipper : Zero", Toast.LENGTH_LONG).show()
                                } else if (parsed.expiresAt <= System.currentTimeMillis()) {
                                    Toast.makeText(context, "Срок действия QR-кода истёк", Toast.LENGTH_LONG).show()
                                } else if (devices.none { it.id == parsed.id }) {
                                    val newDevice = SavedDevice(parsed.id, "Новое устройство", "Ожидает подключения", System.currentTimeMillis())
                                    devices.add(0, newDevice)
                                    saveDevices(context, devices)
                                    Toast.makeText(context, "Приглашение сохранено. Сервер подключения ещё не настроен.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Это устройство уже сохранено", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnCanceledListener { }
                            .addOnFailureListener {
                                Toast.makeText(context, "Не удалось открыть сканер QR", Toast.LENGTH_LONG).show()
                            }
                            .addOnCompleteListener { scanBusy = false }
                    }
                },
                onDevice = { selected = it },
                onAbout = { showAbout = true }
            )
        }

        if (showInvite && invite != null) {
            InviteDialog(
                qr = inviteQr,
                expiresAt = invite!!.expiresAt,
                onDismiss = { showInvite = false }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    devices: List<SavedDevice>,
    onCreateQr: () -> Unit,
    onScan: () -> Unit,
    onDevice: (SavedDevice) -> Unit,
    onAbout: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val introAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(650, easing = FastOutSlowInEasing), label = "introAlpha")
    Column(
        Modifier.fillMaxSize().background(Black).statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Online Flipper : Zero", color = White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 0.3.sp, maxLines = 1, softWrap = false)
                Text("DEVICE CONTROL", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
            }
            Box(
                Modifier.size(42.dp).background(White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("N", color = Black, fontSize = 23.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(30.dp))
        Column(Modifier.alpha(introAlpha)) {
            Text("ТВОИ УСТРОЙСТВА", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Всё под\nконтролем.", color = White, fontSize = 36.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.4).sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Подключай свои телефоны по QR-коду и открывай их панель управления.",
                color = Muted, fontSize = 13.sp, lineHeight = 19.sp
            )
        }
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.fillMaxWidth().background(White, RoundedCornerShape(22.dp))
                .clickable(onClick = onCreateQr).padding(20.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("НОВОЕ ПОДКЛЮЧЕНИЕ", color = Color(0xFF555555), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("Создать QR-код", color = Black, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Один нажим — приглашение готово", color = Color(0xFF666666), fontSize = 11.sp)
                }
                Box(Modifier.size(52.dp).background(Black, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text("⌗", color = White, fontSize = 31.sp, fontWeight = FontWeight.Light)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("СОЗДАТЬ ПРИГЛАШЕНИЕ", color = Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text("↗", color = Black, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(11.dp))
        OutlinedButton(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Stroke),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
        ) {
            Text("▦", fontSize = 18.sp)
            Spacer(Modifier.width(9.dp))
            Text("СКАНИРОВАТЬ QR", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("СОХРАНЁННЫЕ УСТРОЙСТВА", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Spacer(Modifier.weight(1f))
            Text(devices.size.toString().padStart(2, '0'), color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (devices.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().weight(1f).background(Panel, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("▱", color = Color(0xFF555555), fontSize = 36.sp)
                    Text("Пока здесь пусто", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Создай QR-код на другом телефоне\nи отсканируй его здесь.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceRow(device = device, onClick = { onDevice(device) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Online Flipper : Zero  •  PRIVATE CONNECTIONS", color = Color(0xFF555555), fontSize = 8.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text("ABOUT", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onAbout))
        }
    }
}

@Composable
private fun DeviceRow(device: SavedDevice, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(43.dp).background(PanelRaised, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Text("▯", color = White, fontSize = 24.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(device.name, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(device.state, color = Muted, fontSize = 10.sp)
        }
        Text("›", color = Muted, fontSize = 24.sp)
    }
}

@Composable
private fun InviteDialog(qr: Bitmap?, expiresAt: Long, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(White, RoundedCornerShape(26.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ПРИГЛАШЕНИЕ", color = Color(0xFF666666), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, modifier = Modifier.weight(1f))
                Text("×", color = Black, fontSize = 25.sp, modifier = Modifier.clickable(onClick = onDismiss))
            }
            Spacer(Modifier.height(14.dp))
            Text("Отсканируй\nэтот код", color = Black, fontSize = 27.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            if (qr != null) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = "QR invitation",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("Приглашение действует 24 часа", color = Color(0xFF555555), fontSize = 11.sp)
            Spacer(Modifier.height(5.dp))
            Text("Для реального соединения нужен активный сервер Online Flipper : Zero.", color = Color(0xFF777777), fontSize = 10.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Black, contentColor = White)
            ) { Text("ЗАКРЫТЬ", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp) }
        }
    }
}

@Composable
private fun DeviceControlScreen(device: SavedDevice, onBack: () -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Black).statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 15.dp)
    ) {
        Text("‹  НАЗАД К УСТРОЙСТВАМ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(28.dp))
        Text("УПРАВЛЕНИЕ УСТРОЙСТВОМ", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(7.dp))
        Text(device.name, color = White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(device.state, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(38.dp).background(PanelRaised, CircleShape), contentAlignment = Alignment.Center) {
                Text("!", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Соединение ещё не активно", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("QR сохранён локально. Сервер и связь с компаньоном пока не настроены.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("ПАНЕЛЬ УПРАВЛЕНИЯ", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(11.dp))
        ControlGroup("ЭКРАН И НАВИГАЦИЯ", listOf("Просмотр экрана", "Главный экран", "Назад", "Недавние приложения"))
        Spacer(Modifier.height(9.dp))
        ControlGroup("СОСТОЯНИЕ ТЕЛЕФОНА", listOf("Батарея и заряд", "Сеть и Wi-Fi", "Память устройства", "Информация об Android"))
        Spacer(Modifier.height(9.dp))
        ControlGroup("БЫСТРЫЕ ДЕЙСТВИЯ", listOf("Найти телефон звуковым сигналом", "Вибрация", "Фонарик", "Запросить обновление статуса"))
        Spacer(Modifier.weight(1f))
        Text("Действия станут доступны после настройки безопасного соединения и разрешений на втором телефоне.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Stroke),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
        ) { Text("УДАЛИТЬ ИЗ СОХРАНЁННЫХ", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
    }
}

@Composable
private fun ControlGroup(title: String, actions: List<String>) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(17.dp)).padding(14.dp)) {
        Text(title, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(9.dp))
        actions.forEachIndexed { index, action ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(action, color = SoftWhite, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("LOCKED", color = Color(0xFF666666), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
            }
            if (index != actions.lastIndex) Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Black).statusBarsPadding().padding(22.dp)) {
        Text("‹  НАЗАД", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(30.dp))
        Text("Online Flipper : Zero", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, maxLines = 1, softWrap = false)
        Text("DEVICE CONTROL", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(20.dp))
        Text("Один экран. Твои устройства. Твой контроль.", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Приложение использует QR-приглашения и локально сохраняет список устройств. Для настоящего удалённого управления необходим развёрнутый сервер, защищённый канал связи и приложение-компаньон с явными разрешениями на управляемом телефоне.",
            color = Muted, fontSize = 12.sp, lineHeight = 19.sp
        )
        Spacer(Modifier.height(18.dp))
        Text("Версия 0.3.0 • интерфейс управления", color = Color(0xFF666666), fontSize = 10.sp)
    }
}

private fun makeQr(value: String): Bitmap? = try {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 720, 720)
    Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until 720) for (y in 0 until 720) {
            bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
} catch (_: Exception) { null }

private fun parseInvite(raw: String): PairInvite? {
    return try {
        val json = JSONObject(raw)
        if (json.optString("app") != "nexflip" || json.optInt("version") != 1) {
            null
        } else {
            val id = json.getString("session")
            val key = json.getString("key")
            val createdAt = json.getLong("createdAt")
            val expiresAt = json.getLong("expiresAt")
            if (id.isBlank() || key.length < 32 || expiresAt <= createdAt) null
            else PairInvite(id, key, createdAt, expiresAt)
        }
    } catch (_: Exception) {
        null
    }
}

private fun loadSavedDevices(context: Context): List<SavedDevice> {
    return try {
        val raw = context.getSharedPreferences("nexflip_devices", Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
        val array = org.json.JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(SavedDevice(item.getString("id"), item.optString("name", "Android"), item.optString("state", "Сохранено"), item.optLong("addedAt")))
            }
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveDevices(context: Context, devices: List<SavedDevice>) {
    val array = org.json.JSONArray()
    devices.forEach { device ->
        array.put(JSONObject().apply {
            put("id", device.id)
            put("name", device.name)
            put("state", device.state)
            put("addedAt", device.addedAt)
        })
    }
    context.getSharedPreferences("nexflip_devices", Context.MODE_PRIVATE)
        .edit().putString("items", array.toString()).apply()
}
