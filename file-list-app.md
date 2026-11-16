# 앱 작동 관련 파일 목록 (공백 포함 줄수, 줄 수 내림차순)

> 대상: `app/src/main` + `app/build.gradle.kts` + `proguard-rules.pro`. 경로 전체 주소는 제외하고, 파일명과 바로 위 폴더명만 표시합니다.
> 줄 수는 **IDE 표시와 같은 기준**(마지막 빈 줄 포함)입니다. 아이콘 PNG는 바이너리라 제외했습니다.
> 테스트 소스는 아래 **별도 표**로 분리했습니다.

## 1. 앱 본체 (87개 / 7,335줄)

| # | 파일명 | 폴더 | 줄 수 |
|---:|---|---|---:|
| 1 | AppViewModel.kt | galaxy_battery | 497 |
| 2 | HealthGaugeCard.kt | dashboard | 444 |
| 3 | SetupComponents.kt | setup | 368 |
| 4 | TelemetryCard.kt | dashboard | 365 |
| 5 | BatteryParser.kt | parser | 350 |
| 6 | SetupScreen.kt | setup | 307 |
| 7 | MatchedIcon.kt | common | 292 |
| 8 | MainScreen.kt | dashboard | 271 |
| 9 | LogSheet.kt | dashboard | 245 |
| 10 | BatteryHealth.kt | model | 229 |
| 11 | Color.kt | theme | 214 |
| 12 | strings.xml | values | 210 |
| 13 | strings.xml | values-en | 210 |
| 14 | BatteryRepository.kt | data | 209 |
| 15 | RawLogScreen.kt | rawlog | 203 |
| 16 | LogComponents.kt | rawlog | 198 |
| 17 | ErrorDialogs.kt | common | 177 |
| 18 | LogSummaryCard.kt | dashboard | 162 |
| 19 | SetupActivity.kt | galaxy_battery | 157 |
| 20 | BatteryCache.kt | data | 142 |
| 21 | DashboardHeader.kt | dashboard | 141 |
| 22 | ScanPolicy.kt | data | 137 |
| 23 | SystemColors.kt | theme | 132 |
| 24 | BatteryReport.kt | model | 126 |
| 25 | Localization.kt | common | 109 |
| 26 | DesignCapacityTable.kt | parser | 96 |
| 27 | build.gradle.kts | app | 89 |
| 28 | BatteryModels.kt | model | 83 |
| 29 | GaugeCardShell.kt | dashboard | 83 |
| 30 | MainActivity.kt | galaxy_battery | 82 |
| 31 | CommonComponents.kt | common | 73 |
| 32 | FolderAccessManager.kt | data | 69 |
| 33 | Theme.kt | theme | 55 |
| 34 | NumberScanner.kt | parser | 53 |
| 35 | LogRawActivity.kt | galaxy_battery | 51 |
| 36 | AndroidManifest.xml | main | 48 |
| 37 | LogFileNameParser.kt | parser | 48 |
| 38 | GaugeViewModel.kt | viewmodel | 40 |
| 39 | GalaxyBatteryApp.kt | galaxy_battery | 26 |
| 40 | DialCodeViewModel.kt | viewmodel | 22 |
| 41 | FolderState.kt | viewmodel | 17 |
| 42 | themes.xml | values | 17 |
| 43 | themes.xml | values-night | 16 |
| 44 | themes.xml | values-night-v31 | 16 |
| 45 | themes.xml | values-v31 | 16 |
| 46 | BatteryState.kt | viewmodel | 15 |
| 47 | LogSheetState.kt | viewmodel | 13 |
| 48 | ic_sym_arrow_back.xml | drawable | 12 |
| 49 | ic_sym_arrow_forward_ios.xml | drawable | 12 |
| 50 | ic_sym_chevron_left.xml | drawable | 12 |
| 51 | ic_sym_chevron_right.xml | drawable | 12 |
| 52 | proguard-rules.pro | app | 12 |
| 53 | RawLogState.kt | viewmodel | 12 |
| 54 | Type.kt | theme | 12 |
| 55 | ic_sym_bolt.xml | drawable | 11 |
| 56 | ic_sym_calendar_month.xml | drawable | 11 |
| 57 | ic_sym_call.xml | drawable | 11 |
| 58 | ic_sym_check.xml | drawable | 11 |
| 59 | ic_sym_check_circle.xml | drawable | 11 |
| 60 | ic_sym_close.xml | drawable | 11 |
| 61 | ic_sym_delete.xml | drawable | 11 |
| 62 | ic_sym_description.xml | drawable | 11 |
| 63 | ic_sym_edit.xml | drawable | 11 |
| 64 | ic_sym_file_download.xml | drawable | 11 |
| 65 | ic_sym_folder.xml | drawable | 11 |
| 66 | ic_sym_info.xml | drawable | 11 |
| 67 | ic_sym_input.xml | drawable | 11 |
| 68 | ic_sym_memory.xml | drawable | 11 |
| 69 | ic_sym_play_arrow.xml | drawable | 11 |
| 70 | ic_sym_refresh.xml | drawable | 11 |
| 71 | ic_sym_sd_card.xml | drawable | 11 |
| 72 | ic_sym_search.xml | drawable | 11 |
| 73 | ic_sym_speed.xml | drawable | 11 |
| 74 | ic_sym_terminal.xml | drawable | 11 |
| 75 | ic_sym_thermostat.xml | drawable | 11 |
| 76 | ic_sym_tune.xml | drawable | 11 |
| 77 | ic_sym_verified.xml | drawable | 11 |
| 78 | ic_sym_warning.xml | drawable | 11 |
| 79 | data_extraction_rules.xml | xml | 10 |
| 80 | activity_log_raw.xml | layout | 8 |
| 81 | activity_main.xml | layout | 8 |
| 82 | activity_setup.xml | layout | 8 |
| 83 | locales_config.xml | xml | 7 |
| 84 | ic_launcher.xml | mipmap-anydpi | 6 |
| 85 | ic_launcher_background.xml | values | 6 |
| 86 | ic_launcher_round.xml | mipmap-anydpi | 6 |
| 87 | backup_rules.xml | xml | 5 |

**총 파일: 87개 / 총 줄 수: 7,335**

## 2. 테스트 (10개 / 1,680줄)

| 파일 | 소스셋 | 줄 수 | 케이스 |
|---|---|---:|---:|
| BatteryParserTest.kt | test | 402 | 38 |
| BatteryHealthTest.kt | test | 369 | 27 |
| BatteryCacheTest.kt | test | 188 | 8 |
| SystemColorsTest.kt | test | 138 | 12 |
| ScanPolicyTest.kt | test | 99 | 6 |
| LocalizationTest.kt | test | 88 | — |
| ScrollbarTest.kt | test | 53 | 5 |
| SetupComponentsUiTest.kt | androidTest | 182 | 8 |
| SetupLayoutUiTest.kt | androidTest | 134 | 3 |
| SetupScreenUiTest.kt | androidTest | 27 | 2 |

- **단위 테스트 `:app:testDebugUnitTest` — 106개 통과**
- **UI 테스트 `:app:connectedDebugAndroidTest` — 13개 통과** (SM-N976N 실기기. SM-F926N은 초기 10개로 확인)

> `LocalizationTest`의 케이스 수는 표에 적지 않았습니다(집계 기준이 다른 파일과 달라 오해를 부를 수 있음).

## 3. 폴더별 요약

| 폴더 | 파일 수 | 내용 |
|---|---:|---|
| `galaxy_battery` (루트) | 6 | `AppViewModel` · `MainActivity` · `SetupActivity` · `LogRawActivity` · `GalaxyBatteryApp` (+`R`) |
| `ui/dashboard` | 7 | `MainScreen` · `DashboardHeader` · `LogSheet` · `GaugeCardShell` · `HealthGaugeCard` · `TelemetryCard` · `LogSummaryCard` |
| `ui/common` | 4 | `CommonComponents` · `ErrorDialogs` · `MatchedIcon` · `Localization` |
| `ui/setup` | 2 | `SetupScreen` · `SetupComponents` |
| `ui/rawlog` | 2 | `RawLogScreen` · `LogComponents` |
| `ui/theme` | 4 | `Color` · `Theme` · `Type` · `SystemColors` |
| `model` | 3 | `BatteryModels` · `BatteryHealth` · `BatteryReport` |
| `data` | 4 | `BatteryRepository` · `BatteryCache` · `FolderAccessManager` · `ScanPolicy` |
| `data/parser` | 4 | `BatteryParser` · `LogFileNameParser` · `NumberScanner` · `DesignCapacityTable` |
| `viewmodel` | 6 | `BatteryState` · `FolderState` · `LogSheetState` · `RawLogState` · `GaugeViewModel` · `DialCodeViewModel` |

> 최대 파일 **497줄**(`AppViewModel.kt`) — 500줄 상한 이내. 500줄 초과 **0개**.
