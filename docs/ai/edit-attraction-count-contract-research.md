# AI 일정 편집 관광 장소 개수 계약 조사

조사 기준일: 2026-09-21  
기준 저장소: `/Users/wooju-kang/Desktop/planB`  
기준 브랜치: `71-bug-align-less-walking-plan-edits-with-attraction-count-validation`

## 결론

최소 안전 계약은 **생성의 정확한 개수 정책은 유지하고, 명시적인 밀도 감소 편집 대상으로 분류된 날짜에만 허용 범위를 2~3개로 넓히는 것**이다.

| 경로 | MINIMAL 여행자 포함 | 그 외 | 판정 |
| --- | ---: | ---: | --- |
| 신규 생성 | 2 | 3 | 정확히 일치 |
| 일반 편집 | 2 | 3 | 정확히 일치 |
| 밀도 감소 편집 대상 날짜 | 2 | 2~3 | 범위 안이면 허용 |
| 밀도 감소 대상이 아닌 날짜 | 2 | 3 | 정확히 일치 |

동행인이 없어 현재 `expectedCount`가 0을 반환하는 경우에는 기존처럼 관광 장소 개수 규칙을 적용하지 않는다.

이 계약에서 “2~3개”는 모든 편집 결과에 대한 전역 완화가 아니다. 별도의 구조화된 편집 의도에서 밀도 감소 대상으로 확인된 날짜에만 적용한다. 실제 편집 응답의 `changes` 문장이나 결과 개수 자체로 허용 여부를 역추론하면 생성 모델이 자신의 검증 예외를 스스로 승인하게 되므로 사용하지 않는다.

## 현재 불일치

생성 정책은 `TouristPlaceCountPolicy.expectedCount`에서 MINIMAL 2개, 그 외 3개를 반환한다. `trimExcess`도 같은 정확한 목표를 사용한다.

- `src/main/java/com/planb/domain/travel/policy/TouristPlaceCountPolicy.java:22-47`
- `src/main/java/com/planb/domain/travel/policy/TouristPlaceCountPolicy.java:60-84`

편집 프롬프트는 명시적인 밀도 감소 요청이면 관광 장소를 제거하거나 다른 휴식 슬롯으로 바꾸어 개수를 줄이라고 지시한다.

- `src/main/java/com/planb/ai/prompt/EditPlanPrompt.java:175-189`

반면 공통 후처리는 먼저 `MissingSlotCompleter`로 부족한 관광 장소를 생성 기준까지 다시 채우고, 생성 기준을 넘으면 자른 뒤, 생성 기준과 정확히 같아야 통과시킨다.

- 보정 호출: `src/main/java/com/planb/domain/travel/service/PlanService.java:827-847`
- 부족분 계산: `src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:321-369`
- 최종 정확 일치 검증: `src/main/java/com/planb/domain/travel/service/PlanService.java:950-980`

따라서 검증만 `2 또는 3`으로 바꾸면 부족분 보정이 2개 결과를 다시 3개로 만들 수 있다. 반대로 보정만 멈추면 현재 최종 검증이 2개를 거부한다. **최솟값, 최댓값을 한 정책에서 계산하고 보정·초과 제거·검증이 모두 같은 값을 사용해야 한다.**

## 공식 문서가 뒷받침하는 설계 원칙

Spring AI의 `StructuredOutputConverter`는 프롬프트에 형식 지시를 붙이고 모델 텍스트를 타입으로 변환하지만, 공식 문서는 이를 best effort로 설명하며 스키마 검증과 함께 사용하라고 명시한다. `BeanOutputConverter`는 Java 타입에서 JSON Schema를 만들고 결과를 객체로 역직렬화한다. 즉, 구조화 DTO는 자유 텍스트보다 안전한 전달 형식이지만 도메인 규칙의 최종 판정 자체는 아니다.

- [Spring AI Output Converters](https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html)
- [Spring AI `BeanOutputConverter` API](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/converter/BeanOutputConverter.html)

OpenAI Structured Outputs는 JSON Schema 형태 일치를 보장하지만, 공식 제한 사항은 JSON 객체 안의 값 자체가 잘못될 수 있다고 설명한다. 따라서 `densityReductionDayNumbers`가 올바른 타입으로 내려오는 것과, 그 날짜가 실제 여행 날짜이며 그 결과가 허용 개수 범위에 드는지는 별개의 검증이어야 한다.

- [OpenAI Structured Outputs 소개와 제한 사항](https://openai.com/index/introducing-structured-outputs-in-the-api/#limitations-and-restrictions)

Spring AI Tool Calling 문서에서도 모델은 호출 여부와 인자를 제안하고 애플리케이션의 tool manager가 실제 실행을 담당한다. 이 저장소도 같은 책임 분리를 유지해, AI는 편집 의도를 구조화해 제안하고 Java가 허용 범위와 최종 일정 불변식을 집행하는 편이 맞다.

- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html#_the_tool_calling_loop)

OpenAI의 agent guardrail 지침도 최종 모델 출력에 별도 검증을 실행하는 output guardrail을 명시한다. 현재 `PlanService`의 최종 검증은 제거할 대상이 아니라 이 역할을 담당하는 seam이다.

- [OpenAI Agents SDK Guardrails](https://openai.github.io/openai-agents-js/guides/guardrails/#output-guardrails)

## 기존 구조를 이용한 의도 전달

이미 `PlanService.makeEditPlanByAi`는 일정 생성 전에 `classifyEditScope`를 호출하고 그 결과를 `PlanEditScope`로 받는다.

- 선행 분류 호출: `src/main/java/com/planb/domain/travel/service/PlanService.java:162-175`
- 현재 구조화 결과: `src/main/java/com/planb/ai/dto/response/PlanEditScope.java:5-9`
- 현재 분류 프롬프트: `src/main/java/com/planb/ai/prompt/PlanEditScopePrompt.java:9-27`

가장 작은 변경은 새 분류 호출이나 문자열 키워드 판별을 추가하지 않고, 기존 `PlanEditScope`에 `densityReductionDayNumbers`를 추가하는 것이다.

권장 의미는 다음과 같다.

- 사용자가 특정 날짜를 덜 걷거나 더 여유롭게 바꾸라고 명시하면 해당 일차만 넣는다.
- 날짜를 특정하지 않은 여행 전체 밀도 감소 요청이면 현재 일정의 모든 일차를 넣는다.
- 장소 교체, 시간 조정, 식사 변경, 관광지 증가 요청은 빈 목록이다.
- 모호한 요청은 빈 목록으로 두어 기존 정확 개수 정책을 유지한다.
- Java의 `PlanEditValidator`는 현재 `rebuildDayNumbers`처럼 null과 존재하지 않는 일차를 거부하고 불변 `Set`으로 확정한다.

`changes`는 사람이 읽는 설명이며 현재 검증도 비어 있는지만 확인한다(`TravelRecommendHandler.java:416-451`). 밀도 감소 권한을 `changes` 문장에서 찾거나, 편집 결과가 2개라는 이유로 감소 요청이었다고 간주하면 안 된다.

## 하나의 정책이 제공해야 하는 값

`TouristPlaceCountPolicy`가 날짜별 `minimumCount`와 `maximumCount`를 계산하도록 두면 기존 공통 seam을 유지할 수 있다.

- 생성 또는 일반 편집: `minimumCount == maximumCount == expectedCount`
- 밀도 감소 편집 대상: `minimumCount == min(2, expectedCount)`, `maximumCount == expectedCount`
- 동행인 없음: 기존과 같이 규칙 미적용

세 소비자는 다음처럼 같은 계약을 사용해야 한다.

1. `MissingSlotCompleter`: 실제 개수가 `minimumCount`보다 작을 때만 후보를 채운다.
2. `trimExcess`: 실제 개수가 `maximumCount`보다 클 때만 초과분을 제거한다.
3. `validateTouristPlaceCounts`: 실제 개수가 `[minimumCount, maximumCount]` 안인지 검사한다.

이렇게 하면 MODERATE/ACTIVE 편집에서 AI가 2개를 반환해도 다시 3개로 채우지 않고, 1개는 최소 2개까지 보정하거나 후보가 없으면 최종 검증에서 거부한다. 4개 이상은 기존처럼 최대 3개로 잘린다. MINIMAL 여행자는 완화 후에도 2개가 유지된다.

별도의 interface나 adapter는 필요하지 않다. 이미 개수 규칙의 module인 `TouristPlaceCountPolicy`와 의도 분류의 interface인 `PlanEditScope`가 있으며, 이 두 곳을 깊게 만드는 편이 호출부마다 조건문을 복제하는 것보다 locality가 높다.

## 프롬프트 계약

생성 프롬프트는 현재 계약을 유지한다. 현재 `TravelPlanPrompt`는 MINIMAL 2개, 그 외 3개를 요구하고 `LESS_TOURISM`도 개수를 줄이지 않도록 명시한다.

- `src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:79-101`

편집 프롬프트의 STEP 4에는 다음 의미를 명시하는 것이 현재 Java 계약과 맞는다.

- 기본 관광 장소 수는 생성 정책과 같은 하루 3개이며, MINIMAL 여행자가 있으면 2개다.
- 명시적인 걷기 또는 일정 밀도 감소 요청의 대상 날짜에서만 2개까지 줄일 수 있다.
- 감소 요청이어도 더 짧은 이동 동선이나 휴식 추가로 요청을 충족했다면 3개를 유지할 수 있다.
- 감소 요청 대상이 아닌 날짜는 기존 관광 장소 수와 생성 기준을 유지한다.
- `MUST_HAVE`는 기존 정책처럼 관광 장소 개수에 포함하며 우선 보존한다.

`PlanEditScopePrompt`에도 같은 예시를 넣어 생성 프롬프트와 분류 프롬프트가 서로 다른 의미를 만들지 않게 해야 한다. 다만 프롬프트 문구는 의도 분류의 정확도를 높이는 수단이고, 실제 2~3개 범위 집행은 Java 정책이 담당한다.

## 권장 회귀 검증 seam

구현 단계의 가장 작은 유효 테스트 표면은 두 곳이다.

1. `TouristPlaceCountPolicy`의 public interface에서 기본 범위와 밀도 감소 범위를 고정한다. MODERATE/ACTIVE는 기본 `[3,3]`, 감소 `[2,3]`; MINIMAL은 둘 다 `[2,2]`여야 한다.
2. `PlanService.makeEditPlanByAi`에서 같은 2개 결과가 `densityReductionDayNumbers`에 포함된 날짜면 통과하고, 포함되지 않은 날짜면 기존처럼 실패하는지 검증한다. 이 테스트는 `MissingSlotCompleter`가 2개를 다시 3개로 채우지 않는 것도 함께 증명해야 한다.

분류 프롬프트의 자연어 의미 정확도는 외부 모델 호출을 포함하므로 단위 테스트로 고정할 수 없다. 기존 `PlanEditScope`를 mock한 서비스 테스트로 애플리케이션 계약을 고정하고, 실제 “덜 걷고 싶어요” 분류는 기존 external test 계층에서 별도로 확인하는 편이 현재 테스트 구조와 맞다.

## 피해야 할 변경

- 모든 편집에서 2~3개를 허용하지 않는다. 관계없는 수정에서 관광지 누락이 통과한다.
- `EditPlanAiResponse.changes` 텍스트를 파싱하지 않는다.
- 편집 결과가 2개라는 사실 자체를 감소 의도의 증거로 사용하지 않는다.
- 최종 Java 검증을 제거하지 않는다. Structured Outputs는 값의 의미 정확성을 보장하지 않는다.
- 검증만 완화하지 않는다. `MissingSlotCompleter`와 `trimExcess`도 같은 최소·최대 계약을 사용해야 한다.
