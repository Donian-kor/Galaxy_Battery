package com.alyaqdhan.galaxy_battery.parser

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * `dumpState_*.log` 파일 이름 다루기.
 *
 * 화면에 보여줄 이름 축약, 덤프 로그 판별, 파일 이름에 박힌 12자리 타임스탬프 읽기를
 * 담당한다. 파일 내용은 보지 않는다.
 */
object LogFileNameParser {

    private val timestampFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    /** `dumpState_`로 시작하고 `.log`로 끝나는 덤프스테이트 로그인지. */
    fun isDumpStateLog(name: String): Boolean =
        name.startsWith("dumpState_", ignoreCase = true) &&
                name.endsWith(".log", ignoreCase = true)

    /** 화면에 쓸 짧은 이름. 모델 코드 뒤 타임스탬프는 떼어 낸다. */
    fun truncateDisplayName(name: String): String {
        val noExt = if (name.endsWith(".log", ignoreCase = true)) name.dropLast(4) else name

        if (!noExt.startsWith("dumpState_", ignoreCase = true)) {
            return noExt.replace(Regex("""_\d{6,}$"""), "")
        }

        val afterPrefix = noExt.substring("dumpState_".length)
        val model = Regex("""[A-Za-z]\d+[A-Za-z]?""").find(afterPrefix)?.value
            ?: afterPrefix.substringBefore("_")

        return "dumpstate_$model"
    }

    /** 파일 이름의 12자리 타임스탬프(YYYYMMDDHHmm)를 epoch millis로. 없거나 깨졌으면 0. */
    fun timestampFromFileName(name: String): Long {
        val digits = Regex("""(\d{12})""").find(name)?.groupValues?.get(1) ?: return 0L
        val text = "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)} " +
                "${digits.substring(8, 10)}:${digits.substring(10, 12)}:00"
        return try {
            timestampFormat.get()!!.parse(text)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }
}