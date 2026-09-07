package com.cyprienbrisset.fukkatsunop.ui.home

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.integration.RecentContact
import androidx.compose.material3.MaterialTheme
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu

@Composable
fun RecentContactsStrip(contacts: List<RecentContact>, modifier: Modifier = Modifier) {
    if (contacts.isEmpty()) {
        Box(modifier.height(0.dp))
        return
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(contacts, key = { it.key }) { contact ->
            ContactBubble(contact)
        }
    }
}

@Composable
private fun ContactBubble(contact: RecentContact) {
    Column(
        modifier = Modifier.width(60.dp).clickable {
            contact.tapIntent?.let {
                try { it.send() } catch (_: PendingIntent.CanceledException) {}
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AvatarCircle(name = contact.name, avatar = contact.avatar)
        Text(
            contact.name,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AvatarCircle(name: String, avatar: Bitmap?) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier.size(52.dp).clip(CircleShape),
            )
        } else {
            Text(
                name.take(1).uppercase(),
                color = Shu,
                fontFamily = Mincho,
                fontSize = 22.sp,
            )
        }
    }
}
