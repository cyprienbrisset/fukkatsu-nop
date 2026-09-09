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
    val shortcutId: String? = null,
)

object RecentContactsRepository {

    private val _contacts = MutableStateFlow<List<RecentContact>>(emptyList())
    val contacts: StateFlow<List<RecentContact>> = _contacts

    private const val MAX = 6

    /** Pré-remplit depuis les shortcuts Messenger/WhatsApp (appelé au démarrage). */
    fun setBaseContacts(base: List<RecentContact>) {
        val current = _contacts.value.toMutableList()
        base.forEach { sc -> if (current.none { it.key == sc.key }) current.add(sc) }
        _contacts.value = current.sortedByDescending { it.lastSeenMs }.take(MAX)
    }

    /** Met à jour / ajoute un contact à la réception d'une notification. */
    fun onNotification(contact: RecentContact) {
        val current = _contacts.value.toMutableList()
        current.removeAll { it.key == contact.key }
        current.add(0, contact)
        _contacts.value = current.take(MAX)
    }
}
