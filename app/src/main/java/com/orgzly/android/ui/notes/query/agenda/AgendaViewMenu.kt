package com.orgzly.android.ui.notes.query.agenda

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Menu
import androidx.core.view.MenuItemCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.orgzly.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.joda.time.LocalDate
import kotlin.math.roundToInt

internal object AgendaViewMenu {
    val choices = listOf(
        R.id.agenda_view_simple to AgendaViewMode.SIMPLE,
        R.id.agenda_view_day to AgendaViewMode.DAY,
        R.id.agenda_view_three_days to AgendaViewMode.THREE_DAYS,
        R.id.agenda_view_seven_days to AgendaViewMode.SEVEN_DAYS,
        R.id.agenda_view_month to AgendaViewMode.MONTH,
    )

    fun modeForItem(id: Int): AgendaViewMode? = choices.firstOrNull { it.first == id }?.second

    fun inflate(toolbar: MaterialToolbar, mode: AgendaViewMode) {
        toolbar.inflateMenu(R.menu.agenda_view_actions)
        select(toolbar.menu, mode)
        updateToday(toolbar)
    }

    fun select(menu: Menu, mode: AgendaViewMode) {
        val submenu = menu.findItem(R.id.agenda_views)?.subMenu ?: return
        // Intermediate day counts have no preset, so clear the previous selection too.
        submenu.setGroupCheckable(R.id.agenda_view_choices, true, false)
        choices.forEach { (id, choice) -> submenu.findItem(id)?.isChecked = choice == mode }
        submenu.setGroupCheckable(R.id.agenda_view_choices, true, true)
    }

    fun observeToday(owner: LifecycleOwner, toolbar: MaterialToolbar) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    updateToday(toolbar)
                    delay(60_000)
                }
            }
        }
    }

    private fun updateToday(toolbar: MaterialToolbar) {
        val item = toolbar.menu.findItem(R.id.agenda_today) ?: return
        val day = LocalDate.now().dayOfMonth
        if ((item.icon as? AgendaTodayDrawable)?.day != day) {
            item.icon = AgendaTodayDrawable(toolbar.context, day)
            MenuItemCompat.setContentDescription(item, "${toolbar.context.getString(R.string.agenda_grid_today)}, $day")
        }
    }
}

internal class AgendaTodayDrawable(context: Context, val day: Int) : Drawable() {
    private val size = (24 * context.resources.displayMetrics.density).roundToInt()
    private val colors: ColorStateList = context.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.colorControlNormal)).let {
        try { it.getColorStateList(0) ?: ColorStateList.valueOf(Color.GRAY) } finally { it.recycle() }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.defaultColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(4f, 5f, 20f, 21f, 1f, 1f, paint)
        canvas.drawLine(8f, 2f, 8f, 6f, paint)
        canvas.drawLine(16f, 2f, 16f, 6f, paint)
        canvas.drawLine(4f, 9f, 20f, 9f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 9f
        canvas.drawText(day.toString(), 12f, 15f - (paint.ascent() + paint.descent()) / 2f, paint)
        canvas.restoreToCount(save)
    }

    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun isStateful() = colors.isStateful
    override fun onStateChange(state: IntArray): Boolean {
        val color = colors.getColorForState(state, colors.defaultColor)
        if (paint.color == color) return false
        paint.color = color
        invalidateSelf()
        return true
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
}