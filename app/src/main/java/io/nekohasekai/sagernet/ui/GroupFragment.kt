package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.showAllowingStateLoss
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.widget.QRCodeDialog
import top.nkbe.mikubox.ui.component.mikuComposeView
import top.nkbe.mikubox.ui.page.group.GroupScreen
import top.nkbe.mikubox.ui.page.group.GroupScreenCallbacks
import top.nkbe.mikubox.ui.page.group.GroupViewModel

class GroupFragment : ToolbarFragment() {

    private val viewModel: GroupViewModel by viewModels()
    private var pendingExportGroupId: Long = 0L

    private val exportProfiles =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { data ->
            val groupId = pendingExportGroupId
            if (data != null && groupId != 0L) {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(groupId)
                    val links = profiles.joinToString("\n") { it.toStdLink(compact = true) }
                    try {
                        requireActivity().contentResolver.openOutputStream(data)!!.bufferedWriter().use {
                            it.write(links)
                        }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return mikuComposeView {
            val state by viewModel.uiState.collectAsState()
            GroupScreen(
                state = state,
                callbacks = GroupScreenCallbacks(
                    onOpenMenu = {
                        (activity as? NavigationHost)?.showNavigationSheet()
                            ?: activity?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onCreateGroup = {
                        startActivity(Intent(requireContext(), GroupSettingsActivity::class.java))
                    },
                    onUpdateAll = {
                        viewModel.updateAllSubscriptions()
                    },
                    onOpenGroup = { groupId ->
                        viewModel.openGroupNodes(groupId) {
                            (activity as? MainActivity)?.displayFragmentWithId(
                                R.id.nav_configuration,
                                reverseAnim = true,
                            )
                        }
                    },
                    onEditGroup = { groupId ->
                        startActivity(
                            Intent(requireContext(), GroupSettingsActivity::class.java).apply {
                                putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, groupId)
                            }
                        )
                    },
                    onUpdateGroup = { groupId ->
                        viewModel.updateGroup(groupId)
                    },
                    onMoveGroup = { groupId, delta ->
                        viewModel.moveGroup(groupId, delta)
                    },
                    onShareUniversalQr = { group ->
                        QRCodeDialog(group.universalLink, group.name)
                            .showAllowingStateLoss(parentFragmentManager)
                    },
                    onCopyUniversalLink = { group ->
                        val success = SagerNet.trySetPrimaryClip(group.universalLink)
                        (activity as? MainActivity)?.snackbar(
                            if (success) R.string.action_export_msg else R.string.action_export_err
                        )?.show()
                    },
                    onExportClipboard = { group ->
                        runOnDefaultDispatcher {
                            val profiles = SagerDatabase.proxyDao.getByGroup(group.id)
                            val links = profiles.joinToString("\n") { it.toStdLink(compact = true) }
                            onMainDispatcher {
                                SagerNet.trySetPrimaryClip(links)
                                snackbar(getString(R.string.copy_toast_msg)).show()
                            }
                        }
                    },
                    onExportFile = { group ->
                        pendingExportGroupId = group.id
                        startFilesForResult(exportProfiles, "profiles_${group.name}.txt")
                    },
                    onClearGroup = { groupId ->
                        viewModel.clearGroup(groupId)
                    },
                    onDeleteGroup = { groupId ->
                        viewModel.deleteGroup(groupId)
                    },
                ),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }
}
