package com.cyprienbrisset.fukkatsunop.system.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

class VoiceService : Service() {

    enum class ListenState { IDLE, WAKE, COMMAND }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenJob: Job? = null
    private var model: Model? = null

    companion object {
        private val _state = MutableStateFlow(ListenState.IDLE)
        val state: StateFlow<ListenState> = _state.asStateFlow()

        private const val CHANNEL_ID = "voice_service"
        private const val NOTIF_ID = 9002

        fun start(ctx: Context) {
            val prefs = ctx.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("voice_enabled", false)) return
            if (!VoiceModelManager.isModelReady(ctx)) return
            ctx.startForegroundService(Intent(ctx, VoiceService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, VoiceService::class.java))
        }

        fun isEnabled(ctx: Context): Boolean =
            ctx.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE)
                .getBoolean("voice_enabled", false)

        fun setEnabled(ctx: Context, enabled: Boolean) {
            ctx.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("voice_enabled", enabled).apply()
            if (enabled) start(ctx) else stop(ctx)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification("En écoute — dites « Portal »"))
        scope.launch {
            runCatching { model = Model(VoiceModelManager.modelDir(this@VoiceService).absolutePath) }
            if (model != null) startListening()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        runCatching { model?.close() }
        _state.value = ListenState.IDLE
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening() {
        listenJob = scope.launch {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ) * 2
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize,
            )
            val commandGrammar = VoiceCommandDispatcher.buildCommandGrammar(this@VoiceService)
            val wakeRecognizer = Recognizer(model, sampleRate.toFloat(), """["portal", "[unk]"]""")
            val cmdRecognizer = Recognizer(model, sampleRate.toFloat(), commandGrammar)
            val buf = ShortArray(bufferSize / 2)
            recorder.startRecording()
            _state.value = ListenState.WAKE
            var commandMode = false
            var commandDeadline = 0L
            try {
                while (true) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    val bytes = ByteArray(n * 2)
                    for (i in 0 until n) {
                        bytes[i * 2] = (buf[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = (buf[i].toInt() shr 8).toByte()
                    }
                    if (commandMode) {
                        if (System.currentTimeMillis() > commandDeadline) {
                            commandMode = false
                            _state.value = ListenState.WAKE
                            updateNotification("En écoute — dites « Portal »")
                        } else if (cmdRecognizer.acceptWaveForm(bytes, bytes.size)) {
                            val result = cmdRecognizer.result
                            val text = JSONObject(result).optString("text", "")
                            if (text.isNotBlank() && text != "[unk]") {
                                VoiceCommandDispatcher.dispatch(this@VoiceService, text)
                            }
                            commandMode = false
                            cmdRecognizer.reset()
                            _state.value = ListenState.WAKE
                            updateNotification("En écoute — dites « Portal »")
                        }
                    } else {
                        if (wakeRecognizer.acceptWaveForm(bytes, bytes.size)) {
                            val result = wakeRecognizer.result
                            val text = JSONObject(result).optString("text", "")
                            if ("portal" in text) {
                                commandMode = true
                                commandDeadline = System.currentTimeMillis() + 5_000L
                                wakeRecognizer.reset()
                                _state.value = ListenState.COMMAND
                                updateNotification("Parlez…")
                            }
                        }
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
                runCatching { wakeRecognizer.close() }
                runCatching { cmdRecognizer.close() }
            }
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Commandes vocales", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Fukkatsu No P")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
