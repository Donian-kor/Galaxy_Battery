package com.donian.galaxy_battery.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Folder scan branch decisions.
 *
 * These used to sit inside `AppViewModel.rescan()` / `checkOnResume()` where the branches were
 * only reachable through SAF and coroutines. Each case below is one of those branches, so a
 * change in priority (permission before folder, folder before cache) fails here instead of on a
 * device.
 */
class ScanPolicyTest {

    @Test
    fun `same cached file name means nothing to reparse`() {
        val outcome = ScanPolicy.decideRescan(
            hasPermission  = true,
            latestFileName = "dumpState_20240101.log",
            cachedFileName = "dumpState_20240101.log"
        )
        assertEquals(ScanOutcome.NO_CHANGE, outcome)
    }

    @Test
    fun `a newer log file is parsed`() {
        val outcome = ScanPolicy.decideRescan(
            hasPermission  = true,
            latestFileName = "dumpState_20240202.log",
            cachedFileName = "dumpState_20240101.log"
        )
        assertEquals(ScanOutcome.PARSE_LATEST, outcome)
    }

    @Test
    fun `a deleted folder falls back to the cache`() {
        val outcome = ScanPolicy.decideRescan(
            hasPermission  = true,
            latestFileName = null,
            cachedFileName = "dumpState_20240101.log"
        )
        assertEquals(ScanOutcome.FOLDER_DELETED, outcome)
    }

    @Test
    fun `a lost permission outranks everything else`() {
        val outcome = ScanPolicy.decideRescan(
            hasPermission  = false,
            latestFileName = null,
            cachedFileName = null
        )
        assertEquals(ScanOutcome.PERMISSION_LOST, outcome)
    }

    @Test
    fun `a vanished loaded file falls back to the newest log without an error`() {
        // 캐시가 이미 최신 파일을 담고 있으면 다시 파싱하지 않는다.
        assertEquals(
            FallbackSource.RESTORE_CACHED,
            ScanPolicy.decideFallback(
                latestFileName       = "dumpState_20240202.log",
                loadedFileStillExists = false,
                cachedFileName       = "dumpState_20240202.log"
            )
        )
        // 캐시가 다른 파일을 담고 있으면 최신 파일을 파싱한다.
        assertEquals(
            FallbackSource.PARSE_LATEST,
            ScanPolicy.decideFallback(
                latestFileName       = "dumpState_20240202.log",
                loadedFileStillExists = false,
                cachedFileName       = "dumpState_20240101.log"
            )
        )
        // 사용자가 의도적으로 예전 파일을 보고 있으면 건드리지 않는다.
        assertEquals(
            FallbackSource.KEEP_CURRENT,
            ScanPolicy.decideFallback(
                latestFileName       = "dumpState_20240202.log",
                loadedFileStillExists = true,
                cachedFileName       = "dumpState_20240202.log"
            )
        )
    }

    @Test
    fun `a wrong folder with nothing to roll back to is shown as unsupported`() {
        assertEquals(
            WrongFolderRecovery.SHOW_UNSUPPORTED,
            ScanPolicy.decideWrongFolderRecovery(canRollBack = false)
        )
        assertEquals(
            WrongFolderRecovery.ROLL_BACK,
            ScanPolicy.decideWrongFolderRecovery(canRollBack = true)
        )
    }
}
