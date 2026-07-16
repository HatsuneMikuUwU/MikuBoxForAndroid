package top.uwu.mikubox.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import top.uwu.mikubox.R

/** MikuRay-style list of the nodes inside one selector group. */
class ProxyNodeAdapter(
    private val onSelect: (String) -> Unit,
) : RecyclerView.Adapter<ProxyNodeAdapter.VH>() {

    /** [delay]: -3 testing, -2 untested, -1 timeout, otherwise milliseconds. */
    data class Node(val name: String, val type: String, val delay: Int, val selected: Boolean)

    private var nodes: List<Node> = emptyList()

    @SuppressWarnings("NotifyDataSetChanged")
    fun submit(list: List<Node>) {
        nodes = list
        notifyDataSetChanged()
    }

    /** Streams a single node's latency result without rebuilding the whole list. */
    fun updateDelay(name: String, delay: Int) {
        val index = nodes.indexOfFirst { it.name == name }
        if (index < 0) return
        nodes = nodes.toMutableList().also { it[index] = it[index].copy(delay = delay) }
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_proxy_node, parent, false))

    override fun getItemCount(): Int = nodes.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(nodes[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tv_name)
        private val type: TextView = itemView.findViewById(R.id.tv_type)
        private val delay: TextView = itemView.findViewById(R.id.tv_delay)
        private val selected: ImageView = itemView.findViewById(R.id.iv_selected)
        private val selectedBar: View = itemView.findViewById(R.id.selected_view)

        fun bind(node: Node) {
            val ctx = itemView.context
            name.text = node.name
            type.text = node.type
            selected.visibility = if (node.selected) View.VISIBLE else View.INVISIBLE
            selectedBar.visibility = if (node.selected) View.VISIBLE else View.INVISIBLE
            when {
                node.delay == -3 -> {
                    delay.text = "···"
                    delay.setTextColor(ContextCompat.getColor(ctx, R.color.miku_orange))
                }
                node.delay == -2 -> {
                    delay.text = ""
                }
                node.delay < 0 -> {
                    delay.text = ctx.getString(R.string.proxies_delay_timeout)
                    delay.setTextColor(ContextCompat.getColor(ctx, R.color.miku_tertiary))
                }
                else -> {
                    delay.text = ctx.getString(R.string.proxies_delay_ms, node.delay)
                    delay.setTextColor(ContextCompat.getColor(ctx, delayColor(node.delay)))
                }
            }
            itemView.setOnClickListener { onSelect(node.name) }
        }

        private fun delayColor(ms: Int): Int = when {
            ms < 200 -> R.color.ping_green
            ms < 500 -> R.color.miku_orange
            else -> R.color.miku_tertiary
        }
    }
}
