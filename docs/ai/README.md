# AI 문서 책임 안내

상태: 현재 문서 구조의 기준
마지막 검증: 2026-09-26

`docs/ai`는 AI 일정 생성과 외부 API 조사 결과를 보존하는 문서 디렉터리다.
이 디렉터리의 Markdown은 production에서 로드되지 않으며 실행 정책의 source of truth가 아니다.

## 책임 기준

| 위치 | 책임 |
|------|------|
| `src/main/java/com/planb/domain/travel/policy`와 validation 코드 | 식사·관광지 개수·장소 유형·태그·시간 등 최종 일정 invariant 결정 |
| `src/main/java/com/planb/ai/prompt` | 사용자 의도 해석, Tool 사용, 후보 선택과 구조화 응답 형식 안내 |
| `src/test` | Java policy와 Prompt interface의 실행 가능한 회귀 계약 |
| `docs/ai` | 조사 근거, 결정 이유, 적용 당시 구조와 남은 위험 기록 |

Prompt는 모델이 올바른 후보를 제안하도록 Java 정책 일부를 설명할 수 있다. 그러나 Prompt와
Java가 다르면 Java policy와 validation 결과가 최종 계약이다. 문서는 해당 Java 클래스와
테스트를 가리킬 뿐 독립적으로 정책을 정의하지 않는다.

## 문서 분류

| 문서 | 분류 | 사용 방법 |
|------|------|-----------|
| `HANDOFF.md` | 과거 인수인계 스냅샷 | 작성 당시 작업 상태 확인. 현재 계약 판단에 사용하지 않음 |
| `attraction-candidate-quality-research.md` | 조사 스냅샷 | 관광지 분류 필터의 근거와 기각 대안 확인 |
| `chat-stomp-failure-feedback.md` | 구현 기록 | STOMP 실패 피드백을 적용한 당시 계약 확인 |
| `edit-attraction-count-contract-research.md` | 조사 스냅샷 | 편집 관광지 개수 정책을 도출한 근거 확인 |
| `final-meal-nutrition-enrichment-research.md` | 조사 스냅샷 | 최종 식사 영양 보정의 대안과 적용 지점 확인 |
| `food-name-resolution.md` | 조사 스냅샷 | 메뉴명과 표준 품목명 사이의 문제 및 대안 확인 |
| `kor2-coordinate-validation-research.md` | 조사 스냅샷 | KorService2 좌표 검증 범위의 근거 확인 |
| `meal-candidate-fillability-contract.md` | 과거 구현 기록 | 이전 후보 사용 가능성 계약 확인. 현재 식사 요구 계약으로 사용하지 않음 |
| `meal-slot-fix-impact.md` | 조사 스냅샷 | 식사 슬롯 수정 대안의 당시 영향 분석 확인 |
| `meal-slot-policy.md` | 현재 동작 설명 | `MealSlotPolicy`, `MissingSlotCompleter`, `PlanService`의 현재 식사 계약 안내 |
| `mfds-nutrition-api-verification.md` | 외부 API 조사 | 식약처 02·03 오퍼레이션 실측 결과 확인 |
| `mfds-nutrition-api.md` | 외부 API 조사 | 식약처 명세와 기준량 의미 확인 |
| `nutrition-candidate-matching-policy.md` | 조사 스냅샷 | 음식명 후보 매칭 정책의 근거와 안전 경계 확인 |
| `nutrition-serving-evaluation-policy.md` | 조사 스냅샷 | 영양 기준량 평가 정책의 근거와 미확인 사항 확인 |
| `place-validation.md` | 구현 기록 | 장소 검증과 재선택 흐름을 적용한 당시 구조 확인 |
| `restaurant-candidate-acquisition-research.md` | 조사 스냅샷 | 음식점 후보 보충 조회 정책의 근거 확인 |
| `travel-minutes-adjacency-recomputation.md` | 구현 기록 | 일정 변경 후 인접 이동시간 재계산 결정과 적용 결과 확인 |

## 갱신 규칙

- 현재 동작 설명은 담당 Java 클래스와 회귀 테스트를 함께 링크한다.
- 조사 문서는 조사일, 기준 브랜치 또는 기준 커밋을 유지한다.
- 구현 뒤 조사 내용이 낡아도 연구 기록을 현재 계약처럼 고치지 않는다. 대신 상태를 과거 기록으로
  표시하고 현재 계약 문서로 연결한다.
- 사용자 동작을 바꾸는 새 정책은 Java policy와 테스트를 먼저 변경하고 Prompt를 정렬한다.
- Prompt 문구만 바꿔 Java validation을 우회하지 않는다.
- 세션 전용 커밋 명령과 진행 상태는 현재 계약 문서에 추가하지 않는다.
