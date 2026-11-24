package top.nkbe.mikubox.ui.viewmodel

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceDataStore
import com.neko.widget.BannerSettingsCharacterImageView
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileGroupChip(
    val id: Long,
    val name: String,
    val count: Int,
    val order: Int,
    val isSubscription: Boolean,
)

data class ProfilesUiState(
    val groups: List<ProfileGroupChip> = emptyList(),
    val selectedGroupId: Long = 0L,
    val profiles: List<ProxyEntity> = emptyList(),
    val selectedProxyId: Long = 0L,
    val selectedProxyName: String = "",
    val searchQuery: String = "",
    val searchExpanded: Boolean = false,
    val doubleColumn: Boolean = false,
    val alwaysShowAddress: Boolean = false,
    val customProfileName: String = "",
    @DrawableRes val bannerCharacterRes: Int = BannerSettingsCharacterImageView.resolveDrawable(null),
    @DrawableRes val homeBannerRes: Int = top.nkbe.mikubox.ui.theme.MikuBannerManager.getCurrentHomeBannerRes(),
    val isLoading: Boolean = true,
)

class ProfilesViewModel : ViewModel(),
    ProfileManager.Listener,
    GroupManager.Listener,
    OnPreferenceDataStoreChangeListener {

    private val _uiState = MutableStateFlow(
        ProfilesUiState(
            selectedGroupId = DataStore.selectedGroup,
            selectedProxyId = DataStore.selectedProxy,
            doubleColumn = DataStore.doubleColumnProfileList,
            alwaysShowAddress = DataStore.alwaysShowAddress,
            customProfileName = DataStore.customProfileName,
            bannerCharacterRes = BannerSettingsCharacterImageView.resolveDrawable(
                DataStore.bannerSettingsCharacter
            ),
            homeBannerRes = top.nkbe.mikubox.ui.theme.MikuBannerManager.getCurrentHomeBannerRes(),
        )
    )
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    private var allProfilesInGroup: List<ProxyEntity> = emptyList()
    private var coalescedReloadJob: kotlinx.coroutines.Job? = null

    init {
        ProfileManager.addListener(this)
        GroupManager.addListener(this)
        DataStore.configurationStore.registerChangeListener(this)
        DataStore.profileCacheStore.registerChangeListener(this)
        reloadAll()
    }

    override fun onCleared() {
        coalescedReloadJob?.cancel()
        ProfileManager.removeListener(this)
        GroupManager.removeListener(this)
        DataStore.configurationStore.unregisterChangeListener(this)
        DataStore.profileCacheStore.unregisterChangeListener(this)
        super.onCleared()
    }

    private fun scheduleCoalescedReload() {
        coalescedReloadJob?.cancel()
        coalescedReloadJob = viewModelScope.launch {
            kotlinx.coroutines.delay(90L)
            reloadAll()
        }
    }

    fun reloadAll() {
        coalescedReloadJob?.cancel()
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                var rawGroups = ArrayList(SagerDatabase.groupDao.allGroups())
                if (rawGroups.isEmpty()) {
                    SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
                    rawGroups = ArrayList(SagerDatabase.groupDao.allGroups())
                }
                val visibleGroups = rawGroups.filter { grp ->
                    !grp.ungrouped || SagerDatabase.proxyDao.countByGroup(grp.id) > 0L || rawGroups.size == 1
                }
                val chips = visibleGroups.map { grp ->
                    ProfileGroupChip(
                        id = grp.id,
                        name = grp.displayName(),
                        count = SagerDatabase.proxyDao.countByGroup(grp.id).toInt(),
                        order = grp.order,
                        isSubscription = grp.type == GroupType.SUBSCRIPTION,
                    )
                }

                val currentGroupId = DataStore.selectedGroup.takeIf { id ->
                    chips.any { it.id == id }
                } ?: chips.firstOrNull()?.id ?: 0L

                if (currentGroupId != 0L && currentGroupId != DataStore.selectedGroup) {
                    DataStore.selectedGroup = currentGroupId
                }

                val activeChip = chips.firstOrNull { it.id == currentGroupId }
                val profiles = if (currentGroupId != 0L) {
                    val rawList = SagerDatabase.proxyDao.getByGroup(currentGroupId)
                    val safeList = rawList.filter {
                        try {
                            it.requireBean()
                            true
                        } catch (e: Throwable) {
                            Logs.w("Skipping corrupted profile ${it.id}: ${e.message}")
                            false
                        }
                    }
                    sortProfiles(
                        safeList,
                        activeChip?.order ?: GroupOrder.ORIGIN,
                    )
                } else {
                    emptyList()
                }

                var selectedId = DataStore.selectedProxy
                var selectedEntity = if (selectedId > 0L) {
                    SagerDatabase.proxyDao.getById(selectedId)
                } else {
                    null
                }
                if (selectedEntity == null && profiles.isNotEmpty()) {
                    selectedEntity = profiles.first()
                    selectedId = selectedEntity.id
                    DataStore.selectedProxy = selectedId
                }
                val selectedName = selectedEntity?.displayName() ?: ""

                Triple(chips, currentGroupId, Pair(profiles, selectedName))
            }

            val (chips, currentGroupId, profPair) = snapshot
            val (profiles, selectedName) = profPair
            allProfilesInGroup = profiles
            val query = _uiState.value.searchQuery

            _uiState.update {
                it.copy(
                    groups = chips,
                    selectedGroupId = currentGroupId,
                    profiles = applyFilter(profiles, query),
                    selectedProxyId = DataStore.selectedProxy,
                    selectedProxyName = selectedName,
                    isLoading = false,
                )
            }
        }
    }

    fun selectGroup(groupId: Long) {
        if (groupId == _uiState.value.selectedGroupId) return
        DataStore.selectedGroup = groupId
        _uiState.update { it.copy(selectedGroupId = groupId) }
        reloadAll()
    }

    fun selectProxy(entity: ProxyEntity) {
        DataStore.selectedProxy = entity.id
        _uiState.update {
            it.copy(
                selectedProxyId = entity.id,
                selectedProxyName = entity.displayName(),
            )
        }
    }

    fun toggleSearch() {
        _uiState.update { state ->
            val nextExpanded = !state.searchExpanded
            val nextQuery = if (nextExpanded) state.searchQuery else ""
            state.copy(
                searchExpanded = nextExpanded,
                searchQuery = nextQuery,
                profiles = applyFilter(allProfilesInGroup, nextQuery),
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                profiles = applyFilter(allProfilesInGroup, query),
            )
        }
    }

    fun toggleDoubleColumn() {
        val next = !DataStore.doubleColumnProfileList
        DataStore.doubleColumnProfileList = next
        _uiState.update { it.copy(doubleColumn = next) }
    }

    fun deleteProfile(entity: ProxyEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            ProfileManager.deleteProfile(entity.groupId, entity.id)
        }
    }

    fun currentGroupOrder(): Int {
        val id = _uiState.value.selectedGroupId
        return _uiState.value.groups.firstOrNull { it.id == id }?.order ?: GroupOrder.ORIGIN
    }

    private fun sortProfiles(list: List<ProxyEntity>, order: Int): List<ProxyEntity> = when (order) {
        GroupOrder.BY_NAME -> list.sortedBy { it.displayName().lowercase() }
        GroupOrder.BY_DELAY -> list.sortedWith(
            compareBy<ProxyEntity> {
                if (it.status == 1 && it.ping > 0) 0 else 1
            }.thenBy {
                if (it.status == 1 && it.ping > 0) it.ping else Int.MAX_VALUE
            }
        )
        else -> list.sortedBy { it.userOrder }
    }

    private fun applyFilter(list: List<ProxyEntity>, query: String): List<ProxyEntity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return list
        return list.filter { entity ->
            entity.displayName().lowercase().contains(q) ||
                entity.displayType().lowercase().contains(q) ||
                entity.displayAddress().lowercase().contains(q)
        }
    }

    // ProfileManager.Listener
    override suspend fun onAdd(profile: ProxyEntity) = scheduleCoalescedReload()
    override suspend fun onUpdated(data: io.nekohasekai.sagernet.aidl.TrafficData) { /* traffic handled by StatsBar */ }
    override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) = scheduleCoalescedReload()
    override suspend fun onRemoved(groupId: Long, profileId: Long) = scheduleCoalescedReload()

    // GroupManager.Listener
    override suspend fun groupAdd(group: ProxyGroup) = scheduleCoalescedReload()
    override suspend fun groupUpdated(group: ProxyGroup) = scheduleCoalescedReload()
    override suspend fun groupUpdated(groupId: Long) = scheduleCoalescedReload()
    override suspend fun groupRemoved(groupId: Long) = scheduleCoalescedReload()

    // Preference listener
    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            "double_column_profile_list" -> _uiState.update {
                it.copy(doubleColumn = DataStore.doubleColumnProfileList)
            }
            "banner_settings_character" -> _uiState.update {
                it.copy(
                    bannerCharacterRes = BannerSettingsCharacterImageView.resolveDrawable(
                        DataStore.bannerSettingsCharacter
                    )
                )
            }
            "home_banner_theme", "randomize_banner_on_launch" -> _uiState.update {
                it.copy(homeBannerRes = top.nkbe.mikubox.ui.theme.MikuBannerManager.getCurrentHomeBannerRes())
            }
            "custom_profile_name" -> _uiState.update {
                it.copy(customProfileName = DataStore.customProfileName)
            }
            Key.PROFILE_GROUP -> if (store === DataStore.configurationStore) {
                val targetId = DataStore.selectedGroup
                if (targetId > 0L && targetId != _uiState.value.selectedGroupId) {
                    selectGroup(targetId)
                }
            }
            Key.PROFILE_ID -> if (store === DataStore.configurationStore) {
                reloadAll()
            }
        }
    }
}
