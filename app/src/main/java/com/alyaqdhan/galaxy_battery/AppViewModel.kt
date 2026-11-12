package com.alyaqdhan.galaxy_battery

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.alyaqdhan.galaxy_battery.data.BatteryRepository
import com.alyaqdhan.galaxy_battery.viewmodel.FolderState
import com.alyaqdhan.galaxy_battery.viewmodel.GaugeViewModel
import com.alyaqdhan.galaxy_battery.viewmodel.LogSheetState
import com.alyaqdhan.galaxy_battery.viewmodel.RawLogState
import com.alyaqdhan.galaxy_battery.viewmodel.BatteryState
import com.alyaqdhan.galaxy_battery.viewmodel.DialCodeViewModel

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    private val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
    private val repository = BatteryRepository(context, prefs)

    // ── Shared app state ──────────────────────────────────────────────────────
    // 상태는 화면별 묶음(Folder/Battery/LogSheet/RawLog)으로 들고, 갱신은 아래
    // edit* 헬퍼 한 곳으로만 한다. (같은 묶음을 여러 번 copy 하던 갱신 지점을 좁힌다)
    var folderState   by mutableStateOf(FolderState())   ; private set
    var batteryState  by mutableStateOf(BatteryState())  ; private set
    var logSheetState by mutableStateOf(LogSheetState()) ; private set
    var rawLogState   by mutableStateOf(RawLogState())   ; private set

    /** 같은 묶음을 한 번에 갱신하는 단일 지점. */
    private inline fun editFolder(block: (FolderState) -> FolderState) {
        folderState = block(folderState)
    }

    private inline fun editBattery(block: (BatteryState) -> BatteryState) {
        batteryState = block(batteryState)
    }

    private inline fun editLogSheet(block: (LogSheetState) -> LogSheetState) {
        logSheetState = block(logSheetState)
    }

    private inline fun editRawLog(block: (RawLogState) -> RawLogState) {
        rawLogState = block(rawLogState)
    }

    // ── 읽기 전용 위임 프로퍼티 (화면 접근 통로) ──────────────────────────────

    val batteryInfo: BatteryInfo?     get() = batteryState.batteryInfo
    val isRefreshing: Boolean         get() = batteryState.isRefreshing
    val isLoadingDetail: Boolean      get() = batteryState.isLoadingDetail
    val errorDialog: ErrorDialog?     get() = batteryState.errorDialog

    val folderUri: Uri?               get() = folderState.folderUri
    val alreadyHasPerm: Boolean       get() = folderState.alreadyHasPerm
    val folderAccessible: Boolean     get() = folderState.folderAccessible
    val hasEverScanned: Boolean       get() = folderState.hasEverScanned

    val allLogEntries: List<DocEntry> get() = logSheetState.allLogEntries
    val showLogSheet: Boolean         get() = logSheetState.showLogSheet

    val rawLogText: String            get() = rawLogState.rawLogText
    val isLoadingRaw: Boolean         get() = rawLogState.isLoadingRaw
    val selectedLogFileName: String   get() = rawLogState.selectedLogFileName

    // ── 상태 갱신 헬퍼 ────────────────────────────────────────────────────────

    private fun batteryInfo(value: BatteryInfo?) = editBattery { it.copy(batteryInfo = value) }
    private fun isRefreshing(value: Boolean) = editBattery { it.copy(isRefreshing = value) }
    private fun isLoadingDetail(value: Boolean) = editBattery { it.copy(isLoadingDetail = value) }
    private fun errorDialog(value: ErrorDialog?) = editBattery { it.copy(errorDialog = value) }

    private fun folderUri(value: Uri?) = editFolder { it.copy(folderUri = value) }
    private fun alreadyHasPerm(value: Boolean) = editFolder { it.copy(alreadyHasPerm = value) }
    private fun folderAccessible(value: Boolean) = editFolder { it.copy(folderAccessible = value) }
    private fun hasEverScanned(value: Boolean) = editFolder { it.copy(hasEverScanned = value) }

    private fun allLogEntries(value: List<DocEntry>) = editLogSheet { it.copy(allLogEntries = value) }
    private fun showLogSheet(value: Boolean) = editLogSheet { it.copy(showLogSheet = value) }

    private fun rawLogText(value: String) = editRawLog { it.copy(rawLogText = value) }
    private fun isLoadingRaw(value: Boolean) = editRawLog { it.copy(isLoadingRaw = value) }
    private fun selectedLogFileName(value: String) =
        editRawLog { it.copy(selectedLogFileName = value) }

    // ── Gauge animation signals (delegated to GaugeViewModel) ─────────────────

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
        folderUri(savedUri)
        releaseUnusedFolderGrants(keep = savedUri)

        if (savedUri == null) return false

        // Synchronous persisted-permission check (safe on main thread — in-memory list).
        // If permission was revoked since last launch, treat it as first-run and send
        // the user back to SetupActivity so they must re-grant access.
        val hasSyncPerm = context.contentResolver.persistedUriPermissions
            .any { it.uri.toString() == savedUri.toString() && it.isReadPermission }
        if (!hasSyncPerm) return false

        // 폴더를 새로 고른 직후에는 SetupActivity가 대시보드를 먼저 띄우고 스캔은 계속된다.
        // 여기서 또 스캔하면 같은 로그를 두 번 읽게 되므로, 진행 중인 스캔 결과를 기다린다.
        if (isLoadingDetail) return true

        viewModelScope.launch {
            if (!repository.hasPersistedPermission(savedUri)) {
                // Shouldn't normally reach here (handled synchronously above),
                // but guard against race conditions.
                alreadyHasPerm(false)
                folderAccessible(false)
                allLogEntries(emptyList())
                isLoadingDetail(false)
                return@launch
            }

            alreadyHasPerm(true)
            isLoadingDetail(true)

            val cached = repository.loadCachedBatteryInfo()
            if (cached != null) {
                batteryInfo(cached)
                folderAccessible(true)
                hasEverScanned(true)

                val latestEntry = withContext(Dispatchers.IO) {
                    repository.findLatestLogEntry(savedUri)
                }
                if (latestEntry == null) {
                    folderAccessible(false)
                    isLoadingDetail(false)
                    gaugeVm.incrementReplayKey()
                    errorDialog(ErrorDialog.FolderDeleted)
                    return@launch
                }
                if (latestEntry.name != cached.logFileName) {
                    val liveInfo = withContext(Dispatchers.IO) {
                        repository.parseLatestLog(savedUri, latestEntry)
                    }
                    if (liveInfo.readSuccess) batteryInfo(liveInfo)
                }
                isLoadingDetail(false)

            } else {
                val latestEntry = withContext(Dispatchers.IO) {
                    repository.findLatestLogEntry(savedUri)
                }
                if (latestEntry == null) {
                    isLoadingDetail(false)
                    folderAccessible(false)
                    errorDialog(ErrorDialog.FolderDeleted)
                    return@launch
                }
                val info = withContext(Dispatchers.IO) {
                    repository.smartScan(savedUri, latestEntry)
                }
                isLoadingDetail(false)
                batteryInfo(info)
                folderAccessible(true)
                if (info.readSuccess) {
                    hasEverScanned(true)
                } else {
                    errorDialog(ErrorDialog.WrongFolder(info.errorMessage))
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
        isLoadingDetail(true)

        viewModelScope.launch {
            // First check the folder actually contains a dumpstate log
            val latestEntry = withContext(Dispatchers.IO) {
                repository.findLatestLogEntry(uri)
            }

            if (latestEntry == null) {
                // Nothing found — treat as wrong folder, do NOT persist the URI
                isLoadingDetail(false)
                releaseUnusedFolderGrants(keep = previousUri)
                errorDialog(
                    ErrorDialog.WrongFolder(
                        "선택한 폴더에서 dumpState_*.log 파일을 찾지 못했습니다. " +
                                "/Device/log/ 폴더로 이동해 다시 시도하세요."
                    )
                )
                onResult(false)
                return@launch
            }

            // Folder looks correct — persist the URI and navigate to Dashboard immediately.
            // The scan continues in the background; the Dashboard shows isLoadingDetail.
            if (uri != previousUri) repository.clearBatteryInfoCache()
            prefs.edit { putString("folder_uri", uri.toString()) }
            folderUri(uri)
            alreadyHasPerm(true)
            folderAccessible(true)
            allLogEntries(emptyList())
            batteryInfo(null)
            onResult(true)

            val info = withContext(Dispatchers.IO) {
                repository.smartScan(uri, latestEntry)
            }
            isLoadingDetail(false)

            when {
                info.readSuccess -> {
                    batteryInfo(info)
                    hasEverScanned(true)
                    releaseUnusedFolderGrants(keep = uri)
                }
                canRollBack -> {
                    // The log held nothing usable — go back to the folder that was working
                    prefs.edit { putString("folder_uri", previousUri.toString()) }
                    previousCache?.let { repository.cacheBatteryInfo(it) }
                    releaseUnusedFolderGrants(keep = previousUri)
                    folderUri(previousUri)
                    batteryInfo(previousInfo)
                    gaugeVm.incrementReplayKey()
                    errorDialog(ErrorDialog.WrongFolder(info.errorMessage))
                }
                else -> {
                    // Nothing to go back to — show the Dashboard as unsupported, not stuck loading
                    batteryInfo(info.copy(healthUnsupported = true))
                    errorDialog(ErrorDialog.WrongFolder(info.errorMessage))
                }
            }
        }
    }

    /** Forgets the folder and everything read from it, as if the app had just been installed. */
    fun removeFolderAccess() {
        clearAllData(keepUri = false)
    }

    /**
     * Clears all app-internal storage (SharedPreferences cache, battery cache) and resets
     * ViewModel state. When [keepUri] is false the persisted folder URI is also removed so the
     * user must pick the log folder again on next launch.
     */
    fun clearAllData(keepUri: Boolean) {
        releaseUnusedFolderGrants(keep = if (keepUri) folderUri else null)
        repository.clearBatteryInfoCache()
        prefs.edit {
            remove("folder_uri")
            remove("cache_schema_version")
            remove("cache_log_file_name")
            remove("cache_log_timestamp_ms")
            remove("cache_health_percent")
            remove("cache_health_source")
            remove("cache_health_unsupported")
            remove("cache_cycle_count")
            remove("cache_asoc_raw")
            remove("cache_asoc_value")
            remove("cache_bsoh_raw")
            remove("cache_bsoh_percent")
            remove("cache_bsoh_value")
            remove("cache_battery_usage_value")
            remove("cache_usage_raw")
            remove("cache_manufacture_date_ms")
            remove("cache_calibrated_date_ms")
            remove("cache_first_use_date_ms")
            remove("cache_activation_date_ms")
            remove("cache_voltage_mv")
            remove("cache_temperature_c")
            remove("cache_current_ma")
            remove("cache_charge_counter_mah")
            remove("cache_measured_capacity")
            remove("cache_capacity_estimated")
            remove("cache_design_capacity")
            remove("cache_charge_status")
            remove("cache_power_source")
        }
        folderUri(if (keepUri) folderUri else null)
        alreadyHasPerm(false)
        folderAccessible(false)
        hasEverScanned(false)
        batteryInfo(null)
        allLogEntries(emptyList())
        showLogSheet(false)
        errorDialog(null)
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
            isRefreshing(true)

            if (!repository.hasPersistedPermission(uri)) {
                isRefreshing(false)
                showLogSheet(false)
                allLogEntries(emptyList())
                val cached = repository.loadCachedBatteryInfo()
                if (cached != null) {
                    batteryInfo(cached); hasEverScanned(true); gaugeVm.incrementReplayKey()
                }
                errorDialog(ErrorDialog.PermissionLost)
                return@launch
            }

            val latestEntry = withContext(Dispatchers.IO) {
                repository.findLatestLogEntry(uri)
            }
            if (latestEntry == null) {
                isRefreshing(false)
                folderAccessible(false)
                showLogSheet(false)
                val cached = repository.loadCachedBatteryInfo()
                if (cached != null) {
                    batteryInfo(cached); hasEverScanned(true); gaugeVm.incrementReplayKey()
                }
                errorDialog(ErrorDialog.FolderDeleted)
                return@launch
            }

            // Compare cache to latest file — if they match, nothing changed on disk → just animate.
            // If they differ, a new file appeared → parse it.
            // If the user is viewing an older manually-selected file and refreshes, the cache
            // still reflects the true latest, so no unnecessary re-parse happens.
            val cached = repository.loadCachedBatteryInfo()

            if (cached?.logFileName == latestEntry.name) {
                // Files unchanged — just replay the gauge animation
                alreadyHasPerm(true)
                folderAccessible(true)
                isRefreshing(false)
                gaugeVm.incrementReplayKey()
                return@launch
            }

            // New file appeared, or loaded file was deleted — parse latest
            isLoadingDetail(true)
            val info = withContext(Dispatchers.IO) {
                repository.parseLatestLog(uri, latestEntry)
            }
            isRefreshing(false)
            isLoadingDetail(false)
            if (info.readSuccess) {
                batteryInfo(info)
                alreadyHasPerm(true)
                folderAccessible(true)
                hasEverScanned(true)
                gaugeVm.incrementReplayKey()
            } else {
                errorDialog(ErrorDialog.WrongFolder(info.errorMessage))
            }
        }
    }

    // ── Log sheet ─────────────────────────────────────────────────────────────

    fun openLogSheet() {
        val uri = folderUri
        if (uri != null && repository.hasPersistedPermission(uri)) {
            viewModelScope.launch {
                allLogEntries(withContext(Dispatchers.IO) { repository.listAllLogs(uri) })
                showLogSheet(true)
            }
        } else {
            showLogSheet(true)
        }
    }

    fun dismissLogSheet() { showLogSheet(false) }

    fun selectLogEntry(entry: DocEntry) {
        val uri = folderUri ?: return
        viewModelScope.launch {
            isLoadingDetail(true)
            showLogSheet(false)
            startGaugePulse()
            val parsed = withContext(Dispatchers.IO) { repository.parseLogEntry(uri, entry) }
            isLoadingDetail(false)
            if (parsed.readSuccess) {
                batteryInfo(parsed)
                folderAccessible(true)
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
            rawLogText("")
            isLoadingRaw(true)
            showLogSheet(false)
            selectedLogFileName(entry.name)
            val text = withContext(Dispatchers.IO) {
                repository.extractBatterySection(uri, entry)
            }
            rawLogText(text)
            isLoadingRaw(false)
        }
    }

    // ── Error dialog ──────────────────────────────────────────────────────────

    fun dismissErrorDialog() { errorDialog(null) }

    // ── Resume check ─────────────────────────────────────────────────────────

    fun checkOnResume() {
        val uri = folderUri ?: return
        // 폴더 선택 직후의 스캔이 진행 중이면 그쪽이 결과와 오류를 모두 처리한다.
        if (isLoadingDetail) return
        viewModelScope.launch {
            val hasPerm = withContext(Dispatchers.IO) {
                repository.hasPersistedPermission(uri)
            }
            if (!hasPerm && alreadyHasPerm) {
                alreadyHasPerm(false)
                folderAccessible(false)
                allLogEntries(emptyList())
                showLogSheet(false)
                val cached = repository.loadCachedBatteryInfo()
                if (cached != null) {
                    batteryInfo(cached); hasEverScanned(true); gaugeVm.incrementReplayKey()
                }
                errorDialog(ErrorDialog.PermissionLost)
            } else if (hasPerm) {
                val latestEntry = withContext(Dispatchers.IO) {
                    repository.findLatestLogEntry(uri)
                }
                if (latestEntry == null) {
                    if (folderAccessible) {
                        folderAccessible(false)
                        allLogEntries(emptyList())
                        showLogSheet(false)
                        val cached = repository.loadCachedBatteryInfo()
                        if (cached != null) {
                            batteryInfo(cached); hasEverScanned(true); gaugeVm.incrementReplayKey()
                        }
                        errorDialog(ErrorDialog.FolderDeleted)
                    }
                } else {
                    // Folder is accessible — restore state if it was previously inaccessible
                    if (!folderAccessible) folderAccessible(true)

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
                                batteryInfo(cached)
                                hasEverScanned(true)
                                gaugeVm.incrementReplayKey()
                            } else {
                                // Need to parse the latest file
                                isLoadingDetail(true)
                                val info = withContext(Dispatchers.IO) {
                                    repository.parseLatestLog(uri, latestEntry)
                                }
                                isLoadingDetail(false)
                                if (info.readSuccess) {
                                    batteryInfo(info)
                                    hasEverScanned(true)
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