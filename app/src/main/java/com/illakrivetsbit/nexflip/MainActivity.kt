package com.illakrivetsbit.nexflip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    Module("Bluetooth", "ᛒ", "Connect to nearby devices", "Find and connect to compatible Bluetooth devices you own or are allowed to manage. Actual controls depend on each device."),
    Module("Wi-Fi", "⌁", "Local network devices", "Connect to supported devices on your own network using their official local-control features. This app will not break into networks."),
    Module("Infrared", "◉", "TVs and projectors", "Send remote-control commands when your phone has an IR blaster or a supported external IR accessory is connected."),
    Module("NFC / RFID", "▦", "Compatible tags", "Read supported NFC tags and use permitted tag interactions. This does not clone access cards or bypass locks."),
    Module("Smart Home", "⌂", "Home device control", "A future module for devices you own, using their supported APIs and secure pairing."),
    Module("Device Scanner", "⌕", "Discover nearby devices", "Show devices that are discoverable through Android's permitted Bluetooth and local-network APIs.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var selected by remember { mutableStateOf<Module?>(null) }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    if (selected == null) {
                        HomeScreen(onSelect = { selected = it })
                    } else {
                        ModuleScreen(
                            module = selected!!,
                            onBack = { selected = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onSelect: (Module) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NEX", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
            Text("FLIP", color = Accent, fontSize = 29.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("MOBILE LAB", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(22.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(18.dp))
                .padding(17.dp)
        ) {
            Text("DEVICE CONTROL CENTER", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Your devices. One place.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Phone ready  •  External module not connected",
                color = Muted,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(22.dp))
        Text("MODULES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(modules) { module ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(16.dp))
                        .clickable { onSelect(module) }
                        .padding(15.dp)
                ) {
                    Text(module.symbol, color = Accent, fontSize = 25.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(module.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(module.description, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
        Text(
            "Only connect to devices you own or have permission to control.",
            color = Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun ModuleScreen(module: Module, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(22.dp)
    ) {
        Text(
            "‹  BACK",
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() }
        )
        Spacer(Modifier.height(35.dp))
        Text(module.symbol, color = Accent, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(module.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(module.detail, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(28.dp))
        Text(
            "MODULE STATUS",
            color = Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Not connected yet",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The next development step is to add the real Android connection flow, permission prompts and device-specific controls for this module.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
