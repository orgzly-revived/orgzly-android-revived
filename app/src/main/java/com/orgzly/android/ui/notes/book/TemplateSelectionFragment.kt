package com.orgzly.android.ui.notes.book

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.DialogFragment
import com.orgzly.R
import com.orgzly.android.Constants
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.note.NotePayload

class TemplateSelectionFragment (
    private val listener: OnTemplateSelectedListener,
    private val templates: List<NotePayload>)
    : DialogFragment() {


    interface OnTemplateSelectedListener {
        fun onTemplateSelected(notePayload: NotePayload)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val listView = ListView(requireContext())

        // Create an adapter for the ListView
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            templates.map {
                it.properties.get(
                Constants.KEY_PROPERTY_TEMPLATE)
            }
        )
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            // Notify the listener when an item is selected
            listener.onTemplateSelected(templates[position])
            dismiss()
        }

        builder.setView(listView)
            .setTitle(getString(R.string.choose_template))
            .setNegativeButton(getString(R.string.cancel_template_selection)) { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }
}
