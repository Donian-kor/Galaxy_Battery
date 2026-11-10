# 갤럭시 배터리 정보 — 리팩터링 계획

> 버전: v1.1 (검토 의견 반영 갱신)  
> 작성일: 2026-11-10 (최종 수정일: 2026-11-10)  
> 대상: `C:\Users\donian\Desktop\android_app\Galaxy_Battery`  
> 기준 브랜치 상태: 빌드/테스트 통과 (`assembleDebug`, `testDebugUnitTest` SUCCESS)  
> 범위 원칙: 동작 변경 없이 구조만 개선. 단계별로 빌드·테스트 통과를 유지하며 진행.

---

## 0. 현재 구조 스냅샷

| 파일 | 라인 | 역할 |
|---|---|---|
| `MainActivity.kt` | 542 | Activity + Compose 화면 (대시보드) |
| `AppViewModel.kt` | 476 | 단일 ViewModel (폴더/스캔/로그시트/원시로그/다이얼/게이지) |
| `SetupActivity.kt` | 426 | Activity + Compose 화면 (설정/권한) |
| `BatteryParser.kt` | 381 | SAF/Context 의존 파서 + 순수 파싱 |
| `SetupComponents.kt` | 364 | Compose 컴포넌트 |
| `LogRawActivity.kt` | 240 | Activity + Compose 화면 (원시 로그) |
| `GaugeComponents.kt` | 333 | Compose 컴포넌트 |
| `AppTheme.kt` | 277 | 테마 (색상+타이포+Theme) |
| `BatteryCache.kt` | 47 | SharedPreferences 캐시 |
| `BatteryModels.kt` | 31 | 데이터 모델 |

의존 방향: `UI(Activity/Compose)` → `AppViewModel` → `BatteryParser`/`BatteryCache` → `BatteryModels`. 순환 없음. 단일 `:app` 모듈.

---

## Phase 1 — 보안·설정 보강 ✅ (완료)

| 항목 | 파일 | 경계 |
|---|---|---|
| dial code 입력 검증 | `SetupActivity.openDialer` | 함수 단위 |
| 백업 제외 규칙 | `backup_rules.xml`, `data_extraction_rules.xml` | 리소스 단위 |
| `!!` 제거 | 테스트 3건 | 테스트 단위 |

**검증:** `testDebugUnitTest` 통과.  
**상태:** 완료.

---

## Phase 2 — BatteryRepository 추출 (기반 계층)

### 목표
`BatteryParser`가 `Context`/`SharedPreferences`를 직접 받는 결합을 해소.  
파싱 로직(`parseInputStream`)은 순수 함수로 유지, SAF/Context 의존 및 캐시는 Repository로 이동/캡슐화.

### 경계 (수정 대상)
- **신규:** `data/BatteryRepository.kt`
- **수정:** `BatteryParser.kt` (SAF 메서드는 Repository로 이동, 순수 파싱은 유지)
- **수정:** `BatteryCache.kt` (Repository 내부로 캡슐화, 외부 직접 노출 제거)
- **수정:** `AppViewModel.kt` (호출부 `BatteryParser.*`/`BatteryCache.*` → `BatteryRepository.*`)

### 이동 대상 메서드 (BatteryParser → Repository)
- `hasPersistedPermission`, `listChildren`, `findLatestLogEntry`, `listAllLogs`
- `parseLogEntry`, `parseLatestLog`, `smartScan`, `extractBatterySection`

### 유지 (순수 함수, BatteryParser에 남김)
- `isDumpStateLog`, `truncateLogDisplayName`, `readBatterySections`, `parseInputStream`, `extractTimestampFromFileName`, `extractValueOrNull`

### 보완점 (v1.1 반영)
1. **SAF 예외 처리 보강**: SAF 접근 시 발생 가능한 `SecurityException`, `FileNotFoundException`, `IOException`을 Repository 레벨에서 안전하게 처리하거나 `Result<T>` 형태로 전달.
2. **캐시 캡슐화**: `BatteryCache`를 `BatteryRepository` 내부 전용 구현으로 격리하여 ViewModel이 캐시 존재 여부를 알 필요 없도록 감춤.

### 인터페이스 예시
```kotlin
class BatteryRepository(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    fun findLatestLogEntry(folderUri: Uri): DocEntry?
    fun listAllLogs(folderUri: Uri): List<DocEntry>
    fun smartScan(folderUri: Uri): Result<BatteryInfo>
    fun parseLogEntry(folderUri: Uri, entry: DocEntry): Result<BatteryInfo>
    fun parseLatestLog(folderUri: Uri): Result<BatteryInfo>
    fun extractBatterySection(folderUri: Uri, entry: DocEntry): String
    fun hasPersistedPermission(folderUri: Uri): Boolean
    // BatteryCache는 내부에서 위임 처리
}
```

### 검증
- `BatteryParserTest` (순수 파싱) 그대로 통과.
- 신규 `BatteryRepositoryTest` (Robolectric 또는 Fake Context/ContentResolver) 추가.
- `assembleDebug` 통과.

---

## Phase 3 — AppViewModel 구조 개선 및 분해 (화면 단위 + StateFlow)

### 목표
476라인 단일 ViewModel을 3개 화면/기능 단위 ViewModel로 정리하고, Compose `mutableStateOf` 직저장 방식을 **Kotlin `StateFlow` (Unidirectional Data Flow)** 패턴으로 전환하여 단위 테스트 용이성 확보.

### 경계 (수정 대상)
- **신규 UI State:**
  - `viewmodel/DashboardUiState.kt` (batteryInfo, isRefreshing, isLoadingDetail, allLogEntries, showLogSheet, gaugeAmplitude, gaugeReplayKey 등)
  - `viewmodel/SetupUiState.kt` (folderUri, alreadyHasPerm, folderAccessible, hasEverScanned, dialCode 등)
  - `viewmodel/RawLogUiState.kt` (rawLogText, isLoadingRaw, selectedLogFileName 등)
- **신규 ViewModel:**
  - `viewmodel/MainViewModel.kt` (`MainActivity` 대시보드 상태, 배터리 스캔/리프레시, LogSheet 선택, 게이지 펄스)
  - `viewmodel/SetupViewModel.kt` (`SetupActivity` SAF 권한 관리, 폴더 픽, 다이얼 코드 설정)
  - `viewmodel/RawLogViewModel.kt` (`LogRawActivity` 원시 덤프 로그 로딩 및 표시)
- **수정/삭제:** `AppViewModel.kt` (임시 Facade 유지 후 완료 시 제거)

### 서브페이즈 (각각 빌드·테스트 통과 후 커밋)
| 서브 | 내용 | 경계 파일 |
|---|---|---|
| 3a | `DashboardUiState`, `SetupUiState`, `RawLogUiState` 작성 및 StateFlow 구조 적용 | `AppViewModel.kt` |
| 3b | `SetupViewModel` 분리 및 `SetupActivity` 적용 | `SetupViewModel.kt`, `SetupActivity.kt` |
| 3c | `RawLogViewModel` 분리 및 `LogRawActivity` 적용 | `RawLogViewModel.kt`, `LogRawActivity.kt` |
| 3d | `MainViewModel` 분리 및 `MainActivity` 적용 | `MainViewModel.kt`, `MainActivity.kt` |
| 3e | 기존 `AppViewModel` 정리/제거 | `AppViewModel.kt` |

### 검증
- 각 서브페이즈 후 `assembleDebug` + `testDebugUnitTest`.

---

## Phase 4 — UI 파일 추출 (Route - Screen 패턴)

### 목표
Activity 파일에서 Compose 화면을 분리하고, ViewModel 직주입 대신 **Route - Screen 패턴**을 적용해 상태와 이벤트를 명확히 분리.

### 구조 (v1.1 반영)
- **Route Composable**: ViewModel 관조, `collectAsStateWithLifecycle()` 수집, 이벤트 handler 전달
- **Screen Composable**: 순수 UI Composable (State Data Class + Event Lambda만 수신)

### 경계 (수정 대상)
- **신규 패키지:** `ui/main/`, `ui/setup/`, `ui/rawlog/`
- **신규:** `ui/main/MainScreen.kt` & `MainRoute.kt`
- **신규:** `ui/setup/SetupScreen.kt` & `SetupRoute.kt`
- **신규:** `ui/rawlog/RawLogScreen.kt` & `RawLogRoute.kt`
- **수정:** `MainActivity.kt`, `SetupActivity.kt`, `LogRawActivity.kt` (Activity는 lifecycle 설정 및 Route 호출만 담당)

### 검증
- `assembleDebug` 통과.
- `@Preview` 컴포저블 동작 검증 (ViewModel Mock 없이 Pure State 수신 가능).

---

## Phase 5 — 테마 분리

### 목표
`AppTheme.kt`(277라인)를 표준 Compose 테마 구조로 분리.

### 경계
- **신규:** `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/Theme.kt`
- **삭제:** `AppTheme.kt` (내용 이동)
- **수정:** import 참조 (`GalaxyBatteryTheme` 호출부)

### 검증
- `assembleDebug` 통과.

---

## Phase 6 — 테스트 커버리지 확장

### 목표
Repository, ViewModel, UI State 관리에 대한 단위 테스트 구축.

### 경계
- **신규:** `BatteryRepositoryTest.kt`, `MainViewModelTest.kt`, `SetupViewModelTest.kt`
- **수정:** 기존 4개 테스트 파일 (필요 시)

### 인프라 및 도구
- `kotlinx.coroutines.test` (`runTest`, `Turbine` 또는 `StateFlow` 값 검증)
- Robolectric / Fake `ContentResolver` (Repository SAF 파싱 테스트)

### 검증
- `testDebugUnitTest` 통과.

---

## Phase 7 — 성능·정리 (선택)

| 항목 | 파일 | 경계 |
|---|---|---|
| `SimpleDateFormat` ThreadLocal 재사용 / `java.time` 전환 | `BatteryParser.kt` | 메서드 단위 |
| `derivedStateOf`/`remember` 최적화 | `MainActivity.kt`, `GaugeComponents.kt` | Compose 단위 |
| ProGuard / R8 규칙 보강 | `proguard-rules.pro` | 파일 단위 |
| `findLatestLogEntry` 탐색 깊이 문서화 | `BatteryParser.kt` | 메서드 단위 |

---

## 실행 순서 요약

```
Phase 1 (완료) → Phase 2 → Phase 3 (3a→3e) → Phase 4 → Phase 5
                                                          ↓
                                              Phase 6 → Phase 7
```

---

## 각 Phase 공통 검증 체크리스트

- [ ] `.\gradlew.bat assembleDebug` 성공
- [ ] `.\gradlew.bat testDebugUnitTest` 성공
- [ ] 변경된 파일 라인 수가 1000라인 이하 유지
- [ ] 단일 커밋 또는 서브페이즈별 커밋
- [ ] 미사용 import/변수 정리

---

## 롤백 및 위험 완화 전략

| Phase | 리스크 | 완화 전략 |
|---|---|---|
| Phase 2 | SAF 권한 및 예외 발생 | `Result<T>` 래핑 및 Repository 단위 테스트 |
| Phase 3 | ViewModel 과도 분할 | 화면 중심 3개 ViewModel로 범위 제한 및 StateFlow 도입 |
| Phase 4 | `@Preview` 파손 | Route - Screen 분리로 ViewModel 주입 제거 |
