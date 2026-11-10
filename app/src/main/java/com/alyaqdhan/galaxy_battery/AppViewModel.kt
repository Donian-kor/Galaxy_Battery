package com.alyaqdhan.galaxy_battery

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.alyaqdhan.galaxy_battery.data.BatteryRepository
import com.alyaqdhan.galaxy_battery.viewmodel.FolderState
import com.alyaqdhan.galaxy_battery.viewmodel.GaugeViewModel
import com.alyaqdhan.galaxy_battery.viewmodel.DialCodeViewModel

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    private val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
    private val repository = BatteryRepository(context, prefs)

    // ── Shared app state ──────────────────────────────────────────────────────
    var folderState      by mutableStateOf(FolderState())        ; private set
    var batteryInfo      by mutableStateOf<BatteryInfo?>(null)   ; private set

    var folderUri: Uri?
        get() = folderState.folderUri
        private set(value) { folderState = folderState.copy(folderUri = value) }
    var alreadyHasPerm: Boolean
        get() = folderState.alreadyHasPerm
        private set(value) { folderState = folderState.copy(alreadyHasPerm = value) }
    var folderAccessible: Boolean
        get() = folderState.folderAccessible
        private set(value) { folderState = folderState.copy(folderAccessible = value) }
    var hasEverScanned: Boolean
        get() = folderState.hasEverScanned
        private set(value) { folderState = folderState.copy(hasEverScanned = value) }
    var isRefreshing     by mutableStateOf(false)                ; private set
    var isLoadingDetail  by mutableStateOf(false)                ; private set

    // Log sheet
    var allLogEntries  by mutableStateOf<List<DocEntry>>(emptyList()) ; private set
    var showLogSheet   by mutableStateOf(false)                       ; private set

    // Raw log screen
    var rawLogText          by mutableStateOf("")    ; private set
    var isLoadingRaw        by mutableStateOf(false) ; private set
    var selectedLogFileName by mutableStateOf("")    ; private set

    // Error overlay
    var errorDialog by mutableStateOf<ErrorDialog?>(null) ; private set

    // Gauge animation signals (delegated to GaugeViewModel)
    private val gaugeVm = GaugeViewModel(getApplication())
    val gaugeAmplitude: Float get() = gaugeVm.gaugeAmplitude
    val gaugeReplayKey: Int get() = gaugeVm.gaugeReplayKey

    // ── Dial code ─────────────────────────────────────────────────────────────
    private val dialCodeVm = DialCodeViewModel(getApplication())

    val dialCode: String get() = dialCodeVm.dialCode

    fun saveDialCode(code: String) = dialCodeVm.saveDialCode(code)

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Called once from [MainActivity] on creation.
     * Returns true if a saved URI exists (show Dashboard),
     * false if first-run (caller should open SetupActivity).
     */
    fun initialize(): Boolean {
        val savedUriStr = prefs.getString("folder_uri", null)
        val savedUri    = savedUriStr?.toUri()
        folderUri       = savedUri
        releaseUnusedFolderGrants(keep = savedUri)

        if (savedUri == null) return false

        // Synchronous persisted-permission check (safe on main thread — in-memory list).
        // If permission was revoked since last launch, treat it as first-run and send
        // the user back to SetupActivity so they must re-grant access.
        val hasSyncPerm = context.contentResolver.persistedUriPermissions
            .any { it.uri.toString() == savedUri.toString() && it.isReadPermission }
        if (!hasSyncPerm) return false

        viewModelScope.launch {
            if (!repository.hasPersistedPermission(savedUri)) {
                // Shouldn't normally reach here (handled synchronously above),
                // but guard against race conditions.
                alreadyHasPerm   = false
                folderAccessible = false
                allLogEntries    = emptyList()
                isLoadingDetail  = false
                return@launch
            }

            alreadyHasPerm  = true
            isLoadingDetail = true

            val cached = repository.loadCachedBatteryInfo()
            if (cached != null) {
                batteryInfo      = cached
                folderAccessible = true
                hasEverScanned   = true

                val latestEntry = withContext(Dispatchers.IO) {
                    repository.findLatestLogEntry(savedUri)
                }
                if (latestEntry == null) {
                    folderAccessible = false
                    isLoadingDetail  = false
                    gaugeVm.incrementReplayKey()
                    errorDialog = ErrorDialog.FolderDeleted
                    return@launch
                }
                if (latestEntry.name != cached.logFileName) {
                    val liveInfo = withContext(Dispatchers.IO) {
                        repository.parseLatestLog(savedUri)
                    }
                    if (liveInfo.readSuccess) batteryInfo = liveInfo
                }
                isLoadingDetail = false

            } else {
                val latestEntry = withContext(Dispatchers.IO) {
                    repository.findLatestLogEntry(savedUri)
                }
                if (latestEntry == null) {
                    isLoadingDetail  = false
                    folderAccessible = false
                    errorDialog = ErrorDialog.FolderDeleted
                    return@launch
                }
                val info = withContext(Dispatchers.IO) {
                    repository.smartScan(savedUri)
                }
                isLoadingDetail  = false
                batteryInfo      = info
                folderAccessible = true
                if (info.readSuccess) {
                    hasEverScanned = true
                } else {
                    errorDialog = ErrorDialog.WrongFolder(info.errorMessage)
                }
            }
        }
        return true
    }

    // ── Folder picker result ──────────────────────────────────────────────────

    /**
     * Persist the picked URI, then invoke [onResult] immediately once the folder is
     * validated (log files found).  The actual parse/scan continues in the background
     * while the Dashboard is shown with isLoadingDetail = true.
     *
     *  - `onResult(true)`  → folder valid, navigate to Dashboard now
     *  - `onResult(false)` → wrong/empty folder, stay on Setup (errorDialog is set)
     */
    fun onFolderPicked(uri: Uri, onResult: (success: Boolean) -> Unit) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {}

        // A bad pick must leave the current folder working, so nothing about it changes until
        // the new folder has proved itself.
        val previousUri   = prefs.getString("folder_uri", null)?.toUri()
        val previousCache = repository.loadCachedBatteryInfo()
        val previousInfo  = batteryInfo
        val canRollBack   = previousUri != null && previousUri != uri && alreadyHasPerm
        isLoadingDetail   = true

        viewModelScope.launch {
            // First check the folder actually contains a dumpstate log
            val latestEntry = withContext(Dispatchers.IO) {
                repository.findLatestLogEntry(uri)
            }

            if (latestEntry == null) {
                // Nothing found — treat as wrong folder, do NOT persist the URI
                isLoadingDetail = false
                releaseUnusedFolderGrants(keep = previousUri)
                errorDialog     = ErrorDialog.WrongFolder(
                    "No dumpstate log files found in the selected folder. " +
                            "Please navigate to /Device/log/ and try again."
                )
                onResult(false)
                return@launch
            }

            // Folder looks correct — persist the URI and navigate to Dashboard immediately.
            // The scan continues in the background; the Dashboard shows isLoadingDetail.
            if (uri != previousUri) repository.clearBatteryInfoCache()
            prefs.edit { putString("folder_uri", uri.toString()) }
            folderUri        = uri
            alreadyHasPerm   = true
            folderAccessible = true
            allLogEntries    = emptyList()
            batteryInfo      = null
            onResult(true)

            val info = withContext(Dispatchers.IO) {
                repository.smartScan(uri)
            }
            isLoadingDetail = false

            when {
                info.readSuccess -> {
                    batteryInfo    = info
                    hasEverScanned = true
                    releaseUnusedFolderGrants(keep = uri)
                }
                canRollBack -> {
                    // The log held nothing usable — go back to the folder that was working
                    prefs.edit { putString("folder_uri", previousUri.toString()) }
                    previousCache?.let { repository.cacheBatteryInfo(it) }
                    releaseUnusedFolderGrants(keep = previousUri)
                    folderUri   = previousUri
                    batteryInfo = previousInfo
                    gaugeVm.incrementReplayKey()
                    errorDialog = ErrorDialog.WrongFolder(info.errorMessage)
                }
                else -> {
                    // Nothing to go back to — show the Dashboard as unsupported, not stuck loading
                    batteryInfo = info.copy(healthUnsupported = true)
                    errorDialog = ErrorDialog.WrongFolder(info.errorMessage)
                }
            }
        }
    }

    /** Forgets the folder and everything read from it, as if the app had just been installed. */
    fun removeFolderAccess() {
        releaseUnusedFolderGrants(keep = null)
        repository.clearBatteryInfoCache()
        prefs.edit { remove("folder_uri") }
        folderUri        = null
        alreadyHasPerm   = false
        folderAccessible = true
        hasEverScanned   = false
        batteryInfo      = null
        allLogEntries    = emptyList()
        showLogSheet     = false
        errorDialog      = null
    }

    /**
     * The app only ever reads one folder, so any other grant is left over from a rejected or
     * replaced pick. Android keeps those until they're released, even after the app stops using them.
     */
    private fun releaseUnusedFolderGrants(keep: Uri?) {
        val resolver = context.contentResolver
        resolver.persistedUriPermissions
            .filter { it.uri != keep }
            .forEach {
                try {
                    resolver.releasePersistableUriPermission(
                        it.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {}
            }
    }

    // ── Rescan (pull-to-refresh) ──────────────────────────────────────────────

    fun rescan() {
        val uri = folderUri ?: return
        if (isRefreshing) return

        viewModelScope.launch {
            isRefreshing = true

            if (!repository.hasPersistedPermission(uri)) {
                isRefreshing  = false
                showLogSheet  = false
                allLogEntries = emptyList()
                val cached = repository.loadCachedBatteryInfo()
                if (cached != null) {
                    batteryInfo = cached; hasEverScanned = true; gaugeVm.incrementReplayKey()
                }
                errorDialog = ErrorDialog.PermissionLost
                return@launch
            }

            val latestEntry = withContext(Dispatchers.IO) {
                repository.findLatestLogEntry(uri)
            }
            if (latestEntry == null) {
                isRefreshing     = false
                folderAccessible = false
                showLogSheet     = false
                val cached = repository.loadCachedBatteryInfo()
                if (cached != null) {
                    batteryInfo = cached; hasEverScanned = true; gaugeVm.incrementReplayKey()
                }
                errorDialog = ErrorDialog.FolderDeleted
                return@launch
            }

            // Compare cache to latest file — if they match, nothing changed on disk → just animate.
            // If they differ, a new file appeared → parse it.
            // If the user is viewing an older manually-selected file and refreshes, the cache
            // still reflects the true latest, so no unnecessary re-parse happens.
            val cached = repository.loadCachedBatteryInfo()

            if (cached?.logFileName == latestEntry.name) {
                // Files unchanged — just replay the gauge animation
                alreadyHasPerm   = true
                folderAccessible = true
                isRefreshing     = false
                gaugeVm.incrementReplayKey()
                return@launch
            }

            // New file appeared, or loaded file was deleted — parse latest
            isLoadingDetail = true
            val info = withContext(Dispatchers.IO) {
                repository.parseLatestLog(uri)
            }
            isRefreshing    = false
            isLoadingDetail = false
            if (info.readSuccess) {
                batteryInfo      = info
                alreadyHasPerm   = true
                folderAccessible = true
                hasEverScanned   = true
                gaugeVm.incrementReplayKey()
            } else {
                errorDialog = ErrorDialog.WrongFolder(info.errorMessage)
            }
        }
    }

    // ── Log sheet ─────────────────────────────────────────────────────────────

    fun openLogSheet() {
        val uri = folderUri
        if (uri != null && repository.hasPersistedPermission(uri)) {
            viewModelScope.launch {
                allLogEntries = withContext(Dispatchers.IO) { repository.listAllLogs(uri) }
                showLogSheet  = true
            }
        } else {
            showLogSheet = true
        }
    }

    fun dismissLogSheet() { showLogSheet = false }

    fun selectLogEntry(entry: DocEntry) {
        val uri = folderUri ?: return
        viewModelScope.launch {
            isLoadingDetail = true
            showLogSheet    = false
            startGaugePulse()
            val parsed = withContext(Dispatchers.IO) { repository.parseLogEntry(uri, entry) }
            isLoadingDetail = false
            if (parsed.readSuccess) {
                batteryInfo      = parsed
                folderAccessible = true
            }
        }
    }

    /**
     * Loads the raw log text for [entry].
     * Navigation to [LogRawActivity] is handled by the calling Activity.
     */
    fun openRawLog(entry: DocEntry) {
        val uri = folderUri ?: return
        viewModelScope.launch {
            rawLogText          = ""
            isLoadingRaw        = true
            showLogSheet        = false
            selectedLogFileName = entry.name
            val text = withContext(Dispatchers.IO) {
                repository.extractBatterySection(uri, entry)
            }
            rawLogText   = text
            isLoadingRaw = false
        }
    }

    // ── Error dialog ──────────────────────────────────────────────────────────

    fun dismissErrorDialog() { errorDialog = null }

    // ── Resume check ─────────────────────────────────────────────────────────

    fun checkOnResume() {
        val uri = folderUri ?: return
        viewModelScope.launch {
            val hasPerm = withContext(Dispatchers.IO) {
                repository.hasPersistedPermission(uri)
            }
            if (!hasPerm && alreadyHasPerm) {
                alreadyHasPerm   = false
                folderAccessible = false
                allLogEntries    = emptyList()
                showLogSheet     = false
                val cached = repository.loadCachedBatteryInfo()
                if (cached != null) {
                    batteryInfo = cached; hasEverScanned = true; gaugeVm.incrementReplayKey()
                }
                errorDialog = ErrorDialog.PermissionLost
            } else if (hasPerm) {
                val latestEntry = withContext(Dispatchers.IO) {
                    repository.findLatestLogEntry(uri)
                }
                if (latestEntry == null) {
                    if (folderAccessible) {
                        folderAccessible = false
                        allLogEntries    = emptyList()
                        showLogSheet     = false
                        val cached = repository.loadCachedBatteryInfo()
                        if (cached != null) {
                            batteryInfo = cached; hasEverScanned = true; gaugeVm.incrementReplayKey()
                        }
                        errorDialog = ErrorDialog.FolderDeleted
                    }
                } else {
                    // Folder is accessible — restore state if it was previously inaccessible
                    if (!folderAccessible) folderAccessible = true

                    // If the currently loaded file no longer exists in the folder,
                    // silently fall back to the latest without showing an error.
                    // Don't interfere if user intentionally loaded an older file that still exists.
                    val loadedName = batteryInfo?.logFileName?.takeIf { it.isNotEmpty() }
                    if (loadedName != null && loadedName != latestEntry.name) {
                        val loadedStillExists = withContext(Dispatchers.IO) {
                            repository.listAllLogs(uri).any { it.name == loadedName }
                        }
                        if (!loadedStillExists) {
                            val cached = repository.loadCachedBatteryInfo()
                            if (cached?.logFileName == latestEntry.name) {
                                // Cache already holds the latest — use it directly
                                batteryInfo    = cached
                                hasEverScanned = true
                                gaugeVm.incrementReplayKey()
                            } else {
                                // Need to parse the latest file
                                isLoadingDetail = true
                                val info = withContext(Dispatchers.IO) {
                                    repository.parseLatestLog(uri)
                                }
                                isLoadingDetail = false
                                if (info.readSuccess) {
                                    batteryInfo    = info
                                    hasEverScanned = true
                                    gaugeVm.incrementReplayKey()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Gauge pulse (delegated) ───────────────────────────────────────────────

    fun startGaugePulse() = gaugeVm.startGaugePulse()

    fun stopGaugePulse() = gaugeVm.stopGaugePulse()
}