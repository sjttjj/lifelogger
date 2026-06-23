package com.sam.lifelogger

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.sam.lifelogger.recording.RecordingMode
import com.sam.lifelogger.recording.RecordingModeCoordinator
import com.sam.lifelogger.recording.RecordingService
import com.sam.lifelogger.squeeze.SqueezeActionMapper
import com.sam.lifelogger.squeeze.VisibleSqueezeActionBridge
import com.sam.lifelogger.notification.ReminderNotificationHelper
import com.sam.lifelogger.ui.RecordingsScreen
import com.sam.lifelogger.ui.theme.LifeloggerTheme
import com.sam.lifelogger.data.uploadPendingNow
import com.sam.lifelogger.data.WeatherResponse
import com.sam.lifelogger.data.parseWeatherResponse
import com.sam.lifelogger.ui.WeatherDashboardContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.sam.lifelogger.ui.ViewerScreen
import com.sam.lifelogger.ui.SummaryDetailScreen
import com.sam.lifelogger.ui.TranscriptScreen
import com.sam.lifelogger.ui.SearchScreen
import com.sam.lifelogger.ui.PromptEditorScreen
import com.sam.lifelogger.ui.SettingsScreen
import com.sam.lifelogger.ui.EditReminderScreen
import com.sam.lifelogger.ui.SessionDetailScreen
import com.sam.lifelogger.ui.LifeCalendarScreen
import com.sam.lifelogger.data.ApiClient
import com.sam.lifelogger.data.DashboardReminderSelector
import com.sam.lifelogger.data.PromptSyncManager
import com.sam.lifelogger.data.Reminder
import com.sam.lifelogger.data.ReminderDisplayFormatter
import com.sam.lifelogger.data.ReminderSyncManager
import com.sam.lifelogger.data.ReminderSyncWorker
import com.sam.lifelogger.ui.AskScreen

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var pendingAudioPermissionMode: RecordingMode? = null

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val mode = pendingAudioPermissionMode ?: RecordingMode.NORMAL
            pendingAudioPermissionMode = null
            if (granted) {
                startRecordingAfterAudioPermission(mode)
            } else {
                Toast.makeText(this, "Microphone permission is required to record", Toast.LENGTH_SHORT).show()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val exactAlarmPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Observable Compose state that tracks which recording mode is active.
     * Updated by the coordinator sink callbacks so the UI stays in sync.
     */
    private val activeModeState = mutableStateOf<RecordingMode?>(null)

    private val recordingModeCoordinator by lazy {
        RecordingModeCoordinator(
            object : RecordingModeCoordinator.RecordingSink {
                override fun start(mode: RecordingMode) {
                    activeModeState.value = mode
                    startRecordingMode(mode)
                }

                override fun switchMode(mode: RecordingMode) {
                    activeModeState.value = mode
                    switchRecordingMode(mode)
                }

                override fun stop() {
                    activeModeState.value = null
                    stopRecordingService()
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sync prompt templates from the server so the picker is up to date
        lifecycleScope.launch {
            PromptSyncManager.sync(this@MainActivity)
            registerReminderDevice()
        }
        ReminderNotificationHelper.createNotificationChannel(this)
        ReminderSyncWorker.enqueuePeriodic(this)

        // Stale prefs cleanup: if the service crashed, is_recording may be stuck at
        // true even though nothing is running. Reset it so the UI and squeeze logic
        // are consistent.
        if (!isServiceRunning(RecordingService::class.java)) {
            val p = getSharedPreferences("lifelogger_prefs", Context.MODE_PRIVATE)
            if (p.getBoolean("is_recording", false)) {
                Log.w(TAG, "Stale is_recording=true detected - resetting")
                p.edit()
                    .putBoolean("is_recording", false)
                    .remove("recording_started_at")
                    .remove("current_chunk_start")
                    .remove("current_chunk_length")
                    .remove("chunk_length_ms")
                    .remove("active_recording_mode")
                    .remove("normal_requested")
                    .remove("session_requested")
                    .remove("active_session_id")
                    .apply()
            }
        } else {
            restoreRecordingCoordinatorState()
        }

        // Handle squeeze-triggered intent if this is a fresh launch
        handleSqueezeIntent(intent)

        // Immersive mode - hide status + navigation bars, swipe to reveal.
        // Deferred via decorView.post to avoid NPE when the DecorView isn't attached yet.
        window.decorView.post {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        requestNotificationPermissionIfNeeded()

        setContent {
            LifeloggerTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val prefs = remember {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }

                // WorkManager for auto-sync status
                val workManager = remember { WorkManager.getInstance(context) }

                // We hold the current list of auto-upload works in state
                var autoWorkInfos by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }

                // Attach a LiveData observer to WorkManager tag "auto-upload"
                DisposableEffect(workManager) {
                    val liveData = workManager.getWorkInfosByTagLiveData("auto-upload")
                    val observer = Observer<List<WorkInfo>> { infos ->
                        autoWorkInfos = infos ?: emptyList()
                    }
                    liveData.observeForever(observer)

                    onDispose {
                        liveData.removeObserver(observer)
                    }
                }

                // --- UI state ---
                var manualSyncStatus by remember { mutableStateOf<String?>(null) }
                var autoSyncStatus by remember { mutableStateOf<String?>(null) }
                var batteryLevel by remember { mutableStateOf<Int?>(null) }
                var batteryTemp by remember { mutableStateOf<Float?>(null) }
                var currentTime by remember { mutableStateOf("00:00") }
                var timeToNextChunk by remember { mutableStateOf<String?>(null) }

                // Combined sync status: manual overrides auto, then fallback to Idle
                val syncStatus = manualSyncStatus ?: autoSyncStatus ?: "Idle"

                // Time updater - ticks every second
                LaunchedEffect(Unit) {
                    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                    while (true) {
                        currentTime = format.format(Date())
                        delay(1_000L)
                    }
                }

                // Battery + temp updater - every 30 seconds
                LaunchedEffect(Unit) {
                    while (true) {
                        try {
                            val bm =
                                context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                            val level =
                                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

                            val batteryIntent = context.registerReceiver(
                                null,
                                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                            )
                            val tempTenth =
                                batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                                    ?: 0
                            val tempC = tempTenth / 10f

                            batteryLevel = level
                            batteryTemp = tempC
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error reading battery info", e)
                        }

                        delay(30_000L)
                    }
                }


                // Weather state
                var weatherData by remember { mutableStateOf<WeatherResponse?>(null) }

                // Fetch weather on launch and every 10 minutes
                LaunchedEffect(Unit) {
                    while (true) {
                        try {
                            val json = ApiClient.getWeather(context)
                            weatherData = parseWeatherResponse(json)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to fetch weather", e)
                            weatherData = null
                        }
                        delay(600_000L) // 10 minutes
                    }
                }

                // Next-chunk countdown - reads shared prefs written by RecordingService
                LaunchedEffect(Unit) {
                    while (true) {
                        val isRecording = prefs.getBoolean(KEY_IS_RECORDING, false)
                        if (isRecording) {
                            val start = prefs.getLong(KEY_CHUNK_START, 0L)
                            val length = prefs.getLong(KEY_CHUNK_LENGTH, 0L)

                            if (start > 0L && length > 0L) {
                                val elapsed = System.currentTimeMillis() - start
                                val remaining = length - elapsed
                                timeToNextChunk = if (remaining > 0L) {
                                    formatMsToMmSs(remaining)
                                } else {
                                    "soon"
                                }
                            } else {
                                timeToNextChunk = null
                            }
                        } else {
                            timeToNextChunk = null
                        }
                        delay(1_000L)
                    }
                }

                // Auto-sync status, driven by WorkManager
                LaunchedEffect(autoWorkInfos) {
                    if (autoWorkInfos.isEmpty()) {
                        autoSyncStatus = null
                    } else {
                        val running = autoWorkInfos.any { it.state == WorkInfo.State.RUNNING }
                        val enqueued = autoWorkInfos.any {
                            it.state == WorkInfo.State.ENQUEUED ||
                                    it.state == WorkInfo.State.BLOCKED
                        }
                        val failed = autoWorkInfos.any { it.state == WorkInfo.State.FAILED }

                        autoSyncStatus = when {
                            running -> "Auto sync in progress..."
                            enqueued -> "Auto sync queued..."
                            failed -> "Auto sync failed - will retry"
                            else -> null
                        }
                    }
                }

                AppNavigation(
                    activeRecordingMode = activeModeState.value,
                    onStopActiveMode = { stopReminderRecordingFromUi() },
                    onStartRecording = {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            toggleNormalRecording()
                        } else {
                            pendingAudioPermissionMode = RecordingMode.NORMAL
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopRecording = {
                        // Stop the right mode based on what's active
                        when (recordingModeCoordinator.activeMode) {
                            RecordingMode.REMINDER -> stopReminderRecordingFromUi()
                            RecordingMode.SESSION -> toggleSessionRecording()
                            else -> toggleNormalRecording()
                        }
                    },
                    onUploadPending = {
                        scope.launch {
                            manualSyncStatus = "Checking pending recordings..."

                            try {
                                uploadPendingNow(context.applicationContext)
                                manualSyncStatus = "All recordings synced"
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Manual sync error", e)
                                manualSyncStatus = "Sync error - will retry automatically"
                            }

                            delay(3_000L)
                            manualSyncStatus = null
                        }
                    },
                    syncStatus = syncStatus,
                    batteryLevel = batteryLevel,
                    batteryTemp = batteryTemp,
                    currentTime = currentTime,
                    timeToNextChunk = timeToNextChunk,
                    weatherData = weatherData
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        VisibleSqueezeActionBridge.register(
            object : VisibleSqueezeActionBridge.Handler {
                override fun toggleRecording() {
                    toggleNormalRecording()
                }

                override fun toggleSessionRecording() {
                    Log.d(TAG, "Session recording toggled via squeeze")
                    this@MainActivity.toggleSessionRecording()
                }

                override fun startReminderRecording(): Boolean {
                    Log.d(TAG, "Reminder recording started via squeeze")
                    return startReminderRecordingFromUi()
                }

                override fun stopReminderRecording(): Boolean {
                    val stopped = stopReminderRecordingFromUi()
                    if (stopped) {
                        Log.d(TAG, "Reminder recording stopped")
                    }
                    return stopped
                }

                override fun continuousSqueezeStarted() {
                    Log.d(TAG, "Continuous squeeze detected - reminder recording started")
                    Toast.makeText(
                        this@MainActivity,
                        "Reminder recording started",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun continuousSqueezeEnded() {
                    Log.d(TAG, "Continuous squeeze released but reminder is latched")
                }

                override fun reminderModeTimedOut() {
                    Log.d(TAG, "Reminder mode timed out after 2 minutes")
                    Toast.makeText(
                        this@MainActivity,
                        "Reminder ended after 2 minutes",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    override fun onPause() {
        VisibleSqueezeActionBridge.unregister()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSqueezeIntent(intent)
    }

    /**
     * Check if this intent was sent by [SqueezeActionMapper] to toggle recording.
     * Returns true if the intent was handled (prevents further processing).
     */
    private fun handleSqueezeIntent(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(SqueezeActionMapper.EXTRA_SQUEEZE_TOGGLE_RECORDING, false) != true) {
            return false
        }
        val action = intent.getStringExtra(SqueezeActionMapper.EXTRA_SQUEEZE_ACTION)
        Log.d(TAG, "handleSqueezeIntent: action=$action")
        when (action) {
            "start" -> toggleNormalRecording()
            "stop" -> toggleNormalRecording()
        }
        return true
    }

    private fun toggleNormalRecording() {
        if (!ensureAudioPermissionFor(RecordingMode.NORMAL)) return
        recordingModeCoordinator.toggleNormal()
        persistRecordingCoordinatorState()
    }

    private fun toggleSessionRecording() {
        if (!ensureAudioPermissionFor(RecordingMode.SESSION)) return
        recordingModeCoordinator.toggleSession()
        persistRecordingCoordinatorState()
    }

    private fun startReminderRecordingFromUi(): Boolean {
        if (!ensureAudioPermissionFor(RecordingMode.REMINDER)) return false
        val started = recordingModeCoordinator.startReminder()
        persistRecordingCoordinatorState()
        return started
    }

    private fun ensureAudioPermissionFor(mode: RecordingMode): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return true

        pendingAudioPermissionMode = mode
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        return false
    }

    private fun startRecordingAfterAudioPermission(mode: RecordingMode) {
        when (mode) {
            RecordingMode.NORMAL -> toggleNormalRecording()
            RecordingMode.SESSION -> toggleSessionRecording()
            RecordingMode.REMINDER -> {
                if (startReminderRecordingFromUi()) {
                    SqueezeActionMapper.startReminderModeTimerAfterPermissionGrant()
                }
            }
        }
    }

    private fun persistRecordingCoordinatorState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            val activeMode = recordingModeCoordinator.activeMode
            if (activeMode == null) remove(KEY_ACTIVE_RECORDING_MODE)
            else putString(KEY_ACTIVE_RECORDING_MODE, activeMode.wireValue)
            putBoolean(KEY_NORMAL_REQUESTED, recordingModeCoordinator.isNormalRequested)
            putBoolean(KEY_SESSION_REQUESTED, recordingModeCoordinator.isSessionRequested)
        }.apply()
    }

    private fun restoreRecordingCoordinatorState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_IS_RECORDING, false)) return

        val activeMode = prefs.getString(KEY_ACTIVE_RECORDING_MODE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { RecordingMode.fromWireValue(it) }
            ?: if (prefs.getString(KEY_ACTIVE_SESSION_ID, null) != null) {
                RecordingMode.SESSION
            } else {
                RecordingMode.NORMAL
            }

        val isNormalRequested = prefs.getBoolean(
            KEY_NORMAL_REQUESTED,
            activeMode == RecordingMode.NORMAL
        )
        val isSessionRequested = prefs.getBoolean(
            KEY_SESSION_REQUESTED,
            activeMode == RecordingMode.SESSION
        )

        recordingModeCoordinator.restoreState(
            activeMode = activeMode,
            isNormalRequested = isNormalRequested,
            isSessionRequested = isSessionRequested
        )
        activeModeState.value = activeMode
        Log.d(
            TAG,
            "Restored recording mode state active=$activeMode normal=$isNormalRequested session=$isSessionRequested"
        )
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // getRunningServices() returns at most max 100 services; limit is fine for this check.
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }

    private fun startRecordingMode(mode: RecordingMode) {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra(RecordingService.EXTRA_RECORDING_TYPE, mode.wireValue)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun switchRecordingMode(mode: RecordingMode) {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_SWITCH_MODE
            putExtra(RecordingService.EXTRA_RECORDING_TYPE, mode.wireValue)
        }
        startService(intent)
    }

    private fun stopRecordingService() {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        startService(intent)
    }

    private fun stopReminderRecordingFromUi(): Boolean {
        val stopped = recordingModeCoordinator.stopReminder()
        persistRecordingCoordinatorState()
        if (stopped) {
            SqueezeActionMapper.clearReminderModeTimer()
            Log.d(TAG, "Reminder recording stopped via UI")
        }
        return stopped
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                exactAlarmPermissionLauncher.launch(Manifest.permission.SCHEDULE_EXACT_ALARM)
            }
        }
    }

    private suspend fun registerReminderDevice() {
        try {
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ?: "android-${Build.MODEL}"
            ApiClient.registerDevice(
                context = this,
                deviceId = androidId,
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Reminder device registration failed", e)
        }
    }
}

@Composable
fun BlackOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.background,   // dark
            contentColor = MaterialTheme.colorScheme.onBackground    // light text
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground),
        contentPadding = PaddingValues(vertical = 12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(text)
    }
}

/**
 * Top-level composable that handles navigation between:
 * - MainScreen (recording controls)
 * - RecordingsScreen (list + playback)
 */
@Composable
fun AppNavigation(
    activeRecordingMode: RecordingMode?,
    onStopActiveMode: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onUploadPending: () -> Unit,
    onOpenAsk: () -> Unit = {},
    onOpenPrompts: () -> Unit = {},
    syncStatus: String,
    batteryLevel: Int?,
    batteryTemp: Float?,
    currentTime: String,
    timeToNextChunk: String?,
    weatherData: WeatherResponse?
) {
    val navController = rememberNavController()
    var lastSearchQuery by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {

        composable("main") {
            MainScreen(
                activeRecordingMode = activeRecordingMode,
                onStopActiveMode = onStopActiveMode,
                onStart = onStartRecording,
                onStop = onStopRecording,
                onOpenRecordings = { navController.navigate("recordings") },
                onOpenViewer = { navController.navigate("viewer")},
                onOpenSettings = { navController.navigate("settings")},
                onOpenAsk = { navController.navigate("ask") },
                onOpenCalendar = { navController.navigate("calendar") },
                syncStatus = syncStatus,
                batteryLevel = batteryLevel,
                batteryTemp = batteryTemp,
                currentTime = currentTime,
                timeToNextChunk = timeToNextChunk,
                weatherData = weatherData
            )
        }

        composable("recordings") {
            RecordingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("viewer") {
            ViewerScreen(
                onBack = { navController.popBackStack() },
                onDateSelected = { date -> navController.navigate("summary/$date") },
                onSearch = { navController.navigate("search") }
            )
        }

        composable("summary/{date}") { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            SummaryDetailScreen(
                date = date,
                onBack = { navController.popBackStack() },
                onViewTranscripts = { d -> navController.navigate("transcripts/$d") },
                onOpenSession = { d, filename ->
                    navController.navigate("sessions/$d/${Uri.encode(filename)}")
                }
            )
        }

        composable("sessions/{date}/{filename}") { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            val filename = backStackEntry.arguments?.getString("filename")?.let { Uri.decode(it) } ?: ""
            SessionDetailScreen(
                date = date,
                filename = filename,
                onBack = { navController.popBackStack() }
            )
        }

        composable("transcripts/{date}") { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            TranscriptScreen(
                date = date,
                onBack = { navController.popBackStack() }
            )
        }

        composable("transcripts/{date}/search") { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            TranscriptScreen(
                date = date,
                searchQuery = lastSearchQuery,
                onBack = { navController.popBackStack() }
            )
        }

        composable("search") {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onDateSelected = { date -> navController.navigate("summary/$date") },
                onTranscriptsSelected = { date, query ->
                    lastSearchQuery = query
                    navController.navigate("transcripts/$date/search")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNewPrompt = { navController.navigate("prompts/new") },
                onUploadPending = onUploadPending
            )
        }

        composable("prompts/new") {
            PromptEditorScreen(
                onBack = { navController.popBackStack() },
                onCreated = {
                    // Navigate back to the picker after creation
                    navController.popBackStack("prompts", inclusive = false)
                }
            )
        }

        composable("calendar") {
            LifeCalendarScreen(
                onBack = { navController.popBackStack() },
                onOpenReminder = { id -> navController.navigate("reminders/edit/$id") }
            )
        }

        composable("reminders/edit/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            EditReminderScreen(
                reminderId = id,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable("ask") {
            AskScreen(
                onBack = { navController.popBackStack() }
            )
        }
        }
    }

@Composable
fun MainScreen(
    activeRecordingMode: RecordingMode?,
    onStopActiveMode: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenViewer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAsk: () -> Unit = {},
    onOpenPrompts: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    syncStatus: String,
    batteryLevel: Int?,
    batteryTemp: Float?,
    currentTime: String,
    timeToNextChunk: String?,
    weatherData: WeatherResponse?
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("lifelogger_prefs", Context.MODE_PRIVATE)
    }

    var isRecording by remember { mutableStateOf(prefs.getBoolean(KEY_IS_RECORDING, false)) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var dashboardReminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }

    // Recording state and timer are service-owned so button and squeeze paths stay in sync.
    LaunchedEffect(Unit) {
        while (true) {
            val recording = prefs.getBoolean(KEY_IS_RECORDING, false)
            isRecording = recording
            if (recording) {
                val startTime = prefs.getLong(KEY_RECORDING_STARTED_AT, 0L)
                elapsedSeconds = if (startTime > 0L) {
                    (System.currentTimeMillis() - startTime).coerceAtLeast(0L) / 1_000L
                } else {
                    0L
                }
            } else {
                elapsedSeconds = 0L
            }
            delay(1_000L)
        }
    }

    LaunchedEffect(Unit) {
        dashboardReminders = DashboardReminderSelector.selectPressing(
            ReminderSyncManager.syncUpcomingOrUseCache(context)
        )
    }

    val timerText = if (isRecording) formatElapsed(elapsedSeconds) else "00:00"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            HomeTitleRow(
                onOpenSettings = onOpenSettings,
                batteryLevel = batteryLevel,
                batteryTemp = batteryTemp
            )

            DashboardCarousel(
                reminders = dashboardReminders,
                weatherData = weatherData,
                onOpenCalendar = onOpenCalendar,
                onOpenAsk = onOpenAsk
            )
            RecordingStatusStrip(
                isRecording = isRecording,
                activeRecordingMode = activeRecordingMode,
                timerText = timerText,
                syncStatus = syncStatus,
                timeToNextChunk = timeToNextChunk,
                onStopActiveMode = onStopActiveMode,
                onStop = onStop
            )
            HomeActionGrid(
                isRecording = isRecording,
                onRecordClick = {
                    if (isRecording) onStop() else onStart()
                },
                onOpenViewer = onOpenViewer,
                onOpenCalendar = onOpenCalendar,
                onOpenRecordings = onOpenRecordings
            )
        }
    }
}
@Composable
private fun HomeTitleRow(
    onOpenSettings: () -> Unit,
    batteryLevel: Int?,
    batteryTemp: Float?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lifelogger",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.SettingsIcon, contentDescription = "Settings")
            }
        }
        // Battery & temperature status
        val batteryText = buildString {
            if (batteryLevel != null) {
                append("Battery ${batteryLevel}%")
            }
            if (batteryTemp != null) {
                if (isNotEmpty()) append("  ·  ")
                append("Temp ${"%.1f".format(batteryTemp)}\u00B0C")
            }
            if (isEmpty()) {
                append("Battery info unavailable")
            }
        }
        Text(
            text = batteryText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardCarousel(
    reminders: List<Reminder>,
    weatherData: WeatherResponse?,
    onOpenCalendar: () -> Unit,
    onOpenAsk: () -> Unit = {},
) {
    val moduleCount = 4
    val pagerState = rememberPagerState(pageCount = { moduleCount })

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Swipe",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalPager(
            state = pagerState,
            pageSpacing = 18.dp,
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> ReminderDashboardCard(reminders, onOpenCalendar)
                1 -> WeatherDashboardCard(weatherData)
                2 -> PlaceholderDashboardCard(
                    title = "Finance",
                    items = listOf(
                        "Cashflow snapshot" to "Future integration",
                        "Bills and alerts" to "Future money reminders"
                    )
                )
                3 -> AskDashboardCard(
                    onOpenAsk = onOpenAsk
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(moduleCount) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun ReminderDashboardCard(
    reminders: List<Reminder>,
    onOpenCalendar: () -> Unit
) {
    DashboardCard(
        title = "Reminders",
        chip = if (reminders.isEmpty()) "Clear" else "${reminders.size} pressing",
        onClick = onOpenCalendar
    ) {
        if (reminders.isEmpty()) {
            Text(
                text = "No upcoming reminders",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                reminders.forEach { reminder ->
                    MiniDashboardItem(
                        title = reminder.title,
                        subtitle = ReminderDisplayFormatter.dashboardDateContext(reminder),
                        trailing = ReminderDisplayFormatter.dashboardTime(reminder)
                    )
                }
            }
        }
    }
}


@Composable
private fun AskDashboardCard(
    onOpenAsk: () -> Unit
) {
    DashboardCard(
        title = "Ask AI",
        chip = "Ask now",
        enabled = true,
        onClick = onOpenAsk
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MiniDashboardItem(
                title = "Ask across notes",
                subtitle = "Summaries, reminders, events, tasks, WhatsApp",
                trailing = ""
            )
        }
    }
}

@Composable
private fun PlaceholderDashboardCard(
    title: String,
    items: List<Pair<String, String>>
) {
    DashboardCard(
        title = title,
        chip = "Soon",
        enabled = false
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items.forEach { (itemTitle, itemSubtitle) ->
                MiniDashboardItem(
                    title = itemTitle,
                    subtitle = itemSubtitle,
                    trailing = ""
                )
            }
        }
    }
}

@Composable
private fun WeatherDashboardCard(
    weather: WeatherResponse?
) {
    DashboardCard(
        title = if (weather != null) weather.location.name else "Weather",
        chip = if (weather != null) "Feels like " + weather.current.apparentTemperatureC.toInt().toString() + "\u00B0C" else "Unavailable",
        enabled = false
    ) {
        if (weather != null) {
            WeatherDashboardContent(weather)
        } else {
            Text(
                text = "Weather unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    chip: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 146.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(chip) }
                )
            }
            content()
        }
    }
}

@Composable
private fun MiniDashboardItem(
    title: String,
    subtitle: String,
    trailing: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing.isNotBlank()) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun RecordingStatusStrip(
    isRecording: Boolean,
    activeRecordingMode: RecordingMode?,
    timerText: String,
    syncStatus: String,
    timeToNextChunk: String?,
    onStopActiveMode: () -> Unit,
    onStop: () -> Unit
) {
    val modeText = if (isRecording) {
        when (activeRecordingMode) {
            RecordingMode.SESSION -> "Recording session"
            RecordingMode.REMINDER -> "Recording reminder"
            else -> "Recording"
        }
    } else {
        "Ready"
    }
    val detailText = if (isRecording) "Elapsed $timerText" else null

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modeText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (detailText != null) {
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isRecording) {
                    IconButton(
                        onClick = {
                            if (activeRecordingMode == RecordingMode.REMINDER) onStopActiveMode() else onStop()
                        }
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop recording")
                    }
                } else {
                    Text(
                        text = "00:00",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Sync: $syncStatus",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Next chunk: ${timeToNextChunk ?: "Idle"}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeActionGrid(
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    onOpenViewer: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenRecordings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HomeActionButton(
                label = if (isRecording) "Stop" else "Record",
                icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                primary = true,
                onClick = onRecordClick,
                modifier = Modifier.weight(1f)
            )
            HomeActionButton("Life Log", Icons.Filled.ViewList, onOpenViewer, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HomeActionButton("Calendar", Icons.Filled.Event, onOpenCalendar, Modifier.weight(1f))
            HomeActionButton("Recordings", Icons.Filled.LibraryMusic, onOpenRecordings, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (primary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/** Simple MM:SS formatter for the recording timer. */
private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/** MM:SS formatter for milliseconds (for next-chunk display). */
private fun formatMsToMmSs(ms: Long): String {
    val totalSeconds = ms / 1_000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// Keys shared with RecordingService
private const val PREFS_NAME = "lifelogger_prefs"
private const val KEY_IS_RECORDING = "is_recording"
private const val KEY_RECORDING_STARTED_AT = "recording_started_at"
private const val KEY_CHUNK_START = "current_chunk_start"
private const val KEY_CHUNK_LENGTH = "chunk_length_ms"
private const val KEY_ACTIVE_RECORDING_MODE = "active_recording_mode"
private const val KEY_NORMAL_REQUESTED = "normal_requested"
private const val KEY_SESSION_REQUESTED = "session_requested"
private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
