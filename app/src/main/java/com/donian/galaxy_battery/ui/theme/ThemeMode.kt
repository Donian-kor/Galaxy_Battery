package com.donian.galaxy_battery.ui.theme

import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.content.edit

/**
 * 다크 모드 여부를 확정한다.
 *
 * 사용자가 한 번 고르면 그 값을 따르고, 아직 고른 적이 없으면([stored] 가 null) 기기 설정을 따른다.
 * Android 클래스에 기대지 않는 순수 함수라 그대로 테스트할 수 있다.
 */
fun resolveDarkTheme(stored: Boolean?, systemDark: Boolean): Boolean = stored ?: systemDark

/** 다크 모드 선택을 [SharedPreferences] 에 보관한다. */
object ThemeModeStore {

    private const val KEY_DARK = "theme_dark"

    /** 저장된 선택. 고른 적이 없으면 null(기기 설정 따름). */
    fun storedDark(prefs: SharedPreferences): Boolean? =
        if (prefs.contains(KEY_DARK)) prefs.getBoolean(KEY_DARK, false) else null

    /** 사용자가 고른 값을 저장한다. */
    fun saveDark(prefs: SharedPreferences, dark: Boolean) {
        prefs.edit { putBoolean(KEY_DARK, dark) }
    }

    /** 기기 설정이 다크 모드인지. */
    fun systemDark(configuration: Configuration): Boolean =
        configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
