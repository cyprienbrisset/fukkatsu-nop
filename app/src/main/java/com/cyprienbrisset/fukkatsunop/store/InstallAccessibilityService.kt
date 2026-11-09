package com.cyprienbrisset.fukkatsunop.store

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Auto-clicks the "Install" button in PackageInstallerActivity on Portal 1st gen (Android 9).
 *
 * On Portal 1st gen, PackageInstallerActivity renders correctly (buttons are present and
 * clickable) but the Portal's custom display system overlays it with a blank white screen.
 * Touch events still pass through the white overlay to the underlying window, so an
 * AccessibilityService can programmatically click the Install button to complete the install.
 *
 * This service only acts on com.android.packageinstaller (scoped in accessibility_service.xml).
 * It fires only when our app triggers an install via ApkInstaller, since the install intent
 * check happens in InstallTrampolineActivity which already has user confirmation from FukkaStore.
 */
class InstallAccessibilityService : AccessibilityService() {

    @Suppress("DEPRECATION")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName != "com.android.packageinstaller") return
        pressButtons()
    }

    /**
     * When Fukkatsu updates itself, this service dies with the old version and comes back
     * on the (blank) "installed" screen, whose events are already gone: look at it now.
     */
    override fun onServiceConnected() {
        pressButtons()
    }

    @Suppress("DEPRECATION")
    private fun pressButtons() {
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName != "com.android.packageinstaller") return
            // ok_button: "Installer" / "Suivant"; done_button: "OK" on the "installed" screen.
            clickButton(root, "com.android.packageinstaller:id/ok_button") ||
                clickButton(root, "com.android.packageinstaller:id/done_button")
        } finally {
            root.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun clickButton(root: AccessibilityNodeInfo, id: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(id) ?: return false
        val button = nodes.firstOrNull() ?: return false
        try {
            return button.isEnabled && button.isClickable && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            nodes.forEach { it.recycle() }
        }
    }

    override fun onInterrupt() = Unit
}
