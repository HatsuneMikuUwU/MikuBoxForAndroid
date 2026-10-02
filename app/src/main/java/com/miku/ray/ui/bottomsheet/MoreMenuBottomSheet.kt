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

class MoreMenuBottomSheet : BaseBottomSheetFragment() {

    interface OnMoreOptionClickListener {
        fun onMoreOptionClicked(viewId: Int)
    }

    private var mListener: OnMoreOptionClickListener? = null

    private var currentOrder: Int = ORDER_ORIGIN
    private var subscriptionId: String = ""

    private fun orderKey(): String {
        val subId = subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        return "${AppConfig.PREF_SERVER_ORDER}_$subId"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnMoreOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnMoreOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscriptionId = arguments?.getString(ARG_SUBSCRIPTION_ID).orEmpty()
        currentOrder = MmkvManager.decodeSettingsInt(orderKey(), ORDER_ORIGIN)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_more_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(view)
        loadBannerSheet(view)

        val checkOrigin = view.findViewById<CheckedTextView>(R.id.action_order_origin)
        val checkName   = view.findViewById<CheckedTextView>(R.id.action_order_by_name)
        val checkDelay  = view.findViewById<CheckedTextView>(R.id.action_order_by_delay)

        fun updateChecks(order: Int) {
            checkOrigin?.isChecked = order == ORDER_ORIGIN
            checkName?.isChecked   = order == ORDER_BY_NAME
            checkDelay?.isChecked  = order == ORDER_BY_DELAY
        }
        updateChecks(currentOrder)

        val isTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRAFFIC_ENABLED) == true
        view.findViewById<View>(R.id.reset_traffic)?.visibility = if (isTrafficEnabled) View.VISIBLE else View.GONE

        val hasTestResults = MmkvManager.hasAnyTestDelayResults()
        view.findViewById<View>(R.id.clear_test_results)?.visibility = if (hasTestResults) View.VISIBLE else View.GONE
        val hasCountryCodes = MmkvManager.hasAnyCountryCodeResults()
        view.findViewById<View>(R.id.clear_country_codes)?.visibility = if (hasCountryCodes) View.VISIBLE else View.GONE

        val isScrollButtonsHidden = MmkvManager.decodeSettingsBool(AppConfig.PREF_HIDE_SCROLL_BUTTONS, false)
        val hasSelectedServer = !MmkvManager.getSelectServer().isNullOrEmpty()
        view.findViewById<View>(R.id.action_scroll_to_selected)?.visibility =
        if (isScrollButtonsHidden && hasSelectedServer) View.VISIBLE else View.GONE

        val isQuickActionsEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_QUICK_ACTIONS, false)
        listOf(
            R.id.sub_update,
            R.id.country_code_all,
            R.id.tcping_all,
            R.id.real_ping_all
        ).forEach { id ->
            view.findViewById<View>(id)?.visibility = if (isQuickActionsEnabled) View.GONE else View.VISIBLE
        }

        view.findViewById<View>(R.id.card_order_origin)?.setOnClickListener {
            view.findViewById<View>(R.id.action_order_origin)?.performClick()
        }
        view.findViewById<View>(R.id.card_order_by_name)?.setOnClickListener {
            view.findViewById<View>(R.id.action_order_by_name)?.performClick()
        }
        view.findViewById<View>(R.id.card_order_by_delay)?.setOnClickListener {
            view.findViewById<View>(R.id.action_order_by_delay)?.performClick()
        }

        val clickListener = View.OnClickListener { v ->
            mListener?.onMoreOptionClicked(v.id)
            dismiss()
        }

        val orderClickListener = View.OnClickListener { v ->
            val newOrder = when (v.id) {
                R.id.action_order_origin   -> ORDER_ORIGIN
                R.id.action_order_by_name  -> ORDER_BY_NAME
                R.id.action_order_by_delay -> ORDER_BY_DELAY
                else -> currentOrder
            }
            MmkvManager.encodeSettings(orderKey(), newOrder)
            currentOrder = newOrder
            updateChecks(newOrder)
            mListener?.onMoreOptionClicked(v.id)
            dismiss()
        }

        listOf(
            R.id.action_order_origin,
            R.id.action_order_by_name,
            R.id.action_order_by_delay
        ).forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(orderClickListener)
        }

        listOf(
            R.id.service_restart,
            R.id.del_all_config,
            R.id.del_duplicate_config,
            R.id.del_invalid_config,
            R.id.export_all,
            R.id.export_group_file,
            R.id.real_ping_all,
            R.id.country_code_all,
            R.id.tcping_all,
            R.id.clear_test_results,
            R.id.clear_country_codes,
            R.id.sub_update,
            R.id.reset_traffic,
            R.id.action_scroll_to_selected
        ).forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(clickListener)
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "MoreMenuBottomSheet"

        const val ORDER_ORIGIN   = 0
        const val ORDER_BY_NAME  = 1
        const val ORDER_BY_DELAY = 2

        private const val ARG_SUBSCRIPTION_ID = "subscriptionId"

        fun newInstance(subscriptionId: String): MoreMenuBottomSheet {
            return MoreMenuBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SUBSCRIPTION_ID, subscriptionId)
                }
            }
        }
    }
}
