# #180 복약·장소 일정 계약 — 조사·테스트 설계 (구현 전)

작성: 2026-10-12. 기준: origin/dev `f236f95` + #179 미커밋 변경(`/private/tmp/planb-stage2-179`) 읽기 전용 조사.
상태: 사용자 결정에 따라 **#179 dev 병합 후 부모 브랜치 생성**. 브랜치·worktree·코드 변경 없음.

## 확정 결정 (사용자 2026-10-12)

| 항목 | 결정 |
|---|---|
| 기준 코드 | #179 dev 병합 후 dev에서 부모 브랜치 |
| 겹침 대상 | 장소 필요한 비식사 슬롯 전부(관광지·공원·카페·MUST_HAVE 등). 식사·TRANSPORTATION·장소 없는 슬롯 제외 |
| 복구 | 뒤로 미루기만. 체류·이동 유지, 재정규화·복약 재생성 후 재검사, 최대 3회. 원래 창 안 식사가 창 밖으로 / 자정 넘김 / 겹침 잔존 시 INVALID_AI_PLACE |
| Project | High=P1, Effort=Size(부모 L, #181 S, #182 L, #183 M) |

## 코드 사실

- 최종 복약 생성: `PlanService.finishPlan` → `ScheduleNormalizer.ensureMedicationSchedules` (생성·편집·재구성 공통). `validatePlaces`(1373행 부근)에도 중간 호출 있음, 최종 판정 대상 아님.
- `moveOutOfPlaceSlots`: 복약 시작이 장소 [start, end) 안이면 장소 종료로 이동. 꼬리 겹침 미검사. 대상 `nonMealPlaceSlots`는 비식사 scheduleType 전부(TRANSPORTATION·CHECK_IN 포함 가능).
- 병합: `groupingBy(startTime)` 후 첫 non-null interval만 대표값. 규칙별 준수는 DTO로 판정 불가, 원본 healthContexts에서 재계산 필요.
- `normalizeScheduleTimes`: start = max(AI start, 직전 종료 + 이동). 여유 압축 없음, 뒤로 미룬 값 유지. 단, 늦은 식사 보정 `tryShiftPreviousPlaces`가 직전 식사 이후 장소를 앞당길 수 있어 미룬 장소가 다시 겹칠 수 있음, 재검사 루프 필요.
- 입력 범위: `MealMedicationRuleDetail.intervalMinutes`에 검증 없음(null·음수 가능). 이번 범위는 기존 입력 그대로 계산, 입력 검증 추가는 새 정책이라 제외·기록.
- 자정: `LocalTime.plusMinutes` 순환. 23:55 복약 종료 00:05 → 구간 비교 오판 가능. 실제 영향 확인 후 한계 기록.
- 기존 테스트 중 동작 변경 대상: `ScheduleNormalizerTest.movesMedicationOutOfPlaceSlot`(15:10 기대), `TravelPlanAssertions` 267행 "장소 시간대 겹치는 복약의 장소 종료 후 배치" 판정.
- 중복: `PlanPlaceResolver` 92행 candidateId 또는 이름 기준 `usedPlaces`. #179가 보충 식사의 편집 보존 날짜 예약·검증(`validateCompletedMeals`) 추가.

## 테스트 설계

### #181 간격 준수 (ScheduleNormalizerTest, red 먼저)
1. 식후 30분 복약이 관광지 안에 들어가도 식사 종료 + 30분 유지 (현재 red: 장소 종료로 이동)
2. 식전 간격: 식사 시작 − 간격 유지
3. 식중: 식사 시작 유지 (기존 green 유지)
4. 고정 시각(REGARDLESS / 비식사 기준) 유지
5. 간격 0, null
6. 동행인 2명 다른 규칙 → 원본 규칙별 기대 시각 각각 존재. 같은 시각 병합 시 설명 둘 다 포함
7. 반복 적용 동일 결과 (기존 테스트 확장)
8. PlanService 경유 생성·편집·재구성 공통 경로 1건씩 (PlanPlaceValidationTest / PlanServiceTest)

### #182 겹침 회피 (#181 병합 후 구현)
경계: 겹침 = med.start < place.end && place.start < med.end
1. 시작점 내부 / 꼬리만 / 복약이 장소 포함 / 장소가 복약 포함 / 동일 시작 / 양쪽 맞닿음(겹침 아님)
2. 연속 장소: 미룸이 뒤 장소 전부에 전파, 체류·이동 불변
3. 미룸으로 저녁 이동 → 저녁 연동 복약 재계산
4. 원래 창 안 식사가 창 밖으로 → INVALID_AI_PLACE
5. 자정 넘김 → INVALID_AI_PLACE
6. 3회 내 해소 불가 → INVALID_AI_PLACE, 부분 저장 없음(서비스 경계)
7. 식사·TRANSPORTATION·장소 없는 슬롯과 겹침은 무시, 카페·공원은 대상
8. 다중 동행인, 편집 대상 밖 날짜 불변, 멱등

### #183 중복 방지 (독립)
같은 날 / 날짜 간 / 편집 보존 날짜 vs 새 슬롯 / 재선택·보충·원본 복원 후 / 다른 candidateId 같은 장소 / 같은 이름 다른 주소 / 공급자 ID 다름 / 메뉴 중복과 구분 / 기존 저장 중복 미삭제.
기존 보장이면 테스트만 추가, 운영 변경 없음.

## 새 지표 (실측 인계용)
- 규칙별 복약 준수: 원본 healthContexts 규칙마다 기대 시각 계산, 최종 MEDICATION 슬롯 존재 여부. 분모 = 성공 실행 × 날짜 × 규칙
- 전체 구간 겹침 건수: 분모 = 복약 슬롯
- 장소 identity 중복: candidateId·이름+주소 기준. 분모 = 장소 슬롯
- 명시적 거부(INVALID_AI_PLACE 사유별) 포함 성공률
