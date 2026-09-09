package com.cyprienbrisset.fukkatsunop.integration

import android.app.PendingIntent
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RecentContact(
    val key: String,
    val name: String,
    val avatar: Bitmap?,
    val packageName: String,
    val lastSeenMs: Long,
    val tapIntent: PendingIntent?,
    val callIntent: PendingIntent? = null,
)

object RecentContactsRepository {

    private val _contacts = MutableStateFlow<List<RecentContact>>(emptyList())
    val contacts: StateFlow<List<RecentContact>> = _contacts

    private const val MAX = 6

    fun onNotification(contact: RecentContact) {
        val current = _contacts.value.toMutableList()
        current.removeAll { it.key == contact.key }
        current.add(0, contact)
        _contacts.value = current.take(MAX)
    }
}
