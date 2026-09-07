package com.cyprienbrisset.fukkatsunop.ui.google

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cyprienbrisset.fukkatsunop.integration.google.MailBody
import com.cyprienbrisset.fukkatsunop.integration.google.MailMessage
import com.cyprienbrisset.fukkatsunop.ui.sumi.SealIconButton
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FMT_MAIL = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FMT_MAIL = DateTimeFormatter.ofPattern("dd/MM")

@Composable
fun MailTab(
    state: TabState<List<MailMessage>>,
    selectedMailBody: TabState<MailBody>?,
    selectedMessage: MailMessage?,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    if (selectedMailBody != null) {
        MailReader(
            bodyState = selectedMailBody,
            message = selectedMessage,
            onBack = onBack,
        )
    } else {
        MailInbox(
            state = state,
            onOpen = onOpen,
            onRetry = onRetry,
        )
    }
}

// ── Inbox ─────────────────────────────────────────────────────────────────────

@Composable
private fun MailInbox(
    state: TabState<List<MailMessage>>,
    onOpen: (String) -> Unit,
    onRetry: (() -> Unit)?,
) {
    when (state) {
        is TabState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Shu)
        }

        is TabState.Error -> Box(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.message,
                    color = SumiMuted,
                    fontFamily = Mincho,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                if (onRetry != null) {
                    Spacer(Modifier.height(16.dp))
                    SumiPrimaryButton("Réessayer", onRetry, Modifier.fillMaxWidth(0.5f))
                }
            }
        }

        is TabState.Success -> {
            if (state.data.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Aucune messagerie", color = SumiMuted, fontFamily = Mincho)
                }
                return
            }
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                item { Spacer(Modifier.height(12.dp)) }
                items(state.data, key = { it.id }) { message ->
                    MessageRow(message = message, onOpen = onOpen)
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun MessageRow(message: MailMessage, onOpen: (String) -> Unit) {
    val zone    = ZoneId.systemDefault()
    val today   = remember { LocalDate.now(zone) }
    val msgDate = remember(message.date) { message.date.atZone(zone).toLocalDate() }
    val dateStr = remember(msgDate, today) {
        if (msgDate == today) message.date.atZone(zone).format(TIME_FMT_MAIL)
        else                  message.date.atZone(zone).format(DATE_FMT_MAIL)
    }
    val weight = if (message.isUnread) FontWeight.Medium else FontWeight.Normal

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SumiSurface)
            .clickable { onOpen(message.id) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Unread dot
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Shu)
                .alpha(if (message.isUnread) 1f else 0f),
        )
        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.from,
                    fontFamily = Mincho,
                    fontSize = 14.sp,
                    color = Kinari,
                    fontWeight = weight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = dateStr,
                    fontFamily = Gothic,
                    fontSize = 12.sp,
                    color = SumiMuted,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = message.subject,
                fontFamily = Gothic,
                fontSize = 13.sp,
                color = Kinari,
                fontWeight = weight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = message.snippet,
                fontFamily = Gothic,
                fontSize = 12.sp,
                color = SumiMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Reader ────────────────────────────────────────────────────────────────────

@Composable
private fun MailReader(
    bodyState: TabState<MailBody>,
    message: MailMessage?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SealIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                onClick = onBack,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Mail",
                fontFamily = Mincho,
                fontSize = 18.sp,
                color = Kinari,
            )
        }

        when (bodyState) {
            is TabState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Shu)
            }

            is TabState.Error -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                Alignment.Center,
            ) {
                Text(
                    text = bodyState.message,
                    color = SumiMuted,
                    fontFamily = Mincho,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }

            is TabState.Success -> {
                // Header
                if (message != null) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "De : ${message.from}",
                            fontFamily = Mincho,
                            fontSize = 13.sp,
                            color = SumiMuted,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Objet : ${message.subject}",
                            fontFamily = Mincho,
                            fontSize = 13.sp,
                            color = Kinari,
                        )
                    }
                    HorizontalDivider(color = Shu, thickness = 1.dp)
                }

                // Body
                val body = bodyState.data
                when {
                    body.html != null -> {
                        val htmlWithCss = remember(body.html) {
                            "<html><head><style>body{background:#0D0E12;color:#ECE7DD;font-family:serif;font-size:15px;padding:12px;}a{color:#C1272D;}</style></head><body>${body.html}</body></html>"
                        }
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    setBackgroundColor(0xFF0D0E12.toInt())
                                }
                            },
                            update = { webView ->
                                webView.loadDataWithBaseURL(
                                    null,
                                    htmlWithCss,
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                            },
                            onRelease = { webView ->
                                webView.stopLoading()
                                webView.destroy()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    body.text != null -> {
                        Text(
                            text = body.text,
                            color = Kinari,
                            fontFamily = Mincho,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                        )
                    }

                    else -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text("Corps vide", color = SumiMuted, fontFamily = Mincho)
                        }
                    }
                }
            }
        }
    }
}
