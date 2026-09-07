package com.cyprienbrisset.fukkatsunop.system

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cyprienbrisset.fukkatsunop.integration.NotificationBadgeRepository
import com.cyprienbrisset.fukkatsunop.integration.RecentContact
import com.cyprienbrisset.fukkatsunop.integration.RecentContactsRepository

class MediaListenerService : NotificationListenerService() {

    companion object {
        private val COMM_PACKAGES = setOf(
            "com.facebook.aloha.app.whatsapp",
            "com.facebook.aloha.app.messenger",
        )
    }

    override fun onListenerConnected() {
        runCatching {
            val active = getActiveNotifications() ?: return
            active.groupBy { it.packageName }
                .forEach { (pkg, notifs) -> NotificationBadgeRepository.setCount(pkg, notifs.size) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationBadgeRepository.onPosted(sbn.packageName)
        runCatching { handleContactNotification(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationBadgeRepository.onRemoved(sbn.packageName)
    }

    private fun handleContactNotification(sbn: StatusBarNotification) {
        if (sbn.packageName !in COMM_PACKAGES) return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val name = extras.getString(Notification.EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val key = "${sbn.packageName}:$name"

        val avatar: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notif.getLargeIcon()?.loadDrawable(this)?.let { (it as? BitmapDrawable)?.bitmap }
        } else {
            @Suppress("DEPRECATION")
            notif.largeIcon as? Bitmap
        }

        val tapIntent = notif.actions
            ?.firstOrNull { a ->
                a.title?.toString()?.contains("appel", ignoreCase = true) == true
                    || a.title?.toString()?.contains("call", ignoreCase = true) == true
            }
            ?.actionIntent
            ?: notif.contentIntent

        RecentContactsRepository.onNotification(
            RecentContact(
                key = key,
                name = name,
                avatar = avatar,
                packageName = sbn.packageName,
                lastSeenMs = sbn.postTime,
                tapIntent = tapIntent,
            )
        )
    }
}
