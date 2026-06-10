# Editable Summaries — Design Spec

## Overview

Allow users to edit the AI-generated daily summary markdown (and optional title) directly within the `SummaryDetailScreen`, with inline editing, explicit Save/Cancel, and a visual indicator when a summary has been manually edited.

## API Contract

### GET /api/summaries/{date} (existing, enhanced)

Current response fields (unchanged):
- `summary` (string) — markdown content
- `segment_count` (int)
- `total_duration_seconds` (double)

New fields added by server:
- `title` (string | null) — AI-generated or user-edited title
- `edited_at` (string | null) — ISO 8601 timestamp of last edit, null if never edited
- `is_edited` (boolean) — convenience flag

The app uses `optString`/`optBoolean` defaults so missing fields are handled gracefully (no badge shown).

### PUT /api/summaries/{date}

Request:
```json
{
  "markdown": "the edited summary text...",
  "title": "optional new title"
}
```

Response:
```json
{
  "status": "ok",
  "date": "2026-03-25",
  "edited_at": "2026-05-29T14:30:00Z"
}
```

Both `markdown` and `title` are optional in the request — omit either to leave it unchanged.

## UI Changes

### SummaryDetailScreen — Two modes

#### View Mode (default)
```
TopAppBar: [←]  Title (or date fallback if no title)
                date as subtitle          [📄 transcripts icon]
─────────────────────────────────────
Stats Card: Recordings count | Duration
─────────────────────────────────────
[Edit Button]  ← only in view mode, placed between stats and content
                                     ↓
(Edited badge)  ← only shown if is_edited==true
 "Last edited: 29 May 2026, 14:30"
 with subtle tinted background surface
─────────────────────────────────────
Markdown summary content (rendered, scrollable)
─────────────────────────────────────
[View raw transcripts button]
```

#### Edit Mode (toggled by Edit)
```
TopAppBar: unchanged
─────────────────────────────────────
Stats Card: unchanged
─────────────────────────────────────
Title TextField  (pre-filled with current title, single-line)
Markdown body TextField  (pre-filled, multi-line, full height)

[Save Button]  [Cancel Button]  ← side by side, replacing Edit button

(If saving: show progress indicator on Save button)
(If save fails: Snackbar with "Save failed — tap to retry")
─────────────────────────────────────
[View raw transcripts button]  ← still visible
```

- **Edit** → transitions to edit mode, swaps markdown rendering for text fields
- **Save** → PUTs to server, shows saving state, on success returns to view mode with refreshed data (including new `edited_at`)
- **Cancel** → discards changes, returns to view mode

### Edited Indicator

- Rendered as a small `Surface` with a tinted container color (e.g. `tertiaryContainer`)
- Text: `"Last edited: 29 May 2026, 14:30"` — formatted from ISO 8601 to readable local format
- Only visible when `is_edited == true` and `edited_at` is non-null

## Files Changed

| File | Change |
|------|--------|
| `data/ApiClient.kt` | Add `updateSummary(context, date, markdown, title?): String` — PUT call |
| `ui/SummaryDetailScreen.kt` | Add edit/view mode toggle, title display, title + body text fields, Save/Cancel buttons, edited indicator, save error handling |

## Edge Cases & States

| State | Behavior |
|-------|----------|
| Loading summary | Existing spinner — unchanged |
| Error loading summary | Existing error display — unchanged |
| Saving (PUT in flight) | Save button shows `CircularProgressIndicator` (small), both buttons disabled |
| Save succeeds | Refresh GET data, show updated `edited_at`, return to view mode |
| Save fails (network error) | Stay in edit mode, show Snackbar with retry action |
| Save fails (server error 4xx/5xx) | Same as network error — Snackbar with message |
| `edited_at` / `is_edited` absent from GET | Default to null/false — no badge shown (backward compat) |
| Title absent from GET | Fall back to date string in top bar |
| User edits title only, not markdown | Works — sends `{"title": "...", "markdown": "..."}` with current markdown |
| User clears the title field | Send empty string — server decides whether to accept |

## No Local DB Changes

All summary data lives on the server. The app never caches summaries locally. Edited state is purely server-driven.
