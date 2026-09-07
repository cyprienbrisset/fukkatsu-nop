package com.cyprienbrisset.fukkatsunop.ui.google

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted

@Composable
fun GoogleAuthScreen(
    authState: AuthState,
    onStartAuth: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (authState) {
            is AuthState.Loading -> {
                CircularProgressIndicator(
                    color = Shu,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is AuthState.NotLoggedIn -> {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "復活",
                        fontFamily = Mincho,
                        fontSize = 56.sp,
                        color = Shu,
                    )
                    Text(
                        text = "Mode Google",
                        fontFamily = Mincho,
                        fontSize = 22.sp,
                        color = Kinari,
                    )
                    Text(
                        text = "Agenda · Mail · Meet",
                        fontFamily = Mincho,
                        fontSize = 15.sp,
                        color = SumiMuted,
                    )
                    Spacer(Modifier.height(20.dp))
                    if (authState.error != null) {
                        Text(
                            text = authState.error,
                            fontFamily = Mincho,
                            fontSize = 13.sp,
                            color = Shu,
                            textAlign = TextAlign.Center,
                        )
                    }
                    SumiPrimaryButton(text = "Connecter mon compte", onClick = onStartAuth)
                }
            }

            is AuthState.DeviceFlow -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val qrBitmap = remember(authState.verificationUrl) {
                        generateQrBitmap(authState.verificationUrl, 540)
                    }
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR code",
                        modifier = Modifier.size(180.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = authState.userCode,
                        fontFamily = Mincho,
                        fontSize = 40.sp,
                        color = Shu,
                        letterSpacing = 6.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = authState.verificationUrl.removePrefix("https://"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SumiMuted,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Scannez le QR ou ouvrez l'URL\nsur votre téléphone ou ordinateur",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SumiMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    CircularProgressIndicator(color = Shu, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "En attente d'autorisation…",
                        style = MaterialTheme.typography.labelMedium,
                        color = SumiMuted,
                    )
                }
            }

            is AuthState.LoggedIn -> { /* handled by parent */ }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix[x, y]) 0xFF14161C.toInt() else 0xFFF2EDE3.toInt())
        }
    }
    return bmp
}
