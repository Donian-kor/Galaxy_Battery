# Keep the Application subclass
-keep class com.alyaqdhan.galaxy_battery.GalaxyBatteryApp { *; }

# Keep data models (referenced via reflection by Compose/state)
-keep class com.alyaqdhan.galaxy_battery.BatteryInfo { *; }
-keep class com.alyaqdhan.galaxy_battery.DocEntry { *; }
-keep class com.alyaqdhan.galaxy_battery.ErrorDialog { *; }
-keep class com.alyaqdhan.galaxy_battery.viewmodel.** { *; }

# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }