package com.orgzly.android.ui.note

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.annotation.LayoutRes

class NotePropertySuggestionAdapter(
    context: Context,
    @LayoutRes resource: Int,
): ArrayAdapter<String>(context, resource, mutableListOf<String>()) {

    private val dictionary = mutableListOf<String>()
    private var filtered = FilteredSuggestions.EMPTY

    fun updateDictionary(items: List<String>) {
        dictionary.clear()
        dictionary.addAll(items)
        filtered = FilteredSuggestions.EMPTY
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return NotePropertySuggestionAdapterFilter()
    }

    override fun getCount(): Int {
        return filtered.suggestions.size
    }

    override fun getItemId(position: Int): Long {
        return filtered.suggestions[position].hashCode().toLong()
    }

    override fun getItem(position: Int): String {
        return (if (filtered.hasLeadingPlus) "+" else "") +
                filtered.suggestions[position]
    }

    inner class NotePropertySuggestionAdapterFilter: Filter() {
        override fun performFiltering(content: CharSequence?): FilterResults {
            if (content == null) return FilterResults().apply {
                count = 0
                values = FilteredSuggestions.EMPTY
            }

            val altered = content.trim('+').toString()
            val filtered = dictionary.filter {
                it.lowercase().startsWith(altered.lowercase())
            }

            val results = FilterResults()
            results.values = FilteredSuggestions(
                filtered,
                content.startsWith('+')
            )
            results.count = filtered.size
            return results
        }

        override fun publishResults(
            content: CharSequence?,
            filterResults: FilterResults
        ) {
            filtered = filterResults.values as FilteredSuggestions
            notifyDataSetChanged()
        }
    }

    private data class FilteredSuggestions(
        val suggestions: List<String>,
        val hasLeadingPlus: Boolean
    ) {

        companion object {
            val EMPTY = FilteredSuggestions(
                emptyList(),
                false
            )
        }

    }
}