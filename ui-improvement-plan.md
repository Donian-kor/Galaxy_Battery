# 갤럭시 배터리 정보 — UI 개선 기획서 (보완 갱신)

> 작성일: 2026-11-10 (기술 검토 후 보완 갱신)  
> 대상: `C:\Users\donian\Desktop\android_app\Galaxy_Battery`  
> 모드: Plan (구현은 Build 모드 전환 후 진행)

---

## 0. 결정 사항 (확정 및 보완)

- **컬러 후보:** A — Dark-first Material You (확정, 고정 브랜드 다크 테마 방식 적용)
- **스코프:** Phase U1~U5 전부
- **참고 스타일:** 2026 트렌드 (Dark-first, Glassmorphism 2.0, Neumorphism, Bento Minimal 중 A 선택)
- **모션:** Shared Element (화면 내) + Activity Scene Transition + Spring + Staggered (상위 제한) + Haptic
- **mockup 이미지:** OpenRouter 크레딧 부족으로 생성 실패. 텍스트 mockup(A/B/C/D)은 `mockup-a/b/c/d.html`에 보관 (프로젝트 폴더).

---

## 1. A 팔레트 및 테마 정책 (U1부터 적용)

| 토큰 | 값 | 용도 |
|---|---|---|
| `bgDeep` | `#0A0E18` | 페이지 배경 (true dark, OLED) |
| `surface1` | `#111827` | 카드/1차 표면 |
| `surface2` | `#1F2D42` | 2차 표면 (elevated) |
| `surface3` | `#2A3A55` | 3차 표면 (overlay) |
| `border` | `#1F2D42` | 헤어라인 |
| `textPrimary` | `#E8EEFF` | 본문 |
| `textSecondary` | `#7A8BA8` | 보조 |
| `textMuted` | `#5A7090` | 뮤트 |
| `accent` | `#00E5A0` | 그린 액센트 |
| `accentSecondary` | `#4DA6FF` | 블루 보조 |
| `warning` | `#FFAA44` | 경고 |
| `error` | `#FF5C6C` | 에러 |

- **테마 정책:** 2026 Dark-first 일관성 유지를 위해 `GalaxyBatteryTheme`에서 Palette A를 앱 전역 고정 브랜드 표면으로 적용 (Android 12+ 벽지 동적 컬러의 표면 일탈 방지).
- 다크 기본, 라이트는 파생 (tonal)
- 게이지/경고색은 고정 (벽지 영향 X)
- 깊이: 그림자 대신 톤(luminance) 4단계

---

## 2. Phase 구성 (보완 반영)

### Phase U1 — 디자인 토큰 정리 (기반) [선행 필수]
- `ui/theme/Color.kt` — A 팔레트로 재정의 (Dark/Light 톤 체계화)
- `ui/theme/Type.kt` — Typography 스케일 정돈
- `ui/theme/Shape.kt` (신규) — corner radius 토큰 (4/8/12/16/24dp)
- `ui/theme/Spacing.kt` (신규) — spacing scale (4/8/12/16/20/24/32dp, `LocalSpacing` 제공)
- `ui/theme/Theme.kt` — `GalaxyBatteryTheme`에서 Palette A 고정 및 토큰 전역 주입
- **경계:** `ui/theme/` 패키지 전체
- **검증:** `assembleDebug` + 기기 다크/라이트 전환 확인

### Phase U2 — 대시보드 레이아웃 개선
- 헤더 고정 (앱 이름 + 로그 선택 버튼), 스크롤 영역 분리
- `HealthGaugeCard`: 게이지 크기/여백/애니메이션 정돈
- `CycleCountCard`, 원시로그 진입 카드: 시각적 계층 (tonal)
- `RescanPullToRefreshBox`: 인디케이터 스타일 통일
- 빈 상태(empty state) 컴포넌트 추가
- Bold Motion: 화면 내 Morphing Shapes, Spring Physics 적용
- **경계:** `ui/main/MainScreen.kt`, `GaugeComponents.kt`, `CommonComponents.kt`

### Phase U3 — 설정/온보딩 플로우
- 단계 카드: 번호 배지 + 설명 + 액션 버튼 정렬
- 다이얼 코드 편집 다이얼로그 스타일 통일
- 버튼 상태(활성/비활성/로딩) 명확화
- 권한 부여/제거 버튼 색상 구분 (destructive = red)
- 빈 상태 일러스트/문구
- **경계:** `ui/setup/SetupScreen.kt`, `SetupComponents.kt`

### Phase U4 — 원시로그 화면
- 검색바 고정 (top app bar에 통합)
- 검색 결과 하이라이트 강조 및 웨스턴 숫자 변환 유지
- 섹션 헤더(━━━) 스타일 개선
- 뒤로가기/공유 액션 버튼 정리
- Bold Motion: Staggered Entrance (저사양 프레임 드롭 방지를 위해 상위 10개/섹션 단위 제한)
- Activity 간 전환: Activity Scene Transition 애니메이션 적용 (Multi-Activity 구조 연동)
- **경계:** `ui/rawlog/RawLogScreen.kt`, `LogComponents.kt`

### Phase U5 — 인터랙션·접근성·마감
- 버튼/카드 터치 영역 최소 48dp (`Modifier.minimumInteractiveComponentSize()`)
- `contentDescription` 보강 (TalkBack 호환)
- 로딩/에러/빈 상태 일관된 컴포넌트
- 마이크로 애니메이션 정돈
- Bold Motion: Haptic Feedback (`LocalHapticFeedback`)
- **경계:** 전 화면 + `CommonComponents.kt`, `ErrorDialogs.kt`

---

## 3. Bold Motion 적용 계획 (보완)

| 모션 | 적용 위치 | 구현 | Phase |
|---|---|---|---|
| In-screen Shared Transition | 대시보드 모달/시트/상태 전환 | `SharedTransitionLayout` + `sharedElement` | U2 |
| Activity Scene Transition | 대시보드 ↔ 원시로그 Activity | `ActivityOptionsCompat` / Activity Custom Animation | U2/U4 |
| Spring Physics | 카드 등장, 게이지 펄스 | `spring(dampingRatio, stiffness)` | U2/U5 |
| Staggered List Entrance | 로그 목록 아이템 (상위 제한) | `animateItemPlacement` + delay (상위 N개) | U4 |
| Haptic Feedback | 버튼/게이지 탭 | `LocalHapticFeedback` | U5 |
| Morphing Shapes | 건강도 표시 (게이지 ↔ 숫자) | `AnimatedContent` + `SizeTransform` | U2 |
| Scroll-driven Header Collapse | 대시보드 헤더 축소 | `NestedScrollConnection` | U2 |

**피해야 할 것:** 모든 요소에 블러/글래스 + 과도한 패럴랙스 (저사양 기기 프레임 드롭), 전체 로그 항목 개별 애니메이션.

---

## 4. 실행 순서 & 커밋 전략

```
U1 (토큰) → U2 (대시보드) → U3 (설정) → U4 (원시로그) → U5 (마감/모션)
```

- 각 Phase 완료 시: `assembleDebug` + `connectedAndroidTest` (기기 SM-F926N) 통과 후 **커밋**.
- 커밋 메시지: `feat(ui): Phase UX - <설명>` (한국어).
- Phase U1은 U2~U5의 선행 조건.

---

## 5. 검증 체크리스트 (각 Phase 공통)

- [ ] `.\gradlew.bat assembleDebug` 성공
- [ ] `.\gradlew.bat connectedAndroidTest` (기기) 성공
- [ ] 다크/라이트 모드 모두 확인 (Palette A 기준)
- [ ] TalkBack 활성화 시 주요 요소 읽힘 및 48dp 터치 영역 확인 (U5)
- [ ] 저사양 기기 성능 확인 (U5)

---

## 6. 비범위 (Out of Scope)

- 브랜드 로고/아이콘 재디자인
- 새 라이브러리 추가 (Lottie/Rive는 U5에서 선택적)
- 기능 추가/버그 수정 (UI 개선만)

---

## 7. 다음 액션

1. Build 모드 전환
2. "U1 진행해" 지시 → 디자인 토큰 재정의부터 시작
3. 각 Phase 완료 시 기기 테스트 + 커밋

*이 문서는 Plan 모드에서 보완 갱신되었습니다. 구현은 Build 모드 전환 후 진행합니다.*