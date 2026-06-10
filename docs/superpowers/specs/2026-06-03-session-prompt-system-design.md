# Session Prompt System Design

**Date:** 2026-06-03
**Status:** Approved for implementation

## Overview

Add a system for creating and managing session processing prompts. Users create prompts in the Android app, which are synced to the server. When uploading a session recording, the app sends a `prompt_id` so the server knows which prompt to apply when processing that session's transcript into structured notes.

This is the phone side of a phone+server feature. The server endpoints (`GET /api/prompts`, `POST /api/prompts`) are being built in parallel.

## Architecture

```
┌─────────────────────────┐           ┌───────────────────────────┐
│       Android App       │           │     Ubuntu Server         │
│                         │           │                           │
│  ┌───────────────────┐  │  POST     │  ┌─────────────────────┐  │
│  │ SessionRecordingUI │  │  /api/   │  │  prompts.json       │  │
│  │ (prompt picker)    │──┼─────────►│  │  (JSON file store)  │  │
│  └────────┬──────────┘  │  prompts  │  └─────────────────────┘  │
│           │             │           │                           │
│  ┌────────▼──────────┐  │  GET      │  ┌─────────────────────┐  │
│  │ PromptSyncManager  │◄─┼──────────│  │  POST /transcribe   │  │
│  │ (sync + cache)     │  │  /api/   │  │  (reads prompt_id   │  │
│  └────────┬──────────┘  │  prompts  │  │   from upload)      │  │
│           │             │           │  └─────────────────────┘  │
│  ┌────────▼──────────┐  │           │                           │
│  │ Room (cached       │  │           │                           │
│  │  prompts table)    │  │           │                           │
│  └───────────────────┘  │           │                           │
│           │             │           │                           │
│  ┌────────▼──────────┐  │           │                           │
│  │ UploadRecordings   │  │  POST     │                           │
│  │ Worker (sends      │──┼─────────►│  /transcribe              │
│  │  prompt_id field)  │  │           │  +prompt_id              │
│  └───────────────────┘  │           │                           │
└─────────────────────────┘           └───────────────────────────┘
```

## Server Endpoints

### GET /api/prompts
Returns `{ "prompts": [...] }` — the full list of prompt objects stored in `prompts.json`.

### POST /api/prompts
Accepts `{ "id": "slug", "name": "...", "prompt": "...", "is_default": false }`.
Appends to or updates `prompts.json`. No delete for v1.

### Upload field
The `POST /transcribe` endpoint will accept an optional `prompt_id` field. If present and recognized, the server uses that prompt when processing the session. If absent or unrecognized, it falls back to the `is_default: true` prompt or the server's built-in default.

## Phone-Side Data

### Room Entity (`cached_prompts` table)

```kotlin
@Entity(tableName = "cached_prompts")
data class CachedPromptEntity(
    @PrimaryKey val id: String,
    val name: String,
    val prompt: String,
    val isDefault: Boolean,
    val updatedAt: Long
)
```

### PromptDao

- `getAllPrompts(): Flow<List<CachedPromptEntity>>` — live list for UI
- `getPromptById(id: String): CachedPromptEntity?` — sync lookup
- `replaceAll(prompts: List<CachedPromptEntity>)` — clears and re-inserts on sync
- `getDefaultPrompt(): CachedPromptEntity?` — picks the one with isDefault=true, or first

### Database Migration

Version 2 → 3, adding the `cached_prompts` table.

## PromptSyncManager

A class that wraps the sync logic:

- `sync()` — calls `GET /api/prompts`, deserializes response, calls `dao.replaceAll()`
- `createPrompt(name, prompt, isDefault)` — POSTs to server, then triggers sync
- `getAllPrompts(): Flow<List<CachedPromptEntity>>` — delegates to DAO
- Call `sync()` on app launch (in `MainActivity.onCreate` or an initializer)
- If sync fails (no network), the cache from last successful sync is used — the Flow still emits cached data

## Prompt Picker UI

A new screen or bottom sheet accessible from the main screen when preparing to start a session recording:

- **Prompt list screen**: Shows all synced prompts as cards (name + preview snippet)
- **Default pre-selection**: The default prompt is pre-selected; user can change it before starting a session recording
- **Create button**: Opens an editor for name + prompt text (similar to SummaryDetailScreen's editable markdown)
- **Persistent selection**: The selected prompt ID is stored in a preference so it persists between sessions
- **Visual indicator**: The currently selected prompt name is shown on the main screen when session recording is active

### Prompt Editor
A full-screen editor with:
- **Name field** — text input for the prompt display name
- **Prompt text area** — multiline text editor, same pattern as summary editing
- **Auto-generate ID** — derived from name (lowercase, hyphens)

## Upload Changes

### RecordingEntity / upload metadata
Add a `promptId` field to the upload metadata. It's nullable — only session recordings will have one set.

### UploadRecordingsWorker
When uploading a recording where `type == "session"` and a `prompt_id` is set, send the field as additional multipart form data alongside `type`, `recorded_at_*` fields, etc.

## File Changes Summary

| File | Change |
|------|--------|
| `app/.../data/CachedPromptEntity.kt` | New — Room entity |
| `app/.../data/PromptDao.kt` | New — DAO interface |
| `app/.../data/AppDatabase.kt` | Add entity, migration 2→3 |
| `app/.../data/PromptSyncManager.kt` | New — sync + create logic |
| `app/.../data/ApiClient.kt` | Add prompt sync endpoints |
| `app/.../ui/PromptPickerScreen.kt` | New — prompt list + editor |
| `app/.../ui/PromptEditorScreen.kt` | New — create/edit prompt |
| `app/.../ui/MainScreen.kt` | Add prompt picker entry point, show current prompt |
| `app/.../data/RecordingUploadMetadata.kt` | Add promptId field |
| `app/.../data/UploadRecordingsWorker.kt` | Send prompt_id for sessions |
| `app/.../recording/RecordingService.kt` | Update intent extras accepted |

## Testing

- Unit test `PromptDao` with in-memory Room
- Unit test `PromptSyncManager` with mock API client
- Unit test upload metadata includes `promptId` when set
- Manual test: sync prompts from server, create a new prompt, verify it appears in list and is sent with upload