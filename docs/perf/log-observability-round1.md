# Travel AI 로그 출처 계수 — Round 1 (Phase 1-a)

- Issue: #85
- 기준 commit: `918d29c` (`origin/dev`, 코드 변경 전)
- 범위: 로그 계수와 축소만. 지표, Actuator, WebClient 관측은 Phase 1-b에서 다룬다.

## 1. 결정적 계수 (외부 호출 없음)

### `TourismTool` — Tool 메서드 호출 1회당

| 메서드 | 변경 전 INFO | 변경 후 INFO | 외부 응답 전체 출력 |
|---|---|---|---|
| `searchAttractionsByRegion` | 2 | 1 | 있음 → 없음 |
| `searchRestaurantsByLocation` | 2 | 1 | 있음 → 없음 |
| `searchRestaurantCandidatesByRegion` | 2 | 1 | 있음 → 없음 |
| 나머지 메서드 5개 | 1 | 1 | 없음 (변경 없음. 코드 확인값이며 계수 테스트는 추가하지 않음) |

변경 전에는 위 세 메서드가 호출 인자 INFO 1건과 **외부 응답 레코드 전체**를 담은 INFO
1건을 남겼다. 변경 후에는 응답 로그를 DEBUG로 내리고 후보 수만 기록한다.

검증: `TourismToolTest`
- `attractionSearchLogsCallOnlyWithoutResponse`
- `restaurantSearchLogsCallOnlyWithoutResponse`
- `restaurantCandidateSearchLogsCallOnlyWithoutResponse`

### `OpenAiClient` — 실패 경로별

| 경로 | 변경 전 WARN | 변경 후 WARN | 모델 원본 응답 출력 |
|---|---|---|---|
| 파싱 1차 실패 후 재시도 성공 | 2 | 2 | 있음 → 없음 |

파싱 실패 로그가 모델 원본 응답 전체를 출력했다. 원본 응답은 크고 여러 줄이라 줄 단위로
세는 로그 한도를 소모한다. 변경 후에는 응답 길이만 기록한다. 줄 수는 같다.

검증: `OpenAiClientTest.parsingFailureLogsOmitRawModelResponse`

변경하지 않은 것: 재시도 횟수, retryable 판정, 검증, correction, 예외 매핑.

### 요청 단위 실패 요약

`ApiExceptionHandler`는 AI 실패 1건당 스택트레이스 없이 `log.error` 한 줄로 실패 사유를
남긴다. "실패 원인 요약 로그 정확히 한 건" 기준은 변경 전부터 충족되어 있어 수정하지 않았다.

## 2. 실제 요청 표본 — 변경 전 1회

- 테스트: `TravelIntegrationTest.makePlanByAiHasNoDuplicateCafe` (계획 생성 1건)
- 실행 위치: 메인 worktree `main` `a01685e`. `src`와 `build.gradle`이 기준 commit `918d29c`와
  동일하므로 변경 전 코드다
- 실행: 2026-09-27 01:51 UTC, 사용자 환경의 실제 외부 API, 통과
- 원본 로그와 외부 응답은 커밋하지 않았다. 아래는 출처별 개수만이다

### 계획 생성 1건의 운영 로그

| 출처 | 줄 수 | 크기 | 구성 |
|---|---|---|---|
| Spring AI `DEBUG` | 125 | 20.3 KiB | Tool 호출 31회 × 4줄 (`MethodToolCallback` 시작·성공, `DefaultToolCallingManager`, `DefaultToolCallResultConverter`) |
| `TourismTool` `INFO` | 42 | 48.4 KiB | 호출 인자 35줄, 외부 응답 전체 7줄 |
| 기타 애플리케이션 `INFO` | 7 | 1 KiB 미만 | `MissingSlotCompleter` 3, `PlanService` 영양 조회 실패 2, `JwtFilter` 2 |
| `com.planb.global.client` `DEBUG` | 0 | — | 설정은 DEBUG이나 출력 없음 |

제외한 것:
- 애플리케이션 기동·종료 로그 (Flyway, repository 설정, 커넥션 풀 등). 요청과 무관하다
- 테스트가 `System.out.println`으로 출력한 응답 본문 353줄
  (`TravelIntegrationTest.java:103`). 운영 코드가 아니다

### 판정

Railway 한도는 **초당 줄 수** 기준이다. 요청 로그 줄 174줄 중 Spring AI `DEBUG`가 125줄
(약 72%)로 주원인이다. 용량으로는 `TourismTool`의 외부 응답 전체 출력 7줄이 가장 크다.

## 3. Spring AI DEBUG — `INFO`로 축소

2절 판정에 따라 `application.yml`의 `org.springframework.ai`를 `DEBUG`에서 `INFO`로 낮췄다.

- `DEBUG`가 남기던 Tool 실행 시작·성공 기록은 `TourismTool`의 호출 인자 `INFO`와 겹친다.
  Tool 단위 추적 정보는 남는다
- `Logging:` 키 표기(대문자)는 relaxed binding으로 적용되므로 이번 변경 범위에서 고치지 않았다
- `com.planb.global.client: DEBUG`는 표본에서 출력이 없어 원인이 아니므로 그대로 두었다
- 이 설정은 모든 프로파일에 적용된다. 로컬에서 DEBUG가 필요하면
  `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI=DEBUG` 환경변수로 켤 수 있다

## 4. 요청 단위 감소량 — 추정

변경 후 external 표본은 실행하지 않았다. 아래는 2절 표본과 1절 결정적 계수로 계산한
**추정값**이며 실제 측정값이 아니다.

| 출처 | 변경 전 (측정) | 변경 후 (추정) | 근거 |
|---|---|---|---|
| Spring AI `DEBUG` | 125줄 | 0줄 | 로거 수준이 `INFO`가 되어 `DEBUG` 출력이 사라진다 |
| `TourismTool` `INFO` | 42줄 | 35줄 | 응답 전체 출력 7줄이 `DEBUG`로 내려간다 |
| 기타 애플리케이션 `INFO` | 7줄 | 7줄 | 변경 없음 |
| **합계** | **174줄** | **42줄** | 약 76% 감소 |

크기는 `TourismTool` 응답 전체 출력(48.4 KiB 중 대부분)과 Spring AI `DEBUG`(20.3 KiB)가
빠지므로 더 크게 줄어든다. 실제 감소량은 사용자가 변경 후 표본을 승인해 실행할 때 확정한다.

## 5. Phase 2로 이관

전체 요청의 결정적 로그 계수는 Phase 2 외부 stub seam 이후에 수행한다. 현재 외부 호출 없는
일정 테스트는 `OpenAiClient`, `TravelRecommendHandler` 또는 `TourismTool`을 mock하므로 두
출처를 한 요청 안에서 함께 셀 수 없다.
