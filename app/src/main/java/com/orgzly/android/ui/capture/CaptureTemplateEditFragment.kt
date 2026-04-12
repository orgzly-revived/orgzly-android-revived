package com.orgzly.android.ui.capture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.databinding.FragmentCaptureTemplateBinding

class CaptureTemplateEditFragment : Fragment() {

    private lateinit var binding: FragmentCaptureTemplateBinding
    var listener: Listener? = null

    interface Listener {
        fun onTemplateSaved()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCaptureTemplateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val templateId = arguments?.getString(ARG_TEMPLATE_ID)
        val existingTemplate = if (templateId != null) {
            AppPreferences.captureTemplates(requireContext()).find { it.id == templateId }
        } else null

        if (existingTemplate != null) {
            binding.templateDescription.setText(existingTemplate.description)
            binding.templateTitle.setText(existingTemplate.title)
            binding.templateContent.setText(existingTemplate.content)
            binding.templateTargetBook.setText(existingTemplate.targetBook)
            binding.templateState.setText(existingTemplate.state)
            binding.templatePriority.setText(existingTemplate.priority)
            binding.templateTags.setText(existingTemplate.tags)
            binding.templateScheduled.isChecked = existingTemplate.isScheduled
        }

        binding.topToolbar.title = getString(if (existingTemplate != null) R.string.edit_capture_template else R.string.capture_template)
        binding.topToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.topToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.done -> {
                    saveTemplate(existingTemplate?.id)
                    true
                }
                else -> false
            }
        }
    }

    private fun saveTemplate(existingId: String?) {
        val description = binding.templateDescription.text?.toString()?.trim() ?: ""
        if (description.isBlank()) {
            binding.templateDescription.error = getString(R.string.cannot_be_empty_short)
            return
        }

        val template = CaptureTemplate(
            id = existingId ?: java.util.UUID.randomUUID().toString(),
            description = description,
            title = binding.templateTitle.text?.toString()?.trim() ?: "",
            content = binding.templateContent.text?.toString()?.trim() ?: "",
            targetBook = binding.templateTargetBook.text?.toString()?.trim() ?: "",
            state = binding.templateState.text?.toString()?.trim() ?: "",
            priority = binding.templatePriority.text?.toString()?.trim() ?: "",
            tags = binding.templateTags.text?.toString()?.trim() ?: "",
            isScheduled = binding.templateScheduled.isChecked
        )

        val templates = AppPreferences.captureTemplates(requireContext()).toMutableList()
        val existingIndex = templates.indexOfFirst { it.id == existingId }
        if (existingIndex >= 0) {
            templates[existingIndex] = template
        } else {
            templates.add(template)
        }
        AppPreferences.setCaptureTemplates(requireContext(), templates)

        Toast.makeText(requireContext(), R.string.capture_template_saved, Toast.LENGTH_SHORT).show()
        listener?.onTemplateSaved()
        parentFragmentManager.popBackStack()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        val FRAGMENT_TAG: String = CaptureTemplateEditFragment::class.java.name
        private const val ARG_TEMPLATE_ID = "template_id"

        fun getInstance(templateId: String? = null): CaptureTemplateEditFragment {
            val fragment = CaptureTemplateEditFragment()
            val args = Bundle()
            if (templateId != null) {
                args.putString(ARG_TEMPLATE_ID, templateId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
