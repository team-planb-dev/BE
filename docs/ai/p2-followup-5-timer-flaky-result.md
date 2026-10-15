# P2 후속5 — 과거 PlanServiceTest 영양 Timer 간헐 실패 진단·수정 결과

작성일: 2026-10-15
담당: Claude
기준: dev `d542c426baefb8b66af69d5bd5ec938f5c83a900`, main `a6271bf8bce26436b250b426f50fa30bd61a7601`
Issue·PR: #202 / 이 문서를 포함한 dev PR
범위: 무료 단위 테스트·코드 수정. 실제 AI·외부 API 호출 없음

## 1. 결론

- 과거 간헐 실패 테스트 특정: `PlanServiceTest` "영양 조회 단계 시간은 구독부터 메뉴별 완료까지". 1회차(2026-10-12 01:57 UTC)는 보존 로그로 `MeterNotFoundException at PlanServiceTest.java:2070` 확인, 2회차(15:41 UTC)는 같은 테스트 실패만 확인하고 예외 본문은 미보존
- 메커니즘: `lookupNutrition`의 Timer 기록이 `doFinally` 안에 있었음. Reactor `doFinally`는 종료 신호를 하류에 넘긴 뒤 실행되므로, 조회가 호출 스레드가 아닌 스레드에서 끝나면 `collectList().block()`이 먼저 반환되고 Timer 등록·기록이 그 뒤에 일어날 수 있음
- 결정적 재현(§4의 500ms 조건): 다른 스레드의 측정 종료를 호출 반환까지 붙잡는 테스트에서 수정 전 5/5 실패(`expected: <1> but was: <0>`), 수정 후 3/3 통과
- 수정: 성공·실패·취소를 하류 전달 전 hook(`doOnSuccess`·`doOnError`·`doOnCancel`)에서 한 번만 기록
- 과거 실패의 실제 스레드 순서는 기록이 없어 직접 관측 불가. 1회차의 예외 유형·실패 위치와 테스트 조건이 재현 메커니즘과 일치하는 정황 근거, 2회차는 같은 테스트 실패라는 사실만 일치. 현재 CI 성공은 이 판단의 근거로 쓰지 않음

## 2. 과거 실패 특정

| 시각(UTC) | 실행 위치 | 범위 | 결과 | 근거 |
|---|---|---|---|---|
| 2026-10-12 01:57 | `/private/tmp/planb-181`(#181 로컬 작업 트리, 미커밋 변경 포함) | `PlanServiceTest` 등 4클래스 123건 | 해당 테스트 실패, `MeterNotFoundException at PlanServiceTest.java:2070`. meter 이름·timer 유형·`flow=create`는 찾았으나 나머지 tag 조합 없음 | 보존 로그 `/private/tmp/claude-181-green.log`, Claude 세션 기록의 XML 실패 메시지 |
| 2026-10-12 15:41 | `/private/tmp/planb-p24d-time-contract` | travel service·global 단위 440건 | 같은 테스트 1건 실패 | Claude 세션 기록의 Gradle 출력. 예외 본문은 출력에 없음 |

- 두 실행의 XML 원본은 이후 빌드로 덮어써져 보존되지 않음. 정확한 commit은 미특정(로컬 미커밋 작업 트리)
- 이후 `p2-4d-time-contract-result.md` §13-2에 "이전부터 간헐 실패, 단독 재실행 3/3 통과"로만 기록되었던 사례

## 3. 메커니즘

**공식 계약**(reactor-core release 문서, 표시 버전 3.8.7: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html). 프로젝트 실제 해석 버전은 `dependencyInsight`(testRuntimeClasspath) 기준 3.8.2이며, 같은 문구가 3.8.2 sources jar의 `Mono.java:2651-2661`(doFinally)·`:2738-2750`(doOnSuccess)에 있음(독립 리뷰 확인)
- `doFinally`: "executed after the signal has been passed downstream". 문서는 Mono가 callback 전에 완료되므로 `block()` 직후에는 효과가 보이지 않을 수 있다고 명시
- `doOnSuccess`: "The Consumer is executed before propagating either onNext or onComplete downstream"

**프로젝트 사실**(수정 전)
- `PlanService.lookupNutrition`: `doFinally`에서 `stageTimer(...)` 등록과 `sample.stop`
- 호출부: `Flux.flatMapSequential(..., 2).collectList().block()`
- 테스트: `nutritionService` mock이 `Mono.delay(120ms)`를 반환해 완료 신호가 Reactor parallel 스레드에서 발생. 테스트는 `makePlanByAi` 반환 직후 `meterRegistry.get(...).timer()` 조회
- 따라서 parallel 스레드가 하류 완료(`block()` 해제) 뒤 `doFinally`의 Timer 등록 전에 지연되면 조회 시점에 meter가 없어 `MeterNotFoundException`

**보조 관측**(진단용 임시 테스트, 삭제, 출력 파일 미보존·세션 기록에만 존재): `flatMapSequential` + `Mono.delay` + `doFinally`를 3,000회씩 반복하면 `doFinally`가 `block()` 반환 뒤에 실행된 경우가 메뉴1개 3회, 2개 3회, 3개 1회. 신호 유형은 모두 `onComplete`. 드물지만 실제로 발생하는 순서라는 보조 정황

## 4. 결정적 재현과 회귀 테스트

`PlanServiceTest.makePlanByAiRecordsLookupTimerBeforeReturning` ("다른 스레드에서 끝난 영양 조회도 일정 생성 반환 전에 단계 시간 기록")

- 조회 완료 신호를 별도 플랫폼 스레드에서 발생(`Mono.create` + `Thread.ofPlatform().start`)
- registry clock이 호출 스레드가 아닌 스레드의 `monotonicTime()`을 호출 반환 신호까지 붙잡음(상한 500ms). 반환 뒤 기록하는 구현은 호출 스레드가 `block()` 해제 후 500ms 안에 기록 수를 읽는 한 반환 시점에 기록 0건(GC·CI 부하로 500ms를 넘기면 RED가 통과로 보일 수 있음). 반환 전에 기록하는 구현은 이 조건과 무관하게 통과. 상한이 없으면 수정 코드가 교착하므로 상한은 필수
- 반환 직후 Timer 기록 수를 값으로 저장하고 신호 해제. 기록 1건을 단언
- 반환 전에 기록하는 구현은 신호가 오지 않아 상한 뒤 진행. 이 테스트 실행 시간 약 0.5초

| 단계 | 결과 | 증거 |
|---|---|---|
| RED(수정 전) | 5/5 실패, `expected: <1> but was: <0>` | `red-gated-1..5.log`(XML은 마지막 1회) |
| GREEN(수정 후) | 3/3 통과 | `green-gated-1..3.log` |
| `PlanServiceTest` 전체 | 38건 실패0 | `green-planservicetest.xml` |

진단 중 시행착오: registry의 `newTimer`를 붙잡는 방식은 registry 등록 lock을 쥔 채 대기해 호출 스레드의 다른 Timer 등록까지 멈춤. `Mono.delay`의 parallel 스레드를 붙잡는 방식은 다른 작업과 스레드를 공유. 두 방식 모두 RED가 나지 않아 폐기. Timer 객체를 붙잡고 해제 뒤 `count()`를 읽은 첫 판은 해제 뒤 기록을 반영해 통과로 보였음. 반환 시점의 값을 저장하도록 수정

## 5. 수정

`PlanService.lookupNutrition`
- `doFinally` + 결과 참조 변수 → `doOnSuccess`·`doOnError`·`doOnCancel` + `AtomicBoolean` 1회 기록
- 기록 의미 유지: 결과 있음·빈 결과는 `success`, 조회 예외·동기 예외는 `failure`, 다른 메뉴 실패로 인한 취소는 `cancelled`. 성공 뒤 취소가 와도 중복 기록 없음
- 측정 구간: 구독부터 결과를 하류로 넘기기 직전까지. 이전에는 하류 처리 시간 일부가 포함될 수 있었음
- 하지 않은 것: sleep 추가, 테스트 skip, 단언 완화, 재실행 통과 처리, 영양 조회 동시성·순서·결과 변경

## 6. 검증

| 항목 | 결과 |
|---|---|
| 결정적 RED→GREEN | 5/5 실패 → 3/3 통과 |
| 기존 영양 Timer 테스트(성공·실패·취소·구간 120ms) | 통과 |
| 전체 무료 회귀 | 144클래스 1,112건, 실패·오류·skip 0(기존 1,111 + 신규 1). 리뷰 반영(이름·주석) 뒤 재실행도 같은 결과. `full-regression-summary.json`, `full-regression-summary-2.json` |
| 독립 review-loop | 8 → 9/10, 최종 Critical·Important 0. 지적(과거 2회차 실패 근거 과대 표기, 500ms 조건, 형제 조회 경합, 증거 지목, 변수명·주석 위치 등) 모두 반영 |
| 실제 AI·외부 API 호출 | 0 |

## 7. 한계

- 과거 실패의 실제 스레드 순서는 직접 관측 불가. 1회차는 예외 유형·위치·조건 일치, 2회차는 같은 테스트 실패만 일치
- 운영 영향은 계측 기록 시점뿐. 아래 registry 예외 경우를 제외하면 일정 생성 결과·영양값·예외 전파는 변경 없음
- 반환 전 기록 보장은 성공 경로와 실패·취소된 조회 자신에 한정. 형제 조회가 다른 스레드에서 성공 기록 중일 때 다른 조회가 실패하면 예외가 그 성공 기록보다 먼저 전파될 수 있음(기록 1회는 보장). 기존 실패 테스트는 5초 지연이라 해당 없음
- registry가 기록 중 예외를 던지면 이전 `doFinally`는 무시했고 이제는 조회 실패로 전파. 동기 단계 계측 `recordStage`와 같은 정책
- 다른 비동기 계측 지점: `src/main`의 `doFinally` 사용은 이 한 곳뿐(수정 전 확인)

## 8. 보관·보안

- 증거(RED/GREEN XML, 실행 로그, 과거 실패 로그 사본, 회귀 요약): 현재 `/private/tmp/claude-p2-task5-evidence/`에만 존재(재부팅 시 유실 가능). `텍스트 문서/p2-followup-202-20261015/`로 동기화 예정(미완료, Desktop 접근 권한 복구 필요), 완료 시 SHA256 대조 결과 기재. Git 제외
- 진단용 임시 테스트(`DoFinallyOrderScratchTest`)와 `[DEBUG-t5]` 출력은 삭제

