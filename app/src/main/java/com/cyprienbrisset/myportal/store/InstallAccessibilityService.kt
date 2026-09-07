package com.cyprienbrisset.myportal.store

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

        val root = rootInActiveWindow ?: return
        try {
            clickInstallButton(root)
        } finally {
            root.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun clickInstallButton(root: AccessibilityNodeInfo) {
        val nodes = root.findAccessibilityNodeInfosByViewId(
            "com.android.packageinstaller:id/ok_button"
        )
        val button = nodes?.firstOrNull() ?: return
        try {
            if (button.isEnabled && button.isClickable) {
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } finally {
            nodes.forEach { it.recycle() }
        }
    }

    override fun onInterrupt() = Unit
}
