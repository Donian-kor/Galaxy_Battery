# Keep the Application subclass
-keep class com.donian.galaxy_battery.GalaxyBatteryApp { *; }

# Keep data models (referenced via reflection by Compose/state)
-keep class com.donian.galaxy_battery.BatteryInfo { *; }
-keep class com.donian.galaxy_battery.DocEntry { *; }
-keep class com.donian.galaxy_battery.ErrorDialog { *; }
-keep class com.donian.galaxy_battery.viewmodel.** { *; }

# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }