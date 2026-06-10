package com.sam.lifelogger.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionModelsTest {

    @Test
    fun parsesSessionListResponse() {
        val response = JSONObject(
            """
            {
              "date": "2026-06-04",
              "sessions": [
                {
                  "filename": "2026-06-04_meeting_0000_f49704_session.md",
                  "type": "meeting",
                  "size_bytes": 2402,
                  "path": "sessions/2026-06-04/2026-06-04_meeting_0000_f49704_session.md"
                }
              ],
              "count": 1
            }
            """.trimIndent()
        )

        val session = SessionNoteEntry.listFromJsonObject(response).single()

        assertEquals("2026-06-04_meeting_0000_f49704_session.md", session.filename)
        assertEquals("meeting", session.type)
        assertEquals(2402L, session.sizeBytes)
        assertEquals("sessions/2026-06-04/2026-06-04_meeting_0000_f49704_session.md", session.path)
    }

    @Test
    fun parsesOptionalFrontmatterAndKeepsMarkdownBody() {
        val detail = SessionNoteDetail.fromJsonObject(
            JSONObject(
                """
                {
                  "date": "2026-06-04",
                  "filename": "session.md",
                  "content": "---\nsession_id: f4970418-f938-4130-a825-7e2a8e31779d\nchunks: 1\ntotal_duration: 177s\nprompt_id: meeting\n---\n## Meeting Details\nBody"
                }
                """.trimIndent()
            )
        )

        assertEquals("f4970418-f938-4130-a825-7e2a8e31779d", detail.metadata.sessionId)
        assertEquals(1, detail.metadata.chunks)
        assertEquals("177s", detail.metadata.totalDuration)
        assertEquals("meeting", detail.metadata.promptId)
        assertEquals("## Meeting Details\nBody", detail.markdown)
    }

    @Test
    fun parsesOptionalRenderedSessionHtml() {
        val detail = SessionNoteDetail.fromJsonObject(
            JSONObject(
                """
                {
                  "date": "2026-06-04",
                  "filename": "session.md",
                  "content": "## Meeting Details\nBody",
                  "content_html": "<h2>Meeting Details</h2><p>Body</p>"
                }
                """.trimIndent()
            )
        )

        assertEquals("<h2>Meeting Details</h2><p>Body</p>", detail.contentHtml)
    }

    @Test
    fun treatsMissingFrontmatterAsRenderableMarkdown() {
        val detail = SessionNoteDetail.fromContent(
            date = "2026-06-04",
            filename = "session.md",
            content = "## Freeform\nStill render this.",
            contentHtml = null
        )

        assertNull(detail.metadata.sessionId)
        assertEquals("## Freeform\nStill render this.", detail.markdown)
        assertNull(detail.contentHtml)
    }
}
