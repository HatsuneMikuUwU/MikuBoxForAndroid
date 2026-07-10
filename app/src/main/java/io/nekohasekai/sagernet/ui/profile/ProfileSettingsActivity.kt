package io.nekohasekai.sagernet.ui.profile

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.*
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.databinding.LayoutGroupItemBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.widget.ListListener
import kotlinx.parcelize.Parcelize
import kotlin.properties.Delegates
import io.nekohasekai.sagernet.ui.bottomsheet.ProfileSettingsMenuBottomSheet
import io.nekohasekai.sagernet.ui.toolbar.ProfileSettingsMenuController

@Suppress("UNCHECKED_CAST")
abstract class ProfileSettingsActivity<T : AbstractBean>(
    @LayoutRes resId: Int = R.layout.uwu_collapse_layout,
) : ThemedActivity(resId), OnPreferenceDataStoreChangeListener,
    ProfileSettingsMenuBottomSheet.OnOptionClickListener {
    
    private lateinit var menuController: ProfileSettingsMenuController

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    (requireActivity() as ProfileSettingsActivity<*>).saveAndExit()
                }
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    @Parcelize
    data class ProfileIdArg(val profileId: Long, val groupId: Long) : Parcelable
    class DeleteConfirmationDialogFragment : AlertDialogFragment<ProfileIdArg, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.delete_confirm_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    ProfileManager.deleteProfile(arg.groupId, arg.profileId)
                }
                requireActivity().finish()
            }
            setNegativeButton(R.string.no, null)
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "id"
        const val EXTRA_IS_SUBSCRIPTION = "sub"
    }

    abstract fun createEntity(): T
    abstract fun T.init()
    abstract fun T.serialize()

    val proxyEntity by lazy { SagerDatabase.proxyDao.getById(DataStore.editingId) }
    protected var isSubscription by Delegates.notNull<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val collapsingToolbar = findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)
        collapsingToolbar.title = getString(R.string.profile_config)

        toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        menuController = ProfileSettingsMenuController(
            toolbar = toolbar,
            fragmentManager = supportFragmentManager,
            listener = this
        )

        if (savedInstanceState == null) {
            val editingId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
            isSubscription = intent.getBooleanExtra(EXTRA_IS_SUBSCRIPTION, false)
            DataStore.editingId = editingId
            runOnDefaultDispatcher {
                if (editingId == 0L) {
                    DataStore.editingGroup = DataStore.selectedGroupForImport()
                    createEntity().applyDefaultValues().init()
                } else {
                    if (proxyEntity == null) {
                        onMainDispatcher {
                            finish()
                        }
                        return@runOnDefaultDispatcher
                    }
                    DataStore.editingGroup = proxyEntity!!.groupId
                    (proxyEntity!!.requireBean() as T).init()
                }

                onMainDispatcher {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.settings, MyPreferenceFragmentCompat())
                        .commit()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (::menuController.isInitialized) {
            menuController.refresh()
        }
    }

    open suspend fun saveAndExit() {
        val editingId = DataStore.editingId
        if (editingId == 0L) {
            val editingGroup = DataStore.editingGroup
            ProfileManager.createProfile(editingGroup, createEntity().apply { serialize() })
        } else {
            if (proxyEntity == null) {
                finish()
                return
            }
            if (proxyEntity!!.id == DataStore.selectedProxy) {
                SagerNet.stopService()
            }
            ProfileManager.updateProfile(proxyEntity!!.apply { (requireBean() as T).serialize() })
        }
        finish()
    }

    val child by lazy { supportFragmentManager.findFragmentById(R.id.settings) as MyPreferenceFragmentCompat }

    override fun onOptionClicked(viewId: Int) {
        child.handleOptionClick(viewId)
    }

    override fun onBackPressed() {
        if (DataStore.dirty) UnsavedChangesDialogFragment().apply { key() }
            .show(supportFragmentManager, null) else super.onBackPressed()
    }

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key != Key.PROFILE_DIRTY) {
            DataStore.dirty = true
        }
    }

    abstract fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    )

    open fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
    }

    open fun PreferenceFragmentCompat.displayPreferenceDialog(preference: Preference): Boolean {
        return false
    }

    class MyPreferenceFragmentCompat : PreferenceFragmentCompat() {

        var activity: ProfileSettingsActivity<*>? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            try {
                activity = (requireActivity() as ProfileSettingsActivity<*>).apply {
                    createPreferences(savedInstanceState, rootKey)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    SagerNet.application,
                    "Error on createPreferences, please try again.",
                    Toast.LENGTH_SHORT
                ).show()
                Logs.e(e)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            ViewCompat.setOnApplyWindowInsetsListener(listView, ListListener)

            activity?.apply {
                viewCreated(view, savedInstanceState)
                DataStore.dirty = false
                DataStore.profileCacheStore.registerChangeListener(this)
            }
        }

        @SuppressLint("CheckResult")
        fun handleOptionClick(itemId: Int) {
            when (itemId) {
                R.id.action_delete -> {
                    if (DataStore.editingId == 0L) {
                        requireActivity().finish()
                    } else {
                        DeleteConfirmationDialogFragment().apply {
                            arg(
                                ProfileIdArg(
                                    DataStore.editingId, DataStore.editingGroup
                                )
                            )
                            key()
                        }.show(parentFragmentManager, null)
                    }
                }

                R.id.action_apply -> {
                    runOnDefaultDispatcher {
                        activity?.saveAndExit()
                    }
                }

                R.id.action_custom_outbound_json -> {
                    activity?.proxyEntity?.apply {
                        val bean = requireBean()
                        DataStore.serverCustomOutbound = bean.customOutboundJson
                        ProfileJsonEditorDialogFragment.newInstance(
                            Key.SERVER_CUSTOM_OUTBOUND,
                            R.string.custom_outbound_json
                        ).apply {
                            onSaved = { bean.customOutboundJson = it }
                        }.show(parentFragmentManager, "profile_json_editor")
                    }
                }

                R.id.action_custom_config_json -> {
                    activity?.proxyEntity?.apply {
                        val bean = requireBean()
                        DataStore.serverCustom = bean.customConfigJson
                        ProfileJsonEditorDialogFragment.newInstance(
                            Key.SERVER_CUSTOM,
                            R.string.custom_config_json
                        ).apply {
                            onSaved = { bean.customConfigJson = it }
                        }.show(parentFragmentManager, "profile_json_editor")
                    }
                }

                R.id.action_create_shortcut -> {
                    val activity = requireActivity() as ProfileSettingsActivity<*>
                    val ent = activity.proxyEntity!!
                    val shortcut = ShortcutInfoCompat.Builder(activity, "shortcut-profile-${ent.id}")
                        .setShortLabel(ent.displayName())
                        .setLongLabel(ent.displayName())
                        .setIcon(
                            IconCompat.createWithResource(
                                activity, R.drawable.ic_qu_shadowsocks_launcher
                            )
                        ).setIntent(Intent(
                            context, QuickToggleShortcut::class.java
                        ).apply {
                            action = Intent.ACTION_MAIN
                            putExtra("profile", ent.id)
                        }).build()
                    ShortcutManagerCompat.requestPinShortcut(activity, shortcut, null)
                }

                R.id.action_move -> {
                    val activity = requireActivity() as ProfileSettingsActivity<*>
                    val view = LinearLayout(context).apply {
                        val ent = activity.proxyEntity!!
                        orientation = LinearLayout.VERTICAL

                        SagerDatabase.groupDao.allGroups()
                            .filter { it.type == GroupType.BASIC && it.id != ent.groupId }
                            .forEach { group ->
                                LayoutGroupItemBinding.inflate(layoutInflater, this, true).apply {
                                    edit.isVisible = false
                                    options.isVisible = false
                                    groupName.text = group.displayName()
                                    groupUpdate.text = getString(R.string.move)
                                    groupUpdate.setOnClickListener {
                                        runOnDefaultDispatcher {
                                            val oldGroupId = ent.groupId
                                            val newGroupId = group.id
                                            ent.groupId = newGroupId
                                            ProfileManager.updateProfile(ent)
                                            GroupManager.postUpdate(oldGroupId) // reload
                                            GroupManager.postUpdate(newGroupId)
                                            DataStore.editingGroup = newGroupId // post switch animation
                                            runOnMainDispatcher {
                                                activity.finish()
                                            }
                                        }
                                    }
                                }
                            }
                    }
                    val scrollView = ScrollView(context).apply {
                        addView(view)
                    }
                    MaterialAlertDialogBuilder(activity).setView(scrollView).show()
                }
            }
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            activity?.apply {
                if (displayPreferenceDialog(preference)) return
            }
            super.onDisplayPreferenceDialog(preference)
        }
    }

    object PasswordSummaryProvider : Preference.SummaryProvider<EditTextPreference> {
        override fun provideSummary(preference: EditTextPreference): CharSequence {
            val text = preference.text
            return if (text.isNullOrBlank()) {
                preference.context.getString(androidx.preference.R.string.not_set)
            } else {
                "\u2022".repeat(text.length)
            }
        }
    }
}
