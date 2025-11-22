package com.orgzly.android.ui.dialogs

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.TimeType
import com.orgzly.android.ui.util.KeyboardUtils
import com.orgzly.android.ui.views.richtext.RichTextEdit
import com.orgzly.android.util.LogUtils
import com.orgzly.android.util.UserTimeFormatter
import com.orgzly.databinding.DialogTimestampBinding
import com.orgzly.databinding.DialogTimestampTitleBinding
import com.orgzly.org.datetime.OrgDateTime
import java.util.Calendar
import java.util.TreeSet
import kotlin.properties.Delegates

class TimestampDialogFragment : DialogFragment(), View.OnClickListener {
    private var listener: OnDateTimeSetListener? = null

    private lateinit var userTimeFormatter: UserTimeFormatter

    private lateinit var binding: DialogTimestampBinding
    private lateinit var titleBinding: DialogTimestampTitleBinding

    private lateinit var viewModel: TimestampDialogViewModel

    // Currently opened picker. Keep the reference to avoid leak.
    private var pickerDialog: AlertDialog? = null

    // The view that the dialog was launched from.
    // Sometimes required for showing the keyboard again after closing the dialog.
    private var originViewId: Int by Delegates.notNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userTimeFormatter = UserTimeFormatter(requireContext())
    }

    /**
     * Create a new instance of a dialog.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)


        /* This makes sure that the fragment has implemented
         * the callback interface. If not, it throws an exception
         */
        check(parentFragment is OnDateTimeSetListener) { "Fragment " + parentFragment + " must implement " + OnDateTimeSetListener::class.java }

        listener = parentFragment as OnDateTimeSetListener?


        val args = requireArguments()

        originViewId = args.getInt(ARG_ORIGIN_VIEW_ID)
        val timeType = TimeType.valueOf(requireNotNull(args.getString(ARG_TIME_TYPE)))
        val noteIds = TreeSet(args.getLongArray(ARG_NOTE_IDS)?.toList() ?: emptyList())
        val dateTimeString = args.getString(ARG_TIME)


        LayoutInflater.from(requireContext()).let { inflater ->
            binding = DialogTimestampBinding.inflate(inflater)
            titleBinding = DialogTimestampTitleBinding.inflate(inflater)
        }

        val factory = TimestampDialogViewModelFactory.getInstance(timeType, dateTimeString)
        viewModel = ViewModelProvider(this, factory).get(TimestampDialogViewModel::class.java)

        if (timestampIsInline()) {
            // Show/hide the active/inactive checkbox
            val lastInlineTimestampWasActive = AppPreferences.lastInlineTimestampWasActive(context)
            viewModel.setIsActive(lastInlineTimestampWasActive)
            binding.isActiveCheckbox.isChecked = lastInlineTimestampWasActive
            binding.isActiveCheckbox.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setIsActive(isChecked)
            }
        } else {
            binding.isActiveCheckbox.visibility = View.GONE
            binding.isActiveLabel.visibility = View.GONE
        }

        setupObservers()

        // Pickers

        binding.datePickerButton.setOnClickListener(this)

        binding.timePickerButton.setOnClickListener(this)
        binding.timeUsedCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIsTimeUsed(isChecked)
        }

        binding.endTimePickerButton.setOnClickListener(this)
        binding.endTimeUsedCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIsEndTimeUsed(isChecked)
        }

        binding.repeaterPickerButton.setOnClickListener(this)
        binding.repeaterUsedCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIsRepeaterUsed(isChecked)
        }

        binding.delayPickerButton.setOnClickListener(this)
        binding.delayUsedCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIsDelayUsed(isChecked)
        }
        binding.delayPickerLabel.text = if (viewModel.timeType == TimeType.SCHEDULED) {
            getString(R.string.timestamp_dialog_delay)
        } else {
            getString(R.string.timestamp_dialog_warning_period)
        }

        // Shortcuts
        binding.todayButton.setOnClickListener(this)
        binding.tomorrowButton.setOnClickListener(this)
        binding.nextWeekButton.setOnClickListener(this)


        return MaterialAlertDialogBuilder(requireContext())
            .setCustomTitle(titleBinding.root)
            .setView(binding.root)
            .setPositiveButton(R.string.set) { _, _ ->
                val time = viewModel.getOrgDateTime()
                if (time != null) {
                    AppPreferences.lastInlineTimestampWasActive(context, time.isActive)
                }
                listener?.onDateTimeSet(originViewId, noteIds, time)
            }
            .setNeutralButton(R.string.clear) { _, _ ->
                listener?.onDateTimeSet(originViewId, noteIds, null)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                listener?.onDateTimeAborted(originViewId, noteIds)
            }
            .show()
    }

    private fun timestampIsInline(): Boolean {
        return originViewId == R.id.content_edit || originViewId == R.id.title_edit
    }

    /**
     * Receives all dialog's clicks
     */
    override fun onClick(v: View) {
        when (v.id) {
            R.id.date_picker_button -> {
                val picker = DatePickerDialog(requireContext(), { _, year, monthOfYear, dayOfMonth ->
                    viewModel.set(year, monthOfYear, dayOfMonth)
                }, viewModel.getYearMonthDay().first, viewModel.getYearMonthDay().second, viewModel.getYearMonthDay().third)

                picker.show()
            }

            R.id.time_picker_button -> {
                val hourMinute = viewModel.getTimeHourMinute()


                val picker = TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                    viewModel.setTime(hourOfDay, minute)
                }, hourMinute.first, hourMinute.second, DateFormat.is24HourFormat(context))

                picker.show()
            }

            R.id.end_time_picker_button -> {
                val hourMinute = viewModel.getEndTimeHourMinute()

                val picker = TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                    viewModel.setEndTime(hourOfDay, minute)
                }, hourMinute.first, hourMinute.second, DateFormat.is24HourFormat(context))

                picker.show()
            }

            R.id.repeater_picker_button -> {
                val picker = RepeaterPickerDialog(requireContext(), viewModel.getRepeaterString()) {
                    viewModel.set(it)
                }

                picker.setOnDismissListener {
                    pickerDialog = null
                }

                pickerDialog = picker.apply {
                    show()
                }
            }

            R.id.delay_picker_button -> {
                val picker = if (viewModel.timeType == TimeType.SCHEDULED) {
                    DelayPickerDialog(requireContext(), viewModel.getDelayString()) {
                        viewModel.set(it)
                    }
                } else {
                    WarningPeriodPickerDialog(requireContext(), viewModel.getDelayString()) {
                        viewModel.set(it)
                    }
                }

                picker.setOnDismissListener {
                    pickerDialog = null
                }

                pickerDialog = picker.apply {
                    show()
                }
            }

            R.id.today_button -> {
                Calendar.getInstance().apply {
                    viewModel.set(get(Calendar.YEAR), get(Calendar.MONTH), get(Calendar.DAY_OF_MONTH))
                }
            }

            R.id.tomorrow_button -> {
                Calendar.getInstance().apply {
                    add(Calendar.DATE, 1)
                }.apply {
                    viewModel.set(get(Calendar.YEAR), get(Calendar.MONTH), get(Calendar.DAY_OF_MONTH))
                }
            }

            R.id.next_week_button -> {
                Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    add(Calendar.DATE, 7)
                }.apply {
                    viewModel.set(get(Calendar.YEAR), get(Calendar.MONTH), get(Calendar.DAY_OF_MONTH))
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.dateTime.observe(requireActivity()) { dateTime ->
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, dateTime)

            val orgDateTime = viewModel.getOrgDateTime(dateTime)

            titleBinding.timestamp.text = userTimeFormatter.formatAll(orgDateTime)

            binding.datePickerButton.text = userTimeFormatter.formatDate(dateTime)

            binding.timePickerButton.text = userTimeFormatter.formatTime(dateTime)
            binding.timeUsedCheckbox.isChecked = dateTime.isTimeUsed

            binding.endTimePickerButton.text = userTimeFormatter.formatEndTime(dateTime)
            binding.endTimeUsedCheckbox.isChecked = dateTime.isEndTimeUsed
            // Disable if there is no start time
            binding.endTimePickerLabel.isEnabled = dateTime.isTimeUsed
            binding.endTimePickerButton.isEnabled = dateTime.isTimeUsed
            binding.endTimeUsedCheckbox.isEnabled = dateTime.isTimeUsed

            binding.repeaterPickerButton.text = userTimeFormatter.formatRepeater(dateTime)
            binding.repeaterUsedCheckbox.isChecked = dateTime.isRepeaterUsed

            binding.delayPickerButton.text = userTimeFormatter.formatDelay(dateTime)
            binding.delayUsedCheckbox.isChecked = dateTime.isDelayUsed
        }
    }

    override fun onResume() {
        super.onResume()
        KeyboardUtils.closeSoftKeyboard(activity)
    }

    override fun onPause() {
        super.onPause()

        pickerDialog?.dismiss()
        pickerDialog = null
    }

    override fun onDetach() {
        super.onDetach()
        if (timestampIsInline()) {
            originViewId.let {
                val view = requireParentFragment().view?.findViewById<RichTextEdit>(it)
                if (view != null)
                    KeyboardUtils.openSoftKeyboard(view)
            }
        }
    }

    /**
     * The callback used to indicate the user is done filling in the date.
     */
    interface OnDateTimeSetListener {
        fun onDateTimeSet(originViewId: Int, noteIds: TreeSet<Long>, time: OrgDateTime?)
        fun onDateTimeAborted(originViewId: Int, noteIds: TreeSet<Long>)
    }

    companion object {
        val FRAGMENT_TAG: String = TimestampDialogFragment::class.java.name

        private val TAG = TimestampDialogFragment::class.java.name

        private const val ARG_ORIGIN_VIEW_ID = "origin_view_id"
        private const val ARG_TIME_TYPE = "time_type"
        private const val ARG_NOTE_IDS = "note_ids"
        private const val ARG_TIME = "time"


        fun getInstance(originViewId: Int, timeType: TimeType, noteIds: Set<Long>, time: OrgDateTime?): TimestampDialogFragment {
            val fragment = TimestampDialogFragment()

            /* Set arguments for fragment. */
            val bundle = Bundle()

            bundle.putInt(ARG_ORIGIN_VIEW_ID, originViewId)
            bundle.putString(ARG_TIME_TYPE, timeType.name)
            bundle.putLongArray(ARG_NOTE_IDS, toArray(noteIds))

            if (time != null) {
                bundle.putString(ARG_TIME, time.toString())
            }

            fragment.arguments = bundle

            return fragment
        }

        private fun toArray(set: Set<Long>): LongArray {
            var i = 0
            val result = LongArray(set.size)

            for (e in set) {
                result[i++] = e
            }

            return result
        }
    }
}