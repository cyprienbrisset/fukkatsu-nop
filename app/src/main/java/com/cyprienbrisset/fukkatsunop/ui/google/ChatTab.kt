package com.cyprienbrisset.fukkatsunop.ui.google

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import com.cyprienbrisset.fukkatsunop.BuildConfig
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.integration.google.ChatMessage
import com.cyprienbrisset.fukkatsunop.integration.google.ChatSpace
import com.cyprienbrisset.fukkatsunop.integration.google.ChatSpaceType
import com.cyprienbrisset.fukkatsunop.ui.sumi.SealIconButton
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MSG_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val MSG_ZONE: ZoneId = ZoneId.systemDefault()

@Composable
fun ChatTab(
    isLoggedIn: Boolean,
    chatAuthUrl: String?,
    spacesState: TabState<List<ChatSpace>>,
    messagesState: TabState<List<ChatMessage>>,
    selectedSpace: ChatSpace?,
    onSelectSpace: (ChatSpace) -> Unit,
    onBack: () -> Unit,
    onRetrySpaces: () -> Unit,
    onRetryMessages: () -> Unit,
    onReauth: () -> Unit,
    onStartChatAuth: () -> Unit,
    onChatAuthCode: (String) -> Unit,
    onDismissChatAuth: () -> Unit,
) {
    // WebView OAuth overlay
    if (chatAuthUrl != null) {
        OAuthWebView(
            url = chatAuthUrl,
            onCode = onChatAuthCode,
            onDismiss = onDismissChatAuth,
        )
        return
    }

    if (!isLoggedIn) {
        Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    "Connectez votre compte Chat",
                    fontFamily = Mincho,
                    fontSize = 17.sp,
                    color = Kinari,
                )
                Text(
                    "Autorisez l'accès à Google Chat pour voir\nvos conversations directement ici.",
                    fontFamily = Mincho,
                    fontSize = 13.sp,
                    color = SumiMuted,
                )
                SumiPrimaryButton(
                    "Connecter Chat",
                    onStartChatAuth,
                    Modifier.fillMaxWidth(0.55f),
                )
            }
        }
        return
    }
    if (selectedSpace != null) {
        BackHandler(onBack = onBack)
        MessagesPane(
            space = selectedSpace,
            state = messagesState,
            onBack = onBack,
            onRetry = onRetryMessages,
            onReauth = onReauth,
        )
    } else {
        SpacesPane(
            state = spacesState,
            onSelect = onSelectSpace,
            onRetry = onRetrySpaces,
            onReauth = onReauth,
        )
    }
}

// ── Spaces list ───────────────────────────────────────────────────────────────

@Composable
private fun SpacesPane(
    state: TabState<List<ChatSpace>>,
    onSelect: (ChatSpace) -> Unit,
    onRetry: () -> Unit,
    onReauth: () -> Unit,
) {
    when (state) {
        is TabState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Shu)
        }

        is TabState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.message == "403") {
                    Text(
                        "Reconnexion requise pour accéder à Chat",
                        color = SumiMuted, fontFamily = Mincho, fontSize = 15.sp,
                    )
                    SumiPrimaryButton("Se reconnecter", onReauth, Modifier.fillMaxWidth(0.5f))
                } else {
                    Text(state.message, color = SumiMuted, fontFamily = Mincho, fontSize = 15.sp)
                    SumiPrimaryButton("Réessayer", onRetry, Modifier.fillMaxWidth(0.5f))
                }
            }
        }

        is TabState.Success -> {
            val spaces = state.data
            if (spaces.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Aucune conversation", color = SumiMuted, fontFamily = Mincho, fontSize = 16.sp)
                }
                return
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(12.dp)) }
                items(spaces, key = { it.name }) { space ->
                    SpaceCard(space = space, onClick = { onSelect(space) })
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun SpaceCard(space: ChatSpace, onClick: () -> Unit) {
    val label = spaceLabel(space)
    val initial = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val typeLabel = when (space.type) {
        ChatSpaceType.ROOM       -> "Salon"
        ChatSpaceType.GROUP_CHAT -> "Groupe"
        ChatSpaceType.DM         -> "Message direct"
        ChatSpaceType.UNKNOWN    -> "Espace"
    }
    val icon = when (space.type) {
        ChatSpaceType.ROOM    -> Icons.Filled.Tag
        ChatSpaceType.DM      -> Icons.AutoMirrored.Filled.Message
        else                  -> Icons.Filled.Group
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SumiSurface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(AccentShu.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AccentShu, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = Mincho,
                fontSize = 15.sp,
                color = Kinari,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(typeLabel, fontFamily = Gothic, fontSize = 12.sp, color = SumiMuted)
        }

        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = SumiMuted,
            modifier = Modifier.size(16.dp).then(Modifier.padding(0.dp))
                // flip horizontally to make it ArrowForward
                .then(Modifier),
        )
    }
}

// ── Messages view ─────────────────────────────────────────────────────────────

@Composable
private fun MessagesPane(
    space: ChatSpace,
    state: TabState<List<ChatMessage>>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReauth: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(SumiSurface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SealIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                onClick = onBack,
                size = 40.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = spaceLabel(space),
                fontFamily = Mincho,
                fontSize = 16.sp,
                color = Kinari,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (state) {
            is TabState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Shu)
            }

            is TabState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.message == "403") {
                        Text(
                            "Reconnexion requise pour lire les messages",
                            color = SumiMuted, fontFamily = Mincho, fontSize = 15.sp,
                        )
                        SumiPrimaryButton("Se reconnecter", onReauth, Modifier.fillMaxWidth(0.5f))
                    } else {
                        Text(state.message, color = SumiMuted, fontFamily = Mincho, fontSize = 15.sp)
                        SumiPrimaryButton("Réessayer", onRetry, Modifier.fillMaxWidth(0.5f))
                    }
                }
            }

            is TabState.Success -> {
                val messages = state.data
                if (messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Aucun message", color = SumiMuted, fontFamily = Mincho, fontSize = 15.sp)
                    }
                    return@Column
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(messages, key = { it.name }) { msg ->
                        MessageRow(msg)
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(msg: ChatMessage) {
    val initial = remember(msg.senderName) {
        msg.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }
    val timeStr = remember(msg.createTime) {
        msg.createTime.atZone(MSG_ZONE).format(MSG_TIME_FMT)
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Sender avatar
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Shu.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, fontFamily = Mincho, fontSize = 14.sp, color = Kinari)
        }

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = msg.senderName,
                    fontFamily = Gothic,
                    fontSize = 12.sp,
                    color = SumiMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(timeStr, fontFamily = Gothic, fontSize = 11.sp, color = SumiMuted.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = msg.text,
                fontFamily = Mincho,
                fontSize = 14.sp,
                color = Kinari,
            )
        }
    }
}

// ── OAuth WebView ─────────────────────────────────────────────────────────────

@Composable
private fun OAuthWebView(
    url: String,
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val redirectScheme = remember {
        BuildConfig.GOOGLE_ANDROID_CLIENT_ID
            .removeSuffix(".apps.googleusercontent.com")
            .let { "com.googleusercontent.apps.$it" }
    }
    Box(Modifier.fillMaxSize().background(SumiSurface)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val uri = request.url
                            if (uri.scheme == redirectScheme) {
                                val code = uri.getQueryParameter("code")
                                if (code != null) onCode(code) else onDismiss()
                                return true
                            }
                            return false
                        }
                    }
                    settings.javaScriptEnabled = true
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        SealIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Annuler",
            onClick = onDismiss,
            size = 40.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun spaceLabel(space: ChatSpace): String = when {
    space.displayName.isNotBlank() -> space.displayName
    space.type == ChatSpaceType.DM -> "Message direct"
    else                           -> "Groupe"
}
