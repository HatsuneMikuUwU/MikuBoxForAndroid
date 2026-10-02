package com.miku.ray.ui.server.fields

import android.text.TextUtils
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import com.miku.ray.AppConfig.REALITY
import com.miku.ray.AppConfig.TLS
import com.miku.ray.R
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.util.Utils

class TlsFields(view: View) {

    private val streamSecuritys: Array<out String> = view.resources.getStringArray(R.array.streamsecurityxs)
    private val streamSecurityEntries: Array<out String> = view.resources.getStringArray(R.array.streamsecurityxs_entries)
    private val allowinsecures: Array<out String> = view.resources.getStringArray(R.array.allowinsecures)
    private val uTlsItems: Array<out String> = view.resources.getStringArray(R.array.streamsecurity_utls)
    private val alpns: Array<out String> = view.resources.getStringArray(R.array.streamsecurity_alpn)

    private val spStreamSecurity: AutoCompleteTextView? = view.findViewById(R.id.sp_stream_security)
    private val spAllowInsecure: AutoCompleteTextView? = view.findViewById(R.id.sp_allow_insecure)
    private val spStreamFingerprint: AutoCompleteTextView? = view.findViewById(R.id.sp_stream_fingerprint)
    private val spStreamAlpn: AutoCompleteTextView? = view.findViewById(R.id.sp_stream_alpn)
    private val etSni: EditText? = view.findViewById(R.id.et_sni)
    private val etPublicKey: EditText? = view.findViewById(R.id.et_public_key)
    private val etShortId: EditText? = view.findViewById(R.id.et_short_id)
    private val etSpiderX: EditText? = view.findViewById(R.id.et_spider_x)
    private val etMldsa65Verify: EditText? = view.findViewById(R.id.et_mldsa65_verify)
    private val etEchConfigList: EditText? = view.findViewById(R.id.et_ech_config_list)
    private val etVerifyPeerCertByName: EditText? = view.findViewById(R.id.et_verify_peer_cert_by_name)
    private val etPinnedCa256: EditText? = view.findViewById(R.id.et_pinned_ca256)
    private val btnPinnedCa256Action: Button? = view.findViewById(R.id.btn_pinned_ca256_action)

    private val containerAllowInsecure: View? = view.findViewById(R.id.lay_allow_insecure)
    private val containerSni: View? = view.findViewById(R.id.lay_sni)
    private val containerFingerprint: View? = view.findViewById(R.id.lay_stream_fingerprint)
    private val containerAlpn: View? = view.findViewById(R.id.lay_stream_alpn)
    private val containerPublicKey: View? = view.findViewById(R.id.lay_public_key)
    private val containerShortId: View? = view.findViewById(R.id.lay_short_id)
    private val containerSpiderX: View? = view.findViewById(R.id.lay_spider_x)
    private val containerMldsa65Verify: View? = view.findViewById(R.id.lay_mldsa65_verify)
    private val containerEchConfigList: View? = view.findViewById(R.id.lay_ech_config_list)
    private val containerVerifyPeerCertByName: View? = view.findViewById(R.id.lay_verify_peer_cert_by_name)
    private val containerPinnedCa256: View? = view.findViewById(R.id.lay_pinned_ca256)

    val selectedSecurityText: String
        get() {
            val displayText = spStreamSecurity?.text?.toString().orEmpty()
            val idx = Utils.arrayFind(streamSecurityEntries, displayText)
            return streamSecuritys.getOrElse(if (idx >= 0) idx else 0) { "" }
        }
    val pinnedCa256Text: String? get() = etPinnedCa256?.text?.toString()

    fun setPinnedCa256Text(value: String) {
        etPinnedCa256?.text = Utils.getEditable(value)
    }

    fun setFetchButtonEnabled(enabled: Boolean) {
        btnPinnedCa256Action?.isEnabled = enabled
    }

    fun setOnSecurityChanged(onChanged: (security: String) -> Unit) {
        spStreamSecurity?.setOnItemClickListener { parent, _, position, _ ->
            val displayText = parent.getItemAtPosition(position).toString()
            val idx = Utils.arrayFind(streamSecurityEntries, displayText)
            onChanged(streamSecuritys.getOrElse(if (idx >= 0) idx else 0) { "" })
        }
    }

    fun setOnFetchCertClick(onClick: () -> Unit) {
        btnPinnedCa256Action?.setOnClickListener { onClick() }
    }

    fun isSecuritySelected(): Boolean {
        val pos = Utils.arrayFind(streamSecuritys, selectedSecurityText)
        return pos >= 0 && !TextUtils.isEmpty(streamSecuritys[pos])
    }

    fun updateForSecurity(security: String) {
        val isBlank = security.isBlank()
        val isTLS = security.equals(TLS, ignoreCase = true)

        when {
            isBlank -> {
                listOf(
                    containerSni,
                    containerFingerprint,
                    containerAlpn,
                    containerAllowInsecure,
                    containerPublicKey,
                    containerShortId,
                    containerSpiderX,
                    containerMldsa65Verify,
                    containerEchConfigList,
                    containerVerifyPeerCertByName,
                    containerPinnedCa256,
                    btnPinnedCa256Action
                ).forEach { it?.visibility = View.GONE }
            }
            isTLS -> {
                listOf(
                    containerSni,
                    containerFingerprint,
                    containerAlpn,
                    containerAllowInsecure,
                    containerEchConfigList,
                    containerVerifyPeerCertByName,
                    containerPinnedCa256,
                    btnPinnedCa256Action
                ).forEach { it?.visibility = View.VISIBLE }
                listOf(
                    containerPublicKey,
                    containerShortId,
                    containerSpiderX,
                    containerMldsa65Verify
                ).forEach { it?.visibility = View.GONE }
            }
            else -> {
                listOf(
                    containerSni,
                    containerFingerprint,
                    containerPublicKey,
                    containerShortId,
                    containerSpiderX,
                    containerMldsa65Verify
                ).forEach { it?.visibility = View.VISIBLE }
                listOf(
                    containerAlpn,
                    containerAllowInsecure,
                    containerEchConfigList,
                    containerVerifyPeerCertByName,
                    containerPinnedCa256,
                    btnPinnedCa256Action
                ).forEach { it?.visibility = View.GONE }
            }
        }
    }

    fun bind(config: ProfileItem) {
        val streamSecurity = Utils.arrayFind(streamSecuritys, config.security.orEmpty())
        if (streamSecurity < 0) {
            spStreamSecurity?.setText(streamSecurityEntries.getOrElse(0) { "" }, false)
            updateForSecurity("")
            return
        }

        spStreamSecurity?.setText(streamSecurityEntries.getOrElse(streamSecurity) { streamSecuritys[streamSecurity] }, false)
        updateForSecurity(streamSecuritys[streamSecurity])

        etSni?.text = Utils.getEditable(config.sni)
        config.fingerPrint?.let {
            val utlsIndex = Utils.arrayFind(uTlsItems, it)
            if (utlsIndex >= 0) spStreamFingerprint?.setText(uTlsItems[utlsIndex], false)
        }
        config.alpn?.let {
            val alpnIndex = Utils.arrayFind(alpns, it)
            if (alpnIndex >= 0) spStreamAlpn?.setText(alpns[alpnIndex], false)
        }

        if (config.security == TLS) {
            val allowinsecure = Utils.arrayFind(allowinsecures, config.insecure.toString())
            if (allowinsecure >= 0) spAllowInsecure?.setText(allowinsecures[allowinsecure], false)
            etEchConfigList?.text = Utils.getEditable(config.echConfigList)
            etVerifyPeerCertByName?.text = Utils.getEditable(config.verifyPeerCertByName)
            etPinnedCa256?.text = Utils.getEditable(config.pinnedCA256)
        } else if (config.security == REALITY) {
            etPublicKey?.text = Utils.getEditable(config.publicKey.orEmpty())
            etShortId?.text = Utils.getEditable(config.shortId.orEmpty())
            etSpiderX?.text = Utils.getEditable(config.spiderX.orEmpty())
            etMldsa65Verify?.text = Utils.getEditable(config.mldsa65Verify.orEmpty())
        }
    }

    fun clear() {
        if (streamSecuritys.isNotEmpty()) {
            spStreamSecurity?.setText(streamSecurityEntries.getOrElse(0) { streamSecuritys[0] }, false)
            updateForSecurity(streamSecuritys[0])
        }
        if (allowinsecures.isNotEmpty()) {
            spAllowInsecure?.setText(allowinsecures[0], false)
        }
        etSni?.text = null
        etPublicKey?.text = null
    }

    fun save(config: ProfileItem): Boolean {
        val streamSecPos = Utils.arrayFind(streamSecuritys, selectedSecurityText)
        if (streamSecPos < 0) return false

        val sniField = etSni?.text?.toString()?.trim()
        val allowInsecurePos = Utils.arrayFind(allowinsecures, spAllowInsecure?.text.toString())
        val utlsPos = Utils.arrayFind(uTlsItems, spStreamFingerprint?.text.toString())
        val alpnPos = Utils.arrayFind(alpns, spStreamAlpn?.text.toString())

        val publicKey = etPublicKey?.text?.toString()
        val shortId = etShortId?.text?.toString()
        val spiderX = etSpiderX?.text?.toString()
        val mldsa65Verify = etMldsa65Verify?.text?.toString()
        val echConfigList = etEchConfigList?.text?.toString()
        val verifyPeerCertByName = etVerifyPeerCertByName?.text?.toString()
        val pinnedCA256 = etPinnedCa256?.text?.toString()

        val allowInsecure =
        if (allowInsecurePos < 0 || allowinsecures[allowInsecurePos].isBlank()) {
            false
        } else {
            allowinsecures[allowInsecurePos].toBoolean()
        }

        config.security = streamSecuritys[streamSecPos]
        config.insecure = allowInsecure
        config.sni = sniField
        config.fingerPrint = uTlsItems[if (utlsPos >= 0) utlsPos else 0]
        config.alpn = alpns[if (alpnPos >= 0) alpnPos else 0]
        config.publicKey = publicKey
        config.shortId = shortId
        config.spiderX = spiderX
        config.mldsa65Verify = mldsa65Verify
        config.echConfigList = echConfigList
        config.verifyPeerCertByName = verifyPeerCertByName
        config.pinnedCA256 = pinnedCA256

        return true
    }
}
