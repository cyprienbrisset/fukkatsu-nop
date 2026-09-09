package com.cyprienbrisset.fukkatsunop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.data.weather.Weather
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AmbientBanner(
    now: LocalDateTime,
    weather: Weather?,
    modifier: Modifier = Modifier,
    nextAlarm: LocalDateTime? = null,
    portrait: Boolean = false,
    compact: Boolean = false,
    onClockClick: () -> Unit = {},
    cityName: String? = null,
    cityIndex: Int = 0,
    citiesCount: Int = 1,
    onNextCity: () -> Unit = {},
    onPrevCity: () -> Unit = {},
    onRefreshWeather: () -> Unit = {},
    weatherFetchedAt: Long? = null,
    weatherError: String? = null,
) {
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))

    val weatherSwipeModifier = if (citiesCount > 1) {
        Modifier.pointerInput(Unit) {
            var totalX = 0f
            detectDragGestures(
                onDragEnd = {
                    when {
                        totalX < -40f -> onNextCity()
                        totalX > 40f -> onPrevCity()
                    }
                    totalX = 0f
                },
                onDragCancel = { totalX = 0f },
            ) { _, delta -> totalX += delta.x }
        }
    } else Modifier

    val clockSize = when { compact -> 60.sp; portrait -> 72.sp; else -> 92.sp }
    val dateSize  = if (compact) 13.sp else 16.sp
    val tempSize  = if (compact) 20.sp else 28.sp
    val descSize  = if (compact) 12.sp else 14.sp

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clickable { onClockClick() }) {
            Text(
                time,
                fontFamily = Mincho,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = clockSize,
            )
        }
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
        Text(date, color = MaterialTheme.colorScheme.onBackground, fontSize = dateSize)
        if (weather != null || weatherError != null) {
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onRefreshWeather() }.then(weatherSwipeModifier),
            ) {
                if (weather != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "${weather.temperatureC}°",
                            color = Shu,
                            fontSize = tempSize,
                            fontWeight = FontWeight.Light,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            weather.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = descSize,
                        )
                    }
                }
                if (weatherError != null && weather == null) {
                    Text("⚠ $weatherError", color = SumiMuted, fontFamily = Mincho, fontSize = 12.sp)
                    Text("↺ Actualiser", color = SumiMuted.copy(alpha = 0.6f), fontFamily = Mincho, fontSize = 11.sp)
                }
                val ageMinutes = weatherFetchedAt?.let { ((System.currentTimeMillis() - it) / 60_000f).roundToInt() }
                if (ageMinutes != null && ageMinutes >= 20) {
                    Text(
                        "il y a $ageMinutes min — actualiser",
                        color = SumiMuted.copy(alpha = 0.6f),
                        fontFamily = Mincho,
                        fontSize = 11.sp,
                    )
                }
                if (cityName != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        cityName,
                        color = SumiMuted,
                        fontFamily = Mincho,
                        fontSize = 12.sp,
                    )
                }
                if (citiesCount > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(citiesCount) { i ->
                            Box(
                                Modifier.size(if (i == cityIndex) 6.dp else 4.dp)
                                    .clip(CircleShape)
                                    .background(if (i == cityIndex) AccentShu else SumiMuted.copy(alpha = 0.35f)),
                            )
                        }
                    }
                }
            }
        }
        if (nextAlarm != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                "↑ " + nextAlarm.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.FRENCH)),
                color = AccentShu,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
fun HomeBranding(portrait: Boolean, compact: Boolean = false, modifier: Modifier = Modifier) {
    val kanjiSize = when { compact -> 80.sp; portrait -> 84.sp; else -> 120.sp }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "復活",
            fontFamily = Mincho,
            fontWeight = FontWeight.Medium,
            color = Shu,
            fontSize = kanjiSize,
        )
        Text(
            "F U K K A T S U  N O  P",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (compact) 11.sp else 13.sp,
            letterSpacing = if (compact) 4.sp else 6.sp,
        )
    }
}
