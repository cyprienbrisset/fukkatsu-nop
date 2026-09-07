package com.cyprienbrisset.fukkatsunop.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.data.weather.Weather
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AmbientBanner(
    now: LocalDateTime,
    weather: Weather?,
    modifier: Modifier = Modifier,
    nextAlarm: LocalDateTime? = null,
    portrait: Boolean = false,
) {
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            time,
            fontFamily = Mincho,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (portrait) 72.sp else 92.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(date, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
        if (weather != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "${weather.temperatureC}°",
                    color = Shu,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    weather.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }
        if (nextAlarm != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                "⏰ " + nextAlarm.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.FRENCH)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
fun HomeBranding(portrait: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "復活",
            fontFamily = Mincho,
            fontWeight = FontWeight.Medium,
            color = Shu,
            fontSize = if (portrait) 84.sp else 120.sp,
        )
        Text(
            "F U K K A T S U  N O  P",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            letterSpacing = 6.sp,
        )
    }
}
