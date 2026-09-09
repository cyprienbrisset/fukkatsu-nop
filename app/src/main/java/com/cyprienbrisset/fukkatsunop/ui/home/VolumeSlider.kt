package com.cyprienbrisset.fukkatsunop.ui.home

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SpeakerPhone
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted

@Composable
fun VolumeSlider(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var showPicker by remember { mutableStateOf(false) }

    // Active BT output device via AudioManager (no special permission needed)
    val activeAudioBtDevice = remember {
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
    }
    val activeBtName = activeAudioBtDevice?.productName?.toString()

    // Bonded + connected BT devices via BluetoothProfile proxy
    var connectedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var bondedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    val hasBtPerm = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else true
    }

    DisposableEffect(hasBtPerm) {
        if (!hasBtPerm) return@DisposableEffect onDispose {}
        val btManager = ctx.getSystemService(BluetoothManager::class.java)
        val adapter = btManager?.adapter ?: return@DisposableEffect onDispose {}
        bondedDevices = adapter.bondedDevices?.toList() ?: emptyList()

        var proxy: BluetoothProfile? = null
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
                proxy = p
                connectedDevices = p.connectedDevices ?: emptyList()
            }
            override fun onServiceDisconnected(profile: Int) {
                connectedDevices = emptyList()
            }
        }
        adapter.getProfileProxy(ctx, listener, BluetoothProfile.A2DP)
        onDispose {
            proxy?.let { adapter.closeProfileProxy(BluetoothProfile.A2DP, it) }
        }
    }

    val isBtActive = activeBtName != null
    val btIconColor by animateColorAsState(
        targetValue = if (isBtActive) Shu else SumiMuted,
        animationSpec = tween(300),
        label = "bt-color",
    )

    Row(
        modifier.widthIn(max = 560.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Volume icon
        Icon(
            Icons.Rounded.VolumeUp,
            contentDescription = "Volume",
            tint = SumiMuted,
            modifier = Modifier.size(20.dp),
        )

        Slider(
            value = vol,
            onValueChange = { v -> vol = v; am.setStreamVolume(AudioManager.STREAM_MUSIC, v.toInt(), 0) },
            valueRange = 0f..max,
            colors = SliderDefaults.colors(thumbColor = Shu, activeTrackColor = Shu),
            modifier = Modifier.weight(1f),
        )

        // Bluetooth button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isBtActive) Shu.copy(alpha = 0.12f) else Color.Transparent)
                .clickable { showPicker = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    !hasBtPerm -> Icons.Rounded.BluetoothDisabled
                    isBtActive -> Icons.Rounded.BluetoothAudio
                    else -> Icons.Rounded.Bluetooth
                },
                contentDescription = "Bluetooth",
                tint = btIconColor,
                modifier = Modifier.size(20.dp),
            )
            if (isBtActive) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                        .align(Alignment.TopEnd),
                )
            }
        }
    }

    if (showPicker) {
        BluetoothDevicePicker(
            activeName = activeBtName,
            connected = connectedDevices,
            bonded = bondedDevices,
            onDismiss = { showPicker = false },
            onOpenSettings = {
                ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                showPicker = false
            },
        )
    }
}

@Composable
private fun BluetoothDevicePicker(
    activeName: String?,
    connected: List<BluetoothDevice>,
    bonded: List<BluetoothDevice>,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val connectedAddresses = remember(connected) { connected.map { it.address }.toSet() }

    // All bonded devices, connected ones first
    val sorted = remember(bonded, connected) {
        bonded.sortedByDescending { it.address in connectedAddresses }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bluetooth, null, tint = Shu, modifier = Modifier.size(26.dp))
                Spacer(Modifier.size(14.dp))
                Text(
                    "Sortie audio",
                    fontFamily = Mincho,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (activeName != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Shu.copy(alpha = 0.15f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(activeName, fontFamily = Mincho, color = Shu, fontSize = 13.sp)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (sorted.isEmpty()) {
                Text(
                    "Aucun appareil jumelé",
                    fontFamily = Mincho,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(28.dp),
                )
            } else {
                Column {
                    sorted.forEach { device ->
                        val isConnected = device.address in connectedAddresses
                        val isActive = device.name == activeName
                        DeviceRow(
                            name = device.name ?: device.address,
                            isConnected = isConnected,
                            isActive = isActive,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(
                    "Paramètres Bluetooth",
                    fontFamily = Mincho,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun DeviceRow(name: String, isConnected: Boolean, isActive: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isActive) Shu.copy(alpha = 0.07f) else Color.Transparent)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            if (isConnected) Icons.Rounded.BluetoothAudio else Icons.Rounded.SpeakerPhone,
            null,
            tint = if (isActive) Shu else if (isConnected) Color(0xFF4CAF50) else SumiMuted,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontFamily = Mincho,
                color = if (isActive) Shu else MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            )
            if (isConnected) {
                Text(
                    if (isActive) "Actif · Audio en cours" else "Connecté",
                    fontFamily = Mincho,
                    color = if (isActive) Shu.copy(alpha = 0.7f) else Color(0xFF4CAF50).copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            }
        }
        if (isActive) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(Shu)
            )
        }
    }
}
