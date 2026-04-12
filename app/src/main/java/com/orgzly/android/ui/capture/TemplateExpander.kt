package com.orgzly.android.ui.capture

import android.content.ClipboardManager
import android.content.Context
import com.orgzly.org.datetime.OrgDateTime
import java.util.Calendar

/**
 * Expands org-mode–style %-tokens in capture template strings.
 *
 * Supported tokens:
 *  %t  — active date stamp        e.g. <2026-04-12 Sun>
 *  %T  — active date+time stamp   e.g. <2026-04-12 Sun 17:11>
 *  %u  — inactive date stamp      e.g. [2026-04-12 Sun]
 *  %U  — inactive date+time stamp e.g. [2026-04-12 Sun 17:11]
 *  %c  — clipboard content
 *  %%  — literal %
 */
object TemplateExpander {

    fun expand(template: String, context: Context): String {
        if (!template.contains('%')) return template

        val now = Calendar.getInstance()
        val sb = StringBuilder(template.length)
        var i = 0

        while (i < template.length) {
            if (template[i] == '%' && i + 1 < template.length) {
                when (template[i + 1]) {
                    't' -> {
                        sb.append(orgDateStamp(now, active = true, withTime = false))
                        i += 2
                    }
                    'T' -> {
                        sb.append(orgDateStamp(now, active = true, withTime = true))
                        i += 2
                    }
                    'u' -> {
                        sb.append(orgDateStamp(now, active = false, withTime = false))
                        i += 2
                    }
                    'U' -> {
                        sb.append(orgDateStamp(now, active = false, withTime = true))
                        i += 2
                    }
                    'c' -> {
                        sb.append(clipboardText(context))
                        i += 2
                    }
                    '%' -> {
                        sb.append('%')
                        i += 2
                    }
                    else -> {
                        // Unknown token — keep literal
                        sb.append(template[i])
                        i++
                    }
                }
            } else {
                sb.append(template[i])
                i++
            }
        }

        return sb.toString()
    }

    private fun orgDateStamp(cal: Calendar, active: Boolean, withTime: Boolean): String {
        val dt = OrgDateTime(active)
        // OrgDateTime uses current time by default; toString() gives the right format
        // but we need to control whether time is included.
        return if (withTime) {
            dt.toString()
        } else {
            // OrgDateTime(active) includes time — build date-only stamp manually
            val orgDt = OrgDateTime(active)
            val s = orgDt.toString()
            // Format: <2026-04-12 Sun 17:11> or [2026-04-12 Sun 17:11]
            // Strip the time portion (last " HH:MM" before the closing bracket)
            val bracket = if (active) '>' else ']'
            val lastSpace = s.lastIndexOf(' ')
            if (lastSpace > 0 && s.endsWith(bracket.toString())) {
                s.substring(0, lastSpace) + bracket
            } else {
                s
            }
        }
    }

    private fun clipboardText(context: Context): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    }
}
