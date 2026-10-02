package com.miku.ray.handler

import android.content.Context
import android.os.Build
import com.miku.ray.AppConfig
import com.miku.ray.BuildConfig
import com.miku.ray.dto.CheckUpdateResult
import com.miku.ray.dto.GitHubRelease
import com.miku.ray.dto.ReleaseVersionFile
import com.miku.ray.dto.UrlContentRequest
import com.miku.ray.extension.concatUrl
import com.miku.ray.util.HttpUtil
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {

    /** The release's version.json asset, written by the release workflow. */
    private const val VERSION_ASSET = "version.json"

    suspend fun checkForUpdate(context: Context, includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        val url = if (includePreRelease) {
            AppConfig.APP_API_URL
        } else {
            AppConfig.APP_API_URL.concatUrl("latest")
        }

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()

        var response = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000
            )
        )
        if (response.isNullOrEmpty()) {
            val httpPort = SettingsManager.getHttpPort()
            response = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000,
                    httpPort = httpPort,
                    proxyUsername = proxyUsername,
                    proxyPassword = proxyPassword
                )
            )
            ?: throw IllegalStateException("Failed to get response")
        }

        val latestRelease = if (includePreRelease) {
            JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)
            ?.firstOrNull()
            ?: throw IllegalStateException("No pre-release found")
        } else {
            JsonUtil.fromJsonSafe(response, GitHubRelease::class.java)
        }
        if (latestRelease == null) {
            return@withContext CheckUpdateResult(hasUpdate = false)
        }

        val latestVersion = latestRelease.tagName.removePrefix("v")
        val (installedVersion, installedCode) = installedPackageVersion(context)
        LogUtil.i(
            AppConfig.TAG,
            "Found release: $latestVersion (current: $installedVersion/$installedCode)"
        )

        // Updates are decided by the release's version code (version.json),
        // not by parsing version names; the name compare is only the fallback
        // for a release published without the asset.
        val latestCode = latestRelease.assets.firstOrNull { it.name == VERSION_ASSET }
            ?.let { asset -> fetchAsset(asset.browserDownloadUrl, proxyUsername, proxyPassword) }
            ?.let { JsonUtil.fromJsonSafe(it, ReleaseVersionFile::class.java)?.versionCode }
        val hasUpdate = when (latestCode) {
            null -> compareVersions(latestVersion, installedVersion) > 0
            else -> latestCode > installedCode
        }
        if (!hasUpdate) {
            return@withContext CheckUpdateResult(hasUpdate = false)
        }

        return@withContext CheckUpdateResult(
            hasUpdate = true,
            latestVersion = latestVersion,
            releaseNotes = latestRelease.body,
            releaseUrl = latestRelease.htmlUrl.ifBlank {
                AppConfig.APP_API_URL.concatUrl("latest")
            },
            downloadUrl = runCatching { getDownloadUrl(latestRelease, Build.SUPPORTED_ABIS[0]) }.getOrNull(),
            isPreRelease = latestRelease.prerelease
        )
    }

    /** The installed app's own version, straight from PackageManager: it is the
     *  authority regardless of what any compiled-in constant says. */
    private fun installedPackageVersion(context: Context): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()
        return (info.versionName ?: "") to code
    }

    private fun fetchAsset(url: String, proxyUsername: String?, proxyPassword: String?): String? {
        HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = 5000))?.let { return it }
        return HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000,
                httpPort = SettingsManager.getHttpPort(),
                proxyUsername = proxyUsername,
                proxyPassword = proxyPassword
            )
        )
    }

    /** Fallback for releases without version.json: compare the digit runs of
     *  the tag and the installed version name ("v1.2.3" vs "1.2.3" works). */
    private fun compareVersions(version1: String, version2: String): Int {
        val runs = Regex("\\d+")
        fun digits(version: String) = runs.findAll(version).map { it.value.toLong() }.toList()
        val v1 = digits(version1)
        val v2 = digits(version2)
        for (i in 0 until maxOf(v1.size, v2.size)) {
            val a = v1.getOrElse(i) { 0L }
            val b = v2.getOrElse(i) { 0L }
            if (a != b) return if (a < b) -1 else 1
        }
        return 0
    }

    private fun getDownloadUrl(release: GitHubRelease, abi: String): String {
        val fDroid = "fdroid"

        val assetsByAbi = release.assets.filter {
            (it.name.contains(abi, true))
        }

        val asset = if (BuildConfig.APPLICATION_ID.contains(fDroid, ignoreCase = true)) {
            assetsByAbi.firstOrNull { it.name.contains(fDroid) }
        } else {
            assetsByAbi.firstOrNull { !it.name.contains(fDroid) }
        }

        return asset?.browserDownloadUrl
        ?: throw IllegalStateException("No compatible APK found")
    }
}
