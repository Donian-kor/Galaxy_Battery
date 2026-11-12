package com.alyaqdhan.galaxy_battery

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
    val batteryDateMs: Long = 0L,
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