# Phase 5C Java·외부 API 최적화 후보 판정

작성일: 2026-10-06
코드 기준: `codex/phase5c-stage-metrics`의 `6d23bcd`와 현재 미커밋 단계 계측 변경
범위: Travel 일정 생성 요청 내부의 Java 처리와 OpenAI 이외 외부 API 호출. 운영 코드 변경 없음.

## 결정 요약

1. **첫 실험 후보: AI 응답 이후 누락 메뉴의 영양정보 조회.** 메뉴별 입력 독립성과 실제 지연 기여도를 확인. 중복 제거를 호출 전에 끝내고, 동시성 2 이내의 순서 보존 병합을 로컬 스텁으로 검증할 가치가 있음. 실제 서비스 적용 여부는 전체 사용자 부하와 제공자 제한을 추가 확인한 뒤 결정.
2. **호출 수 절감 후보: Kor2 지역코드 반복 조회.** 병렬화보다 동일 요청 안의 지역코드 재사용을 먼저 검토. 반복 호출 사실은 코드로 확인했지만 시간 기여도는 미측정.
3. **계측 후보: 확정된 장소 사이의 경로 조회.** 구간별 HTTP 조회는 일부 독립 가능. 현재 장소 복구와 시간표 보정은 앞 구간 결과에 의존하므로 즉시 병렬화하지 않음. 기존 좌표 두 건 조회에는 이미 `Mono.zip` 사용.
4. **이번 범위 제외: AI↔Tool 호출, DB snapshot·저장, 식사 후보 보충, 단일 메뉴의 표준명 재조회.** 앞 단계 결과 의존성 또는 원자성 보존 필요.

같은 단계에 표시된 호출이라는 사실만으로 독립성은 성립하지 않음. 모든 입력이 fan-out 전에 확정되고, 각 호출이 서로의 결과를 읽지 않으며, 병합 순서·실패 계약·호출 예산을 보존하는 경우만 병렬화 후보.

## 현재 실행 경로와 의존성

```text
동행인·건강정보 readOnly snapshot
  → OpenAI 및 Tool 왕복: 후보 검색·선택·구조화 응답
  → Java 후보 검증 및 필요 시 AI 재선택·음식점 후보 보충
  → 확정 장소의 누락 이동시간 조회·시간표 정규화
  → 식사 슬롯 보충 및 최종 검증
  → 기존 평가가 없는 메뉴별 영양정보 조회
  → 복약 일정·태그 확정
  → Travel부터 RestaurantDetail까지 write 트랜잭션 저장
```

근거: `TravelFacade.makeTravelOptionsAndRecommend`, `TravelService.loadHealthContexts/saveGeneratedPlan`, `PlanService.makePlanByAi/validatePlaces/finishPlan`, `TravelRecommendHandler.createPlanByAi`, `PlanTourismTool`.

| 후보 | 코드상의 독립성 | 현재 근거 | 위험과 선행 조건 | 판정 |
|---|---|---|---|---|
| 최종 메뉴별 영양 조회 | 서로 다른 확정 메뉴는 입력 독립. `NutritionService`의 구독별 상태는 `Mono.defer` 내부에 생성 | 실제 API 준비 호출 후 건별 약 4초. 동시 2건 두 차례는 약 4.1초에 완료. 4초 지연 로컬 fixture에서 영양 보강 8.02초/전체 14.19초 | 중복 제거와 결과 순서 보존. 요청 간 합산 호출량·초당 제한, 메뉴 매칭 0건, 메뉴별 실패 처리 검증 | **1순위 제한적 실험** |
| Kor2 지역코드 반복 조회 | 같은 여행 지역의 `areaCode2`·시군구 코드 결과는 뒤의 관광지·음식점 검색에서 재사용 가능 | `searchAttractions`, `searchRestaurants`, `searchRestaurantCandidates`가 각각 `getAreaCode()`로 시작. 광역 외 지역은 `getSigunguCode()`도 조회. 구간 시간은 미측정 | 요청 단위 범위·지역키 정확성·API 오류 처리. 전역 캐시와 임의 TTL 도입 금지 | **호출 수 절감 실험** |
| 확정된 경로 구간 조회 | 모든 장소가 확정된 뒤라면 구간별 좌표·이동수단으로 독립 조회 가능 | `fillMissingTravelMinutes`는 현재 구간별 `getRoute().block()`을 순차 실행. 스텁 smoke는 특정 생성 경로에서 Mobility 4회 확인 | 장소 복구 중 이전 장소와 종료 시각이 변함. HTTP 결과 수집과 시간표 반영 분리 필요. Kakao 응답 지연·호출 한도 미측정 | **계측 후 재판정** |
| 경로 양 끝 좌표 조회 | 현재도 두 조회가 독립 | `KakaoMapServiceHandler.getRoute`가 `Mono.zip(routeCoordinates(origin), routeCoordinates(destination))` 사용 | 새 최적화 대상 아님 | **이미 적용** |
| 음식점·관광지 Tool 검색 | AI가 앞 후보를 보고 다음 장소·keyword 선택. 후보 저장소 공유 | `OpenAiClient`의 Tool 호출 및 `PlanTourismTool`의 `PlaceCandidateContext` 변경 | AI↔Tool 순서 변경과 candidateId 유실 위험 | **제외** |
| 단일 음식의 표준명 재조회 | 첫 검색이 비어야 대체 검색 실행 | `NutritionService.evaluateFoodNutrition`의 `flatMap` 조건부 재조회 | 선행 조회 결과 의존 | **제외** |
| DB 조회와 최종 저장 | AI 입력 snapshot과 완성된 AI 결과가 각각 필요 | `TravelTransactionService`의 readOnly/write 분리 | 저장 원자성과 소유권 재검증 보존 | **제외** |

## 1순위: 최종 메뉴 영양 조회

**Seam:** `PlanService.enrichMissingNutritionEvaluations`. 호출자의 Interface는 최종 일정과 건강정보를 받아 메뉴별 평가 결과를 기존 순서대로 돌려주는 것. 외부 영양 API는 `NutritionService`와 `FoodNtrCpntHandler` 뒤의 Adapter. 이 내부 구현만 바꾸면 Facade·AI Tool 호출 계약을 유지 가능.

현재 구현은 `LinkedHashSet`에 대한 `filter(evaluatedMenus::add)`로 중복을 제거하고, 각 메뉴의 `Mono`에 차례로 `blockOptional()`을 호출. `enriched`에도 순차 추가. 이를 그대로 `parallelStream()`으로 바꾸면 공유 Set·List와 결과 순서가 깨질 수 있음. 변경한다면 먼저 중복 메뉴 목록을 확정하고, 최대 동시성 2로 구독한 뒤 원래 순서대로 합치는 방식. AI Tool의 `NutritionEvaluationCollector`는 `ThreadLocal` 기반이므로 이 Tool 단계까지 fan-out하지 않음.

기존 측정: 실제 API의 준비 이후 순차 두 조회 HTTP 시간 합계 8,099.4ms(호출 간 2초 휴지 제외), 동시 두 조회 wall-clock 4,071.4ms와 4,156.9ms. 로컬 4초 지연 fixture의 전체 요청 14,190.5ms 중 영양 보강 8,024.75ms(56.6%). 이는 **병렬 변경 후 전체 응답 시간의 측정값이 아니며**, 두 메뉴가 보강 대상인 경로에 한정된 잠재 이득. 원시값은 `phase5c-real-nutrition-measurements-20261005.json`과 `phase5c-stage-analysis.md`에 보존.

호출 건수는 동일해도 순간 호출 밀도는 증가. 공식 포털은 개발계정 신청 가능 트래픽 10,000, 일일 초과 오류 22, 초당 초과 오류 23을 공개하지만 계정의 실제 승인량·초당 숫자·동시 호출 한도를 공개하지 않음. 단발 동시 2건 성공을 지속 부하 허용 근거로 사용하지 않음. `불고기`는 API 응답 후 현재 `FoodNtrCpntHelper` 매칭 0건이라 `UNAVAILABLE` 가능. 이는 병렬화와 별개의 품질 문제.

**실험 gate:** 동일 fixture의 메뉴 순서·중복 제거·호출 수·15초 조회별 timeout·메뉴별 fallback·영양 태그 동일성 검증. 실패 메뉴 하나가 성공 메뉴를 가리지 않는지 확인. 외부 API 없는 스텁에서 순차/병렬 전체 요청 p50·p95와 오류율을 동일 부하로 비교. 현재 `FoodNtrCpntHelper`는 응답의 `header.resultCode`를 검사하지 않으므로 제한 초과 코드 23, HTTP 오류, timeout, 정상 응답의 매칭 0건이 일정 성공 및 `UNAVAILABLE` 집계에 섞일 수 있음. 운영 오류율 판정 전에는 이 네 경우를 구분하는 저카디널리티 계측과 스텁 검증 필요. `nutrition_lookup` Timer도 구독 시작부터 메뉴별 조회 완료까지 측정하여 현재 `blockOptional()` 구간과 같은 의미를 유지. 실제 제공자에는 한도 탐색용 부하를 보내지 않음. 운영 적용 전에는 여러 사용자가 합쳐 만든 초당 요청량과 제한 초과를 볼 수 있는 제한 정책 필요.

## 2순위: Kor2 지역코드 조회 재사용

`Kor2ServiceHandler`의 세 검색 Interface 모두 `getAreaCode()`에서 출발. 광역 외 지역은 `getSigunguCode()`가 이어지고, 그 결과로 관광지·음식점 엔드포인트를 호출. 한 검색 내부에서는 이 순서가 필수라 병렬화 후보가 아님. 여러 검색이 같은 여행 지역을 대상으로 반복될 때만 동일 지역코드 응답 재사용 후보.

`PlanTourismTool`에는 같은 지역 관광지 **결과**를 재사용하는 필드가 이미 있음. 비광역 지역에서 조회한 시군구 코드는 실제 `/areaBasedList2`와 `/searchKeyword2`의 `sigunguCode` 파라미터로 전달되므로 조회를 무조건 제거하면 검색 범위가 바뀜. 지역코드까지 캐시한다면 중복 인터페이스나 전역 mutable 저장소를 새로 만들기 전에 요청 단위에서 호출 수를 검증. 우선 스텁의 `areaCode2` 요청 수·응답 지연을 메뉴 키워드 수에 따라 기록. 절감량이 작으면 변경하지 않음. `Mono.cache(Duration)`를 고려할 경우 오류·빈 결과도 TTL 동안 재생된다는 공식 계약을 확인하고 실패 캐싱 정책을 정해야 함.

## 3순위: 경로 구간 조회 계측

`PlanService.fillMissingTravelMinutes`는 이전 확정 장소와 현재 장소로 경로를 조회. 장소 복구 시 `recalculateSlot`이 이전 일정의 종료 시각과 이동시간으로 다음 시작·종료 시각을 바꿈. 따라서 현재 순회 전체를 병렬화하면 안 됨. 먼저 최종 장소 목록이 바뀌지 않는 구간에서만 독립적인 경로 입력을 만들 수 있는지 검증하고, HTTP 조회와 순서대로의 시간표 반영을 분리할 가치가 있는지 측정. 실패 시 `fillScheduleTravelMinutes`는 기존 슬롯을 유지하지만 `recalculateSlot`은 장소 변경 뒤 경로가 없으면 예외를 발생시킴. 조회 분리 전에 두 실패 계약을 각각 테스트로 고정.

`KakaoMapServiceHandler.getRoute` 내부의 출발지·도착지 좌표 조회는 이미 `Mono.zip`을 사용. 여기에 중첩 병렬화를 더하는 것은 측정 전에는 근거 부족. `TRANSIT`·`CAR`별 경로 조회 건수와 각 호출 duration, 총 route wall-clock, `finish_plan` 중 비중을 먼저 확인. 요청값·장소명 같은 고카디널리티 메트릭 태그는 사용하지 않음. [카카오 공식 쿼터](https://developers.kakao.com/docs/ko/getting-started/quota)의 무료 일간 대중교통 경로 조회 1,000건과 자동차 길찾기 10,000건은 각각 호출 예산 확인 대상이며, 초당 병렬 허용량으로 해석하지 않음.

## 실행 순서와 중단 조건

1. 후보별 현행 호출 건수와 stage wall-clock 확인. 없는 수치는 추정으로 분리.
2. 영양 조회의 독립 메뉴만 대상으로 호출 전 중복 제거 → 상한 2 fan-out → 순서 보존 병합을 스텁에서 비교. 실제 API 호출은 별도 소수 표본만 사용.
3. 동일 fixture의 결과·태그·에러·호출 수·전체 요청 시간 비교. 개선이 재현되지 않거나 오류가 늘면 운영 적용 중단.
4. Kor2 지역코드 중복 호출과 route 지연을 독립 계측. 각 후보를 별도 결정으로 유지.

이번 문서는 구현 승인 또는 성능 개선 완료 판정이 아님. 현재 5C 작업 트리의 단계 계측·실측 결과를 기준으로 다음 변경의 우선순위를 정한 기록.

## 공식 자료

- [Spring Framework WebClient 동기 사용](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-synchronous.html): 독립 조회를 각각 `block()`하기보다 결합 후 한 번 기다리는 예시. 현재 Spring MVC 경로의 적용 후보.
- [Project Reactor `Flux.flatMapSequential`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#flatMapSequential(java.util.function.Function,int)): 동시 구독·원래 순서대로 결과 방출의 조합.
- [Project Reactor `Mono.zip`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#zip(reactor.core.publisher.Mono,reactor.core.publisher.Mono)): 독립 소스의 결과 결합.
- [Project Reactor `Mono.cache(Duration)`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#cache(java.time.Duration)): 결과·오류·빈 완료의 TTL 재사용. 지역코드 캐싱 시 장애 재생 주의.
- [공공데이터포털 식품영양성분DB정보](https://www.data.go.kr/data/15127578/openapi.do): 개발계정 신청 가능 트래픽과 일일·초당 초과 오류 설명. 계정별 초당 수치는 없음.
- [공공데이터포털 국문 관광정보 서비스](https://www.data.go.kr/data/15101578/openapi.do): 개발계정 신청 가능 트래픽 1,000과 일일·초당 초과 오류 설명.
- [카카오디벨로퍼스 쿼터](https://developers.kakao.com/docs/ko/getting-started/quota): 앱의 API별 일간 무료 쿼터와 사용량 확인 위치.
