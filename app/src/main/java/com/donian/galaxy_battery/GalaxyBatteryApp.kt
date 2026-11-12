package com.donian.galaxy_battery

import android.app.Application
import androidx.lifecycle.ViewModelProvider

/**
 * Application class that owns the shared AppViewModel singleton.
 * Accessed from every Activity via Application.appViewModel.
 */
class GalaxyBatteryApp : Application() {

    val viewModel: AppViewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory
            .getInstance(this)
            .create(AppViewModel::class.java)
    }
}

/** Convenience extension so any Activity can reach the shared ViewModel. */
val android.app.Activity.appViewModel: AppViewModel
    get() = (application as GalaxyBatteryApp).viewModel
