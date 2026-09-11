package com.cyprienbrisset.fukkatsunop.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.media.NowPlaying
import com.cyprienbrisset.fukkatsunop.ui.theme.Ink2
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.OnShu
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiLine
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface

@Composable
fun NowPlayingBar(
    np: NowPlaying,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit = {},
    onOpenApp: () -> Unit = {},
) {
    Column(
        modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SumiSurface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Tapping the artwork or title opens the app currently playing.
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).background(Ink2).clickable { onOpenApp() },
                contentAlignment = Alignment.Center,
            ) {
                val art = np.art
                if (art != null) {
                    Image(art.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = SumiMuted, modifier = Modifier.size(36.dp))
                }
            }
            Column(Modifier.weight(1f).clickable { onOpenApp() }) {
                Text(
                    np.title.ifBlank { "Lecture en cours" },
                    color = Kinari, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (np.artist.isNotBlank()) {
                    Text(np.artist, color = SumiMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                Icons.Rounded.SkipPrevious, "Précédent", tint = Kinari,
                modifier = Modifier.size(42.dp).clickable { onPrev() },
            )
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(AccentShu).clickable { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    "Play/Pause", tint = OnShu, modifier = Modifier.size(34.dp),
                )
            }
            Icon(
                Icons.Rounded.SkipNext, "Suivant", tint = Kinari,
                modifier = Modifier.size(42.dp).clickable { onNext() },
            )
        }

        if (np.durationMs > 0 && np.positionMs >= 0) {
            SeekBar(np = np, onSeek = onSeek)
        }
        HorizontalDivider(color = SumiLine, thickness = 1.dp)
        VolumeSlider(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SeekBar(np: NowPlaying, onSeek: (Long) -> Unit) {
    val dur = np.durationMs
    // While the user drags, `scrub` holds the pending fraction so the thumb doesn't jump back on
    // the next per-second refresh. Reset when the track changes.
    var scrub by remember(np.title, dur) { mutableStateOf<Float?>(null) }
    val fraction = (scrub ?: (np.positionMs.toFloat() / dur)).coerceIn(0f, 1f)
    val shownPos = (scrub?.let { (it * dur).toLong() } ?: np.positionMs)

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { if (np.canSeek) scrub = it },
            onValueChangeFinished = {
                scrub?.let { onSeek((it * dur).toLong()) }
                scrub = null
            },
            enabled = np.canSeek,
            colors = SliderDefaults.colors(
                thumbColor = AccentShu,
                activeTrackColor = AccentShu,
                inactiveTrackColor = SumiLine,
                disabledThumbColor = SumiMuted,
                disabledActiveTrackColor = SumiMuted,
                disabledInactiveTrackColor = SumiLine,
            ),
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shownPos), color = SumiMuted, fontSize = 12.sp)
            Text(formatTime(dur), color = SumiMuted, fontSize = 12.sp)
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "--:--"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
