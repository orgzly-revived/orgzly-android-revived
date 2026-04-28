package com.orgzly.android.ui.notes.query.agenda

import android.graphics.Color
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.databinding.FragmentQueryAgendaBinding
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class AgendaMonthView(
    private val fragment: Fragment,
    private val binding: FragmentQueryAgendaBinding,
    private val getItems: () -> List<AgendaItem>,
    private val getCurrentMonth: () -> DateTime,
    private val getSelectedDay: () -> DateTime,
    private val onDaySelected: (DateTime) -> Unit,
    private val openNote: (AgendaItem) -> Unit,
    private val onSelectionToggle: (AgendaItem.Note) -> Unit
) {
    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        fragment.requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    fun render(month: DateTime, selectedDay: DateTime, attachTodayButtonFn: (LinearLayout, () -> Unit) -> Unit, goToToday: () -> Unit) {
        binding.monthGrid.visibility = View.VISIBLE
        binding.monthDayEventsRecyclerView.visibility = View.GONE

        binding.monthLabelContainer.removeAllViews()
        binding.monthLabelContainer.addView(binding.monthLabel)
        binding.monthLabel.text = DateUtils.formatDateTime(
            fragment.requireContext(), month.millis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_NO_MONTH_DAY
        )
        attachTodayButtonFn(binding.monthLabelContainer, goToToday)
        renderGrid(month, selectedDay)
    }

    private fun renderGrid(month: DateTime, selectedDay: DateTime) {
        val grid = binding.monthGrid
        grid.removeAllViews()
        val ctx            = fragment.requireContext()
        val textSize       = AppPreferences.calendarTextSize(ctx).toFloat()
        val showBook       = AppPreferences.calendarShowBookName(ctx)
        val systemFirstDOW = Calendar.getInstance().firstDayOfWeek
        val firstDOWJoda   = if (systemFirstDOW == Calendar.SUNDAY) DateTimeConstants.SUNDAY else DateTimeConstants.MONDAY
        val firstDay       = month.withDayOfMonth(1).withTimeAtStartOfDay()
        val gridStart      = firstDay.minusDays((7 + firstDay.dayOfWeek - firstDOWJoda) % 7)
        val dayLabels      = DateFormatSymbols(Locale.getDefault()).shortWeekdays
        val density        = fragment.resources.displayMetrics.density

        val colorOnSurface = themeColor(com.google.android.material.R.attr.colorOnSurface)
        val selectedBg     = (colorOnSurface and 0x00FFFFFF) or 0x44000000
        val barColor       = themeColor(com.google.android.material.R.attr.colorSecondary)

        repeat(7) { i ->
            grid.addView(
                TextView(ctx).apply {
                    text = dayLabels[(systemFirstDOW + i - 1) % 7 + 1]
                    gravity = Gravity.CENTER
                    setPadding(0, 8, 0, 8)
                },
                GridLayout.LayoutParams().apply {
                    width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(i, 1f)
                    rowSpec    = GridLayout.spec(0)
                }
            )
        }

        repeat(42) { index ->
            val day        = gridStart.plusDays(index)
            val inMonth    = isSameMonth(day, month)
            val isSelected = day.millis == selectedDay.millis
            val events     = dayEvents(ctx, getItems(), day)
            val (allDay, timed) = splitEvents(events)

            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.TOP
                setPadding(6, 6, 6, 6)
                alpha = if (inMonth) 1f else 0.4f
                if (isSelected) {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(selectedBg)
                        cornerRadius = 12f
                    }
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onDaySelected(day) }
            }

            cell.addView(TextView(ctx).apply {
                text = day.dayOfMonth.toString()
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            })

            allDay.take(2).forEach { cell.addView(buildEventView(it, textSize, showBook, false, density = density, barColor = barColor, colorOnSurface = colorOnSurface)) }
            timed.take(2).forEach {
                cell.addView(buildEventView(it, textSize, showBook, true,
                    hourForEvent(it.agendaItem) ?: 0f,
                    if (!isRepeating(it.agendaItem)) CalendarEventStyle.ACCENT_BAR else CalendarEventStyle.PLAIN,
                    density = density, barColor = barColor, colorOnSurface = colorOnSurface))
            }
            if (events.size > 4) {
                cell.addView(TextView(ctx).apply { text = "•"; gravity = Gravity.CENTER; alpha = 0.7f })
            }

            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0; height = 0
                columnSpec = GridLayout.spec(index % 7, 1f)
                rowSpec    = GridLayout.spec(index / 7 + 1, 1f)
            })
        }
    }

    private fun buildEventView(
        e: DayEvent,
        textSize: Float,
        showBookName: Boolean = false,
        showTime: Boolean = false,
        hour: Float = 0f,
        style: CalendarEventStyle = CalendarEventStyle.PLAIN,
        density: Float = 1f,
        barColor: Int = Color.GRAY,
        colorOnSurface: Int = Color.BLACK
    ): View {
        val ctx         = fragment.requireContext()
        val showBar     = style != CalendarEventStyle.PLAIN
        val matchHeight = style == CalendarEventStyle.ACCENT_BAR_FULL

        val bgAlpha    = if (showBar) 0x18 else 0x33
        val eventBgArgb = (colorOnSurface and 0x00FFFFFF) or (bgAlpha shl 24)

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 3, 4, 3)
            gravity = Gravity.TOP
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(eventBgArgb)
                cornerRadius = 2 * density
            }
            addView(TextView(ctx).apply {
                text = e.title
                this.textSize = textSize * 0.9f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (showBookName) {
                addView(TextView(ctx).apply {
                    text = e.bookName
                    this.textSize = textSize * 0.7f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    alpha = 0.8f
                })
            }
            if (showTime) {
                val h = hour.toInt()
                val m = ((hour % 1) * 60).toInt()
                val startStr = String.format("%02d:%02d", h, m)
                val duration = durationHoursForEvent(e.agendaItem)
                val timeStr = if (duration != null) {
                    val endFloat = hour + duration
                    "$startStr-${String.format("%02d:%02d", endFloat.toInt(), ((endFloat % 1) * 60).toInt())}"
                } else startStr
                addView(TextView(ctx).apply {
                    text = timeStr
                    this.textSize = textSize * 0.7f
                    alpha = 0.6f
                })
            }
            isClickable = true
            setOnClickListener { openNote(e.agendaItem) }
            setOnLongClickListener { onSelectionToggle(e.agendaItem); true }
        }

        return if (showBar) {
            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(2, 2, 2, 2) }
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(4, MATCH_PARENT).apply { setMargins(0, 2, 4, 2) }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(barColor)
                        cornerRadius = 8f
                    }
                })
                addView(content, LinearLayout.LayoutParams(0, if (matchHeight) MATCH_PARENT else WRAP_CONTENT, 1f))
            }
        } else {
            content.apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0, 0, 0, 4) }
            }
        }
    }
}