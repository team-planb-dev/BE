# 식사 후보 사용 가능성 계약

> 상태: 과거 구현 기록
> 이 문서의 후보 판정은 작성 당시 구조를 설명한다. 현재 식사 요구·면제·최종 거부 계약은
> `docs/ai/meal-slot-policy.md`, `MealSlotPolicy`와 `PlanService`를 기준으로 확인한다.

조사 기준일: 2026-09-20  
기준 커밋: `98c161c`  
범위: `TravelRecommendHandler`, `MissingSlotCompleter`, `PlanService`의 식사 후보 판정

## 문제

음식점 검색 후보에는 장소 식별자, 분류, 주소, 좌표만 있다. 식사 슬롯에 필요한 대표메뉴는
후보의 `candidateId`로 음식점 상세 조회를 한 뒤 `firstmenu`에서 별도로 확정한다
(`PlaceCandidateContext.java:31-49`, `MissingSlotCompleter.java:391-427`).

기존 `TravelRecommendHandler`와 `PlanService`는 응답에서 사용하지 않은 음식점 장소명만 세었다.
반면 실제 보정기는 다음 후보를 제외했다.

1. 이미 사용한 장소명
2. 음식점 상세 조회에 실패한 후보
3. 비어 있지 않은 `firstmenu`가 없는 후보
4. 대표메뉴가 이미 사용된 후보

따라서 검증기는 보정할 수 있다고 판단했지만 실제 보정기는 후보를 사용할 수 없는 계약 불일치가 있었다.
그 결과 AI 교정이 생략되거나 최종 일정이 `INVALID_AI_PLACE`로 잘못 거부될 수 있었다.

## 당시 확정 계약

식사 후보는 다음 조건을 모두 만족할 때만 사용 가능하다.

- 정규화한 장소명이 현재 일정과 외부 예약 집합에 없다.
- 음식점 상세 조회가 성공한다.
- 상세 응답에 비어 있지 않은 `firstmenu`가 있다.
- 정규화한 대표메뉴가 현재 일정과 외부 예약 집합에 없다.
- 후보를 세면 해당 장소명과 대표메뉴를 즉시 예약한다.

마지막 조건은 같은 장소명이나 같은 대표메뉴를 가진 여러 후보를 여러 슬롯 분량으로 세지 않기 위해 필요하다.

## 당시 source of truth

`MissingSlotCompleter`가 식사 후보 사용 가능성의 source of truth다.

- `complete(...)`는 응답과 호출부의 사용 장소·메뉴 집합을 합친다
  (`MissingSlotCompleter.java:60-100`).
- `fillableMealCount(...)`는 실제 슬롯 생성과 같은 판정으로 사용 가능한 후보만 센다
  (`MissingSlotCompleter.java:103-151`).
- `mealSlot(...)`과 후보 수 계산은 같은 `mealCandidate(...)` selector를 사용한다
  (`MissingSlotCompleter.java:329-389`).
- 상세 조회 예외와 불완전 응답은 사용 불가능 후보로 처리한다
  (`MissingSlotCompleter.java:391-427`).

새 policy나 별도 module은 추가하지 않았다. 이미 상세 조회와 중복 판정을 소유한 module의 interface만 확장했다.

## 호출 흐름

생성 응답 검증은 누락 식사 수에서 실제 사용 가능한 후보 수만큼만 제외한다.
대표메뉴를 확인할 수 없거나 메뉴가 중복된 후보는 AI correction 면제 수에 포함하지 않는다
(`TravelRecommendHandler.java:221-280`).

최종 일정 검증은 먼저 실제 누락 식사를 계산한다. 누락이 있을 때만 상세 조회를 수행한다.
사용 가능한 후보가 없으면 기존 정책대로 누락을 허용하고, 사용 가능한 후보가 남았으면
기존 `INVALID_AI_PLACE` 검증을 유지한다 (`PlanService.java:673-705`).

편집과 부분 재구성도 최종적으로 `finishPlan(...)`을 통과하므로 같은 최종 검증을 사용한다
(`PlanService.java:495-555`).

## 회귀 테스트

`TravelRecommendHandlerContractTest`가 다음 계약을 검증한다.

- 사용 가능한 후보 수만큼만 correction 사유 제외
- 대표메뉴 미확인 후보 제외
- 같은 대표메뉴를 가진 후보 중복 계산 방지

관련 테스트는 `TravelRecommendHandlerContractTest.java:145-243`에 있다.

`PlanPlaceValidationTest`는 남은 음식점 후보의 대표메뉴가 이미 사용된 경우
식사 누락을 이유로 최종 일정을 거부하지 않는지 검증한다
(`PlanPlaceValidationTest.java:2782-2861`).

기존 `MissingSlotCompleterTest`는 실제 대표메뉴로 슬롯을 만들고, 사용한 메뉴를 건너뛰며,
모든 후보 메뉴가 중복이면 슬롯을 만들지 않는 selector 동작을 계속 검증한다.

## 남은 위험

사용 가능 후보 수를 판정할 때 음식점 상세 조회가 필요하다. 생성 응답 검증과 최종 보정에서
같은 후보를 다시 조회할 수 있다. 이번 이슈에서는 요청 단위 cache를 추가하지 않았다.
실제 호출량이나 지연 문제가 측정되면 `PlaceCandidateContext`에 성공과 실패 결과를 함께 저장하는
요청 단위 cache를 검토한다. singleton인 `MissingSlotCompleter`에는 요청 상태를 저장하지 않는다.

Prompt, DTO, 식사 슬롯 수 정책, 첫날 아침과 마지막 날 저녁 면제 정책은 변경하지 않았다.
