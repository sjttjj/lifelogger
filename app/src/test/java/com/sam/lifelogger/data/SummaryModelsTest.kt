package com.sam.lifelogger.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryModelsTest {

    @Test
    fun parsesOptionalRenderedSummaryHtml() {
        val summary = SummaryDetail.fromJsonObject(
            JSONObject(
                """
                {
                  "summary": "## Daily Summary\n\n**Date:** Today",
                  "summary_html": "<h2>Daily Summary</h2><p><strong>Date:</strong> Today</p>",
                  "segment_count": 2,
                  "total_duration_seconds": 123.0,
                  "title": "Today",
                  "edited_at": null,
                  "is_edited": false
                }
                """.trimIndent()
            )
        )

        assertEquals("## Daily Summary\n\n**Date:** Today", summary.summary)
        assertEquals("<h2>Daily Summary</h2><p><strong>Date:</strong> Today</p>", summary.summaryHtml)
        assertEquals(2, summary.segmentCount)
        assertEquals(123.0, summary.totalDurationSeconds, 0.0)
        assertEquals("Today", summary.title)
        assertNull(summary.editedAt)
    }
}
