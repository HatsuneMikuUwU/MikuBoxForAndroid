package com.miku.ray.ui.routing

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textview.MaterialTextView
import com.miku.ray.MikuProfiles
import com.miku.ray.MikuCoreBridge
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.server.ConfigDocument

/** File switches own policy switches; original YAML and rule order are retained. */
class RoutingSettingActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = MaterialToolbar(this)
        root.addView(toolbar)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(com.miku.ray.R.string.mihomo_routing_title))
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        content.addView(MaterialTextView(this).apply {
            text = getString(com.miku.ray.R.string.mihomo_routing_description)
        })
        content.addView(MaterialButton(this).apply {
            text = getString(com.miku.ray.R.string.mihomo_routing_apply_now)
            setOnClickListener {
                // CONNECTING counts as live: the bridge queues the reload and the
                // service replays it once the tunnel is up, exactly like the
                // settings pages that consume SettingsChangeManager.
                val message = if (!MikuCoreBridge.isRunning() && !MikuCoreBridge.isConnecting()) getString(com.miku.ray.R.string.mihomo_routing_saved_next_connect)
                    else if (MikuCoreBridge.restart()) getString(com.miku.ray.R.string.mihomo_routing_reconnecting)
                    else getString(com.miku.ray.R.string.mihomo_routing_reconnect_failed)
                Toast.makeText(this@RoutingSettingActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
        val store = MikuProfiles.impl ?: return
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                store.list().map { profile -> profile to runCatching { ConfigDocument.rules(ConfigDocument.parse(profile.config)) } }
            }
            if (parsed.isEmpty()) content.addView(MaterialTextView(this@RoutingSettingActivity).apply {
                text = getString(com.miku.ray.R.string.mihomo_routing_empty)
            })
            parsed.forEach { (profile, rules) ->
                val section = LinearLayout(this@RoutingSettingActivity).apply { orientation = LinearLayout.VERTICAL }
                val fileSwitch = MaterialSwitch(this@RoutingSettingActivity).apply { text = profile.name; isChecked = store.routingEnabled(profile.id) }
                content.addView(fileSwitch)
                content.addView(section)
                rules.onSuccess { entries ->
                    val disabled = store.disabledPolicies(profile.id)
                    entries.groupBy(ConfigDocument::policy).forEach { (policy, matching) ->
                        section.addView(MaterialSwitch(this@RoutingSettingActivity).apply {
                            // The count is a plurals so word order survives
                            // translation (Russian needs a different form).
                            text = "$policy · " + resources.getQuantityString(
                                com.miku.ray.R.plurals.mihomo_routing_rule_count, matching.size, matching.size,
                            )
                            setPadding((24 * resources.displayMetrics.density).toInt(), 0, 0, 0)
                            isChecked = policy !in disabled
                            isEnabled = fileSwitch.isChecked
                            setOnCheckedChangeListener { _, checked -> store.setPolicyEnabled(profile.id, policy, checked) }
                        })
                    }
                    if (entries.isEmpty()) section.addView(MaterialTextView(this@RoutingSettingActivity).apply {
                        text = getString(com.miku.ray.R.string.mihomo_routing_no_rules)
                    })
                }.onFailure { error ->
                    fileSwitch.isEnabled = false
                    section.addView(MaterialTextView(this@RoutingSettingActivity).apply {
                        text = getString(com.miku.ray.R.string.mihomo_routing_parse_failed, error.message.orEmpty())
                    })
                }
                fileSwitch.setOnCheckedChangeListener { _, checked ->
                    store.setRoutingEnabled(profile.id, checked)
                    for (index in 0 until section.childCount) section.getChildAt(index).isEnabled = checked
                }
            }
        }
    }
}
