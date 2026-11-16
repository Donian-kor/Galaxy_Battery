package com.donian.galaxy_battery.data

/** 당겨서 새로고침([rescan]) 후 해야 할 일. */
enum class ScanOutcome {
    /** 저장된 권한이 없다 — 캐시로 폴백하고 PermissionLost를 알린다. */
    PERMISSION_LOST,

    /** 폴더에 로그 파일이 없다 — 캐시로 폴백하고 FolderDeleted를 알린다. */
    FOLDER_DELETED,

    /** 캐시와 최신 파일이 같다 — 디스크 변화 없음. 애니메이션만 다시 재생한다. */
    NO_CHANGE,

    /** 새 파일이 생겼거나 로드된 파일이 사라졌다 — 최신 파일을 파싱한다. */
    PARSE_LATEST
}

/** 화면 복귀([checkOnResume]) 확인 절차에서 다음에 할 일. */
enum class ResumeStep {
    /** 더 확인할 것이 없다. */
    DONE,

    /** 권한이 회수됐다 — 캐시로 폴백하고 PermissionLost를 알린다. */
    PERMISSION_LOST,

    /** 폴더가 사라졌다 — 캐시로 폴백하고 FolderDeleted를 알린다. */
    FOLDER_DELETED,

    /** 권한이 살아 있다 — 최신 로그 파일을 찾는다. */
    LOOK_UP_LATEST,

    /** 폴더가 살아 있다 — 로드된 파일이 아직 있는지 확인한다. */
    CHECK_LOADED_FILE
}

/** 로드된 파일이 사라졌을 때 값을 어디서 되살릴지. */
enum class FallbackSource {
    /** 지금 보고 있는 값을 그대로 둔다. */
    KEEP_CURRENT,

    /** 캐시가 이미 최신 파일을 담고 있다 — 그대로 사용한다. */
    RESTORE_CACHED,

    /** 최신 파일을 파싱한다. */
    PARSE_LATEST
}

/** 잘못된 폴더를 골랐을 때의 처리. */
enum class WrongFolderRecovery {
    /** 직전에 잘 되던 폴더로 되돌린다. */
    ROLL_BACK,

    /** 되돌아갈 폴더가 없다 — 로딩에 멈추지 않고 미지원 상태로 표시한다. */
    SHOW_UNSUPPORTED
}

/**
 * 폴더 스캔 분기 판단.
 *
 * [com.donian.galaxy_battery.AppViewModel] 안에 흩어져 있던 "권한 → 최신 파일 → 캐시" 비교를
 * 입력과 결정으로 분리한다. Android·코루틴에 의존하지 않으므로 그대로 테스트할 수 있고,
 * 분기 자체를 한 표에 모아 두면 조건이 늘어날 때 누락을 찾기 쉽다.
 */
object ScanPolicy {

    /**
     * 재스캔 결과 판단.
     *
     * 순서가 곧 우선순위다: 권한이 없으면 폴더 내용과 무관하게 [ScanOutcome.PERMISSION_LOST],
     * 로그 파일이 없으면 캐시 유무와 무관하게 [ScanOutcome.FOLDER_DELETED]다.
     *
     * @param latestFileName 폴더에서 찾은 최신 로그 파일 이름. 없으면 null.
     * @param cachedFileName 캐시가 들고 있는 로그 파일 이름. 캐시가 없으면 null.
     */
    fun decideRescan(
        hasPermission: Boolean,
        latestFileName: String?,
        cachedFileName: String?
    ): ScanOutcome = when {
        !hasPermission -> ScanOutcome.PERMISSION_LOST
        latestFileName == null -> ScanOutcome.FOLDER_DELETED
        cachedFileName == latestFileName -> ScanOutcome.NO_CHANGE
        else -> ScanOutcome.PARSE_LATEST
    }

    /**
     * 복귀 시 권한 확인.
     *
     * 이전에도 권한이 없었다면 이미 최초 실행 취급을 받은 상태이므로 아무 것도 하지 않는다.
     * 권한이 살아 있으면 최신 로그를 찾는 단계로 넘어간다.
     */
    fun decideResumePermission(
        hasPermission: Boolean,
        alreadyHadPermission: Boolean
    ): ResumeStep = when {
        !hasPermission && alreadyHadPermission -> ResumeStep.PERMISSION_LOST
        !hasPermission -> ResumeStep.DONE
        else -> ResumeStep.LOOK_UP_LATEST
    }

    /**
     * 복귀 시 폴더 확인.
     *
     * 폴더가 접근 불가로 표시돼 있었다면 이미 FolderDeleted를 알린 상태이므로 다시 알리지 않는다.
     */
    fun decideResumeFolder(
        folderAccessible: Boolean,
        latestFileName: String?
    ): ResumeStep = when {
        latestFileName != null -> ResumeStep.CHECK_LOADED_FILE
        folderAccessible -> ResumeStep.FOLDER_DELETED
        else -> ResumeStep.DONE
    }

    /**
     * 로드된 파일이 사라졌을 때의 폴백 판단.
     *
     * 사용자가 의도적으로(여전히 존재하는) 예전 파일을 보는 중이면 [FallbackSource.KEEP_CURRENT]다.
     *
     * @param loadedFileStillExists 여전히 존재하는 예전 파일을 보고 있는가.
     * @param cachedFileName 캐시가 들고 있는 로그 파일 이름. 캐시가 없으면 null.
     */
    fun decideFallback(
        latestFileName: String,
        loadedFileStillExists: Boolean,
        cachedFileName: String?
    ): FallbackSource = when {
        loadedFileStillExists -> FallbackSource.KEEP_CURRENT
        cachedFileName == latestFileName -> FallbackSource.RESTORE_CACHED
        else -> FallbackSource.PARSE_LATEST
    }

    /** 잘못된 폴더를 골랐을 때 되돌릴 폴더가 있는지로 처리를 가른다. */
    fun decideWrongFolderRecovery(canRollBack: Boolean): WrongFolderRecovery =
        if (canRollBack) WrongFolderRecovery.ROLL_BACK else WrongFolderRecovery.SHOW_UNSUPPORTED
}
