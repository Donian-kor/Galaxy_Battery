# 앱 작동 관련 파일 목록 (공백 포함 줄수, 줄 수 내림차순)

> 대상: app/src/main + app/build.gradle.kts + proguard-rules.pro. 경로 전체 주소는 제외하고, 파일명과 바로 위 폴더명만 표시합니다.
> 줄 수는 **공백 줄을 포함한** 전체 줄 수입니다. 아이콘 PNG는 바이너리라 제외했습니다.

| # | 파일명 | 폴더 | 줄 수 |
|---:|---|---|---:|
| 1 | AppViewModel.kt | galaxy_battery | 496 |
| 2 | HealthGaugeCard.kt | dashboard | 443 |
| 3 | TelemetryCard.kt | dashboard | 364 |
| 4 | BatteryParser.kt | parser | 349 |
| 5 | SetupComponents.kt | setup | 338 |
| 6 | SetupScreen.kt | setup | 306 |
| 7 | MatchedIcon.kt | common | 291 |
| 8 | MainScreen.kt | dashboard | 270 |
| 9 | LogSheet.kt | dashboard | 244 |
| 10 | BatteryHealth.kt | model | 228 |
| 11 | Color.kt | theme | 213 |
| 12 | strings.xml | values-en | 209 |
| 13 | strings.xml | values | 209 |
| 14 | BatteryRepository.kt | data | 208 |
| 15 | RawLogScreen.kt | rawlog | 202 |
| 16 | LogComponents.kt | rawlog | 197 |
| 17 | ErrorDialogs.kt | common | 176 |
| 18 | LogSummaryCard.kt | dashboard | 161 |
| 19 | SetupActivity.kt | galaxy_battery | 156 |
| 20 | BatteryCache.kt | data | 141 |
| 21 | DashboardHeader.kt | dashboard | 140 |
| 22 | ScanPolicy.kt | data | 136 |
| 23 | SystemColors.kt | theme | 131 |
| 24 | BatteryReport.kt | model | 125 |
| 25 | Localization.kt | common | 108 |
| 26 | DesignCapacityTable.kt | parser | 95 |
| 27 | build.gradle.kts | app | 88 |
| 28 | GaugeCardShell.kt | dashboard | 82 |
| 29 | BatteryModels.kt | model | 82 |
| 30 | MainActivity.kt | galaxy_battery | 81 |
| 31 | CommonComponents.kt | common | 72 |
| 32 | FolderAccessManager.kt | data | 68 |
| 33 | Theme.kt | theme | 54 |
| 34 | NumberScanner.kt | parser | 52 |
| 35 | LogRawActivity.kt | galaxy_battery | 50 |
| 36 | LogFileNameParser.kt | parser | 47 |
| 37 | AndroidManifest.xml | main | 47 |
| 38 | GaugeViewModel.kt | viewmodel | 39 |
| 39 | GalaxyBatteryApp.kt | galaxy_battery | 25 |
| 40 | DialCodeViewModel.kt | viewmodel | 21 |
| 41 | themes.xml | values | 16 |
| 42 | FolderState.kt | viewmodel | 16 |
| 43 | themes.xml | values-night | 15 |
| 44 | themes.xml | values-night-v31 | 15 |
| 45 | themes.xml | values-v31 | 15 |
| 46 | BatteryState.kt | viewmodel | 14 |
| 47 | LogSheetState.kt | viewmodel | 12 |
| 48 | ic_sym_chevron_right.xml | drawable | 11 |
| 49 | proguard-rules.pro | app | 11 |
| 50 | ic_sym_chevron_left.xml | drawable | 11 |
| 51 | ic_sym_arrow_back.xml | drawable | 11 |
| 52 | ic_sym_arrow_forward_ios.xml | drawable | 11 |
| 53 | RawLogState.kt | viewmodel | 11 |
| 54 | Type.kt | theme | 11 |
| 55 | ic_sym_verified.xml | drawable | 10 |
| 56 | ic_sym_warning.xml | drawable | 10 |
| 57 | ic_sym_tune.xml | drawable | 10 |
| 58 | ic_sym_terminal.xml | drawable | 10 |
| 59 | ic_sym_thermostat.xml | drawable | 10 |
| 60 | ic_sym_bolt.xml | drawable | 10 |
| 61 | ic_sym_check_circle.xml | drawable | 10 |
| 62 | ic_sym_calendar_month.xml | drawable | 10 |
| 63 | ic_sym_check.xml | drawable | 10 |
| 64 | ic_sym_call.xml | drawable | 10 |
| 65 | ic_sym_speed.xml | drawable | 10 |
| 66 | ic_sym_info.xml | drawable | 10 |
| 67 | ic_sym_folder.xml | drawable | 10 |
| 68 | ic_sym_close.xml | drawable | 10 |
| 69 | ic_sym_input.xml | drawable | 10 |
| 70 | ic_sym_description.xml | drawable | 10 |
| 71 | ic_sym_delete.xml | drawable | 10 |
| 72 | ic_sym_file_download.xml | drawable | 10 |
| 73 | ic_sym_edit.xml | drawable | 10 |
| 74 | ic_sym_search.xml | drawable | 10 |
| 75 | ic_sym_memory.xml | drawable | 10 |
| 76 | ic_sym_sd_card.xml | drawable | 10 |
| 77 | ic_sym_refresh.xml | drawable | 10 |
| 78 | ic_sym_play_arrow.xml | drawable | 10 |
| 79 | data_extraction_rules.xml | xml | 9 |
| 80 | activity_log_raw.xml | layout | 7 |
| 81 | activity_main.xml | layout | 7 |
| 82 | activity_setup.xml | layout | 7 |
| 83 | locales_config.xml | xml | 6 |
| 84 | ic_launcher_background.xml | values | 5 |
| 85 | ic_launcher_round.xml | mipmap-anydpi | 5 |
| 86 | ic_launcher.xml | mipmap-anydpi | 5 |
| 87 | backup_rules.xml | xml | 4 |

**총 파일: 87개 / 총 줄 수: 7219**

## 폴더별 요약

| 폴더 | 파일 수 | 내용 |
|---|---:|---|
| `galaxy_battery` (루트) | 6 | `AppViewModel` · `MainActivity` · `SetupActivity` · `LogRawActivity` · `GalaxyBatteryApp` · `R` |
| `ui/dashboard` | 7 | `MainScreen` · `DashboardHeader` · `LogSheet` · `GaugeCardShell` · `HealthGaugeCard` · `TelemetryCard` · `LogSummaryCard` |
| `ui/common` | 4 | `CommonComponents` · `ErrorDialogs` · `MatchedIcon` · `Localization` |
| `ui/setup` | 2 | `SetupScreen` · `SetupComponents` |
| `ui/rawlog` | 2 | `RawLogScreen` · `LogComponents` |
| `ui/theme` | 4 | `Color` · `Theme` · `Type` · `SystemColors` |
| `model` | 3 | `BatteryModels` · `BatteryHealth` · `BatteryReport` |
| `data` | 4 | `BatteryRepository` · `BatteryCache` · `FolderAccessManager` · `ScanPolicy` |
| `data/parser` | 4 | `BatteryParser` · `LogFileNameParser` · `NumberScanner` · `DesignCapacityTable` |
| `viewmodel` | 6 | `BatteryState` · `FolderState` · `LogSheetState` · `RawLogState` · `GaugeViewModel` · `DialCodeViewModel` |

> 최대 파일 496줄(`AppViewModel.kt`) — 500줄 상한 이내. 500줄 초과 파일 **0개**.
