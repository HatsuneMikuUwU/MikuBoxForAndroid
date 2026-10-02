package com.miku.ray.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.text.Editable
import android.util.Base64
import android.util.Patterns
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.LOOPBACK
import com.miku.ray.BuildConfig
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object Utils {

    private val IPV4_REGEX =
    Regex("^([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])$")
    private val IPV6_REGEX = Regex("^((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$")

    fun getEditable(text: String?): Editable {
        return Editable.Factory.getInstance().newEditable(text.orEmpty())
    }

    fun arrayFind(array: Array<out String>, value: String): Int {
        return array.indexOf(value)
    }

    fun parseInt(str: String?, default: Int = 0): Int {
        return str?.toIntOrNull() ?: default
    }

    fun getClipboard(context: Context): String {
        return try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cmb.primaryClip?.getItemAt(0)?.text.toString()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to get clipboard content", e)
            ""
        }
    }

    fun setClipboard(context: Context, content: String) {
        try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(null, content)
            cmb.setPrimaryClip(clipData)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to set clipboard content", e)
        }
    }

    fun decode(text: String?): String {
        val value = text?.trim().orEmpty()

        if (value.startsWith("http://", ignoreCase = true)
            || value.startsWith("https://", ignoreCase = true)
            || value.contains("://")
        ) {
            return value
        }
        return tryDecodeBase64(value) ?: tryDecodeBase64(value.trimEnd('=')).orEmpty()
    }

    fun tryDecodeBase64(text: String?): String? {
        if (text.isNullOrEmpty()) return null

        try {
            return Base64.decode(text, Base64.NO_WRAP).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to decode standard base64", e)
        }
        try {
            return Base64.decode(text, Base64.NO_WRAP.or(Base64.URL_SAFE)).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to decode URL-safe base64", e)
        }
        return null
    }

    fun encode(text: String, removePadding: Boolean = false): String {
        return try {
            var encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            if (removePadding) {
                encoded = encoded.trimEnd('=')
            }
            encoded
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to encode text to base64", e)
            ""
        }
    }

    fun isIpAddress(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false

        try {
            var addr = value.trim()
            if (addr.isEmpty()) return false

            if (addr.contains("/")) {
                val arr = addr.split("/")
                if (arr.size == 2 && arr[1].toIntOrNull() != null && arr[1].toInt() > -1) {
                    addr = arr[0]
                }
            }

            if (addr.startsWith("::ffff:") && '.' in addr) {
                addr = addr.drop(7)
            } else if (addr.startsWith("[::ffff:") && '.' in addr) {
                addr = addr.drop(8).replace("]", "")
            }

            val octets = addr.split('.')
            if (octets.size == 4) {
                if (octets[3].contains(":")) {
                    addr = addr.substring(0, addr.indexOf(":"))
                }
                return isIpv4Address(addr)
            }

            return isIpv6Address(addr)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to validate IP address", e)
            return false
        }
    }

    fun isPureIpAddress(value: String): Boolean {
        return isIpv4Address(value) || isIpv6Address(value)
    }

    fun isDomainName(input: String?): Boolean {
        if (input.isNullOrEmpty()) return false

        return !isPureIpAddress(input) && isValidUrl(input)
    }

    private fun isIpv4Address(value: String): Boolean {
        return IPV4_REGEX.matches(value)
    }

    private fun isIpv6Address(value: String): Boolean {
        var addr = value
        if (addr.startsWith("[")) {
            val closingBracket = addr.lastIndexOf(']')
            if (closingBracket <= 1) return false
            addr = addr.substring(1, closingBracket)
        }
        return IPV6_REGEX.matches(addr)
    }

    fun isCoreDNSAddress(s: String): Boolean {
        return s.startsWith("https") ||
        s.startsWith("tcp") ||
        s.startsWith("quic") ||
        s == "localhost"
    }

    fun isValidUrl(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false

        return try {
            Patterns.WEB_URL.matcher(value).matches() ||
            Patterns.DOMAIN_NAME.matcher(value).matches() ||
            URLUtil.isValidUrl(value)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to validate URL", e)
            false
        }
    }

    fun openUri(context: Context, uriString: String) {
        try {
            val uri = uriString.toUri()
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to open URI", e)
        }
    }

    fun getUuid(): String {
        return try {
            UUID.randomUUID().toString().replace("-", "")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to generate UUID", e)
            ""
        }
    }

    fun urlDecode(url: String): String {
        return try {
            URLDecoder.decode(url, Charsets.UTF_8.toString())
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to decode URL", e)
            url
        }
    }

    fun urlEncode(url: String): String {
        return try {
            URLEncoder.encode(url, Charsets.UTF_8.toString())
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to encode URL", e)
            url
        }
    }

    fun decodeURIComponent(url: String): String {
        return try {
            URLDecoder.decode(url.replace("+", "%2B"), Charsets.UTF_8.toString())
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to decode encodeURIComponent", e)
            url
        }
    }

    fun encodeURIComponent(url: String): String {
        return try {
            URLEncoder.encode(url, Charsets.UTF_8.toString()).replace("+", "%20")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to encode encodeURIComponent", e)
            url
        }
    }

    fun readTextFromAssets(context: Context?, fileName: String): String {
        if (context == null) return ""

        return try {
            context.assets.open(fileName).use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read asset file: $fileName", e)
            ""
        }
    }

    fun userAssetPath(context: Context?): String {
        if (context == null) return ""

        return try {
            context.getDir(AppConfig.DIR_ASSETS, Context.MODE_PRIVATE).absolutePath
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to get user asset path", e)
            ""
        }
    }

    fun legacyExternalAssetPath(context: Context?): String {
        if (context == null) return ""

        return try {
            val externalFilesDir = context.getExternalFilesDir(null) ?: return ""
            File(externalFilesDir, "assets").absolutePath
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to get legacy external asset path", e)
            ""
        }
    }

    fun getDeviceIdForXUDPBaseKey(): String {
        return try {
            val androidId = Settings.Secure.ANDROID_ID.toByteArray(Charsets.UTF_8)
            Base64.encodeToString(androidId.copyOf(32), Base64.NO_PADDING.or(Base64.URL_SAFE))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to generate device ID", e)
            ""
        }
    }

    fun getDarkModeStatus(context: Context): Boolean {
        return context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK != UI_MODE_NIGHT_NO
    }

    fun getIpv6Address(address: String?): String {
        if (address.isNullOrEmpty()) return ""

        return if (isIpv6Address(address) && !address.contains('[') && !address.contains(']')) {
            "[$address]"
        } else {
            address
        }
    }

    fun getSysLocale(): Locale = LocaleList.getDefault().get(0) ?: Locale.getDefault()

    fun fixIllegalUrl(str: String): String {
        return str.replace(" ", "%20")
        .replace("|", "%7C")
    }

    fun findFreePort(ports: List<Int>): Int {
        for (port in ports) {
            try {
                return ServerSocket(port).use { it.localPort }
            } catch (ex: IOException) {
                continue
            }
        }

        throw IOException("no free port found")
    }

    fun findRandomFreePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    fun isValidSubUrl(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false

        try {
            if (URLUtil.isHttpsUrl(value)) return true
            if (URLUtil.isHttpUrl(value)) {
                if (value.contains(LOOPBACK)) return true

                val uri = URI(fixIllegalUrl(value))
                if (isIpAddress(uri.host)) {
                    AppConfig.PRIVATE_IP_LIST.forEach {
                        if (isIpInCidr(uri.host, it)) return true
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to validate subscription URL", e)
        }
        return false
    }

    /**
     * Flags for receivers that genuinely must be reachable from other apps.
     * Same-app channels must register with [ContextCompat.RECEIVER_NOT_EXPORTED]
     * instead: on API 33+ this helper returns RECEIVER_EXPORTED, which exposes
     * the receiver to every installed application.
     */
    fun receiverFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.RECEIVER_EXPORTED
    } else {
        ContextCompat.RECEIVER_NOT_EXPORTED
    }

    fun isXray(): Boolean = BuildConfig.APPLICATION_ID.startsWith("com.miku.ray")

    fun isGoogleFlavor(): Boolean = true

    fun isIpInCidr(ip: String, cidr: String): Boolean {
        val parts = cidr.split('/')
        if (parts.size != 2 || !isIpv4Address(ip) || !isIpv4Address(parts[0])) return false
        val prefixLength = parts[1].toIntOrNull()?.takeIf { it in 0..32 } ?: return false
        val mask = if (prefixLength == 0) 0L else (-1L shl (32 - prefixLength))
        return (ipv4ToLong(ip) and mask) == (ipv4ToLong(parts[0]) and mask)
    }

    private fun ipv4ToLong(ip: String): Long {
        return ip.split('.').fold(0L) { result, octet -> (result shl 8) or octet.toLong() }
    }

    fun formatTimestamp(ts: Long?, pattern: String = "yyyy-MM-dd HH:mm", locale: Locale = Locale.getDefault()): String {
        if (ts == null || ts <= 0L) return ""
        return try {
            val sdf = SimpleDateFormat(pattern, locale)
            sdf.format(Date(ts))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to format timestamp", e)
            ""
        }
    }

    fun countryCodeToFlag(countryCode: String?): String {
        if (countryCode.isNullOrBlank() || countryCode.length < 2) return ""
        val upper = countryCode.uppercase(Locale.ROOT)
        if (!upper.all { it in 'A'..'Z' }) return ""
        val firstChar = Character.toChars(upper[0].code - 'A'.code + 0x1F1E6)
        val secondChar = Character.toChars(upper[1].code - 'A'.code + 0x1F1E6)
        return String(firstChar) + String(secondChar)
    }
}
