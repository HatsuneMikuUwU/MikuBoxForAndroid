package com.miku.ray.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.miku.ray.helper.CustomDividerItemDecoration

abstract class BaseFragment<VB : ViewBinding> : Fragment() {
    private var _binding: VB? = null
    protected val binding: VB
    get() = _binding!!

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun addCustomDividerToRecyclerView(recyclerView: RecyclerView, drawableResId: Int, orientation: Int = DividerItemDecoration.VERTICAL) {
        val drawable = ContextCompat.getDrawable(requireContext(), drawableResId)
        requireNotNull(drawable) { "Drawable resource not found" }

        val dividerItemDecoration = CustomDividerItemDecoration(drawable, orientation)

        recyclerView.addItemDecoration(dividerItemDecoration)
    }
}
