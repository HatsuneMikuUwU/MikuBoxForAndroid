package com.miku.ray.util

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import java.io.File

object SoundPlayer {

    private var player: MediaPlayer? = null

    fun playConnect(context: Context) {
        playSound(context, AppConfig.PREF_CUSTOM_CONNECT_SOUND_URI, R.raw.connect_sound)
    }

    fun playDisconnect(context: Context) {
        playSound(context, AppConfig.PREF_CUSTOM_DISCONNECT_SOUND_URI, R.raw.disconnect_sound)
    }

    private fun playSound(context: Context, preferenceKey: String, fallbackResId: Int) {
        release()
        val customUri = MmkvManager.decodeSettingsString(preferenceKey).orEmpty()
        player = if (customUri.isNotBlank()) {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(context, Uri.parse(customUri))
                    setOnCompletionListener { release() }
                    setOnErrorListener { _, _, _ -> release(); true }
                    prepare()
                }
            }.getOrNull()
        } else {
            MediaPlayer.create(context, fallbackResId)?.apply {
                setOnCompletionListener { release() }
                setOnErrorListener { _, _, _ -> release(); true }
            }
        }
        if (player == null) {
            player = MediaPlayer.create(context, fallbackResId)?.apply {
                setOnCompletionListener { release() }
            }
        }
        player?.start()
    }

    private fun release() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }
}
