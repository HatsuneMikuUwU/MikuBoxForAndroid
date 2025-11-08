package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen

class TuicSettingsActivity : ProfileSettingsActivity<TuicBean>() {

    override fun createEntity() = TuicBean().applyDefaultValues()

    override fun TuicBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = uuid
        DataStore.serverPassword = token
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = caText
        DataStore.serverUDPRelayMode = udpRelayMode
        DataStore.serverCongestionController = congestionController
        DataStore.serverDisableSNI = disableSNI
        DataStore.serverSNI = sni
        DataStore.serverReduceRTT = reduceRTT
        DataStore.serverAllowInsecure = allowInsecure
    }

    override fun TuicBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        uuid = DataStore.serverUsername
        token = DataStore.serverPassword
        alpn = DataStore.serverALPN
        caText = DataStore.serverCertificates
        udpRelayMode = DataStore.serverUDPRelayMode
        congestionController = DataStore.serverCongestionController
        disableSNI = DataStore.serverDisableSNI
        sni = DataStore.serverSNI
        reduceRTT = DataStore.serverReduceRTT
        allowInsecure = DataStore.serverAllowInsecure
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.tuic_preferences)

        val styleValue = DataStore.categoryStyle
        preferenceScreen?.let { screen ->
            updateAllCategoryStyles(styleValue, screen)
        }

        val disableSNI = findPreference<SwitchPreference>(Key.SERVER_DISABLE_SNI)!!
        val sni = findPreference<EditTextPreference>(Key.SERVER_SNI)!!
        sni.isEnabled = !disableSNI.isChecked
        disableSNI.setOnPreferenceChangeListener { _, newValue ->
            sni.isEnabled = !(newValue as Boolean)
            true
        }

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

    private fun updateAllCategoryStyles(styleValue: String?, group: PreferenceGroup) {
        val newLayout = when (styleValue) {
            "style1" -> R.layout.uwu_preference_category_1
            "style2" -> R.layout.uwu_preference_category_2
            "style3" -> R.layout.uwu_preference_category_3
            "style4" -> R.layout.uwu_preference_category_4
            "style5" -> R.layout.uwu_preference_category_5
            "style6" -> R.layout.uwu_preference_category_6
            else -> R.layout.uwu_preference_category_1
        }

        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            if (preference is PreferenceCategory) {
                preference.layoutResource = newLayout
            }
            if (preference is PreferenceGroup) {
                updateAllCategoryStyles(styleValue, preference)
            }
        }
    }

}
