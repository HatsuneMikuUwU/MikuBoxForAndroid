package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet

enum class PluginEntry(
    val pluginId: String,
    val displayName: String,
    val downloadLink: String = "https://matsuridayo.github.io/"
) {
    TrojanGo(
        "trojan-go-plugin",
        SagerNet.application.getString(R.string.action_trojan_go)
    ),
    MieruProxy(
        "mieru-plugin",
        SagerNet.application.getString(R.string.action_mieru),
        "https://github.com/MatsuriDayo/plugins/releases?q=mieru"
    ),
    NaiveProxy(
        "naive-plugin",
        SagerNet.application.getString(R.string.action_naive),
        "https://github.com/MatsuriDayo/plugins/releases?q=naive"
    ),
    Hysteria(
        "hysteria-plugin",
        SagerNet.application.getString(R.string.action_hysteria),
        "https://github.com/MatsuriDayo/plugins/releases?q=Hysteria"
    ),
    ;

    companion object {

        fun find(name: String): PluginEntry? {
            for (pluginEntry in enumValues<PluginEntry>()) {
                if (name == pluginEntry.pluginId) {
                    return pluginEntry
                }
            }
            return null
        }

    }

}
