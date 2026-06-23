# Lifelogger Ask AI — App Integration & Server Handoff

Date: 2026-06-19

## Overview

The Android app now has a full "Ask LifeLogger" feature that lets the user query their Lifelogger data (summaries, reminders, events, tasks, WhatsApp) using natural language. Users can type a question or record a short voice clip.

This document describes what the app built, the exact API contract it uses, and two issues the server team needs to investigate.

---

## What the App Built

### Files Created

| File | Purpose |
|------|---------|
| `data/AskModels.kt` | Data classes: `AskScope`, `AskResponse`, `Citation`, `AskSearchInfo` with JSON parsing |
| `data/AskAudioRecorder.kt` | Lightweight `MediaRecorder` wrapper for short voice questions (max 60s, AAC 16kHz) — separate from the main lifelogger recording pipeline |
| `data/ApiClient.kt` | Two new methods: `askText()` and `askAudio()` (see API contract below) |
| `ui/AskScreen.kt` | Full-screen Compose UI with text input, mic button, recording timer, "Recent 30 days" scope chip, loading state, markdown answer rendering, confidence badge, and citation cards |
| `MainActivity.kt` | Navigation route `"ask"` + clickable Ask AI dashboard card on the home screen |

### Navigation Flow

1. User taps "Ask AI" dashboard card (4th card in the home screen carousel)
2. App navigates to full-screen `AskScreen`
3. User types a question OR taps mic to record a voice clip
4. App sends request to server and displays the answer

### Voice Questions

- Voice clips are **not stored** in the lifelogger DB or processed as summaries/reminders
- They are recorded to a temp file in the app's cache directory and deleted after upload
- Max recording duration is 60 seconds (configurable)
- After recording stops, the clip is uploaded immediately to `/api/ask/audio`
- The server returns a `transcript` field which the app displays above the answer
- The user can optionally edit the transcribed text and resubmit as a text query

---

## API Contract

### Base URL

Same as all Lifelogger APIs:
```
http://100.78.20.28:8000
```

### Text Ask

```
POST /api/ask
Content-Type: application/json
```

**Request:**
```json
{
  "question": "When did I last swim with Justin?",
  "scope": {
    "start_date": "2026-06-01",
    "end_date": "2026-06-18",
    "sources": ["summaries", "reminders", "events", "tasks", "whatsapp"]
  }
}
```

The `scope` object is entirely optional. When the user does NOT toggle "Recent 30 days", the app sends **no scope field at all** (the server searches everything).

When the user toggles "Recent 30 days", the app sends:
```json
{
  "scope": {
    "start_date": "2026-06-19",
    "end_date": "2026-07-19"
  }
}
```
(No `sources` filter — server defaults to all sources.)

### Voice Ask

```
POST /api/ask/audio
Content-Type: multipart/form-data
```

**Multipart fields:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `audio` | file | yes | AAC/MP4 audio file |
| `scope` | string | no | JSON string of scope object (same shape as text ask) |
| `recorded_at_ms` | string | no | Epoch milliseconds |
| `recorded_at_iso` | string | no | ISO 8601 timestamp |
| `recorded_timezone` | string | no | IANA timezone ID (e.g. "Australia/Sydney") |

### Response Shape

**Text ask response:**
```json
{
  "question": "When did I last swim with Justin?",
  "input_type": "text",
  "answer": "You discussed swimming with Justin on June 18...",
  "confidence": "medium",
  "citations": [
    {
      "source": "whatsapp",
      "date": "2026-06-18",
      "title": "Justin Wan",
      "snippet": "Me: swim tomoz?"
    }
  ],
  "searched": {
    "start_date": "2026-06-01",
    "end_date": "2026-06-18",
    "sources": ["summaries", "reminders", "events", "tasks", "whatsapp"],
    "result_count": 3
  }
}
```

**Voice ask response** adds:
```json
{
  "transcript": "When did I last swim with Justin?",
  "audio_duration_seconds": 3.2
}
```

### Error Responses (as previously documented)

| Status | Body | Meaning |
|--------|------|---------|
| 400 | `{"error": "Missing question"}` | Empty or absent question field |
| 400 | `{"error": "Audio query too long (75s). Max is 60s."}` | Voice file too long |
| 400 | `{"error": "No speech detected in query audio"}` | No speech in recording |
| 400 | `{"error": "scope must be valid JSON"}` | Scope field is not valid JSON |

---

## Issues to Investigate (Server-Side)

### Issue 1: Citations returned alongside negative answers

**Observed behaviour:** The app asks a question, and the server responds with an answer like *"No information found about that"* but also includes a non-empty `citations` array with items. This is contradictory — if there is genuinely no information, the citations array should be empty.

**App renders:** Both the answer text and the citation cards faithfully. The app has no logic to suppress citations based on answer content — it displays whatever the server returns.

**Likely root cause:** The LLM prompt on the server generates the answer and citations independently. When the LLM determines it cannot answer, it still returns whatever search results were found rather than clearing the citations array.

**Suggested server fix:** In the LLM prompt/response pipeline: when the final answer indicates no relevant information was found, set `"citations": []` (empty array) rather than returning partial or unrelated results.

---

### Issue 2: Future reminders not found by search

**Observed behaviour:** A test reminder was created for *tomorrow* with content about "swimming with Juz". The question *"when is the next time I'm swimming with Juz"* returned *"no information found"* despite the reminder existing on the server.

**What the app sends (no "Recent 30 days" toggle):**
```json
{
  "question": "when is the next time I'm swimming with Juz"
}
```
No `scope` field at all — the server should search everything, including future dates.

**What the app sends (with "Recent 30 days" toggle):**
```json
{
  "question": "when is the next time I'm swimming with Juz",
  "scope": {
    "start_date": "2026-05-20",
    "end_date": "2026-06-19"
  }
}
```
Note: this would exclude tomorrow's date, but the user tested without this toggle.

**Likely root causes (to investigate):**

1. **Future dates not indexed:** The server's search/index might only cover past dates (summaries, transcripts) and exclude future/upcoming reminders. The reminders table should be queried separately for upcoming items.

2. **Reminder source not included in default search:** When no `sources` filter is sent, the server should default to `["summaries", "reminders", "events", "tasks", "whatsapp"]`. Verify that `reminders` is included in the default.

3. **Reminder content not searchable:** The server might be searching reminder `title` fields but not `description` fields. The test content might be in one or the other.

4. **LLM not using search results:** The LLM might receive search results containing the reminder but fail to incorporate them into its answer.

**Suggested server investigation:**
- Check what the search/indexing layer returns for future-dated reminders
- Verify the default source list includes `reminders`
- Log the raw search results alongside the final answer to see if the reminder was found but the LLM ignored it
- Test with explicit scope: `{"sources": ["reminders"]}` to isolate whether the issue is source filtering

---

## App-Side Summary for Reference

The app code is open for inspection at `G:\android_projects\lifelogger`. Key files for this feature:

- `app/src/main/java/com/sam/lifelogger/data/AskModels.kt` — Request/response JSON parsing
- `app/src/main/java/com/sam/lifelogger/data/ApiClient.kt` — Lines near the bottom for `askText()` and `askAudio()` implementations
- `app/src/main/java/com/sam/lifelogger/ui/AskScreen.kt` — Full UI implementation

The app does not cache ask responses or modify them in any way — everything displayed comes directly from the server response JSON.
