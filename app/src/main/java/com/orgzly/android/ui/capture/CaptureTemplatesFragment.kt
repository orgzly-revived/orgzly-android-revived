package com.orgzly.android.ui.capture

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.databinding.FragmentCaptureTemplatesBinding

class CaptureTemplatesFragment : Fragment() {

    private lateinit var binding: FragmentCaptureTemplatesBinding
    private lateinit var viewAdapter: CaptureTemplatesAdapter
    private var listener: Listener? = null

    interface Listener {
        fun onCaptureTemplateClose()
        fun getCaptureTemplatesContainerId(): Int
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = activity as? Listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCaptureTemplatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? com.orgzly.android.ui.settings.SettingsFragment.Listener)?.onTitleChange(getString(R.string.capture_templates))
        setupRecyclerView()
        setupFab()
        loadTemplates()
    }

    private fun setupRecyclerView() {
        viewAdapter = CaptureTemplatesAdapter(
            onItemClick = { template -> openTemplate(template) },
            onItemLongClick = { template -> showDeleteDialog(template); true }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = viewAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            openTemplate(null)
        }
    }

    private fun loadTemplates() {
        val templates = AppPreferences.captureTemplates(requireContext())
        viewAdapter.submitList(templates)
        updateViewState(templates)
    }

    private fun updateViewState(templates: List<CaptureTemplate>) {
        binding.flipper.displayedChild = when {
            templates.isEmpty() -> 2
            else -> 1
        }
    }

    private fun openTemplate(template: CaptureTemplate?) {
        val containerId = listener?.getCaptureTemplatesContainerId() ?: return
        val fragment = CaptureTemplateEditFragment.getInstance(template?.id)
        fragment.listener = object : CaptureTemplateEditFragment.Listener {
            override fun onTemplateSaved() {
                loadTemplates()
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(containerId, fragment, CaptureTemplateEditFragment.FRAGMENT_TAG)
            .addToBackStack(null)
            .commit()
    }

    private fun showDeleteDialog(template: CaptureTemplate) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(template.description.ifBlank { getString(R.string.capture_template) })
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteTemplate(template)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteTemplate(template: CaptureTemplate) {
        val templates = AppPreferences.captureTemplates(requireContext()).toMutableList()
        templates.removeAll { it.id == template.id }
        AppPreferences.setCaptureTemplates(requireContext(), templates)
        loadTemplates()
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
