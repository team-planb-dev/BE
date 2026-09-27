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

## 2. 실제 요청 표본 — 미확보 (blocker)

변경 전 external 표본(`TravelIntegrationTest.makePlanByAiHasNoDuplicateCafe` 1회)은 실행에
성공했으나 결과가 IntelliJ 콘솔에만 남아 출처별 개수를 계수하지 못했다.

재수집 방법: 메인 worktree(`main`, `a01685e`)는 기준 commit `918d29c`와 `src`,
`build.gradle`이 동일하므로 변경 전 코드로 다시 실행할 수 있다. 터미널 Gradle로 실행하면
`build/test-results/externalTest/`의 XML `<system-out>`에서 계수할 수 있다.

```bash
./gradlew externalTest --tests "com.planb.integration.domain.travel.TravelIntegrationTest.makePlanByAiHasNoDuplicateCafe"
```

기록할 값은 출처별 개수(Spring AI `DEBUG`, `TourismTool` `INFO`, 기타)와 실행 commit뿐이다.
원본 로그와 외부 응답은 커밋하지 않는다.

## 3. Spring AI DEBUG — 판정 보류

`application.yml`의 `Logging:` 키가 relaxed binding으로 `org.springframework.ai: DEBUG`를
모든 프로파일에 적용한다. 이 출처가 로그 폭증의 원인인지는 2절 표본 없이 판정할 수 없다.

Issue #85 기준("계수하거나 판정할 수 없는 출처는 로그 수준을 바꾸지 않는다")에 따라
`application.yml`은 수정하지 않았다. 표본을 확보한 뒤 별도 변경으로 판단한다.

## 4. 요청 단위 감소량

변경 후 external 표본은 실행하지 않았다. 요청 단위 감소량은 다음으로 **추정**한다.
실제 측정값이 아니다.

```
TourismTool INFO 감소량 = (관광지 후보 + 음식점 검색 + 음식점 후보 조회 호출 수) × 1
```

세 메서드의 요청당 호출 수는 2절 표본에서 얻는다. 표본이 없어 현재는 수치를 확정하지 않았다.

## 5. Phase 2로 이관

전체 요청의 결정적 로그 계수는 Phase 2 외부 stub seam 이후에 수행한다. 현재 외부 호출 없는
일정 테스트는 `OpenAiClient`, `TravelRecommendHandler` 또는 `TourismTool`을 mock하므로 두
출처를 한 요청 안에서 함께 셀 수 없다.
