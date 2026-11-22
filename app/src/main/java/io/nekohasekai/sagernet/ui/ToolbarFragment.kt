package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import io.nekohasekai.sagernet.R

interface NavigationHost {
    fun showNavigationSheet()
}

open class ToolbarFragment : Fragment {

    constructor() : super()
    constructor(@LayoutRes contentLayoutId: Int) : super(contentLayoutId)

    lateinit var toolbar: Toolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)  
        val foundToolbar = view.findViewById<Toolbar>(R.id.toolbar) ?: return
        toolbar = foundToolbar
        
        toolbar.setNavigationIcon(R.drawable.ic_navigation_menu)
        
        toolbar.setNavigationOnClickListener {
            val act = activity
            if (act is NavigationHost) {
                act.showNavigationSheet()
            } else {
                act?.onBackPressed()
            }
        }
    }

    open fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = false
    open fun onBackPressed(): Boolean = false
}