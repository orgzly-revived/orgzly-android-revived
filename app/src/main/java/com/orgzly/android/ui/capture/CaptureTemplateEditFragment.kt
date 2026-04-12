package com.orgzly.android.ui.capture

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.settings.SettingsFragment
import com.orgzly.databinding.FragmentCaptureTemplateBinding
import javax.inject.Inject

class CaptureTemplateEditFragment : Fragment() {

    private lateinit var binding: FragmentCaptureTemplateBinding
    private var existingTemplateId: String? = null
    var listener: Listener? = null

    @Inject
    lateinit var dataRepository: DataRepository

    interface Listener {
        fun onTemplateSaved()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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
        existingTemplateId = templateId
        val existingTemplate = if (templateId != null) {
            AppPreferences.captureTemplates(requireContext()).find { it.id == templateId }
        } else null

        if (existingTemplate != null) {
            binding.templateDescription.setText(existingTemplate.description)
            binding.templateContent.setText(existingTemplate.content)
            binding.templateTags.setText(existingTemplate.tags)
            binding.templateScheduled.isChecked = existingTemplate.isScheduled
        }

        val title = getString(if (existingTemplate != null) R.string.edit_capture_template else R.string.capture_template)
        (activity as? SettingsFragment.Listener)?.onTitleChange(title)

        setupNotebookSelector(existingTemplate?.targetBook)
        setupStateSpinner(existingTemplate?.state)
        setupPrioritySpinner(existingTemplate?.priority)
    }

    private var selectedBookName: String = ""

    private fun setupNotebookSelector(existingTargetBook: String?) {
        val books = dataRepository.getBooks()
        val none = getString(R.string.default_notebook)
        val bookNames = mutableListOf(none)
        bookNames.addAll(books.map { it.book.name })

        selectedBookName = existingTargetBook ?: ""
        val currentDisplay = if (selectedBookName.isNotBlank() && bookNames.contains(selectedBookName)) {
            selectedBookName
        } else {
            none
        }
        binding.templateTargetBook.setText(currentDisplay)

        binding.templateTargetBook.setOnClickListener {
            val currentText = binding.templateTargetBook.text.toString()
            val checkedItem = bookNames.indexOf(currentText).coerceAtLeast(0)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.template_target_book)
                .setSingleChoiceItems(bookNames.toTypedArray(), checkedItem) { dialog, which ->
                    if (which == 0) {
                        selectedBookName = ""
                        binding.templateTargetBook.setText(none)
                    } else {
                        selectedBookName = bookNames[which]
                        binding.templateTargetBook.setText(selectedBookName)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupStateSpinner(selectedState: String?) {
        val states = mutableListOf<String>()
        states.add(getString(R.string.none))
        states.addAll(AppPreferences.todoKeywordsSet(requireContext()))
        states.addAll(AppPreferences.doneKeywordsSet(requireContext()))

        val currentState = if (selectedState != null && states.contains(selectedState)) selectedState else states[0]
        binding.templateState.setText(currentState)

        binding.templateState.setOnClickListener {
            val checkedItem = states.indexOf(binding.templateState.text.toString())
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.template_state)
                .setSingleChoiceItems(states.toTypedArray(), checkedItem) { dialog, which ->
                    binding.templateState.setText(states[which])
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupPrioritySpinner(selectedPriority: String?) {
        val entries = mutableListOf(getString(R.string.none))
        entries.addAll(resources.getStringArray(R.array.priorities))
        val values = mutableListOf("")
        values.addAll(resources.getStringArray(R.array.priority_values))

        val index = values.indexOf(selectedPriority ?: "")
        val currentEntry = if (index != -1) entries[index] else entries[0]
        binding.templatePriority.setText(currentEntry)

        binding.templatePriority.setOnClickListener {
            val checkedItem = entries.indexOf(binding.templatePriority.text.toString())
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.template_priority)
                .setSingleChoiceItems(entries.toTypedArray(), checkedItem) { dialog, which ->
                    binding.templatePriority.setText(entries[which])
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.done, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.done -> {
                saveTemplate(existingTemplateId)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveTemplate(existingId: String?) {
        val description = binding.templateDescription.text?.toString()?.trim() ?: ""
        if (description.isBlank()) {
            binding.templateDescription.error = getString(R.string.cannot_be_empty_short)
            return
        }

        val none = getString(R.string.none)
        val stateText = binding.templateState.text.toString()
        val state = if (stateText == none) "" else stateText

        val priorityText = binding.templatePriority.text.toString()
        val priorityEntries = mutableListOf(none)
        priorityEntries.addAll(resources.getStringArray(R.array.priorities))
        val priorityValues = mutableListOf("")
        priorityValues.addAll(resources.getStringArray(R.array.priority_values))
        val priorityIndex = priorityEntries.indexOf(priorityText)
        val priority = if (priorityIndex != -1) priorityValues[priorityIndex] else ""

        val template = CaptureTemplate(
            id = existingId ?: java.util.UUID.randomUUID().toString(),
            description = description,
            content = binding.templateContent.text?.toString()?.trim() ?: "",
            targetBook = selectedBookName,
            state = state,
            priority = priority,
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
