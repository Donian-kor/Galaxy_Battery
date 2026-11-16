package com.donian.galaxy_battery

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import com.donian.galaxy_battery.ui.theme.ThemeModeStore
import com.donian.galaxy_battery.ui.theme.resolveDarkTheme

/**
 * 앱 전체가 같은 AppViewModel 을 쓰도록 소유하는 Application.
 *
 * Activity 마다 ViewModel 을 따로 만들면 스캔 상태와 캐시가 화면마다 갈라진다.
 * 그래서 여기서 한 번만 만들어 두고 [appViewModel] 로 꺼내 쓴다.
 *
 * 화면 테마도 같은 이유로 여기서 한 번만 들고 있는다. 세 Activity 가 각자 값을 들면
 * 한 화면에서 바꾼 테마가 다른 화면에 반영되지 않는다.
 */
class GalaxyBatteryApp : Application() {

    private val viewModelFactory by lazy {
        ViewModelProvider.AndroidViewModelFactory.getInstance(this)
    }

    val viewModel: AppViewModel by lazy {
        viewModelFactory.create(AppViewModel::class.java)
    }

    /**
     * 생성자에서는 Context 가 아직 붙지 않아 `getSharedPreferences` 가 NPE 를 던진다.
     * Application 의 생성자는 `attachBaseContext` 보다 먼저 돌기 때문이다.
     * 그래서 실제 조회는 [onCreate] 에서 한다.
     */
    private val prefs by lazy {
        getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
    }

    private val darkTheme = mutableStateOf(false)

    /**
     * 지금 화면이 다크 모드인지.
     *
     * 사용자가 고른 적이 없으면 기기 설정을 따르고, 한 번 고르면 그 선택을 유지한다.
     * Compose 스냅샷 상태라 값이 바뀌면 떠 있는 모든 화면이 다시 그려진다.
     */
    var isDarkTheme: Boolean
        get() = darkTheme.value
        private set(value) {
            darkTheme.value = value
        }

    override fun onCreate() {
        super.onCreate()
        isDarkTheme = resolveDarkTheme(
            stored     = ThemeModeStore.storedDark(prefs),
            systemDark = ThemeModeStore.systemDark(resources.configuration)
        )
    }

    /**
     * 사용자가 고른 테마를 저장하고 즉시 화면에 반영한다.
     *
     * 이름을 `setDarkTheme` 으로 두면 [isDarkTheme] 의 자동 생성 setter 와 JVM 시그니처가
     * 겹친다(둘 다 `setDarkTheme(Z)V`).
     */
    fun applyTheme(dark: Boolean) {
        ThemeModeStore.saveDark(prefs, dark)
        isDarkTheme = dark
    }
}

/** 어느 Activity 에서든 공유 ViewModel 에 닿게 하는 확장. */
val android.app.Activity.appViewModel: AppViewModel
    get() = (application as GalaxyBatteryApp).viewModel

/** 어느 Activity 에서든 앱 전역 테마 상태에 닿게 하는 확장. */
val android.app.Activity.galaxyApp: GalaxyBatteryApp
    get() = application as GalaxyBatteryApp
