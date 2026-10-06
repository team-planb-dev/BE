# Phase 5B 가상 스레드 운영 적용 절차

Issue: [#135](https://github.com/team-planb-dev/BE/issues/135)  
선행 실험: [Phase 5B 비교 결과](phase5b-virtual-thread-result.md)
독립 재측정: [#133 main 병합 후 결과](phase5b-main-comparison.md)

## 현재 상태

| 구분 | 상태 |
|---|---|
| 코드 반영 | `SPRING_THREADS_VIRTUAL_ENABLED` 토글과 검증 테스트를 `dev`·`main`에 반영 (#135) |
| 운영 기본값 | `false`. 코드 배포만으로는 가상 스레드가 켜지지 않음 |
| Railway 실제 활성화 | **하지 않음**. 운영 환경변수 변경과 운영 효과 측정은 별도 배포 단계 |
| 실제 Railway·유료 외부 API 조건의 성능 | **미검증** |

## 적용 범위

- 공통 설정의 `SPRING_THREADS_VIRTUAL_ENABLED` 기본값은 `false`
- `true` 설정 시 Spring Boot의 Tomcat HTTP 요청 실행기와 JVM keep-alive 동시 활성
- 적용 대상은 Travel만이 아닌 전체 HTTP 요청
- STOMP inbound/outbound 채널 풀과 WebClient 이벤트 루프는 별도 실행기
- `@Async` 추가, 요청 내부 병렬화, Hikari 크기 및 외부 API 호출 계약 변경 없음
- #136의 AI 이후 영양 조회 동시성(`planb.travel.nutrition.lookup-concurrency`, 운영 기본 1)은 별도 설정이며
  이 토글과 연결되지 않음. 요청 스레드가 가상 스레드여도 영양 조회의 `block()`은 그 요청 스레드에서만 대기함

### 토글 연결과 덮어쓰기 규칙

- `application.yml`의 두 속성이 같은 환경변수를 참조함
  - `spring.threads.virtual.enabled: ${SPRING_THREADS_VIRTUAL_ENABLED:false}`
  - `spring.main.keep-alive: ${SPRING_THREADS_VIRTUAL_ENABLED:false}`
- Spring Boot는 나중 property source가 앞의 값을 덮어쓰며, OS 환경변수가 config data(`application.yml`)보다
  우선함. 근거: [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- 따라서 운영 환경에 `SPRING_MAIN_KEEPALIVE` 또는 `SPRING_MAIN_KEEP_ALIVE`가 따로 있으면, yml의 연결과 관계없이
  그 값이 keep-alive를 정함. 두 표기 모두 같은 속성에 연결되는 것을 Spring Boot `Binder` 테스트로 확인함
  (`VirtualThreadConfigurationTest.separateKeepAliveVariableOverridesToggle`)
- `SPRING_THREADS_VIRTUAL_ENABLED` 자체도 relaxed binding으로 `spring.threads.virtual.enabled`에 직접 연결됨.
  그래서 테스트에서 이 이름의 테스트 속성만 바꾸면 실행 환경의 같은 이름 환경변수를 이기지 못함. HTTP 통합 테스트는
  표준 속성 이름도 함께 고정함

Spring Boot 4.0.2의 Tomcat customizer는 virtual mode에서 프로토콜 실행기를
`VirtualThreadExecutor`로 교체함. Spring Framework의 STOMP 채널은 별도
`ThreadPoolTaskExecutor`를 사용함. 근거:
[Spring Boot Tomcat customizer](https://docs.spring.io/spring-boot/4.0/api/java/org/springframework/boot/tomcat/autoconfigure/TomcatVirtualThreadsWebServerFactoryCustomizer.html),
[Spring STOMP 실행기 설정](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html).

## 배포 전 확인

1. #133의 5B 측정 결과가 `main`에 병합됐는지 확인
2. Railway의 `SPRING_PROFILES_ACTIVE=prod`와 활성 `common-prod` 프로파일 확인
3. 이 변경의 설정 테스트, 실제 HTTP 가상 스레드 테스트, Travel 스텁·STOMP 회귀 통과 확인
4. 배포 직전 Railway의 동시 요청량, 일정 생성 p95, 오류율, Hikari pending·timeout,
   OpenAI·외부 API 429/5xx, heap·RSS 기준값 기록
5. 서비스가 직접 호출하는 외부 API의 실제 quota와 과금 조건 확인

독립 재측정에서는 36 req/s의 p95가 플랫폼 10.05초, 가상 6.12초였고 가상 모드는 2,161건 모두 성공. 플랫폼 모드에서 Docker 경로 TCP 연결 실패 1건과 k6 시작 누락 42건이 관측되어 실제 오류 원인은 미확정.

로컬 고정 지연 스텁에서 42 req/s까지 통과했다는 결과를 Railway 처리량 보장으로 사용하지 않음.
실제 OpenAI와 Railway 자원 조건의 결과는 아직 없음.

## 로컬 검증

기준: #136(Phase 5C)까지 포함한 최신 `dev`와 통합한 #135 브랜치, 2026-10-06 실행.

| 검증 | 결과 |
|---|---|
| `./gradlew clean test` (환경변수 없음, 기본 platform thread) | 810건 통과, 실패·건너뜀 0건 |
| `SPRING_THREADS_VIRTUAL_ENABLED=true ./gradlew clean test` (전체 테스트를 가상 스레드 토글로 실행) | 810건 통과, 실패·건너뜀 0건 |
| `./gradlew build -x test` | 성공 |
| `VirtualThreadConfigurationTest` | 기본값 false, 토글 시 두 속성 동시 활성, 별도 keep-alive 환경변수의 우선 적용 확인 |
| `VirtualThreadHttpIntegrationTest`·`PlatformThreadHttpIntegrationTest` | 실행 환경변수 `true`·`false`·미설정 세 경우 모두 통과. 실제 Tomcat HTTP 요청 스레드 종류와 keep-alive 확인 |
| 가상 스레드 모드의 주요 회귀 | `TravelLoadTestSmokeIntegrationTest` 4건, `ChatWebSocketIntegrationTest` 3건, `NutritionEvaluationCollectorTest` 6건(요청별 격리 포함), `PlanServiceTest` 37건(#136 영양 조회 병렬화 포함) 통과 |

- 처음 가상 스레드 모드로 전체를 실행했을 때 `PlatformThreadHttpIntegrationTest` 1건이 실패했음. 원인은 테스트 격리였음:
  실행 환경의 `SPRING_THREADS_VIRTUAL_ENABLED=true`가 표준 속성에 직접 연결돼 테스트 속성을 이김. 두 HTTP 테스트에
  `spring.threads.virtual.enabled`를 함께 고정해 해결함. 운영 코드 변경은 없음
- Travel 스텁 통합 테스트는 MockMvc 경로이며 실제 Tomcat 가상 스레드에서 일정 생성 요청을 실행하는 검증은 아님
- 실제 외부 API 키가 필요한 `@Tag("external")` 테스트는 기본 `test`에서 제외되며 실행하지 않음. 사용자가 키와 비용을
  관리하며 `./gradlew externalTest --tests <클래스> --rerun-tasks`로 직접 실행할 대상:
  `TravelIntegrationTest`, `TravelRecommendHandlerTest`, `PlanEditRebuildTest`, `TravelLlmQualityBaselineTest`,
  `FoodNtrCpntHandlerTest`, `FoodNutritionLatencyExternalTest`, `KakaoMapServiceHandlerTest`, `Kor2ServiceHandlerTest`
- 실제 Railway 활성화와 운영 성능 검증은 하지 않음

## 단계적 활성화

1. Railway 운영 환경변수에 `SPRING_MAIN_KEEPALIVE`·`SPRING_MAIN_KEEP_ALIVE`·`SPRING_THREADS_VIRTUAL_ENABLED`가
   이미 있는지 확인. 별도 keep-alive 변수가 있으면 토글과 다른 값이 되지 않게 제거하거나 같은 값으로 맞춤
2. `SPRING_THREADS_VIRTUAL_ENABLED=true` 설정 후 재배포
3. `/actuator/health`와 실제 인증 요청, 일정 생성·조회, STOMP CONNECT/SUBSCRIBE/SEND 확인
4. 비슷한 요청량의 활성화 전후 구간에서 15분 이상 다음 지표 비교
   - HTTP 및 일정 생성 완료율·p95
   - Hikari pending·connection timeout
   - OpenAI·외부 API 429/5xx와 재시도·AI 실패
   - heap·RSS와 CPU
   - JFR `jdk.VirtualThreadPinned`(Java 21, 20ms 이상)
5. 비교 가능한 요청량이 없거나 외부 API quota가 부족하면 채택 판정 보류

실제 외부 API를 호출하는 통합 테스트는 사용자가 키와 비용을 관리하며 직접 실행.
STOMP와 WebClient는 실행기가 자동으로 전환되지 않지만 기능 회귀 대상으로 유지.

## 중단·복구

- 새 5xx·AI 실패·외부 429 증가, Hikari timeout 발생, 지속적인 pending,
  비교 가능한 부하에서 p95 악화, 메모리 급증 또는 요청 경로의 장시간 pinning 발생 시 중단
- `SPRING_THREADS_VIRTUAL_ENABLED=false`로 되돌리고 재배포
- 복구 후 동일 지표와 오류 로그 확인. 원인 분리 전 재활성화 금지

기본값을 `false`로 유지하므로 코드 배포만으로 실행기 변경은 발생하지 않음.
설정 활성화와 실제 운영 효과 판정은 별도의 배포 단계.
