package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.nekohasekai.sagernet.database.DataStore
import top.nkbe.mikubox.ui.component.mikuComposeView
import top.nkbe.mikubox.ui.page.tools.WebviewScreen

// Fragment must preserve a public no-arg constructor for state restoration
class WebviewFragment : ToolbarFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return mikuComposeView {
            WebviewScreen(
                initialUrl = DataStore.yacdURL,
                onOpenMenu = {
                    (activity as? NavigationHost)?.showNavigationSheet()
                        ?: activity?.onBackPressedDispatcher?.onBackPressed()
                },
                onUpdateUrl = { url ->
                    DataStore.yacdURL = url
                },
            )
        }
    }
}
