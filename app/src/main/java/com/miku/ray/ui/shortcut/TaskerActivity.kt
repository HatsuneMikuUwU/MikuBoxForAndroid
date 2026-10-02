package com.miku.ray.ui.shortcut
import com.miku.ray.ui.base.BaseActivity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityTaskerBinding
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.LogUtil

private data class TaskerItem(
    val label: String,
    val guid: String,
)

class TaskerActivity : BaseActivity() {
    private val binding by lazy { ActivityTaskerBinding.inflate(layoutInflater) }

    private var listview: ListView? = null
    private val items = mutableListOf<TaskerItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = "")

        items.add(TaskerItem(label = "Default", guid = AppConfig.TASKER_DEFAULT_GUID))

        MmkvManager.decodeAllServerList().forEach { key ->
            MmkvManager.decodeServerConfig(key)?.let { config ->
                items.add(TaskerItem(label = config.remarks, guid = key))
            }
        }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            items.map { it.label }
        )
        listview = findViewById<View>(R.id.listview) as ListView
        listview?.adapter = adapter
        listview?.applyEdgeToEdgeListInsets()

        init()
    }

    private fun init() {
        try {
            val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, "")

            if (switch == null || TextUtils.isEmpty(guid)) {
                return
            } else {
                binding.switchStartService.isChecked = switch
                val pos = items.indexOfFirst { it.guid == guid.toString() }
                if (pos >= 0) {
                    listview?.setItemChecked(pos, true)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to initialize Tasker settings", e)

        }
    }

    private fun confirmFinish() {
        val position = listview?.checkedItemPosition
        if (position == null || position < 0) {
            return
        }

        val extraBundle = Bundle()
        extraBundle.putBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, binding.switchStartService.isChecked)
        extraBundle.putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, items[position].guid)
        val intent = Intent()

        val label = items[position].label
        val blurb = if (binding.switchStartService.isChecked) {
            "Start $label"
        } else {
            "Stop $label"
        }

        intent.putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extraBundle)
        intent.putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, blurb)
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val delConfig = menu.findItem(R.id.del_config)
        delConfig?.isVisible = false
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            true
        }

        R.id.save_config -> {
            confirmFinish()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

}
