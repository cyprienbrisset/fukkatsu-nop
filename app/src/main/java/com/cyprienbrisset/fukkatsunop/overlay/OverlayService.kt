package com.cyprienbrisset.fukkatsunop.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cyprienbrisset.fukkatsunop.R

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var overlayParams: WindowManager.LayoutParams
    private val owner = ComposeLifecycleOwner()

    companion object {
        const val ACTION_STOP = "com.cyprienbrisset.fukkatsunop.STOP_OVERLAY"
        var isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        owner.init()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startForeground(42, buildNotification())
        owner.start()
        setupOverlay()
        return START_STICKY
    }

    private fun setupOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }

        overlayView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                OverlayPanel(
                    windowManager = windowManager,
                    overlayView = { overlayView },
                    overlayParams = overlayParams,
                    onStop = { stopSelf() },
                )
            }
        }
        windowManager.addView(overlayView, overlayParams)
    }

    private fun buildNotification(): Notification {
        val ch = "overlay"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(ch) == null) {
            nm.createNotificationChannel(NotificationChannel(ch, "Overlay", NotificationManager.IMPORTANCE_MIN))
        }
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, ch)
            .setContentTitle("Contrôles Fukkatsu No P actifs")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(0, "Arrêter", stopPi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        owner.destroy()
        if (::overlayView.isInitialized) runCatching { windowManager.removeView(overlayView) }
        isRunning = false
        super.onDestroy()
    }

    // ── Lifecycle owner for the ComposeView ─────────────────────────────────
    class ComposeLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val _lifecycle = LifecycleRegistry(this)
        private val _savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = _lifecycle
        override val savedStateRegistry: SavedStateRegistry get() = _savedState.savedStateRegistry

        fun init() { _savedState.performRestore(null); _lifecycle.currentState = Lifecycle.State.CREATED }
        fun start() { _lifecycle.currentState = Lifecycle.State.STARTED }
        fun destroy() { _lifecycle.currentState = Lifecycle.State.DESTROYED }
    }
}
