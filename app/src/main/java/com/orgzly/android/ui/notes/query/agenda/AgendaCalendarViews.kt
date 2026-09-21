package com.orgzly.android.ui.notes.query.agenda

import android.content.Context
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
import android.widget.TextView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.TimeType
import com.orgzly.databinding.FragmentQueryAgendaBinding
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

enum class CalendarDisplayMode { AGENDA, MONTH, WEEK }


fun isSameMonth(day: DateTime, month: DateTime): Boolean =
    day.year == month.year && day.monthOfYear == month.monthOfYear

private fun eventsForDay(items: List<AgendaItem>, day: DateTime): List<AgendaItem.Note> {
    val targetMillis = day.withTimeAtStartOfDay().millis
    val result = mutableListOf<AgendaItem.Note>()
    var inTargetDay = false
    for (item in items) {
        when (item) {
            is AgendaItem.Day     -> inTargetDay = item.day.withTimeAtStartOfDay().millis == targetMillis
            is AgendaItem.Note    -> if (inTargetDay) result.add(item)
            is AgendaItem.Overdue -> {}
        }
    }
    return result
}

private fun hourForEvent(item: AgendaItem.Note): Float? {
    val (hour, timestamp) = when (item.timeType) {
        TimeType.SCHEDULED -> item.note.scheduledTimeHour to item.note.scheduledTimeTimestamp
        TimeType.DEADLINE  -> item.note.deadlineTimeHour  to item.note.deadlineTimeTimestamp
        TimeType.EVENT     -> item.note.eventHour         to item.note.eventTimestamp
        else               -> null to null
    }
    hour ?: return null
    val minute = timestamp?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.MINUTE)
    } ?: 0
    return hour + minute / 60f
}

private fun systemFirstDOWJoda(): Int {
    val systemFirstDOW = Calendar.getInstance().firstDayOfWeek
    return if (systemFirstDOW == Calendar.SUNDAY) DateTimeConstants.SUNDAY else DateTimeConstants.MONDAY
}

public fun weekStartForDay(day: DateTime): DateTime {
    val firstDOWJoda = systemFirstDOWJoda()
    return day.withTimeAtStartOfDay().minusDays((7 + day.dayOfWeek - firstDOWJoda) % 7)
}

private fun buildEventChip(
    ctx: Context,
    item: AgendaItem.Note,
    textSize: Float,
    showBookName: Boolean,
    density: Float,
    barColor: Int,
    colorOnSurface: Int,
    openNote: (AgendaItem) -> Unit,
    onSelectionToggle: (AgendaItem.Note) -> Unit,
    showTime: Boolean = false
): View {
    val hour = if (showTime) hourForEvent(item) else null
    val showBar = hour != null
    val bgAlpha = if (showBar) 0x18 else 0x33
    val eventBg = (colorOnSurface and 0x00FFFFFF) or (bgAlpha shl 24)

    val content = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(4, 3, 4, 3)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(eventBg)
            cornerRadius = 2 * density
        }

        addView(TextView(ctx).apply {
            text = item.note.note.title
            this.textSize = textSize * 0.9f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        if (showBookName) {
            addView(TextView(ctx).apply {
                text = item.note.bookName
                this.textSize = textSize * 0.7f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                alpha = 0.8f
            })
        }

        if (hour != null) {
            val h = hour.toInt()
            val m = ((hour % 1) * 60).toInt()

            addView(TextView(ctx).apply {
                text = String.format("%02d:%02d", h, m)
                this.textSize = textSize * 0.7f
                alpha = 0.6f
            })
        }

        isClickable = true
        setOnClickListener { openNote(item) }
        setOnLongClickListener {
            onSelectionToggle(item)
            true
        }
    }

    return if (showBar) {
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(2, 2, 2, 2)
            }

            addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(4, MATCH_PARENT).apply {
                    setMargins(0, 2, 4, 2)
                }

                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(barColor)
                    cornerRadius = 8f
                }
            })

            addView(content, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
    } else {
        content.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 4)
            }
        }
    }
}

class AgendaMonthView(
    private val fragment: Fragment,
    private val binding: FragmentQueryAgendaBinding,
    private val getItems: () -> List<AgendaItem>,
    private val onDaySelected: (DateTime) -> Unit,
    private val openNote: (AgendaItem) -> Unit,
    private val onSelectionToggle: (AgendaItem.Note) -> Unit
) {
    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        fragment.requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    fun render(month: DateTime, selectedDay: DateTime) {
        val ctx = fragment.requireContext()

        binding.monthLabel.text = DateUtils.formatDateTime(
            ctx, month.millis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_NO_MONTH_DAY
        )

        val today = DateTime.now().withTimeAtStartOfDay()
        val isAtToday = today.millis == selectedDay.millis

        binding.monthLabelContainer.removeViews(1, (binding.monthLabelContainer.childCount - 1).coerceAtLeast(0))
        binding.monthLabelContainer.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_today)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val dp36 = (36 * ctx.resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(dp36, dp36)
            setColorFilter(if (isAtToday) Color.GRAY else Color.WHITE)
            setOnClickListener { if (!isAtToday) onDaySelected(today) }
        })

        val grid           = binding.monthGrid
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
        val todayBg     = (colorOnSurface and 0x00FFFFFF) or 0x22000000
        val barColor       = themeColor(com.google.android.material.R.attr.colorSecondary)

        grid.removeAllViews()
        grid.columnCount = 7
        grid.rowCount = 7

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
            val events     = eventsForDay(getItems(), day)
            val allDay     = events.filter { hourForEvent(it) == null }
            val timed      = events.filter { hourForEvent(it) != null }

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

                if (today.millis == day.millis && !isSelected) {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(todayBg)
                        cornerRadius = 12f
                    }

                    setTextColor(Color.WHITE)
                }

                val size = (32 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            })

            allDay.take(2).forEach {
                cell.addView(
                    buildEventChip(
                        ctx,
                        it,
                        textSize,
                        showBook,
                        density,
                        barColor,
                        colorOnSurface,
                        openNote,
                        onSelectionToggle
                    )
                )
            }

            timed.take(2).forEach {
                cell.addView(
                    buildEventChip(
                        ctx,
                        it,
                        textSize,
                        showBook,
                        density,
                        barColor,
                        colorOnSurface,
                        openNote,
                        onSelectionToggle,
                        showTime = true
                    )
                )
            }

            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(index % 7, 1f)
                rowSpec = GridLayout.spec(index / 7 + 1, 1f)
            })
        }
    }
}

class AgendaWeekView(
    private val fragment: Fragment,
    private val binding: FragmentQueryAgendaBinding,
    private val getItems: () -> List<AgendaItem>,
    private val onDaySelected: (DateTime) -> Unit,
    private val openNote: (AgendaItem) -> Unit,
    private val onSelectionToggle: (AgendaItem.Note) -> Unit
) {
    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        fragment.requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    fun render(weekStart: DateTime, selectedDay: DateTime) {
        val ctx = fragment.requireContext()
        val selectedDayStart = selectedDay.withTimeAtStartOfDay()

        binding.monthLabel.text = DateUtils.formatDateRange(
            ctx,
            weekStart.millis,
            weekStart.plusDays(6).millis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
        )

        val today = DateTime.now().withTimeAtStartOfDay()
        val isAtToday = selectedDayStart.millis == today.millis

        binding.monthLabelContainer.removeViews(
            1,
            (binding.monthLabelContainer.childCount - 1).coerceAtLeast(0)
        )

        binding.monthLabelContainer.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_today)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val dp36 = (36 * ctx.resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(dp36, dp36)
            setColorFilter(if (isAtToday) Color.GRAY else Color.WHITE)
            setOnClickListener { onDaySelected(today) }
        })

        val grid = binding.monthGrid
        val textSize = AppPreferences.calendarTextSize(ctx).toFloat()
        val showBook = AppPreferences.calendarShowBookName(ctx)
        val systemFirstDOW = Calendar.getInstance().firstDayOfWeek
        val firstDOWJoda =
            if (systemFirstDOW == Calendar.SUNDAY) DateTimeConstants.SUNDAY
            else DateTimeConstants.MONDAY
        val dayLabels = DateFormatSymbols(Locale.getDefault()).shortWeekdays
        val density = fragment.resources.displayMetrics.density
        val colorOnSurface = themeColor(com.google.android.material.R.attr.colorOnSurface)
        val barColor = themeColor(com.google.android.material.R.attr.colorSecondary)
        val selectedBg = (colorOnSurface and 0x00FFFFFF) or 0x44000000
        val todayBg = (colorOnSurface and 0x00FFFFFF) or 0x22000000

        grid.removeAllViews()
        grid.columnCount = 7
        grid.rowCount = 1

        repeat(7) { i ->
            val day = weekStart.plusDays(i)
            val dayStart = day.withTimeAtStartOfDay()
            val isSelected = dayStart.millis == selectedDayStart.millis
            val events = eventsForDay(getItems(), day)
            val allDay = events.filter { hourForEvent(it) == null }
            val timed = events.filter { hourForEvent(it) != null }

            val dayColumn = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(4, 4, 4, 4)

                if (isSelected) {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(selectedBg)
                        cornerRadius = 12f
                    }
                }

                setOnClickListener { onDaySelected(dayStart) }
            }

            dayColumn.addView(TextView(ctx).apply {
                val dowIndex = ((day.dayOfWeek - firstDOWJoda + 7) % 7)
                text = dayLabels[(systemFirstDOW + dowIndex - 1) % 7 + 1]
                gravity = Gravity.CENTER
            })

            dayColumn.addView(TextView(ctx).apply {
                text = day.dayOfMonth.toString()
                gravity = Gravity.CENTER

                if (today.millis == dayStart.millis && !isSelected) {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(todayBg)
                        cornerRadius = 12f
                    }
                    setTextColor(Color.WHITE)
                }

                val size = (32 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            })

            if (events.isEmpty()) {
                dayColumn.addView(TextView(ctx).apply {
                    gravity = Gravity.CENTER
                    alpha = 0.3f
                    this.textSize = textSize * 0.8f
                })
            } else {
                allDay.forEach {
                    dayColumn.addView(
                        buildEventChip(
                            ctx,
                            it,
                            textSize,
                            showBook,
                            density,
                            barColor,
                            colorOnSurface,
                            openNote,
                            onSelectionToggle
                        )
                    )
                }

                timed.forEach {
                    dayColumn.addView(
                        buildEventChip(
                            ctx,
                            it,
                            textSize,
                            showBook,
                            density,
                            barColor,
                            colorOnSurface,
                            openNote,
                            onSelectionToggle,
                            showTime = true
                        )
                    )
                }
            }

            grid.addView(
                androidx.core.widget.NestedScrollView(ctx).apply {
                    addView(dayColumn)
                },
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = WRAP_CONTENT
                    columnSpec = GridLayout.spec(i, 1f)
                    rowSpec = GridLayout.spec(0, 1f)
                }
            )
        }
    }
}