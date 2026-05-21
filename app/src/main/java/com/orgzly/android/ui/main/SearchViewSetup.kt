package com.orgzly.android.ui.main

import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentActivity
import com.orgzly.R
import com.orgzly.android.db.entity.Book
import com.orgzly.android.query.Condition
import com.orgzly.android.query.Query
import com.orgzly.android.query.user.DottedQueryBuilder
import com.orgzly.android.ui.DisplayManager
import com.orgzly.android.ui.notes.book.BookFragment


/**
 * SearchView setup and query text listeners.
 * TODO: http://developer.android.com/training/search/setup.html
 */
fun FragmentActivity.setupSearchView(menu: Menu) {
    val activity = this

    val searchItem = menu.findItem(R.id.search_view)

    // Hide FAB while searching
    searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
            Fab.hide(activity)
            return true
        }

        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
            Fab.show(activity)
            return true
        }
    })

    val searchView = searchItem.actionView as SearchView

    searchView.queryHint = getString(R.string.search_hint)

    searchView.setOnSearchClickListener {
        // Make search as wide as possible
        searchView.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
    }

    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextChange(str: String?): Boolean {
            return false
        }

        override fun onQueryTextSubmit(str: String): Boolean {
            // Close search
            searchItem.collapseActionView()
            DisplayManager.displayQuery(supportFragmentManager, str.trim { it <= ' ' })
            return true
        }
    })
}
