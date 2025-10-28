package com.orgzly.android.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.ui.views.style.UrlLinkSpan
import org.junit.Before

open class OrgFormatterTest {

    protected lateinit var context: Context

    @Before
    open fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    protected inner class SpanItem {
        var span: Any? = null
        var start: Int = 0
        var end: Int = 0
        var url: String? = null
    }

    protected inner class ParseResult(str: String) {
        val outputString: String
        val foundSpans: Array<SpanItem>

        init {
            val ssb = OrgFormatter.parse(str, context)

            outputString = ssb.toString()

            val allSpans = ssb.getSpans(0, ssb.length, Any::class.java)

            foundSpans = Array(allSpans.size) { i ->
                val span = allSpans[i]
                SpanItem().apply {
                    this.span = span
                    this.start = ssb.getSpanStart(span)
                    this.end = ssb.getSpanEnd(span)

                    if (span is UrlLinkSpan) {
                        this.url = span.url
                    }
                }
            }

            // Sort spans in the order they appear
            foundSpans.sortBy { it.start }
        }
    }
}
