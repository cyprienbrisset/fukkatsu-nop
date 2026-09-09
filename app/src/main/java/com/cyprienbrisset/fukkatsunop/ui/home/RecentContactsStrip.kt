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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            AvatarCircle(
                name = contact.name,
                avatar = contact.avatar,
                onClick = { contact.tapIntent?.let { runCatching { it.send() } } },
            )
            if (contact.callIntent != null) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .clip(CircleShape)
                        .background(Shu)
                        .clickable { runCatching { contact.callIntent.send() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = "Appel vidéo",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
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
private fun AvatarCircle(name: String, avatar: Bitmap?, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() },
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
