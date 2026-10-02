package com.miku.ray.contracts

interface BaseAdapterListener {
    fun onEdit(guid: String, position: Int)

    fun onRemove(guid: String, position: Int)

    fun onShare(url: String)

    fun onRefreshData()
}
