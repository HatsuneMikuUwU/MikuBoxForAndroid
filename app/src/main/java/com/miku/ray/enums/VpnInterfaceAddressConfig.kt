package com.miku.ray.enums

enum class VpnInterfaceAddressConfig(
    val displayName: String,
    val ipv4Client: String,
    val ipv4Router: String,
    val ipv6Client: String,
    val ipv6Router: String
) {
    OPTION_1("10.10.14.x", "10.10.14.1", "10.10.14.2", "fc00::10:10:14:1", "fc00::10:10:14:2"),
    OPTION_2("10.1.0.x", "10.1.0.1", "10.1.0.2", "fc00::10:1:0:1", "fc00::10:1:0:2"),
    OPTION_3("10.0.0.x", "10.0.0.1", "10.0.0.2", "fc00::10:0:0:1", "fc00::10:0:0:2"),
    OPTION_4("172.31.0.x", "172.31.0.1", "172.31.0.2", "fc00::172:31:0:1", "fc00::172:31:0:2"),
    OPTION_5("172.20.0.x", "172.20.0.1", "172.20.0.2", "fc00::172:20:0:1", "fc00::172:20:0:2"),
    OPTION_6("172.16.0.x", "172.16.0.1", "172.16.0.2", "fc00::172:16:0:1", "fc00::172:16:0:2"),
    OPTION_7("192.168.100.x", "192.168.100.1", "192.168.100.2", "fc00::192:168:100:1", "fc00::192:168:100:2");

    companion object {
        fun getConfigByIndex(index: Int): VpnInterfaceAddressConfig {
            return if (index in entries.toTypedArray().indices) {
                entries[index]
            } else {
                OPTION_1
            }
        }
    }
}
