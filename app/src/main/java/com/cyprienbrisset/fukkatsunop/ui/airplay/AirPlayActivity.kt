package com.cyprienbrisset.fukkatsunop.ui.airplay

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayReceiver
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AirPlayActivity : ComponentActivity() {
    private val vm: AirPlayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        val isExtended = intent.getBooleanExtra(EXTRA_EXTENDED, false)

        val frame = FrameLayout(this)
        val surfaceView = SurfaceView(this)
        frame.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))

        if (isExtended) {
            // Subtle pill indicator — fades out after 4 s, reappears on tap
            val badge = TextView(this).apply {
                text = "Écran étendu Mac"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(24, 10, 24, 10)
                setBackgroundColor(Color.argb(140, 0, 0, 0))
                alpha = 0.85f
            }
            val badgeParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.TOP or Gravity.END; topMargin = 16; rightMargin = 16 }
            frame.addView(badge, badgeParams)
            lifecycleScope.launch {
                delay(4_000)
                badge.animate().alpha(0f).setDuration(800).start()
            }
            surfaceView.setOnClickListener {
                badge.animate().cancel(); badge.alpha = 0.85f
                lifecycleScope.launch {
                    delay(3_000)
                    badge.animate().alpha(0f).setDuration(800).start()
                }
            }
        } else {
            // Mirror mode: close button
            val closeBtn = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = null
                setOnClickListener { vm.disconnect() }
            }
            val closeBtnParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.TOP or Gravity.END; topMargin = 24; rightMargin = 24 }
            frame.addView(closeBtn, closeBtnParams)
            lifecycleScope.launch {
                delay(3_000)
                closeBtn.animate().alpha(0f).setDuration(500).start()
            }
            surfaceView.setOnClickListener {
                closeBtn.animate().cancel(); closeBtn.alpha = 1f; closeBtn.visibility = View.VISIBLE
                lifecycleScope.launch {
                    delay(3_000)
                    closeBtn.animate().alpha(0f).setDuration(500).start()
                }
            }
        }

        setContentView(frame)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { AirPlayReceiver.attachSurface(holder.surface) }
            override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) { AirPlayReceiver.detachSurface() }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    if (state is AirPlayState.Waiting || state is AirPlayState.Error) finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Re-attach surface when re-launched by AirPlayService (singleTop)
    }

    companion object {
        const val EXTRA_EXTENDED = "is_extended"
    }
}
