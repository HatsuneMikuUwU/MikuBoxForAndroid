package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.needReload
import top.nkbe.mikubox.ui.component.mikuComposeView
import top.nkbe.mikubox.ui.page.route.RouteScreen
import top.nkbe.mikubox.ui.page.route.RouteScreenCallbacks
import top.nkbe.mikubox.ui.page.route.RouteViewModel

class RouteFragment : ToolbarFragment() {

    private val viewModel: RouteViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        viewModel.onNeedReload = {
            if (isAdded) needReload()
        }
        return mikuComposeView {
            val state by viewModel.uiState.collectAsState()
            RouteScreen(
                state = state,
                callbacks = RouteScreenCallbacks(
                    onOpenMenu = {
                        (activity as? NavigationHost)?.showNavigationSheet()
                            ?: activity?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onCreateRoute = {
                        startActivity(Intent(requireContext(), RouteSettingsActivity::class.java))
                    },
                    onManageAssets = {
                        startActivity(Intent(requireContext(), AssetsActivity::class.java))
                    },
                    onOpenDocs = {
                        requireContext().launchCustomTab("https://matsuridayo.github.io/nb4a-route/")
                    },
                    onEditRule = { ruleId ->
                        startActivity(
                            Intent(requireContext(), RouteSettingsActivity::class.java).apply {
                                putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, ruleId)
                            }
                        )
                    },
                    onToggleRule = { ruleId, enabled ->
                        viewModel.toggleRule(ruleId, enabled)
                    },
                    onMoveRule = { ruleId, delta ->
                        viewModel.moveRule(ruleId, delta)
                    },
                    onDeleteRule = { ruleId ->
                        viewModel.deleteRule(ruleId)
                    },
                    onResetRules = {
                        viewModel.resetRules()
                    },
                ),
            )
        }
    }
}
