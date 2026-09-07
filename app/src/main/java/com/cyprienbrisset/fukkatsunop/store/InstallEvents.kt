package com.cyprienbrisset.fukkatsunop.store

import kotlinx.coroutines.flow.MutableSharedFlow

/** Result of a PackageInstaller session, reported back to the UI. */
data class InstallEvent(val packageName: String?, val success: Boolean)

/**
 * Process-wide bus so the manifest [InstallResultReceiver] (a separate instance from any
 * ViewModel) can report install outcomes back to the store UI. Without this the install card
 * stays stuck on "Installation…" after the system finishes installing.
 */
object InstallEvents {
    val events = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 16)
    fun emit(event: InstallEvent) { events.tryEmit(event) }
}
