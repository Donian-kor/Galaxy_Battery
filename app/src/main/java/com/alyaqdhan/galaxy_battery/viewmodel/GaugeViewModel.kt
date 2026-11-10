package com.alyaqdhan.galaxy_battery.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GaugeViewModel(app: Application) : AndroidViewModel(app) {
    var gaugeAmplitude by mutableFloatStateOf(1f)
        private set
    var gaugeReplayKey by mutableIntStateOf(0)
        private set

    private var pulseJob: Job? = null

    fun incrementReplayKey() { gaugeReplayKey++ }

    fun startGaugePulse() {
        pulseJob?.cancel()
        pulseJob = viewModelScope.launch {
            while (true) {
                delay(900L)
                gaugeAmplitude = if (gaugeAmplitude < 0.5f) 1f else 0f
            }
        }
    }

    fun stopGaugePulse() {
        pulseJob?.cancel()
        pulseJob = null
        gaugeAmplitude = 1f
    }
}