package com.orgzly.android.ui.capture

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.Note
import com.orgzly.android.ui.Breadcrumbs
import com.orgzly.android.ui.refile.RefileAdapter
import com.orgzly.android.ui.refile.RefileViewModel
import com.orgzly.databinding.DialogRefileBinding
import javax.inject.Inject

/**
 * Heading picker for capture templates. Reuses the refile dialog's layout and adapter to let
 * the user navigate the target book's existing headings, select one (or the book root), or
 * create a new child heading. The result is delivered back via the Fragment Result API.
 */
class CaptureTemplateHeadlinePickerFragment : DialogFragment() {

    private lateinit var binding: DialogRefileBinding

    @Inject
    lateinit var dataRepository: DataRepository

    lateinit var viewModel: CaptureTemplateHeadlinePickerViewModel

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = CaptureTemplateHeadlinePickerViewModelFactory.create(dataRepository)

        viewModel = ViewModelProvider(this, factory)
            .get(CaptureTemplateHeadlinePickerViewModel::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), theme).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogRefileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dialogRefileToolbar.apply {
            title = getString(R.string.template_select_target)

            setNavigationOnClickListener {
                dismiss()
            }

            inflateMenu(R.menu.capture_template_headline_picker)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.new_heading -> {
                        promptNewHeading()
                        true
                    }
                    else -> false
                }
            }
        }

        // "Add heading" only makes sense inside a notebook, and selecting the current
        // location is meaningless at the notebook list. Start hidden; the data observer
        // reveals them once we drill into a notebook.
        val newHeadingItem = binding.dialogRefileToolbar.menu.findItem(R.id.new_heading)
        newHeadingItem?.isVisible = false

        val adapter = RefileAdapter(binding.root.context, object : RefileAdapter.OnClickListener {
            override fun onItem(item: RefileViewModel.Item) {
                viewModel.open(item)
            }

            override fun onButton(item: RefileViewModel.Item) {
                viewModel.select(item)
            }
        })

        binding.dialogRefileTargets.let {
            it.layoutManager = LinearLayoutManager(context)
            it.adapter = adapter
        }

        val selectHereButton = binding.dialogRefileRefileHere.apply {
            contentDescription = getString(R.string.template_select_this_headline)
            visibility = View.INVISIBLE
            setOnClickListener {
                viewModel.selectHere()
            }
        }

        binding.dialogRefileBreadcrumbs.movementMethod = LinkMovementMethod.getInstance()

        viewModel.data.observe(viewLifecycleOwner) { data ->
            val breadcrumbs = data.first
            val list = data.second

            adapter.submitList(list)

            // breadcrumbs == [Home] means we're at the notebook list.
            val atNotebookList = breadcrumbs.size <= 1
            selectHereButton.visibility = if (atNotebookList) View.INVISIBLE else View.VISIBLE
            newHeadingItem?.isVisible = !atNotebookList

            binding.dialogRefileBreadcrumbs.text = generateBreadcrumbs(breadcrumbs)
            binding.dialogRefileBreadcrumbsScrollView.post {
                binding.dialogRefileBreadcrumbsScrollView.fullScroll(View.FOCUS_RIGHT)
            }
        }

        viewModel.selectedEvent.observeSingle(viewLifecycleOwner) { result ->
            if (result.ambiguous) {
                Toast.makeText(
                    requireContext(),
                    R.string.template_headline_ambiguous,
                    Toast.LENGTH_LONG
                ).show()
            }

            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_BOOK to result.book,
                    RESULT_PATH to result.path,
                    RESULT_LABEL to result.label
                )
            )

            dismiss()
        }

        viewModel.errorEvent.observeSingle(viewLifecycleOwner) { error ->
            binding.dialogRefileToolbar.subtitle = (error.cause ?: error).localizedMessage
        }

        viewModel.openForTheFirstTime()
    }

    private fun promptNewHeading() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.template_new_headline_name)
        }

        val pad = resources.getDimensionPixelSize(R.dimen.screen_edge)
        val container = FrameLayout(requireContext()).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.template_new_headline)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                when {
                    name.isEmpty() ->
                        Toast.makeText(
                            requireContext(),
                            R.string.cannot_be_empty_short,
                            Toast.LENGTH_SHORT
                        ).show()
                    name.contains("/") ->
                        Toast.makeText(
                            requireContext(),
                            R.string.template_headline_no_slash,
                            Toast.LENGTH_SHORT
                        ).show()
                    else ->
                        viewModel.createHeadingHere(name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun generateBreadcrumbs(path: List<RefileViewModel.Item>): CharSequence {
        val breadcrumbs = Breadcrumbs()

        path.forEachIndexed { index, item ->
            val onClick = if (index != path.size - 1) { // Not last
                fun() {
                    viewModel.onBreadcrumbClick(item)
                }
            } else {
                null
            }

            when (val payload = item.payload) {
                is RefileViewModel.Home ->
                    breadcrumbs.add(getString(R.string.notebooks), 0, onClick = onClick)
                is Book ->
                    breadcrumbs.add(payload.title ?: payload.name, 0, onClick = onClick)
                is Note ->
                    breadcrumbs.add(payload.title, onClick = onClick)
            }
        }

        return breadcrumbs.toCharSequence()
    }

    override fun onResume() {
        super.onResume()

        dialog?.apply {
            val w = resources.displayMetrics.widthPixels
            val h = resources.displayMetrics.heightPixels

            requireDialog().window?.apply {
                if (h > w) { // Portrait
                    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (h * 0.90).toInt())
                } else {
                    setLayout((w * 0.90).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
                }
            }
        }
    }

    companion object {
        const val REQUEST_KEY = "capture_template_headline_picker"
        const val RESULT_BOOK = "book"
        const val RESULT_PATH = "path"
        const val RESULT_LABEL = "label"

        val FRAGMENT_TAG: String = CaptureTemplateHeadlinePickerFragment::class.java.name

        fun getInstance(): CaptureTemplateHeadlinePickerFragment {
            return CaptureTemplateHeadlinePickerFragment()
        }
    }
}
