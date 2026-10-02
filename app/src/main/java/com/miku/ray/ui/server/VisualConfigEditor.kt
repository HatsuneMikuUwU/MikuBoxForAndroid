package com.miku.ray.ui.server

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import androidx.core.view.ViewCompat
import com.miku.ray.R

/** Section cards and editable preference rows over the complete YAML document. */
class VisualConfigEditor(context: Context, private val changed: (String) -> Unit) : LinearLayout(context) {
    private var document = linkedMapOf<String, Any?>()
    private val path = mutableListOf<Any>()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun color(attr: Int) = MaterialColors.getColor(this, attr)
    private fun text(id: Int) = context.getString(id)
    init { orientation = VERTICAL }

    fun load(text: String) {
        document = LinkedHashMap(ConfigDocument.parse(text))
        path.clear()
        render()
    }

    private fun current(): Any? {
        var value: Any? = document
        path.forEach { key -> value = if (key is Int) (value as List<*>)[key] else (value as Map<*, *>)[key] }
        return value
    }

    @Suppress("UNCHECKED_CAST")
    private fun set(key: Any, value: Any?) {
        when (val parent = current()) {
            is MutableMap<*, *> -> (parent as MutableMap<String, Any?>)[key as String] = value
            is MutableList<*> -> (parent as MutableList<Any?>)[key as Int] = value
        }
        changed(ConfigDocument.dump(document))
        render()
    }

    private fun label(value: String, title: Boolean = false) = MaterialTextView(context).apply {
        text = value
        setTextAppearance(if (title) com.google.android.material.R.style.TextAppearance_Material3_TitleMedium else com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        setTextColor(color(if (title) com.google.android.material.R.attr.colorOnSurface else com.google.android.material.R.attr.colorOnSurfaceVariant))
        maxLines = if (title) 1 else 2
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun section(title: String, entries: List<Pair<Any, Any?>>) {
        addView(label(title, true).apply {
            setTextColor(color(R.attr.colorPrimary))
            setPadding(dp(8), dp(16), dp(8), dp(12))
            // N12: programmatic section titles must still be headings, so
            // screen-reader users can jump between General/Configuration/etc.
            ViewCompat.setAccessibilityHeading(this, true)
        })
        val rows = LinearLayout(context).apply { orientation = VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        val card = MaterialCardView(context).apply {
            radius = dp(24).toFloat(); cardElevation = 0f; strokeWidth = 0
            setCardBackgroundColor(color(R.attr.colorCard))
            addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (entries.isEmpty()) rows.addView(label(text(R.string.profile_editor_empty)).apply { setPadding(dp(20), dp(20), dp(20), dp(20)) })
        entries.forEach { (key, value) -> rows.addView(row(key, value)) }
    }

    private fun row(key: Any, value: Any?): View {
        val nested = value is Map<*, *> || value is List<*>
        val title = when {
            key is String -> labelFor(key)
            value is Map<*, *> -> value["name"]?.toString() ?: context.getString(R.string.profile_editor_item, (key as Int) + 1)
            path.lastOrNull() == "rules" -> value.toString().substringBefore(',')
            else -> value?.toString() ?: "null"
        }
        val summary = when {
            value is Map<*, *> -> listOfNotNull(value["type"], value["server"]).joinToString(" · ").ifBlank { context.getString(R.string.profile_editor_fields, value.size) }
            value is List<*> -> context.getString(R.string.profile_editor_items, value.size)
            path.lastOrNull() == "rules" -> value.toString().substringAfter(',')
            value is Boolean -> if (value) text(R.string.profile_editor_enabled) else text(R.string.profile_editor_disabled)
            key is String -> value?.toString() ?: "null"
            else -> context.getString(R.string.profile_editor_item, (key as Int) + 1)
        }
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(76)
            setPadding(dp(16), dp(8), dp(8), dp(8))
            val leadingIcon = ImageView(context).apply {
                setImageResource(iconFor(key.toString()))
                imageTintList = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnPrimaryContainer))
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(color(com.google.android.material.R.attr.colorPrimaryContainer)) }
                setPadding(dp(10), dp(10), dp(10), dp(10))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(leadingIcon, LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
            val copy = LinearLayout(context).apply {
                orientation = VERTICAL
                addView(label(title, true))
                addView(label(summary).apply { setPadding(0, dp(3), 0, 0) })
            }
            addView(copy, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            val action = { if (nested) { path.add(key); render() } else edit(key, value) }
            if (value is Boolean) {
                addView(MaterialSwitch(context).apply {
                    contentDescription = title
                    isChecked = value
                    setOnCheckedChangeListener { _, checked -> set(key, checked) }
                })
            } else {
                copy.isClickable = true
                copy.isFocusable = true
                copy.contentDescription = "$title, $summary"
                copy.setOnClickListener { action() }
                leadingIcon.setOnClickListener { action() }
                addView(ImageView(context).apply {
                    setImageResource(if (nested) R.drawable.rmx_arrows_arrow_right_s_line else R.drawable.rmx_edit_line)
                    imageTintList = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    // N29: this icon is itself clickable, so instead of hiding
                    // it from the tree it now carries a spoken edit action and
                    // a generated id, so audits can target it too.
                    id = View.generateViewId()
                    contentDescription = context.getString(R.string.profile_editor_edit_row, title)
                    setOnClickListener { action() }
                }, LayoutParams(dp(24), dp(24)))
            }
            addView(MaterialButton(context, null, R.attr.borderlessButtonStyle).apply {
                icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.rmx_more_2_line)
                iconTint = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
                iconPadding = 0; iconSize = dp(20)
                minWidth = 0; minimumWidth = 0; setPadding(dp(14), 0, dp(14), 0)
                contentDescription = context.getString(R.string.profile_editor_actions, title)
                setOnClickListener { showActions(this, key) }
            }, LayoutParams(dp(48), dp(48)))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun showActions(anchor: View, key: Any) {
        val parent = current()
        PopupMenu(context, anchor).apply {
            if (parent is List<*>) {
                menu.add(0, 1, 0, R.string.profile_editor_move_up).isEnabled = (key as Int) > 0
                menu.add(0, 2, 1, R.string.profile_editor_move_down).isEnabled = key < parent.lastIndex
            }
            menu.add(0, 3, 2, R.string.profile_editor_remove)
            setOnMenuItemClickListener { item ->
                if (parent is MutableList<*>) {
                    val list = parent as MutableList<Any?>
                    when (item.itemId) {
                        1 -> java.util.Collections.swap(list, key as Int, key - 1)
                        2 -> java.util.Collections.swap(list, key as Int, key + 1)
                        3 -> list.removeAt(key as Int)
                    }
                } else (parent as? MutableMap<*, *>)?.remove(key)
                changed(ConfigDocument.dump(document)); render(); true
            }
            show()
        }
    }

    private fun render() {
        removeAllViews()
        if (path.isNotEmpty()) {
            addView(MaterialButton(context, null, R.attr.borderlessButtonStyle).apply {
                text = path.joinToString(" / ") { if (it is Int) "${it + 1}" else labelFor(it.toString()) }
                icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.rmx_arrows_arrow_left_s_line)
                contentDescription = context.getString(R.string.profile_editor_back, text)
                isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { path.removeAt(path.lastIndex); render() }
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))
        }
        val current = current()
        val entries: List<Pair<Any, Any?>> = when (current) {
            is Map<*, *> -> current.entries.map { it.key!! to it.value }
            is List<*> -> current.mapIndexed { index, value -> index to value }
            else -> emptyList()
        }
        if (path.isEmpty()) {
            val basic = entries.filter { it.second !is Map<*, *> && it.second !is List<*> }
            val sections = entries - basic.toSet()
            if (basic.isNotEmpty()) section(text(R.string.profile_editor_general), basic)
            section(text(R.string.profile_editor_sections), sections)
        } else section((current as? Map<*, *>)?.get("name")?.toString()
            ?: if (path.last() is Int) context.getString(R.string.profile_editor_item, (path.last() as Int) + 1)
            else labelFor(path.last().toString()), entries)
        addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = context.getString(if (current is List<*>) R.string.profile_editor_add_item else R.string.profile_editor_add_field)
            icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.rmx_system_add_line)
            isAllCaps = false
            setOnClickListener { addEntry() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(16) })
        if (path.isEmpty()) addView(label(text(R.string.profile_visual_summary)).apply { setPadding(dp(8), dp(12), dp(8), dp(16)); maxLines = 5 })
    }

    private fun iconFor(key: String): Int = when (key) {
        "proxies", "proxy-providers" -> R.drawable.rmx_device_server_line
        "proxy-groups" -> R.drawable.rmx_development_git_branch_line
        "rules", "rule-providers" -> R.drawable.rmx_route_line
        "dns" -> R.drawable.rmx_business_global_line
        else -> R.drawable.rmx_system_settings_3_line
    }

    private fun labelFor(key: String): String = when (key) {
        "proxies" -> text(R.string.profile_section_proxies)
        "proxy-groups" -> text(R.string.profile_section_groups)
        "rules" -> text(R.string.profile_section_rules)
        "dns" -> text(R.string.profile_section_dns)
        "proxy-providers" -> text(R.string.profile_section_providers)
        "rule-providers" -> text(R.string.profile_section_rule_providers)
        "mode" -> text(R.string.profile_editor_mode)
        "log-level" -> text(R.string.profile_editor_log_level)
        "mixed-port" -> text(R.string.profile_editor_mixed_port)
        "allow-lan" -> text(R.string.profile_editor_allow_lan)
        "ipv6" -> "IPv6"
        else -> key
    }

    private fun edit(key: Any, old: Any?) {
        if (path.lastOrNull() == "rules" && old is String) {
            editRule(old) { set(key, it) }
            return
        }
        val options = when (key.takeIf { path.isEmpty() }) {
            "mode" -> arrayOf("rule", "global", "direct")
            "log-level" -> arrayOf("silent", "error", "warning", "info", "debug")
            else -> null
        }
        if (options != null && old is String) {
            MaterialAlertDialogBuilder(context).setTitle(labelFor(key.toString()))
                .setSingleChoiceItems(options, options.indexOf(old)) { dialog, index -> set(key, options[index]); dialog.dismiss() }
                .setNegativeButton(android.R.string.cancel, null).show()
            return
        }
        val input = TextInputEditText(context).apply {
            setText(old?.toString().orEmpty())
            inputType = if (old is Number) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val box = TextInputLayout(context).apply { hint = labelFor(key.toString()); setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
        val dialog = MaterialAlertDialogBuilder(context).setTitle("编辑字段").setView(box)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                runCatching {
                    val text = input.text.toString()
                    val value: Any = when (old) {
                        is Int -> text.toInt()
                        is Long -> text.toLong()
                        is Number -> text.toDouble().also { require(it.isFinite()) }
                        else -> text
                    }
                    set(key, value)
                }.onSuccess { dialog.dismiss() }.onFailure { box.error = "值无效：${it.message}" }
            }
        }
        dialog.show()
    }

    private fun editRule(source: String, save: (String) -> Unit) {
        val parts = source.split(',').map(String::trim)
        val flags = parts.takeLastWhile { it.lowercase() in setOf("no-resolve", "src") }
        val rule = parts.dropLast(flags.size)
        val fields = LinearLayout(context).apply {
            orientation = VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
        fun field(label: String, value: String): TextInputEditText {
            val input = TextInputEditText(context).apply { setSingleLine(); setText(value) }
            fields.addView(TextInputLayout(context).apply { hint = label; addView(input) })
            return input
        }
        val type = field("规则类型", rule.firstOrNull().orEmpty())
        val payload = field("匹配内容（MATCH 留空）", rule.drop(1).dropLast(1).joinToString(","))
        val target = field("目标策略", rule.lastOrNull().orEmpty())
        val noResolve = MaterialSwitch(context).apply { text = "no-resolve"; isChecked = "no-resolve" in flags }
        fields.addView(noResolve)
        val dialog = MaterialAlertDialogBuilder(context).setTitle("编辑分流规则").setView(fields)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                val kind = type.text.toString().trim().uppercase()
                val value = payload.text.toString().trim()
                val destination = target.text.toString().trim()
                if (kind.isBlank() || ',' in kind) { type.error = "请输入规则类型"; return@setOnClickListener }
                if (destination.isBlank() || ',' in destination) { target.error = "请输入目标策略"; return@setOnClickListener }
                if (kind != "MATCH" && value.isBlank()) { payload.error = "请输入匹配内容"; return@setOnClickListener }
                val result = if (kind == "MATCH") "$kind,$destination" else "$kind,$value,$destination"
                save(result + (if (noResolve.isChecked) ",no-resolve" else "") + (if ("src" in flags) ",src" else ""))
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    @Suppress("UNCHECKED_CAST")
    private fun addEntry() {
        if (path.lastOrNull() == "rules" && current() is MutableList<*>) {
            editRule("DOMAIN-SUFFIX,example.com,DIRECT") { rule ->
                (current() as MutableList<Any?>).add(rule)
                changed(ConfigDocument.dump(document)); render()
            }
            return
        }
        val container = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val keyInput = TextInputEditText(context).apply { hint = "字段名" }
        val valueInput = TextInputEditText(context).apply { hint = "值（YAML：字符串、数字、true、{} 或 []）" }
        if (current() is Map<*, *>) container.addView(keyInput)
        container.addView(valueInput)
        val dialog = MaterialAlertDialogBuilder(context).setTitle("添加")
            .setView(container).setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                runCatching {
                    val value = ConfigDocument.parse("value: ${valueInput.text}")["value"]
                    when (val parent = current()) {
                        is MutableMap<*, *> -> {
                            val key = keyInput.text.toString().trim()
                            require(key.isNotEmpty() && !parent.containsKey(key)) { "字段名为空或已存在" }
                            (parent as MutableMap<String, Any?>)[key] = value
                        }
                        is MutableList<*> -> (parent as MutableList<Any?>).add(value)
                    }
                    changed(ConfigDocument.dump(document)); render()
                }.onSuccess { dialog.dismiss() }.onFailure { valueInput.error = it.message }
            }
        }
        dialog.show()
    }
}
