package com.miku.ray.ui.main

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.MikuRouting
import com.miku.ray.R
import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.util.getColorAttr
import com.miku.ray.util.Utils

/**
 * The body of the global-exit picker: a search field, category pills
 * (all/groups/nodes), region pills derived from what is listed, and the
 * sectioned result list. Everything filters in memory; picking an entry hands
 * the name to [onPick] and the dialog decides whether to stay open.
 */
class ExitPickerPanel(
    context: Context,
    private val state: MikuRouting.State,
    private val measured: Map<String, String>,
    private val onPick: (MikuRouting.Exit) -> Unit,
) : LinearLayout(context) {

    private enum class Category { ALL, GROUPS, NODES }

    private sealed class Row {
        class Header(val title: String) : Row()
        class Item(val exit: MikuRouting.Exit, val region: String) : Row()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // Host palette, resolved once — the dialog overlay can tint differently.
    private val accent = context.getColorAttr(androidx.appcompat.R.attr.colorPrimary)
    private val onAccent = context.getColorAttr(com.google.android.material.R.attr.colorOnPrimary)
    private val onSurface = context.getColorAttr(com.google.android.material.R.attr.colorOnSurface)
    private val variant = context.getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val fieldColor = context.getColorAttr(com.google.android.material.R.attr.colorSurfaceContainerHighest)
    private val delayGreen = ContextCompat.getColor(context, R.color.colorPing)

    private var category = Category.ALL
    private var region = ""
    private val rows = mutableListOf<Row>()

    private val search = EditText(context)
    private val regionScroll = HorizontalScrollView(context)
    private val regionRow = LinearLayout(context)
    private val list = RecyclerView(context)
    private val empty = TextView(context)
    private val adapter = Adapter()

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(4), dp(12), 0)

        search.apply {
            hint = context.getString(R.string.mihomo_exit_search)
            setSingleLine()
            textSize = 15f
            setTextColor(onSurface)
            setHintTextColor(variant)
            background = fill(fieldColor, 22)
            val icon = ContextCompat.getDrawable(context, RemixR.drawable.rmx_search_line)?.mutate()
            TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(variant))
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            compoundDrawablePadding = dp(10)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) = refilter()
            })
        }
        addView(search, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(8), 0, dp(8))
        })

        addView(categoryRow(), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(8))
        })

        regionScroll.apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            visibility = GONE
        }
        regionRow.orientation = LinearLayout.HORIZONTAL
        regionScroll.addView(regionRow)
        addView(regionScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(8))
        })

        list.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ExitPickerPanel.adapter
            clipToPadding = false
            setPadding(0, 0, 0, dp(8))
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        empty.apply {
            setText(R.string.mihomo_exit_empty)
            textSize = 14f
            setTextColor(variant)
            gravity = Gravity.CENTER
            setPadding(0, dp(32), 0, dp(32))
            visibility = GONE
        }
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        refilter()
    }

    /** Rebuilds rows and the region pills from the current filters. */
    private fun refilter() {
        val text = search.text?.toString()?.trim()?.lowercase().orEmpty()
        val matches = state.options.filter { text.isEmpty() || it.name.lowercase().contains(text) }
        val wantedGroups = category != Category.NODES
        val wantedNodes = category != Category.GROUPS

        // Region pills describe what the search + category filters currently
        // list, so they never offer a region that would show nothing.
        val counted = LinkedHashMap<String, Int>()
        matches.forEach { exit ->
            val code = ExitRegion.resolve(exit.name, measured)
            if (code.isNotEmpty()) counted[code] = (counted[code] ?: 0) + 1
        }

        rows.clear()
        var shown = 0
        if (wantedGroups) {
            val groups = matches.filter { it.group && (region.isEmpty() || ExitRegion.resolve(it.name, measured) == region) }
            if (groups.isNotEmpty()) {
                rows += Row.Header(context.getString(R.string.mihomo_exit_section_groups))
                groups.forEach { rows += Row.Item(it, ExitRegion.resolve(it.name, measured)) }
                shown += groups.size
            }
        }
        if (wantedNodes) {
            val nodes = matches.filter { !it.group && (region.isEmpty() || ExitRegion.resolve(it.name, measured) == region) }
            if (nodes.isNotEmpty()) {
                rows += Row.Header(context.getString(R.string.mihomo_exit_section_nodes))
                nodes.forEach { rows += Row.Item(it, ExitRegion.resolve(it.name, measured)) }
                shown += nodes.size
            }
        }
        adapter.notifyDataSetChanged()
        list.layoutManager?.scrollToPosition(0)
        empty.visibility = if (shown == 0) VISIBLE else GONE
        list.visibility = if (shown == 0) GONE else VISIBLE
        rebuildRegionPills(counted)
    }

    /** Region pills reflect what the search + category filters currently list. */
    private fun rebuildRegionPills(counted: LinkedHashMap<String, Int>) {
        regionRow.removeAllViews()
        if (counted.size < 2) {
            regionScroll.visibility = GONE
            if (region.isNotEmpty()) { region = ""; refilter() }
            return
        }
        regionScroll.visibility = VISIBLE
        regionRow.addView(pill(context.getString(R.string.mihomo_exit_filter_all), region.isEmpty()) { region = ""; refilter() })
        counted.entries.sortedByDescending { it.value }.forEach { (code, count) ->
            val label = "${ExitRegion.label(code)} · $count"
            regionRow.addView(pill(label, region == code) { region = if (region == code) "" else code; refilter() })
        }
    }

    private fun categoryRow(): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val entries = listOf(
            Category.ALL to context.getString(R.string.mihomo_exit_filter_all),
            Category.GROUPS to context.getString(R.string.mihomo_exit_filter_groups),
            Category.NODES to context.getString(R.string.mihomo_exit_filter_nodes),
        )
        val pills = entries.map { (value, label) -> pill(label, value == category) { } }
        pills.forEach { row.addView(it) }
        pills.forEachIndexed { index, view ->
            view.setOnClickListener {
                category = entries[index].first
                pills.forEachIndexed { i, p -> p.isSelected = i == index }
                refilter()
            }
        }
        return row
    }

    private fun pill(label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
            isSelected = selected
            typeface = Typeface.create(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (selected) onAccent else variant)
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 31)),
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_selected), fill(accent, 17))
                    addState(intArrayOf(), fill(fieldColor, 17))
                },
                null,
            )
            setOnClickListener { onClick() }
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)).apply { marginEnd = dp(8) }
        }

    private fun fill(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int) =
            if (rows[position] is Row.Header) TYPE_HEADER else TYPE_ITEM

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == TYPE_HEADER) HeaderHolder(makeHeaderText()) else ItemHolder()

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).bind(row)
                is Row.Item -> (holder as ItemHolder).bind(row)
            }
        }
    }

    private fun makeHeaderText() = TextView(context).apply {
        textSize = 13f
        setTextColor(variant)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(4))
        setTypeface(typeface, Typeface.BOLD)
        // N12: section titles must be real headings so screen-reader users can
        // jump between them instead of wading through the whole list.
        ViewCompat.setAccessibilityHeading(this, true)
    }

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(row: Row.Header) { (itemView as TextView).text = row.title }
    }

    /** Row construction lives on the panel so holders can build their views safely. */
    private val leadId = View.generateViewId()
    private val nameId = View.generateViewId()
    private val delayId = View.generateViewId()
    private val checkId = View.generateViewId()

    private fun makeItemRow(): LinearLayout {
        val lead = FrameLayout(context).apply { id = leadId }
        val name = TextView(context).apply {
            id = nameId
            textSize = 15f
            setTextColor(onSurface)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val delay = TextView(context).apply {
            id = delayId
            textSize = 12f
        }
        val check = ImageView(context).apply {
            id = checkId
            setImageResource(RemixR.drawable.rmx_checkbox_circle_line)
            setColorFilter(accent)
        }
        val r = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            minimumHeight = dp(52)
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 31)),
                ColorDrawable(android.graphics.Color.TRANSPARENT),
                null,
            )
        }
        r.addView(lead, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(10) })
        r.addView(name, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        delay.layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
        r.addView(delay)
        r.addView(check, LinearLayout.LayoutParams(dp(20), dp(20)))
        return r
    }

    /** One exit entry: lead slot (group icon / flag), name, live delay, check. */
    private inner class ItemHolder : RecyclerView.ViewHolder(makeItemRow()) {
        private val row = itemView as LinearLayout
        private val lead = itemView.findViewById<FrameLayout>(leadId)
        private val name = itemView.findViewById<TextView>(nameId)
        private val delay = itemView.findViewById<TextView>(delayId)
        private val check = itemView.findViewById<ImageView>(checkId)

        fun bind(item: Row.Item) {
            val exit = item.exit
            val selected = exit.name == state.exit
            // The lead slot already shows the resolved flag; drop a leading
            // flag emoji from the name itself so it is not shown twice.
            name.text = if (exit.group) exit.name else exit.name.replace(LEADING_FLAG, "")
            name.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            name.setTextColor(if (selected) accent else onSurface)
            check.visibility = if (selected) VISIBLE else GONE
            delay.visibility = if (exit.delay > 0) VISIBLE else GONE
            delay.text = "${exit.delay}ms"
            delay.setTextColor(delayGreen)
            // N11: selection used to be announced only through the bold
            // typeface and the bare checkmark image — invisible to a screen
            // reader. Carry it as a real state, and drop the redundant
            // unlabeled checkmark image from the tree.
            row.isSelected = selected
            ViewCompat.setStateDescription(
                row,
                if (selected) context.getString(R.string.a11y_selected) else null,
            )
            check.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            lead.removeAllViews()
            when {
                exit.group -> lead.addView(icon(RemixR.drawable.rmx_group_line))
                item.region.isNotEmpty() -> lead.addView(TextView(context).apply {
                    text = Utils.countryCodeToFlag(item.region)
                    textSize = 17f
                    gravity = Gravity.CENTER
                }, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                else -> lead.addView(icon(RemixR.drawable.rmx_global_line))
            }
            row.setOnClickListener { onPick(exit) }
        }

        private fun icon(res: Int) = ImageView(context).apply {
            setImageResource(res)
            setColorFilter(variant)
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1

        /** A flag emoji pair at the start of a node name (code-point regex). */
        val LEADING_FLAG = Regex("^[\\x{1F1E6}-\\x{1F1FF}]{2}\\s*")
    }
}
