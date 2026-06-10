# Next Session Kickoff Prompt

Use this prompt to start the next implementation session.

```text
You are working in G:\android_projects\lifelogger on the Android app Lifelogger.

First read:
1. HANDOVER.md
2. docs/server-contracts/reminder-calendar-notification-contract-2026-06-09.md
3. docs/superpowers/specs/2026-06-09-calendar-agenda-month-map-design.md
4. docs/superpowers/plans/2026-06-09-calendar-agenda-month-map.md

Current app state:
- The reminder system is implemented app-side.
- The home dashboard refresh is implemented and installed.
- Reminder dashboard times display Australia/Sydney local time with DST.
- Session notes and summaries use server-provided HTML fields where available.
- Reminder end dates are supported app-side and in local cache.
- Needs Review reminders can be cancelled with a confirmation dialog.
- Life Calendar is implemented as an agenda view with Week/Month/All selector and a compact bottom month map.
- Month map has per-reminder colours, multi-day coverage, same-day colour splits, +N overflow, and a white border for today.
- Multiple notification controls are implemented app-side in EditReminderScreen:
  - None
  - At time
  - Custom, up to 3 offsets before reminder start time using minutes/hours/days
- The app calls PUT /api/reminders/{id}/notifications to replace or clear notification jobs.
- The latest debug APK was installed on Pixel 2 XL - 15 after passing compile, unit tests, assemble, and install.

Important server dependency:
- The server team is implementing PUT /api/reminders/{id}/notifications in parallel.
- Until that endpoint exists, reminder saves that attempt notification replacement may fail after PATCH /api/reminders/{id}.
- The server contract is documented at docs/server-contracts/reminder-calendar-notification-contract-2026-06-09.md.

Likely next work:
1. Test reminder edit/save once the server notification endpoint is deployed.
2. If endpoint is delayed, add a temporary fallback/warning around notification replacement.
3. Start Habit reminders.
4. Start simple Workout tracker.
5. Later: LLM query API integration.
6. Later: Financial alerts/news API.

Useful commands:
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
adb logcat -s MainActivity ReminderSync ReminderNotification ReminderBroadcast RecordingService UploadWorker

Workspace note:
- This folder currently reports fatal: not a git repository. Do not rely on git diff/status unless .git is restored.

Proceed by reading the handover and then ask what the user wants to work on next, unless they already gave a specific task.
```
