package com.miku.ray.handler

import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.WebDavConfig
import com.miku.ray.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

object WebDavManager {
    private var cfg: WebDavConfig? = null
    private var client: OkHttpClient? = null

    fun init(config: WebDavConfig) {
        cfg = config
        client = OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .build()
    }

    suspend fun uploadFile(localFile: File, remoteFileName: String): Boolean = withContext(Dispatchers.IO) {
        val remote = buildRemoteUrl(remoteFileName)
        try {
            val cl = client ?: return@withContext false

            val dirPath = remote.substringBeforeLast('/')
            if (dirPath != remote) {
                ensureRemoteDirs(dirPath)
            }

            val mediaType = when (localFile.extension.lowercase()) {
                "zip" -> "application/zip"
                "json" -> "application/json"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }.toMediaTypeOrNull()

            val body = localFile.asRequestBody(mediaType)
            val req = applyAuth(Request.Builder().url(remote).put(body)).build()
            cl.newCall(req).execute().use { resp ->
                val success = resp.isSuccessful
                if (success) {
                    LogUtil.i(AppConfig.TAG, "WebDAV upload success: $remote")
                } else {
                    LogUtil.e(AppConfig.TAG, "WebDAV upload failed: $remote (HTTP ${resp.code})")
                }
                return@withContext success
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WebDAV upload exception: $remote", e)
            return@withContext false
        }
    }

    suspend fun downloadFile(remoteFileName: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        val remote = buildRemoteUrl(remoteFileName)
        try {
            val cl = client ?: return@withContext false
            val req = applyAuth(Request.Builder().url(remote).get()).build()
            cl.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    LogUtil.e(AppConfig.TAG, "WebDAV download failed: $remote (HTTP ${resp.code})")
                    return@withContext false
                }

                val stream = resp.body?.byteStream() ?: return@withContext false
                stream.use { input ->
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { fos ->
                        input.copyTo(fos)
                    }
                }

                LogUtil.i(AppConfig.TAG, "WebDAV download success: $remote")
                return@withContext true
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WebDAV download exception: $remote", e)
            return@withContext false
        }
    }

    private fun buildRemoteUrl(remoteFileName: String): String {
        val base = cfg?.baseUrl?.trimEnd('/') ?: ""
        val basePathConfigured = cfg?.remoteBasePath?.trim('/')?.takeIf { it.isNotEmpty() }
        val basePath = basePathConfigured ?: AppConfig.WEBDAV_BACKUP_DIR
        val rel = remoteFileName.trimStart('/')
        return if (basePath.isEmpty()) "$base/$rel" else "$base/$basePath/$rel"
    }

    private fun applyAuth(builder: Request.Builder): Request.Builder {
        val username = cfg?.username
        val password = cfg?.password
        if (!username.isNullOrEmpty()) {
            builder.header("Authorization", Credentials.basic(username, password ?: ""))
        }
        return builder
    }

    private fun ensureRemoteDirs(dirUrl: String) {
        try {
            val cl = client ?: return
            val url = URL(dirUrl)
            val segments = url.path.split("/").filter { it.isNotEmpty() }
            var accum = ""
            for (seg in segments) {
                accum += "/$seg"
                val mkUrl = URL(url.protocol, url.host, if (url.port == -1) -1 else url.port, accum).toString()
                try {
                    val req = applyAuth(Request.Builder().url(mkUrl).method("MKCOL", null)).build()
                    cl.newCall(req).execute().use { resp ->
                        if (resp.code != 201 && resp.code != 405 && resp.code != 409) {
                            LogUtil.w(AppConfig.TAG, "WebDAV MKCOL $mkUrl returned ${resp.code}")
                        }
                    }
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WebDAV ensureRemoteDirs error", e)
        }
    }
}
