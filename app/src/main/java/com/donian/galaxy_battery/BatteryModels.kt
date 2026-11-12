package com.donian.galaxy_battery

data class BatteryInfo(
    val healthPercent: Int?,
    /**
     * 건강도 표시에 쓴 값의 출처. 판정 규칙(BSOH 우선)과 같은 기준으로 정해진다.
     * (이전에는 "asoc"/"bsoh" 문자열이어서 판정 enum과 따로 놀았다)
     */
    val healthSource: HealthSource? = null,
    val healthUnsupported: Boolean = false,
    val cycleCount: Int?,
    val asocRaw: String = "",
    val asocValue: Int? = null,
    val bsohRaw: String = "",
    val bsohPercent: Int? = null,
    val bsohValue: Int? = null,
    val usageRaw: String = "",
    val batteryUsageValue: Int? = null,
    val llbType: String = "",
    /**
     * 배터리 제조일(`LLB MAN`). 새 배터리로 교체하면 새 배터리의 생산일로 바뀐다.
     */
    val manufactureDateMs: Long = 0L,
    /**
     * 배터리 최초 보정/사용일(`LLB CAL`).
     *
     * 공식 센터에서 배터리를 교체하고 초기화(리셋)하면 **교체 당일**로 바뀐다.
     * 배터리 사용 개월 수의 근거로 쓸 수 있는 가장 확실한 값이다.
     * 구형 기종에는 값이 있고 One UI 5.1+로 업데이트된 기종은 비어 있다.
     */
    val calibratedDateMs: Long = 0L,
    /**
     * 기기(메인보드) 최초 사용일(`battery FirstUseDate`).
     *
     * **배터리 교체와 무관하다.** 배터리를 갈아도 유지되며, 메인보드 교체·
     * 특정 리셋·펌웨어 패치 시에만 바뀐다.
     */
    val firstUseDateMs: Long = 0L,
    /**
     * 기기 최초 통화일(`ril.actdate`). SYSTEM PROPERTIES 영역에 있어 배터리 블록과 별개로 읽는다.
     *
     * 개통 시점의 기준값이며, 메인보드 교체 시에만 바뀐다.
     */
    val activationDateMs: Long = 0L,
    val logFileName: String = "",
    val logTimestampMs: Long = 0L,
    val readSuccess: Boolean = false,
    val errorMessage: String = "",
    val chargeCounterMah: Int? = null,
    val measuredCapacityMah: Int? = null,
    val capacityEstimated: Boolean = false,
    val designCapacityMah: Int? = null,
    /**
     * 판정 결과. 파싱 시점에 한 번 계산해 carry 하므로 게이지·텔레메트리·리포트가
     * 같은 값을 공유한다. 표시 경로가 따로 재판정하지 않는 것이 이중 기준을 없애는 핵심이다.
     */
    val assessment: BatteryHealthAssessment = BatteryHealthClassifier.assess(null, null)
) {
    /** 측정·추정 용량에 적용할 건강도 배율. 판정과 같은 H(BSOH 우선)를 쓴다. */
    val healthFactor: Double
        get() = assessment.healthValue?.takeIf { it in 1..100 }?.div(100.0) ?: 1.0

    /** ASOC 원시값 폴백. 출처 비교는 문자열이 아니라 enum으로 한다. */
    val displayAsocRaw: String
        get() = asocRaw.ifBlank {
            if (healthSource == HealthSource.ASOC) asocValue?.toString() ?: "—" else "—"
        }
}

data class DocEntry(
    val id: String,
    val name: String,
    val mime: String,
    val lastModified: Long = 0L
)

/** Overlay error dialogs — replaces the three full-screen error routes. */
sealed class ErrorDialog {
    data class WrongFolder(val errorMessage: String) : ErrorDialog()
    object FolderDeleted  : ErrorDialog()
    object PermissionLost : ErrorDialog()
}