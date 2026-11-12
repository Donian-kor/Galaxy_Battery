package com.donian.galaxy_battery.parser

import java.util.Locale

/**
 * 기기 코드 → 설계 용량(mAh) 조회표.
 *
 * 코드를 직접 수정하지 않아도 되는 것을 목표로, 모델 코드와 용량 쌍만 데이터로
 * 모아 두었다. 새 기종은 [ENTRIES]에 한 줄 추가하면 된다.
 *
 * 매칭은 "모델 문자열이 코드를 포함하는가"이며, 위에 있는 항목이 이긴다.
 * 폴더블·플립처럼 코드가 겹치는 계열은 더 긴(구체적인) 코드를 위에 둔다.
 */
object DesignCapacityTable {

    private data class Entry(val code: String, val capacityMah: Int, val label: String)

    private val ENTRIES: List<Entry> = listOf(
        // Fold
        Entry("F966", 4400, "Fold 7"), Entry("F960", 4400, "Fold 7"),
        Entry("F976", 4400, "Fold 8"), Entry("F970", 4400, "Fold 8"),
        Entry("F926", 4400, "Fold 3"),
        Entry("F936", 4400, "Fold 4"),
        Entry("F946", 4400, "Fold 5"),
        Entry("F956", 4400, "Fold 6"),
        Entry("F900", 4380, "Fold 1"), Entry("F907", 4380, "Fold 1"),
        Entry("F916", 4500, "Fold 2"),

        // Flip
        Entry("F751", 4000, "Flip 7"), Entry("F750", 4000, "Flip 7"),
        Entry("F761", 4000, "Flip 8"), Entry("F760", 4000, "Flip 8"),
        Entry("F700", 3300, "Flip 1"), Entry("F707", 3300, "Flip 1"),
        Entry("F711", 3300, "Flip 3"),
        Entry("F721", 3700, "Flip 4"),
        Entry("F731", 3700, "Flip 5"),
        Entry("F741", 4000, "Flip 6"),

        // S26
        Entry("S941", 4000, "S26"),
        Entry("S946", 4900, "S26+"),
        Entry("S948", 5000, "S26 Ultra"),

        // S25
        Entry("S931", 4000, "S25"),
        Entry("S936", 4900, "S25+"),
        Entry("S938", 5000, "S25 Ultra"),

        // S24
        Entry("S921", 4000, "S24"),
        Entry("S926", 4900, "S24+"),
        Entry("S928", 5000, "S24 Ultra"),
        Entry("S721", 4700, "S24 FE"),

        // S23
        Entry("S911", 3900, "S23"),
        Entry("S916", 4700, "S23+"),
        Entry("S918", 5000, "S23 Ultra"),
        Entry("S711", 4500, "S23 FE"),

        // S22
        Entry("S901", 3700, "S22"),
        Entry("S906", 4500, "S22+"),
        Entry("S908", 5000, "S22 Ultra"),

        // S21
        Entry("G991", 4000, "S21"),
        Entry("G996", 4800, "S21+"),
        Entry("G998", 5000, "S21 Ultra"),
        Entry("G990", 4500, "S21 FE"),

        // S20
        Entry("G981", 4000, "S20 FE"), Entry("G980", 4000, "S20"),
        Entry("G986", 4500, "S20+"), Entry("G985", 4500, "S20 FE 5G"),
        Entry("G988", 5000, "S20 Ultra"),
        Entry("G781", 4500, "S20 FE"), Entry("G780", 4500, "S20 FE"),

        // Note
        Entry("N970", 3500, "Note 10"),
        Entry("N975", 4300, "Note 10+"), Entry("N976", 4300, "Note 10+ 5G"),
        Entry("N981", 4300, "Note 20"), Entry("N980", 4300, "Note 20"),
        Entry("N986", 4500, "Note 20 Ultra"), Entry("N985", 4500, "Note 20 Ultra"),

        // S10
        Entry("G970", 3100, "S10e"),
        Entry("G973", 3400, "S10"),
        Entry("G975", 4100, "S10+"),
        Entry("G977", 4500, "S10 5G")
    )

    /** 모델 코드나 로그 파일 이름에서 설계 용량을 찾는다. 없으면 null. */
    fun lookup(modelOrFileName: String): Int? {
        val upper = modelOrFileName.uppercase(Locale.US)
        return ENTRIES.firstOrNull { upper.contains(it.code) }?.capacityMah
    }
}