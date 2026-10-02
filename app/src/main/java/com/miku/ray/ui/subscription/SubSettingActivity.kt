package com.miku.ray.ui.subscription

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.miku.ray.MikuSubscriptions
import com.miku.ray.R
import com.miku.ray.ui.base.HelperBaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The subscription list: every Clash subscription this app has, with its URL, its
 * schedule and the actions that matter for one.
 *
 * MikuRay's screen manages its own subscription records; here the rows come from
 * [MikuSubscriptions], which is backed by this app's profile store — so the
 * switch, the refresh and the delete all act on the profile the tunnel will
 * actually use. Rows MikuRay shows that a Clash subscription cannot fill (traffic
 * quota and expiry, which the provider reports in response headers) stay hidden
 * rather than showing a placeholder number.
 */
class SubSettingActivity : HelperBaseActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var adapter: SubscriptionAdapter
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_setting)

        setupToolbar(
            findViewById<MaterialToolbar>(R.id.toolbar),
            showHomeAsUp = true,
            title = getString(R.string.title_sub_setting),
            subtitle = getString(R.string.subtitle_server_config),
        )

        recycler = findViewById(R.id.recycler_view)
        emptyState = findViewById(R.id.layout_empty_state)
        adapter = SubscriptionAdapter(
            onAutoUpdateChanged = { id, enabled -> setAutoUpdate(id, enabled) },
            onEdit = { id -> openEditor(id) },
            onRemove = { id, name -> confirmRemove(id, name) },
            onShare = { url -> share(url) },
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        reload()
    }

    override fun onResume() {
        super.onResume()
        if (::recycler.isInitialized) reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_sub_setting, menu)
        menu.add(0, 0x6d01, 0, getString(R.string.mihomo_group_manage_title))
        // Sorting subscriptions would need an order this store does not keep.
        menu.findItem(R.id.sub_sort)?.isVisible = false
        (menu.findItem(R.id.search_view)?.actionView as? SearchView)?.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    this@SubSettingActivity.query = newText.orEmpty().trim()
                    reload()
                    return false
                }
            },
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        0x6d01 -> {
            com.miku.ray.ui.server.ProfileGroupPicker.manage(this) {
                com.miku.ray.handler.SettingsChangeManager.makeSetupGroupTab()

            }
            true
        }
        R.id.add_config -> { openEditor(null); true }
        R.id.sub_update -> { refreshAll(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun reload() {
        val all = MikuSubscriptions.list()
        val visible = if (query.isEmpty()) {
            all
        } else {
            all.filter { it.name.contains(query, true) || it.url.contains(query, true) }
        }
        adapter.submit(visible)
        emptyState.isVisible = all.isEmpty()
        recycler.isVisible = all.isNotEmpty()
    }

    private fun openEditor(id: String?) {
        val intent = android.content.Intent(this, SubEditActivity::class.java)
        id?.let { intent.putExtra("subId", it) }
        startActivity(intent)
    }

    /** Off is stored as "no schedule", which is what the updater keys off. */
    private fun setAutoUpdate(id: String, enabled: Boolean) {
        val current = MikuSubscriptions.list().firstOrNull { it.id == id } ?: return
        MikuSubscriptions.upsert(
            id = id,
            name = current.name,
            url = current.url,
            autoUpdate = enabled,
            intervalMinutes = if (enabled) current.intervalMinutes.coerceAtLeast(DEFAULT_INTERVAL_MINUTES) else 0L,
            throughProxy = current.throughProxy,
        )
        reload()
    }

    private fun confirmRemove(id: String, name: String) {
        // F17: this screen built its own dialog with the whole warning as the
        // TITLE, so the text was cut off at the dialog edge and the shared
        // scrollable delete-confirm layout never reached it. Route through the
        // shared builder: the name is the title, the warning scrolls in full.
        com.miku.ray.util.showDeleteConfirmDialog(
            context = this,
            message = getString(R.string.del_config_dialog_comfirm_message),
            titleRes = R.string.del_config_comfirm,
        ) {
            MikuSubscriptions.remove(id)
            reload()
        }
    }

    private fun share(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, url)
        }
        runCatching { startActivity(android.content.Intent.createChooser(intent, null)) }
    }

    private fun refreshAll() {
        val ids = MikuSubscriptions.list().map { it.id }
        if (ids.isEmpty()) return
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { ids.map { MikuSubscriptions.refresh(it) } }
            val message = if (results.all { it }) {
                R.string.title_alerter_success
            } else {
                R.string.title_alerter_error
            }
            android.widget.Toast.makeText(this@SubSettingActivity, message, android.widget.Toast.LENGTH_SHORT).show()
            reload()
        }
    }

    /** One subscription per row, in MikuRay's row layout. */
    private class SubscriptionAdapter(
        private val onAutoUpdateChanged: (String, Boolean) -> Unit,
        private val onEdit: (String) -> Unit,
        private val onRemove: (String, String) -> Unit,
        private val onShare: (String) -> Unit,
    ) : RecyclerView.Adapter<SubscriptionAdapter.Holder>() {

        private var items: List<MikuSubscriptions.Subscription> = emptyList()

        fun submit(list: List<MikuSubscriptions.Subscription>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_recycler_sub_setting, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val name: TextView = view.findViewById(R.id.tv_name)
            private val url: TextView = view.findViewById(R.id.tv_url)
            private val servers: TextView = view.findViewById(R.id.tv_server_count)
            private val updated: TextView = view.findViewById(R.id.tv_last_updated)
            private val autoUpdate: MaterialSwitch = view.findViewById(R.id.chk_enable)

            fun bind(item: MikuSubscriptions.Subscription) {
                val context = itemView.context
                name.text = item.name
                url.text = item.url
                // The row's "servers" slot carries the schedule: a Clash
                // subscription's own count is not known until it is parsed.
                servers.text = if (item.intervalMinutes > 0) "${item.intervalMinutes / 60}h" else ""
                updated.text = if (item.lastUpdatedMillis > 0) {
                    android.text.format.DateFormat.getDateFormat(context).format(item.lastUpdatedMillis)
                } else {
                    ""
                }

                // A Clash subscription has no quota or expiry of its own; those come
                // from provider headers this client does not read yet.
                itemView.findViewById<View>(R.id.tv_subscription_usage)?.isVisible = false
                itemView.findViewById<View>(R.id.tv_subscription_expire)?.isVisible = false

                autoUpdate.setOnCheckedChangeListener(null)
                autoUpdate.isChecked = item.autoUpdate
                autoUpdate.setOnCheckedChangeListener { _, checked -> onAutoUpdateChanged(item.id, checked) }

                itemView.findViewById<View>(R.id.layout_edit).setOnClickListener { onEdit(item.id) }
                itemView.findViewById<View>(R.id.layout_remove).setOnClickListener { onRemove(item.id, item.name) }
                itemView.findViewById<View>(R.id.layout_share).setOnClickListener { onShare(item.url) }
            }
        }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MINUTES = 24L * 60
    }
}
