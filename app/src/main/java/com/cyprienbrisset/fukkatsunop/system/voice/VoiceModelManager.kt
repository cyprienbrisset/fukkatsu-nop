package com.cyprienbrisset.fukkatsunop.system.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

object VoiceModelManager {
    private const val MODEL_NAME = "vosk-model-small-fr-0.22"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()

    fun modelDir(ctx: Context): File = File(ctx.filesDir, MODEL_NAME)

    fun isModelReady(ctx: Context): Boolean {
        val dir = modelDir(ctx)
        return dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    fun resetDownloadProgress() { _downloadProgress.value = null }
    fun setDownloadProgressForTest(value: Int?) { _downloadProgress.value = value }

    suspend fun downloadAndExtract(ctx: Context): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                _downloadProgress.value = 0
                val zipFile = File(ctx.cacheDir, "$MODEL_NAME.zip")
                val conn = URL(MODEL_URL).openConnection()
                val total = conn.contentLength.toLong()
                var downloaded = 0L
                conn.getInputStream().use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) {
                                _downloadProgress.value = ((downloaded * 100) / total).toInt()
                            }
                        }
                    }
                }
                _downloadProgress.value = 100
                val dest = modelDir(ctx)
                dest.mkdirs()
                ZipInputStream(zipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryPath = entry.name.substringAfter("$MODEL_NAME/")
                        if (entryPath.isNotEmpty()) {
                            val outFile = File(dest, entryPath)
                            if (entry.isDirectory) outFile.mkdirs()
                            else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { zis.copyTo(it) }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
                zipFile.delete()
                _downloadProgress.value = null
                true
            }.getOrElse { _downloadProgress.value = -1; false }
        }
}
