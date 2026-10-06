# Phase 5B 가상 스레드 운영 적용 절차

Issue: [#135](https://github.com/team-planb-dev/BE/issues/135)  
선행 실험: [Phase 5B 비교 결과](phase5b-virtual-thread-result.md)
독립 재측정: [#133 main 병합 후 결과](phase5b-main-comparison.md)

## 적용 범위

- 공통 설정의 `SPRING_THREADS_VIRTUAL_ENABLED` 기본값은 `false`
- `true` 설정 시 Spring Boot의 Tomcat HTTP 요청 실행기와 JVM keep-alive 동시 활성
- 적용 대상은 Travel만이 아닌 전체 HTTP 요청
- STOMP inbound/outbound 채널 풀과 WebClient 이벤트 루프는 별도 실행기
- `@Async` 추가, 요청 내부 병렬화, Hikari 크기 및 외부 API 호출 계약 변경 없음

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

- `./gradlew test --tests "com.planb.integration.global.VirtualThreadHttpIntegrationTest" --tests "com.planb.integration.global.PlatformThreadHttpIntegrationTest" --tests "com.planb.performance.external.VirtualThreadConfigurationTest"` — 통과. 토글 양쪽에서 실제 HTTP 요청 스레드와 keep-alive 속성 확인
- `SPRING_THREADS_VIRTUAL_ENABLED=true ./gradlew test --tests "com.planb.integration.global.VirtualThreadHttpIntegrationTest" --tests "com.planb.integration.domain.travel.TravelLoadTestSmokeIntegrationTest" --tests "com.planb.integration.domain.chat.ChatWebSocketIntegrationTest" --tests "com.planb.unit.global.ai.mcp.NutritionEvaluationCollectorTest" --tests "com.planb.performance.external.VirtualThreadConfigurationTest"` — 13건 통과
- `./gradlew build` — 794건 통과, 실패·건너뜀 0건
- Travel 스텁 통합 테스트는 MockMvc 경로이며 실제 Tomcat 가상 스레드에서 일정 생성 요청을 실행하는 검증은 아님
- 실제 외부 API·Railway 활성화 검증은 별도 배포 단계

## 단계적 활성화

1. Railway 운영 환경변수 `SPRING_THREADS_VIRTUAL_ENABLED=true` 설정 후 재배포.
   별도의 `SPRING_MAIN_KEEP_ALIVE` 값이 이를 덮어쓰지 않는지 확인
2. `/actuator/health`와 실제 인증 요청, 일정 생성·조회, STOMP CONNECT/SUBSCRIBE/SEND 확인
3. 비슷한 요청량의 활성화 전후 구간에서 15분 이상 다음 지표 비교
   - HTTP 및 일정 생성 완료율·p95
   - Hikari pending·connection timeout
   - OpenAI·외부 API 429/5xx와 재시도·AI 실패
   - heap·RSS와 CPU
   - JFR `jdk.VirtualThreadPinned`(Java 21, 20ms 이상)
4. 비교 가능한 요청량이 없거나 외부 API quota가 부족하면 채택 판정 보류

실제 외부 API를 호출하는 통합 테스트는 사용자가 키와 비용을 관리하며 직접 실행.
STOMP와 WebClient는 실행기가 자동으로 전환되지 않지만 기능 회귀 대상으로 유지.

## 중단·복구

- 새 5xx·AI 실패·외부 429 증가, Hikari timeout 발생, 지속적인 pending,
  비교 가능한 부하에서 p95 악화, 메모리 급증 또는 요청 경로의 장시간 pinning 발생 시 중단
- `SPRING_THREADS_VIRTUAL_ENABLED=false`로 되돌리고 재배포
- 복구 후 동일 지표와 오류 로그 확인. 원인 분리 전 재활성화 금지

기본값을 `false`로 유지하므로 코드 배포만으로 실행기 변경은 발생하지 않음.
설정 활성화와 실제 운영 효과 판정은 별도의 배포 단계.
