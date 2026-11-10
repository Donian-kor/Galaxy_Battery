# Galaxy_Battery

<img width="250" alt="Screenshot_20261107-073946_Galaxy Battery" src="https://github.com/user-attachments/assets/a595121e-b44f-4a02-9077-caf02e98947c" />
<img width="250" alt="Screenshot_20261108-013036_Galaxy Battery" src="https://github.com/user-attachments/assets/ae7c8e1d-3d40-4a04-9aa0-5d522ccdbdda" />




## 상세 정보
GalaxyBattery는 Samsung 덤프스테이트 배터리 로그(`dumpState_*.log`)를 읽어 다음과 같은 값을 추출합니다:

- `mSavedBatteryAsoc`
  배터리 상태 비율(기본값).
  Samsung이 제공하는 가장 정확한 용량 추정치입니다.
  예: `96`은 배터리가 원래 용량의 약 96%를 보유하고 있음을 의미합니다.

- `mSavedBatteryBsoh`
  배터리 상태 비율(대체값).
  `mSavedBatteryAsoc`이 없거나 지원되지 않을 때만 사용됩니다.

- `mSavedBatteryUsage`
  배터리 사이클 수(충전 회수).
  원시 값은 사이클 × 100으로 저장됩니다.
  예: `157000`은 약 1570 사이클을 의미합니다.

- `LLB CAL` 또는 `LLB MAN`
  배터리 제조/조립 날짜입니다.
  배터리가 언제 생산되었는지 추정하는 데 사용됩니다.

- 로그 타임스탬프(dumpstate 헤더 또는 파일명에서)
  앱은 로그가 생성된 시점을 감지하여 데이터가 얼마나 최근의 것인지 표시합니다.



> [!IMPORTANT]
> GalaxyBattery는 공식 Samsung 앱이 아닙니다.  
> 배터리 결과는 기기에서 사용할 수 있는 로그 파일에 따라 달라집니다.
