package com.cyprienbrisset.fukkatsunop.ui.screensaver

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.cyprienbrisset.fukkatsunop.ui.theme.MyPortalTheme

class SumiSaverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setShowWhenLocked(true)

        val videoResId = resources.getIdentifier("sumi_saver", "raw", packageName)

        setContent {
            MyPortalTheme(darkTheme = true) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { finish() },
                ) {
                    if (videoResId != 0) {
                        // TextureView s'intègre dans la hiérarchie Compose (contrairement à
                        // VideoView/SurfaceView qui se rend derrière sur une couche séparée).
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).also { tv ->
                                    tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        private var mp: MediaPlayer? = null

                                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                            mp = MediaPlayer().apply {
                                                setSurface(Surface(st))
                                                val afd = ctx.resources.openRawResourceFd(videoResId)
                                                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                                afd.close()
                                                isLooping = true
                                                setVolume(0f, 0f)
                                                prepare()
                                                start()
                                            }
                                        }

                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                            mp?.release(); mp = null
                                            return true
                                        }

                                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        SumiSaverCanvas(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
