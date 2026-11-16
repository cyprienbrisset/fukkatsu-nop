package com.cyprienbrisset.fukkatsunop.ui.google

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cyprienbrisset.fukkatsunop.BuildConfig
import com.cyprienbrisset.fukkatsunop.integration.google.ChatMessage
import com.cyprienbrisset.fukkatsunop.integration.google.ChatPreview
import com.cyprienbrisset.fukkatsunop.integration.google.ChatSpace
import com.cyprienbrisset.fukkatsunop.integration.google.ChatSpaceType
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiButton
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiIconButton
import com.cyprienbrisset.fukkatsunop.ui.fuji.glass
import com.cyprienbrisset.fukkatsunop.ui.sumi.SealIconButton
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val FR = Locale.FRENCH
private val ZONE: ZoneId = ZoneId.systemDefault()
private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val WeekdayFmt = DateTimeFormatter.ofPattern("EEE", FR)
private val ShortDateFmt = DateTimeFormatter.ofPattern("d MMM", FR)
private val DayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)

/** Soft colours that tell people apart on the dark glass. */
private val PersonColors = listOf(
    Color(0xFF8FB8DE), Color(0xFFE3A587), Color(0xFF9CCB9A),
    Color(0xFFD9A5C8), Color(0xFFE6C77A), Color(0xFFA8A3E0),
)

private fun colorOf(key: String) = PersonColors[Math.floorMod(key.hashCode(), PersonColors.size)]

/**
 * Google Chat, read-only, laid out like a messaging app for the wide Portal screen:
 * conversations on the left (most recent first), the open one on the right in bubbles.
 */
@Composable
fun ChatTab(
    isLoggedIn: Boolean,
    chatAuthUrl: String?,
    spacesState: TabState<List<ChatSpace>>,
    messagesState: TabState<List<ChatMessage>>,
    selectedSpace: ChatSpace?,
    previews: Map<String, ChatPreview>,
    me: String?,
    onSelectSpace: (ChatSpace) -> Unit,
    onLoadPreviews: () -> Unit,
    onRefresh: () -> Unit,
    onRetrySpaces: () -> Unit,
    onRetryMessages: () -> Unit,
    onReauth: () -> Unit,
    onStartChatAuth: () -> Unit,
    onChatAuthCode: (String) -> Unit,
    onDismissChatAuth: () -> Unit,
) {
    if (chatAuthUrl != null) {
        OAuthWebView(url = chatAuthUrl, onCode = onChatAuthCode, onDismiss = onDismissChatAuth)
        return
    }
    if (!isLoggedIn) {
        Notice(
            title = "Connectez Google Chat",
            text = "Vos conversations s'affichent ici, en lecture seule.",
            action = "Connecter Chat",
            onAction = onStartChatAuth,
        )
        return
    }
    when (spacesState) {
        is TabState.Loading -> Loading()
        is TabState.Error -> ErrorNotice(spacesState.message, onRetrySpaces, onReauth)
        is TabState.Success -> {
            val spaces = spacesState.data
            if (spaces.isEmpty()) {
                Notice("Aucune conversation", "Les conversations apparaissent après leur premier message.")
                return
            }
            LaunchedEffect(spaces) {
                onLoadPreviews()
                if (selectedSpace == null) onSelectSpace(spaces.first())
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ConversationList(spaces, previews, me, selectedSpace, onSelectSpace, Modifier.width(620.dp).fillMaxHeight())
                if (selectedSpace != null) {
                    Conversation(
                        space = selectedSpace,
                        title = titleOf(selectedSpace, previews),
                        state = messagesState,
                        me = me,
                        onRefresh = onRefresh,
                        onRetry = onRetryMessages,
                        onReauth = onReauth,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

private fun titleOf(space: ChatSpace, previews: Map<String, ChatPreview>): String = when {
    space.displayName.isNotBlank() -> space.displayName
    previews[space.name]?.title != null -> previews[space.name]!!.title!!
    space.type == ChatSpaceType.DM -> "Message direct"
    else -> "Groupe sans nom"
}

private fun initialsOf(title: String): String =
    title.split(' ', '-', '·').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** "14:05" today, "Hier", "lun." this week, "9 nov." before. */
private fun shortWhen(t: Instant): String {
    val d = t.atZone(ZONE)
    val days = ChronoUnit.DAYS.between(d.toLocalDate(), LocalDate.now(ZONE))
    return when {
        days <= 0 -> d.format(TimeFmt)
        days == 1L -> "Hier"
        days < 7 -> d.format(WeekdayFmt)
        else -> d.format(ShortDateFmt)
    }
}

private fun dayLabel(day: LocalDate): String {
    val days = ChronoUnit.DAYS.between(day, LocalDate.now(ZONE))
    return when (days) {
        0L -> "Aujourd'hui"
        1L -> "Hier"
        else -> day.format(DayFmt).replaceFirstChar { it.titlecase(FR) }
    }
}

// ── Conversation list ────────────────────────────────────────────────────────

@Composable
private fun ConversationList(
    spaces: List<ChatSpace>,
    previews: Map<String, ChatPreview>,
    me: String?,
    selected: ChatSpace?,
    onSelect: (ChatSpace) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.glass(RoundedCornerShape(36.dp), FujiColors.GlassDark),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(spaces, key = { it.name }) { space ->
            val title = titleOf(space, previews)
            val last = previews[space.name]?.last
            val active = space.name == selected?.name
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (active) FujiColors.GlassStrong else Color.Transparent)
                    .clickable { onSelect(space) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Avatar(space, title, 76.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 26.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        (last?.createTime ?: space.lastActive)?.let {
                            Text(shortWhen(it), color = FujiColors.Faint, fontFamily = Gothic, fontSize = 19.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    if (last != null) {
                        val who = when {
                            me != null && last.senderId == me -> "Vous : "
                            space.type != ChatSpaceType.DM && last.senderName != "?" -> last.senderName.substringBefore(' ') + " : "
                            else -> ""
                        }
                        Text(
                            who + last.text.replace('\n', ' '), color = FujiColors.Muted, fontFamily = Gothic, fontSize = 21.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(space: ChatSpace, title: String, size: androidx.compose.ui.unit.Dp) {
    val tint = colorOf(space.name)
    val icon: ImageVector? = when (space.type) {
        ChatSpaceType.ROOM -> Icons.Rounded.Tag
        ChatSpaceType.GROUP_CHAT -> Icons.Rounded.Groups
        else -> null
    }
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.48f))
        else Text(initialsOf(title), color = tint, fontFamily = Gothic, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Medium)
    }
}

// ── Open conversation ────────────────────────────────────────────────────────

@Composable
private fun Conversation(
    space: ChatSpace,
    title: String,
    state: TabState<List<ChatMessage>>,
    me: String?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onReauth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // New messages arrive without touching anything: the Portal sits on a shelf.
    LaunchedEffect(space.name) {
        while (true) { delay(20_000); onRefresh() }
    }
    Column(modifier.glass(RoundedCornerShape(36.dp), FujiColors.GlassDark)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Avatar(space, title, 64.dp)
            Column(Modifier.weight(1f)) {
                Text(title, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 32.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (space.type) {
                        ChatSpaceType.DM -> "Message direct · lecture seule"
                        ChatSpaceType.ROOM -> "Espace · lecture seule"
                        else -> "Groupe · lecture seule"
                    },
                    color = FujiColors.Faint, fontFamily = Gothic, fontSize = 19.sp,
                )
            }
            FujiIconButton(Icons.Rounded.Refresh, "Actualiser", onRefresh, size = 72.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(FujiColors.Line))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (state) {
                is TabState.Loading -> Loading()
                is TabState.Error -> ErrorNotice(state.message, onRetry, onReauth)
                is TabState.Success -> {
                    if (state.data.isEmpty()) Notice("Aucun message", "Cette conversation est vide.")
                    else Messages(state.data, me, groupNames = space.type != ChatSpaceType.DM)
                }
            }
        }
    }
}

/** [messages] newest first; drawn bottom-up so the latest sits at the bottom. */
@Composable
private fun Messages(messages: List<ChatMessage>, me: String?, groupNames: Boolean) {
    LazyColumn(
        Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
    ) {
        itemsIndexed(messages, key = { _, m -> m.name }) { i, msg ->
            val older = messages.getOrNull(i + 1)
            val newer = messages.getOrNull(i - 1)
            val day = msg.createTime.atZone(ZONE).toLocalDate()
            val newDay = older == null || older.createTime.atZone(ZONE).toLocalDate() != day
            fun sameRun(a: ChatMessage?, b: ChatMessage) =
                a != null && a.senderId == b.senderId && a.senderName == b.senderName &&
                    kotlin.math.abs(a.createTime.epochSecond - b.createTime.epochSecond) < 600
            val startsRun = newDay || !sameRun(older, msg)
            val endsRun = newer == null || !sameRun(newer, msg) ||
                newer.createTime.atZone(ZONE).toLocalDate() != day
            Column {
                if (newDay) DaySeparator(dayLabel(day))
                Bubble(msg, mine = me != null && msg.senderId == me, showName = startsRun && groupNames, showTime = endsRun, topGap = startsRun && !newDay)
            }
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Text(
            label, color = FujiColors.Muted, fontFamily = Gothic, fontSize = 19.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(FujiColors.Glass).padding(horizontal = 22.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Bubble(msg: ChatMessage, mine: Boolean, showName: Boolean, showTime: Boolean, topGap: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = if (topGap) 18.dp else 6.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (showName && !mine && msg.senderName != "?") {
            Text(
                msg.senderName, color = colorOf(msg.senderId.ifEmpty { msg.senderName }), fontFamily = Gothic,
                fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
            )
        }
        Text(
            msg.text,
            color = if (mine) FujiColors.Ink else FujiColors.Cream,
            fontFamily = Gothic,
            fontSize = 25.sp,
            lineHeight = 35.sp,
            modifier = Modifier
                .widthIn(max = 820.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (mine) FujiColors.Cream else FujiColors.GlassStrong)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        )
        if (showTime) {
            Text(
                msg.createTime.atZone(ZONE).format(TimeFmt), color = FujiColors.Faint, fontFamily = Gothic, fontSize = 17.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
    }
}

// ── States ───────────────────────────────────────────────────────────────────

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = FujiColors.Cream, modifier = Modifier.size(64.dp)) }
}

@Composable
private fun Notice(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(48.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Icons.Rounded.Forum, null, tint = FujiColors.Muted, modifier = Modifier.size(72.dp))
            Text(title, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 34.sp, fontWeight = FontWeight.Medium)
            Text(text, color = FujiColors.Muted, fontFamily = Gothic, fontSize = 23.sp, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                FujiButton(action, onAction, primary = true)
            }
        }
    }
}

@Composable
private fun ErrorNotice(message: String, onRetry: () -> Unit, onReauth: () -> Unit) {
    if (message == "403") Notice("Reconnexion nécessaire", "Google demande d'autoriser à nouveau l'accès à Chat.", "Se reconnecter", onReauth)
    else Notice("Chat indisponible", message, "Réessayer", onRetry)
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
