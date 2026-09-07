package com.cyprienbrisset.fukkatsunop.ui.airplay

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
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
        val frame = FrameLayout(this)
        val surfaceView = SurfaceView(this)
        frame.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            visibility = View.VISIBLE
            setOnClickListener { vm.disconnect() }
        }
        val hudParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            topMargin = 24; rightMargin = 24
        }
        frame.addView(closeBtn, hudParams)
        setContentView(frame)

        lifecycleScope.launch {
            delay(3000)
            closeBtn.animate().alpha(0f).setDuration(500).start()
        }
        surfaceView.setOnClickListener {
            closeBtn.animate().cancel()
            closeBtn.alpha = 1f
            closeBtn.visibility = View.VISIBLE
            lifecycleScope.launch {
                delay(3000)
                closeBtn.animate().alpha(0f).setDuration(500).start()
            }
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                AirPlayReceiver.attachSurface(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                AirPlayReceiver.detachSurface()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    if (state is AirPlayState.Waiting || state is AirPlayState.Error) {
                        finish()
                    }
                }
            }
        }
    }
}
