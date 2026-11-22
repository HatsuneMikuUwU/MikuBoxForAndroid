package top.nkbe.mikubox.ui.page.group

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.group.GroupUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SubHealth {
    OK,
    WARN,
    ERR,
    LOCAL,
}

@Immutable
data class GroupItemUi(
    val id: Long,
    val name: String,
    val isSubscription: Boolean,
    val ungrouped: Boolean,
    val username: String,
    val nodeCount: Int,
    val lastUpdatedSec: Int,
    val hasQuota: Boolean,
    val usedBytes: Long,
    val totalBytes: Long,
    val percent: Int,
    val daysLeft: Int?,
    val health: SubHealth,
    val isUpdating: Boolean,
    val updateProgress: Float?,
    val universalLink: String,
)

@Immutable
data class GroupUiState(
    val groups: List<GroupItemUi> = emptyList(),
    val subCount: Int = 0,
    val totalNodes: Int = 0,
    val attentionCount: Int = 0,
)

private data class SubMetrics(
    val hasQuota: Boolean = false,
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val percent: Int = 0,
    val daysLeft: Int? = null,
    val health: SubHealth = SubHealth.LOCAL,
)

class GroupViewModel : ViewModel(), GroupManager.Listener {

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    private var cachedGroups: List<ProxyGroup> = emptyList()

    init {
        GroupManager.addListener(this)
        reload()
    }

    override fun onCleared() {
        GroupManager.removeListener(this)
        super.onCleared()
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = SagerDatabase.groupDao.allGroups().toMutableList()
            val ungrouped = all.find { it.ungrouped }
            if (all.size > 1 && ungrouped != null && SagerDatabase.proxyDao.countByGroup(ungrouped.id) == 0L) {
                all.removeAll { it.ungrouped }
            }
            cachedGroups = all

            var subCount = 0
            var totalNodes = 0
            var attentionCount = 0

            val mapped = all.map { group ->
                val count = SagerDatabase.proxyDao.countByGroup(group.id).toInt()
                totalNodes += count
                val metrics = parseSubMetrics(group)
                if (group.type == GroupType.SUBSCRIPTION) {
                    subCount++
                    if (metrics.health == SubHealth.WARN || metrics.health == SubHealth.ERR) {
                        attentionCount++
                    }
                }
                val isUpdating = group.id in GroupUpdater.updating
                val progressObj = GroupUpdater.progress[group.id]
                val progressFraction = if (isUpdating && progressObj != null && progressObj.max > 0) {
                    (progressObj.progress.toFloat() / progressObj.max.toFloat()).coerceIn(0f, 1f)
                } else {
                    null
                }
                GroupItemUi(
                    id = group.id,
                    name = group.displayName(),
                    isSubscription = group.type == GroupType.SUBSCRIPTION,
                    ungrouped = group.ungrouped,
                    username = group.subscription?.username.orEmpty(),
                    nodeCount = count,
                    lastUpdatedSec = group.subscription?.lastUpdated ?: 0,
                    hasQuota = metrics.hasQuota,
                    usedBytes = metrics.usedBytes,
                    totalBytes = metrics.totalBytes,
                    percent = metrics.percent,
                    daysLeft = metrics.daysLeft,
                    health = metrics.health,
                    isUpdating = isUpdating,
                    updateProgress = progressFraction,
                    universalLink = if (group.type == GroupType.SUBSCRIPTION) group.toUniversalLink() else "",
                )
            }

            _uiState.update {
                GroupUiState(
                    groups = mapped,
                    subCount = subCount,
                    totalNodes = totalNodes,
                    attentionCount = attentionCount,
                )
            }
        }
    }

    fun findGroup(groupId: Long): ProxyGroup? {
        return cachedGroups.firstOrNull { it.id == groupId } ?: SagerDatabase.groupDao.getById(groupId)
    }

    fun updateGroup(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val group = findGroup(groupId) ?: return@launch
            withContext(Dispatchers.Main) {
                GroupUpdater.startUpdate(group, true)
            }
        }
    }

    fun updateAllSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            val subs = SagerDatabase.groupDao.allGroups().filter { it.type == GroupType.SUBSCRIPTION }
            withContext(Dispatchers.Main) {
                subs.forEach { GroupUpdater.startUpdate(it, true) }
            }
        }
    }

    fun moveGroup(groupId: Long, delta: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = cachedGroups.toMutableList()
            val index = current.indexOfFirst { it.id == groupId }
            val targetIndex = index + delta
            if (index == -1 || targetIndex !in current.indices) return@launch

            val first = current[index]
            val second = current[targetIndex]
            val swappedOrder = first.userOrder
            first.userOrder = second.userOrder
            second.userOrder = swappedOrder

            SagerDatabase.groupDao.updateGroup(first)
            SagerDatabase.groupDao.updateGroup(second)
            reload()
        }
    }

    fun clearGroup(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            GroupManager.clearGroup(groupId)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = findGroup(groupId) ?: return@launch
            if (!target.ungrouped && target.id !in GroupUpdater.updating) {
                GroupManager.deleteGroup(listOf(target))
                reload()
            }
        }
    }

    fun openGroupNodes(groupId: Long, onNavigate: () -> Unit) {
        DataStore.selectedGroup = groupId
        viewModelScope.launch(Dispatchers.IO) {
            val proxies = SagerDatabase.proxyDao.getByGroup(groupId)
            if (proxies.isNotEmpty() &&
                (DataStore.selectedProxy == 0L || SagerDatabase.proxyDao.getById(DataStore.selectedProxy) == null)
            ) {
                DataStore.selectedProxy = proxies.first().id
            }
            withContext(Dispatchers.Main) {
                onNavigate()
            }
        }
    }

    override suspend fun groupAdd(group: ProxyGroup) {
        delay(300L)
        reload()
        if (group.type == GroupType.SUBSCRIPTION && group.id !in GroupUpdater.updating) {
            withContext(Dispatchers.Main) {
                GroupUpdater.startUpdate(group, true)
            }
        }
    }

    override suspend fun groupUpdated(group: ProxyGroup) {
        reload()
    }

    override suspend fun groupRemoved(groupId: Long) {
        reload()
    }

    override suspend fun groupUpdated(groupId: Long) {
        reload()
    }

    private fun parseSubMetrics(group: ProxyGroup): SubMetrics {
        if (group.type != GroupType.SUBSCRIPTION) return SubMetrics(health = SubHealth.LOCAL)
        val sub = group.subscription ?: return SubMetrics(health = SubHealth.OK)

        var used = 0L
        var total = 0L
        var expireSec = 0L

        if (sub.bytesUsed > 0L || sub.bytesRemaining > 0L) {
            used = sub.bytesUsed.coerceAtLeast(0L)
            val remain = sub.bytesRemaining.coerceAtLeast(0L)
            total = if (remain > 0L) used + remain else 0L
            if (sub.expiryDate != null && sub.expiryDate > 0) {
                expireSec = sub.expiryDate.toLong()
            }
        } else if (!sub.subscriptionUserinfo.isNullOrBlank()) {
            val info = sub.subscriptionUserinfo
            fun extract(regex: String): Long? =
                regex.toRegex(RegexOption.IGNORE_CASE).find(info)?.groupValues?.getOrNull(1)?.toLongOrNull()
            used = (extract("upload=([0-9]+)") ?: 0L) + (extract("download=([0-9]+)") ?: 0L)
            total = extract("total=([0-9]+)") ?: 0L
            expireSec = extract("expire=([0-9]+)") ?: 0L
        }

        val hasQuota = used > 0L || total > 0L
        val pct = if (total > 0L) ((used * 100L) / total).toInt().coerceIn(0, 100) else 0
        val daysLeft: Int? = if (expireSec > 0L) {
            val nowSec = System.currentTimeMillis() / 1000L
            val diffSec = expireSec - nowSec
            if (diffSec <= 0L) 0 else ((diffSec + 86399L) / 86400L).toInt()
        } else {
            null
        }

        val health = when {
            (daysLeft != null && daysLeft <= 0) || (total > 0L && used >= total) -> SubHealth.ERR
            (daysLeft != null && daysLeft <= 5) || (total > 0L && pct >= 80) -> SubHealth.WARN
            else -> SubHealth.OK
        }

        return SubMetrics(hasQuota, used, total, pct, daysLeft, health)
    }
}
