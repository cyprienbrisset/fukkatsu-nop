package com.cyprienbrisset.fukkatsunop.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ApkDownloader(private val context: Context, private val http: OkHttpClient = OkHttpClient()) {
    suspend fun download(pkg: String, files: List<ApkFile>, onProgress: (Int) -> Unit): List<File> =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "apks/$pkg").apply { deleteRecursively(); mkdirs() }
            val knownTotal = files.sumOf { it.size }
            var done = 0L
            files.map { f ->
                val out = File(dir, if (f.name.endsWith(".apk")) f.name else "${f.name}.apk")
                http.newCall(Request.Builder().url(f.url).build()).execute().use { resp ->
                    val body = resp.body ?: throw StoreException("Téléchargement vide (${f.name})")
                    // Use Content-Length from response if size hint was unknown (0).
                    val total = if (knownTotal > 0) knownTotal
                                else (resp.header("Content-Length")?.toLongOrNull() ?: 0L).coerceAtLeast(1L)
                    out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        body.byteStream().use { ins ->
                            var last = -1
                            while (true) {
                                val n = ins.read(buf); if (n < 0) break
                                os.write(buf, 0, n); done += n
                                val pct = if (total > 1L) ((done * 100) / total).toInt().coerceIn(0, 99)
                                          else 0
                                if (pct != last) { onProgress(pct); last = pct }
                            }
                        }
                    }
                }
                out
            }
        }
}
