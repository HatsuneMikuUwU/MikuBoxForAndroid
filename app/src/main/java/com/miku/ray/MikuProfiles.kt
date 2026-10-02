package com.miku.ray

/** Native configuration storage behind the ported UI. Null only in UI previews. */
object MikuProfiles {
    data class Profile(val id: String, val name: String, val config: String, val groupId: String = AppConfig.DEFAULT_SUBSCRIPTION_ID)
    data class Group(val id: String, val name: String)
    interface Impl {
        fun list(): List<Profile>
        fun routingEnabled(id: String): Boolean
        fun disabledPolicies(id: String): Set<String>
        fun setRoutingEnabled(id: String, enabled: Boolean)
        fun setPolicyEnabled(id: String, policy: String, enabled: Boolean)
        fun groups(): List<Group>
        fun createGroup(name: String): Group
        fun deleteGroup(id: String)
        fun moveToGroup(id: String, groupId: String)
        fun get(id: String): Profile?
        fun save(id: String?, name: String, content: String): String

        /**
         * Persists a shared node link (vmess://, trojan://, …) as a profile and
         * returns its id. The per-protocol forms build one of these from their
         * fields; converting the link into a mihomo configuration lives in the
         * app module (the decoder), behind this interface. Throws when the link
         * cannot be converted.
         */
        fun saveShareLink(id: String?, name: String, shareLink: String, groupId: String?): String
        fun importContent(content: String, groupId: String = AppConfig.DEFAULT_SUBSCRIPTION_ID): Pair<Int, Int>
        fun remove(id: String)
        fun select(id: String)
        fun sync()
        fun exportBackup(): String
        fun validateBackup(content: String)
        fun restoreBackup(content: String)
    }
    @Volatile var impl: Impl? = null
        private set
    fun install(implementation: Impl) { impl = implementation }
}
