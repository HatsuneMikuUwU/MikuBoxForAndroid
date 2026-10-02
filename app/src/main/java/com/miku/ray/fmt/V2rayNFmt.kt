package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.dto.V2rayNShareItem
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils

object V2rayNFmt : FmtBase() {
    fun parse(lines: List<String>, subId: String): List<ProfileItem> {
        val items = lines.mapNotNull(::parseShareItem)
        val itemsById = linkedMapOf<String, V2rayNShareItem>()
        items.forEach { item ->
            item.IndexId?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
                itemsById.putIfAbsent(id, item)
            }
        }

        return items.map { item ->
            item.toProfileItem().apply {
                if (item.ProtoExtraObj?.SubChildItems == "self") {
                    policyGroupSubscriptionId = subId
                }

                val childIds = item.ProtoExtraObj?.ChildItems?.takeIf { it.isNotNullEmpty() }
                if (childIds != null) {
                    val childRemarks = childIds.split(",")
                    .mapNotNull { childId -> itemsById[childId.trim()]?.Remarks }
                    .filter { it.isNotNullEmpty() }

                    if (childRemarks.isNotEmpty()) {
                        when (configType) {
                            EConfigType.POLICYGROUP -> {
                                policyGroupFilter = childRemarks.joinToString(
                                    separator = "|",
                                    prefix = "^(",
                                    postfix = ")$",
                                ) { Regex.escape(it) }
                            }
                            EConfigType.PROXYCHAIN -> {
                                proxyChainProfiles = childRemarks.joinToString(",")
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun parseShareItem(str: String): V2rayNShareItem? = try {
        JsonUtil.fromJson(
            Utils.decode(str.substringAfterLast('/')),
            V2rayNShareItem::class.java,
        )
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to parse V2rayN share item", e)
        null
    }
}
