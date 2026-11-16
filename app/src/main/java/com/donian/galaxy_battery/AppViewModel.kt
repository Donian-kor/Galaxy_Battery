package com.donian.galaxy_battery

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.donian.galaxy_battery.data.*
import com.donian.galaxy_battery.model.*
import com.donian.galaxy_battery.viewmodel.FolderState
import com.donian.galaxy_battery.viewmodel.GaugeViewModel
import com.donian.galaxy_battery.viewmodel.LogSheetState
import com.donian.galaxy_battery.viewmodel.RawLogState
import com.donian.galaxy_battery.viewmodel.BatteryState
import com.donian.galaxy_battery.viewmodel.DialCodeViewModel

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    private val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
    private val repository = BatteryRepository(context, prefs)
    private val folders = FolderAccessManager(context, prefs)

    // ── 공유 앱 상태 ───────────────────────────────────────────────────────
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

    // ── 게이지 애니메이션 신호 (GaugeViewModel로 위임) ─────────────────────────

    private val gaugeVm = GaugeViewModel(getApplication())
    val gaugeAmplitude: Float get() = gaugeVm.gaugeAmplitude
    val gaugeReplayKey: Int get() = gaugeVm.gaugeReplayKey

    // ── 다이얼 코드 ────────────────────────────────────────────────────────
    private val dialCodeVm = DialCodeViewModel(getApplication())

    val dialCode: String get() = dialCodeVm.dialCode

    fun saveDialCode(code: String) = dialCodeVm.saveDialCode(code)

    // ── 초기화 ────────────────────────────────────────────────────────────

    /**
     * [MainActivity] 생성 시 한 번 호출된다.
     * 저장된 URI가 있으면(대시보드 표시) true,
     * 최초 실행이면(호출측에서 SetupActivity를 열어야 함) false를 돌려준다.
     */
    fun initialize(): Boolean {
        val savedUri = folders.savedFolderUri()
        folderUri(savedUri)
        folders.releaseUnusedGrants(keep = savedUri)

        if (savedUri == null) return false

        // 동기 권한 확인(메모리 목록 조회라 메인 스레드에서 안전).
        // 마지막 실행 이후 권한이 회수됐다면 최초 실행으로 취급해
        // 사용자를 SetupActivity로 돌려보내 다시 권한을 받게 한다.
        if (!folders.hasPersistedReadPermission(savedUri)) return false

        // 폴더를 새로 고른 직후에는 SetupActivity가 대시보드를 먼저 띄우고 스캔은 계속된다.
        // 여기서 또 스캔하면 같은 로그를 두 번 읽게 되므로, 진행 중인 스캔 결과를 기다린다.
        if (isLoadingDetail) return true

        viewModelScope.launch {
            if (!repository.hasPersistedPermission(savedUri)) {
                // 보통 여기까지 오지 않는다(위에서 동기 처리됨). 경쟁 상태에 대비한 방어 코드.
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

                val latestEntry = withContext(Dispatchers.IO) { repository.findLatestLogEntry(savedUri) }
                if (latestEntry == null) {
                    folderAccessible(false); isLoadingDetail(false); gaugeVm.incrementReplayKey()
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
                val latestEntry = withContext(Dispatchers.IO) { repository.findLatestLogEntry(savedUri) }
                if (latestEntry == null) {
                    isLoadingDetail(false); folderAccessible(false)
                    errorDialog(ErrorDialog.FolderDeleted)
                    return@launch
                }
                val info = withContext(Dispatchers.IO) { repository.smartScan(savedUri, latestEntry) }
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

    // ── 폴더 선택 결과 ────────────────────────────────────────────────────

    /**
     * 선택한 URI를 저장하고, 폴더 검증(로그 파일 발견)이 끝나면 즉시 [onResult]를 호출한다.
     * 실제 파싱/스캔은 백그라운드에서 계속되며, 그동안 대시보드는
     * isLoadingDetail = true 상태로 표시된다.
     *
     *  - `onResult(true)`  → 폴더 유효, 지금 대시보드로 이동
     *  - `onResult(false)` → 잘못된/빈 폴더, Setup에 유지(errorDialog가 설정됨)
     */
    fun onFolderPicked(uri: Uri, onResult: (success: Boolean) -> Unit) {
        folders.takePersistableReadPermission(uri)

        // 잘못된 선택이 현재 폴더를 망치지 않도록, 새 폴더가 스스로 증명하기 전까지는
        // 아무것도 바꾸지 않는다.
        val previousUri   = folders.savedFolderUri()
        val previousCache = repository.loadCachedBatteryInfo()
        val previousInfo  = batteryInfo
        val canRollBack   = previousUri != null && previousUri != uri && alreadyHasPerm
        isLoadingDetail(true)

        viewModelScope.launch {
            val isLogFolder = withContext(Dispatchers.IO) { repository.isLogFolderUri(uri) }
            val latestEntry = withContext(Dispatchers.IO) { repository.findLatestLogEntry(uri) }

            if (latestEntry == null) {
                // 아무것도 못 찾음 — 폴더 불일치 처리, URI는 저장하지 않는다
                isLoadingDetail(false)
                folders.releaseUnusedGrants(keep = previousUri)

                val message = if (!isLogFolder) {
                    "선택하신 폴더는 'log' 폴더가 아닙니다. " +
                            "삼성 전화 앱에서 *#9900# 실행 후 복사된 'log' 폴더(/Device/log/ 또는 내 파일의 log)를 선택해 주세요."
                } else {
                    "선택하신 'log' 폴더에서 dumpState_*.log 파일을 찾지 못했습니다. " +
                            "*#9900#에서 'Copy to sdcard'를 실행했는지 확인해 주세요."
                }

                errorDialog(ErrorDialog.WrongFolder(message))
                onResult(false)
                return@launch
            }

            // 폴더가 맞아 보인다 — URI를 저장하고 즉시 대시보드로 이동한다.
            // 스캔은 백그라운드에서 계속되고, 대시보드는 isLoadingDetail을 표시한다.
            if (uri != previousUri) repository.clearBatteryInfoCache()
            folders.saveFolderUri(uri)
            folderUri(uri)
            alreadyHasPerm(true)
            folderAccessible(true)
            allLogEntries(emptyList())
            batteryInfo(null)
            onResult(true)

            val info = withContext(Dispatchers.IO) { repository.smartScan(uri, latestEntry) }
            isLoadingDetail(false)

            if (info.readSuccess) {
                batteryInfo(info)
                hasEverScanned(true)
                folders.releaseUnusedGrants(keep = uri)
            } else when (ScanPolicy.decideWrongFolderRecovery(canRollBack)) {
                WrongFolderRecovery.ROLL_BACK -> {
                    // 로그에 쓸 만한 값이 없음 — 직전에 잘 되던 폴더로 되돌린다
                    folders.saveFolderUri(previousUri)
                    previousCache?.let { repository.cacheBatteryInfo(it) }
                    folders.releaseUnusedGrants(keep = previousUri)
                    folderUri(previousUri)
                    batteryInfo(previousInfo)
                    gaugeVm.incrementReplayKey()
                    errorDialog(ErrorDialog.WrongFolder(info.errorMessage))
                }
                WrongFolderRecovery.SHOW_UNSUPPORTED -> {
                    // 되돌아갈 폴더가 없음 — 로딩에 멈추지 않고 대시보드를 미지원 상태로 표시
                    batteryInfo(info.copy(healthUnsupported = true))
                    errorDialog(ErrorDialog.WrongFolder(info.errorMessage))
                }
            }
        }
    }

    /** 앱을 방금 설치한 것처럼 폴더와 거기서 읽은 모든 것을 잊는다. */
    fun removeFolderAccess() {
        clearAllData(keepUri = false)
    }

    /**
     * 앱 내부 저장소(SharedPreferences 캐시, 배터리 캐시)를 모두 지우고 ViewModel 상태를 리셋한다.
     * [keepUri]가 false면 저장된 폴더 URI도 제거하여 다음 실행 때 다시 로그 폴더를 고르게 한다.
     */
    fun clearAllData(keepUri: Boolean) {
        folders.releaseUnusedGrants(keep = if (keepUri) folderUri else null)
        repository.clearBatteryInfoCache()
        // 캐시 키는 여기서 나열하지 않는다. 바로 위 clearBatteryInfoCache()가
        // K enum 전체(현재 키 + 폐기 키)를 지운다. 여기에 목록을 또 두면
        // 두 벌이 생겨 한쪽만 추가될 때 어긋난다.
        folders.saveFolderUri(null)
        folderUri(if (keepUri) folderUri else null)
        alreadyHasPerm(false); folderAccessible(false); hasEverScanned(false)
        batteryInfo(null); allLogEntries(emptyList()); showLogSheet(false); errorDialog(null)
    }

    // ── 상태 반영 헬퍼 ───────────────────────────────────────────────────────

    /**
     * 폴더에 접근할 수 없을 때: 마지막으로 읽은 값을 그대로 보여주고 사유만 알린다.
     * 보여줄 캐시가 없으면 오류만 띄운다.
     */
    private fun fallBackToCache(error: ErrorDialog, cached: BatteryInfo?) {
        if (cached != null) {
            batteryInfo(cached); hasEverScanned(true); gaugeVm.incrementReplayKey()
        }
        errorDialog(error)
    }

    /** 최신 파일을 성공적으로 읽었을 때의 상태 반영. */
    private fun commitLatestInfo(info: BatteryInfo) {
        batteryInfo(info); hasEverScanned(true); gaugeVm.incrementReplayKey()
    }

    // ── 재스캔 (당겨서 새로고침) ────────────────────────────────────────────

    fun rescan() {
        val uri = folderUri ?: return
        if (isRefreshing) return

        viewModelScope.launch {
            isRefreshing(true)

            val hasPermission = repository.hasPersistedPermission(uri)
            val latestEntry = withContext(Dispatchers.IO) {
                repository.findLatestLogEntry(uri)
            }
            val cached = repository.loadCachedBatteryInfo()

            // 캐시와 최신 파일을 비교 — 같으면 디스크 변경 없음 → 애니메이션만 재생.
            // 다르면 새 파일이 생김 → 파싱한다.
            // 사용자가 수동으로 고른 예전 파일을 보며 새로고침해도 캐시는 여전히 진짜 최신을
            // 반영하므로 불필요한 재파싱은 일어나지 않는다.
            val outcome = ScanPolicy.decideRescan(
                hasPermission   = hasPermission,
                latestFileName  = latestEntry?.name,
                cachedFileName  = cached?.logFileName
            )

            when (outcome) {
                ScanOutcome.PERMISSION_LOST -> {
                    isRefreshing(false); showLogSheet(false); allLogEntries(emptyList())
                    fallBackToCache(ErrorDialog.PermissionLost, cached)
                }

                ScanOutcome.FOLDER_DELETED -> {
                    isRefreshing(false); folderAccessible(false); showLogSheet(false)
                    fallBackToCache(ErrorDialog.FolderDeleted, cached)
                }

                ScanOutcome.NO_CHANGE -> {
                    // 파일 변화 없음 — 게이지 애니메이션만 다시 재생
                    alreadyHasPerm(true); folderAccessible(true); isRefreshing(false)
                    gaugeVm.incrementReplayKey()
                }

                ScanOutcome.PARSE_LATEST -> {
                    // 새 파일이 생겼거나, 로드된 파일이 삭제됨 — 최신을 파싱
                    val entry = latestEntry ?: return@launch
                    isLoadingDetail(true)
                    val info = withContext(Dispatchers.IO) { repository.parseLatestLog(uri, entry) }
                    isRefreshing(false); isLoadingDetail(false)
                    if (info.readSuccess) {
                        alreadyHasPerm(true); folderAccessible(true); commitLatestInfo(info)
                    } else {
                        errorDialog(ErrorDialog.WrongFolder(info.errorMessage))
                    }
                }
            }
        }
    }

    // ── 로그 시트 ──────────────────────────────────────────────────────────

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
     * [entry]의 원시 로그 텍스트를 로드한다.
     * [LogRawActivity]로의 화면 전환은 호출한 Activity가 처리한다.
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

    // ── 오류 다이얼로그 ─────────────────────────────────────────────────────

    fun dismissErrorDialog() { errorDialog(null) }

    // ── 재개 시 확인 ──────────────────────────────────────────────────────

    fun checkOnResume() {
        val uri = folderUri ?: return
        // 폴더 선택 직후의 스캔이 진행 중이면 그쪽이 결과와 오류를 모두 처리한다.
        if (isLoadingDetail) return
        viewModelScope.launch {
            val hasPerm = withContext(Dispatchers.IO) {
                repository.hasPersistedPermission(uri)
            }

            when (ScanPolicy.decideResumePermission(hasPerm, alreadyHasPerm)) {
                ResumeStep.DONE -> return@launch

                ResumeStep.PERMISSION_LOST -> {
                    alreadyHasPerm(false); folderAccessible(false)
                    allLogEntries(emptyList()); showLogSheet(false)
                    fallBackToCache(ErrorDialog.PermissionLost, repository.loadCachedBatteryInfo())
                }

                // LOOK_UP_LATEST — 권한이 살아 있으니 폴더를 확인한다.
                else -> {
                    val latestEntry = withContext(Dispatchers.IO) { repository.findLatestLogEntry(uri) }

                    when (ScanPolicy.decideResumeFolder(folderAccessible, latestEntry?.name)) {
                        ResumeStep.DONE -> return@launch

                        ResumeStep.FOLDER_DELETED -> {
                            folderAccessible(false); allLogEntries(emptyList())
                            showLogSheet(false)
                            fallBackToCache(ErrorDialog.FolderDeleted, repository.loadCachedBatteryInfo())
                        }

                        // CHECK_LOADED_FILE — 폴더가 살아 있다.
                        else -> {
                            val entry = latestEntry ?: return@launch
                            // 폴더 접근 가능 — 이전에 접근 불가였다면 상태를 복구한다
                            if (!folderAccessible) folderAccessible(true)

                            // 현재 로드된 파일이 폴더에 더 없으면,
                            // 오류를 띄우지 않고 조용히 최신 파일로 폴백한다.
                            // 사용자가 의도적으로(여전히 존재하는) 예전 파일을 본 중이면 건드리지 않는다.
                            val loadedName = batteryInfo?.logFileName?.takeIf { it.isNotEmpty() }
                            if (loadedName == null || loadedName == entry.name) return@launch

                            val loadedStillExists = withContext(Dispatchers.IO) {
                                repository.listAllLogs(uri).any { it.name == loadedName }
                            }
                            val cached = repository.loadCachedBatteryInfo()
                            val source = ScanPolicy.decideFallback(
                                latestFileName        = entry.name,
                                loadedFileStillExists = loadedStillExists,
                                cachedFileName        = cached?.logFileName
                            )
                            // 로드된 파일이 살아 있으면 [FallbackSource.KEEP_CURRENT] — 지금 보던 값을 그대로 둔다.
                            if (source == FallbackSource.RESTORE_CACHED) {
                                // 캐시가 이미 최신을 담고 있음 — 그대로 사용
                                cached?.let { commitLatestInfo(it) }
                            } else if (source == FallbackSource.PARSE_LATEST) {
                                // 최신 파일을 파싱해야 함
                                isLoadingDetail(true)
                                val info = withContext(Dispatchers.IO) {
                                    repository.parseLatestLog(uri, entry)
                                }
                                isLoadingDetail(false)
                                if (info.readSuccess) commitLatestInfo(info)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 게이지 펄스 (위임) ─────────────────────────────────────────────────

    fun startGaugePulse() = gaugeVm.startGaugePulse()

    fun stopGaugePulse() = gaugeVm.stopGaugePulse()
}
