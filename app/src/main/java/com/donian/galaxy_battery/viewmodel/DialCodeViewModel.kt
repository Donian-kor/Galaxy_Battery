package com.donian.galaxy_battery.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel

class DialCodeViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)

    var dialCode by mutableStateOf(prefs.getString("dial_code", "*#9900") ?: "*#9900")
        private set

    fun saveDialCode(code: String) {
        dialCode = code.ifBlank { "*#9900" }
        prefs.edit { putString("dial_code", dialCode) }
    }
}