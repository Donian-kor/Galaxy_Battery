package com.donian.galaxy_battery

import android.app.Application
import androidx.lifecycle.ViewModelProvider

/**
 * 앱 전체가 같은 AppViewModel 을 쓰도록 소유하는 Application.
 *
 * Activity 마다 ViewModel 을 따로 만들면 스캔 상태와 캐시가 화면마다 갈라진다.
 * 그래서 여기서 한 번만 만들어 두고 [appViewModel] 로 꺼내 쓴다.
 */
class GalaxyBatteryApp : Application() {

    private val viewModelFactory by lazy {
        ViewModelProvider.AndroidViewModelFactory.getInstance(this)
    }

    val viewModel: AppViewModel by lazy {
        viewModelFactory.create(AppViewModel::class.java)
    }
}

/** 어느 Activity 에서든 공유 ViewModel 에 닿게 하는 확장. */
val android.app.Activity.appViewModel: AppViewModel
    get() = (application as GalaxyBatteryApp).viewModel
