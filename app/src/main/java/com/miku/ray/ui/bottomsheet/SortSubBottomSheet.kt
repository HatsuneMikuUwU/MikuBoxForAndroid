package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager

class SortSubBottomSheet : BaseBottomSheetFragment() {

    interface OnSortSubOptionClickListener {
        fun onSortSubOptionClicked(order: Int)
    }

    private var mListener: OnSortSubOptionClickListener? = null
    private var currentOrder: Int = ORDER_ORIGIN

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSortSubOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnSortSubOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentOrder = MmkvManager.decodeSettingsInt(AppConfig.PREF_SUB_SORT_ORDER, ORDER_ORIGIN)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_sort_sub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(view)
        loadBannerSheet(view)

        val checkOrigin  = view.findViewById<CheckedTextView>(R.id.action_sort_sub_origin)
        val checkAdded   = view.findViewById<CheckedTextView>(R.id.action_sort_sub_added)
        val checkUpdated = view.findViewById<CheckedTextView>(R.id.action_sort_sub_updated)

        fun updateChecks(order: Int) {
            checkOrigin?.isChecked  = order == ORDER_ORIGIN
            checkAdded?.isChecked   = order == ORDER_BY_ADDED
            checkUpdated?.isChecked = order == ORDER_BY_UPDATED
        }
        updateChecks(currentOrder)

        view.findViewById<View>(R.id.card_sort_sub_origin)?.setOnClickListener {
            view.findViewById<View>(R.id.action_sort_sub_origin)?.performClick()
        }
        view.findViewById<View>(R.id.card_sort_sub_added)?.setOnClickListener {
            view.findViewById<View>(R.id.action_sort_sub_added)?.performClick()
        }
        view.findViewById<View>(R.id.card_sort_sub_updated)?.setOnClickListener {
            view.findViewById<View>(R.id.action_sort_sub_updated)?.performClick()
        }

        val orderClickListener = View.OnClickListener { v ->
            val newOrder = when (v.id) {
                R.id.action_sort_sub_origin  -> ORDER_ORIGIN
                R.id.action_sort_sub_added   -> ORDER_BY_ADDED
                R.id.action_sort_sub_updated -> ORDER_BY_UPDATED
                else -> currentOrder
            }
            MmkvManager.encodeSettings(AppConfig.PREF_SUB_SORT_ORDER, newOrder)
            currentOrder = newOrder
            updateChecks(newOrder)
            mListener?.onSortSubOptionClicked(newOrder)
            dismiss()
        }

        listOf(
            R.id.action_sort_sub_origin,
            R.id.action_sort_sub_added,
            R.id.action_sort_sub_updated
        ).forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(orderClickListener)
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "SortSubBottomSheet"

        const val ORDER_ORIGIN     = 0
        const val ORDER_BY_ADDED   = 1
        const val ORDER_BY_UPDATED = 2

        fun newInstance(): SortSubBottomSheet {
            return SortSubBottomSheet()
        }

        fun <T> sorted(list: List<T>, addedTime: (T) -> Long, lastUpdated: (T) -> Long): List<T> {
            val order = MmkvManager.decodeSettingsInt(AppConfig.PREF_SUB_SORT_ORDER, ORDER_ORIGIN)
            return when (order) {
                ORDER_BY_ADDED   -> list.sortedByDescending(addedTime)
                ORDER_BY_UPDATED -> list.sortedByDescending(lastUpdated)
                else             -> list
            }
        }
    }
}
