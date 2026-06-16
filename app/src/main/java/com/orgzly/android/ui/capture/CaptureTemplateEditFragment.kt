package com.orgzly.android.ui.capture

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    @Inject
    lateinit var dataRepository: DataRepository

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
            binding.templateTitle.setText(existingTemplate.title)
            binding.templateContent.setText(existingTemplate.content)
            binding.templateTags.setText(existingTemplate.tags)
            binding.templateScheduled.isChecked = existingTemplate.isScheduled
        }

        // selectedBookName/selectedHeadline are plain logic state (not view state), so restore
        // them from savedInstanceState on recreation; otherwise seed from the existing template.
        val initialBook = savedInstanceState?.getString(STATE_BOOK) ?: existingTemplate?.targetBook
        val initialHeadline = if (savedInstanceState != null) {
            savedInstanceState.getString(STATE_HEADLINE)
        } else {
            existingTemplate?.targetHeadline
        }
        selectedHeadline = initialHeadline
        binding.templateTargetHeadline.setText(initialHeadline.orEmpty())

        val title = getString(if (existingTemplate != null) R.string.edit_capture_template else R.string.capture_template)
        (activity as? SettingsFragment.Listener)?.onTitleChange(title)

        setupNotebookSelector(initialBook)
        setupHeadlinePicker()
        setupStateSpinner(existingTemplate?.state)
        setupPrioritySpinner(existingTemplate?.priority)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_BOOK, selectedBookName)
        outState.putString(STATE_HEADLINE, selectedHeadline)
    }

    private var selectedBookName: String = ""
    private var selectedHeadline: String? = null

    private fun setupHeadlinePicker() {
        parentFragmentManager.setFragmentResultListener(
            CaptureTemplateHeadlinePickerFragment.REQUEST_KEY, viewLifecycleOwner
        ) { _, bundle ->
            val path = bundle.getString(CaptureTemplateHeadlinePickerFragment.RESULT_PATH).orEmpty()
            selectedHeadline = path.ifBlank { null }
            binding.templateTargetHeadline.setText(path)
        }

        binding.templateTargetHeadline.setOnClickListener {
            if (selectedBookName.isBlank()) {
                return@setOnClickListener
            }
            val book = dataRepository.getBook(selectedBookName) ?: return@setOnClickListener
            CaptureTemplateHeadlinePickerFragment.getInstance(book.id)
                .show(parentFragmentManager, CaptureTemplateHeadlinePickerFragment.FRAGMENT_TAG)
        }
    }

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
        updateHeadlineVisibility()

        binding.templateTargetBook.setOnClickListener {
            val currentText = binding.templateTargetBook.text.toString()
            val checkedItem = bookNames.indexOf(currentText).coerceAtLeast(0)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.template_target_book)
                .setSingleChoiceItems(bookNames.toTypedArray(), checkedItem) { dialog, which ->
                    val previousBookName = selectedBookName
                    if (which == 0) {
                        selectedBookName = ""
                        binding.templateTargetBook.setText(none)
                    } else {
                        selectedBookName = bookNames[which]
                        binding.templateTargetBook.setText(selectedBookName)
                    }
                    if (selectedBookName != previousBookName) {
                        // A headline belongs to a specific book; clear it when the book changes.
                        selectedHeadline = null
                        binding.templateTargetHeadline.setText("")
                    }
                    updateHeadlineVisibility()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun updateHeadlineVisibility() {
        val enabled = selectedBookName.isNotBlank()
        binding.templateTargetHeadline.isEnabled = enabled
        binding.templateTargetHeadlineLayout.isEnabled = enabled
        binding.templateTargetHeadlineLayout.helperText = getString(
            if (enabled) R.string.template_target_headline_hint
            else R.string.template_select_notebook_first
        )
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
        inflater.inflate(R.menu.capture_template_edit, menu)
        if (existingTemplateId == null) {
            menu.findItem(R.id.delete)?.isVisible = false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.done -> {
                saveTemplate(existingTemplateId)
                true
            }
            R.id.delete -> {
                confirmDelete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmDelete() {
        val templateId = existingTemplateId ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(binding.templateDescription.text?.toString()?.ifBlank {
                getString(R.string.capture_template)
            } ?: getString(R.string.capture_template))
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteTemplate(templateId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteTemplate(templateId: String) {
        val templates = AppPreferences.captureTemplates(requireContext()).toMutableList()
        templates.removeAll { it.id == templateId }
        AppPreferences.setCaptureTemplates(requireContext(), templates)
        Toast.makeText(requireContext(), R.string.capture_template_deleted, Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
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

        val headline = normalizeHeadlinePath(selectedHeadline)

        val template = CaptureTemplate(
            id = existingId ?: java.util.UUID.randomUUID().toString(),
            description = description,
            title = binding.templateTitle.text?.toString()?.trim() ?: "",
            content = binding.templateContent.text?.toString()?.trim() ?: "",
            targetBook = selectedBookName,
            targetHeadline = headline,
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
        parentFragmentManager.popBackStack()
    }

    companion object {
        val FRAGMENT_TAG: String = CaptureTemplateEditFragment::class.java.name
        private const val ARG_TEMPLATE_ID = "template_id"
        private const val STATE_BOOK = "state_selected_book"
        private const val STATE_HEADLINE = "state_selected_headline"

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
