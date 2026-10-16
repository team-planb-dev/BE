# P2 품질 마감: 공급자 재시도·STOMP 운영 정책 근거

확인일: 2026-10-16. 기준 작업공간 `/private/tmp/planb-p2-quality-closeout-20261016`, 검토 기준 dev `8787db6`.
읽기 전용 조사. 런타임 코드·설정 수정, 인증키 사용 API 호출, 계정 콘솔 조회 없음.

## 조사 범위와 버전

- 기존 `docs/ai/p2-followup-4-official-research.md`, `docs/ai/p2-followup-4-provider-ops-result.md`와 현재 `Kor2ServiceClient`, `WebSocketConfig` 확인.
- 프로젝트 `build.gradle`은 Spring Boot **4.0.2**. 로컬 공식 BOM `spring-boot-dependencies-4.0.2.pom`의 `spring-framework.version`은 **7.0.3**. 후속 `dependencyInsight --dependency spring-messaging --configuration testRuntimeClasspath` 실제 해석값도7.0.3(selected by rule). 같은 버전 공식 `spring-messaging-7.0.3-sources.jar`의 `SimpleBrokerRegistration`·`SimpleBrokerMessageHandler`를 열어 버전 고정 확인.
- Spring 공개 reference는 이번에도 **7.0.9**를 표시. 7.0.3 Javadoc·GitHub 웹 문서는 검색 도구 fetch 실패였으므로 해당 페이지를 이번에 직접 검증했다고 표현하지 않음. 아래 버전별 세부값은 로컬 공식 sources JAR를 근거로 사용.
- STOMP 규격은 **1.2**. StompJS 최신 API 페이지는 **stompjs 7.4.0** 표기이나 후속 고정 FE dev98f7965의 package.json은 `@stomp/stompjs:^7.3.0`. 정확한 설치·운영 배포 버전은 UNKNOWN.

## 1. TourAPI: 확정 사실과 최소 정책

**[공식]** 현행 TourAPI 포털의 오류표는 `22`를 **일일 호출 허용량 초과**, `23`을 **초당 호출 허용량 초과**로 구분. 22는 초기화 뒤 이용·트래픽 증설, 23은 잠시 후 재호출 안내. 같은 페이지에 초당 허용량 수치, 보장된 대기 시간, 22/23과 HTTP 상태의 대응은 기재되지 않음. 개발계정 1,000·운영계정 심의승인 표기로 실제 운영 계정 승인 상태나 할당량을 확인할 수 없음. [TourAPI 공식 상세·오류표](https://www.data.go.kr/data/15101578/openapi.do)

**[공식]** HTTP `429`는 일정 기간의 과도한 요청을 뜻하고 `Retry-After`를 포함할 수 있음. 표준은 요청 집계 방식·기간을 특정하지 않으므로 `429 = 초당 제한 = 1초 후 회복`이라는 등식은 성립하지 않음. [RFC 6585 §4](https://www.rfc-editor.org/rfc/rfc6585.html#section-4)

**[코드 관찰]** 조사 기준8787db6의 공유 클라이언트는 `HTTP 429`에 한해서 **1초 뒤 최대 1회** 재시도. XML `returnReasonCode`는 계측에 보존하지만 재시도 필터는 상태만 확인하므로 **429+XML22도 한 번 재시도**. 2xx XML23, HTTP500+XML23, JSON `resultCode=23`에는 재시도 없음. 이 동작은 기존 구현이며 공식 권장값을 뜻하지 않음.

| 판단 대상 | 채택 정책 | 이유·검증 범위 |
|---|---|---|
| HTTP429 + 식별된 XML22 | **최소 수정: 기존 재시도에서 제외** | [정책 추론] 일일 한도에 1초 재요청은 회복 근거가 없고 불필요한 요청 1건 증가. 실패·기록 경로는 유지. 429+22 한 번만 호출되는 무료 stub 회귀로 증명 가능 |
| 알 수 없는 본문의 HTTP429 | 기존 1초·최대1회 **유지** | [정책 결정] 이번 변경 범위에서 동작 확장을 피하는 기존 계약 유지. 1초의 최적성·공식 보장은 주장하지 않음 |
| HTTP429 + XML23 | 기존 1초·최대1회 **유지** | [정책 결정] 이미 존재하는 HTTP429 경로에만 적용. 23 전용 재시도 도입 아님 |
| HTTP429 이외 XML23, JSON23 | 기존 **무재시도 유지** | [정책 추론] 정확한 제한·복구시간·실제 재발 빈도 미측정. 새 재시도는 공급자 요청과 지연을 늘리며 회복 효과는 미확인 |
| 전역 초당 제한·동시성 제한·새 backoff·회로차단·자정 재시도 | **보류** | [정책 추론] 계정별 할당량과 정상/제한 시 관측 부재. 수치 추정·상태 보관 도입보다 현 계측으로 재발 증거 확보 우선 |
| 22 사용자 노출 문구·새 API 오류 계약 | **보류** | [범위 판단] 현재 후보 부족/공급자 실패 처리에서 별도 공개 계약으로 바꾸는 것은 클라이언트 영향 확인 필요 |

**검증에 필요한 최소 행렬:** 429+XML22 1회, 429+XML23 최대2회, 본문 없는429 최대2회, 2xx+XML22·23 각1회, 500+XML22 각1회, 기존 정상JSON·result_22 보존. 재시도 필터가 공유 클라이언트의 기존 XML 판별기를 재사용하면 새 파서·클라이언트별 중복 정책 불필요.

재발 관측 시 필요한 증거는 HTTP 상태·정규화 gateway code·endpoint·시각·시도횟수·기존 meter. 서비스키·전체 URI 노출은 불필요. 공급자 문의와 실제 계정 한도 확인은 별도 계정 접근이 있을 때 수행.

## 2. Spring simple broker heartbeat

**[공식]** simple broker heartbeat는 `TaskScheduler` 설정이 필요. 내장 WebSocket scheduler 재사용 시 순환 의존 방지를 위한 `@Lazy` 예시가 있음. [Spring reference, 이번 표시 버전 7.0.9](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-simple-broker.html)

**[7.0.3 공식 소스 확인]** `SimpleBrokerRegistration.java:55–78`, `SimpleBrokerMessageHandler.java:191–235, 265–268, 431–465`:

- scheduler 기본 미설정, heartbeat 기본 `0,0`; scheduler 설정 시 별도 heartbeat 값이 없으면 `10000,10000`ms.
- 양수 heartbeat만 지정하고 scheduler가 없으면 시작 검증 실패.
- 서버 읽기 기한은 협상된 수신 간격의 **3배**, 송신 간격은 협상된 값. 이는 Spring 구현의 오차 허용 정책이며 STOMP 공통 상수 아님.

공식 버전 고정 소스 위치: [SimpleBrokerRegistration v7.0.3](https://github.com/spring-projects/spring-framework/blob/v7.0.3/spring-messaging/src/main/java/org/springframework/messaging/simp/config/SimpleBrokerRegistration.java), [SimpleBrokerMessageHandler v7.0.3](https://github.com/spring-projects/spring-framework/blob/v7.0.3/spring-messaging/src/main/java/org/springframework/messaging/simp/broker/SimpleBrokerMessageHandler.java). 이번 검증은 아래 로컬 공식 아티팩트로 수행:

`/Users/wooju-kang/.gradle/caches/modules-2/files-2.1/org.springframework/spring-messaging/7.0.3/ea7b7f0008c98aaa687859d8047ad48bafe76ad0/spring-messaging-7.0.3-sources.jar`

**[공식]** STOMP 1.2는 CONNECT/CONNECTED의 `heart-beat`로 방향별 협상. 누락은 `0,0`, 어느 한쪽 관련 값이 0이면 해당 방향 heartbeat 없음. 둘 다 양수면 두 값의 최댓값을 사용. 수신자는 무수신 시 연결을 죽은 것으로 간주할 수 있으나 시간 오차를 허용해야 함. [STOMP 1.2 Heart-beating](https://stomp.github.io/stomp-specification-1.2.html#Heart-beating)

**[코드 관찰]** 현재 `WebSocketConfig`는 `enableSimpleBroker("/sub")`만 호출. scheduler·heartbeat 설정 없음. 서버 STOMP heartbeat는 비활성. 이는 TCP/컨테이너의 다른 연결 종료 감지까지 없다는 뜻은 아님.

**채택 정책: 현 설정 유지, 도입 보류.** [정책 추론] 서버 heartbeat는 반쯤 열린 연결 감지 수단이지 재접속·편집 결과 복구 계약 전체가 아님. 실제 프런트 협상값과 background tab 동작을 모른 상태에서 설정만 켜면 운영 검증 완료로 판정할 수 없음. 도입 재개 조건은 실제 클라이언트 버전·협상 헤더 확인, scheduler 시작 검증, 유휴 heartbeat 송수신, 무수신 종료, 재접속/재구독/인증 GET 복구의 증거. 새 scheduler·주기 설정을 이번 문서 마감 작업에 추가할 근거 없음.

**[공식]** Railway 공개 networking 문서는 WebSocket 연결을 HTTP 기간·무활동 제한에서 면제. [Railway Specs & Limits](https://docs.railway.com/networking/public-networking/specs-and-limits)

**[추론 한계]** 따라서 과거 122초 TALK 무응답을 Railway의 일반 HTTP 유휴 제한 탓으로 단정할 수 없음. 실제 운영 경로·다른 프록시·모바일 네트워크에서 절대로 끊기지 않는다는 보장도 아님.

## 3. 재연결·브라우저 검증

**[공식]** WebSocket 표준의 종료 절차는 `CLOSED` 상태와 `close` 이벤트 통지. 자동 재접속·구독 복원을 규정하지 않음. [WHATWG WebSocket 종료 절차](https://websockets.spec.whatwg.org/#feedback-from-the-protocol)

**[공식]** StompJS **7.4.0** `Client`는 자동 재접속 기능, 기본 `reconnectDelay=5000`ms(0이면 비활성), incoming/outgoing heartbeat 기본 각10000ms. 이 값은 해당 라이브러리 기본값이며 우리 프런트 설정 확인 근거가 아님. [StompJS Client API](https://stomp-js.github.io/api-docs/latest/classes/Client.html)

**[공식]** StompJS 가이드는 재연결 후에도 실행되는 `onConnect`에서 구독하도록 안내. [StompJS 재연결·구독 가이드](https://stomp-js.github.io/guide/stompjs/using-stompjs-v5.html)

**채택 정책:** 기존 접근 가능한 프런트가 있으면 읽기 전용으로 연결/구독 설정과 실제 프레임을 관찰할 수 있으나, 없으면 브라우저 실측을 **미검증**으로 남김. 임시 프런트·새 CDN STOMP 클라이언트를 만들어 실제 프런트 검증으로 대체하지 않음. 로컬 Java 하네스의 연결·수신·GET 복구 계약 증거와 브라우저·운영 프록시 검증을 구분. STOMP 규격만으로 재접속 후 메시지 재전송·누락 복구·중복 방지 보장을 주장하지 않음.

## 4. 마감 문구의 증거 경계

- **올바른 표현:** “수집한 실행 로그 5종에서 해당 경고 문자열 0건”, “수집한 TourAPI meter delta 131건 모두 ok”, “Kakao 경로 meter는 당시 수집하지 않아 실패 횟수 UNKNOWN”.
- **수정할 과추론:** 기존 후속4 결과의 “NutritionService 예외 경고 0건이므로 timeout·게이트웨이 오류는 아님”은 “해당 로그에서 timeout·게이트웨이 예외의 직접 증거 미발견; 미수집·흡수·로깅 경로를 포함한 실제 발생 부재까지 증명하지 못함”으로 제한.
- 응답 본문·에러 코드가 없던 과거 Kakao400 원인은 UNKNOWN 유지. 이후 성공은 과거 원인 해소 증거가 아님.
- 실제 운영계정 승인·사용량·브라우저 복구·실제 장애 재현은 이번 무료 조사로 확인하지 않았음. 무발생·완전 해결·운영 검증 완료라고 쓰지 않음.

정리된 구현 범위는 **알려진 HTTP429+XML22의 불필요한 기존 재시도 1회 제거**. 나머지는 기존 동작 보존과 명시적 보류 사유·재개 조건 기록.

생략·미확인: 실제 API 호출, 계정 콘솔, 프런트 브라우저 실측, 운영 재배포 실험.
남은 위험: 알 수 없는429의 기존1초 재시도 적절성, 비429 XML23의 일시 실패, heartbeat 미사용과 프런트 복구 미검증.
