package com.cyprienbrisset.fukkatsunop.ui.airplay

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayPrefs
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayReceiver
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayService
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayState
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiButton
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiIconButton
import com.cyprienbrisset.fukkatsunop.ui.fuji.StatusDot
import com.cyprienbrisset.fukkatsunop.ui.fuji.glass
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic

/** "Use the Portal as a Mac display": status and the three steps to follow on the Mac. */
@Composable
fun MacWaitingScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val state by AirPlayReceiver.state.collectAsState()
    val visible by AirPlayReceiver.advertising.collectAsState()
    val name = remember { AirPlayPrefs.getName(ctx) }

    val (dot, status) = when {
        !visible -> FujiColors.Badge to "Écran Mac masqué"
        state is AirPlayState.Connecting -> FujiColors.Waiting to "Connexion du Mac…"
        state is AirPlayState.Streaming -> FujiColors.Ok to "Mac connecté · réception de l'image…"
        state is AirPlayState.Error -> FujiColors.Badge to "Erreur : ${(state as AirPlayState.Error).msg}"
        else -> FujiColors.Waiting to "En attente du Mac · visible sous « $name »"
    }

    Box(Modifier.fillMaxSize()) {
        FujiIconButton(Icons.Rounded.Close, "Fermer", onClose, Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 72.dp))

        Column(
            Modifier
                .align(Alignment.Center)
                .width(1200.dp)
                .glass(RoundedCornerShape(48.dp), FujiColors.GlassDark)
                .padding(horizontal = 72.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Icon(Icons.Rounded.Laptop, null, tint = FujiColors.Cream, modifier = Modifier.size(120.dp))
            Text("Prêt à servir d'écran", color = FujiColors.Cream, fontFamily = Gothic, fontSize = 52.sp, fontWeight = FontWeight.Medium)
            Row(
                Modifier.height(64.dp).clip(RoundedCornerShape(32.dp)).glass(RoundedCornerShape(32.dp), FujiColors.GlassStrong).padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatusDot(dot)
                Text(status, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 24.sp)
            }

            if (visible && state is AirPlayState.Waiting) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Step(1, "Sur le Mac, ouvrez le ", "Centre de contrôle", " puis Recopie de l'écran", Modifier.weight(1f))
                    Step(2, "Choisissez ", name, " dans la liste", Modifier.weight(1f))
                    Step(3, "", "Écran séparé", " pour étendre le bureau, ou Recopier", Modifier.weight(1f))
                }
                Text(
                    "Pendant la diffusion, touchez l'écran pour afficher « Déconnecter »",
                    color = FujiColors.Faint, fontFamily = Gothic, fontSize = 20.sp, textAlign = TextAlign.Center,
                )
            } else if (!visible) {
                FujiButton(
                    "Rendre le Portal visible", { ctx.startForegroundService(Intent(ctx, AirPlayService::class.java)) },
                    primary = true, height = 96.dp,
                )
            }
        }
    }
}

@Composable
private fun Step(n: Int, before: String, bold: String, after: String, modifier: Modifier = Modifier) {
    Column(
        modifier.glass(RoundedCornerShape(30.dp), FujiColors.Glass).padding(30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(FujiColors.Cream), contentAlignment = Alignment.Center) {
            Text("$n", color = FujiColors.Ink, fontFamily = Gothic, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            buildAnnotatedString {
                append(before)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                append(after)
            },
            color = FujiColors.Cream, fontFamily = Gothic, fontSize = 24.sp, lineHeight = 32.sp,
        )
    }
}
