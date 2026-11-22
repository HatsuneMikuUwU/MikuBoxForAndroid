package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.widget.NestedScrollView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutNetworkBinding
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.widget.StatsBar
import com.neko.hostnamefinder.HostnameFinder
import com.neko.hosttoip.HostToIP
import com.neko.hostpotproxy.ui.ProxySettings
import com.neko.ip.hostchecker.HostChecker
import com.neko.speedtest.SpeedTestActivity
import top.nkbe.mikubox.ui.component.applyBottomDockPadding

class NetworkFragment : NamedFragment(R.layout.layout_network) {

    override fun name0() = app.getString(R.string.tools_network)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutNetworkBinding.bind(view)
        (binding.root as? NestedScrollView)?.applyBottomDockPadding(20)

        binding.stunTest.setOnClickListener {
            startActivity(Intent(requireContext(), StunActivity::class.java))
        }

        binding.hostnameFinder.setOnClickListener {
            startActivity(Intent(requireContext(), HostnameFinder::class.java))
        }

        binding.hostToIP.setOnClickListener {
            startActivity(Intent(requireContext(), HostToIP::class.java))
        }

        binding.hostpotProxy.setOnClickListener {
            startActivity(Intent(requireContext(), ProxySettings::class.java))
        }
        
        binding.hostChecker.setOnClickListener {
            startActivity(Intent(requireContext(), HostChecker::class.java))
        }
        
        binding.speedTest.setOnClickListener {
            startActivity(Intent(requireContext(), SpeedTestActivity::class.java))
        }

        view.post {
            val bottomAppBar = requireActivity().findViewById<StatsBar>(R.id.stats) ?: return@post

            fun updateBottomBarVisibility() {
                val isConnected = DataStore.serviceState == BaseService.State.Connected
                val showController = DataStore.showBottomBar

                if (!isConnected) {
                    bottomAppBar.performHide()
                } else {
                    if (showController) bottomAppBar.performShow()
                    else bottomAppBar.performHide()
                }
            }

            updateBottomBarVisibility()

            val scrollView = binding.root as? NestedScrollView
            scrollView?.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                val isConnected = DataStore.serviceState == BaseService.State.Connected
                val showController = DataStore.showBottomBar

                if (isConnected && showController) {
                    if (dy > 6) bottomAppBar.performHide()
                    else if (dy < -6) bottomAppBar.performShow()
                } else {
                    bottomAppBar.performHide()
                }
            })
        }
    }
}
