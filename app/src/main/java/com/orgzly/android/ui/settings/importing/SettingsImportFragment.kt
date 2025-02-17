package com.orgzly.android.ui.settings.importing

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.Note
import com.orgzly.android.ui.Breadcrumbs
import com.orgzly.android.ui.note.NotePayload
import com.orgzly.android.ui.showSnackbar
import com.orgzly.android.util.LogUtils
import com.orgzly.databinding.DialogImportSettingsBinding
import javax.inject.Inject

class SettingsImportFragment : DialogFragment() {

    private lateinit var binding: DialogImportSettingsBinding

    @Inject
    lateinit var dataRepository: DataRepository

    lateinit var viewModel: SettingsImportViewModel

    override fun onAttach(context: Context) {
        super.onAttach(context)

        App.appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        val noteIds = arguments?.getLongArray(ARG_NOTE_IDS)?.toSet() ?: emptySet()
        val count = arguments?.getInt(ARG_COUNT) ?: 0

        val factory = SettingsImportViewModelFactory.forNotes(dataRepository, noteIds, count)

        viewModel = ViewModelProvider(this, factory)[SettingsImportViewModel::class.java]
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        val dialog = MaterialAlertDialogBuilder(requireContext(), theme)

        return dialog.show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        binding = DialogImportSettingsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dialogImportSettingsToolbar.apply {
            title = resources.getString(R.string.import_settings_from_note)

            setNavigationOnClickListener {
                dismiss()
            }
        }

        val adapter = SettingsImportAdapter(binding.root.context, object: SettingsImportAdapter.OnClickListener {
            override fun onItem(item: SettingsImportViewModel.Item) {
                viewModel.open(item)
            }

            override fun onButton(item: SettingsImportViewModel.Item) {
                viewModel.import(item)
            }
        })

        binding.dialogImportSettingsTargets.let {
            it.layoutManager = LinearLayoutManager(context)
            it.adapter = adapter
        }
        
        binding.dialogImportSettingsBreadcrumbs.movementMethod = LinkMovementMethod.getInstance()

        viewModel.data.observe(viewLifecycleOwner) { data ->
            val breadcrumbs = data.first
            val list = data.second

            adapter.submitList(list)

            // Update and scroll breadcrumbs to the end
            binding.dialogImportSettingsBreadcrumbs.text = generateBreadcrumbs(breadcrumbs)
            binding.dialogImportSettingsBreadcrumbsScrollView.apply {
                post {
                    fullScroll(View.FOCUS_RIGHT)
                }
            }
        }

        viewModel.importedEvent.observeSingle(viewLifecycleOwner) { result ->
            if (result.userData is RuntimeException) {
                (result.userData).let {
                    activity?.showSnackbar(it.localizedMessage)
                }
            } else {
                dismiss()

                (result.userData as NotePayload).let {
                    activity?.showSnackbar(
                        getString(
                            R.string.settings_imported_from, it.title
                        )
                    )
                }
            }
        }

        viewModel.errorEvent.observeSingle(viewLifecycleOwner) { error ->
            binding.dialogImportSettingsToolbar.subtitle = (error.cause ?: error).localizedMessage
        }

        viewModel.openForTheFirstTime()
    }

    private fun generateBreadcrumbs(path: List<SettingsImportViewModel.Item>): CharSequence {
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
                is SettingsImportViewModel.Home ->
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
        fun getInstance(noteIds: Set<Long>, count: Int): SettingsImportFragment {
            return SettingsImportFragment().also { fragment ->
                fragment.arguments = Bundle().apply {
                    putLongArray(ARG_NOTE_IDS, noteIds.toLongArray())
                    putInt(ARG_COUNT, count)
                }
            }
        }

        private const val ARG_NOTE_IDS = "note_ids"
        private const val ARG_COUNT = "count"

        private val TAG = SettingsImportFragment::class.java.name

        val FRAGMENT_TAG: String = SettingsImportFragment::class.java.name
    }
}