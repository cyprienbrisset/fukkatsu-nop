package com.cyprienbrisset.myportal.ui.home

import android.app.Activity
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.ui.theme.Shu
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BrightnessEdgeControl(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var brightness by remember {
        mutableFloatStateOf(
            runCatching {
                Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            }.getOrDefault(0.5f).coerceIn(0.05f, 1f)
        )
    }
    var visible by remember { mutableStateOf(false) }
    var hideJob: Job? by remember { mutableStateOf(null) }

    fun applyBrightness(v: Float) {
        brightness = v.coerceIn(0.05f, 1f)
        if (Settings.System.canWrite(ctx)) {
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                (brightness * 255).toInt().coerceIn(1, 255),
            )
        } else {
            val activity = ctx as? Activity ?: return
            val params = activity.window.attributes
            params.screenBrightness = brightness
            activity.window.attributes = params
        }
    }

    fun scheduleHide() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(2000)
            visible = false
        }
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { visible = true; hideJob?.cancel() },
                        onDragEnd = { scheduleHide() },
                        onDragCancel = { scheduleHide() },
                    ) { _, delta ->
                        applyBrightness(brightness - delta.y / size.height)
                    }
                },
        )

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            BrightnessIndicator(brightness = brightness)
        }
    }
}

@Composable
private fun BrightnessIndicator(brightness: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Icon(
            Icons.Rounded.WbSunny,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(6.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(brightness)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Shu),
            )
        }
    }
}
