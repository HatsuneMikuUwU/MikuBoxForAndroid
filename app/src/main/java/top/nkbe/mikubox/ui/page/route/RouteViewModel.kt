package top.nkbe.mikubox.ui.page.route

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.needReload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class RuleItemUi(
    val id: Long,
    val name: String,
    val summary: String,
    val outboundCode: Long,
    val outboundLabel: String,
    val enabled: Boolean,
)

@Immutable
data class RouteUiState(
    val rules: List<RuleItemUi> = emptyList(),
)

class RouteViewModel : ViewModel(), ProfileManager.RuleListener {

    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    private var cachedRules: List<RuleEntity> = emptyList()

    init {
        ProfileManager.addListener(this)
        reload()
    }

    override fun onCleared() {
        ProfileManager.removeListener(this)
        super.onCleared()
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val rules = ProfileManager.getRules()
            val mapped = rules.map { it.toUi() }
            cachedRules = rules
            _uiState.update { it.copy(rules = mapped) }
        }
    }

    fun toggleRule(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = cachedRules.firstOrNull { it.id == ruleId }
                ?: SagerDatabase.rulesDao.getById(ruleId)
                ?: return@launch
            target.enabled = enabled
            SagerDatabase.rulesDao.updateRule(target)
            cachedRules = cachedRules.map { if (it.id == ruleId) target else it }
            _uiState.update { state ->
                state.copy(
                    rules = state.rules.map { item ->
                        if (item.id == ruleId) item.copy(enabled = enabled) else item
                    }
                )
            }
            withContext(Dispatchers.Main) {
                needReload()
            }
        }
    }

    fun moveRule(ruleId: Long, delta: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = cachedRules.toMutableList()
            val index = current.indexOfFirst { it.id == ruleId }
            val targetIndex = index + delta
            if (index == -1 || targetIndex !in current.indices) return@launch

            val first = current[index]
            val second = current[targetIndex]
            val swappedOrder = first.userOrder
            first.userOrder = second.userOrder
            second.userOrder = swappedOrder

            current[index] = second
            current[targetIndex] = first
            cachedRules = current

            SagerDatabase.rulesDao.updateRules(listOf(first, second))
            val mapped = current.map { it.toUi() }
            _uiState.update { it.copy(rules = mapped) }

            withContext(Dispatchers.Main) {
                needReload()
            }
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            ProfileManager.deleteRule(ruleId)
        }
    }

    fun resetRules() {
        viewModelScope.launch(Dispatchers.IO) {
            SagerDatabase.rulesDao.reset()
            DataStore.rulesFirstCreate = false
            reload()
            withContext(Dispatchers.Main) {
                needReload()
            }
        }
    }

    override suspend fun onAdd(rule: RuleEntity) {
        reload()
        withContext(Dispatchers.Main) {
            needReload()
        }
    }

    override suspend fun onUpdated(rule: RuleEntity) {
        reload()
        withContext(Dispatchers.Main) {
            needReload()
        }
    }

    override suspend fun onRemoved(ruleId: Long) {
        reload()
        withContext(Dispatchers.Main) {
            needReload()
        }
    }

    override suspend fun onCleared() {
        cachedRules = emptyList()
        _uiState.update { it.copy(rules = emptyList()) }
        withContext(Dispatchers.Main) {
            needReload()
        }
    }

    private fun RuleEntity.toUi(): RuleItemUi = RuleItemUi(
        id = id,
        name = displayName(),
        summary = mkSummary(),
        outboundCode = outbound,
        outboundLabel = displayOutbound(),
        enabled = enabled,
    )
}
