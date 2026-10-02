package com.miku.ray.ui.server.fields

import android.view.View
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.util.Utils

/**
 * Per-profile Mux (connection multiplexing) fields.
 *
 * Mux used to be a single global switch in Settings that applied to every profile and only ever
 * kicked in for vmess/vless outbounds. It now lives on [ProfileItem] itself (see
 * [ProfileItem.muxEnabled] and friends), so each server can be tuned independently, and
 * [com.miku.ray.core.CoreOutboundBuilder] applies it regardless of protocol (aside from
 * transports that genuinely can't use it).
 */
class MuxFields(view: View) {

    private val xudpQuicEntries: Array<out String> = view.resources.getStringArray(R.array.mux_xudp_quic_entries)
    private val xudpQuicValues: Array<out String> = view.resources.getStringArray(R.array.mux_xudp_quic_value)
    private val muxEnabledEntries: Array<out String> = view.resources.getStringArray(R.array.mux_enabled_entries)
    private val muxEnabledValues: Array<out String> = view.resources.getStringArray(R.array.mux_enabled_values)

    private fun muxEntryFor(enabled: Boolean): String {
        val idx = muxEnabledValues.indexOf(enabled.toString())
        return muxEnabledEntries.getOrElse(if (idx >= 0) idx else 0) { enabled.toString() }
    }

    private fun muxValueFrom(text: String?): Boolean {
        val idx = Utils.arrayFind(muxEnabledEntries, text.orEmpty())
        return muxEnabledValues.getOrElse(if (idx >= 0) idx else 0) { "false" } == "true"
    }

    private val spMuxEnabled: MaterialAutoCompleteTextView? = view.findViewById(R.id.sp_mux_enabled)
    private val etMuxConcurrency: TextInputEditText? = view.findViewById(R.id.et_mux_concurrency)
    private val etMuxXudpConcurrency: TextInputEditText? = view.findViewById(R.id.et_mux_xudp_concurrency)
    private val spMuxXudpQuic: MaterialAutoCompleteTextView? = view.findViewById(R.id.sp_mux_xudp_quic)

    private val containerMuxConcurrency: View? = view.findViewById(R.id.lay_mux_concurrency)
    private val containerMuxXudpConcurrency: View? = view.findViewById(R.id.lay_mux_xudp_concurrency)
    private val containerMuxXudpQuic: View? = view.findViewById(R.id.lay_mux_xudp_quic)

    fun setOnEnabledChanged(onChanged: (enabled: Boolean) -> Unit) {
        spMuxEnabled?.setOnItemClickListener { _, _, position, _ -> onChanged(muxEnabledValues.getOrElse(position) { "false" } == "true") }
    }

    fun updateForEnabled(enabled: Boolean) {
        listOf(containerMuxConcurrency, containerMuxXudpConcurrency, containerMuxXudpQuic)
            .forEach { it?.visibility = if (enabled) View.VISIBLE else View.GONE }
    }

    fun bind(config: ProfileItem) {
        val enabled = config.muxEnabled ?: false
        spMuxEnabled?.setText(muxEntryFor(enabled), false)
        updateForEnabled(enabled)

        etMuxConcurrency?.text = Utils.getEditable(config.muxConcurrency ?: "8")
        etMuxXudpConcurrency?.text = Utils.getEditable(config.muxXudpConcurrency ?: AppConfig.DEFAULT_MUX_XUDP_CONCURRENCY)

        val quicIndex = Utils.arrayFind(xudpQuicValues, config.muxXudpQuic ?: "reject")
        spMuxXudpQuic?.setText(xudpQuicEntries.getOrElse(if (quicIndex >= 0) quicIndex else 0) { "" }, false)
    }

    fun clear() {
        spMuxEnabled?.setText(muxEntryFor(false), false)
        updateForEnabled(false)
        etMuxConcurrency?.text = Utils.getEditable("8")
        etMuxXudpConcurrency?.text = Utils.getEditable(AppConfig.DEFAULT_MUX_XUDP_CONCURRENCY)
        spMuxXudpQuic?.setText(xudpQuicEntries.firstOrNull().orEmpty(), false)
    }

    fun save(config: ProfileItem) {
        config.muxEnabled = muxValueFrom(spMuxEnabled?.text?.toString())
        config.muxConcurrency = etMuxConcurrency?.text?.toString()?.trim().let { if (it.isNullOrEmpty()) "8" else it }
        config.muxXudpConcurrency = etMuxXudpConcurrency?.text?.toString()?.trim()
            .let { if (it.isNullOrEmpty()) AppConfig.DEFAULT_MUX_XUDP_CONCURRENCY else it }

        val quicPos = Utils.arrayFind(xudpQuicEntries, spMuxXudpQuic?.text.toString())
        config.muxXudpQuic = xudpQuicValues.getOrElse(if (quicPos >= 0) quicPos else 0) { "reject" }
    }
}
