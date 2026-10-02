package com.miku.ray

/**
 * The subscription table, as the vendored subscription screens need it.
 *
 * Clash subscriptions carry exactly four things: the URL, whether to refresh on
 * a schedule, how often, and whether that refresh goes through the tunnel. That
 * is the whole shape here — the per-protocol fields MikuRay's editor also offers
 * (user agent, headers, filters, profile chaining) belong to its own core, not to
 * this one, so they are not part of the contract and its editor hides them.
 *
 * Declared in this module and implemented by the app, for the same reason as
 * [MikuCoreBridge]: the vendored code cannot see the app module.
 */
object MikuSubscriptions {

    data class Subscription(
        val id: String,
        val name: String,
        val url: String,
        val autoUpdate: Boolean,
        val intervalMinutes: Long,
        val throughProxy: Boolean,
        // MikuBox addition (2026-10-02, deliberate deviation from upstream
        // MikuRay): the app-side profile store carries this flag and the
        // subscription editor exposes it; upstream re-sync would drop it.
        val updateWhenConnectedOnly: Boolean = false,
        val lastUpdatedMillis: Long,
    )

    interface Impl {
        fun list(): List<Subscription>

        /**
         * Creates ([id] null) or updates a subscription.
         * Returns the id of the stored entry, or null when it was rejected.
         */
        fun upsert(
            id: String?,
            name: String,
            url: String,
            autoUpdate: Boolean,
            intervalMinutes: Long,
            throughProxy: Boolean,
            // MikuBox addition, same deviation note as [Subscription].
            updateWhenConnectedOnly: Boolean = false,
        ): String?

        fun remove(id: String)

        /** Refreshes now; true when the fetch succeeded. */
        fun refresh(id: String): Boolean
    }

    private object Inert : Impl {
        override fun list(): List<Subscription> = emptyList()
        override fun upsert(
            id: String?,
            name: String,
            url: String,
            autoUpdate: Boolean,
            intervalMinutes: Long,
            throughProxy: Boolean,
            updateWhenConnectedOnly: Boolean,
        ): String? = null

        override fun remove(id: String) = Unit
        override fun refresh(id: String): Boolean = false
    }

    @Volatile
    private var impl: Impl = Inert

    fun install(implementation: Impl) {
        impl = implementation
    }

    fun list(): List<Subscription> = impl.list()

    fun upsert(
        id: String?,
        name: String,
        url: String,
        autoUpdate: Boolean,
        intervalMinutes: Long,
        throughProxy: Boolean,
        updateWhenConnectedOnly: Boolean = false,
    ): String? = impl.upsert(id, name, url, autoUpdate, intervalMinutes, throughProxy, updateWhenConnectedOnly)

    /** Saving a new/empty/repointed subscription includes its first download. */
    suspend fun saveAndRefresh(
        id: String?, name: String, url: String, autoUpdate: Boolean,
        intervalMinutes: Long, throughProxy: Boolean,
        updateWhenConnectedOnly: Boolean = false,
        onSaved: (String) -> Unit = {},
    ): Boolean {
        val previous = id?.let { wanted -> list().firstOrNull { it.id == wanted } }
        val saved = requireNotNull(
            upsert(id, name, url, autoUpdate, intervalMinutes, throughProxy, updateWhenConnectedOnly)
        )
        // Give the editor its stable ID before starting cancellable network work.
        onSaved(saved)
        val needsFetch = previous == null || previous.lastUpdatedMillis == 0L || previous.url != url
        return !needsFetch || kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { refresh(saved) }
    }

    fun refreshAll(): com.miku.ray.dto.SubscriptionUpdateResult {
        var result = com.miku.ray.dto.SubscriptionUpdateResult()
        list().forEach { entry ->
            result += if (refresh(entry.id)) com.miku.ray.dto.SubscriptionUpdateResult(configCount = 1, successCount = 1)
                else com.miku.ray.dto.SubscriptionUpdateResult(failureCount = 1)
        }
        return result
    }

    fun remove(id: String) = impl.remove(id)

    fun refresh(id: String): Boolean = impl.refresh(id)
}
