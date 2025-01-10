package com.orgzly.android.ui.notes.book

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import com.orgzly.R
import com.orgzly.android.Constants
import com.orgzly.android.ui.note.NotePayload
import com.orgzly.android.ui.util.KeyboardUtils

class TemplateSelectionFragment (
    private val listener: OnTemplateSelectedListener,
    private val templates: List<NotePayload>)
    : DialogFragment() {


    interface OnTemplateSelectedListener {
        fun onTemplateSelected(notePayload: NotePayload)
    }

    private fun noTemplatesAvailableDialog(): Dialog {
        var textView = MaterialTextView(requireContext())
        textView.setText(getString(R.string.no_template_error_msg, Constants.KEY_PROPERTY_TEMPLATE))
        textView.gravity = Gravity.CENTER
        textView.top.plus(20)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.no_templates_available))
            .setView(textView)
            .setNegativeButton(getString(R.string.acknowledge_no_templates_available)) { _, _ ->
                // Closing due to used android:windowSoftInputMode="stateUnchanged"
                KeyboardUtils.closeSoftKeyboard(activity)
            }
            .create()
        return dialog
    }

    private fun templatesAvailableDialog(): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val listView = ListView(requireContext())

        // Create an adapter for the ListView
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            templates.map {
                it.properties.get(
                    Constants.KEY_PROPERTY_TEMPLATE
                )
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return if (templates.isEmpty())
            noTemplatesAvailableDialog()
        else templatesAvailableDialog()
    }
}
