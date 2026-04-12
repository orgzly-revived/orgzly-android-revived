package com.orgzly.android.ui.capture

import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.settings.SettingsFragment

class CaptureTemplatesFragment : PreferenceFragmentCompat() {

    private var listener: Listener? = null

    interface Listener {
        fun onCaptureTemplateEdit(templateId: String?)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = activity as? Listener
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        loadTemplates()
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsFragment.Listener)?.onTitleChange(getString(R.string.capture_templates))
        loadTemplates()
    }

    private fun loadTemplates() {
        preferenceScreen.removeAll()

        val templates = AppPreferences.captureTemplates(requireContext())

        for ((index, template) in templates.withIndex()) {
            preferenceScreen.addPreference(Preference(requireContext()).apply {
                title = template.getDisplayName(
                    getString(R.string.capture_template_numbered, index + 1)
                )
                summary = buildDetailsString(template)
                setOnPreferenceClickListener {
                    listener?.onCaptureTemplateEdit(template.id)
                    true
                }
            })
        }

        if (templates.isEmpty()) {
            preferenceScreen.addPreference(Preference(requireContext()).apply {
                title = getString(R.string.no_capture_templates)
                isEnabled = false
            })
        }

        preferenceScreen.addPreference(Preference(requireContext()).apply {
            title = getString(R.string.new_capture_template)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_add)
            setOnPreferenceClickListener {
                listener?.onCaptureTemplateEdit(null)
                true
            }
        })
    }

    private fun buildDetailsString(template: CaptureTemplate): String {
        return buildString {
            if (template.targetBook.isNotBlank()) append(template.targetBook)
            if (template.state.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(template.state)
            }
            if (template.tags.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(template.tags)
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        val FRAGMENT_TAG: String = CaptureTemplatesFragment::class.java.name

        fun getInstance(): CaptureTemplatesFragment = CaptureTemplatesFragment()
    }
}
